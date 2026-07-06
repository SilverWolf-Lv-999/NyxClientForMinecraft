package io.github.seraphina.nyxclient.ui.mainui;

import io.github.seraphina.nyxclient.utility.Render2DUtility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;

public final class MainUI extends Screen {
    public MainUI() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Render2DUtility.withGuiGraphics(guiGraphics, () ->
            Render2DUtility.drawRect(10, 10, 42, 252, Color.RED.getRGB())
        );
    }
}
