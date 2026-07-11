package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(name = "nyxclient.module.containeresp.name", description = "nyxclient.module.containeresp.description", category = Category.VISUAL)
public class ContainerESP extends Module {
    public static final ContainerESP INSTANCE = new ContainerESP();
    private static final Direction[] FACE_DIRECTIONS = {
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };
    private static final double LINE_MERGE_EPSILON = 1.0E-7D;

    public final BoolValue fill = ValueBuild.boolSetting("fill", true, this);
    public final BoolValue outline = ValueBuild.boolSetting("outline", true, this);
    public final IntValue renderRange = ValueBuild.intSetting("render range", 128, 16, 512, 8, this);
    public final IntValue fillAlpha = ValueBuild.intSetting("fill alpha", 45, 0, 255, 5, () -> fill.getValue(), this);
    public final IntValue outlineAlpha = ValueBuild.intSetting("outline alpha", 220, 0, 255, 5, () -> outline.getValue(), this);
    public final DoubleValue boxInflate = ValueBuild.doubleSetting("box inflate", 0.002D, 0.0D, 0.05D, 0.001D, this);

    public final BoolValue chests = ValueBuild.boolSetting("chests", true, this);
    public final ColorValue chestColor = ValueBuild.colorSetting("chest color", new Color(255, 184, 64), false, () -> chests.getValue(), this);

    public final BoolValue trappedChests = ValueBuild.boolSetting("trapped chests", true, this);
    public final ColorValue trappedChestColor = ValueBuild.colorSetting("trapped chest color", new Color(255, 82, 82), false, () -> trappedChests.getValue(), this);

    public final BoolValue enderChests = ValueBuild.boolSetting("ender chests", true, this);
    public final ColorValue enderChestColor = ValueBuild.colorSetting("ender chest color", new Color(156, 92, 255), false, () -> enderChests.getValue(), this);

    public final BoolValue barrels = ValueBuild.boolSetting("barrels", true, this);
    public final ColorValue barrelColor = ValueBuild.colorSetting("barrel color", new Color(192, 132, 72), false, () -> barrels.getValue(), this);

    public final BoolValue shulkerBoxes = ValueBuild.boolSetting("shulker boxes", true, this);
    public final ColorValue shulkerBoxColor = ValueBuild.colorSetting("shulker box color", new Color(224, 82, 255), false, () -> shulkerBoxes.getValue(), this);

    public final BoolValue furnaces = ValueBuild.boolSetting("furnaces", true, this);
    public final ColorValue furnaceColor = ValueBuild.colorSetting("furnace color", new Color(154, 166, 178), false, () -> furnaces.getValue(), this);

    public final BoolValue blastFurnaces = ValueBuild.boolSetting("blast furnaces", true, this);
    public final ColorValue blastFurnaceColor = ValueBuild.colorSetting("blast furnace color", new Color(86, 166, 255), false, () -> blastFurnaces.getValue(), this);

    public final BoolValue smokers = ValueBuild.boolSetting("smokers", true, this);
    public final ColorValue smokerColor = ValueBuild.colorSetting("smoker color", new Color(110, 205, 145), false, () -> smokers.getValue(), this);

    public final BoolValue brewingStands = ValueBuild.boolSetting("brewing stands", true, this);
    public final ColorValue brewingStandColor = ValueBuild.colorSetting("brewing stand color", new Color(82, 217, 205), false, () -> brewingStands.getValue(), this);

    public final BoolValue hoppers = ValueBuild.boolSetting("hoppers", true, this);
    public final ColorValue hopperColor = ValueBuild.colorSetting("hopper color", new Color(92, 150, 185), false, () -> hoppers.getValue(), this);

    public final BoolValue dispensers = ValueBuild.boolSetting("dispensers", true, this);
    public final ColorValue dispenserColor = ValueBuild.colorSetting("dispenser color", new Color(145, 170, 188), false, () -> dispensers.getValue(), this);

    public final BoolValue droppers = ValueBuild.boolSetting("droppers", true, this);
    public final ColorValue dropperColor = ValueBuild.colorSetting("dropper color", new Color(132, 215, 255), false, () -> droppers.getValue(), this);

    public final BoolValue crafters = ValueBuild.boolSetting("crafters", true, this);
    public final ColorValue crafterColor = ValueBuild.colorSetting("crafter color", new Color(255, 205, 72), false, () -> crafters.getValue(), this);

    public final BoolValue decoratedPots = ValueBuild.boolSetting("decorated pots", true, this);
    public final ColorValue decoratedPotColor = ValueBuild.colorSetting("decorated pot color", new Color(214, 115, 67), false, () -> decoratedPots.getValue(), this);

    public final BoolValue chiseledBookshelves = ValueBuild.boolSetting("chiseled bookshelves", true, this);
    public final ColorValue chiseledBookshelfColor = ValueBuild.colorSetting("chiseled bookshelf color", new Color(204, 151, 89), false, () -> chiseledBookshelves.getValue(), this);

    public final BoolValue shelves = ValueBuild.boolSetting("shelves", true, this);
    public final ColorValue shelfColor = ValueBuild.colorSetting("shelf color", new Color(139, 212, 116), false, () -> shelves.getValue(), this);

    private final List<ContainerEntry> containers = List.of(
            new ContainerEntry(BlockEntityType.CHEST, chests, chestColor),
            new ContainerEntry(BlockEntityType.TRAPPED_CHEST, trappedChests, trappedChestColor),
            new ContainerEntry(BlockEntityType.ENDER_CHEST, enderChests, enderChestColor),
            new ContainerEntry(BlockEntityType.BARREL, barrels, barrelColor),
            new ContainerEntry(BlockEntityType.SHULKER_BOX, shulkerBoxes, shulkerBoxColor),
            new ContainerEntry(BlockEntityType.FURNACE, furnaces, furnaceColor),
            new ContainerEntry(BlockEntityType.BLAST_FURNACE, blastFurnaces, blastFurnaceColor),
            new ContainerEntry(BlockEntityType.SMOKER, smokers, smokerColor),
            new ContainerEntry(BlockEntityType.BREWING_STAND, brewingStands, brewingStandColor),
            new ContainerEntry(BlockEntityType.HOPPER, hoppers, hopperColor),
            new ContainerEntry(BlockEntityType.DISPENSER, dispensers, dispenserColor),
            new ContainerEntry(BlockEntityType.DROPPER, droppers, dropperColor),
            new ContainerEntry(BlockEntityType.CRAFTER, crafters, crafterColor),
            new ContainerEntry(BlockEntityType.DECORATED_POT, decoratedPots, decoratedPotColor),
            new ContainerEntry(BlockEntityType.CHISELED_BOOKSHELF, chiseledBookshelves, chiseledBookshelfColor),
            new ContainerEntry(BlockEntityType.SHELF, shelves, shelfColor)
    );

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!canRender() || !shouldRenderGeometry()) {
            return;
        }

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        int range = renderRange.getValue();
        double maxDistanceSqr = (double) range * range;
        int chunkRange = Math.max(1, (int) Math.ceil(range / 16.0D));
        int playerChunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        LongSet occupiedContainers = new LongOpenHashSet();
        Map<Integer, List<BlockPos>> containersByColor = new HashMap<>();

        for (int chunkX = playerChunkX - chunkRange; chunkX <= playerChunkX + chunkRange; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRange; chunkZ <= playerChunkZ + chunkRange; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    collectContainer(player, blockEntity, maxDistanceSqr, occupiedContainers, containersByColor);
                }
            }
        }

        renderMergedContainers(event.getPoseStack(), cameraPos, occupiedContainers, containersByColor);
    }

    private boolean canRender() {
        return mc.player != null && mc.level != null;
    }

    private boolean shouldRenderGeometry() {
        return fill.getValue() && fillAlpha.getValue() > 0 || outline.getValue() && outlineAlpha.getValue() > 0;
    }

    private void collectContainer(
            LocalPlayer player,
            BlockEntity blockEntity,
            double maxDistanceSqr,
            LongSet occupiedContainers,
            Map<Integer, List<BlockPos>> containersByColor
    ) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }

        ContainerEntry entry = entryFor(blockEntity.getType());
        if (entry == null || !entry.enabled().getValue()) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        if (!isInRange(player, pos, maxDistanceSqr)) {
            return;
        }

        Color color = entry.color().getValue();
        int colorKey = Render3DUtility.rgb(color.getRed(), color.getGreen(), color.getBlue());
        occupiedContainers.add(pos.asLong());
        containersByColor.computeIfAbsent(colorKey, ignored -> new ArrayList<>()).add(pos);
    }

    private void renderMergedContainers(
            PoseStack poseStack,
            Vec3 cameraPos,
            LongSet occupiedContainers,
            Map<Integer, List<BlockPos>> containersByColor
    ) {
        if (occupiedContainers.isEmpty() || containersByColor.isEmpty()) {
            return;
        }

        boolean renderFill = fill.getValue() && fillAlpha.getValue() > 0;
        boolean renderOutline = outline.getValue() && outlineAlpha.getValue() > 0;
        double inflate = boxInflate.getValue();

        for (Map.Entry<Integer, List<BlockPos>> entry : containersByColor.entrySet()) {
            Map<FacePlane, Set<Cell2D>> visibleFaces = collectVisibleFaces(entry.getValue(), occupiedContainers, cameraPos);
            if (visibleFaces.isEmpty()) {
                continue;
            }

            if (renderFill) {
                Render3DUtility.renderFilledQuadsNoDepth(
                        poseStack,
                        buildFillQuads(visibleFaces, inflate),
                        Render3DUtility.withAlpha(entry.getKey(), fillAlpha.getValue())
                );
            }
            if (renderOutline) {
                Render3DUtility.renderLineSegmentsNoDepth(
                        poseStack,
                        buildOutlineLines(visibleFaces, inflate),
                        Render3DUtility.withAlpha(entry.getKey(), outlineAlpha.getValue())
                );
            }
        }
    }

    private ContainerEntry entryFor(BlockEntityType<?> type) {
        for (ContainerEntry entry : containers) {
            if (entry.type() == type) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isInRange(LocalPlayer player, BlockPos pos, double maxDistanceSqr) {
        double x = pos.getX() + 0.5D - player.getX();
        double y = pos.getY() + 0.5D - player.getY();
        double z = pos.getZ() + 0.5D - player.getZ();
        return x * x + y * y + z * z <= maxDistanceSqr;
    }

    private static Map<FacePlane, Set<Cell2D>> collectVisibleFaces(
            Collection<BlockPos> positions,
            LongSet occupiedContainers,
            Vec3 cameraPos
    ) {
        Map<FacePlane, Set<Cell2D>> visibleFaces = new HashMap<>();
        for (BlockPos pos : positions) {
            for (Direction direction : FACE_DIRECTIONS) {
                if (hasNeighbor(pos, direction, occupiedContainers) || !isFaceCameraVisible(pos, direction, cameraPos)) {
                    continue;
                }

                FaceCell cell = faceCell(pos, direction);
                visibleFaces.computeIfAbsent(new FacePlane(direction, cell.plane()), ignored -> new HashSet<>())
                        .add(new Cell2D(cell.u(), cell.v()));
            }
        }
        return visibleFaces;
    }

    private static boolean hasNeighbor(BlockPos pos, Direction direction, LongSet occupiedContainers) {
        return occupiedContainers.contains(BlockPos.asLong(
                pos.getX() + direction.getStepX(),
                pos.getY() + direction.getStepY(),
                pos.getZ() + direction.getStepZ()
        ));
    }

    private static boolean isFaceCameraVisible(BlockPos pos, Direction direction, Vec3 cameraPos) {
        return switch (direction) {
            case DOWN -> cameraPos.y < pos.getY();
            case UP -> cameraPos.y > pos.getY() + 1.0D;
            case NORTH -> cameraPos.z < pos.getZ();
            case SOUTH -> cameraPos.z > pos.getZ() + 1.0D;
            case WEST -> cameraPos.x < pos.getX();
            case EAST -> cameraPos.x > pos.getX() + 1.0D;
        };
    }

    private static FaceCell faceCell(BlockPos pos, Direction direction) {
        return switch (direction) {
            case DOWN -> new FaceCell(pos.getY(), pos.getX(), pos.getZ());
            case UP -> new FaceCell(pos.getY() + 1, pos.getX(), pos.getZ());
            case NORTH -> new FaceCell(pos.getZ(), pos.getX(), pos.getY());
            case SOUTH -> new FaceCell(pos.getZ() + 1, pos.getX(), pos.getY());
            case WEST -> new FaceCell(pos.getX(), pos.getY(), pos.getZ());
            case EAST -> new FaceCell(pos.getX() + 1, pos.getY(), pos.getZ());
        };
    }

    private static List<Render3DUtility.Quad> buildFillQuads(Map<FacePlane, Set<Cell2D>> visibleFaces, double inflate) {
        List<Render3DUtility.Quad> quads = new ArrayList<>();
        for (Map.Entry<FacePlane, Set<Cell2D>> entry : visibleFaces.entrySet()) {
            for (FaceRect rect : mergeCells(entry.getKey(), entry.getValue())) {
                quads.add(toQuad(rect, inflate));
            }
        }
        return quads;
    }

    private static List<FaceRect> mergeCells(FacePlane plane, Set<Cell2D> cells) {
        Set<Cell2D> remaining = new HashSet<>(cells);
        List<FaceRect> rectangles = new ArrayList<>();

        while (!remaining.isEmpty()) {
            Cell2D start = firstCell(remaining);
            int width = 1;
            while (remaining.contains(new Cell2D(start.u() + width, start.v()))) {
                width++;
            }

            int height = 1;
            boolean canGrow = true;
            while (canGrow) {
                for (int x = 0; x < width; x++) {
                    if (!remaining.contains(new Cell2D(start.u() + x, start.v() + height))) {
                        canGrow = false;
                        break;
                    }
                }
                if (canGrow) {
                    height++;
                }
            }

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    remaining.remove(new Cell2D(start.u() + x, start.v() + y));
                }
            }

            rectangles.add(new FaceRect(plane.direction(), plane.plane(), start.u(), start.v(), width, height));
        }

        return rectangles;
    }

    private static Cell2D firstCell(Set<Cell2D> cells) {
        Cell2D first = null;
        for (Cell2D cell : cells) {
            if (first == null || cell.v() < first.v() || cell.v() == first.v() && cell.u() < first.u()) {
                first = cell;
            }
        }
        if (first == null) {
            throw new IllegalStateException("cells is empty");
        }
        return first;
    }

    private static Render3DUtility.Quad toQuad(FaceRect rect, double inflate) {
        return switch (rect.direction()) {
            case DOWN -> {
                double y = shiftedPlane(rect, inflate);
                double minX = rect.u() - inflate;
                double maxX = rect.u() + rect.width() + inflate;
                double minZ = rect.v() - inflate;
                double maxZ = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, minX, y, maxZ);
            }
            case UP -> {
                double y = shiftedPlane(rect, inflate);
                double minX = rect.u() - inflate;
                double maxX = rect.u() + rect.width() + inflate;
                double minZ = rect.v() - inflate;
                double maxZ = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(minX, y, minZ, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ);
            }
            case NORTH -> {
                double z = shiftedPlane(rect, inflate);
                double minX = rect.u() - inflate;
                double maxX = rect.u() + rect.width() + inflate;
                double minY = rect.v() - inflate;
                double maxY = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(minX, minY, z, minX, maxY, z, maxX, maxY, z, maxX, minY, z);
            }
            case SOUTH -> {
                double z = shiftedPlane(rect, inflate);
                double minX = rect.u() - inflate;
                double maxX = rect.u() + rect.width() + inflate;
                double minY = rect.v() - inflate;
                double maxY = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(minX, minY, z, maxX, minY, z, maxX, maxY, z, minX, maxY, z);
            }
            case WEST -> {
                double x = shiftedPlane(rect, inflate);
                double minY = rect.u() - inflate;
                double maxY = rect.u() + rect.width() + inflate;
                double minZ = rect.v() - inflate;
                double maxZ = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(x, minY, minZ, x, minY, maxZ, x, maxY, maxZ, x, maxY, minZ);
            }
            case EAST -> {
                double x = shiftedPlane(rect, inflate);
                double minY = rect.u() - inflate;
                double maxY = rect.u() + rect.width() + inflate;
                double minZ = rect.v() - inflate;
                double maxZ = rect.v() + rect.height() + inflate;
                yield new Render3DUtility.Quad(x, minY, minZ, x, maxY, minZ, x, maxY, maxZ, x, minY, maxZ);
            }
        };
    }

    private static double shiftedPlane(FaceRect rect, double inflate) {
        return rect.plane() + faceSign(rect.direction()) * inflate;
    }

    private static List<Render3DUtility.LineSegment> buildOutlineLines(Map<FacePlane, Set<Cell2D>> visibleFaces, double inflate) {
        Map<LineAxis, List<DoubleInterval>> intervalsByLine = new HashMap<>();
        for (Map.Entry<FacePlane, Set<Cell2D>> entry : visibleFaces.entrySet()) {
            for (Edge2D edge : collectPlaneOutlineEdges(entry.getValue())) {
                addOutlineEdge(intervalsByLine, entry.getKey(), edge, inflate);
            }
        }
        return buildMergedLineSegments(intervalsByLine);
    }

    private static Collection<Edge2D> collectPlaneOutlineEdges(Set<Cell2D> cells) {
        Map<Edge2DKey, Edge2D> edges = new HashMap<>();
        for (Cell2D cell : cells) {
            toggleEdge(edges, true, cell.v(), cell.u(), -1);
            toggleEdge(edges, true, cell.v() + 1, cell.u(), 1);
            toggleEdge(edges, false, cell.u(), cell.v(), -1);
            toggleEdge(edges, false, cell.u() + 1, cell.v(), 1);
        }
        return edges.values();
    }

    private static void toggleEdge(Map<Edge2DKey, Edge2D> edges, boolean alongU, int fixed, int start, int side) {
        Edge2DKey key = new Edge2DKey(alongU, fixed, start);
        if (edges.remove(key) == null) {
            edges.put(key, new Edge2D(key, side));
        }
    }

    private static void addOutlineEdge(
            Map<LineAxis, List<DoubleInterval>> intervalsByLine,
            FacePlane plane,
            Edge2D edge,
            double inflate
    ) {
        Edge2DKey key = edge.key();
        double planeCoord = plane.plane() + faceSign(plane.direction()) * inflate;
        double fixedCoord = key.fixed() + edge.side() * inflate;
        double start = key.start() - inflate;
        double end = key.start() + 1.0D + inflate;

        switch (plane.direction()) {
            case DOWN, UP -> {
                if (key.alongU()) {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.X, planeCoord, fixedCoord), start, end);
                } else {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.Z, fixedCoord, planeCoord), start, end);
                }
            }
            case NORTH, SOUTH -> {
                if (key.alongU()) {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.X, fixedCoord, planeCoord), start, end);
                } else {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.Y, fixedCoord, planeCoord), start, end);
                }
            }
            case WEST, EAST -> {
                if (key.alongU()) {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.Y, planeCoord, fixedCoord), start, end);
                } else {
                    addInterval(intervalsByLine, new LineAxis(Axis3D.Z, planeCoord, fixedCoord), start, end);
                }
            }
        }
    }

    private static void addInterval(Map<LineAxis, List<DoubleInterval>> intervalsByLine, LineAxis axis, double start, double end) {
        double min = Math.min(start, end);
        double max = Math.max(start, end);
        intervalsByLine.computeIfAbsent(axis.clean(), ignored -> new ArrayList<>()).add(new DoubleInterval(min, max));
    }

    private static List<Render3DUtility.LineSegment> buildMergedLineSegments(Map<LineAxis, List<DoubleInterval>> intervalsByLine) {
        List<Render3DUtility.LineSegment> lines = new ArrayList<>();
        for (Map.Entry<LineAxis, List<DoubleInterval>> entry : intervalsByLine.entrySet()) {
            List<DoubleInterval> intervals = entry.getValue();
            intervals.sort(Comparator.comparingDouble(DoubleInterval::start).thenComparingDouble(DoubleInterval::end));

            double start = Double.NaN;
            double end = Double.NaN;
            for (DoubleInterval interval : intervals) {
                if (Double.isNaN(start)) {
                    start = interval.start();
                    end = interval.end();
                    continue;
                }

                if (interval.start() <= end + LINE_MERGE_EPSILON) {
                    end = Math.max(end, interval.end());
                } else {
                    addLineSegment(lines, entry.getKey(), start, end);
                    start = interval.start();
                    end = interval.end();
                }
            }

            if (!Double.isNaN(start)) {
                addLineSegment(lines, entry.getKey(), start, end);
            }
        }
        return lines;
    }

    private static void addLineSegment(List<Render3DUtility.LineSegment> lines, LineAxis axis, double start, double end) {
        if (end - start <= LINE_MERGE_EPSILON) {
            return;
        }

        switch (axis.axis()) {
            case X -> lines.add(new Render3DUtility.LineSegment(start, axis.fixedA(), axis.fixedB(), end, axis.fixedA(), axis.fixedB()));
            case Y -> lines.add(new Render3DUtility.LineSegment(axis.fixedA(), start, axis.fixedB(), axis.fixedA(), end, axis.fixedB()));
            case Z -> lines.add(new Render3DUtility.LineSegment(axis.fixedA(), axis.fixedB(), start, axis.fixedA(), axis.fixedB(), end));
        }
    }

    private static int faceSign(Direction direction) {
        return switch (direction) {
            case DOWN, NORTH, WEST -> -1;
            case UP, SOUTH, EAST -> 1;
        };
    }

    private static double cleanZero(double value) {
        return value == 0.0D ? 0.0D : value;
    }

    private record ContainerEntry(BlockEntityType<?> type, BoolValue enabled, ColorValue color) {
    }

    private record FaceCell(int plane, int u, int v) {
    }

    private record FacePlane(Direction direction, int plane) {
    }

    private record Cell2D(int u, int v) {
    }

    private record FaceRect(Direction direction, int plane, int u, int v, int width, int height) {
    }

    private record Edge2DKey(boolean alongU, int fixed, int start) {
    }

    private record Edge2D(Edge2DKey key, int side) {
    }

    private enum Axis3D {
        X,
        Y,
        Z
    }

    private record LineAxis(Axis3D axis, double fixedA, double fixedB) {
        private LineAxis clean() {
            return new LineAxis(axis, cleanZero(fixedA), cleanZero(fixedB));
        }
    }

    private record DoubleInterval(double start, double end) {
    }
}
