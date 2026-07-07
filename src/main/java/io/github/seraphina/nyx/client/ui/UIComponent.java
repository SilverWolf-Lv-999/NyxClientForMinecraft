package io.github.seraphina.nyx.client.ui;

import io.github.seraphina.nyx.client.utility.IMinecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

public interface UIComponent extends IMinecraft {
    default String getId() {
        return getClass().getSimpleName();
    }

    default boolean isVisible() {
        return true;
    }

    default float getDefaultX() {
        return 8.0F;
    }

    default float getDefaultY() {
        return 8.0F;
    }

    default float getDefaultScale() {
        return 1.0F;
    }

    void render(GuiGraphics graphics, float partialTicks, float scale);

    AABB getBoundingBox();
}
