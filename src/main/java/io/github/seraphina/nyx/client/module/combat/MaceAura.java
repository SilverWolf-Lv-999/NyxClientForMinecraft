package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
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
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
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
    private static final float CLIMB_PITCH = -35.0F;
    private static final int EQUIP_CONFIRM_TIMEOUT_TICKS = 20;
    private static final int GLIDE_CONFIRM_TIMEOUT_TICKS = 30;
    private static final int GLIDE_PACKET_DELAY_TICKS = 2;
    private static final int MIN_APEX_TICKS = 5;
    private static final int APEX_TIMEOUT_TICKS = 60;
    private static final int STAGE_TIMEOUT_TICKS = 120;

    public final EnumValue<DuelMode> duelMode = ValueBuild.enumSetting("duel mode", DuelMode.ELYTRA, this);
    public final EnumValue<ItemType> itemType = ValueBuild.enumSetting("item type", ItemType.WIND_CHARGE, this);
    public final DoubleValue targetRange = ValueBuild.doubleSetting("target range", 24.0D, 4.0D, 64.0D, 0.5D, this);
    public final DoubleValue attackRange = ValueBuild.doubleSetting("attack range", 4.5D, 2.5D, 8.0D, 0.1D, this);
    public final DoubleValue diveSpeed = ValueBuild.doubleSetting("dive speed", 1.6D, 0.3D, 3.5D, 0.1D, this);
    public final IntValue blockheight = ValueBuild.intSetting("block height", 15, 10, 40, 1, () -> itemType.getValue() == ItemType.FIREWORK_ROCKET, this);

    private Stage stage = Stage.ACQUIRE_TARGET;
    private LivingEntity target;
    private int originalSelectedSlot = InventoryUtility.NOT_FOUND;
    private ItemStack originalChestStack = ItemStack.EMPTY;
    private int elytraRestoreSlot = InventoryUtility.NOT_FOUND;
    private boolean equippedElytra;
    private int stageTicks;
    private int glidePacketDelayTicks;
    private int launchTicks;
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
        restoreChestSlot();
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

        switch (stage) {
            case JUMP_RELEASE -> {
                event.setJump(false);
                setStage(Stage.JUMP_PRESS);
            }
            case JUMP_PRESS -> {
                event.setJump(true);
                setStage(Stage.WAIT_AIRBORNE);
            }
            default -> event.setJump(false);
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
        if (!canRun()) {
            resetCombatCycle();
            return;
        }

        DetectedLoadout loadout = detectLoadout();
        if (loadout == null) {
            if (!warnedMissingLoadout) {
                DebugUtility.msg("MaceAura waiting: need elytra, mace and wind charge/firework rocket");
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
                    setStage(Stage.EQUIP_ELYTRA);
                }
            }
            case EQUIP_ELYTRA -> tickEquipElytra();
            case WAIT_ELYTRA_EQUIP -> tickWaitElytraEquip();
            case START_GLIDE -> tickStartGlide();
            case JUMP_RELEASE, JUMP_PRESS -> {
            }
            case WAIT_AIRBORNE -> tickWaitAirborne();
            case WAIT_GLIDE -> tickWaitGlide();
            case USE_WIND_CHARGE -> tickUseWindCharge();
            case WAIT_APEX -> tickWaitApex();
            case DIVE -> tickDive();
        }
    }

    private void tickEquipElytra() {
        if (isWearingElytra()) {
            setStage(Stage.START_GLIDE);
            return;
        }

        int elytraSlot = InventoryUtility.findInventorySlot(Items.ELYTRA);
        if (!InventoryUtility.isValidInventorySlot(elytraSlot)) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        if (!equippedElytra) {
            originalChestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
            elytraRestoreSlot = elytraSlot;
            equippedElytra = true;
        }

        if (InventoryUtility.equipFromInventorySlot(elytraSlot)) {
            setStage(Stage.WAIT_ELYTRA_EQUIP);
        }
    }

    private void tickWaitElytraEquip() {
        if (isWearingElytra()) {
            setStage(Stage.START_GLIDE);
            return;
        }

        if (stageTicks > EQUIP_CONFIRM_TIMEOUT_TICKS) {
            setStage(Stage.ACQUIRE_TARGET);
        }
    }

    private void tickStartGlide() {
        if (!isWearingElytra()) {
            setStage(Stage.EQUIP_ELYTRA);
            return;
        }

        if (mc.player.isFallFlying()) {
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (!canStartFallFlying()) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.JUMP_RELEASE);
            return;
        }

        startFallFlying();
        setStage(Stage.WAIT_GLIDE);
    }

    private void tickWaitAirborne() {
        if (!isWearingElytra()) {
            setStage(Stage.EQUIP_ELYTRA);
            return;
        }

        if (mc.player.isFallFlying()) {
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (!canStartFallFlying()) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        if (!mc.player.onGround()) {
            startFallFlying();
            setStage(Stage.WAIT_GLIDE);
        }
    }

    private void tickWaitGlide() {
        if (mc.player.isFallFlying()) {
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (!canStartFallFlying()) {
            setStage(Stage.START_GLIDE);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.START_GLIDE);
            return;
        }

        if (glidePacketDelayTicks > 0) {
            glidePacketDelayTicks--;
        } else {
            startFallFlying();
        }

        if (stageTicks > GLIDE_CONFIRM_TIMEOUT_TICKS) {
            setStage(Stage.START_GLIDE);
        }
    }

    private void tickUseWindCharge() {
        if (!isWearingElytra()) {
            setStage(Stage.EQUIP_ELYTRA);
            return;
        }

        if (!mc.player.isFallFlying() && !mc.player.onGround() && canStartFallFlying()) {
            startFallFlying();
        }

        rotateDown();

        int maceSlot = InventoryUtility.findHotbarSlot(Items.MACE);
        int windChargeSlot = ensureHotbarItem(Items.WIND_CHARGE, maceSlot);
        if (!canUseHotbarItem(windChargeSlot, Items.WIND_CHARGE)) {
            return;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != windChargeSlot
                && !InventoryUtility.selectHotbarSlot(windChargeSlot, true)) {
            return;
        }

        if (useSelectedItem()) {
            launchTicks = 0;
            sawUpwardMotion = false;
            setStage(Stage.WAIT_APEX);
        }
    }

    private void tickWaitApex() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        keepFallFlying();
        rotateClimb();

        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y > 0.08D) {
            sawUpwardMotion = true;
        }

        launchTicks++;
        if (hasReachedApex(velocity)) {
            setStage(Stage.DIVE);
            return;
        }

        if (mc.player.onGround() && launchTicks > MIN_APEX_TICKS) {
            setStage(Stage.START_GLIDE);
        }
    }

    private void tickDive() {
        if (target == null) {
            setStage(Stage.ACQUIRE_TARGET);
            return;
        }

        if (!isWearingElytra()) {
            setStage(Stage.EQUIP_ELYTRA);
            return;
        }

        keepFallFlying();
        aimAtTarget();
        applyDiveVelocity();

        if (canAttackTarget(target) && attackTarget(target)) {
            setStage(Stage.USE_WIND_CHARGE);
            return;
        }

        if (mc.player.onGround()) {
            setStage(Stage.START_GLIDE);
        }
    }

    private DetectedLoadout detectLoadout() {
        boolean hasElytra = isWearingElytra() || InventoryUtility.findInventorySlot(Items.ELYTRA) != InventoryUtility.NOT_FOUND;
        boolean hasMace = InventoryUtility.findInventorySlot(Items.MACE) != InventoryUtility.NOT_FOUND;
        boolean hasWindCharge = InventoryUtility.findInventorySlot(Items.WIND_CHARGE) != InventoryUtility.NOT_FOUND;
        boolean hasFireworkRocket = InventoryUtility.findInventorySlot(Items.FIREWORK_ROCKET) != InventoryUtility.NOT_FOUND;

        if (!hasElytra || !hasMace || (!hasWindCharge && !hasFireworkRocket)) {
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

    private boolean attackTarget(LivingEntity target) {
        int windChargeSlot = InventoryUtility.findHotbarSlot(Items.WIND_CHARGE);
        int maceSlot = ensureHotbarItem(Items.MACE, windChargeSlot);
        if (!canHoldHotbarItem(maceSlot, Items.MACE)) {
            return false;
        }

        if (InventoryUtility.getSelectedHotbarSlot() != maceSlot
                && !InventoryUtility.selectHotbarSlot(maceSlot, true)) {
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
                format(RotationUtility.getEyeDistanceToEntity(target))
        );
        return true;
    }

    private boolean canAttackTarget(LivingEntity target) {
        if (RotationUtility.getEyeDistanceToEntity(target) > attackRange.getValue()) {
            return false;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        return velocity.y < -0.03D || mc.player.fallDistance > 0.0F || mc.player.getY() > target.getY() + 0.5D;
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

    private void rotateDown() {
        RotationManager.INSTANCE.setRotations(new Vector2f(targetYaw(), DOWN_PITCH), 180.0D, Priority.Highest);
    }

    private void rotateClimb() {
        RotationManager.INSTANCE.setRotations(new Vector2f(targetYaw(), CLIMB_PITCH), 180.0D, Priority.Highest);
    }

    private void aimAtTarget() {
        RotationManager.INSTANCE.setRotations(RotationUtility.calculate(target.getBoundingBox().getCenter()), 180.0D, Priority.Highest);
    }

    private float targetYaw() {
        if (target == null) {
            return mc.player.getYRot();
        }

        return RotationUtility.calculate(target.getBoundingBox().getCenter()).x;
    }

    private void applyDiveVelocity() {
        Vec3 direction = target.getBoundingBox().getCenter().subtract(mc.player.getBoundingBox().getCenter());
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        mc.player.setDeltaMovement(direction.normalize().scale(diveSpeed.getValue()));
    }

    private boolean hasReachedApex(Vec3 velocity) {
        if (launchTicks < MIN_APEX_TICKS) {
            return false;
        }

        return (sawUpwardMotion && velocity.y <= 0.03D)
                || (mc.player.fallDistance > 0.0F && velocity.y < 0.0D)
                || launchTicks >= APEX_TIMEOUT_TICKS;
    }

    private void keepFallFlying() {
        if (mc.player.isFallFlying() || mc.player.onGround() || !canStartFallFlying()) {
            return;
        }

        if (glidePacketDelayTicks > 0) {
            glidePacketDelayTicks--;
            return;
        }

        startFallFlying();
    }

    private void startFallFlying() {
        if (mc.player == null || mc.player.connection == null || mc.player.onGround()) {
            return;
        }

        mc.player.connection.send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
        mc.player.startFallFlying();
        glidePacketDelayTicks = GLIDE_PACKET_DELAY_TICKS;
    }

    private boolean canStartFallFlying() {
        return mc.player != null
                && isWearingElytra()
                && !mc.player.isPassenger()
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().flying
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private boolean isWearingElytra() {
        return mc.player != null && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
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
        switch (stage) {
            case WAIT_APEX, DIVE, USE_WIND_CHARGE -> setStage(Stage.START_GLIDE);
            default -> setStage(Stage.ACQUIRE_TARGET);
        }
    }

    private void resetCombatCycle() {
        target = null;
        stageTicks = 0;
        launchTicks = 0;
        sawUpwardMotion = false;
        glidePacketDelayTicks = 0;
        stage = Stage.ACQUIRE_TARGET;
    }

    private void resetRuntimeState() {
        resetCombatCycle();
        originalSelectedSlot = InventoryUtility.NOT_FOUND;
        originalChestStack = ItemStack.EMPTY;
        elytraRestoreSlot = InventoryUtility.NOT_FOUND;
        equippedElytra = false;
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

    private void restoreChestSlot() {
        if (!equippedElytra || mc.player == null || !isWearingElytra()) {
            return;
        }

        int restoreTarget = findChestRestoreTarget();
        if (restoreTarget != InventoryUtility.NOT_FOUND) {
            InventoryUtility.swapInventorySlots(InventoryUtility.ARMOR_CHEST_SLOT, restoreTarget);
        }
    }

    private int findChestRestoreTarget() {
        if (isUsableChestRestoreSlot(elytraRestoreSlot)) {
            return elytraRestoreSlot;
        }

        if (!originalChestStack.isEmpty()) {
            int originalSlot = InventoryUtility.findSlot(this::isOriginalChestStack);
            if (originalSlot != InventoryUtility.NOT_FOUND && originalSlot != InventoryUtility.ARMOR_CHEST_SLOT) {
                return originalSlot;
            }

            return InventoryUtility.NOT_FOUND;
        }

        int emptySlot = InventoryUtility.findEmptySlot();
        return emptySlot == InventoryUtility.ARMOR_CHEST_SLOT ? InventoryUtility.NOT_FOUND : emptySlot;
    }

    private boolean isUsableChestRestoreSlot(int inventorySlot) {
        if (!InventoryUtility.isValidInventorySlot(inventorySlot) || inventorySlot == InventoryUtility.ARMOR_CHEST_SLOT) {
            return false;
        }

        ItemStack stack = InventoryUtility.getStack(inventorySlot);
        return originalChestStack.isEmpty() ? stack.isEmpty() : isOriginalChestStack(stack);
    }

    private boolean isOriginalChestStack(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, originalChestStack);
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
        EQUIP_ELYTRA,
        WAIT_ELYTRA_EQUIP,
        START_GLIDE,
        JUMP_RELEASE,
        JUMP_PRESS,
        WAIT_AIRBORNE,
        WAIT_GLIDE,
        USE_WIND_CHARGE,
        WAIT_APEX,
        DIVE
    }

    private record DetectedLoadout(DuelMode duelMode, ItemType itemType) {
    }
}
