package io.github.seraphina.nyx.client.module.player;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackBlockEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.packetmine.name", description = "nyxclient.module.packetmine.description", category = Category.PLAYER)
public class PacketMine extends Module {
    public static final PacketMine INSTANCE = new PacketMine();

    private static final int TICKS_PER_SECOND = 20;
    private static final long TIMER_PRIMED_MS = 917813L;

    public final BoolValue usingPause = ValueBuild.boolSetting("pause on use", true, this);
    public final BoolValue onlyMain = ValueBuild.boolSetting("only main", true, () -> usingPause.getValue(), this);
    public final EnumValue<SwitchMode> switchMode = ValueBuild.enumSetting("switch mode", SwitchMode.SILENT, this);
    public final IntValue range = ValueBuild.intSetting("range", 6, 0, 12, 1, this);
    public final IntValue maxBreaks = ValueBuild.intSetting("try break time", 6, 0, 10, 1, this);
    public final BoolValue farCancel = ValueBuild.boolSetting("far cancel", true, this);
    public final BoolValue swing = ValueBuild.boolSetting("swing hand", true, this);
    public final BoolValue instantMine = ValueBuild.boolSetting("instant mine", true, this);
    public final IntValue instantDelay = ValueBuild.intSetting("instant delay", 10, 0, 1000, 10, this);
    public final BoolValue fastBypass = ValueBuild.boolSetting("fast bypass", true, this);
    public final BoolValue doubleBreak = ValueBuild.boolSetting("double break", false, this);
    public final BoolValue checkGround = ValueBuild.boolSetting("check ground", true, this);
    public final BoolValue bypassGround = ValueBuild.boolSetting("bypass ground", false, this);
    public final BoolValue clientRemove = ValueBuild.boolSetting("client remove", true, this);
    public final IntValue switchDamage = ValueBuild.intSetting("switch damage", 95, 0, 100, 1, this);
    public final IntValue switchTime = ValueBuild.intSetting("switch time", 100, 0, 1000, 10, this);
    public final IntValue mineDelay = ValueBuild.intSetting("mine delay", 300, 0, 1000, 10, this);
    public final IntValue packetDelay = ValueBuild.intSetting("packet delay", 200, 0, 1000, 10, this);
    public final DoubleValue mineDamage = ValueBuild.doubleSetting("damage", 0.8D, 0.0D, 2.0D, 0.05D, this);

    public final EnumValue<RenderMode> renderMode = ValueBuild.enumSetting("render mode", RenderMode.SHRINK, this);
    public final BoolValue fading = ValueBuild.boolSetting("fading", true, this);
    public final DoubleValue renderTime = ValueBuild.doubleSetting("render time", 0.1D, 0.0D, 5.0D, 0.1D, () -> fading.getValue(), this);
    public final DoubleValue fadeTime = ValueBuild.doubleSetting("fade time", 0.2D, 0.0D, 5.0D, 0.1D, () -> fading.getValue(), this);
    public final ColorValue fadeSideColor = ValueBuild.colorSetting("fade side color", new Color(70, 200, 155, 31), this);
    public final ColorValue fadeLineColor = ValueBuild.colorSetting("fade line color", new Color(70, 200, 155, 233), this);
    public final ColorValue sideStartColor = ValueBuild.colorSetting("side start", new Color(255, 0, 0, 31), this);
    public final ColorValue sideEndColor = ValueBuild.colorSetting("side end", new Color(0, 150, 10, 31), this);
    public final ColorValue lineStartColor = ValueBuild.colorSetting("line start", new Color(255, 0, 0, 233), this);
    public final ColorValue lineEndColor = ValueBuild.colorSetting("line end", new Color(5, 160, 0, 233), this);
    public final ColorValue secondSideStartColor = ValueBuild.colorSetting("second side start", new Color(255, 0, 0, 31), this);
    public final ColorValue secondSideEndColor = ValueBuild.colorSetting("second side end", new Color(0, 150, 10, 31), this);
    public final ColorValue secondLineStartColor = ValueBuild.colorSetting("second line start", new Color(255, 0, 0, 233), this);
    public final ColorValue secondLineEndColor = ValueBuild.colorSetting("second line end", new Color(5, 160, 0, 233), this);

    public static BlockPos selfClickPos;
    public static int maxBreaksCount;
    public static int mainProgressPercent;
    public static int secondProgressPercent;
    public static boolean completed;
    public static BlockPos targetPos;
    public static BlockPos secondPos;

    private static float progress;
    private static float secondProgress;
    private static boolean started;
    private static boolean secondStarted;

    private final SimpleTimer switchTimer = new SimpleTimer();
    public final SimpleTimer mineTimer = new SimpleTimer();
    private final SimpleTimer instantTimer = new SimpleTimer();
    private final List<DelayedStop> delayedStops = new ArrayList<>();

    private long lastTime;
    private long secondLastTime;
    private long fadeLastTime;
    private BlockPos renderPos;
    private BlockPos secondRenderPos;
    private double renderProgress;
    private double secondRenderProgress;
    private int oldSlot = InventoryUtility.NOT_FOUND;
    private boolean hasSwitch;

    @Override
    public void onEnable() {
        resetState();
        switchTimer.setElapsed(TIMER_PRIMED_MS);
        mineTimer.setElapsed(TIMER_PRIMED_MS);
        instantTimer.setElapsed(TIMER_PRIMED_MS);
    }

    @Override
    public void onDisable() {
        restoreSwitchedSlot();
        resetState();
    }

    @EventTarget
    public void onStartBreakingBlock(AttackBlockEvent event) {
        if (!canRun() || !canBreak(event.getBlockPos())) {
            return;
        }

        event.setCancelled(true);
        if (!mineTimer.passed(mineDelay.getValue())) {
            return;
        }

        selfClickPos = event.getBlockPos();
        mine(event.getBlockPos());
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!canRun()) {
            restoreSwitchedSlot();
            clearMiningTargets();
            return;
        }

        long now = System.currentTimeMillis();
        double fadeDelta = (now - fadeLastTime) / 1000.0D;
        fadeLastTime = now;

        tickDelayedStops(now);
        tickSlotRestore();
        tickTargetLiveness();
        updateRenderPositions();
        updateFadeProgress(fadeDelta);
        renderFadeBoxes(event.getPoseStack());

        tickSecondTarget(event.getPoseStack());
        tickDoubleBreakSwitch();
        tickMainTarget(event.getPoseStack());
    }

    public boolean isInstantMining(BlockPos pos) {
        if (!isEnabled() || !instantMine.getValue() || pos == null) {
            return false;
        }
        if (!completed || targetPos == null || !targetPos.equals(pos)) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    public void mine(BlockPos pos) {
        if (pos == null) {
            return;
        }

        mineTimer.reset();
        maxBreaksCount = 0;

        if (doubleBreak.getValue()) {
            mineDouble(pos);
            return;
        }

        if (!pos.equals(targetPos)) {
            mainProgressPercent = 0;
            targetPos = pos;
            started = false;
            progress = 0.0F;
            completed = false;
        }
    }

    private void mineDouble(BlockPos pos) {
        if (targetPos != null && secondPos == null && !targetPos.equals(pos)) {
            if (completed) {
                targetPos = pos;
                secondStarted = false;
                secondProgress = 0.0F;
                secondProgressPercent = 0;
                mainProgressPercent = 0;
                started = false;
                progress = 0.0F;
                completed = false;
            } else {
                secondPos = targetPos;
                targetPos = pos;
                secondStarted = false;
                secondProgress = 0.0F;
                secondProgressPercent = 0;
                started = false;
            }
        } else if (targetPos == null || !targetPos.equals(pos)) {
            mainProgressPercent = 0;
            targetPos = pos;
            started = false;
            progress = 0.0F;
            completed = false;
        }
    }

    private void tickTargetLiveness() {
        if (targetPos == null && secondPos == null) {
            selfClickPos = null;
        }
        if (mainProgressPercent >= 100 && !instantMine.getValue()) {
            targetPos = null;
        }
        if (secondProgressPercent >= 100) {
            secondPos = null;
        }
        if (maxBreaks.getValue() >= 0 && maxBreaksCount >= maxBreaks.getValue() * 10) {
            maxBreaksCount = 0;
            targetPos = null;
        }
    }

    private void updateRenderPositions() {
        if (targetPos != null) {
            renderPos = targetPos;
        }
        if (secondPos != null) {
            secondRenderPos = secondPos;
        }
    }

    private void tickSlotRestore() {
        if (!hasSwitch || !switchTimer.passed(switchTime.getValue())) {
            return;
        }

        restoreSwitchedSlot();
    }

    private void tickSecondTarget(PoseStack poseStack) {
        if (secondPos == null || !doubleBreak.getValue()) {
            return;
        }

        if (shouldCancelForDistance(secondPos)) {
            secondPos = null;
            return;
        }

        double secondMax = mineTicks(secondPos, getTool(secondPos));
        double secondDelta = elapsedSeconds(secondLastTime);
        secondLastTime = System.currentTimeMillis();
        secondProgressPercent = progressPercent(secondProgress, secondMax);

        if (!secondStarted) {
            sendStart(secondPos);
            secondStarted = true;
            secondProgress = 0.0F;
            return;
        }

        advanceProgress(true, secondDelta);
        secondBlockRender(poseStack);
        if (secondProgress >= secondMax * mineDamage.getValue()) {
            sendStopSecond();
        }
    }

    private void tickDoubleBreakSwitch() {
        if (!doubleBreak.getValue()
                || usingPause.getValue() && checkPause(onlyMain.getValue())
                || secondPos == null
                || hasSwitch
                || secondProgressPercent < switchDamage.getValue() && mainProgressPercent < switchDamage.getValue()) {
            return;
        }

        switchToBestTool(secondPos);
    }

    private void tickMainTarget(PoseStack poseStack) {
        if (targetPos == null) {
            return;
        }

        if (shouldCancelForDistance(targetPos)) {
            targetPos = null;
            return;
        }

        double max = mineTicks(targetPos, getTool(targetPos));
        mainProgressPercent = progressPercent(progress, max);
        tickCompletedRetryCounter();

        if (instantMine.getValue() && completed) {
            renderInstantTarget(poseStack);
            if (!isAir(targetPos)
                    && !mc.level.getBlockState(targetPos).canBeReplaced()
                    && instantTimer.passed(instantDelay.getValue())) {
                sendStop();
                instantTimer.reset();
            }
            return;
        }

        double delta = elapsedSeconds(lastTime);
        lastTime = System.currentTimeMillis();
        if (!started) {
            sendStart(targetPos);
            return;
        }

        advanceProgress(false, delta);
        mainBlockRender(poseStack);
        if (progress >= max * mineDamage.getValue()) {
            sendStop();
            completed = true;
            if (!instantMine.getValue() && secondPos == null) {
                targetPos = null;
            }
        }
    }

    private void tickCompletedRetryCounter() {
        if (targetPos == null || progress < mineTicks(targetPos, getTool(targetPos)) * mineDamage.getValue() || !completed) {
            return;
        }

        if (isAir(targetPos) || mc.level.getBlockState(targetPos).canBeReplaced()) {
            maxBreaksCount = 0;
        } else if (!usingPause.getValue() || !checkPause(onlyMain.getValue())) {
            maxBreaksCount++;
        }
    }

    private void renderInstantTarget(PoseStack poseStack) {
        Color sideColor = progress >= 0.95F ? sideEndColor.getValue() : sideStartColor.getValue();
        Color lineColor = progress >= 0.95F ? lineEndColor.getValue() : lineStartColor.getValue();
        Render3DUtility.renderBlockBox(poseStack, targetPos, sideColor);
        Render3DUtility.renderBlockOutline(poseStack, targetPos, lineColor);
    }

    private void advanceProgress(boolean second, double delta) {
        float step = (float)(delta * (isGroundedForMining() ? TICKS_PER_SECOND : 4.0D));
        if (second) {
            secondProgress += step;
        } else {
            progress += step;
        }
    }

    private boolean isGroundedForMining() {
        return !checkGround.getValue() || mc.player.onGround();
    }

    private boolean shouldCancelForDistance(BlockPos pos) {
        return farCancel.getValue()
                && pos != null
                && Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.getCenter())) > range.getValue();
    }

    private double elapsedSeconds(long last) {
        return Math.max(0.0D, (System.currentTimeMillis() - last) / 1000.0D);
    }

    private int progressPercent(float currentProgress, double maxTicks) {
        double denominator = maxTicks * mineDamage.getValue();
        if (denominator <= 0.0D || Double.isInfinite(denominator) || Double.isNaN(denominator)) {
            return 0;
        }

        return (int)(currentProgress / denominator * 100.0D);
    }

    private void sendStart(BlockPos pos) {
        sendActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, RotationUtility.getClickSide(pos));

        if (fastBypass.getValue()) {
            BlockPos bypassPos = BlockPos.containing(mc.player.getX(), 321.0D, mc.player.getZ());
            sendActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN);
        }

        if (doubleBreak.getValue()) {
            delayedStops.add(new DelayedStop(pos.immutable(), System.currentTimeMillis() + packetDelay.getValue()));
        }

        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (pos.equals(targetPos)) {
            started = true;
            progress = 0.0F;
        } else {
            secondStarted = true;
            secondProgress = 0.0F;
        }
    }

    private void sendStop() {
        if (targetPos == null || usingPause.getValue() && checkPause(onlyMain.getValue())) {
            return;
        }

        if (!doubleBreak.getValue() || secondPos == null) {
            switchToBestTool(targetPos);
        }

        sendGroundBypass(targetPos);
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        sendActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, targetPos, RotationUtility.getClickSide(targetPos));
        clientRemove(targetPos);
    }

    private void sendStopSecond() {
        if (secondPos == null) {
            return;
        }

        sendGroundBypass(secondPos);
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        clientRemove(secondPos);
    }

    private void tickDelayedStops(long now) {
        Iterator<DelayedStop> iterator = delayedStops.iterator();
        while (iterator.hasNext()) {
            DelayedStop delayedStop = iterator.next();
            if (now < delayedStop.deadlineMs()) {
                continue;
            }

            sendActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    delayedStop.pos(),
                    RotationUtility.getClickSide(delayedStop.pos())
            );
            iterator.remove();
        }
    }

    private void sendGroundBypass(BlockPos pos) {
        if (!bypassGround.getValue()
                || mc.player.isFallFlying()
                || pos == null
                || isAir(pos)
                || mc.player.onGround()) {
            return;
        }

        mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY() + 1.0E-9D,
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                true,
                mc.player.horizontalCollision
        ));
        mc.player.resetFallDistance();
    }

    private void sendActionPacket(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction direction) {
        if (mc.player == null || mc.player.connection == null || mc.level == null || pos == null || direction == null) {
            return;
        }

        try (BlockStatePredictionHandler prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(action, pos, direction, prediction.currentSequence()));
        }
    }

    private void clientRemove(BlockPos pos) {
        if (clientRemove.getValue() && pos != null && !isAir(pos)) {
            mc.gameMode.destroyBlock(pos);
        }
    }

    private void switchToBestTool(BlockPos pos) {
        if (pos == null || switchMode.is(SwitchMode.NONE)) {
            return;
        }

        int bestSlot = getTool(pos);
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!Inventory.isHotbarSlot(bestSlot) || !Inventory.isHotbarSlot(selectedSlot) || bestSlot == selectedSlot) {
            return;
        }

        if (!hasSwitch) {
            oldSlot = selectedSlot;
        }

        if (switchMode.is(SwitchMode.DELAY)) {
            if (!InventoryUtility.selectHotbarSlot(bestSlot, true)) {
                return;
            }
        } else if (switchMode.is(SwitchMode.SILENT)) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(bestSlot));
        }

        switchTimer.reset();
        hasSwitch = true;
    }

    private void restoreSwitchedSlot() {
        if (!hasSwitch) {
            return;
        }

        if (Inventory.isHotbarSlot(oldSlot)) {
            if (switchMode.is(SwitchMode.DELAY)) {
                InventoryUtility.selectHotbarSlot(oldSlot, true);
            } else if (switchMode.is(SwitchMode.SILENT) && mc.player != null && mc.player.connection != null) {
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
            }
        }

        oldSlot = InventoryUtility.NOT_FOUND;
        hasSwitch = false;
    }

    private double mineTicks(BlockPos pos, int slot) {
        if (pos == null || mc.level == null || mc.player == null) {
            return TICKS_PER_SECOND;
        }

        BlockState state = mc.level.getBlockState(pos);
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0.0F) {
            return Float.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 1.0D;
        }

        ItemStack stack = Inventory.isHotbarSlot(slot) ? InventoryUtility.getStack(slot) : ItemStack.EMPTY;
        boolean canHarvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        float speed = getDestroySpeed(state, stack);
        float damage = speed / hardness / (canHarvest ? 30.0F : 100.0F);
        if (damage <= 0.0F) {
            return Float.MAX_VALUE;
        }

        return 1.0F / damage;
    }

    private float getDestroySpeed(BlockState state, ItemStack stack) {
        float speed = stack.getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += efficiencyLevel(stack) * efficiencyLevel(stack);
        }

        if (MobEffectUtil.hasDigSpeed(mc.player)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(mc.player) + 1) * 0.2F;
        }

        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amplifier = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amplifier) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 0.00081F;
            };
        }

        speed *= (float)mc.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (mc.player.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float)mc.player.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }
        if (!mc.player.onGround()) {
            speed /= 5.0F;
        }

        return speed;
    }

    private int efficiencyLevel(ItemStack stack) {
        if (stack.isEmpty() || mc.level == null) {
            return 0;
        }

        Holder<Enchantment> efficiency = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
        return stack.getEnchantmentLevel(efficiency);
    }

    private int getTool(BlockPos pos) {
        if (pos == null || mc.player == null) {
            return InventoryUtility.NOT_FOUND;
        }

        int bestSlot = InventoryUtility.NOT_FOUND;
        double bestTicks = Double.MAX_VALUE;
        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.HOTBAR_END; slot++) {
            ItemStack stack = InventoryUtility.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            double ticks = mineTicks(pos, slot);
            if (ticks < bestTicks) {
                bestTicks = ticks;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean isAir(BlockPos breakPos) {
        if (breakPos == null || mc.level == null) {
            return true;
        }

        BlockState state = mc.level.getBlockState(breakPos);
        return state.isAir() || state.is(Blocks.FIRE) && hasCrystal(breakPos);
    }

    private boolean isSolidForFade(BlockPos pos) {
        if (pos == null || mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);
        return !isAir(pos) && !state.canBeReplaced();
    }

    private boolean hasCrystal(BlockPos pos) {
        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity instanceof EndCrystal endCrystal && endCrystal.isAlive()) {
                return true;
            }
        }

        return false;
    }

    private boolean checkPause(boolean onlyMainHand) {
        return mc.options.keyUse.isDown()
                && (!onlyMainHand || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    private boolean canBreak(BlockPos blockPos) {
        if (blockPos == null || mc.level == null || mc.player == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(blockPos);
        if (!mc.player.isCreative() && state.getDestroySpeed(mc.level, blockPos) < 0.0F) {
            return false;
        }

        return !state.getCollisionShape(mc.level, blockPos).isEmpty();
    }

    private void updateFadeProgress(double delta) {
        if (!fading.getValue()) {
            renderProgress = 0.0D;
            secondRenderProgress = 0.0D;
            return;
        }

        boolean paused = usingPause.getValue() && checkPause(onlyMain.getValue());
        renderProgress = fadeProgress(renderPos, renderProgress, delta, paused);
        secondRenderProgress = fadeProgress(secondRenderPos, secondRenderProgress, delta, paused);
    }

    private double fadeProgress(BlockPos pos, double currentProgress, double delta, boolean paused) {
        if (isSolidForFade(pos) && !paused) {
            return fadeTime.getValue() + renderTime.getValue();
        }

        return Math.max(0.0D, currentProgress - delta);
    }

    private void renderFadeBoxes(PoseStack poseStack) {
        if (!fading.getValue()) {
            return;
        }

        renderFadeBox(poseStack, renderPos, renderProgress);
        renderFadeBox(poseStack, secondRenderPos, secondRenderProgress);
    }

    private void renderFadeBox(PoseStack poseStack, BlockPos pos, double currentProgress) {
        if (pos == null || currentProgress <= 0.0D || isSolidForFade(pos)) {
            return;
        }

        double fadeSeconds = fadeTime.getValue();
        double alphaFactor = fadeSeconds <= 0.0D ? 1.0D : Mth.clamp(currentProgress / fadeSeconds, 0.0D, 1.0D);
        Render3DUtility.renderBlockBox(poseStack, pos, withAlpha(fadeSideColor.getValue(), alphaFactor));
        Render3DUtility.renderBlockOutline(poseStack, pos, withAlpha(fadeLineColor.getValue(), alphaFactor));
    }

    private void mainBlockRender(PoseStack poseStack) {
        if (targetPos == null) {
            return;
        }

        double max = mineTicks(targetPos, getTool(targetPos));
        double rawProgress = rawRenderProgress(progress, max);
        Color sideColor = rawProgress >= 0.95D ? sideEndColor.getValue() : sideStartColor.getValue();
        Color lineColor = rawProgress >= 0.95D ? lineEndColor.getValue() : lineStartColor.getValue();
        renderMineBox(poseStack, targetPos, rawProgress, sideColor, lineColor);
    }

    private void secondBlockRender(PoseStack poseStack) {
        if (secondPos == null) {
            return;
        }

        double max = mineTicks(secondPos, getTool(secondPos));
        double rawProgress = rawRenderProgress(secondProgress, max);
        Color sideColor = rawProgress >= 0.95D ? secondSideEndColor.getValue() : secondSideStartColor.getValue();
        Color lineColor = rawProgress >= 0.95D ? secondLineEndColor.getValue() : secondLineStartColor.getValue();
        renderMineBox(poseStack, secondPos, rawProgress, sideColor, lineColor);
    }

    private double rawRenderProgress(float currentProgress, double maxTicks) {
        double denominator = maxTicks * mineDamage.getValue();
        if (denominator <= 0.0D || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return 0.0D;
        }

        return Mth.clamp(currentProgress / denominator, 0.0D, 1.0D);
    }

    private void renderMineBox(PoseStack poseStack, BlockPos pos, double rawProgress, Color sideColor, Color lineColor) {
        switch (renderMode.getValue()) {
            case BOX -> {
                Render3DUtility.renderBlockBox(poseStack, pos, sideColor);
                Render3DUtility.renderBlockOutline(poseStack, pos, lineColor);
            }
            case NORMAL -> {
                AABB box = AABB.ofSize(pos.getCenter(), rawProgress, rawProgress, rawProgress);
                Render3DUtility.renderFilledBox(poseStack, box, sideColor);
                Render3DUtility.renderOutlineBox(poseStack, box, lineColor);
            }
            case GROW -> {
                AABB box = new AABB(pos).setMaxY(pos.getY() + rawProgress);
                Render3DUtility.renderFilledBox(poseStack, box, sideColor);
                Render3DUtility.renderOutlineBox(poseStack, box, lineColor);
            }
            case SHRINK -> {
                double size = Math.round(rawProgress * 100.0D) / 100.0D;
                AABB box = AABB.ofSize(pos.getCenter(), size, size, size);
                Render3DUtility.renderFilledBox(poseStack, box, sideColor);
                Render3DUtility.renderOutlineBox(poseStack, box, lineColor);
            }
        }
    }

    private Color withAlpha(Color color, double factor) {
        int alpha = Mth.clamp((int)Math.round(color.getAlpha() * factor), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.player.connection != null
                && !mc.player.isSpectator();
    }

    private void clearMiningTargets() {
        targetPos = null;
        secondPos = null;
        selfClickPos = null;
        started = false;
        secondStarted = false;
        progress = 0.0F;
        secondProgress = 0.0F;
        mainProgressPercent = 0;
        secondProgressPercent = 0;
        completed = false;
        delayedStops.clear();
    }

    private void resetState() {
        restoreOnlyLocalState();
        long now = System.currentTimeMillis();
        lastTime = now;
        secondLastTime = now;
        fadeLastTime = now;
        switchTimer.reset();
        mineTimer.reset();
        instantTimer.reset();
    }

    private void restoreOnlyLocalState() {
        maxBreaksCount = 0;
        selfClickPos = null;
        targetPos = null;
        secondPos = null;
        started = false;
        secondStarted = false;
        completed = false;
        progress = 0.0F;
        secondProgress = 0.0F;
        mainProgressPercent = 0;
        secondProgressPercent = 0;
        renderPos = null;
        secondRenderPos = null;
        renderProgress = 0.0D;
        secondRenderProgress = 0.0D;
        oldSlot = InventoryUtility.NOT_FOUND;
        hasSwitch = false;
        delayedStops.clear();
    }

    public enum SwitchMode {
        NONE("None"),
        DELAY("Delay"),
        SILENT("Silent");

        private final String displayName;

        SwitchMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RenderMode {
        BOX("Box"),
        NORMAL("Normal"),
        SHRINK("Shrink"),
        GROW("Grow");

        private final String displayName;

        RenderMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private record DelayedStop(BlockPos pos, long deadlineMs) {
    }

    public static final class SimpleTimer {
        private long lastMs = System.currentTimeMillis();

        public void reset() {
            lastMs = System.currentTimeMillis();
        }

        public void setElapsed(long elapsedMs) {
            lastMs = System.currentTimeMillis() - Math.max(0L, elapsedMs);
        }

        public boolean passed(long delayMs) {
            return System.currentTimeMillis() - lastMs >= delayMs;
        }
    }
}
