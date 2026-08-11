package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import lombok.Getter;
import net.minecraft.client.gui.GuiGraphics;

@Getter
public class Render2DEvent implements Event {

    private final GuiGraphics guiGraphics;

    protected Render2DEvent(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
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
