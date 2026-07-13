package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.autonowhite.name", description = "nyxclient.module.autonowhite.description", category = Category.OTHER)
public class AutoNoWhite extends Module {
    public static final AutoNoWhite INSTANCE = new AutoNoWhite();

    private static final double MIN_FORWARD_DISTANCE = 0.25D;
    private static final double MIN_VERTICAL_ROW_SPAN = 1.0D;
    private static final double ROW_EPSILON = 0.25D;

    public final IntValue scanRange = ValueBuild.intSetting("scan range", 5, 2, 12, 1, this);
    public final BoolValue requireLineOfSight = ValueBuild.boolSetting("line of sight", true, this);
    public final BoolValue swing = ValueBuild.boolSetting("swing", true, this);
    public final EnumValue<DetectMode> detectMode = ValueBuild.enumSetting("detect mode", DetectMode.NAMED_BLACK, this);

    private volatile int boardUpdateSequence;
    private volatile int handledBoardUpdateSequence = -1;

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (isNull() || mc.screen != null || mc.player.connection == null) {
            reset();
            return;
        }

        if (handledBoardUpdateSequence == boardUpdateSequence) {
            return;
        }

        ScanResult scan = scanTargets();
        if (!scan.hasBoard()) {
            handledBoardUpdateSequence = boardUpdateSequence;
            return;
        }

        clickFirstTarget(scan.targets());
        handledBoardUpdateSequence = boardUpdateSequence;
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (isNull() || mc.player.connection == null) {
            return;
        }

        if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet) {
            markBoardUpdated(packet.getPos());
            return;
        }

        if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates((pos, state) -> markBoardUpdated(pos));
        }
    }

    private void clickFirstTarget(List<ClickTarget> targets) {
        for (ClickTarget target : targets) {
            if (isClickableTarget(target.pos())) {
                click(target);
                return;
            }
        }
    }

    private void reset() {
        boardUpdateSequence = 0;
        handledBoardUpdateSequence = -1;
    }

    private ScanResult scanTargets() {
        int range = scanRange.getValue();
        BlockPos playerPos = mc.player.blockPosition();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 forward = horizontalForward();
        if (forward.lengthSqr() < 1.0E-6D) {
            return ScanResult.empty();
        }
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        List<BoardBlock> boardBlocks = scanBoardBlocks(range, playerPos, eyePos, forward, right);
        if (boardBlocks.isEmpty()) {
            return ScanResult.empty();
        }

        boolean verticalRows = hasVerticalRows(boardBlocks);
        double bottomRow = bottomRowCoordinate(boardBlocks, forward, verticalRows);
        List<ClickTarget> targets = new ArrayList<>();
        double maxReachSqr = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();

        for (BoardBlock block : boardBlocks) {
            BlockPos pos = block.pos();
            if (!block.target() || !isBottomRow(block, forward, verticalRows, bottomRow)) {
                continue;
            }

            Vec3 center = block.center();
            if (eyePos.distanceToSqr(center) > maxReachSqr || !isClickableTarget(pos)) {
                continue;
            }

            BlockHitResult hitResult = raycastBlock(center);
            if (requireLineOfSight.getValue()
                    && (hitResult.getType() != HitResult.Type.BLOCK || !hitResult.getBlockPos().equals(pos))) {
                continue;
            }

            Direction direction = hitResult.getType() == HitResult.Type.BLOCK && hitResult.getBlockPos().equals(pos)
                    ? hitResult.getDirection()
                    : faceTowardPlayer(pos);
            targets.add(new ClickTarget(pos, direction, targetScore(center)));
        }

        targets.sort(Comparator.comparingDouble(ClickTarget::score));
        return new ScanResult(targets, true);
    }

    private List<BoardBlock> scanBoardBlocks(int range, BlockPos playerPos, Vec3 eyePos, Vec3 forward, Vec3 right) {
        List<BoardBlock> boardBlocks = new ArrayList<>();
        for (BlockPos mutablePos : BlockPos.betweenClosed(
                playerPos.offset(-range, -range, -range),
                playerPos.offset(range, range, range)
        )) {
            BlockPos pos = mutablePos.immutable();
            BoardBlockType type = boardBlockType(mc.level.getBlockState(pos));
            if (type == BoardBlockType.NONE) {
                continue;
            }

            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 delta = center.subtract(eyePos);
            double forwardDistance = delta.dot(forward);
            if (forwardDistance < MIN_FORWARD_DISTANCE || forwardDistance > range) {
                continue;
            }

            if (Math.abs(delta.dot(right)) > range || Math.abs(delta.y) > range) {
                continue;
            }

            boardBlocks.add(new BoardBlock(pos, center, type == BoardBlockType.TARGET));
        }

        return boardBlocks;
    }

    private static boolean hasVerticalRows(List<BoardBlock> boardBlocks) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BoardBlock block : boardBlocks) {
            int y = block.pos().getY();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return maxY - minY >= MIN_VERTICAL_ROW_SPAN;
    }

    private static double bottomRowCoordinate(List<BoardBlock> boardBlocks, Vec3 forward, boolean verticalRows) {
        double bottomRow = Double.POSITIVE_INFINITY;
        for (BoardBlock block : boardBlocks) {
            bottomRow = Math.min(bottomRow, rowCoordinate(block, forward, verticalRows));
        }
        return bottomRow;
    }

    private static boolean isBottomRow(BoardBlock block, Vec3 forward, boolean verticalRows, double bottomRow) {
        return Math.abs(rowCoordinate(block, forward, verticalRows) - bottomRow) <= ROW_EPSILON;
    }

    private static double rowCoordinate(BoardBlock block, Vec3 forward, boolean verticalRows) {
        if (verticalRows) {
            return block.pos().getY();
        }

        if (Math.abs(forward.x) >= Math.abs(forward.z)) {
            return block.pos().getX() * Math.signum(forward.x);
        }
        return block.pos().getZ() * Math.signum(forward.z);
    }

    private boolean isClickableTarget(BlockPos pos) {
        return pos != null
                && mc.level != null
                && isTargetBlock(mc.level.getBlockState(pos));
    }

    private boolean isTargetBlock(BlockState state) {
        return boardBlockType(state) == BoardBlockType.TARGET;
    }

    private BoardBlockType boardBlockType(BlockState state) {
        if (state == null || state.isAir()) {
            return BoardBlockType.NONE;
        }

        String path = blockPath(state.getBlock());
        if (isKnownWhitePath(path)) {
            return BoardBlockType.SAFE;
        }

        if (isKnownBlackPath(path)) {
            return BoardBlockType.TARGET;
        }

        return detectMode.is(DetectMode.DARK_COLOR) && isDarkMapColor(state) ? BoardBlockType.TARGET : BoardBlockType.NONE;
    }

    private static boolean isKnownBlackPath(String path) {
        return path.equals("black_concrete");
    }

    private static boolean isKnownWhitePath(String path) {
        return path.startsWith("white_")
                || path.equals("snow")
                || path.equals("snow_block")
                || path.equals("powder_snow")
                || path.equals("quartz_block")
                || path.endsWith("_quartz");
    }

    private static String blockPath(Block block) {
        String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
        int separator = id.indexOf(':');
        return separator == -1 ? id : id.substring(separator + 1);
    }

    private boolean isDarkMapColor(BlockState state) {
        MapColor color = state.getMapColor(mc.level, BlockPos.ZERO);
        if (color == MapColor.NONE) {
            return false;
        }

        int argb = color.calculateARGBColor(MapColor.Brightness.NORMAL);
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max <= 55 || max <= 85 && max - min <= 25;
    }

    private BlockHitResult raycastBlock(Vec3 target) {
        return mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
    }

    private Direction faceTowardPlayer(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eyes = mc.player.getEyePosition();
        double dx = eyes.x - center.x;
        double dy = eyes.y - center.y;
        double dz = eyes.z - center.z;
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absX >= absY && absX >= absZ) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (absZ >= absX && absZ >= absY) {
            return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        return dy >= 0.0D ? Direction.UP : Direction.DOWN;
    }

    private void markBoardUpdated(BlockPos pos) {
        if (pos != null && isInFrontScanArea(pos)) {
            boardUpdateSequence++;
        }
    }

    private boolean isInFrontScanArea(BlockPos pos) {
        int range = scanRange.getValue();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 forward = horizontalForward();
        if (forward.lengthSqr() < 1.0E-6D) {
            return false;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 delta = center.subtract(eyePos);
        double forwardDistance = delta.dot(forward);
        if (forwardDistance < MIN_FORWARD_DISTANCE || forwardDistance > range) {
            return false;
        }

        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return Math.abs(delta.dot(right)) <= range && Math.abs(delta.y) <= range;
    }

    private double targetScore(Vec3 center) {
        Vector2f rotations = RotationUtility.calculate(center);
        double yawDiff = Math.abs(Mth.wrapDegrees(rotations.x - mc.player.getYRot()));
        double pitchDiff = Math.abs(rotations.y - mc.player.getXRot());
        double distanceSqr = mc.player.getEyePosition().distanceToSqr(center);
        return yawDiff * 0.75D + pitchDiff + distanceSqr * 0.1D;
    }

    private void click(ClickTarget target) {
        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    target.pos(),
                    target.direction(),
                    prediction.currentSequence()
            ));
        }

        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private Vec3 horizontalForward() {
        Vec3 look = mc.player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        double length = forward.length();
        return length <= 1.0E-6D ? Vec3.ZERO : forward.scale(1.0D / length);
    }

    private enum DetectMode {
        NAMED_BLACK,
        DARK_COLOR
    }

    private enum BoardBlockType {
        NONE,
        SAFE,
        TARGET
    }

    private record ClickTarget(BlockPos pos, Direction direction, double score) {
    }

    private record ScanResult(List<ClickTarget> targets, boolean hasBoard) {
        private static ScanResult empty() {
            return new ScanResult(List.of(), false);
        }
    }

    private record BoardBlock(BlockPos pos, Vec3 center, boolean target) {
    }
}
