package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.airplace.name", description = "nyxclient.module.airplace.description", category = Category.PLAYER)
public final class AirPlace extends Module {
    public static final AirPlace INSTANCE = new AirPlace();

    public final BoolValue render = ValueBuild.boolSetting("render", true, this);
    public final BoolValue customRange = ValueBuild.boolSetting("custom range", false, this);
    public final DoubleValue range = ValueBuild.doubleSetting(
            "range",
            5.0D,
            0.0D,
            6.0D,
            0.1D,
            () -> customRange.getValue(),
            this
    );
    public final ColorValue sideColor = ValueBuild.colorSetting("side color", new Color(204, 0, 0, 10), this);
    public final ColorValue lineColor = ValueBuild.colorSetting("line color", new Color(204, 0, 0, 255), this);

    private AirPlace() {
    }

    @EventTarget
    public void onStartUseItem(StartUseItemEvent event) {
        BlockHitResult targetHitResult = findTargetHitResult(event.getHitResult());
        if (targetHitResult != null) {
            event.setHitResult(targetHitResult);
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!render.getValue()) {
            return;
        }

        BlockHitResult targetHitResult = findTargetHitResult(mc.hitResult);
        if (targetHitResult != null) {
            Render3DUtility.renderBox(
                    event.getPoseStack(),
                    new net.minecraft.world.phys.AABB(targetHitResult.getBlockPos()),
                    sideColor.getValue(),
                    lineColor.getValue()
            );
        }
    }

    private BlockHitResult findTargetHitResult(HitResult currentHitResult) {
        if (isNull()
                || mc.getCameraEntity() == null
                || currentHitResult == null
                || currentHitResult.getType() != HitResult.Type.MISS
                || !hasPlaceableItem()) {
            return null;
        }

        double placeRange = customRange.getValue() ? range.getValue() : mc.player.blockInteractionRange();
        HitResult raycastResult = mc.getCameraEntity().pick(placeRange, 0.0F, false);
        if (!(raycastResult instanceof BlockHitResult raycastHitResult)) {
            return null;
        }

        BlockPos blockPos = raycastHitResult.getBlockPos();
        if (!mc.level.getBlockState(blockPos).canBeReplaced()) {
            return null;
        }

        Direction direction = mc.player.getDirection().getOpposite();
        return new BlockHitResult(Vec3.atCenterOf(blockPos), direction, blockPos, false);
    }

    private boolean hasPlaceableItem() {
        return isPlaceable(mc.player.getMainHandItem()) || isPlaceable(mc.player.getOffhandItem());
    }

    private boolean isPlaceable(ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem
                || itemStack.getItem() instanceof SpawnEggItem
                || itemStack.getItem() instanceof FireworkRocketItem
                || itemStack.getItem() instanceof ArmorStandItem;
    }
}
