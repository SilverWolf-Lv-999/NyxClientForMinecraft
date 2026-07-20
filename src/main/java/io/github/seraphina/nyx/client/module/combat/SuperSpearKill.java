package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.TPUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        name = "nyxclient.module.superspearkill.name",
        description = "nyxclient.module.superspearkill.description",
        category = Category.COMBAT
)
public class SuperSpearKill extends Module {
    public static final SuperSpearKill INSTANCE = new SuperSpearKill();

    public final DoubleValue powerDistance = ValueBuild.doubleSetting(
            "power distance",
            5.0D,
            0.5D,
            30.0D,
            0.5D,
            this
    );
    public final IntValue waitTicks = ValueBuild.intSetting("wait ticks", 5, 0, 20, 1, this);

    private boolean rightClicking;
    private int clickTimer;
    private int stage;
    private Vec3 startPosition;
    private Vec3 targetPosition;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || !TPUtility.allowSendP()) {
            resetState();
            return;
        }

        if (!mc.options.keyUse.isDown()) {
            if (rightClicking) {
                resetState();
            }
            return;
        }

        if (!rightClicking) {
            rightClicking = true;
            clickTimer = 0;
            startPosition = mc.player.position();
            return;
        }

        clickTimer++;
        if (clickTimer >= waitTicks.getValue() && stage == 0) {
            stage = 1;
            calculateTargetPosition();
            moveBackwardAndReturn();
        }
    }

    private void calculateTargetPosition() {
        if (mc.player == null || startPosition == null) {
            return;
        }

        double yawRadians = Math.toRadians(mc.player.getYRot() + 180.0F);
        targetPosition = new Vec3(
                startPosition.x - Math.sin(yawRadians) * powerDistance.getValue(),
                startPosition.y,
                startPosition.z + Math.cos(yawRadians) * powerDistance.getValue()
        );
    }

    private void moveBackwardAndReturn() {
        if (startPosition == null || targetPosition == null) {
            resetState();
            return;
        }

        TPUtility.doTp(startPosition, targetPosition);
        stage = 2;
        TPUtility.doTp(targetPosition, startPosition);
        resetState();
    }

    private void resetState() {
        rightClicking = false;
        clickTimer = 0;
        stage = 0;
        startPosition = null;
        targetPosition = null;
    }
}
