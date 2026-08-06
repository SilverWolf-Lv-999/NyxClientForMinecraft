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
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.tracers.name", description = "nyxclient.module.tracers.description", category = Category.VISUAL)
public class Tracers extends Module {
    public static final Tracers INSTANCE = new Tracers();

    public final IntValue renderRange = ValueBuild.intSetting("render range", 128, 16, 512, 8, this);
    public final BoolValue items = ValueBuild.boolSetting("items", true, this);
    public final ColorValue itemColor = ValueBuild.colorSetting("item color", new Color(255, 255, 255, 100), this);
    public final BoolValue players = ValueBuild.boolSetting("players", true, this);
    public final ColorValue playerColor = ValueBuild.colorSetting("player color", new Color(255, 255, 255, 100), this);
    public final BoolValue chests = ValueBuild.boolSetting("chests", false, this);
    public final ColorValue chestColor = ValueBuild.colorSetting("chest color", new Color(255, 255, 255, 100), this);
    public final BoolValue enderChests = ValueBuild.boolSetting("ender chests", false, this);
    public final ColorValue enderChestColor = ValueBuild.colorSetting("ender chest color", new Color(255, 100, 255, 100), this);
    public final BoolValue shulkerBoxes = ValueBuild.boolSetting("shulker boxes", false, this);
    public final ColorValue shulkerBoxColor = ValueBuild.colorSetting("shulker box color", new Color(15, 255, 255, 100), this);

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        int range = renderRange.getValue();
        double maxDistanceSqr = (double)range * range;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f look = camera.rotation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vec3 origin = camera.position().add(look.x * 0.2D, look.y * 0.2D, look.z * 0.2D);
        Map<Integer, List<Render3DUtility.LineSegment>> linesByColor = new HashMap<>();

        collectEntityLines(level, player, origin, event.getPartialTick(), range, maxDistanceSqr, linesByColor);
        collectBlockEntityLines(level, player, origin, range, maxDistanceSqr, linesByColor);
        renderLines(event.getPoseStack(), linesByColor);
    }

    private void collectEntityLines(
            ClientLevel level,
            LocalPlayer player,
            Vec3 origin,
            float partialTick,
            int range,
            double maxDistanceSqr,
            Map<Integer, List<Render3DUtility.LineSegment>> linesByColor
    ) {
        if (!items.getValue() && !players.getValue()) {
            return;
        }

        AABB searchBox = player.getBoundingBox().inflate(range);
        List<Entity> entities = level.getEntities(
                player,
                searchBox,
                entity -> entity != null
                        && !entity.isRemoved()
                        && ((items.getValue() && entity instanceof ItemEntity)
                        || (players.getValue() && entity instanceof Player))
        );
        for (Entity entity : entities) {
            Vec3 target = interpolatedEntityCenter(entity, partialTick);
            if (player.getEyePosition().distanceToSqr(target) > maxDistanceSqr) {
                continue;
            }

            Color color = entity instanceof ItemEntity ? itemColor.getValue() : playerColor.getValue();
            addLine(linesByColor, origin, target, color);
        }
    }

    private void collectBlockEntityLines(
            ClientLevel level,
            LocalPlayer player,
            Vec3 origin,
            int range,
            double maxDistanceSqr,
            Map<Integer, List<Render3DUtility.LineSegment>> linesByColor
    ) {
        if (!chests.getValue() && !enderChests.getValue() && !shulkerBoxes.getValue()) {
            return;
        }

        int chunkRange = Math.max(1, (int)Math.ceil(range / 16.0D));
        int playerChunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        for (int chunkX = playerChunkX - chunkRange; chunkX <= playerChunkX + chunkRange; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRange; chunkZ <= playerChunkZ + chunkRange; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    collectBlockEntityLine(player, origin, maxDistanceSqr, blockEntity, linesByColor);
                }
            }
        }
    }

    private void collectBlockEntityLine(
            LocalPlayer player,
            Vec3 origin,
            double maxDistanceSqr,
            BlockEntity blockEntity,
            Map<Integer, List<Render3DUtility.LineSegment>> linesByColor
    ) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }

        Color color = colorFor(blockEntity);
        if (color == null) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        Vec3 target = pos.getCenter();
        if (player.getEyePosition().distanceToSqr(target) <= maxDistanceSqr) {
            addLine(linesByColor, origin, target, color);
        }
    }

    private Color colorFor(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        if (type == BlockEntityType.CHEST && chests.getValue()) {
            return chestColor.getValue();
        }
        if (type == BlockEntityType.ENDER_CHEST && enderChests.getValue()) {
            return enderChestColor.getValue();
        }
        if (type == BlockEntityType.SHULKER_BOX && shulkerBoxes.getValue()) {
            return shulkerBoxColor.getValue();
        }
        return null;
    }

    private static Vec3 interpolatedEntityCenter(Entity entity, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick + entity.getBbHeight() * 0.5D;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        return new Vec3(x, y, z);
    }

    private static void addLine(
            Map<Integer, List<Render3DUtility.LineSegment>> linesByColor,
            Vec3 origin,
            Vec3 target,
            Color color
    ) {
        int lineColor = Render3DUtility.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        linesByColor.computeIfAbsent(lineColor, ignored -> new ArrayList<>()).add(
                new Render3DUtility.LineSegment(origin.x, origin.y, origin.z, target.x, target.y, target.z)
        );
    }

    private static void renderLines(PoseStack poseStack, Map<Integer, List<Render3DUtility.LineSegment>> linesByColor) {
        for (Map.Entry<Integer, List<Render3DUtility.LineSegment>> entry : linesByColor.entrySet()) {
            Render3DUtility.renderLineSegmentsNoDepth(poseStack, entry.getValue(), entry.getKey());
        }
    }
}
