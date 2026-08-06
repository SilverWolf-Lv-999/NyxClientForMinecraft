package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.entitycontrol.name", description = "nyxclient.module.entitycontrol.description", category = Category.MOVEMENT)
public final class EntityControl extends Module {
    public static final EntityControl INSTANCE = new EntityControl();

    public final BoolValue yaw = ValueBuild.boolSetting("yaw", true, this);
    public final BoolValue horizontalSpeed = ValueBuild.boolSetting("horizontal speed", true, this);
    public final DoubleValue speed = ValueBuild.doubleSetting("speed", 5.0D, 0.1D, 50.0D, 0.1D, this);
    public final BoolValue fly = ValueBuild.boolSetting("fly", true, this);
    public final DoubleValue verticalSpeed = ValueBuild.doubleSetting("vertical speed", 6.0D, 0.0D, 20.0D, 0.1D, this);
    public final DoubleValue fallSpeed = ValueBuild.doubleSetting("fall speed", 0.1D, 0.0D, 50.0D, 0.1D, this);
    public final BoolValue noSync = ValueBuild.boolSetting("no sync", false, this);

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        Entity vehicle = controlledVehicle();
        if (vehicle == null) {
            return;
        }

        if (yaw.getValue()) {
            vehicle.setYRot(mc.player.getYRot());
        }

        Vec3 velocity = vehicle.getDeltaMovement();
        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(speed.getValue() / 20.0D, mc.player.getYRot());
        double verticalVelocity = fly.getValue() ? getVerticalVelocity() : velocity.y;
        vehicle.setDeltaMovement(
                horizontalSpeed.getValue() ? horizontalVelocity.x : velocity.x,
                verticalVelocity,
                horizontalSpeed.getValue() ? horizontalVelocity.z : velocity.z
        );
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (noSync.getValue()
                && event.getPacket() instanceof ClientboundMoveVehiclePacket
                && controlledVehicle() != null) {
            event.setCancelled(true);
        }
    }

    private Entity controlledVehicle() {
        if (isNull()) {
            return null;
        }

        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || vehicle.isRemoved() || vehicle.getControllingPassenger() != mc.player) {
            return null;
        }

        return vehicle;
    }

    private double getVerticalVelocity() {
        if (mc.screen != null) {
            return -fallSpeed.getValue() / 20.0D;
        }

        boolean jumping = mc.options.keyJump.isDown();
        boolean descending = mc.options.keyShift.isDown();
        if (jumping == descending) {
            return -fallSpeed.getValue() / 20.0D;
        }

        return jumping ? verticalSpeed.getValue() / 20.0D : -verticalSpeed.getValue() / 20.0D;
    }
}
