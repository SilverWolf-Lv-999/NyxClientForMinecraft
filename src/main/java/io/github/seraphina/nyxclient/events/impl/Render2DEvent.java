package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.Event;

import net.minecraft.client.gui.GuiGraphics;

public class Render2DEvent implements Event {

    private final GuiGraphics guiGraphics;

    protected Render2DEvent(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public static final class Level extends Render2DEvent {
        public Level(GuiGraphics guiGraphics) {
            super(guiGraphics);
        }
    }

    public static final class HUD extends Render2DEvent {
        public HUD(GuiGraphics guiGraphics) {
            super(guiGraphics);
        }
    }

}
