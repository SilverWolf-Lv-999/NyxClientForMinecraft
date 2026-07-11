package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import net.minecraft.core.BlockPos;

public class BiomeView extends TextComponent {
    @Override
    public String getId() {
        return "biome";
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.biome.getValue();
    }

    @Override
    public float getDefaultY() {
        return 128.0F;
    }

    @Override
    protected int accentColor() {
        return 0xFFB38CFF;
    }

    @Override
    public String getValue() {
        if (mc.level == null || mc.player == null) {
            return "Biome: N/A";
        }

        BlockPos pos = mc.player.blockPosition();
        return "Biome: " + mc.level.getBiome(pos).getRegisteredName();
    }
}
