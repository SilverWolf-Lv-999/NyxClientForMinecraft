package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.manager.FriendManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

@ModuleInfo(name = "nyxclient.module.nointeraction.name", description = "nyxclient.module.nointeraction.description", category = Category.OTHER)
public class NoInteraction extends Module {
    public static final NoInteraction INSTANCE = new NoInteraction();

    public final BoolValue village = ValueBuild.boolSetting("village", false, this);

    public final BoolValue friend = ValueBuild.boolSetting("friend", false, this);

    @EventTarget(0)
    public void onAttack(AttackEntityEvent event) {
        if (event.getPlayer() != mc.player) {
            return;
        }

        if (shouldCancelAttack(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelAttack(Entity entity) {
        return (village.getValue() && entity instanceof Villager)
                || (friend.getValue() && FriendManager.isFriend(entity));
    }
}
