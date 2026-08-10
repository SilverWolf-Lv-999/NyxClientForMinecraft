package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MathUtility;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(name = "nyxclient.module.placerender.name", description = "nyxclient.module.placerender.description", category = Category.VISUAL)
public class PlaceRender extends Module {
    public static final PlaceRender INSTANCE = new PlaceRender();

    public final IntValue fadeTime = ValueBuild.intSetting("fade time", 500, 0, 3000, 25, this);
    public final IntValue timeout = ValueBuild.intSetting("timeout", 500, 0, 3000, 25, this);
    public final BoolValue placedFill = ValueBuild.boolSetting("placed fill", true, this);
    public final ColorValue placedFillColor = ValueBuild.colorSetting(
            "placed fill color",
            new Color(255, 255, 255, 100),
            () -> placedFill.getValue(),
            this
    );
    public final BoolValue placedOutline = ValueBuild.boolSetting("placed outline", true, this);
    public final ColorValue placedOutlineColor = ValueBuild.colorSetting(
            "placed outline color",
            new Color(255, 255, 255, 255),
            () -> placedOutline.getValue(),
            this
    );
    public final BoolValue attemptFill = ValueBuild.boolSetting("attempt fill", true, this);
    public final ColorValue attemptFillColor = ValueBuild.colorSetting(
            "attempt fill color",
            new Color(255, 119, 119, 157),
            () -> attemptFill.getValue(),
            this
    );
    public final BoolValue attemptOutline = ValueBuild.boolSetting("attempt outline", true, this);
    public final ColorValue attemptOutlineColor = ValueBuild.colorSetting(
            "attempt outline color",
            new Color(178, 178, 178, 255),
            () -> attemptOutline.getValue(),
            this
    );
    public final BoolValue noFail = ValueBuild.boolSetting("no fail", false, this);
    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.ALL, this);
    public final EnumValue<Easing> easing = ValueBuild.enumSetting("easing", Easing.CUBIC_IN_OUT, this);

    private final Map<Long, PlaceEntry> placements = new ConcurrentHashMap<>();
    private volatile int serverHotbarSlot = Inventory.NOT_FOUND_INDEX;

    @Override
    public void onDisable() {
        placements.clear();
        serverHotbarSlot = Inventory.NOT_FOUND_INDEX;
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
            serverHotbarSlot = setCarriedItemPacket.getSlot();
            return;
        }

        if (!(packet instanceof ServerboundUseItemOnPacket useItemPacket) || mc.player == null || mc.level == null) {
            return;
        }

        ItemStack stack = stackFor(useItemPacket.getHand());
        if (!(stack.getItem() instanceof BlockItem)) {
            return;
        }

        BlockHitResult hitResult = useItemPacket.getHitResult();
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = mc.level.getBlockState(clickedPos);
        BlockPos placePos = clickedState.canBeReplaced()
                ? clickedPos
                : clickedPos.relative(hitResult.getDirection());

        placements.put(placePos.asLong(), new PlaceEntry(placePos.immutable(), System.currentTimeMillis()));
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null) {
            placements.clear();
            return;
        }

        long now = System.currentTimeMillis();
        PoseStack poseStack = event.getPoseStack();
        for (Map.Entry<Long, PlaceEntry> mapEntry : placements.entrySet()) {
            PlaceEntry entry = mapEntry.getValue();
            if (entry == null || renderEntry(poseStack, entry, now)) {
                placements.remove(mapEntry.getKey(), entry);
            }
        }
    }

    private ItemStack stackFor(InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return mc.player.getOffhandItem();
        }

        int slot = serverHotbarSlot;
        if (!Inventory.isHotbarSlot(slot)) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        return Inventory.isHotbarSlot(slot) ? mc.player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    private boolean renderEntry(PoseStack poseStack, PlaceEntry entry, long now) {
        if (entry.fadeStartedAt == 0L) {
            if (!noFail.getValue() && mc.level.isEmptyBlock(entry.pos)) {
                if (now - entry.createdAt > timeout.getValue()) {
                    return true;
                }

                renderAttempt(poseStack, entry.pos);
                return false;
            }
            entry.fadeStartedAt = now;
        }

        int duration = fadeTime.getValue();
        float rawProgress = duration <= 0 ? 1.0F : MathUtility.clamp((float)(now - entry.fadeStartedAt) / duration, 0.0F, 1.0F);
        if (rawProgress >= 1.0F) {
            return true;
        }

        float progress = ease(rawProgress);
        double opacity = mode.is(Mode.FADE) || mode.is(Mode.ALL) ? 1.0D - progress : 1.0D;
        double shrink = mode.is(Mode.SHRINK) || mode.is(Mode.ALL) ? progress * 0.5D : 0.0D;
        AABB box = deflate(new AABB(entry.pos), shrink);

        if (placedFill.getValue()) {
            Render3DUtility.renderFilledBoxNoDepth(poseStack, box, withOpacity(placedFillColor.getValue(), opacity));
        }
        if (placedOutline.getValue()) {
            Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, withOpacity(placedOutlineColor.getValue(), opacity));
        }
        return false;
    }

    private void renderAttempt(PoseStack poseStack, BlockPos pos) {
        AABB box = new AABB(pos);
        if (attemptFill.getValue()) {
            Render3DUtility.renderFilledBoxNoDepth(poseStack, box, attemptFillColor.getValue());
        }
        if (attemptOutline.getValue()) {
            Render3DUtility.renderOutlineBoxNoDepth(poseStack, box, attemptOutlineColor.getValue());
        }
    }

    private float ease(float progress) {
        return switch (easing.getValue()) {
            case LINEAR -> progress;
            case CUBIC_IN_OUT -> MathUtility.easeInOutCubic(progress);
        };
    }

    private static AABB deflate(AABB box, double amount) {
        return new AABB(
                box.minX + amount,
                box.minY + amount,
                box.minZ + amount,
                box.maxX - amount,
                box.maxY - amount,
                box.maxZ - amount
        );
    }

    private static Color withOpacity(Color color, double opacity) {
        int alpha = (int)Math.round(color.getAlpha() * Math.max(0.0D, Math.min(1.0D, opacity)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public enum Mode {
        FADE,
        SHRINK,
        ALL
    }

    public enum Easing {
        LINEAR,
        CUBIC_IN_OUT
    }

    private static final class PlaceEntry {
        private final BlockPos pos;
        private final long createdAt;
        private long fadeStartedAt;

        private PlaceEntry(BlockPos pos, long createdAt) {
            this.pos = pos;
            this.createdAt = createdAt;
        }
    }
}
