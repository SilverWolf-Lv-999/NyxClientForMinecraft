package io.github.seraphina.nyx.client.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "nyxclient.module.maceeffect.name", description = "nyxclient.module.maceeffect.description", category = Category.VISUAL)
public class MaceEffect extends Module {
    public static final MaceEffect INSTANCE = new MaceEffect();

    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();

    public final IntValue duration = ValueBuild.intSetting("duration", 450, 100, 1500, 50, this);
    public final DoubleValue radius = ValueBuild.doubleSetting("radius", 3.2D, 0.5D, 8.0D, 0.1D, this);
    public final DoubleValue thickness = ValueBuild.doubleSetting("thickness", 0.35D, 0.05D, 1.5D, 0.01D, this);
    public final DoubleValue height = ValueBuild.doubleSetting("height", 0.06D, 0.0D, 1.0D, 0.01D, this);
    public final IntValue opacity = ValueBuild.intSetting("opacity", 180, 0, 255, 5, this);
    public final IntValue segments = ValueBuild.intSetting("segments", 50, 12, 120, 1, this);
    public final BoolValue pulse = ValueBuild.boolSetting("pulse", true, this);
    public final BoolValue doubleWave = ValueBuild.boolSetting("double wave", true, this);
    public final ColorValue color = ValueBuild.colorSetting("color", new Color(255, 255, 255, 180), true, this);

    public static void addShockwave(Vec3 position) {
        if (position == null || !INSTANCE.isEnabled() || INSTANCE.isNull()) {
            return;
        }

        synchronized (SHOCKWAVES) {
            SHOCKWAVES.add(new Shockwave(position, 0.55F));
            if (INSTANCE.doubleWave.getValue()) {
                SHOCKWAVES.add(new Shockwave(position, 1.0F));
            }
        }
    }

    @Override
    public void onDisable() {
        synchronized (SHOCKWAVES) {
            SHOCKWAVES.clear();
        }
    }

    @EventTarget(0)
    public void onAttack(AttackEntityEvent event) {
        if (event.isCancelled()
                || isNull()
                || event.getPlayer() != mc.player
                || !(event.getEntity() instanceof LivingEntity)
                || !mc.player.getMainHandItem().is(Items.MACE)
                || !isFallingMaceSmash()) {
            return;
        }

        Entity entity = event.getEntity();
        addShockwave(entity.position());
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (isNull()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Shockwave> shockwaves;
        synchronized (SHOCKWAVES) {
            if (SHOCKWAVES.isEmpty()) {
                return;
            }
            shockwaves = new ArrayList<>(SHOCKWAVES);
        }

        for (Shockwave shockwave : shockwaves) {
            float progress = (float)(now - shockwave.startTime()) / duration.getValue();
            if (progress >= 1.0F) {
                synchronized (SHOCKWAVES) {
                    SHOCKWAVES.remove(shockwave);
                }
                continue;
            }

            renderShockwave(event.getPoseStack(), shockwave, Mth.clamp(progress, 0.0F, 1.0F));
        }
    }

    private boolean isFallingMaceSmash() {
        return mc.player.fallDistance > 1.5F
                && !mc.player.onGround()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isPassenger();
    }

    private void renderShockwave(PoseStack poseStack, Shockwave shockwave, float progress) {
        float easedProgress = 1.0F - (1.0F - progress) * (1.0F - progress);
        float outerRadius = (float)(radius.getValue() * shockwave.scale() * easedProgress);
        float ringThickness = Math.max(0.03F, (float)(thickness.getValue() * (1.0F - progress * 0.65F)));
        float innerRadius = Math.max(0.0F, outerRadius - ringThickness);
        int baseAlpha = Math.min(opacity.getValue(), color.getValue().getAlpha());
        int alpha = Math.round(baseAlpha * (1.0F - progress));
        if (outerRadius <= 0.0F || alpha <= 0) {
            return;
        }

        Color renderColor = pulse.getValue() ? pulseColor(color.getValue(), progress) : color.getValue();
        int rgb = Render3DUtility.rgb(renderColor.getRed(), renderColor.getGreen(), renderColor.getBlue());
        int ringColor = Render3DUtility.withAlpha(rgb, alpha);
        Render3DUtility.renderFilledQuadsNoDepth(
                poseStack,
                buildRingQuads(shockwave.position(), outerRadius, innerRadius),
                ringColor
        );
    }

    private List<Render3DUtility.Quad> buildRingQuads(Vec3 center, float outerRadius, float innerRadius) {
        int count = Math.max(12, segments.getValue());
        double y = center.y + height.getValue();
        List<Render3DUtility.Quad> quads = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double nextAngle = Math.PI * 2.0D * (i + 1) / count;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double nextCos = Math.cos(nextAngle);
            double nextSin = Math.sin(nextAngle);

            quads.add(new Render3DUtility.Quad(
                    center.x + cos * outerRadius,
                    y,
                    center.z + sin * outerRadius,
                    center.x + nextCos * outerRadius,
                    y,
                    center.z + nextSin * outerRadius,
                    center.x + nextCos * innerRadius,
                    y,
                    center.z + nextSin * innerRadius,
                    center.x + cos * innerRadius,
                    y,
                    center.z + sin * innerRadius
            ));
        }

        return quads;
    }

    private static Color pulseColor(Color color, float progress) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float brightness = Mth.clamp(hsb[2] + (float)Math.sin(progress * Math.PI * 15.0D) * 0.18F, 0.0F, 1.0F);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], brightness);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, color.getAlpha());
    }

    private record Shockwave(Vec3 position, long startTime, float scale) {
        private Shockwave(Vec3 position, float scale) {
            this(position, System.currentTimeMillis(), scale);
        }
    }
}
