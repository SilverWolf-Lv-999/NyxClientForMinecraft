package io.github.seraphina.nyx.client.module.visual.hud.component.text;

import io.github.seraphina.nyx.client.module.visual.hud.HUD;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public class LevelTypeView extends TextComponent {
    @Override
    public String getId() {
        return "level_type";
    }

    @Override
    public boolean isVisible() {
        return HUD.INSTANCE.levelType.getValue();
    }

    @Override
    public float getDefaultY() {
        return 68.0F;
    }

    @Override
    protected int accentColor() {
        return 0xFF57C7FF;
    }

    @Override
    public String getValue() {
        if (mc.level == null) {
            return "Dimension: N/A";
        }

        Level level = mc.level;
        return "Dimension: " + displayName(level.dimension().identifier());
    }

    private static String displayName(Identifier location) {
        String path = location.getPath();
        if ("the_nether".equals(path)) {
            return "Nether";
        }
        if ("the_end".equals(path)) {
            return "End";
        }
        if ("overworld".equals(path)) {
            return "Overworld";
        }
        return location.toString();
    }
}
