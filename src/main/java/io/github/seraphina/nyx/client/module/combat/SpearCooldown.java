package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(name = "nyxclient.module.spearcooldown.name", description = "nyxclient.module.spearcooldown.description", category = Category.COMBAT)
public class SpearCooldown extends Module {
    public static final SpearCooldown INSTANCE = new SpearCooldown();

    public final IntValue leftDelay = ValueBuild.intSetting("left delay", 0, 0, 40, 1, this);
    public final IntValue rightDelay = ValueBuild.intSetting("right delay", 0, 0, 10, 1, this);
    public final IntValue useDelay = ValueBuild.intSetting("use delay", 0, 0, 40, 1, this);
    public final IntValue hitCooldown = ValueBuild.intSetting("hit cooldown", 0, 0, 40, 1, this);
    public final IntValue swingDuration = ValueBuild.intSetting("swing duration", 6, 1, 40, 1, this);

    private int heldLeftTicks;

    @Override
    public void onDisable() {
        heldLeftTicks = 0;
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || !isEnabled() || !isHoldingSpear()) {
            heldLeftTicks = 0;
            return;
        }

        mc.rightClickDelay = Math.min(mc.rightClickDelay, rightDelay.getValue());
        repeatHeldMainHandAttack();
    }

    public boolean shouldOverrideSpearAttackCooldown(ItemStack stack) {
        return isEnabled() && isSpear(stack);
    }

    public boolean cannotAttackYet(int attackStrengthTicks, int adjustTicks) {
        return attackStrengthTicks + adjustTicks < leftDelay.getValue();
    }

    public int leftClickMissDelay(int original) {
        if (!isEnabled() || mc.player == null || !isHoldingMainHandSpear()) {
            return original;
        }

        return Math.min(original, leftDelay.getValue());
    }

    public int rightClickDelay(int original) {
        if (!isEnabled() || mc.player == null || !isHoldingSpear()) {
            return original;
        }

        return Math.min(original, rightDelay.getValue());
    }

    public int useDelay(int original) {
        return isEnabled() ? Math.min(original, useDelay.getValue()) : original;
    }

    public int hitCooldown(int original) {
        return isEnabled() ? Math.min(original, hitCooldown.getValue()) : original;
    }

    public int swingDuration(int original, ItemStack stack) {
        if (!isEnabled() || !isSpear(stack)) {
            return original;
        }

        return Math.min(original, swingDuration.getValue());
    }

    public boolean isSpear(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.has(DataComponents.KINETIC_WEAPON)
                && stack.has(DataComponents.PIERCING_WEAPON);
    }

    private boolean isHoldingSpear() {
        return isSpear(mc.player.getItemInHand(InteractionHand.MAIN_HAND))
                || isSpear(mc.player.getItemInHand(InteractionHand.OFF_HAND));
    }

    private boolean isHoldingMainHandSpear() {
        return isSpear(mc.player.getItemInHand(InteractionHand.MAIN_HAND));
    }

    private void repeatHeldMainHandAttack() {
        if (mc.screen != null || !mc.options.keyAttack.isDown() || !isHoldingMainHandSpear()) {
            heldLeftTicks = 0;
            return;
        }

        if (heldLeftTicks++ > 0) {
            mc.startAttack();
        }
    }
}
