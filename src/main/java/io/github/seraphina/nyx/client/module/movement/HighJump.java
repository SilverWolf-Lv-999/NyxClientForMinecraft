package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.JumpEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        name = "nyxclient.module.highjump.name",
        description = "nyxclient.module.highjump.description",
        category = Category.MOVEMENT
)
public class HighJump extends Module {
    public static final HighJump INSTANCE = new HighJump();

    /** Multiplier applied to the upward velocity of each jump. */
    public final DoubleValue height = ValueBuild.doubleSetting("height", 1.5D, 1.0D, 5.0D, 0.1D, this);

    @EventTarget
    public void onJump(JumpEvent event) {
        if (mc.player == null || mc.player.isSpectator()) {
            return;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        if (velocity.y <= 0.0D) {
            return;
        }

        mc.player.setDeltaMovement(velocity.x, velocity.y * height.getValue(), velocity.z);
    }
}
