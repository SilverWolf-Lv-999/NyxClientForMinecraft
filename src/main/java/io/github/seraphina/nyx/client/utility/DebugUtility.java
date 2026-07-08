package io.github.seraphina.nyx.client.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DebugUtility {
    public static void msg(Object... msg) {
        StringBuilder sb = new StringBuilder("[Nyx] ");
        for (Object o : msg)
            sb.append(o.toString());
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(sb.toString()));
    }
}
