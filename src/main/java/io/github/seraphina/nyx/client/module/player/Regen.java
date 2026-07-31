package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@ModuleInfo(name = "nyxclient.module.regen.name", description = "nyxclient.module.regen.description", category = Category.PLAYER)
public class Regen extends Module {
    public static final Regen INSTANCE = new Regen();

    public final IntValue health = ValueBuild.intSetting("health", 10, 0, 20, 1, this);
    public final IntValue packetsPerTick = ValueBuild.intSetting("packets per tick", 5, 2, 20, 1, this);

    @EventTarget
    public void onPreTick(TickEvent.Pre event) {
        if (isNull() || mc.player.connection == null
                || mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.getValue()) {
            return;
        }

        for (int index = 0; index < packetsPerTick.getValue(); index++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));
        }
    }
}
