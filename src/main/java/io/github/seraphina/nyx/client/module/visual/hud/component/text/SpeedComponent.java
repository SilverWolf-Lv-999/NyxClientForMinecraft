package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class SpeedComponent extends TextComponent {
    @Override
    public String getId() {
        return "speed";
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.speed.getValue();
    }

    @Override
    public float getDefaultY() {
        return 158.0F;
    }

    @Override
    protected int accentColor() {
        return 0xFFFF8C42;
    }

    @Override
    public String getValue() {
        if (mc.player == null) {
            return "Speed: N/A";
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        double blocksPerSecond = Math.hypot(velocity.x, velocity.z) * 20.0D;
        return String.format(Locale.ROOT, "Speed: %.2f BPS", blocksPerSecond);
    }
}
