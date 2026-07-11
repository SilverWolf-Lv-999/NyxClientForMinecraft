package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// 单挑模式专用
@ModuleInfo(name = "nyxclient.module.maceaura.name", description = "nyxclient.module.maceaura.description", category = Category.COMBAT)
public class MaceAura extends Module {
    public static final MaceAura INSTANCE = new MaceAura();

    private static final float DOWN_PITCH = 90.0F;
    private static final int WIND_CHARGE_USE_DELAY_TICKS = 0;
    private static final int WIND_CHARGE_JUMP_TIMEOUT_TICKS = 6;
    private static final int WIND_CHARGE_USE_TIMEOUT_TICKS = 10;
    private static final int MIN_LAUNCH_TICKS = 3;
    private static final int MACE_SWITCH_AFTER_USE_DELAY_TICKS = 1;
    private static final int MACE_SWITCH_SETTLE_TICKS = 2;
    private static final int POST_WIND_CHARGE_ATTACK_DELAY_TICKS = 4;
    private static final int ATTACK_HOLD_TICKS = 5;
    private static final int AFTER_ATTACK_TIMEOUT_TICKS = 20;
    private static final int STAGE_TIMEOUT_TICKS = 120;
    private static final double LAUNCH_VELOCITY_THRESHOLD = 0.08D;
    private static final double FALLING_ATTACK_VELOCITY = -0.03D;
    private static final float MIN_MACE_FALL_DISTANCE = 1.5F;
    private static final float FIREWORK_MIN_ASCEND_ANGLE = 57.0F;
    private static final float FIREWORK_MAX_ASCEND_ANGLE = 76.0F;
    private static final int FIREWORK_EQUIP_TIMEOUT_TICKS = 20;
    private static final int FIREWORK_GLIDE_TIMEOUT_TICKS = 30;
    private static final int FIREWORK_RESET_SETTLE_TICKS = 1;
    private static final int FIREWORK_MACE_SWITCH_SETTLE_TICKS = 1;
    private static final double FIREWORK_ARMOR_SWITCH_RANGE = 5.0D;
    private static final int FIREWORK_POST_ATTACK_ARMOR_HOLD_TICKS = 3;
    private static final int FIREWORK_POST_ATTACK_LAUNCH_WAIT_TICKS = 8;
    private static final int FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_WAIT_TICKS = 8;
    private static final double FIREWORK_POST_ATTACK_LAUNCH_MAX_SHORTFALL = 4.0D;
    private static final double FIREWORK_POST_ATTACK_DIRECT_DIVE_MIN_HEIGHT = 8.0D;
    private static final double FIREWORK_POST_ATTACK_LAUNCH_UPWARD_VELOCITY = 0.02D;
    private static final double FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_UPWARD_VELOCITY = 0.16D;
    private static final int FIREWORK_GRIM_FALL_FLYING_READY_TICKS = 3;
    private static final double GRIM_ATTACK_RANGE = 2.90D;
    private static final int POST_USE_ITEM_SLOT_DELAY_TICKS = 3;
    private static final int FIREWORK_USE_RETRY_DELAY_TICKS = 4;

    public final EnumValue<DuelMode> duelMode = ValueBuild.enumSetting("duel mode", DuelMode.ELYTRA, this);
    public final EnumValue<ItemType> itemType = ValueBuild.enumSetting("item type", ItemType.WIND_CHARGE, this);
    public final DoubleValue targetRange = ValueBuild.doubleSetting("target range", 24.0D, 4.0D, 64.0D, 0.5D, this);
    public final DoubleValue attackRange = ValueBuild.doubleSetting("attack range", 4.5D, 2.5D, 8.0D, 0.1D, this);
    public final IntValue blockheight = ValueBuild.intSetting("block height", 15, 10, 40, 1, () -> itemType.getValue() == ItemType.FIREWORK_ROCKET, this);

    private Stage stage = Stage.ACQUIRE_TARGET;
    private LivingEntity target;
    private int originalSelectedSlot = InventoryUtility.NOT_FOUND;
    private int maceSlot = InventoryUtility.NOT_FOUND;
    private int windChargeOriginalSlot = InventoryUtility.NOT_FOUND;
    private int windChargeSlot = InventoryUtility.NOT_FOUND;
    private int fireworkRocketSlot = InventoryUtility.NOT_FOUND;
    private int elytraSlot = InventoryUtility.NOT_FOUND;
    private int windChargeUseDelayTicks;
    private int stageTicks;
    private int launchTicks;
    private int maceSwitchDelayTicks;
    private int windChargeAttackDelayTicks;
    private int heldMaceTicks;
    private int attackHoldTicks;
    private int fireworkResetDelayTicks;
    private int fireworkFallFlyingTicks;
    private double fireworkStartY;
    private double fireworkPostAttackStartY = Double.NaN;
    private float fireworkAscendYaw;
    private float fireworkAscendPitch;
    private ItemStack fireworkOriginalChestStack = ItemStack.EMPTY;
    private boolean fireworkDiveBoostUsed;
    private boolean fireworkAllowElytraMaceAttack;
    private boolean fireworkReleasedJumpForGlide;
    private boolean fireworkPressJumpForGlide;
    private int postUseItemSlotDelayTicks;
    private int fireworkUseRetryDelayTicks;
    private int queuedHotbarSlot = InventoryUtility.NOT_FOUND;
    private boolean warnedMissingLoadout;

    @Override
    public void onEnable() {
        resetRuntimeState();
        originalSelectedSlot = InventoryUtility.getSelectedHotbarSlot();
        MsgUtility.debug("MaceAura enabled");
    }

    @Override
    public void onDisable() {
        restoreOriginalSelectedSlot();
        RotationManager.INSTANCE.setActive(false);
        resetRuntimeState();
        MsgUtility.debug("MaceAura disabled");
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!canRun() || !shouldControlInput()) {
            return;
        }

        event.setForward(shouldMoveForward() ? 1.0F : 0.0F);
        event.setStrafe(0.0F);
        event.setSprint(shouldMoveForward());
        event.setSneak(false);
        event.setJump(shouldJump());
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!canRun() || !shouldControlMovementYaw()) {
            return;
        }

        event.setYaw(targetYaw());
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        tickCombatCycle();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            RotationManager.INSTANCE.setActive(false);
            resetCombatCycle();
            MsgUtility.debug("MaceAura recovered: server corrected position");
        }
    }

    private void tickCombatCycle() {
        fireworkPressJumpForGlide = false;
        tickActionDelays();

        if (!canRun()) {
            resetCombatCycle();
            return;
        }

        applyQueuedHotbarSlot();

        DetectedLoadout loadout = detectLoadout();
        if (loadout == null) {
            if (!warnedMissingLoadout) {
                MsgUtility.debug("MaceAura waiting: need mace and wind charge/firework rocket");
                warnedMissingLoadout = true;
            }
            resetCombatCycle();
            return;
        }

        warnedMissingLoadout = false;
        syncDetectedValues(loadout);
        tickHeldMaceState();

        if (stageTicks > STAGE_TIMEOUT_TICKS) {
            recoverFromTimeout();
            return;
        }

        target = refreshTarget();
        if (target == null && stage != Stage.ACQUIRE_TARGET && !canRecoverFireworkAfterAttackWithoutTarget()) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        Stage stageBeforeTick = stage;
        tickStage();
        if (stage == stageBeforeTick) {
            stageTicks++;
        }
    }

    private void tickStage() {
        switch (stage) {
            case ACQUIRE_TARGET -> {
                if (target != null) {
                    setStage(initialAttackStage());
                }
            }
            case USE_WIND_CHARGE -> tickUseWindCharge();
            case JUMP -> tickJumpForWindCharge();
            case WIND_CHARGE_USE -> tickQueuedWindChargeUse();
            case WAIT_LAUNCH -> tickWaitLaunch();
            case FIREWORK_PREPARE -> tickPrepareFireworkRocket();
            case FIREWORK_EQUIP_ELYTRA -> tickEquipFireworkElytra();
            case FIREWORK_START_ASCENT -> tickStartFireworkAscent();
            case FIREWORK_ASCENT -> tickFireworkAscent();
            case FIREWORK_RESTORE_ARMOR -> tickRestoreFireworkArmor();
            case FIREWORK_REEQUIP_ELYTRA -> tickReequipFireworkElytra();
            case FIREWORK_START_DIVE -> tickStartFireworkDive();
            case FIREWORK_DIVE_FIREWORK -> tickUseDiveFirework();
            case FIREWORK_AFTER_ATTACK_HOLD_ARMOR -> tickPostAttackFireworkArmorHold();
            case APPROACH_TARGET -> tickApproachTarget();
            case SWITCH_TO_MACE -> tickSwitchToMace();
            case ATTACK -> tickAttack();
            case AFTER_ATTACK -> tickAfterAttack();
        }
    }

    private void tickUseWindCharge() {
        if (target == null || !mc.player.onGround()) {
            return;
        }

        rotateDown();

        int foundMaceSlot = ensureHotbarItem(Items.MACE, InventoryUtility.NOT_FOUND);
        if (!canHoldHotbarItem(foundMaceSlot, Items.MACE)) {
            return;
        }

        int foundWindChargeSlot = ensureHotbarItem(Items.WIND_CHARGE, foundMaceSlot);
        if (!canUseHotbarItem(foundWindChargeSlot, Items.WIND_CHARGE)) {
            return;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        windChargeOriginalSlot = selectedSlot;
        maceSlot = foundMaceSlot;
        windChargeSlot = foundWindChargeSlot;
        if (!selectHotbarSlot(foundWindChargeSlot)) {
            if (isQueuedHotbarSlot(foundWindChargeSlot)) {
                return;
            }

            clearQueuedWindCharge();
            return;
        }

        setStage(Stage.JUMP);
    }

    private void tickJumpForWindCharge() {
        rotateDown();

        if (stageTicks == 0 && canJumpFromGround()) {
            jumpForWindCharge();
        }

        if (InventoryUtility.getSelectedHotbarSlot() != windChargeSlot
                || !canUseHotbarItem(windChargeSlot, Items.WIND_CHARGE)) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        if (!mc.player.onGround() || velocity.y > LAUNCH_VELOCITY_THRESHOLD) {
            windChargeUseDelayTicks = WIND_CHARGE_USE_DELAY_TICKS;
            setStage(Stage.WIND_CHARGE_USE);
            return;
        }

        if (stageTicks == 2 && canJumpFromGround()) {
            jumpForWindCharge();
            return;
        }

        if (stageTicks > WIND_CHARGE_JUMP_TIMEOUT_TICKS) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickQueuedWindChargeUse() {
        rotateDown();

        if (stageTicks > WIND_CHARGE_USE_TIMEOUT_TICKS) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != windChargeSlot
                || !canUseHotbarItem(windChargeSlot, Items.WIND_CHARGE)) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (windChargeUseDelayTicks > 0) {
            windChargeUseDelayTicks--;
            return;
        }

        if (!useSelectedItem()) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        launchTicks = 0;
        clearQueuedWindCharge();
        maceSwitchDelayTicks = MACE_SWITCH_AFTER_USE_DELAY_TICKS;
        windChargeAttackDelayTicks = POST_WIND_CHARGE_ATTACK_DELAY_TICKS;
        setStage(Stage.WAIT_LAUNCH);
    }

    private void tickPrepareFireworkRocket() {
        if (target == null) {
            clearPostAttackLaunchStart();
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;

        if (tryPrepareActiveFlightWithHotbarElytra()) {
            return;
        }

        if (tryPrepareWornElytraWithHotbarChestArmor()) {
            return;
        }

        if (restoreFireworkArmorBeforePrepare()) {
            return;
        }

        if (InventoryUtility.count(Items.FIREWORK_ROCKET) < 2 || !hasChestArmorForFireworkReset()) {
            return;
        }

        int foundElytraSlot = InventoryUtility.findHotbarSlot(Items.ELYTRA);
        if (!canHoldHotbarItem(foundElytraSlot, Items.ELYTRA)) {
            return;
        }

        int foundMaceSlot = ensureHotbarItem(Items.MACE, foundElytraSlot);
        if (!canHoldHotbarItem(foundMaceSlot, Items.MACE)) {
            return;
        }

        int foundFireworkSlot = ensureHotbarItem(Items.FIREWORK_ROCKET, foundElytraSlot, foundMaceSlot);
        if (!canUseHotbarItem(foundFireworkSlot, Items.FIREWORK_ROCKET)) {
            return;
        }

        elytraSlot = foundElytraSlot;
        maceSlot = foundMaceSlot;
        fireworkRocketSlot = foundFireworkSlot;
        fireworkOriginalChestStack = getChestStack().copy();
        fireworkStartY = mc.player.getY();
        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;

        if (!selectHotbarSlot(elytraSlot)) {
            if (isQueuedHotbarSlot(elytraSlot)) {
                return;
            }

            resetFireworkState();
            return;
        }

        setStage(Stage.FIREWORK_EQUIP_ELYTRA);
    }

    private boolean tryPrepareActiveFlightWithHotbarElytra() {
        if (!isWearingElytra() || !mc.player.isFallFlying()) {
            return false;
        }

        int foundElytraSlot = InventoryUtility.findHotbarSlot(Items.ELYTRA);
        if (!canHoldHotbarItem(foundElytraSlot, Items.ELYTRA)) {
            return false;
        }

        LivingEntity cameraTarget = crosshairFireworkTarget();
        if (cameraTarget == null) {
            target = null;
            return true;
        }

        int foundMaceSlot = ensureHotbarItem(Items.MACE, foundElytraSlot);
        if (!canHoldHotbarItem(foundMaceSlot, Items.MACE)) {
            return true;
        }

        int foundFireworkSlot = ensureHotbarItem(Items.FIREWORK_ROCKET, foundElytraSlot, foundMaceSlot);
        if (!canUseHotbarItem(foundFireworkSlot, Items.FIREWORK_ROCKET)) {
            return true;
        }

        target = cameraTarget;
        elytraSlot = foundElytraSlot;
        maceSlot = foundMaceSlot;
        fireworkRocketSlot = foundFireworkSlot;
        fireworkOriginalChestStack = ItemStack.EMPTY;
        fireworkStartY = mc.player.getY();
        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = true;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;
        setStage(Stage.FIREWORK_DIVE_FIREWORK);
        return true;
    }

    private boolean tryPrepareWornElytraWithHotbarChestArmor() {
        if (!isWearingElytra()) {
            return false;
        }

        int foundChestArmorSlot = findHotbarChestArmorSlot();
        if (!canHoldChestArmor(foundChestArmorSlot)) {
            return false;
        }

        if (InventoryUtility.count(Items.FIREWORK_ROCKET) < 2) {
            return true;
        }

        int foundMaceSlot = ensureHotbarItem(Items.MACE, foundChestArmorSlot);
        if (!canHoldHotbarItem(foundMaceSlot, Items.MACE)) {
            return true;
        }

        int foundFireworkSlot = ensureHotbarItem(Items.FIREWORK_ROCKET, foundChestArmorSlot, foundMaceSlot);
        if (!canUseHotbarItem(foundFireworkSlot, Items.FIREWORK_ROCKET)) {
            return true;
        }

        elytraSlot = foundChestArmorSlot;
        maceSlot = foundMaceSlot;
        fireworkRocketSlot = foundFireworkSlot;
        fireworkOriginalChestStack = InventoryUtility.getStack(foundChestArmorSlot).copy();
        fireworkStartY = mc.player.getY();
        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;
        setStage(Stage.FIREWORK_START_ASCENT);
        return true;
    }

    private void tickEquipFireworkElytra() {
        rotateFireworkAscent();

        if (isWearingElytra()) {
            setStage(Stage.FIREWORK_START_ASCENT);
            return;
        }

        if (stageTicks > FIREWORK_EQUIP_TIMEOUT_TICKS) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != elytraSlot
                && !selectHotbarSlot(elytraSlot)) {
            if (isQueuedHotbarSlot(elytraSlot)) {
                return;
            }

            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (canHoldHotbarItem(elytraSlot, Items.ELYTRA) && !useSelectedItemWithRetryDelay()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
        }
    }

    private void tickStartFireworkAscent() {
        rotateFireworkAscent();

        if (!isWearingElytra()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (mc.player.onGround()) {
            if (stageTicks > FIREWORK_GLIDE_TIMEOUT_TICKS) {
                resetFireworkState();
                setStage(Stage.FIREWORK_PREPARE);
            }
            return;
        }

        if (!mc.player.isFallFlying()) {
            requestFallFlyingStart();
            return;
        }
        resetFireworkGlideJumpState();

        if (fireworkFallFlyingTicks < FIREWORK_GRIM_FALL_FLYING_READY_TICKS) {
            return;
        }

        if (!ensureFireworkRocketSlot()) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != fireworkRocketSlot
                && !selectHotbarSlot(fireworkRocketSlot)) {
            return;
        }

        if (!useSelectedItem()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        launchTicks = 0;
        setStage(Stage.FIREWORK_ASCENT);
    }

    private void tickFireworkAscent() {
        rotateFireworkAscent();

        if (!isWearingElytra()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (!mc.player.onGround() && !mc.player.isFallFlying()) {
            requestFallFlyingStart();
        }

        if (mc.player.getY() >= fireworkStartY + blockheight.getValue()) {
            fireworkResetDelayTicks = FIREWORK_RESET_SETTLE_TICKS;
            setStage(Stage.FIREWORK_RESTORE_ARMOR);
            return;
        }

        launchTicks++;
        if (mc.player.onGround() && launchTicks > MIN_LAUNCH_TICKS) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
        }
    }

    private void tickRestoreFireworkArmor() {
        rotateFireworkAscent();

        if (!isWearingElytra()) {
            fireworkResetDelayTicks = FIREWORK_RESET_SETTLE_TICKS;
            setStage(Stage.FIREWORK_REEQUIP_ELYTRA);
            return;
        }

        if (stageTicks > FIREWORK_EQUIP_TIMEOUT_TICKS) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        ItemStack restoreStack = InventoryUtility.getStack(elytraSlot);
        if (!isOriginalChestStack(restoreStack)) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != elytraSlot
                && !selectHotbarSlot(elytraSlot)) {
            return;
        }

        if (!useSelectedItemWithRetryDelay()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
        }
    }

    private void tickReequipFireworkElytra() {
        aimAtTarget();

        if (isWearingElytra()) {
            setStage(Stage.FIREWORK_START_DIVE);
            return;
        }

        if (fireworkResetDelayTicks > 0) {
            fireworkResetDelayTicks--;
            return;
        }

        if (stageTicks > FIREWORK_EQUIP_TIMEOUT_TICKS) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (!canHoldHotbarItem(elytraSlot, Items.ELYTRA)) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != elytraSlot
                && !selectHotbarSlot(elytraSlot)) {
            return;
        }

        if (!useSelectedItemWithRetryDelay()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
        }
    }

    private void tickStartFireworkDive() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!isWearingElytra() || mc.player.onGround()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (!mc.player.isFallFlying()) {
            requestFallFlyingStart();
            return;
        }
        resetFireworkGlideJumpState();

        setStage(Stage.FIREWORK_DIVE_FIREWORK);
    }

    private void tickUseDiveFirework() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!isWearingElytra() || mc.player.onGround()) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        if (!mc.player.isFallFlying()) {
            requestFallFlyingStart();
            return;
        }
        resetFireworkGlideJumpState();

        if (!ensureFireworkRocketSlot()) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != fireworkRocketSlot
                && !selectHotbarSlot(fireworkRocketSlot)) {
            return;
        }

        if (!useSelectedItem()) {
            setStage(Stage.FIREWORK_START_DIVE);
            return;
        }

        fireworkDiveBoostUsed = true;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;

        if (!selectFireworkArmorSlot()) {
            setStage(Stage.SWITCH_TO_MACE);
            return;
        }

        setStage(Stage.APPROACH_TARGET);
    }

    private void tickPostAttackFireworkArmorHold() {
        rotateFireworkAscent();

        if (isWearingElytra()) {
            equipFireworkChestArmor();
            return;
        }

        if (stageTicks < FIREWORK_POST_ATTACK_ARMOR_HOLD_TICKS) {
            return;
        }

        if (target == null) {
            clearPostAttackLaunchStart();
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        double postAttackLaunchHeight = postAttackLaunchHeight();
        if (shouldWaitForPostAttackLaunch(postAttackLaunchHeight)) {
            return;
        }

        if (tryUsePostAttackLaunchForDive(postAttackLaunchHeight)) {
            return;
        }

        if (!canHoldHotbarItem(elytraSlot, Items.ELYTRA)) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return;
        }

        prepareNextFireworkAscent();
        if (InventoryUtility.getSelectedHotbarSlot() != elytraSlot
                && !selectHotbarSlot(elytraSlot)) {
            if (isQueuedHotbarSlot(elytraSlot)) {
                return;
            }

            clearPostAttackLaunchStart();
            setStage(Stage.FIREWORK_EQUIP_ELYTRA);
            return;
        }

        if (useSelectedItem()) {
            clearPostAttackLaunchStart();
            setStage(Stage.FIREWORK_START_ASCENT);
        } else {
            clearPostAttackLaunchStart();
            setStage(Stage.FIREWORK_EQUIP_ELYTRA);
        }
    }

    private void tickWaitLaunch() {
        rotateDown();
        selectMaceSlot();

        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y > LAUNCH_VELOCITY_THRESHOLD || !mc.player.onGround()) {
            setStage(Stage.APPROACH_TARGET);
            return;
        }

        launchTicks++;
        if (launchTicks > WIND_CHARGE_USE_TIMEOUT_TICKS) {
            setStage(initialAttackStage());
        }
    }

    private void tickApproachTarget() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();
        if (shouldDelayFireworkMaceSwitch(target)) {
            selectFireworkArmorSlot();
        } else if (ensureFireworkChestArmorBeforeMace()) {
            selectMaceSlot();
        }

        if (canAttackTarget(target)) {
            attackOrEnterAttackStage();
            return;
        }

        launchTicks++;
        if (mc.player.onGround() && launchTicks > MIN_LAUNCH_TICKS) {
            setStage(initialAttackStage());
        }
    }

    private void tickSwitchToMace() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();
        if (!ensureFireworkChestArmorBeforeMace()) {
            return;
        }

        selectMaceSlot();

        if (canAttackTarget(target)) {
            attackOrEnterAttackStage();
            return;
        }

        if (!isTargetInAttackRange(target)) {
            setStage(mc.player.onGround() ? initialAttackStage() : Stage.APPROACH_TARGET);
            return;
        }

        if (mc.player.onGround()) {
            setStage(initialAttackStage());
        }
    }

    private void tickAttack() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!canAttackTarget(target)) {
            waitForAttackWindowOrRetry();
            return;
        }

        if (attackTarget(target)) {
            if (isFireworkDiveAttack()) {
                beginPostAttackFireworkReset();
            } else {
                attackHoldTicks = ATTACK_HOLD_TICKS;
                setStage(Stage.AFTER_ATTACK);
            }
        }
    }

    private void attackOrEnterAttackStage() {
        if (isFireworkDiveAttack()) {
            tickAttack();
        } else {
            setStage(Stage.ATTACK);
        }
    }

    private void tickAfterAttack() {
        aimAtTarget();

        if (attackHoldTicks > 0) {
            attackHoldTicks--;
            return;
        }

        if (target == null || !target.isAlive() || target.isRemoved()) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        if (mc.player.onGround() || stageTicks > AFTER_ATTACK_TIMEOUT_TICKS) {
            setStage(initialAttackStage());
        }
    }

    private boolean canRecoverFireworkAfterAttackWithoutTarget() {
        return switch (stage) {
            case FIREWORK_AFTER_ATTACK_HOLD_ARMOR -> true;
            default -> false;
        };
    }

    private DetectedLoadout detectLoadout() {
        boolean hasMace = InventoryUtility.findInventorySlot(Items.MACE) != InventoryUtility.NOT_FOUND;
        boolean hasWindCharge = InventoryUtility.findInventorySlot(Items.WIND_CHARGE) != InventoryUtility.NOT_FOUND;
        boolean hasFireworkRocket = InventoryUtility.findInventorySlot(Items.FIREWORK_ROCKET) != InventoryUtility.NOT_FOUND;
        boolean canContinueFireworkRoute = isFireworkRouteInProgress() && hasMace;

        if (!hasMace || (!hasWindCharge && !hasFireworkRocket && !canContinueFireworkRoute)) {
            return null;
        }

        ItemType requestedItemType = itemType.getValue();
        if (requestedItemType == ItemType.FIREWORK_ROCKET && (hasFireworkRocket || canContinueFireworkRoute)) {
            return new DetectedLoadout(DuelMode.ELYTRA, ItemType.FIREWORK_ROCKET);
        }

        if (requestedItemType == ItemType.WIND_CHARGE && hasWindCharge) {
            return new DetectedLoadout(DuelMode.ELYTRA, ItemType.WIND_CHARGE);
        }

        return new DetectedLoadout(DuelMode.ELYTRA, hasWindCharge ? ItemType.WIND_CHARGE : ItemType.FIREWORK_ROCKET);
    }

    private void syncDetectedValues(DetectedLoadout loadout) {
        duelMode.setValueSilently(loadout.duelMode());
        itemType.setValueSilently(loadout.itemType());
    }

    private LivingEntity refreshTarget() {
        if (isValidTarget(target, targetRetentionRange())) {
            return target;
        }

        return findTarget(targetRange.getValue());
    }

    private double targetRetentionRange() {
        double baseRange = targetRange.getValue() * 1.5D;
        if (itemType.getValue() != ItemType.FIREWORK_ROCKET && !isFireworkRouteInProgress()) {
            return baseRange;
        }

        return Math.max(baseRange, targetRange.getValue() + blockheight.getValue() + attackRange.getValue());
    }

    private LivingEntity findTarget(double range) {
        AABB searchBox = mc.player.getBoundingBox().inflate(range);
        List<Entity> entities = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity, range)
        );

        return entities.stream()
                .map(LivingEntity.class::cast)
                .min(Comparator.comparingDouble(RotationUtility::getEyeDistanceToEntity))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity, double range) {
        return entity != null
                && entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity)
                && !entity.isRemoved()
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && RotationUtility.getEyeDistanceToEntity(entity) <= range;
    }

    private boolean selectMaceSlot() {
        if (maceSwitchDelayTicks > 0) {
            return false;
        }

        if (InventoryUtility.getSelectedStack().is(Items.MACE)) {
            return true;
        }

        if (!canHoldHotbarItem(maceSlot, Items.MACE)) {
            maceSlot = itemType.getValue() == ItemType.FIREWORK_ROCKET
                    ? ensureHotbarItem(Items.MACE, fireworkRocketSlot, elytraSlot)
                    : ensureHotbarItem(Items.MACE, windChargeSlot);
        }

        if (!canHoldHotbarItem(maceSlot, Items.MACE)) {
            return false;
        }

        boolean selected = InventoryUtility.getSelectedHotbarSlot() == maceSlot
                || selectHotbarSlot(maceSlot);
        if (selected) {
            heldMaceTicks = 0;
        }

        return selected;
    }

    private boolean selectFireworkArmorSlot() {
        if (!Inventory.isHotbarSlot(elytraSlot) || !isOriginalChestStack(InventoryUtility.getStack(elytraSlot))) {
            return false;
        }

        return InventoryUtility.getSelectedHotbarSlot() == elytraSlot
                || selectHotbarSlot(elytraSlot);
    }

    private boolean equipFireworkChestArmor() {
        return selectFireworkArmorSlot() && useSelectedItemWithRetryDelay();
    }

    private boolean ensureFireworkChestArmorBeforeMace() {
        if (!isFireworkDiveAttack() || fireworkAllowElytraMaceAttack || !isWearingElytra()) {
            return true;
        }

        equipFireworkChestArmor();
        return false;
    }

    private boolean shouldDelayFireworkMaceSwitch(LivingEntity target) {
        return itemType.getValue() == ItemType.FIREWORK_ROCKET
                && fireworkDiveBoostUsed
                && !fireworkAllowElytraMaceAttack
                && isWearingElytra()
                && !isTargetInFireworkMaceSwitchRange(target);
    }

    private boolean isTargetInFireworkMaceSwitchRange(LivingEntity target) {
        return RotationUtility.getEyeDistanceToEntity(target)
                <= FIREWORK_ARMOR_SWITCH_RANGE;
    }

    private boolean isFireworkDiveAttack() {
        return itemType.getValue() == ItemType.FIREWORK_ROCKET && fireworkDiveBoostUsed;
    }

    private double postAttackLaunchHeight() {
        if (!hasPostAttackLaunchStart()) {
            return 0.0D;
        }

        return Math.max(0.0D, mc.player.getY() - fireworkPostAttackStartY);
    }

    private boolean hasPostAttackLaunchStart() {
        return !Double.isNaN(fireworkPostAttackStartY);
    }

    private void clearPostAttackLaunchStart() {
        fireworkPostAttackStartY = Double.NaN;
    }

    private boolean tryUsePostAttackLaunchForDive(double launchHeight) {
        if (!isPostAttackLaunchEnoughForDive(launchHeight)
                || !isPostAttackLaunchStableForDive()) {
            return false;
        }

        if (!canHoldHotbarItem(elytraSlot, Items.ELYTRA)) {
            resetFireworkState();
            setStage(Stage.FIREWORK_PREPARE);
            return true;
        }

        preparePostAttackLaunchDive();
        if (InventoryUtility.getSelectedHotbarSlot() != elytraSlot
                && !selectHotbarSlot(elytraSlot)) {
            if (!isQueuedHotbarSlot(elytraSlot)) {
                clearPostAttackLaunchStart();
                setStage(Stage.FIREWORK_EQUIP_ELYTRA);
            }
            return true;
        }

        if (useSelectedItem()) {
            MsgUtility.debug(
                    "MaceAura using post-attack launch: height=",
                    format(launchHeight),
                    ", block=",
                    blockheight.getValue()
            );
            clearPostAttackLaunchStart();
            setStage(Stage.FIREWORK_START_DIVE);
        } else {
            clearPostAttackLaunchStart();
            setStage(Stage.FIREWORK_EQUIP_ELYTRA);
        }
        return true;
    }

    private boolean isPostAttackLaunchEnoughForDive(double launchHeight) {
        double requiredHeight = Math.max(
                FIREWORK_POST_ATTACK_DIRECT_DIVE_MIN_HEIGHT,
                blockheight.getValue() - FIREWORK_POST_ATTACK_LAUNCH_MAX_SHORTFALL
        );
        return launchHeight >= requiredHeight;
    }

    private boolean shouldWaitForPostAttackLaunch(double launchHeight) {
        if (!hasPostAttackLaunchStart()
                || mc.player.onGround()) {
            return false;
        }

        double upwardVelocity = mc.player.getDeltaMovement().y;
        if (upwardVelocity <= FIREWORK_POST_ATTACK_LAUNCH_UPWARD_VELOCITY) {
            return false;
        }

        if (isPostAttackLaunchEnoughForDive(launchHeight)) {
            return upwardVelocity > FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_UPWARD_VELOCITY
                    && stageTicks < FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_WAIT_TICKS;
        }

        return stageTicks < FIREWORK_POST_ATTACK_LAUNCH_WAIT_TICKS
                && launchHeight < blockheight.getValue();
    }

    private boolean isPostAttackLaunchStableForDive() {
        return mc.player.getDeltaMovement().y <= FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_UPWARD_VELOCITY
                || stageTicks >= FIREWORK_POST_ATTACK_DIRECT_DIVE_MAX_WAIT_TICKS;
    }

    private void preparePostAttackLaunchDive() {
        fireworkStartY = hasPostAttackLaunchStart() ? fireworkPostAttackStartY : mc.player.getY();
        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;
        attackHoldTicks = 0;
    }

    private void beginPostAttackFireworkReset() {
        if (fireworkAllowElytraMaceAttack) {
            fireworkDiveBoostUsed = false;
            fireworkAllowElytraMaceAttack = false;
            clearPostAttackLaunchStart();
            attackHoldTicks = ATTACK_HOLD_TICKS;
            setStage(Stage.AFTER_ATTACK);
            return;
        }

        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;
        fireworkPostAttackStartY = mc.player.getY();
        attackHoldTicks = 0;
        setStage(Stage.FIREWORK_AFTER_ATTACK_HOLD_ARMOR);
    }

    private void prepareNextFireworkAscent() {
        fireworkStartY = hasPostAttackLaunchStart() ? fireworkPostAttackStartY : mc.player.getY();
        fireworkAscendYaw = targetYaw();
        fireworkAscendPitch = randomFireworkAscendPitch();
        fireworkResetDelayTicks = 0;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;
        attackHoldTicks = 0;
    }

    private boolean attackTarget(LivingEntity target) {
        if (!InventoryUtility.getSelectedStack().is(Items.MACE)) {
            return false;
        }

        Vector2f rotations = RotationUtility.calculate(target, true, effectiveAttackRange());
        applyCombatRotation(rotations);

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        MsgUtility.debug(
                "MaceAura attacked ",
                target.getName().getString(),
                ": range=",
                format(RotationUtility.getEyeDistanceToEntity(target)),
                ", fall=",
                format(mc.player.fallDistance)
        );
        return true;
    }

    private boolean isTargetInAttackRange(LivingEntity target) {
        return RotationUtility.getEyeDistanceToEntity(target) <= effectiveAttackRange();
    }

    private double effectiveAttackRange() {
        return Math.min(attackRange.getValue(), GRIM_ATTACK_RANGE);
    }

    private boolean canAttackTarget(LivingEntity target) {
        return isTargetInAttackRange(target)
                && isHeldMaceSettled()
                && isWindChargeAttackSettled()
                && isFireworkArmorReadyForAttack()
                && isMaceSmashReady(target);
    }

    private boolean isFireworkArmorReadyForAttack() {
        return !isFireworkDiveAttack() || fireworkAllowElytraMaceAttack || !isWearingElytra();
    }

    private boolean isHeldMaceSettled() {
        int requiredTicks = isFireworkDiveAttack() ? FIREWORK_MACE_SWITCH_SETTLE_TICKS : MACE_SWITCH_SETTLE_TICKS;
        return InventoryUtility.getSelectedStack().is(Items.MACE)
                && heldMaceTicks >= requiredTicks;
    }

    private boolean isWindChargeAttackSettled() {
        return windChargeAttackDelayTicks <= 0;
    }

    private boolean isMaceSmashReady(LivingEntity target) {
        Vec3 velocity = mc.player.getDeltaMovement();
        boolean hasSmashFallDistance = mc.player.fallDistance >= MIN_MACE_FALL_DISTANCE;
        boolean descendingFromHeight = velocity.y <= FALLING_ATTACK_VELOCITY
                && mc.player.getY() > target.getY() + MIN_MACE_FALL_DISTANCE;
        return !mc.player.onGround() && (hasSmashFallDistance || descendingFromHeight);
    }

    private void waitForAttackWindowOrRetry() {
        if (!isTargetInAttackRange(target)) {
            setStage(mc.player.onGround() ? initialAttackStage() : Stage.APPROACH_TARGET);
            return;
        }

        if (mc.player.onGround()) {
            setStage(initialAttackStage());
        }
    }

    private int ensureHotbarItem(Item item, int... reservedSlots) {
        int hotbarSlot = InventoryUtility.findHotbarSlot(item);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return hotbarSlot;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(item);
        if (!InventoryUtility.isMainInventorySlot(inventorySlot)) {
            return InventoryUtility.NOT_FOUND;
        }

        int targetHotbarSlot = findHotbarTargetSlot(reservedSlots);
        if (!Inventory.isHotbarSlot(targetHotbarSlot)) {
            return InventoryUtility.NOT_FOUND;
        }

        return InventoryUtility.moveInventorySlotToHotbar(inventorySlot, targetHotbarSlot)
                ? targetHotbarSlot
                : InventoryUtility.NOT_FOUND;
    }

    private int findHotbarTargetSlot(int... reservedSlots) {
        int emptySlot = InventoryUtility.findEmptyHotbarSlot();
        if (Inventory.isHotbarSlot(emptySlot) && !isReservedHotbarSlot(emptySlot, reservedSlots)) {
            return emptySlot;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (Inventory.isHotbarSlot(selectedSlot) && !isReservedHotbarSlot(selectedSlot, reservedSlots)) {
            return selectedSlot;
        }

        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.HOTBAR_END; slot++) {
            if (!isReservedHotbarSlot(slot, reservedSlots)) {
                return slot;
            }
        }

        return InventoryUtility.NOT_FOUND;
    }

    private boolean isReservedHotbarSlot(int hotbarSlot, int... reservedSlots) {
        for (int reservedSlot : reservedSlots) {
            if (hotbarSlot == reservedSlot) {
                return true;
            }
        }

        return false;
    }

    private boolean canUseHotbarItem(int hotbarSlot, Item item) {
        if (!canHoldHotbarItem(hotbarSlot, item)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        return !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private boolean canHoldHotbarItem(int hotbarSlot, Item item) {
        if (mc.player == null || mc.level == null || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(hotbarSlot);
        return !stack.isEmpty()
                && stack.is(item)
                && stack.isItemEnabled(mc.level.enabledFeatures());
    }

    private boolean ensureFireworkRocketSlot() {
        if (!canUseHotbarItem(fireworkRocketSlot, Items.FIREWORK_ROCKET)) {
            fireworkRocketSlot = ensureHotbarItem(Items.FIREWORK_ROCKET, elytraSlot, maceSlot);
        }

        return canUseHotbarItem(fireworkRocketSlot, Items.FIREWORK_ROCKET);
    }

    private boolean restoreFireworkArmorBeforePrepare() {
        if (!isWearingElytra()) {
            return false;
        }

        int restoreSlot = findFireworkChestRestoreSlot();
        if (!InventoryUtility.isValidInventorySlot(restoreSlot)) {
            return true;
        }

        InventoryUtility.swapInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, restoreSlot);
        return true;
    }

    private int findHotbarChestArmorSlot() {
        return InventoryUtility.findHotbarSlot(this::isChestArmorStack);
    }

    private boolean canHoldChestArmor(int hotbarSlot) {
        if (mc.player == null || !Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        return isChestArmorStack(InventoryUtility.getStack(hotbarSlot));
    }

    private boolean isWearingElytra() {
        return getChestStack().is(Items.ELYTRA);
    }

    private boolean hasChestArmorForFireworkReset() {
        ItemStack chestStack = getChestStack();
        return !chestStack.isEmpty() && !chestStack.is(Items.ELYTRA);
    }

    private ItemStack getChestStack() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(EquipmentSlot.CHEST);
    }

    private boolean isOriginalChestStack(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, fireworkOriginalChestStack);
    }

    private int findFireworkChestRestoreSlot() {
        if (isKnownFireworkChestRestoreSlot(elytraSlot)) {
            return elytraSlot;
        }

        if (!fireworkOriginalChestStack.isEmpty()) {
            int originalSlot = InventoryUtility.findSlot(this::isOriginalChestStack);
            if (isUsableFireworkChestRestoreSlot(originalSlot)) {
                return originalSlot;
            }
        }

        int chestArmorSlot = InventoryUtility.findSlot(this::isChestArmorStack);
        if (isUsableFireworkChestRestoreSlot(chestArmorSlot)) {
            return chestArmorSlot;
        }

        int emptySlot = InventoryUtility.findEmptySlot();
        return isUsableFireworkChestRestoreSlot(emptySlot) ? emptySlot : InventoryUtility.NOT_FOUND;
    }

    private boolean isKnownFireworkChestRestoreSlot(int inventorySlot) {
        if (!isUsableFireworkChestRestoreSlot(inventorySlot)) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(inventorySlot);
        return fireworkOriginalChestStack.isEmpty()
                ? stack.isEmpty() || isChestArmorStack(stack)
                : isOriginalChestStack(stack);
    }

    private boolean isUsableFireworkChestRestoreSlot(int inventorySlot) {
        return InventoryUtility.isValidInventorySlot(inventorySlot)
                && inventorySlot != InventoryUtility.ARMOR_CHEST_SLOT;
    }

    private boolean isChestArmorStack(ItemStack stack) {
        return mc.player != null
                && !stack.isEmpty()
                && !stack.is(Items.ELYTRA)
                && mc.player.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
    }

    private LivingEntity crosshairFireworkTarget() {
        double range = targetRange.getValue();
        HitResult hitResult = PlayerUtility.raycastForEntity(
                mc.level,
                mc.player,
                (float) range,
                true,
                entity -> entity instanceof LivingEntity livingEntity
                        && isValidTarget(livingEntity, range)
        );
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity, range)) {
            return livingEntity;
        }

        return null;
    }

    private float randomFireworkAscendPitch() {
        return -(float) ThreadLocalRandom.current().nextDouble(FIREWORK_MIN_ASCEND_ANGLE, FIREWORK_MAX_ASCEND_ANGLE);
    }

    private void requestFallFlyingStart() {
        if (mc.player == null || mc.player.onGround()) {
            return;
        }

        if (mc.player.isFallFlying()) {
            resetFireworkGlideJumpState();
            return;
        }

        if (!fireworkReleasedJumpForGlide) {
            fireworkReleasedJumpForGlide = true;
            return;
        }

        fireworkPressJumpForGlide = true;
        fireworkReleasedJumpForGlide = false;
    }

    private boolean useSelectedItem() {
        if (mc.player == null || mc.player.connection == null || mc.level == null) {
            return false;
        }

        if (!InventoryUtility.syncSelectedSlot()) {
            return false;
        }

        Vector2f rotations = legitimizeRotations(RotationManager.INSTANCE.getRotation());
        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.player.connection.send(new ServerboundUseItemPacket(
                    InteractionHand.MAIN_HAND,
                    prediction.currentSequence(),
                    rotations.x,
                    rotations.y
            ));
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
        postUseItemSlotDelayTicks = POST_USE_ITEM_SLOT_DELAY_TICKS;
        return true;
    }

    private boolean useSelectedItemWithRetryDelay() {
        if (fireworkUseRetryDelayTicks > 0) {
            return true;
        }

        if (!useSelectedItem()) {
            return false;
        }

        fireworkUseRetryDelayTicks = FIREWORK_USE_RETRY_DELAY_TICKS;
        return true;
    }

    private Vector2f currentPlayerRotations() {
        if (mc.player == null) {
            return new Vector2f();
        }

        return new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private boolean selectHotbarSlot(int hotbarSlot) {
        if (!Inventory.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        if (InventoryUtility.getSelectedHotbarSlot() == hotbarSlot) {
            return true;
        }

        if (postUseItemSlotDelayTicks > 0) {
            queuedHotbarSlot = hotbarSlot;
            return false;
        }

        return InventoryUtility.selectHotbarSlot(hotbarSlot, true);
    }

    private void applyQueuedHotbarSlot() {
        if (!Inventory.isHotbarSlot(queuedHotbarSlot)) {
            queuedHotbarSlot = InventoryUtility.NOT_FOUND;
            return;
        }

        if (postUseItemSlotDelayTicks > 0) {
            return;
        }

        InventoryUtility.selectHotbarSlot(queuedHotbarSlot, true);
        queuedHotbarSlot = InventoryUtility.NOT_FOUND;
    }

    private boolean isQueuedHotbarSlot(int hotbarSlot) {
        return Inventory.isHotbarSlot(hotbarSlot) && queuedHotbarSlot == hotbarSlot;
    }

    private void clearQueuedWindCharge() {
        windChargeOriginalSlot = InventoryUtility.NOT_FOUND;
        windChargeSlot = InventoryUtility.NOT_FOUND;
        windChargeUseDelayTicks = 0;
    }

    private void restoreQueuedWindChargeSlotNow() {
        if (mc.player != null
                && Inventory.isHotbarSlot(windChargeOriginalSlot)
                && Inventory.isHotbarSlot(windChargeSlot)
                && windChargeOriginalSlot != windChargeSlot
                && InventoryUtility.getSelectedHotbarSlot() == windChargeSlot) {
            selectHotbarSlot(windChargeOriginalSlot);
        }

        clearQueuedWindCharge();
    }

    private void tickHeldMaceState() {
        if (InventoryUtility.getSelectedStack().is(Items.MACE)) {
            heldMaceTicks++;
        } else {
            heldMaceTicks = 0;
        }
    }

    private void tickActionDelays() {
        tickFireworkFallFlyingState();

        if (maceSwitchDelayTicks > 0) {
            maceSwitchDelayTicks--;
        }

        if (windChargeAttackDelayTicks > 0) {
            windChargeAttackDelayTicks--;
        }

        if (postUseItemSlotDelayTicks > 0) {
            postUseItemSlotDelayTicks--;
        }

        if (fireworkUseRetryDelayTicks > 0) {
            fireworkUseRetryDelayTicks--;
        }
    }

    private void tickFireworkFallFlyingState() {
        if (mc.player != null && mc.player.isFallFlying()) {
            fireworkFallFlyingTicks++;
        } else {
            fireworkFallFlyingTicks = 0;
        }
    }

    private void rotateDown() {
        applyCombatRotation(new Vector2f(targetYaw(), DOWN_PITCH));
    }

    private void rotateFireworkAscent() {
        Vector2f rotations = new Vector2f(fireworkAscendYaw, fireworkAscendPitch);
        applyCombatRotation(rotations);
    }

    private void aimAtTarget() {
        if (target != null) {
            Vector2f rotations = RotationUtility.calculate(target.getBoundingBox().getCenter());
            applyCombatRotation(rotations);
        }
    }

    private Vector2f applyCombatRotation(Vector2f rotations) {
        Vector2f fixedRotations = legitimizeRotations(rotations);
        RotationManager.INSTANCE.setRotations(fixedRotations, 180.0D, Priority.Highest);
        return RotationManager.INSTANCE.getRotation();
    }

    private Vector2f legitimizeRotations(Vector2f rotations) {
        if (mc.player == null || rotations == null) {
            return new Vector2f();
        }

        Vector2f baseRotations = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : currentPlayerRotations();
        float yaw = baseRotations.x + Mth.wrapDegrees(rotations.x - baseRotations.x);
        float pitch = Mth.clamp(rotations.y, -90.0F, 90.0F);
        return new Vector2f(yaw, pitch);
    }

    private float targetYaw() {
        if (target == null) {
            return RotationManager.INSTANCE.getRotation().x;
        }

        return legitimizeRotations(RotationUtility.calculate(target.getBoundingBox().getCenter())).x;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean canJumpFromGround() {
        return mc.player.onGround()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying();
    }

    private void jumpForWindCharge() {
        mc.player.setSprinting(false);
        mc.player.jumpFromGround();
    }

    private Stage initialAttackStage() {
        return itemType.getValue() == ItemType.FIREWORK_ROCKET ? Stage.FIREWORK_PREPARE : Stage.USE_WIND_CHARGE;
    }

    private boolean shouldControlInput() {
        return switch (stage) {
            case JUMP,
                 WIND_CHARGE_USE,
                 WAIT_LAUNCH,
                 FIREWORK_START_ASCENT,
                 FIREWORK_ASCENT,
                 FIREWORK_RESTORE_ARMOR,
                 FIREWORK_REEQUIP_ELYTRA,
                 FIREWORK_START_DIVE,
                 FIREWORK_DIVE_FIREWORK,
                 FIREWORK_AFTER_ATTACK_HOLD_ARMOR,
                 APPROACH_TARGET,
                 SWITCH_TO_MACE,
                 ATTACK,
                 AFTER_ATTACK -> true;
            default -> false;
        };
    }

    private boolean shouldJump() {
        if (stage == Stage.JUMP) {
            return true;
        }

        if (stage == Stage.FIREWORK_START_ASCENT && mc.player != null && mc.player.onGround()) {
            return true;
        }

        return fireworkPressJumpForGlide;
    }

    private boolean shouldMoveForward() {
        return target != null && switch (stage) {
            case FIREWORK_START_DIVE,
                 FIREWORK_DIVE_FIREWORK,
                 APPROACH_TARGET,
                 SWITCH_TO_MACE,
                 ATTACK -> true;
            default -> false;
        };
    }

    private boolean shouldControlMovementYaw() {
        return shouldMoveForward();
    }

    private void recoverFromTimeout() {
        if (mc.player != null && mc.player.onGround()) {
            setStage(initialAttackStage());
        } else {
            setStage(Stage.ACQUIRE_TARGET);
        }
    }

    private void resetCombatCycle() {
        target = null;
        stageTicks = 0;
        launchTicks = 0;
        maceSwitchDelayTicks = 0;
        windChargeAttackDelayTicks = 0;
        heldMaceTicks = 0;
        attackHoldTicks = 0;
        postUseItemSlotDelayTicks = 0;
        fireworkUseRetryDelayTicks = 0;
        fireworkFallFlyingTicks = 0;
        queuedHotbarSlot = InventoryUtility.NOT_FOUND;
        restoreQueuedWindChargeSlotNow();
        resetFireworkState();
        stage = Stage.ACQUIRE_TARGET;
    }

    private void resetRuntimeState() {
        resetCombatCycle();
        originalSelectedSlot = InventoryUtility.NOT_FOUND;
        maceSlot = InventoryUtility.NOT_FOUND;
        warnedMissingLoadout = false;
    }

    private void resetFireworkState() {
        fireworkRocketSlot = InventoryUtility.NOT_FOUND;
        elytraSlot = InventoryUtility.NOT_FOUND;
        fireworkResetDelayTicks = 0;
        fireworkStartY = 0.0D;
        clearPostAttackLaunchStart();
        fireworkAscendYaw = 0.0F;
        fireworkAscendPitch = 0.0F;
        fireworkOriginalChestStack = ItemStack.EMPTY;
        fireworkDiveBoostUsed = false;
        fireworkAllowElytraMaceAttack = false;
        fireworkUseRetryDelayTicks = 0;
        fireworkFallFlyingTicks = 0;
        resetFireworkGlideJumpState();
    }

    private void resetFireworkGlideJumpState() {
        fireworkReleasedJumpForGlide = false;
        fireworkPressJumpForGlide = false;
    }

    private boolean isFireworkRouteInProgress() {
        return itemType.getValue() == ItemType.FIREWORK_ROCKET && (fireworkDiveBoostUsed || switch (stage) {
            case FIREWORK_EQUIP_ELYTRA,
                 FIREWORK_START_ASCENT,
                 FIREWORK_ASCENT,
                 FIREWORK_RESTORE_ARMOR,
                 FIREWORK_REEQUIP_ELYTRA,
                 FIREWORK_START_DIVE,
                 FIREWORK_DIVE_FIREWORK,
                 FIREWORK_AFTER_ATTACK_HOLD_ARMOR -> true;
            default -> false;
        });
    }

    private void setStage(Stage stage) {
        if (this.stage == stage) {
            return;
        }

        this.stage = stage;
        stageTicks = 0;
        fireworkUseRetryDelayTicks = 0;
        if (stage == Stage.FIREWORK_START_ASCENT || stage == Stage.FIREWORK_START_DIVE) {
            resetFireworkGlideJumpState();
        }
    }

    private void restoreOriginalSelectedSlot() {
        if (Inventory.isHotbarSlot(originalSelectedSlot)) {
            InventoryUtility.selectHotbarSlot(originalSelectedSlot);
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public enum DuelMode {
        ELYTRA
    }

    public enum ItemType {
        WIND_CHARGE, FIREWORK_ROCKET
    }

    private enum Stage {
        ACQUIRE_TARGET,
        USE_WIND_CHARGE,
        JUMP,
        WIND_CHARGE_USE,
        WAIT_LAUNCH,
        FIREWORK_PREPARE,
        FIREWORK_EQUIP_ELYTRA,
        FIREWORK_START_ASCENT,
        FIREWORK_ASCENT,
        FIREWORK_RESTORE_ARMOR,
        FIREWORK_REEQUIP_ELYTRA,
        FIREWORK_START_DIVE,
        FIREWORK_DIVE_FIREWORK,
        FIREWORK_AFTER_ATTACK_HOLD_ARMOR,
        APPROACH_TARGET,
        SWITCH_TO_MACE,
        ATTACK,
        AFTER_ATTACK
    }

    private record DetectedLoadout(DuelMode duelMode, ItemType itemType) {
    }
}
