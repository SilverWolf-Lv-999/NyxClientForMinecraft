package io.github.seraphina.nyx.client.utility;

import io.github.seraphina.nyx.client.manager.NotificationManager;
import io.github.seraphina.nyx.client.module.client.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class MsgUtility {
    public static final String PREFIX = "Nyx >> ";

    public static void debug(Object... msg) {
        if (!Debug.INSTANCE.isEnabled()) return;
        StringBuilder sb = new StringBuilder();
        if (msg != null) {
            for (Object o : msg) {
                sb.append(o);
            }
        }
        String message = sb.toString();
        Minecraft minecraft = Minecraft.getInstance();
        Runnable action = () -> {
            minecraft.gui.getChat().addMessage(Component.literal(PREFIX + message));
            NotificationManager.pushDebug(message);
        };

        if (minecraft.isSameThread()) {
            action.run();
        } else {
            minecraft.execute(action);
        }
    }
}
