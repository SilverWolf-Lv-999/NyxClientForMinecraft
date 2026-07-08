package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.elytrafly.name", description = "Controls elytra flight speed and uses fireworks in Grim mode", category = Category.MOVEMENT)
public class ElytraFly extends Module {
    public static final ElytraFly INSTANCE = new ElytraFly();

    private static final int GRIM_FALL_FLYING_READY_TICKS = 3;
    private static final int GRIM_RESTORE_SLOT_DELAY_TICKS = 1;

    public final BoolValue onlyOnPreeSpace = ValueBuild.boolSetting("on space press", false, this);

    public final EnumValue<FlyType> flyType = ValueBuild.enumSetting("fly type", FlyType.NORMAL, this);

    public final DoubleValue flySpeed = ValueBuild.doubleSetting("fly speed", 1.0, 0.1, 10.0, 0.1, ()-> flyType.getValue() == FlyType.NORMAL, this);

    public final BoolValue shouldDown = ValueBuild.boolSetting("should down", false, ()-> flyType.getValue() == FlyType.NORMAL, this);

    public final DoubleValue downFlySpeed = ValueBuild.doubleSetting("down speed", 0.2, 0.1, 1.0, 0.1, ()-> flyType.getValue() == FlyType.NORMAL && shouldDown.getValue(), this);

    public final IntValue fireworkDelay = ValueBuild.intSetting("firework delay", 30, 1, 100, 1, ()-> flyType.getValue() == FlyType.GRIM, this);

    private boolean activeFallFlight;
    private int fireworkDelayTicks;
    private int fallFlyingTicks;
    private int lastFallFlyingTick = -1;
    private FireworkUseStage fireworkUseStage = FireworkUseStage.IDLE;
    private int fireworkOriginalSlot = InventoryUtility.NOT_FOUND;
    private int fireworkSlot = InventoryUtility.NOT_FOUND;
    private int fireworkRestoreSlotDelayTicks;

    @Override
    public void onEnable() {
        resetFlightState();
    }

    @Override
    public void onDisable() {
        resetFlightState();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!flyType.is(FlyType.NORMAL) || !canRun()) {
            return;
        }

        applyNormalVelocity();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (!canRun()) {
            resetFireworkUseState(true);
            return;
        }

        if (flyType.is(FlyType.NORMAL)) {
            resetFireworkUseState(true);
            keepFallFlying();
        } else {
            useFireworkRocket();
        }
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (!flyType.is(FlyType.GRIM) || fireworkUseStage != FireworkUseStage.USE) {
            return;
        }

        if (!canRun() || !canUseFireworkRocket()) {
            resetFireworkUseState(true);
            return;
        }

        if (useQueuedFireworkRocket()) {
            event.setCancelled(true);
        }
    }

    private boolean canRun() {
        updateActiveFallFlight();
        return activeFallFlight
                && (!onlyOnPreeSpace.getValue() || mc.options.keyJump.isDown());
    }

    private void updateActiveFallFlight() {
        if (mc.player == null || mc.level == null) {
            resetFlightState();
            return;
        }

        if (!isWearingElytra()
                || mc.player.onGround()
                || mc.player.isPassenger()
                || mc.player.isSpectator()
                || mc.player.getAbilities().flying
                || mc.player.isInWater()
                || mc.player.isInLava()) {
            resetFlightState();
            return;
        }

        if (mc.player.isFallFlying()) {
            activeFallFlight = true;
            if (lastFallFlyingTick != mc.player.tickCount) {
                fallFlyingTicks++;
                lastFallFlyingTick = mc.player.tickCount;
            }
        } else {
            fallFlyingTicks = 0;
            lastFallFlyingTick = -1;
        }
    }

    private void resetFlightState() {
        activeFallFlight = false;
        fireworkDelayTicks = 0;
        fallFlyingTicks = 0;
        lastFallFlyingTick = -1;
        resetFireworkUseState(true);
    }

    private boolean isWearingElytra() {
        if (mc.player == null) {
            return false;
        }

        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return chestStack.is(Items.ELYTRA);
    }

    private void applyNormalVelocity() {
        Vec3 direction = horizontalDirection();
        double speed = flySpeed.getValue();
        mc.player.setDeltaMovement(direction.x * speed, verticalVelocity(), direction.z * speed);
    }

    private Vec3 horizontalDirection() {
        int forward = MovingUtility.forwardVal();
        int strafe = MovingUtility.strafeVal();
        if (forward == 0 && strafe == 0) {
            return Vec3.ZERO;
        }

        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        float sinYaw = Mth.sin(yaw);
        float cosYaw = Mth.cos(yaw);
        Vec3 direction = new Vec3(strafe * cosYaw - forward * sinYaw, 0.0D, forward * cosYaw + strafe * sinYaw);
        return direction.lengthSqr() > 1.0E-6D ? direction.normalize() : Vec3.ZERO;
    }

    private double verticalVelocity() {
        if (mc.options.keyJump.isDown()) {
            return flySpeed.getValue();
        }

        if (mc.options.keyShift.isDown() || shouldDown.getValue()) {
            return -downFlySpeed.getValue();
        }

        return 0.0D;
    }

    private void keepFallFlying() {
        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));

        if (!mc.player.isFallFlying()) {
            mc.player.startFallFlying();
        }
    }

    private void useFireworkRocket() {
        if (!canUseFireworkRocket()) {
            resetFireworkUseState(true);
            return;
        }

        if (fallFlyingTicks < GRIM_FALL_FLYING_READY_TICKS) {
            return;
        }

        switch (fireworkUseStage) {
            case IDLE -> queueFireworkRocket();
            case USE -> {
                if (InventoryUtility.getSelectedHotbarSlot() != fireworkSlot) {
                    resetFireworkUseState(false);
                }
            }
            case RESTORE -> restoreQueuedFireworkSlot();
        }
    }

    private boolean canUseFireworkRocket() {
        return mc.screen == null && mc.gameMode != null && mc.player.isFallFlying();
    }

    private void queueFireworkRocket() {
        if (fireworkDelayTicks > 0) {
            fireworkDelayTicks--;
            return;
        }

        int foundFireworkSlot = InventoryUtility.findHotbarSlot(Items.FIREWORK_ROCKET);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (foundFireworkSlot == InventoryUtility.NOT_FOUND || selectedSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        fireworkOriginalSlot = selectedSlot;
        fireworkSlot = foundFireworkSlot;
        if (foundFireworkSlot != selectedSlot) {
            InventoryUtility.selectHotbarSlot(fireworkSlot);
        }
        fireworkUseStage = FireworkUseStage.USE;
    }

    private boolean useQueuedFireworkRocket() {
        if (InventoryUtility.getSelectedHotbarSlot() != fireworkSlot) {
            resetFireworkUseState(false);
            return false;
        }

        if (!useSelectedFireworkRocket()) {
            resetFireworkUseState(true);
            return false;
        }

        fireworkDelayTicks = fireworkDelay.getValue();
        fireworkRestoreSlotDelayTicks = GRIM_RESTORE_SLOT_DELAY_TICKS;
        fireworkUseStage = FireworkUseStage.RESTORE;
        return true;
    }

    private boolean useSelectedFireworkRocket() {
        return mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND) instanceof InteractionResult.Success;
    }

    private void restoreQueuedFireworkSlot() {
        if (fireworkRestoreSlotDelayTicks > 0) {
            fireworkRestoreSlotDelayTicks--;
            return;
        }

        restoreOriginalFireworkSlot();
        resetFireworkUseState(false);
    }

    private void resetFireworkUseState(boolean restoreSelectedSlot) {
        if (restoreSelectedSlot) {
            restoreOriginalFireworkSlot();
        }

        fireworkUseStage = FireworkUseStage.IDLE;
        fireworkOriginalSlot = InventoryUtility.NOT_FOUND;
        fireworkSlot = InventoryUtility.NOT_FOUND;
        fireworkRestoreSlotDelayTicks = 0;
    }

    private void restoreOriginalFireworkSlot() {
        if (fireworkOriginalSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() == fireworkSlot) {
            InventoryUtility.selectHotbarSlot(fireworkOriginalSlot);
        }
    }

    public enum FlyType {
        NORMAL, GRIM
    }

    private enum FireworkUseStage {
        IDLE, USE, RESTORE
    }
}
