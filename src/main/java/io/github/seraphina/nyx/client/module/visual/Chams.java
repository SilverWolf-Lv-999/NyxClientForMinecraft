package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;

@ModuleInfo(name = "nyxclient.module.chams.name", description = "nyxclient.module.chams.description", category = Category.VISUAL)
public class Chams extends Module {
    public static final Chams INSTANCE = new Chams();

    private static final RenderPipeline NO_DEPTH_ENTITY_TRANSLUCENT_PIPELINE = RenderPipelines.ENTITY_TRANSLUCENT.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath("nyxclient", "pipeline/chams_entity_translucent"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();
    private static final BiFunction<Identifier, Boolean, RenderType> NO_DEPTH_ENTITY_TRANSLUCENT = Util.memoize(
            (texture, outline) -> RenderType.create(
                    "nyx_chams_entity_translucent",
                    RenderSetup.builder(NO_DEPTH_ENTITY_TRANSLUCENT_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .sortOnUpload()
                            .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
                            .setOutline(outline ? RenderSetup.OutlineProperty.AFFECTS_OUTLINE : RenderSetup.OutlineProperty.NONE)
                            .createRenderSetup()
            )
    );

    public final IntValue renderRange = ValueBuild.intSetting("render range", 128, 8, 512, 8, this);
    public final BoolValue throughWalls = ValueBuild.boolSetting("through walls", true, this);
    public final BoolValue players = ValueBuild.boolSetting("players", true, this);
    public final BoolValue monsters = ValueBuild.boolSetting("monsters", true, this);
    public final BoolValue animals = ValueBuild.boolSetting("animals", false, this);
    public final BoolValue villagers = ValueBuild.boolSetting("villagers", true, this);
    public final BoolValue self = ValueBuild.boolSetting("self", false, this);
    public final ColorValue color = ValueBuild.colorSetting("color", new Color(84, 170, 255, 115), true, this);

    private final Map<LivingEntityRenderState, LivingEntity> entities = new IdentityHashMap<>();

    public void rememberEntity(LivingEntity entity, LivingEntityRenderState state) {
        if (!isEnabled() || entity == null || state == null) {
            return;
        }

        entities.put(state, entity);
    }

    @Override
    public void onDisable() {
        entities.clear();
    }

    public RenderType getRenderType(LivingEntityRenderState state, Identifier texture, RenderType original) {
        if (original == null || texture == null || !shouldRenderChams(state)) {
            return original;
        }

        if (throughWalls.getValue()) {
            return NO_DEPTH_ENTITY_TRANSLUCENT.apply(texture, !state.isInvisibleToPlayer);
        }
        return RenderTypes.entityTranslucent(texture, !state.isInvisibleToPlayer);
    }

    public int getModelTint(LivingEntityRenderState state, int original) {
        if (!shouldRenderChams(state)) {
            return original;
        }

        Color value = color.getValue();
        int chamsColor = Render3DUtility.rgba(value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha());
        return multiplyColor(original, chamsColor);
    }

    private boolean shouldRenderChams(LivingEntityRenderState state) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return false;
        }

        LivingEntity entity = entities.get(state);
        if (entity == null || entity.isRemoved() || entity.isSpectator()) {
            return false;
        }

        if (entity == mc.player) {
            return self.getValue();
        }

        int range = renderRange.getValue();
        if (mc.player.distanceToSqr(entity) > (double)range * range) {
            return false;
        }

        return switch (entity) {
            case Player ignored -> players.getValue();
            case Monster ignored -> monsters.getValue();
            case Villager ignored -> villagers.getValue();
            case WanderingTrader ignored -> villagers.getValue();
            default -> entity instanceof Animal && animals.getValue();
        };
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
}
