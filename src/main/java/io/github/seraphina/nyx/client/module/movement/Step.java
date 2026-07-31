package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

@ModuleInfo(name = "nyxclient.module.step.name", description = "nyxclient.module.step.description", category = Category.MOVEMENT)
public final class Step extends Module {
    public static final Step INSTANCE = new Step();

    private static final double STEP_HEIGHT = 1.0D;

    private LocalPlayer playerWithOriginalStepHeight;
    private double originalStepHeight;

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null) {
            return;
        }

        AttributeInstance stepHeight = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight == null) {
            return;
        }

        if (playerWithOriginalStepHeight != mc.player) {
            playerWithOriginalStepHeight = mc.player;
            originalStepHeight = stepHeight.getBaseValue();
        }

        double targetStepHeight = mc.player.onGround() ? STEP_HEIGHT : originalStepHeight;
        if (Double.compare(stepHeight.getBaseValue(), targetStepHeight) != 0) {
            stepHeight.setBaseValue(targetStepHeight);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.player != playerWithOriginalStepHeight) {
            return;
        }

        AttributeInstance stepHeight = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.setBaseValue(originalStepHeight);
        }

        playerWithOriginalStepHeight = null;
    }
}
