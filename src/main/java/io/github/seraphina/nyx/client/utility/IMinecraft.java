package io.github.seraphina.nyx.client.utility;

import net.minecraft.client.Minecraft;

public interface IMinecraft {
    Minecraft mc = Minecraft.getInstance();

    default boolean isNull() {
        return mc.player == null || mc.level == null;
    }
}
