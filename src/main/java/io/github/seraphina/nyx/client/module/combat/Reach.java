package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.rotation.RaytraceUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector2f;

@ModuleInfo(name = "nyxclient.module.reach.name", description = "nyxclient.module.reach.description", category = Category.COMBAT)
public class Reach extends Module {
    public static final Reach INSTANCE = new Reach();

    private static final double VANILLA_ENTITY_RANGE = 3.0D;

    public final DoubleValue minRange = ValueBuild.doubleValue("minrange", 3.0D, VANILLA_ENTITY_RANGE, 6.0D, 0.01D, this);
    public final DoubleValue range = ValueBuild.doubleValue("range", 4.0D, VANILLA_ENTITY_RANGE, 6.0D, 0.01D, this);
    public final BoolValue bufferAbuse = ValueBuild.boolValue("bufferabuse", false, this);
    public final DoubleValue bufferDecrease = ValueBuild.doubleValue("bufferdecrease", 1.0D, 0.1D, 10.0D, 0.1D, () -> bufferAbuse.getValue(), this);
    public final IntValue maxBuffer = ValueBuild.intValue("maxbuffer", 5, 1, 200, 1, () -> bufferAbuse.getValue(), this);

    private int lastId = -1;
    private int attackTicks;
    private double combo;

    @Override
    public void onEnable() {
        resetBuffer();
    }

    @Override
    public void onDisable() {
        resetBuffer();
    }

    public double getEntityRange(double original) {
        if (!isEnabled()) {
            return original;
        }

        double min = Math.min(minRange.getValue(), range.getValue());
        double max = Math.max(minRange.getValue(), range.getValue());
        return Math.max(original, randomRange(min, max));
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        attackTicks++;
    }

    @EventTarget
    public void onAttack(AttackEntityEvent event) {
        if (event.getPlayer() != mc.player) {
            return;
        }

        Entity entity = event.getEntity();
        if (bufferAbuse.getValue()) {
            if (!isVanillaRaytraceEntity()) {
                if ((attackTicks > 9 || entity.getId() != lastId) && combo < maxBuffer.getValue()) {
                    combo++;
                } else {
                    event.setCancelled(true);
                }
            } else {
                combo = Math.max(0.0D, combo - bufferDecrease.getValue());
            }
        } else {
            combo = 0.0D;
        }

        lastId = entity.getId();
        attackTicks = 0;
    }

    private boolean isVanillaRaytraceEntity() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        HitResult hitResult = RaytraceUtility.raytrace(getRaytraceRotation(), VANILLA_ENTITY_RANGE);
        return hitResult instanceof EntityHitResult;
    }

    private Vector2f getRaytraceRotation() {
        Vector2f rotation = RotationManager.INSTANCE.getRotation();
        if (rotation != null) {
            return rotation;
        }

        if (mc.player == null) {
            return new Vector2f();
        }

        return new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private double randomRange(double min, double max) {
        if (max <= min) {
            return min;
        }

        return min + Math.random() * (max - min);
    }

    private void resetBuffer() {
        lastId = -1;
        attackTicks = 0;
        combo = 0.0D;
    }
}
