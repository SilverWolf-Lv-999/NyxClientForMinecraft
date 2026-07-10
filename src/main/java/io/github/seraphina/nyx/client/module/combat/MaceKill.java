package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.events.impl.TravelEvent;
import io.github.seraphina.nyx.client.manager.RotationManager;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.other.Target;
import io.github.seraphina.nyx.client.utility.MsgUtility;
import io.github.seraphina.nyx.client.utility.rotation.Priority;
import io.github.seraphina.nyx.client.utility.rotation.RotationUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.macekill.name", description = "nyxclient.module.macekill.description", category = Category.COMBAT)
public class MaceKill extends Module {
    public static final MaceKill INSTANCE = new MaceKill();

    private static final int TICKS_PER_SECOND = 20;

    public final DoubleValue range = ValueBuild.doubleValue("range", 4.5D, 1.0D, 8.0D, 0.1D, this);
    public final IntValue cps = ValueBuild.intSetting("cps", 20, 1, 20, 1, this);
    public final BoolValue autodisble = ValueBuild.boolSetting("auto disable", false, this);

    private int attackProgress;

    @Override
    public void onEnable() {
        attackProgress = TICKS_PER_SECOND;
        MsgUtility.debug("MaceKill enabled: range=", format(range.getValue()), ", cps=", cps.getValue());
    }

    @Override
    public void onDisable() {
        attackProgress = 0;
        MsgUtility.debug("MaceKill disabled");
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            MsgUtility.debug("MaceKill disabled: server corrected position");
            setEnabled(false);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            this.setEnabled(false);
            return;
        }

        LivingEntity target = findTarget(range.getValue());
        if (target == null) {
            attackProgress = Math.min(attackProgress + cps.getValue(), TICKS_PER_SECOND);
            return;
        }

        attackProgress += cps.getValue();
        if (attackProgress >= TICKS_PER_SECOND) {
            attackProgress -= TICKS_PER_SECOND;
            attackTarget(target);
        }
    }

    @EventTarget
    public void onTravel(TravelEvent event) {
        if (mc.player != null && mc.player.positionReminder < 19) {
            event.setCancelled(true);
        }
    }

    private void attackTarget(LivingEntity target) {
        Vector2f rotations = RotationUtility.calculate(target, true, range.getValue());
        RotationManager.INSTANCE.setRotations(rotations, 180.0D, Priority.Highest);

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        MsgUtility.debug(
                "MaceKill attacked ",
                target.getName().getString(),
                ": range=",
                format(RotationUtility.getEyeDistanceToEntity(target))
        );
        if (this.autodisble.getValue()) {
            this.setEnabled(false);
            MsgUtility.debug("MaceKill disabled");
        }
    }

    private LivingEntity findTarget(double attackRange) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        AABB searchBox = mc.player.getBoundingBox().inflate(attackRange);
        List<Entity> entities = mc.level.getEntities(
                mc.player,
                searchBox,
                entity -> entity instanceof LivingEntity livingEntity && isValidTarget(livingEntity, attackRange)
        );

        return entities.stream()
                .map(LivingEntity.class::cast)
                .min(Comparator.comparingDouble(RotationUtility::getEyeDistanceToEntity))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity, double attackRange) {
        return entity != mc.player
                && entity.isAlive()
                && Target.isTarget(entity)
                && !entity.isSpectator()
                && entity.isPickable()
                && !entity.isInvulnerable()
                && RotationUtility.getEyeDistanceToEntity(entity) <= attackRange;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
