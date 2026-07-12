package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "nyxclient.module.auto2048.name", description = "nyxclient.module.auto2048.description", category = Category.OTHER)
public class Auto2048 extends Module {
    public static final Auto2048 INSTANCE = new Auto2048();

    private static final int SIZE = 4;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<!\\d)(\\d+)(?!\\d)");
    private static final double AXIS_CLUSTER_EPSILON = 0.35D;
    private static final double MIN_AXIS_STEP = 0.25D;

    public final IntValue scanRange = ValueBuild.intSetting("scan range", 8, 2, 32, 1, this);
    public final DoubleValue cellSpacing = ValueBuild.doubleSetting("cell spacing", 1.0D, 0.4D, 3.0D, 0.05D, this);
    public final IntValue minTiles = ValueBuild.intSetting("min tiles", 2, 1, 16, 1, this);
    public final IntValue searchDepth = ValueBuild.intSetting("search depth", 3, 1, 5, 1, this);
    public final IntValue pressTicks = ValueBuild.intSetting("press ticks", 4, 1, 20, 1, this);
    public final IntValue moveDelay = ValueBuild.intSetting("move delay", 8, 1, 40, 1, this);
    public final BoolValue verticalBoard = ValueBuild.boolSetting("vertical board", false, this);
    public final BoolValue releaseOtherMovement = ValueBuild.boolSetting("release other movement", true, this);

    private KeyMapping activeKey;
    private boolean activeKeyWasDown;
    private int pressTicksLeft;
    private int delayTicksLeft;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        releaseActiveKey();
        resetState();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (isNull() || mc.screen != null) {
            releaseActiveKey();
            delayTicksLeft = moveDelay.getValue();
            return;
        }

        if (pressTicksLeft > 0) {
            pressTicksLeft--;
            if (activeKey != null) {
                activeKey.setDown(true);
            }
            return;
        }

        if (activeKey != null) {
            releaseActiveKey();
            delayTicksLeft = moveDelay.getValue();
            return;
        }

        if (delayTicksLeft > 0) {
            delayTicksLeft--;
            return;
        }

        BoardSnapshot snapshot = scanBoard();
        if (snapshot == null || snapshot.tileCount() < minTiles.getValue()) {
            return;
        }

        Move move = chooseMove(snapshot.board());
        if (move != null) {
            press(move);
        }
    }

    private void resetState() {
        activeKey = null;
        activeKeyWasDown = false;
        pressTicksLeft = 0;
        delayTicksLeft = 0;
    }

    private void press(Move move) {
        KeyMapping key = keyFor(move);
        if (key == null) {
            return;
        }

        if (releaseOtherMovement.getValue()) {
            releaseMovementKeysExcept(key);
        }

        activeKey = key;
        activeKeyWasDown = key.isDown();
        activeKey.setDown(true);
        pressTicksLeft = pressTicks.getValue();
    }

    private void releaseActiveKey() {
        if (activeKey != null) {
            activeKey.setDown(activeKeyWasDown);
        }
        activeKey = null;
        activeKeyWasDown = false;
        pressTicksLeft = 0;
    }

    private void releaseMovementKeysExcept(KeyMapping except) {
        if (mc.options.keyUp != except) {
            mc.options.keyUp.setDown(false);
        }
        if (mc.options.keyDown != except) {
            mc.options.keyDown.setDown(false);
        }
        if (mc.options.keyLeft != except) {
            mc.options.keyLeft.setDown(false);
        }
        if (mc.options.keyRight != except) {
            mc.options.keyRight.setDown(false);
        }
    }

    private KeyMapping keyFor(Move move) {
        return switch (move) {
            case UP -> mc.options.keyUp;
            case DOWN -> mc.options.keyDown;
            case LEFT -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
        };
    }

    private BoardSnapshot scanBoard() {
        LocalPlayer player = mc.player;
        int range = scanRange.getValue();
        AABB searchBox = player.getBoundingBox().inflate(range, range, range);
        List<Entity> entities = mc.level.getEntities(
                player,
                searchBox,
                entity -> entity != null && !entity.isRemoved() && namedTileValue(entity) > 0
        );
        if (entities.isEmpty()) {
            return null;
        }

        Vec3 forward = horizontalForward(player);
        if (forward.lengthSqr() < 1.0E-6D) {
            return null;
        }
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        List<Tile> tiles = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            int value = namedTileValue(entity);
            if (value <= 0) {
                continue;
            }
            Vec3 delta = entity.position().subtract(player.position());
            tiles.add(new Tile(value, delta.dot(right), rowCoordinate(delta, forward), delta.lengthSqr()));
        }
        if (tiles.isEmpty()) {
            return null;
        }

        AxisMapping columns = inferAxis(tiles.stream().mapToDouble(Tile::column).toArray(), 0.0D);
        AxisMapping rows = inferAxis(tiles.stream().mapToDouble(Tile::row).toArray(), average(tiles.stream().mapToDouble(Tile::row).toArray()));
        if (columns == null || rows == null) {
            return null;
        }

        int[][] board = new int[SIZE][SIZE];
        int tileCount = 0;
        tiles.sort(Comparator.comparingDouble(Tile::distanceSqr));
        for (Tile tile : tiles) {
            int column = columns.index(tile.column());
            int row = rows.index(tile.row());
            if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
                continue;
            }

            if (board[row][column] == 0) {
                tileCount++;
            }
            board[row][column] = Math.max(board[row][column], tile.value());
        }

        return tileCount == 0 ? null : new BoardSnapshot(board, tileCount);
    }

    private double rowCoordinate(Vec3 delta, Vec3 forward) {
        if (verticalBoard.getValue()) {
            return -delta.y;
        }
        return -delta.dot(forward);
    }

    private static Vec3 horizontalForward(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        double length = forward.length();
        return length <= 1.0E-6D ? Vec3.ZERO : forward.scale(1.0D / length);
    }

    private int namedTileValue(Entity entity) {
        String name = entityName(entity);
        if (name.isBlank()) {
            return 0;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(name.replace(",", ""));
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (isPowerOfTwoTile(value)) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String entityName(Entity entity) {
        Component customName = entity.getCustomName();
        String name = customName != null ? customName.getString() : entity.getName().getString();
        String stripped = ChatFormatting.stripFormatting(name);
        return stripped == null ? "" : stripped.strip();
    }

    private static boolean isPowerOfTwoTile(int value) {
        return value >= 2 && (value & value - 1) == 0;
    }

    private AxisMapping inferAxis(double[] positions, double expectedCenter) {
        if (positions.length == 0) {
            return null;
        }

        double step = inferStep(positions);
        List<AxisMapping> candidates = new ArrayList<>();
        for (double position : positions) {
            for (int index = 0; index < SIZE; index++) {
                double start = position - index * step;
                candidates.add(new AxisMapping(start, step));
            }
        }

        int requiredFits = Math.min(positions.length, Math.max(1, minTiles.getValue()));
        return candidates.stream()
                .min(Comparator.comparingInt((AxisMapping mapping) -> -mapping.validCount(positions))
                        .thenComparingDouble(mapping -> mapping.fitError(positions))
                        .thenComparingDouble(mapping -> Math.abs(mapping.center() - expectedCenter)))
                .filter(mapping -> mapping.validCount(positions) >= requiredFits)
                .orElse(null);
    }

    private double inferStep(double[] positions) {
        double configured = cellSpacing.getValue();
        double best = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;

        Arrays.sort(positions);
        for (int i = 0; i < positions.length; i++) {
            for (int j = i + 1; j < positions.length; j++) {
                double diff = Math.abs(positions[j] - positions[i]);
                if (diff < MIN_AXIS_STEP) {
                    continue;
                }
                int slots = Math.max(1, Math.min(3, (int)Math.round(diff / configured)));
                double candidate = diff / slots;
                double error = Math.abs(candidate - configured);
                if (candidate >= MIN_AXIS_STEP && error < bestError) {
                    best = candidate;
                    bestError = error;
                }
            }
        }

        if (Double.isNaN(best)) {
            return configured;
        }
        return best;
    }

    private Move chooseMove(int[][] board) {
        Move bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int depth = searchDepth.getValue();

        for (Move move : Move.values()) {
            MoveResult result = move(board, move);
            if (!result.changed()) {
                continue;
            }

            double score = expectimax(result.board(), depth - 1, false);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private double expectimax(int[][] board, int depth, boolean playerTurn) {
        if (depth <= 0) {
            return evaluate(board);
        }

        if (playerTurn) {
            double best = Double.NEGATIVE_INFINITY;
            for (Move move : Move.values()) {
                MoveResult result = move(board, move);
                if (result.changed()) {
                    best = Math.max(best, expectimax(result.board(), depth - 1, false));
                }
            }
            return best == Double.NEGATIVE_INFINITY ? evaluate(board) : best;
        }

        List<Cell> emptyCells = emptyCells(board);
        if (emptyCells.isEmpty()) {
            return expectimax(board, depth - 1, true);
        }

        double total = 0.0D;
        double cellProbability = 1.0D / emptyCells.size();
        for (Cell cell : emptyCells) {
            int[][] withTwo = copyBoard(board);
            withTwo[cell.row()][cell.column()] = 2;
            total += cellProbability * 0.9D * expectimax(withTwo, depth - 1, true);

            int[][] withFour = copyBoard(board);
            withFour[cell.row()][cell.column()] = 4;
            total += cellProbability * 0.1D * expectimax(withFour, depth - 1, true);
        }
        return total;
    }

    private static MoveResult move(int[][] board, Move move) {
        int[][] result = new int[SIZE][SIZE];
        boolean changed = false;

        for (int i = 0; i < SIZE; i++) {
            int[] line = readLine(board, move, i);
            int[] merged = mergeLine(line);
            writeLine(result, move, i, merged);
            changed |= !Arrays.equals(line, merged);
        }

        return new MoveResult(result, changed);
    }

    private static int[] readLine(int[][] board, Move move, int index) {
        int[] line = new int[SIZE];
        for (int offset = 0; offset < SIZE; offset++) {
            int row;
            int column;
            switch (move) {
                case LEFT -> {
                    row = index;
                    column = offset;
                }
                case RIGHT -> {
                    row = index;
                    column = SIZE - 1 - offset;
                }
                case UP -> {
                    row = offset;
                    column = index;
                }
                case DOWN -> {
                    row = SIZE - 1 - offset;
                    column = index;
                }
                default -> throw new IllegalStateException("Unexpected value: " + move);
            }
            line[offset] = board[row][column];
        }
        return line;
    }

    private static void writeLine(int[][] board, Move move, int index, int[] line) {
        for (int offset = 0; offset < SIZE; offset++) {
            int row;
            int column;
            switch (move) {
                case LEFT -> {
                    row = index;
                    column = offset;
                }
                case RIGHT -> {
                    row = index;
                    column = SIZE - 1 - offset;
                }
                case UP -> {
                    row = offset;
                    column = index;
                }
                case DOWN -> {
                    row = SIZE - 1 - offset;
                    column = index;
                }
                default -> throw new IllegalStateException("Unexpected value: " + move);
            }
            board[row][column] = line[offset];
        }
    }

    private static int[] mergeLine(int[] line) {
        int[] compact = new int[SIZE];
        int compactIndex = 0;
        for (int value : line) {
            if (value != 0) {
                compact[compactIndex++] = value;
            }
        }

        int[] merged = new int[SIZE];
        int mergedIndex = 0;
        for (int i = 0; i < SIZE; i++) {
            int value = compact[i];
            if (value == 0) {
                continue;
            }
            if (i + 1 < SIZE && compact[i + 1] == value) {
                merged[mergedIndex++] = value * 2;
                i++;
            } else {
                merged[mergedIndex++] = value;
            }
        }
        return merged;
    }

    private static double evaluate(int[][] board) {
        int empty = emptyCells(board).size();
        int max = maxTile(board);
        double score = empty * 2700.0D;
        score += log2(max) * 1200.0D;
        score += mergePotential(board) * 700.0D;
        score += monotonicity(board) * 250.0D;
        score -= smoothness(board) * 80.0D;
        score += snakeScore(board) * 2.0D;
        if (isMaxInCorner(board, max)) {
            score += 4000.0D;
        }
        return score;
    }

    private static double smoothness(int[][] board) {
        double penalty = 0.0D;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int value = board[row][column];
                if (value == 0) {
                    continue;
                }
                if (column + 1 < SIZE && board[row][column + 1] != 0) {
                    penalty += Math.abs(log2(value) - log2(board[row][column + 1]));
                }
                if (row + 1 < SIZE && board[row + 1][column] != 0) {
                    penalty += Math.abs(log2(value) - log2(board[row + 1][column]));
                }
            }
        }
        return penalty;
    }

    private static double monotonicity(int[][] board) {
        double score = 0.0D;
        for (int row = 0; row < SIZE; row++) {
            score -= lineMonotonicPenalty(board[row]);
        }
        for (int column = 0; column < SIZE; column++) {
            int[] line = new int[SIZE];
            for (int row = 0; row < SIZE; row++) {
                line[row] = board[row][column];
            }
            score -= lineMonotonicPenalty(line);
        }
        return score;
    }

    private static double lineMonotonicPenalty(int[] line) {
        double increasingPenalty = 0.0D;
        double decreasingPenalty = 0.0D;
        for (int i = 0; i < SIZE - 1; i++) {
            double current = log2(line[i]);
            double next = log2(line[i + 1]);
            increasingPenalty += Math.max(0.0D, current - next);
            decreasingPenalty += Math.max(0.0D, next - current);
        }
        return Math.min(increasingPenalty, decreasingPenalty);
    }

    private static double snakeScore(int[][] board) {
        int[][] weights = {
                {64, 32, 16, 8},
                {1, 2, 4, 7},
                {1, 1, 2, 3},
                {1, 1, 1, 2}
        };
        double best = Double.NEGATIVE_INFINITY;
        for (int rotation = 0; rotation < 4; rotation++) {
            double score = 0.0D;
            for (int row = 0; row < SIZE; row++) {
                for (int column = 0; column < SIZE; column++) {
                    score += log2(board[row][column]) * rotatedWeight(weights, rotation, row, column);
                }
            }
            best = Math.max(best, score);
        }
        return best;
    }

    private static int rotatedWeight(int[][] weights, int rotation, int row, int column) {
        return switch (rotation) {
            case 0 -> weights[row][column];
            case 1 -> weights[column][SIZE - 1 - row];
            case 2 -> weights[SIZE - 1 - row][SIZE - 1 - column];
            case 3 -> weights[SIZE - 1 - column][row];
            default -> throw new IllegalStateException("Unexpected value: " + rotation);
        };
    }

    private static int mergePotential(int[][] board) {
        int merges = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int value = board[row][column];
                if (value == 0) {
                    continue;
                }
                if (column + 1 < SIZE && board[row][column + 1] == value) {
                    merges++;
                }
                if (row + 1 < SIZE && board[row + 1][column] == value) {
                    merges++;
                }
            }
        }
        return merges;
    }

    private static boolean isMaxInCorner(int[][] board, int max) {
        return max > 0
                && (board[0][0] == max
                || board[0][SIZE - 1] == max
                || board[SIZE - 1][0] == max
                || board[SIZE - 1][SIZE - 1] == max);
    }

    private static int maxTile(int[][] board) {
        int max = 0;
        for (int[] row : board) {
            for (int value : row) {
                max = Math.max(max, value);
            }
        }
        return max;
    }

    private static List<Cell> emptyCells(int[][] board) {
        List<Cell> cells = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (board[row][column] == 0) {
                    cells.add(new Cell(row, column));
                }
            }
        }
        return cells;
    }

    private static int[][] copyBoard(int[][] board) {
        int[][] copy = new int[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(board[row], 0, copy[row], 0, SIZE);
        }
        return copy;
    }

    private static double log2(int value) {
        return value <= 0 ? 0.0D : Math.log(value) / Math.log(2.0D);
    }

    private static double average(double[] values) {
        if (values.length == 0) {
            return 0.0D;
        }
        double total = 0.0D;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private enum Move {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    private record BoardSnapshot(int[][] board, int tileCount) {
    }

    private record MoveResult(int[][] board, boolean changed) {
    }

    private record Cell(int row, int column) {
    }

    private record Tile(int value, double column, double row, double distanceSqr) {
    }

    private record AxisMapping(double start, double step) {
        int index(double position) {
            int index = (int)Math.round((position - start) / step);
            if (index < 0 || index >= SIZE) {
                return -1;
            }

            double center = start + index * step;
            return Math.abs(position - center) <= step * 0.5D + AXIS_CLUSTER_EPSILON ? index : -1;
        }

        double center() {
            return start + step * 1.5D;
        }

        int validCount(double[] positions) {
            int count = 0;
            for (double position : positions) {
                if (index(position) != -1) {
                    count++;
                }
            }
            return count;
        }

        double fitError(double[] positions) {
            double score = 0.0D;
            for (double position : positions) {
                int index = index(position);
                if (index != -1) {
                    double center = start + index * step;
                    double error = position - center;
                    score += error * error;
                }
            }
            return score;
        }
    }
}
