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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.containeresp.name", description = "nyxclient.module.containeresp.description", category = Category.VISUAL)
public class ContainerESP extends Module {
    public static final ContainerESP INSTANCE = new ContainerESP();

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
        if (!canRender()) {
            return;
        }

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        int range = renderRange.getValue();
        double maxDistanceSqr = (double) range * range;
        int chunkRange = Math.max(1, (int) Math.ceil(range / 16.0D));
        int playerChunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());

        for (int chunkX = playerChunkX - chunkRange; chunkX <= playerChunkX + chunkRange; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRange; chunkZ <= playerChunkZ + chunkRange; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    renderContainer(event.getPoseStack(), player, blockEntity, maxDistanceSqr);
                }
            }
        }
    }

    private boolean canRender() {
        return mc.player != null && mc.level != null;
    }

    private void renderContainer(PoseStack poseStack, LocalPlayer player, BlockEntity blockEntity, double maxDistanceSqr) {
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

        AABB box = new AABB(pos).inflate(boxInflate.getValue());
        Color color = entry.color().getValue();
        if (fill.getValue()) {
            Render3DUtility.renderFilledBoxNoDepth(poseStack, box, withAlpha(color, fillAlpha.getValue()));
        }
        if (outline.getValue()) {
            Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, withAlpha(color, outlineAlpha.getValue()));
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

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private record ContainerEntry(BlockEntityType<?> type, BoolValue enabled, ColorValue color) {
    }
}
