package io.github.seraphina.nyx.client.utility;

import io.github.seraphina.nyx.client.manager.NotificationManager;
import io.github.seraphina.nyx.client.module.client.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class MsgUtility {
    public static final String PREFIX = "Nyx >> ";
    public static final int DEBUG_SHADOW_COLOR = 0xFF030712;

    private static final int DEBUG_PREFIX_COLOR = 0xFFD166;
    private static final int DEBUG_MESSAGE_COLOR = 0xF2FBFF;
    private static final Style DEBUG_PREFIX_STYLE = Style.EMPTY
        .withColor(DEBUG_PREFIX_COLOR)
        .withShadowColor(DEBUG_SHADOW_COLOR)
        .withBold(true);
    private static final Style DEBUG_MESSAGE_STYLE = Style.EMPTY
        .withColor(DEBUG_MESSAGE_COLOR)
        .withShadowColor(DEBUG_SHADOW_COLOR);

    public static void debug(Object... msg) {
        if (!Debug.INSTANCE.isEnabled()) return;
        push(msg, true);
    }

    public static void info(Object... msg) {
        push(msg, false);
    }

    private static void push(Object[] msg, boolean debug) {
        StringBuilder sb = new StringBuilder();
        if (msg != null) {
            for (Object o : msg) {
                sb.append(o);
            }
        }
        String message = sb.toString();
        Minecraft minecraft = Minecraft.getInstance();
        Runnable action = () -> {
            minecraft.gui.getChat().addMessage(debugComponent(message));
            if (debug) {
                NotificationManager.pushDebug(message);
            } else {
                NotificationManager.pushInfo(message);
            }
        };

        if (minecraft.isSameThread()) {
            action.run();
        } else {
            minecraft.execute(action);
        }
    }

    private static Component debugComponent(String message) {
        return Component.empty()
            .append(Component.literal(PREFIX).withStyle(DEBUG_PREFIX_STYLE))
            .append(Component.literal(message).withStyle(DEBUG_MESSAGE_STYLE));
    }
}
