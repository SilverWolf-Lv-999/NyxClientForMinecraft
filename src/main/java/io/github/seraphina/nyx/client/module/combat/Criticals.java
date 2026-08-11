package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.criticals.name", description = "nyxclient.module.criticals.description", category = Category.COMBAT)
public final class Criticals extends Module {
    public static final Criticals INSTANCE = new Criticals();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.OLD_NCP, this);
    public final BoolValue onlyGround = ValueBuild.boolSetting(
            "only ground",
            true,
            () -> !mode.is(Mode.GROUND),
            this
    );
    public final BoolValue setNoGround = ValueBuild.boolSetting(
            "set no ground",
            false,
            () -> mode.is(Mode.GROUND),
            this
    );
    public final BoolValue blockCheck = ValueBuild.boolSetting(
            "block check",
            true,
            () -> mode.is(Mode.GROUND),
            this
    );
    public final BoolValue autoJump = ValueBuild.boolSetting(
            "auto jump",
            true,
            () -> mode.is(Mode.GROUND),
            this
    );
    public final BoolValue mini = ValueBuild.boolSetting(
            "mini",
            true,
            () -> mode.is(Mode.GROUND) && autoJump.getValue(),
            this
    );
    public final DoubleValue motionY = ValueBuild.doubleSetting(
            "motion y",
            0.05D,
            0.0D,
            1.0D,
            0.01D,
            () -> mode.is(Mode.GROUND) && autoJump.getValue(),
            this
    );
    public final BoolValue autoDisable = ValueBuild.boolSetting(
            "auto disable",
            true,
            () -> mode.is(Mode.GROUND),
            this
    );
    public final BoolValue crawlingDisable = ValueBuild.boolSetting(
            "crawling disable",
            true,
            () -> mode.is(Mode.GROUND),
            this
    );
    public final BoolValue flight = ValueBuild.boolSetting(
            "flight",
            false,
            () -> mode.is(Mode.GROUND),
            this
    );

    private boolean requireJump;

    private Criticals() {
    }

    @Override
    public void onEnable() {
        requireJump = true;
        if (!mode.is(Mode.GROUND)) {
            return;
        }

        if (isNull()) {
            if (autoDisable.getValue()) {
                setEnabled(false);
            }
            return;
        }

        if (shouldDisableGroundMode()) {
            setEnabled(false);
            return;
        }

        if (mc.player.onGround()
                && autoJump.getValue()
                && (!blockCheck.getValue() || hasJumpClearance())) {
            jump();
        }
    }

    @Override
    public void onDisable() {
        requireJump = false;
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled() || mc.player == null || mc.level == null || mc.player.connection == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (mode.is(Mode.GROUND)) {
            if (setNoGround.getValue()
                    && packet instanceof ServerboundMovePlayerPacket movePacket
                    && movePacket.isOnGround()) {
                event.setPacket(withoutGround(movePacket));
            }
            return;
        }

        if (!(packet instanceof ServerboundInteractPacket interactPacket)
                || !isAttack(interactPacket)
                || !canCrit()) {
            return;
        }

        sendCriticalPackets();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!mode.is(Mode.GROUND) || isNull()) {
            return;
        }

        if (shouldDisableGroundMode()) {
            setEnabled(false);
            return;
        }

        if (flight.getValue() && mc.player.fallDistance > 0.0F) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            requireJump = false;
            return;
        }

        if (blockCheck.getValue() && !hasJumpClearance()) {
            requireJump = true;
        }

        if (mc.player.onGround()
                && autoJump.getValue()
                && (flight.getValue() || requireJump)
                && (!blockCheck.getValue() || hasJumpClearance())) {
            jump();
            requireJump = false;
        }
    }

    private boolean shouldDisableGroundMode() {
        return autoDisable.getValue() && MovingUtility.isMoving()
                || crawlingDisable.getValue() && mc.player.getPose() == Pose.SWIMMING;
    }

    private boolean hasJumpClearance() {
        return mc.level.noBlockCollision(mc.player, mc.player.getBoundingBox().move(0.0D, 2.0D, 0.0D));
    }

    private void jump() {
        if (mini.getValue()) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, motionY.getValue(), velocity.z);
        } else {
            mc.player.jumpFromGround();
        }
    }

    private boolean canCrit() {
        return !isCrystalAttack()
                && (!onlyGround.getValue() || mc.player.onGround() || mc.player.getAbilities().flying)
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private boolean isCrystalAttack() {
        return mc.hitResult instanceof EntityHitResult hitResult && hitResult.getEntity() instanceof EndCrystal;
    }

    private boolean isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
    }

    private void sendCriticalPackets() {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        switch (mode.getValue()) {
            case UPDATED_NCP -> {
                sendPosition(x, y + 2.71875E-7D, z, false);
                sendPosition(x, y, z, false);
            }
            case STRICT -> {
                sendPosition(x, y + 0.062600301692775D, z, false);
                sendPosition(x, y + 0.07260029960661D, z, false);
                sendPosition(x, y, z, false);
                sendPosition(x, y, z, false);
            }
            case NCP -> {
                sendPosition(x, y + 0.0625D, z, false);
                sendPosition(x, y, z, false);
            }
            case OLD_NCP -> {
                sendPosition(x, y + 1.058293536E-5D, z, false);
                sendPosition(x, y + 9.16580235E-6D, z, false);
                sendPosition(x, y + 1.0371854E-7D, z, false);
            }
            case HYPIXEL_2022 -> {
                sendPosition(x, y + 0.0045D, z, true);
                sendPosition(x, y + 1.52121E-4D, z, false);
                sendPosition(x, y + 0.3D, z, false);
                sendPosition(x, y + 0.025D, z, false);
            }
            case PACKET -> {
                sendPosition(x, y + 5.0E-4D, z, false);
                sendPosition(x, y + 1.0E-4D, z, false);
            }
            case BBTT -> {
                if (MovingUtility.isMoving() || mc.player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D) {
                    return;
                }

                sendPosition(x, y, z, true);
                sendPosition(x, y + 0.0625D, z, false);
                sendPosition(x, y + 0.045D, z, false);
            }
            case GROUND -> {
            }
        }
    }

    private void sendPosition(double x, double y, double z, boolean onGround) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x,
                y,
                z,
                onGround,
                mc.player.horizontalCollision
        ));
    }

    private ServerboundMovePlayerPacket withoutGround(ServerboundMovePlayerPacket packet) {
        if (packet.hasPosition() && packet.hasRotation()) {
            return new ServerboundMovePlayerPacket.PosRot(
                    packet.getX(0.0D),
                    packet.getY(0.0D),
                    packet.getZ(0.0D),
                    packet.getYRot(0.0F),
                    packet.getXRot(0.0F),
                    false,
                    packet.horizontalCollision()
            );
        }
        if (packet.hasPosition()) {
            return new ServerboundMovePlayerPacket.Pos(
                    packet.getX(0.0D),
                    packet.getY(0.0D),
                    packet.getZ(0.0D),
                    false,
                    packet.horizontalCollision()
            );
        }
        if (packet.hasRotation()) {
            return new ServerboundMovePlayerPacket.Rot(
                    packet.getYRot(0.0F),
                    packet.getXRot(0.0F),
                    false,
                    packet.horizontalCollision()
            );
        }
        return new ServerboundMovePlayerPacket.StatusOnly(false, packet.horizontalCollision());
    }

    public enum Mode {
        UPDATED_NCP,
        STRICT,
        NCP,
        OLD_NCP,
        HYPIXEL_2022,
        PACKET,
        GROUND,
        BBTT
    }
}
