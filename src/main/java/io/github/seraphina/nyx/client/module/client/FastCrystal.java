package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.bus.EventHandler;
import io.github.seraphina.nyx.client.events.bus.EventPriority;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.mixins.ServerboundInteractPacketAccessor;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.fastcrystal.name", description = "nyxclient.module.fastcrystal.description", category = Category.CLIENT)
public class FastCrystal extends Module {
    public static final FastCrystal INSTANCE = new FastCrystal();


    @EventHandler(priority = EventPriority.LOWEST)
    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled() || mc.level == null || !(event.getPacket() instanceof ServerboundInteractPacket packet)) {
            return;
        }

        if (!isAttackPacket(packet)) {
            return;
        }

        Entity entity = mc.level.getEntity(((ServerboundInteractPacketAccessor) packet).nyx$getEntityId());
        if (entity instanceof EndCrystal crystal && crystal.isAlive() && !crystal.isRemoved()) {
            mc.level.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private boolean isAttackPacket(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
    }
}
