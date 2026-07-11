package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(name = "nyxclient.module.playeralert.name", description = "nyxclient.module.playeralert.description", category = Category.OTHER)
public class PlayerAlert extends Module {
    public static final PlayerAlert INSTANCE = new PlayerAlert();

    private final Set<UUID> visiblePlayers = new HashSet<>();

    @Override
    public void onEnable() {
        visiblePlayers.clear();
        collectVisiblePlayers(visiblePlayers);
    }

    @Override
    public void onDisable() {
        visiblePlayers.clear();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!canRun()) {
            visiblePlayers.clear();
            return;
        }

        Set<UUID> currentPlayers = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player) {
                continue;
            }

            UUID uuid = player.getUUID();
            currentPlayers.add(uuid);
            if (!visiblePlayers.contains(uuid)) {
                MsgUtility.info(player.getName().getString(), " entered render distance.");
            }
        }

        visiblePlayers.clear();
        visiblePlayers.addAll(currentPlayers);
    }

    private boolean canRun() {
        return mc.player != null && mc.level != null;
    }

    private void collectVisiblePlayers(Set<UUID> target) {
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            return;
        }

        for (AbstractClientPlayer player : level.players()) {
            if (player != mc.player) {
                target.add(player.getUUID());
            }
        }
    }
}
