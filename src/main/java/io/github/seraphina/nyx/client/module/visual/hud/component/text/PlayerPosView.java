package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import net.minecraft.core.BlockPos;

public class PlayerPosView extends TextComponent {
    @Override
    public String getId() {
        return "player_pos";
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.playerPos.getValue();
    }

    @Override
    public float getDefaultY() {
        return 98.0F;
    }

    @Override
    protected int accentColor() {
        return 0xFFFFD166;
    }

    @Override
    public String getValue() {
        if (mc.player == null) {
            return "XYZ: N/A";
        }

        BlockPos pos = mc.player.blockPosition();
        return "XYZ: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
