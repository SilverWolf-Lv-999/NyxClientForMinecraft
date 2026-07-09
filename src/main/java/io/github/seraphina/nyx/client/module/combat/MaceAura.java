package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
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
    private static final int WIND_CHARGE_USE_TIMEOUT_TICKS = 10;
    private static final int WIND_CHARGE_RESTORE_SLOT_DELAY_TICKS = 2;
    private static final int MIN_LAUNCH_TICKS = 3;
    private static final int MIN_APEX_TICKS = 5;
    private static final int APEX_TIMEOUT_TICKS = 60;
    private static final int ATTACK_HOLD_TICKS = 5;
    private static final int AFTER_ATTACK_TIMEOUT_TICKS = 20;
    private static final int STAGE_TIMEOUT_TICKS = 120;
    private static final double LAUNCH_VELOCITY_THRESHOLD = 0.08D;
    private static final double FALLING_ATTACK_VELOCITY = -0.18D;
    private static final float MIN_MACE_FALL_DISTANCE = 1.5F;

    public final EnumValue<DuelMode> duelMode = ValueBuild.enumSetting("duel mode", DuelMode.ELYTRA, this);
    public final EnumValue<ItemType> itemType = ValueBuild.enumSetting("item type", ItemType.WIND_CHARGE, this);
    public final DoubleValue targetRange = ValueBuild.doubleSetting("target range", 24.0D, 4.0D, 64.0D, 0.5D, this);
    public final DoubleValue attackRange = ValueBuild.doubleSetting("attack range", 4.5D, 2.5D, 8.0D, 0.1D, this);
    public final IntValue blockheight = ValueBuild.intSetting("block height", 15, 10, 40, 1, () -> itemType.getValue() == ItemType.FIREWORK_ROCKET, this);

    private Stage stage = Stage.ACQUIRE_TARGET;
    private LivingEntity target;
    private int originalSelectedSlot = InventoryUtility.NOT_FOUND;
    private int windChargeOriginalSlot = InventoryUtility.NOT_FOUND;
    private int windChargeSlot = InventoryUtility.NOT_FOUND;
    private int windChargeRestoreSlotDelayTicks;
    private int stageTicks;
    private int launchTicks;
    private int attackHoldTicks;
    private boolean sawUpwardMotion;
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
        if (!shouldControlInput()) {
            return;
        }

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setSprint(false);
        event.setSneak(false);
        event.setJump(false);
    }

    @EventTarget(4)
    public void onClick(ClickEvent event) {
        if (stage != Stage.WIND_CHARGE_USE) {
            return;
        }

        event.setCancelled(true);
        drainUseClicks();

        if (!canRun()
                || InventoryUtility.getSelectedHotbarSlot() != windChargeSlot
                || !canUseHotbarItem(windChargeSlot, Items.WIND_CHARGE)) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        rotateDown();
        if (!useSelectedItem()) {
            restoreQueuedWindChargeSlotNow();
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        launchTicks = 0;
        sawUpwardMotion = false;
        windChargeRestoreSlotDelayTicks = WIND_CHARGE_RESTORE_SLOT_DELAY_TICKS;
        setStage(Stage.WAIT_LAUNCH);
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
        tickWindChargeSlotRestore();

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
            case WIND_CHARGE_USE -> tickQueuedWindChargeUse();
            case WAIT_LAUNCH -> tickWaitLaunch();
            case WAIT_APEX -> tickWaitApex();
            case DIVE -> tickDive();
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

        int reservedMaceSlot = InventoryUtility.findHotbarSlot(Items.MACE);
        int foundWindChargeSlot = ensureHotbarItem(Items.WIND_CHARGE, reservedMaceSlot);
        if (!canUseHotbarItem(foundWindChargeSlot, Items.WIND_CHARGE)) {
            return;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(selectedSlot)) {
            return;
        }

        windChargeOriginalSlot = selectedSlot;
        windChargeSlot = foundWindChargeSlot;
        if (selectedSlot != foundWindChargeSlot && !InventoryUtility.selectHotbarSlot(foundWindChargeSlot)) {
            clearQueuedWindCharge();
            return;
        }

        setStage(Stage.WIND_CHARGE_USE);
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
        }
    }

    private void tickWaitLaunch() {
        rotateDown();

        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y > LAUNCH_VELOCITY_THRESHOLD || !mc.player.onGround()) {
            sawUpwardMotion = velocity.y > LAUNCH_VELOCITY_THRESHOLD;
            setStage(Stage.WAIT_APEX);
            return;
        }

        launchTicks++;
        if (launchTicks > WIND_CHARGE_USE_TIMEOUT_TICKS) {
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickWaitApex() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y > LAUNCH_VELOCITY_THRESHOLD) {
            sawUpwardMotion = true;
        }

        launchTicks++;
        if (hasReachedApex(velocity)) {
            setStage(Stage.DIVE);
            return;
        }

        if (mc.player.onGround() && launchTicks > MIN_LAUNCH_TICKS) {
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickDive() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (canAttackTarget(target)) {
            setStage(Stage.SWITCH_TO_MACE);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.USE_WIND_CHARGE);
        }
    }

    private void tickSwitchToMace() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!canAttackTarget(target)) {
            setStage(mc.player.onGround() ? Stage.USE_WIND_CHARGE : Stage.DIVE);
            return;
        }

        if (selectMaceSlot()) {
            setStage(Stage.ATTACK);
        }
    }

    private void tickAttack() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        aimAtTarget();

        if (!canAttackTarget(target)) {
            setStage(mc.player.onGround() ? Stage.USE_WIND_CHARGE : Stage.DIVE);
            return;
        }

        if (attackTarget(target)) {
            attackHoldTicks = ATTACK_HOLD_TICKS;
            setStage(Stage.AFTER_ATTACK);
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
        int maceSlot = ensureHotbarItem(Items.MACE, windChargeSlot);
        if (!canHoldHotbarItem(maceSlot, Items.MACE)) {
            return false;
        }

        return InventoryUtility.getSelectedHotbarSlot() == maceSlot
                || InventoryUtility.selectHotbarSlot(maceSlot);
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

    private boolean canAttackTarget(LivingEntity target) {
        if (RotationUtility.getEyeDistanceToEntity(target) > attackRange.getValue()) {
            return false;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        return velocity.y <= FALLING_ATTACK_VELOCITY
                && (mc.player.fallDistance >= MIN_MACE_FALL_DISTANCE || mc.player.getY() > target.getY() + 1.5D);
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

    private void tickWindChargeSlotRestore() {
        if (!Inventory.isHotbarSlot(windChargeOriginalSlot)
                || !Inventory.isHotbarSlot(windChargeSlot)
                || windChargeOriginalSlot == windChargeSlot) {
            return;
        }

        if (windChargeRestoreSlotDelayTicks > 0) {
            windChargeRestoreSlotDelayTicks--;
            return;
        }

        if (mc.player != null && InventoryUtility.getSelectedHotbarSlot() == windChargeSlot) {
            InventoryUtility.selectHotbarSlot(windChargeOriginalSlot);
        }

        clearQueuedWindCharge();
    }

    private void clearQueuedWindCharge() {
        windChargeOriginalSlot = InventoryUtility.NOT_FOUND;
        windChargeSlot = InventoryUtility.NOT_FOUND;
        windChargeRestoreSlotDelayTicks = 0;
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

    private boolean hasReachedApex(Vec3 velocity) {
        if (launchTicks < MIN_APEX_TICKS) {
            return false;
        }

        return (sawUpwardMotion && velocity.y <= 0.03D)
                || (mc.player.fallDistance > 0.0F && velocity.y < 0.0D)
                || launchTicks >= APEX_TIMEOUT_TICKS;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private boolean shouldControlInput() {
        return canRun() && stage != Stage.ACQUIRE_TARGET;
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
        attackHoldTicks = 0;
        sawUpwardMotion = false;
        restoreQueuedWindChargeSlotNow();
        stage = Stage.ACQUIRE_TARGET;
    }

    private void resetRuntimeState() {
        resetCombatCycle();
        originalSelectedSlot = InventoryUtility.NOT_FOUND;
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

    private void drainUseClicks() {
        while (mc.options.keyUse.consumeClick()) {
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
        WIND_CHARGE_USE,
        WAIT_LAUNCH,
        WAIT_APEX,
        DIVE,
        SWITCH_TO_MACE,
        ATTACK,
        AFTER_ATTACK
    }

    private record DetectedLoadout(DuelMode duelMode, ItemType itemType) {
    }
}
