package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.manager.FriendManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.client.Friend;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Map;

@ModuleInfo(name = "nyxclient.module.shader.name", description = "nyxclient.module.shader.description", category = Category.VISUAL)
public final class Shader extends Module {
    public static final Shader INSTANCE = new Shader();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.SOLID, this);
    public final IntValue speed = ValueBuild.intSetting("speed", 4, 0, 20, 1, () -> !mode.is(Mode.SOLID), this);
    public final ColorValue color = ValueBuild.colorSetting("color", Color.WHITE, false, this);
    public final IntValue renderRange = ValueBuild.intSetting("render range", 64, 16, 512, 8, this);
    public final BoolValue self = ValueBuild.boolSetting("self", true, this);
    public final BoolValue players = ValueBuild.boolSetting("players", true, this);
    public final BoolValue friends = ValueBuild.boolSetting("friends", true, this);
    public final BoolValue monsters = ValueBuild.boolSetting("monsters", false, this);
    public final BoolValue animals = ValueBuild.boolSetting("animals", false, this);
    public final BoolValue villagers = ValueBuild.boolSetting("villagers", false, this);

    private final Map<LivingEntityRenderState, LivingEntity> entities = new IdentityHashMap<>();

    public void rememberEntity(LivingEntity entity, LivingEntityRenderState state) {
        if (isEnabled() && entity != null && state != null) {
            entities.put(state, entity);
        }
    }

    @Override
    public void onDisable() {
        entities.clear();
    }

    public int getModelTint(LivingEntityRenderState state, int original) {
        if (!shouldRender(entities.get(state))) {
            return original;
        }

        Color tint = getTintColor();
        int tintColor = Render3DUtility.rgba(tint.getRed(), tint.getGreen(), tint.getBlue(), tint.getAlpha());
        return multiplyColor(original, tintColor);
    }

    private boolean shouldRender(LivingEntity entity) {
        if (!isEnabled() || mc.player == null || mc.level == null || entity == null || entity.isRemoved() || entity.isSpectator()) {
            return false;
        }

        if (mc.player.distanceToSqr(entity) > (double) renderRange.getValue() * renderRange.getValue()) {
            return false;
        }

        if (entity == mc.player) {
            return self.getValue();
        }

        return switch (entity) {
            case Player ignored when Friend.INSTANCE.isEnabled() && FriendManager.isFriend(entity) -> friends.getValue();
            case Player ignored -> players.getValue();
            case Monster ignored -> monsters.getValue();
            case Villager ignored -> villagers.getValue();
            case WanderingTrader ignored -> villagers.getValue();
            default -> entity instanceof Animal && animals.getValue();
        };
    }

    private Color getTintColor() {
        Color baseColor = color.getValue();
        if (mode.is(Mode.SOLID) || speed.getValue() == 0) {
            return baseColor;
        }

        double animationTime = System.currentTimeMillis() / 1_000.0D * speed.getValue();
        if (mode.is(Mode.PULSE)) {
            float brightness = (float) (0.35D + 0.65D * ((Math.sin(animationTime * Math.PI) + 1.0D) / 2.0D));
            return new Color(
                    Math.round(baseColor.getRed() * brightness),
                    Math.round(baseColor.getGreen() * brightness),
                    Math.round(baseColor.getBlue() * brightness),
                    baseColor.getAlpha()
            );
        }

        int rgb = Color.HSBtoRGB((float) ((animationTime * 0.1D) % 1.0D), 0.85F, 1.0F);
        return new Color(rgb);
    }

    private static int multiplyColor(int base, int tint) {
        int alpha = multiplyChannel(base >>> 24, tint >>> 24);
        int red = multiplyChannel(base >>> 16, tint >>> 16);
        int green = multiplyChannel(base >>> 8, tint >>> 8);
        int blue = multiplyChannel(base, tint);
        return Render3DUtility.rgba(red, green, blue, alpha);
    }

    private static int multiplyChannel(int first, int second) {
        return ((first & 0xFF) * (second & 0xFF)) / 255;
    }

    public enum Mode {
        SOLID,
        PULSE,
        RAINBOW
    }
}
