package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import io.github.seraphina.nyx.client.value.impl.StringValue;


@ModuleInfo(name = "nyxclient.module.autoleave.name", description = "nyxclient.module.autoleave.description", category = Category.PLAYER)
public class AutoLeave extends Module {
    public static final AutoLeave INSTANCE = new AutoLeave();

    public final IntValue health = ValueBuild.intSetting("health", 5, 1, 20, 1 ,this);

    public final EnumValue<Type> mode = ValueBuild.enumSetting("type", Type.HUB, this);

    public final StringValue value = ValueBuild.stringSetting("value", "/sucide", ()-> mode.getValue() == Type.CUSTOM, this);

    private boolean commandSent;

    @Override
    public void onEnable() {
        commandSent = false;
    }

    @Override
    public void onDisable() {
        commandSent = false;
    }

    @EventTarget
    public void onTick(TickEvent.Post event) {
        if (!canRun()) {
            commandSent = false;
            return;
        }

        if (mc.player.getHealth() > health.getValue()) {
            commandSent = false;
            return;
        }

        if (commandSent) {
            return;
        }

        String command = getLeaveCommand();
        if (command.isBlank()) {
            return;
        }

        PlayerUtility.runCmd(command);
        commandSent = true;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.player.connection != null
                && !mc.player.isSpectator();
    }

    private String getLeaveCommand() {
        return switch (mode.getValue()) {
            case HUB -> "/hub";
            case LOBBY -> "/lobby";
            case DA_TING -> "/\u5927\u5385";
            case CUSTOM -> value.getValue() == null ? "" : value.getValue();
        };
    }

    public enum Type {
        HUB,LOBBY,DA_TING,CUSTOM
    }
}
