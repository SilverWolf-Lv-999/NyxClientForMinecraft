package io.github.seraphina.nyx.client.utility;

import io.github.seraphina.nyx.client.module.client.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DebugUtility {
    public static final String PREFIX = "Nyx >> ";

    public static void msg(Object... msg) {
        if (!Debug.INSTANCE.isEnabled()) return;
        StringBuilder sb = new StringBuilder(PREFIX);
        for (Object o : msg)
            sb.append(o.toString());
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(sb.toString()));
    }
}
