package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.noinertia.name", description = "nyxclient.module.noinertia.description", category = Category.MOVEMENT)
public final class NoInertia extends Module {
    public static final NoInertia INSTANCE = new NoInertia();

    @EventHandler(priority = EventPriority.LOWEST - 1)
    public void onMoveInput(MoveInputEvent event) {
        if (!canStopWalking(event) || isNull()) {
            return;
        }

        assert mc.player != null;
        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }

    private boolean canStopWalking(MoveInputEvent event) {
        return mc.player != null
                && mc.player.onGround()
                && !event.isJump()
                && event.getForward() == 0.0F
                && event.getStrafe() == 0.0F
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }
}
