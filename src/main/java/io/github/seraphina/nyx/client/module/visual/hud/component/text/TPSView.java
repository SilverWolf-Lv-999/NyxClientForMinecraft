package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import net.minecraft.client.server.IntegratedServer;

import java.util.Locale;

public class TPSView extends TextComponent {
    @Override
    public String getId() {
        return "tps";
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.tps.getValue();
    }

    @Override
    public float getDefaultY() {
        return 38.0F;
    }

    @Override
    protected int accentColor() {
        return 0xFF53E08C;
    }

    @Override
    public String getValue() {
        if (mc.level == null) {
            return "TPS: N/A";
        }

        float targetTickRate = mc.level.tickRateManager().tickrate();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return String.format(Locale.ROOT, "TPS: %.1f", targetTickRate);
        }

        float tickTime = server.getCurrentSmoothedTickTime();
        if (tickTime <= 0.0F || server.tickRateManager().isSprinting()) {
            return String.format(Locale.ROOT, "TPS: %.1f", targetTickRate);
        }

        float tps = Math.min(targetTickRate, 1000.0F / tickTime);
        return String.format(Locale.ROOT, "TPS: %.1f (%.1f ms)", tps, tickTime);
    }
}
