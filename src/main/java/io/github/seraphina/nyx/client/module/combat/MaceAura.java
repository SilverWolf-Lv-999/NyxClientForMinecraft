package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.StrafeEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.DebugUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private int windChargeUseDelayTicks;
    private int stageTicks;
    private int launchTicks;
    private int maceSwitchDelayTicks;
    private int windChargeAttackDelayTicks;
    private int heldMaceTicks;
    private int attackHoldTicks;
    private boolean warnedMissingLoadout;
    private boolean warnedFireworkOnly;

    @Override
    public void onEnable() {
        resetRuntimeState();
        originalSelectedSlot = InventoryUtility.getSelectedHotbarSlot();
        DebugUtility.msg("MaceAura enabled");
    }

    @Override
    public void onDisable() {
        restoreOriginalSelectedSlot();
        RotationManager.INSTANCE.setActive(false);
        resetRuntimeState();
        DebugUtility.msg("MaceAura disabled");
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!canRun() || !shouldControlInput()) {
            return;
        }

        event.setForward(shouldApproachTarget() ? 1.0F : 0.0F);
        event.setStrafe(0.0F);
        event.setSprint(shouldApproachTarget());
        event.setSneak(false);
        event.setJump(stage == Stage.JUMP);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!canRun() || !shouldApproachTarget()) {
            return;
        }

        event.setYaw(targetYaw());
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!canRun()) {
            return;
        }

        switch (stage) {
            case JUMP -> tickJumpBeforeMove();
            case WIND_CHARGE_USE -> tickQueuedWindChargeUseBeforeMove();
            case ATTACK -> tickAttackBeforeMove();
            default -> {
            }
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            DebugUtility.msg("MaceAura disabled: server corrected position");
            setEnabled(false);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        tickActionDelays();

        if (!canRun()) {
            resetCombatCycle();
            return;
        }

        DetectedLoadout loadout = detectLoadout();
        if (loadout == null) {
            if (!warnedMissingLoadout) {
                DebugUtility.msg("MaceAura waiting: need mace and wind charge/firework rocket");
                warnedMissingLoadout = true;
            }
            resetCombatCycle();
            return;
        }

        warnedMissingLoadout = false;
        syncDetectedValues(loadout);
        tickHeldMaceState();

        if (loadout.itemType() != ItemType.WIND_CHARGE) {
            if (!warnedFireworkOnly) {
                DebugUtility.msg("MaceAura waiting: firework route is not implemented yet");
                warnedFireworkOnly = true;
            }
            resetCombatCycle();
            return;
        }

        warnedFireworkOnly = false;
        stageTicks++;

        if (stageTicks > STAGE_TIMEOUT_TICKS) {
            recoverFromTimeout();
            return;
        }

        target = refreshTarget();
        if (target == null && stage != Stage.ACQUIRE_TARGET) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        tickStage();
    }

    private void tickStage() {
        switch (stage) {
            case ACQUIRE_TARGET -> {
                if (target != null) {
                    setStage(Stage.USE_WIND_CHARGE);
                }
            }
            case USE_WIND_CHARGE -> tickUseWindCharge();
            case JUMP -> tickJumpForWindCharge();
            case WIND_CHARGE_USE -> tickAwaitQueuedWindChargeUse();
            case WAIT_LAUNCH -> tickWaitLaunch();
            case APPROACH_TARGET -> tickApproachTarget();
            case SWITCH_TO_MACE -> tickSwitchToMace();
            case ATTACK -> tickAwaitAttack();
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
        if (!InventoryUtility.selectHotbarSlot(foundWindChargeSlot)) {
            clearQueuedWindCharge();
            return;
        }

        setStage(Stage.JUMP);
    }

    private void tickJumpBeforeMove() {
        rotateDown();
        if (stageTicks == 0 && canJumpFromGround()) {
            jumpForWindCharge();
        }
    }

    private void tickJumpForWindCharge() {
        rotateDown();

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

    private void tickAwaitQueuedWindChargeUse() {
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
        }
    }

    private void tickQueuedWindChargeUseBeforeMove() {
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
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickApproachTarget() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();
        selectMaceSlot();

        if (canAttackTarget(target)) {
            setStage(Stage.ATTACK);
            return;
        }

        launchTicks++;
        if (mc.player.onGround() && launchTicks > MIN_LAUNCH_TICKS) {
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickSwitchToMace() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();
        selectMaceSlot();

        if (canAttackTarget(target)) {
            setStage(Stage.ATTACK);
            return;
        }

        if (!isTargetInAttackRange(target)) {
            setStage(mc.player.onGround() ? Stage.USE_WIND_CHARGE : Stage.APPROACH_TARGET);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.USE_WIND_CHARGE);
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
            attackHoldTicks = ATTACK_HOLD_TICKS;
            setStage(Stage.AFTER_ATTACK);
        }
    }

    private void tickAttackBeforeMove() {
        tickAttack();
    }

    private void tickAwaitAttack() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!canAttackTarget(target)) {
            waitForAttackWindowOrRetry();
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
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private DetectedLoadout detectLoadout() {
        boolean hasMace = InventoryUtility.findInventorySlot(Items.MACE) != InventoryUtility.NOT_FOUND;
        boolean hasWindCharge = InventoryUtility.findInventorySlot(Items.WIND_CHARGE) != InventoryUtility.NOT_FOUND;
        boolean hasFireworkRocket = InventoryUtility.findInventorySlot(Items.FIREWORK_ROCKET) != InventoryUtility.NOT_FOUND;

        if (!hasMace || (!hasWindCharge && !hasFireworkRocket)) {
            return null;
        }

        return new DetectedLoadout(DuelMode.ELYTRA, hasWindCharge ? ItemType.WIND_CHARGE : ItemType.FIREWORK_ROCKET);
    }

    private void syncDetectedValues(DetectedLoadout loadout) {
        duelMode.setValueSilently(loadout.duelMode());
        itemType.setValueSilently(loadout.itemType());
    }

    private LivingEntity refreshTarget() {
        if (isValidTarget(target, targetRange.getValue() * 1.5D)) {
            return target;
        }

        return findTarget(targetRange.getValue());
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
            maceSlot = ensureHotbarItem(Items.MACE, windChargeSlot);
        }

        if (!canHoldHotbarItem(maceSlot, Items.MACE)) {
            return false;
        }

        boolean selected = InventoryUtility.getSelectedHotbarSlot() == maceSlot
                || InventoryUtility.selectHotbarSlot(maceSlot);
        if (selected) {
            heldMaceTicks = 0;
        }

        return selected;
    }

    private boolean attackTarget(LivingEntity target) {
        if (!InventoryUtility.getSelectedStack().is(Items.MACE)) {
            return false;
        }

        Vector2f rotations = RotationUtility.calculate(target, true, attackRange.getValue());
        RotationManager.INSTANCE.setRotations(rotations, 180.0D, Priority.Highest);

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        DebugUtility.msg(
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
        return RotationUtility.getEyeDistanceToEntity(target) <= attackRange.getValue();
    }

    private boolean canAttackTarget(LivingEntity target) {
        return isTargetInAttackRange(target)
                && isHeldMaceSettled()
                && isWindChargeAttackSettled()
                && isMaceSmashReady(target);
    }

    private boolean isHeldMaceSettled() {
        return InventoryUtility.getSelectedStack().is(Items.MACE)
                && heldMaceTicks >= MACE_SWITCH_SETTLE_TICKS;
    }

    private boolean isWindChargeAttackSettled() {
        return windChargeAttackDelayTicks <= 0;
    }

    private boolean isMaceSmashReady(LivingEntity target) {
        Vec3 velocity = mc.player.getDeltaMovement();
        return velocity.y <= FALLING_ATTACK_VELOCITY
                && (mc.player.fallDistance >= MIN_MACE_FALL_DISTANCE
                || mc.player.getY() > target.getY() + MIN_MACE_FALL_DISTANCE);
    }

    private void waitForAttackWindowOrRetry() {
        if (!isTargetInAttackRange(target)) {
            setStage(mc.player.onGround() ? Stage.USE_WIND_CHARGE : Stage.APPROACH_TARGET);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private int ensureHotbarItem(Item item, int reservedSlot) {
        int hotbarSlot = InventoryUtility.findHotbarSlot(item);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return hotbarSlot;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(item);
        if (!InventoryUtility.isMainInventorySlot(inventorySlot)) {
            return InventoryUtility.NOT_FOUND;
        }

        int targetHotbarSlot = findHotbarTargetSlot(reservedSlot);
        if (!Inventory.isHotbarSlot(targetHotbarSlot)) {
            return InventoryUtility.NOT_FOUND;
        }

        return InventoryUtility.moveInventorySlotToHotbar(inventorySlot, targetHotbarSlot)
                ? targetHotbarSlot
                : InventoryUtility.NOT_FOUND;
    }

    private int findHotbarTargetSlot(int reservedSlot) {
        int emptySlot = InventoryUtility.findEmptyHotbarSlot();
        if (Inventory.isHotbarSlot(emptySlot) && emptySlot != reservedSlot) {
            return emptySlot;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (Inventory.isHotbarSlot(selectedSlot) && selectedSlot != reservedSlot) {
            return selectedSlot;
        }

        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.HOTBAR_END; slot++) {
            if (slot != reservedSlot) {
                return slot;
            }
        }

        return InventoryUtility.NOT_FOUND;
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

    private boolean useSelectedItem() {
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (result instanceof InteractionResult.Success useSuccess) {
            if (useSuccess.swingSource() == InteractionResult.SwingSource.CLIENT) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
            return true;
        }

        return false;
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
            InventoryUtility.selectHotbarSlot(windChargeOriginalSlot);
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
        if (maceSwitchDelayTicks > 0) {
            maceSwitchDelayTicks--;
        }

        if (windChargeAttackDelayTicks > 0) {
            windChargeAttackDelayTicks--;
        }
    }

    private void rotateDown() {
        RotationManager.INSTANCE.setRotations(new Vector2f(targetYaw(), DOWN_PITCH), 180.0D, Priority.Highest);
    }

    private void aimAtTarget() {
        if (target != null) {
            RotationManager.INSTANCE.setRotations(RotationUtility.calculate(target.getBoundingBox().getCenter()), 180.0D, Priority.Highest);
        }
    }

    private float targetYaw() {
        if (target == null) {
            return mc.player.getYRot();
        }

        return RotationUtility.calculate(target.getBoundingBox().getCenter()).x;
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

    private boolean shouldControlInput() {
        return switch (stage) {
            case JUMP, WIND_CHARGE_USE, WAIT_LAUNCH, APPROACH_TARGET, SWITCH_TO_MACE, ATTACK, AFTER_ATTACK -> true;
            default -> false;
        };
    }

    private boolean shouldApproachTarget() {
        return target != null && switch (stage) {
            case APPROACH_TARGET, SWITCH_TO_MACE, ATTACK -> true;
            default -> false;
        };
    }

    private void recoverFromTimeout() {
        if (mc.player != null && mc.player.onGround()) {
            setStage(Stage.USE_WIND_CHARGE);
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
        restoreQueuedWindChargeSlotNow();
        stage = Stage.ACQUIRE_TARGET;
    }

    private void resetRuntimeState() {
        resetCombatCycle();
        originalSelectedSlot = InventoryUtility.NOT_FOUND;
        maceSlot = InventoryUtility.NOT_FOUND;
        warnedMissingLoadout = false;
        warnedFireworkOnly = false;
    }

    private void setStage(Stage stage) {
        if (this.stage == stage) {
            return;
        }

        this.stage = stage;
        stageTicks = 0;
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
        APPROACH_TARGET,
        SWITCH_TO_MACE,
        ATTACK,
        AFTER_ATTACK
    }

    private record DetectedLoadout(DuelMode duelMode, ItemType itemType) {
    }
}
