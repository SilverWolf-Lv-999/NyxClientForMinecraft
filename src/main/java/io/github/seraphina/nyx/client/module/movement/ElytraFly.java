package io.github.seraphina.nyx.client.module.movement;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.JumpEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

@ModuleInfo(name = "nyxclient.module.elytrafly.name", description = "nyxclient.module.elytrafly.description", category = Category.MOVEMENT)
public class ElytraFly extends Module {
    public static final ElytraFly INSTANCE = new ElytraFly();

    private static final int GRIM_FALL_FLYING_READY_TICKS = 3;
    private static final int GRIM_ARMOR_FALL_FLYING_READY_TICKS = 1;
    private static final int GRIM_RESTORE_SLOT_DELAY_TICKS = 1;
    private static final int SHARED_FLAGS_DATA_ID = 0;
    private static final int FALL_FLYING_FLAG_MASK = 1 << 7;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long GRIM_ARMOR_EQUIP_TIMEOUT_NS = 1_500L * NANOS_PER_MILLISECOND;
    private static final long GRIM_ARMOR_FIREWORK_TIMEOUT_NS = 1_000L * NANOS_PER_MILLISECOND;
    private static final long GRIM_ARMOR_RESTORE_TIMEOUT_NS = 1_500L * NANOS_PER_MILLISECOND;
    private static final long GRIM_ARMOR_FIREWORK_BOOST_NS = 100L * NANOS_PER_MILLISECOND;
    private static final int GRIM_ARMOR_MIN_SWITCH_DELAY_MS = 50;
    private static final double GRIM_ARMOR_ROTATION_SPEED = 180.0D;
    private static final double GRIM_ARMOR_MIN_FIREWORK_SPEED = 1.0D;
    private static final double GRIM_ARMOR_BOOST_COLLISION_CHECK_DISTANCE = 0.25D;
    private static final double NORMAL_ASCEND_ACCELERATION = 0.08D;
    private static final double NORMAL_DESCEND_ACCELERATION = 0.04D;

    public final BoolValue onlyOnPreeSpace = ValueBuild.boolSetting("on space press", false, this);

    public final EnumValue<FlyType> flyType = ValueBuild.enumSetting("fly type", FlyType.NORMAL, this);

    public final DoubleValue flySpeed = ValueBuild.doubleSetting("fly speed", 1.0, 0.1, 10.0, 0.1, ()-> flyType.getValue() == FlyType.NORMAL, this);

    public final BoolValue shouldDown = ValueBuild.boolSetting("should down", false, ()-> flyType.getValue() == FlyType.NORMAL, this);

    public final DoubleValue downFlySpeed = ValueBuild.doubleSetting("down speed", 0.2, 0.1, 1.0, 0.1, ()-> flyType.getValue() == FlyType.NORMAL && shouldDown.getValue(), this);

    public final IntValue startPacketDelay = ValueBuild.intSetting("start packet delay", 5, 2, 40, 1, ()-> flyType.getValue() == FlyType.NORMAL, this);

    public final IntValue fireworkDelay = ValueBuild.intSetting("firework delay", 30, 1, 100, 1, ()-> flyType.getValue() == FlyType.GRIM, this);

    public final DoubleValue fireworkSpeed = ValueBuild.doubleSetting("firework speed", 1.0D, 1.0D, 5.0D, 0.1D, ()-> flyType.getValue() == FlyType.GRIM_ARMOR_FLY, this);

    public final IntValue switchDelayMs = ValueBuild.intSetting("switch delay ms", 50, 50, 500, 10, ()-> flyType.getValue() == FlyType.GRIM_ARMOR_FLY, this);

    public final IntValue armorCycleDelayMs = ValueBuild.intSetting("armor cycle delay ms", 50, 50, 1000, 10, ()-> flyType.getValue() == FlyType.GRIM_ARMOR_FLY, this);

    private boolean activeFallFlight;
    private int startPacketDelayTicks;
    private int fireworkDelayTicks;
    private int fallFlyingTicks;
    private int lastFallFlyingTick = -1;
    private FireworkUseStage fireworkUseStage = FireworkUseStage.IDLE;
    private int fireworkOriginalSlot = InventoryUtility.NOT_FOUND;
    private int fireworkSlot = InventoryUtility.NOT_FOUND;
    private int fireworkRestoreSlotDelayTicks;
    private GrimArmorFlyStage grimArmorFlyStage = GrimArmorFlyStage.IDLE;
    private long grimArmorFlyStageStartTimeNs;
    private long grimArmorFlyNextActionTimeNs;
    private long grimArmorFlyNextCycleTimeNs;
    private long grimArmorFlyFallFlyingStartTimeNs;
    private long grimArmorFlyBoostEndTimeNs;
    private long grimArmorFlyBoostPendingUntilTimeNs;
    private int grimArmorFlyElytraSlot = InventoryUtility.NOT_FOUND;
    private int grimArmorFlyFireworkSlot = InventoryUtility.NOT_FOUND;
    private int grimArmorFlyOriginalSelectedSlot = InventoryUtility.NOT_FOUND;
    private ItemStack grimArmorFlyOriginalChestStack = ItemStack.EMPTY;
    private Vector2f grimArmorFlyRotations;
    private float grimArmorFlyBaseYaw;
    private boolean grimArmorFlyControllingRotations;
    private int grimArmorFlyFallFlyingTicks;
    private int grimArmorFlyLastFallFlyingTick = -1;
    private boolean grimArmorFlyReleasedJumpForGlide;
    private boolean grimArmorFlyReleaseJumpApplied;
    private boolean grimArmorFlyPressJumpForGlide;
    private boolean grimArmorFlySuppressMovementInput;
    private final AtomicBoolean grimArmorFlyRestartRequested = new AtomicBoolean();

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
        if (flyType.is(FlyType.NORMAL)) {
            if (canRun()) {
                applyNormalVelocity();
            }
            return;
        }

        if (flyType.is(FlyType.GRIM_ARMOR_FLY)) {
            resetFireworkUseState(true);
            runGrimArmorFly(System.nanoTime());
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!flyType.is(FlyType.GRIM_ARMOR_FLY)
                || !canRunGrimArmorFly()
                || !mc.player.isFallFlying()
                || !(event.getPacket() instanceof ClientboundSetEntityDataPacket packet)
                || packet.id() != mc.player.getId()) {
            return;
        }

        for (int i = 0; i < packet.packedItems().size(); i++) {
            SynchedEntityData.DataValue<?> dataValue = packet.packedItems().get(i);
            if (dataValue.id() != SHARED_FLAGS_DATA_ID
                    || dataValue.serializer() != EntityDataSerializers.BYTE
                    || !(dataValue.value() instanceof Byte flags)
                    || (flags & FALL_FLYING_FLAG_MASK) != 0) {
                continue;
            }

            ArrayList<SynchedEntityData.DataValue<?>> patchedItems = new ArrayList<>(packet.packedItems());
            patchedItems.set(i, new SynchedEntityData.DataValue<>(
                    dataValue.id(),
                    EntityDataSerializers.BYTE,
                    (byte) (flags | FALL_FLYING_FLAG_MASK)
            ));
            event.setPacket(new ClientboundSetEntityDataPacket(packet.id(), patchedItems));
            grimArmorFlyRestartRequested.set(true);
            return;
        }
    }

    @EventTarget(4)
    public void onMoveInput(MoveInputEvent event) {
        if (!flyType.is(FlyType.GRIM_ARMOR_FLY) || grimArmorFlyStage == GrimArmorFlyStage.IDLE) {
            return;
        }

        event.setSprint(false);
        stopSprinting();
        if (grimArmorFlySuppressMovementInput) {
            neutralizeGrimArmorFlyMoveInput(event);
            return;
        }

        alignGrimArmorFlyMoveInput(event);

        if (grimArmorFlyStage != GrimArmorFlyStage.START_FALL_FLYING
                && grimArmorFlyStage != GrimArmorFlyStage.WAIT_FALL_FLYING) {
            return;
        }

        if (grimArmorFlyPressJumpForGlide) {
            event.setJump(true);
        } else if (grimArmorFlyReleasedJumpForGlide) {
            event.setJump(false);
            grimArmorFlyReleaseJumpApplied = true;
        }
    }

    @EventTarget(4)
    public void onStrafe(StrafeEvent event) {
        if (shouldLockGrimArmorFlyMovementYaw()) {
            event.setYaw(grimArmorFlyRotations.x);
        }
    }

    @EventTarget(4)
    public void onJump(JumpEvent event) {
        if (shouldLockGrimArmorFlyMovementYaw()) {
            event.setYaw(grimArmorFlyRotations.x);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (flyType.is(FlyType.NORMAL)) {
            if (!canRun()) {
                resetFireworkUseState(true);
                resetGrimArmorFlyState(true);
                return;
            }

            resetFireworkUseState(true);
            resetGrimArmorFlyState(true);
            keepFallFlying();
        } else if (flyType.is(FlyType.GRIM)) {
            if (!canRun()) {
                resetFireworkUseState(true);
                resetGrimArmorFlyState(true);
                return;
            }

            resetGrimArmorFlyState(true);
            useFireworkRocket();
        } else {
            resetFireworkUseState(true);
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

        if ((!isWearingElytra() && !mc.player.isFallFlying())
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
        startPacketDelayTicks = 0;
        fireworkDelayTicks = 0;
        fallFlyingTicks = 0;
        lastFallFlyingTick = -1;
        resetFireworkUseState(true);
        resetGrimArmorFlyState(true);
    }

    private boolean isWearingElytra() {
        if (mc.player == null) {
            return false;
        }

        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return chestStack.is(Items.ELYTRA);
    }

    private ItemStack getChestStack() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(EquipmentSlot.CHEST);
    }

    private void applyNormalVelocity() {
        Vec3 direction = horizontalDirection();
        Vec3 velocity = mc.player.getDeltaMovement();
        double speed = flySpeed.getValue();
        mc.player.setDeltaMovement(direction.x * speed, verticalVelocity(velocity.y), direction.z * speed);
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

    private double verticalVelocity(double currentVelocity) {
        if (mc.options.keyJump.isDown()) {
            double targetVelocity = flySpeed.getValue();
            return currentVelocity < targetVelocity ? currentVelocity + NORMAL_ASCEND_ACCELERATION : currentVelocity;
        }

        if (mc.options.keyShift.isDown() || shouldDown.getValue()) {
            double targetVelocity = -downFlySpeed.getValue();
            return currentVelocity > targetVelocity ? currentVelocity - NORMAL_DESCEND_ACCELERATION : currentVelocity;
        }

        return currentVelocity;
    }

    private void keepFallFlying() {
        if (mc.player.isFallFlying()) {
            return;
        }

        if (startPacketDelayTicks > 0) {
            startPacketDelayTicks--;
            return;
        }

        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
        startPacketDelayTicks = startPacketDelay.getValue();

        mc.player.startFallFlying();
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

    private void runGrimArmorFly(long nowNs) {
        if (!canRunGrimArmorFly()) {
            if (grimArmorFlyStage == GrimArmorFlyStage.IDLE) {
                resetGrimArmorFlyState(true);
            } else {
                cancelGrimArmorFly(true);
            }
            return;
        }

        updateGrimArmorFlyFallFlyingTime(nowNs);
        restartGrimArmorFlyIfRequested();

        Vector2f inputRotations = calculateGrimArmorFlyInputRotations();
        if (grimArmorFlyStage == GrimArmorFlyStage.IDLE) {
            boolean restartRequested = grimArmorFlyRestartRequested.get();
            if (!restartRequested && nowNs < grimArmorFlyNextCycleTimeNs) {
                return;
            }

            if ((!restartRequested && inputRotations == null)
                    || !prepareGrimArmorFly(nowNs, restartRequested)) {
                if (restartRequested) {
                    abandonGrimArmorFlyRestart();
                }
                return;
            }
        }

        if (inputRotations != null) {
            applyGrimArmorFlyRotations(inputRotations);
        }

        applyGrimArmorFlyFireworkBoost(nowNs);

        switch (grimArmorFlyStage) {
            case IDLE -> {
            }
            case EQUIP_ELYTRA -> runGrimArmorFlyEquipElytra(nowNs);
            case START_FALL_FLYING -> runGrimArmorFlyStartFallFlying(nowNs);
            case WAIT_FALL_FLYING -> runGrimArmorFlyWaitFallFlying(nowNs);
            case USE_FIREWORK -> runGrimArmorFlyUseFirework(nowNs);
            case RESTORE_ARMOR -> runGrimArmorFlyRestoreArmor(nowNs);
            case WAIT_ARMOR_RESTORE -> runGrimArmorFlyWaitArmorRestore(nowNs);
        }
    }

    private boolean canRunGrimArmorFly() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.onGround()
                && !mc.player.isPassenger()
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().flying
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && (!onlyOnPreeSpace.getValue() || mc.options.keyJump.isDown());
    }

    private void updateGrimArmorFlyFallFlyingTime(long nowNs) {
        if (mc.player.isFallFlying()) {
            if (grimArmorFlyFallFlyingStartTimeNs == 0L) {
                grimArmorFlyFallFlyingStartTimeNs = nowNs;
            }
            if (grimArmorFlyLastFallFlyingTick != mc.player.tickCount) {
                grimArmorFlyFallFlyingTicks++;
                grimArmorFlyLastFallFlyingTick = mc.player.tickCount;
            }
        } else {
            grimArmorFlyFallFlyingStartTimeNs = 0L;
            grimArmorFlyFallFlyingTicks = 0;
            grimArmorFlyLastFallFlyingTick = -1;
        }
    }

    private boolean prepareGrimArmorFly(long nowNs, boolean restartRequested) {
        if (isWearingElytra() || !isChestArmorStack(getChestStack())) {
            return false;
        }

        int elytraSlot = InventoryUtility.findHotbarSlot(Items.ELYTRA);
        int fireworkSlot = InventoryUtility.findHotbarSlot(Items.FIREWORK_ROCKET);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!canHoldHotbarItem(elytraSlot, Items.ELYTRA)
                || (!restartRequested && !canUseHotbarItem(fireworkSlot, Items.FIREWORK_ROCKET))
                || !InventoryUtility.isHotbarSlot(selectedSlot)) {
            return false;
        }

        grimArmorFlyElytraSlot = elytraSlot;
        grimArmorFlyFireworkSlot = canUseHotbarItem(fireworkSlot, Items.FIREWORK_ROCKET)
                ? fireworkSlot
                : InventoryUtility.NOT_FOUND;
        grimArmorFlyOriginalSelectedSlot = selectedSlot;
        grimArmorFlyOriginalChestStack = getChestStack().copy();
        grimArmorFlyBaseYaw = mc.player.getYRot();
        stopSprinting();
        grimArmorFlyNextActionTimeNs = nowNs;
        setGrimArmorFlyStage(GrimArmorFlyStage.EQUIP_ELYTRA, nowNs);
        return true;
    }

    private void restartGrimArmorFlyIfRequested() {
        if (!isWearingElytra()
                || !canRunGrimArmorFlyAction()
                || !grimArmorFlyRestartRequested.compareAndSet(true, false)) {
            return;
        }

        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
        mc.player.startFallFlying();
        delayNextGrimArmorFlyAction();
    }

    private void runGrimArmorFlyEquipElytra(long nowNs) {
        if (isWearingElytra()) {
            setGrimArmorFlyStage(GrimArmorFlyStage.START_FALL_FLYING, nowNs);
            return;
        }

        if (hasGrimArmorFlyStageElapsed(nowNs, GRIM_ARMOR_EQUIP_TIMEOUT_NS)
                || !canHoldHotbarItem(grimArmorFlyElytraSlot, Items.ELYTRA)) {
            cancelGrimArmorFly(true);
            return;
        }

        GrimArmorFlyActionResult result = useGrimArmorFlyHotbarItem(grimArmorFlyElytraSlot, currentGrimArmorFlyRotations());
        if (result == GrimArmorFlyActionResult.FAIL) {
            cancelGrimArmorFly(true);
        }
    }

    private void runGrimArmorFlyStartFallFlying(long nowNs) {
        if (!isWearingElytra() && !mc.player.isFallFlying()) {
            cancelGrimArmorFly(true);
            return;
        }

        if (mc.player.isFallFlying()) {
            resetGrimArmorFlyGlideJumpState();
            setGrimArmorFlyStage(GrimArmorFlyStage.WAIT_FALL_FLYING, nowNs);
            return;
        }

        requestGrimArmorFlyFallFlyingStart();
        setGrimArmorFlyStage(GrimArmorFlyStage.WAIT_FALL_FLYING, nowNs);
    }

    private void runGrimArmorFlyWaitFallFlying(long nowNs) {
        if (!isWearingElytra() && !mc.player.isFallFlying()) {
            cancelGrimArmorFly(true);
            return;
        }

        if (!mc.player.isFallFlying()) {
            if (hasGrimArmorFlyStageElapsed(nowNs, GRIM_ARMOR_EQUIP_TIMEOUT_NS)) {
                cancelGrimArmorFly(true);
                return;
            }

            requestGrimArmorFlyFallFlyingStart();
            return;
        }
        resetGrimArmorFlyGlideJumpState();

        if (grimArmorFlyFallFlyingTicks >= GRIM_ARMOR_FALL_FLYING_READY_TICKS) {
            setGrimArmorFlyStage(GrimArmorFlyStage.USE_FIREWORK, nowNs);
        }
    }

    private void runGrimArmorFlyUseFirework(long nowNs) {
        if (!mc.player.isFallFlying()) {
            cancelGrimArmorFly(true);
            return;
        }

        if (!canUseHotbarItem(grimArmorFlyFireworkSlot, Items.FIREWORK_ROCKET)) {
            setGrimArmorFlyStage(GrimArmorFlyStage.RESTORE_ARMOR, nowNs);
            return;
        }

        if (hasGrimArmorFlyStageElapsed(nowNs, GRIM_ARMOR_FIREWORK_TIMEOUT_NS)) {
            setGrimArmorFlyStage(GrimArmorFlyStage.RESTORE_ARMOR, nowNs);
            return;
        }

        Vector2f rotations = currentGrimArmorFlyRotations();
        GrimArmorFlyActionResult result = useGrimArmorFlyHotbarItem(grimArmorFlyFireworkSlot, rotations);
        if (result == GrimArmorFlyActionResult.SUCCESS) {
            grimArmorFlyNextCycleTimeNs = nowNs + getGrimArmorFlyCycleDelayNs();
            grimArmorFlyBoostPendingUntilTimeNs = nowNs + GRIM_ARMOR_FIREWORK_TIMEOUT_NS;
            setGrimArmorFlyStage(GrimArmorFlyStage.RESTORE_ARMOR, nowNs);
        } else if (result == GrimArmorFlyActionResult.FAIL) {
            setGrimArmorFlyStage(GrimArmorFlyStage.RESTORE_ARMOR, nowNs);
        }
    }

    private void runGrimArmorFlyRestoreArmor(long nowNs) {
        if (isGrimArmorFlyFireworkBoostPending(nowNs)) {
            return;
        }

        if (!isWearingElytra()) {
            finishGrimArmorFlyCycle(false);
            return;
        }

        int restoreSlot = findGrimArmorFlyRestoreSlot();
        if (restoreSlot == InventoryUtility.NOT_FOUND) {
            if (hasGrimArmorFlyStageElapsed(nowNs, GRIM_ARMOR_RESTORE_TIMEOUT_NS)) {
                resetGrimArmorFlyState(true);
            }
            return;
        }

        if (InventoryUtility.isHotbarSlot(restoreSlot)) {
            GrimArmorFlyActionResult result = useGrimArmorFlyHotbarItem(restoreSlot, currentGrimArmorFlyRotations());
            if (result == GrimArmorFlyActionResult.SUCCESS) {
                setGrimArmorFlyStage(GrimArmorFlyStage.WAIT_ARMOR_RESTORE, nowNs);
            }
            return;
        }

        if (!canRunGrimArmorFlyAction()) {
            return;
        }

        GrimArmorFlyActionResult result = swapGrimArmorFlyInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, restoreSlot);
        if (result == GrimArmorFlyActionResult.SUCCESS) {
            setGrimArmorFlyStage(GrimArmorFlyStage.WAIT_ARMOR_RESTORE, nowNs);
        } else if (result == GrimArmorFlyActionResult.FAIL) {
            resetGrimArmorFlyState(true);
        }
    }

    private void runGrimArmorFlyWaitArmorRestore(long nowNs) {
        if (!isWearingElytra()) {
            finishGrimArmorFlyCycle(false);
            return;
        }

        if (hasGrimArmorFlyStageElapsed(nowNs, GRIM_ARMOR_RESTORE_TIMEOUT_NS)) {
            resetGrimArmorFlyState(true);
        }
    }

    private Vector2f calculateGrimArmorFlyInputRotations() {
        int forward = MovingUtility.forwardVal();
        int strafe = MovingUtility.strafeVal();
        int vertical = verticalInput();
        boolean hasHorizontalInput = forward != 0 || strafe != 0;
        if (!hasHorizontalInput && vertical == 0) {
            return null;
        }

        float yaw = grimArmorFlyStage == GrimArmorFlyStage.IDLE ? mc.player.getYRot() : grimArmorFlyBaseYaw;
        if (hasHorizontalInput) {
            if (forward < 0) {
                yaw += 180.0F;
            }

            if (strafe != 0) {
                if (forward == 0) {
                    yaw -= strafe * 90.0F;
                } else {
                    yaw -= strafe * (forward > 0 ? 45.0F : -45.0F);
                }
            }
        }

        float pitch = 0.0F;
        if (vertical > 0) {
            pitch = hasHorizontalInput ? -45.0F : -90.0F;
        } else if (vertical < 0) {
            pitch = hasHorizontalInput ? 45.0F : 90.0F;
        }

        return legitimizeRotations(new Vector2f(yaw, pitch));
    }

    private void alignGrimArmorFlyMoveInput(MoveInputEvent event) {
        if (!shouldLockGrimArmorFlyMovementYaw()) {
            return;
        }

        if (event.getForward() == 0.0F && event.getStrafe() == 0.0F) {
            return;
        }

        event.setForward(1.0F);
        event.setStrafe(0.0F);
    }

    private void neutralizeGrimArmorFlyMoveInput(MoveInputEvent event) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSprint(false);
    }

    private boolean shouldLockGrimArmorFlyMovementYaw() {
        return flyType.is(FlyType.GRIM_ARMOR_FLY)
                && grimArmorFlyStage != GrimArmorFlyStage.IDLE
                && grimArmorFlyRotations != null;
    }

    private int verticalInput() {
        boolean jump = mc.options.keyJump.isDown();
        boolean shift = mc.options.keyShift.isDown();
        if (jump == shift) {
            return 0;
        }

        return jump ? 1 : -1;
    }

    private void applyGrimArmorFlyRotations(Vector2f rotations) {
        if (rotations == null) {
            return;
        }

        RotationManager.INSTANCE.setSmoothed(false);
        RotationManager.INSTANCE.setRotations(rotations, GRIM_ARMOR_ROTATION_SPEED, Priority.Highest);
        grimArmorFlyRotations = new Vector2f(RotationManager.INSTANCE.getRotation());
        grimArmorFlyControllingRotations = true;
    }

    private Vector2f currentGrimArmorFlyRotations() {
        if (grimArmorFlyRotations != null) {
            return grimArmorFlyRotations;
        }

        return legitimizeRotations(new Vector2f(mc.player.getYRot(), mc.player.getXRot()));
    }

    private void requestGrimArmorFlyFallFlyingStart() {
        if (mc.player == null || mc.player.onGround()) {
            return;
        }

        if (mc.player.isFallFlying()) {
            resetGrimArmorFlyGlideJumpState();
            return;
        }

        if (grimArmorFlyPressJumpForGlide) {
            return;
        }

        if (!grimArmorFlyReleasedJumpForGlide && !grimArmorFlyReleaseJumpApplied) {
            grimArmorFlyReleasedJumpForGlide = true;
            grimArmorFlyPressJumpForGlide = false;
            return;
        }

        if (!grimArmorFlyReleaseJumpApplied) {
            return;
        }

        grimArmorFlyPressJumpForGlide = true;
        grimArmorFlyReleasedJumpForGlide = false;
    }

    private void resetGrimArmorFlyGlideJumpState() {
        grimArmorFlyReleasedJumpForGlide = false;
        grimArmorFlyReleaseJumpApplied = false;
        grimArmorFlyPressJumpForGlide = false;
    }

    private void applyGrimArmorFlyFireworkBoost(long nowNs) {
        if (grimArmorFlyBoostPendingUntilTimeNs != 0L) {
            if (nowNs > grimArmorFlyBoostPendingUntilTimeNs) {
                grimArmorFlyBoostPendingUntilTimeNs = 0L;
            } else if (!hasGrimArmorFlyAttachedFirework()) {
                return;
            } else {
                grimArmorFlyBoostPendingUntilTimeNs = 0L;
                grimArmorFlyBoostEndTimeNs = nowNs + GRIM_ARMOR_FIREWORK_BOOST_NS;
            }
        }

        if (grimArmorFlyBoostEndTimeNs == 0L || nowNs > grimArmorFlyBoostEndTimeNs) {
            grimArmorFlyBoostEndTimeNs = 0L;
            return;
        }

        applyGrimArmorFlyFireworkBoost(currentGrimArmorFlyRotations());
    }

    private boolean isGrimArmorFlyFireworkBoostPending(long nowNs) {
        return (grimArmorFlyBoostPendingUntilTimeNs != 0L && nowNs <= grimArmorFlyBoostPendingUntilTimeNs)
                || (grimArmorFlyBoostEndTimeNs != 0L && nowNs <= grimArmorFlyBoostEndTimeNs);
    }

    private boolean hasGrimArmorFlyAttachedFirework() {
        if (mc.level == null || mc.player == null) {
            return false;
        }

        return !mc.level.getEntitiesOfClass(
                FireworkRocketEntity.class,
                mc.player.getBoundingBox().inflate(2.0D),
                firework -> firework.getOwner() == mc.player || firework.distanceToSqr(mc.player) < 1.0D
        ).isEmpty();
    }

    private void applyGrimArmorFlyFireworkBoost(Vector2f rotations) {
        if (mc.player == null || rotations == null) {
            return;
        }

        Vec3 direction = Vec3.directionFromRotation(rotations.y, rotations.x);
        if (direction.lengthSqr() <= 1.0E-6D) {
            return;
        }

        direction = direction.normalize();
        Vec3 velocity = mc.player.getDeltaMovement();
        double speed = Math.max(GRIM_ARMOR_MIN_FIREWORK_SPEED, fireworkSpeed.getValue());
        double currentSpeed = velocity.dot(direction);
        if (currentSpeed >= speed) {
            return;
        }

        Vec3 boost = direction.scale(speed - Math.max(0.0D, currentSpeed));
        if (!canApplyGrimArmorFlyBoost(boost)) {
            return;
        }

        mc.player.setDeltaMovement(velocity.add(boost));
    }

    private boolean canApplyGrimArmorFlyBoost(Vec3 boost) {
        if (mc.player.horizontalCollision) {
            return false;
        }

        double checkScale = Math.min(1.0D, GRIM_ARMOR_BOOST_COLLISION_CHECK_DISTANCE / Math.max(boost.length(), 1.0E-6D));
        Vec3 checkedBoost = boost.scale(checkScale);
        return MovingUtility.canMove(checkedBoost.x, checkedBoost.z, checkedBoost.y);
    }

    private GrimArmorFlyActionResult useGrimArmorFlyHotbarItem(int hotbarSlot, Vector2f rotations) {
        if (!InventoryUtility.isHotbarSlot(hotbarSlot)) {
            return GrimArmorFlyActionResult.FAIL;
        }

        if (!canRunGrimArmorFlyAction()) {
            return GrimArmorFlyActionResult.WAIT;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != hotbarSlot) {
            if (!InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
                return GrimArmorFlyActionResult.FAIL;
            }
        }

        return sendUseSelectedItemPacket(rotations)
                ? GrimArmorFlyActionResult.SUCCESS
                : GrimArmorFlyActionResult.WAIT;
    }

    private void stopSprinting() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private boolean sendUseSelectedItemPacket(Vector2f rotations) {
        if (mc.player == null || mc.player.connection == null || mc.level == null || mc.gameRenderer == null) {
            return false;
        }

        if (!canRunGrimArmorFlyAction()) {
            return false;
        }

        if (!InventoryUtility.syncSelectedSlot()) {
            return false;
        }

        Vector2f fixedRotations = legitimizeRotations(rotations);
        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.player.connection.send(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND,
                    prediction.currentSequence(),
                    fixedRotations.x,
                    fixedRotations.y
            ));
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
        delayNextGrimArmorFlyAction();
        return true;
    }

    private Vector2f legitimizeRotations(Vector2f rotations) {
        if (mc.player == null || rotations == null) {
            return new Vector2f();
        }

        Vector2f baseRotations = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        float yaw = baseRotations.x + Mth.wrapDegrees(rotations.x - baseRotations.x);
        float pitch = Mth.clamp(rotations.y, -90.0F, 90.0F);
        return new Vector2f(yaw, pitch);
    }

    private boolean canRunGrimArmorFlyAction() {
        return System.nanoTime() >= grimArmorFlyNextActionTimeNs;
    }

    private GrimArmorFlyActionResult swapGrimArmorFlyInventorySlots(int firstInventorySlot, int secondInventorySlot) {
        if (!canRunGrimArmorFlyAction()) {
            return GrimArmorFlyActionResult.WAIT;
        }

        if (!canSendGrimArmorFlyInventoryClick()) {
            return GrimArmorFlyActionResult.WAIT;
        }

        if (!InventoryUtility.swapInventorySlots(firstInventorySlot, secondInventorySlot)) {
            return GrimArmorFlyActionResult.FAIL;
        }

        grimArmorFlySuppressMovementInput = false;
        delayNextGrimArmorFlyAction();
        return GrimArmorFlyActionResult.SUCCESS;
    }

    private boolean canSendGrimArmorFlyInventoryClick() {
        if (mc.player == null) {
            return false;
        }

        grimArmorFlySuppressMovementInput = true;
        return !isGrimArmorFlyMovingInput(mc.player.input.keyPresses)
                && !isGrimArmorFlyMovingInput(mc.player.getLastSentInput());
    }

    private boolean isGrimArmorFlyMovingInput(Input input) {
        return input != null && (input.forward()
                || input.backward()
                || input.left()
                || input.right()
                || input.jump());
    }

    private void delayNextGrimArmorFlyAction() {
        grimArmorFlyNextActionTimeNs = System.nanoTime() + millisToNanos(Math.max(GRIM_ARMOR_MIN_SWITCH_DELAY_MS, switchDelayMs.getValue()));
    }

    private long millisToNanos(long milliseconds) {
        return milliseconds * NANOS_PER_MILLISECOND;
    }

    private long getGrimArmorFlyCycleDelayNs() {
        return millisToNanos(armorCycleDelayMs.getValue());
    }

    private boolean hasGrimArmorFlyStageElapsed(long nowNs, long durationNs) {
        return grimArmorFlyStageStartTimeNs != 0L && nowNs - grimArmorFlyStageStartTimeNs > durationNs;
    }

    private boolean canUseHotbarItem(int hotbarSlot, Item item) {
        if (!canHoldHotbarItem(hotbarSlot, item)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        return !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private boolean canHoldHotbarItem(int hotbarSlot, Item item) {
        if (mc.player == null || mc.level == null || !InventoryUtility.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        return !stack.isEmpty()
                && stack.is(item)
                && stack.isItemEnabled(mc.level.enabledFeatures());
    }

    private boolean isChestArmorStack(ItemStack stack) {
        return mc.player != null
                && !stack.isEmpty()
                && !stack.is(Items.ELYTRA)
                && mc.player.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
    }

    private boolean isOriginalGrimArmorChestStack(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, grimArmorFlyOriginalChestStack);
    }

    private int findGrimArmorFlyRestoreSlot() {
        if (isKnownGrimArmorFlyRestoreSlot(grimArmorFlyElytraSlot)) {
            return grimArmorFlyElytraSlot;
        }

        if (!grimArmorFlyOriginalChestStack.isEmpty()) {
            int originalSlot = InventoryUtility.findSlot(this::isOriginalGrimArmorChestStack);
            if (isUsableGrimArmorFlyRestoreSlot(originalSlot)) {
                return originalSlot;
            }
        }

        int chestArmorSlot = InventoryUtility.findSlot(this::isChestArmorStack);
        return isUsableGrimArmorFlyRestoreSlot(chestArmorSlot) ? chestArmorSlot : InventoryUtility.NOT_FOUND;
    }

    private boolean isKnownGrimArmorFlyRestoreSlot(int inventorySlot) {
        if (!isUsableGrimArmorFlyRestoreSlot(inventorySlot)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(inventorySlot);
        return !grimArmorFlyOriginalChestStack.isEmpty()
                ? isOriginalGrimArmorChestStack(stack)
                : isChestArmorStack(stack);
    }

    private boolean isUsableGrimArmorFlyRestoreSlot(int inventorySlot) {
        return InventoryUtility.isValidInventorySlot(inventorySlot)
                && inventorySlot != InventoryUtility.ARMOR_CHEST_SLOT;
    }

    private void cancelGrimArmorFly(boolean restoreSelectedSlot) {
        if (isWearingElytra()) {
            int restoreSlot = findGrimArmorFlyRestoreSlot();
            if (restoreSlot != InventoryUtility.NOT_FOUND) {
                GrimArmorFlyActionResult result = swapGrimArmorFlyInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, restoreSlot);
                if (result == GrimArmorFlyActionResult.WAIT) {
                    return;
                }
            }
        }

        resetGrimArmorFlyState(restoreSelectedSlot);
    }

    private void resetGrimArmorFlyState(boolean restoreSelectedSlot) {
        resetGrimArmorFlyState(restoreSelectedSlot, true);
    }

    private void resetGrimArmorFlyState(boolean restoreSelectedSlot, boolean clearRestartRequest) {
        if (clearRestartRequest) {
            abandonGrimArmorFlyRestart();
        }

        if (restoreSelectedSlot) {
            restoreGrimArmorFlySelectedSlot();
        }

        releaseGrimArmorFlyRotations();
        grimArmorFlyStage = GrimArmorFlyStage.IDLE;
        grimArmorFlyStageStartTimeNs = 0L;
        grimArmorFlyNextActionTimeNs = 0L;
        grimArmorFlyNextCycleTimeNs = 0L;
        grimArmorFlyFallFlyingStartTimeNs = 0L;
        grimArmorFlyBoostEndTimeNs = 0L;
        grimArmorFlyBoostPendingUntilTimeNs = 0L;
        grimArmorFlyElytraSlot = InventoryUtility.NOT_FOUND;
        grimArmorFlyFireworkSlot = InventoryUtility.NOT_FOUND;
        grimArmorFlyOriginalSelectedSlot = InventoryUtility.NOT_FOUND;
        grimArmorFlyOriginalChestStack = ItemStack.EMPTY;
        grimArmorFlyRotations = null;
        grimArmorFlyBaseYaw = 0.0F;
        grimArmorFlyControllingRotations = false;
        grimArmorFlyFallFlyingTicks = 0;
        grimArmorFlyLastFallFlyingTick = -1;
        resetGrimArmorFlyGlideJumpState();
        grimArmorFlySuppressMovementInput = false;
    }

    private void finishGrimArmorFlyCycle(boolean restoreSelectedSlot) {
        long nextCycleTimeNs = grimArmorFlyNextCycleTimeNs;
        resetGrimArmorFlyState(restoreSelectedSlot, false);
        grimArmorFlyNextCycleTimeNs = nextCycleTimeNs;
    }

    private void abandonGrimArmorFlyRestart() {
        if (grimArmorFlyRestartRequested.getAndSet(false) && mc.player != null) {
            mc.player.stopFallFlying();
        }
    }

    private void restoreGrimArmorFlySelectedSlot() {
        if (grimArmorFlyOriginalSelectedSlot == InventoryUtility.NOT_FOUND
                || !InventoryUtility.isHotbarSlot(grimArmorFlyOriginalSelectedSlot)) {
            return;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (selectedSlot == grimArmorFlyElytraSlot || selectedSlot == grimArmorFlyFireworkSlot) {
            InventoryUtility.selectHotbarSlot(grimArmorFlyOriginalSelectedSlot, true);
        }
    }

    private void releaseGrimArmorFlyRotations() {
        if (!grimArmorFlyControllingRotations || grimArmorFlyRotations == null || !RotationManager.INSTANCE.isActive()) {
            return;
        }

        Vector2f activeRotations = RotationManager.INSTANCE.getRotation();
        if (Math.abs(Mth.wrapDegrees(activeRotations.x - grimArmorFlyRotations.x)) <= 1.0F
                && Math.abs(activeRotations.y - grimArmorFlyRotations.y) <= 1.0F) {
            RotationManager.INSTANCE.setActive(false);
        }
    }

    private void setGrimArmorFlyStage(GrimArmorFlyStage stage) {
        setGrimArmorFlyStage(stage, System.nanoTime());
    }

    private void setGrimArmorFlyStage(GrimArmorFlyStage stage, long nowNs) {
        if (grimArmorFlyStage == stage) {
            return;
        }

        grimArmorFlyStage = stage;
        grimArmorFlyStageStartTimeNs = nowNs;
        if (stage == GrimArmorFlyStage.START_FALL_FLYING) {
            resetGrimArmorFlyGlideJumpState();
        }
    }

    public enum FlyType {
        NORMAL, GRIM, GRIM_ARMOR_FLY
    }

    private enum FireworkUseStage {
        IDLE, USE, RESTORE
    }

    private enum GrimArmorFlyStage {
        IDLE,
        EQUIP_ELYTRA,
        START_FALL_FLYING,
        WAIT_FALL_FLYING,
        USE_FIREWORK,
        RESTORE_ARMOR,
        WAIT_ARMOR_RESTORE
    }

    private enum GrimArmorFlyActionResult {
        WAIT,
        SUCCESS,
        FAIL
    }
}
