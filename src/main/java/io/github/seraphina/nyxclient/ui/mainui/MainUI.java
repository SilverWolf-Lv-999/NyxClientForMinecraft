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
    public void renderBackground(GuiGraphics p_283688_, int p_296369_, int p_296477_, float p_294317_) {
        super.renderBackground(p_283688_, p_296369_, p_296477_, p_294317_);
        Render2DUtility.drawRect(10, 10, 42, 252, Color.RED.getRGB());
    }
}
