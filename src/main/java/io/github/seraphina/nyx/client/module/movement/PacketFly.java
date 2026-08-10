package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@ModuleInfo(name = "nyxclient.module.packetfly.name", description = "nyxclient.module.packetfly.description", category = Category.MOVEMENT)
public class PacketFly extends Module {
    public static final PacketFly INSTANCE = new PacketFly();

    private static final int INITIAL_PACKET_INTERVAL = 6;
    private static final int ANTI_KICK_SETBACK_INTERVAL = 10;
    private static final int ANTI_KICK_INTERVAL = 20;
    private static final int IDLE_PACKET_INTERVAL = 4;
    private static final long PENDING_TELEPORT_MAX_AGE_MS = 30_000L;
    private static final double UPWARD_SPEED = 0.062D;
    private static final double DOWNWARD_SPEED = -0.062D;
    private static final double UPWARD_JITTER_SPEED = 0.061D;
    private static final double DOWNWARD_JITTER_SPEED = -0.061D;
    private static final double ANTI_KICK_UPWARD_SPEED = 0.062D;
    private static final double ANTI_KICK_DOWNWARD_SPEED = -0.032D;
    private static final double ANTI_KICK_IDLE_SPEED = -0.04D;
    private static final double NORMAL_HORIZONTAL_SPEED = 0.26D;
    private static final double PHASE_HORIZONTAL_SPEED = 0.031D;
    private static final double JITTER_HORIZONTAL_SPEED = 0.25D;
    private static final double JITTER_PHASE_HORIZONTAL_SPEED = 0.03D;
    private static final double FULL_PHASE_VERTICAL_DIVISOR = 2.5D;
    private static final double BOUNDING_BOX_OFFSET = -0.0625D;
    private static final int PRESERVE_COORDINATE_RANGE = 29_000_000;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.FACTOR, this);
    public final DoubleValue factor = ValueBuild.doubleSetting("factor", 1.0D, 0.0D, 10.0D, 1.0D, this);
    public final EnumValue<Phase> phase = ValueBuild.enumSetting("phase", Phase.FULL, this);
    public final EnumValue<Type> type = ValueBuild.enumSetting("type", Type.UP, this);
    public final BoolValue antiKick = ValueBuild.boolSetting("anti kick", true, this);
    public final BoolValue noRotation = ValueBuild.boolSetting("no rotation", false, this);
    public final BoolValue noMovePacket = ValueBuild.boolSetting("no move packet", false, this);
    public final BoolValue boundingBoxOffset = ValueBuild.boolSetting("bounding box offset", false, this);
    public final IntValue invalidOffset = ValueBuild.intSetting("invalid offset", 1337, 0, 1337, 1, this);
    public final IntValue invalidPackets = ValueBuild.intSetting("invalid packets", 1, 0, 10, 1, this);
    public final IntValue teleportPackets = ValueBuild.intSetting("teleport packets", 1, 0, 10, 1, this);
    public final DoubleValue concealY = ValueBuild.doubleSetting("conceal y", 0.0D, -256.0D, 256.0D, 1.0D, this);
    public final DoubleValue concealMultiplier = ValueBuild.doubleSetting("conceal multiplier", 1.0D, 0.0D, 2.0D, 0.1D, this);
    public final DoubleValue verticalMultiplier = ValueBuild.doubleSetting("vertical multiplier", 1.0D, 0.0D, 2.0D, 0.1D, this);
    public final DoubleValue horizontalMultiplier = ValueBuild.doubleSetting("horizontal multiplier", 1.0D, 0.0D, 2.0D, 0.1D, this);
    public final BoolValue elytra = ValueBuild.boolSetting("elytra", false, this);
    public final BoolValue horizontalJitter = ValueBuild.boolSetting("horizontal jitter", false, this);
    public final BoolValue verticalJitter = ValueBuild.boolSetting("vertical jitter", false, this);
    public final BoolValue zeroSpeed = ValueBuild.boolSetting("zero speed", false, this);
    public final BoolValue zeroY = ValueBuild.boolSetting("zero y", false, this);
    public final BoolValue zeroTeleport = ValueBuild.boolSetting("zero teleport", true, this);
    public final IntValue zoomer = ValueBuild.intSetting("zoomies", 3, 0, 10, 1, this);

    private final Map<Integer, TimedPosition> pendingTeleports = new ConcurrentHashMap<>();
    private final Set<Packet<?>> sentMovePackets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger teleportId = new AtomicInteger();

    private LocalPlayer noPhysicsPlayer;
    private boolean originalNoPhysics;
    private int packetCounter;
    private boolean zoomies;
    private double lastFactor = 1.0D;
    private int zoomTimer;

    @Override
    public void onEnable() {
        clearState();
        if (isNull() || mc.player.connection == null) {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        restoreNoPhysics();
        clearState();
    }

    @EventTarget
    public void onTick(TickEvent.Pre event) {
        prunePendingTeleports();
        if (isNull()) {
            return;
        }

        updateNoPhysics();
        if (mode.is(Mode.COMPATIBILITY)) {
            return;
        }

        runPacketFly();
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (isNull() || mode.is(Mode.COMPATIBILITY)
                || !(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)) {
            return;
        }

        Vec3 correctionPosition = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(mc.player),
                packet.change(),
                packet.relatives()
        ).position();
        TimedPosition sentPosition = pendingTeleports.remove(packet.id());

        if (mc.player.isAlive()
                && !mode.is(Mode.SETBACK)
                && !mode.is(Mode.SLOW)
                && sentPosition != null
                && sentPosition.matches(correctionPosition)) {
            event.setCancelled(true);
            return;
        }

        teleportId.set(packet.id());
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.COMPATIBILITY)
                || !(event.getPacket() instanceof ServerboundMovePlayerPacket movePacket)
                || sentMovePackets.remove(movePacket)) {
            return;
        }

        if (movePacket instanceof ServerboundMovePlayerPacket.Rot && !noRotation.getValue()) {
            return;
        }

        if (noMovePacket.getValue()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        setEnabled(false);
    }

    private void runPacketFly() {
        mc.player.setDeltaMovement(Vec3.ZERO);

        if (!mode.is(Mode.SETBACK) && teleportId.get() == 0) {
            if (checkPackets(INITIAL_PACKET_INTERVAL)) {
                sendPackets(0.0D, 0.0D, 0.0D, true);
            }
            return;
        }

        boolean phasing = isInsideBlock();
        double verticalSpeed = verticalSpeed(phasing);
        if (phase.is(Phase.FULL) && phasing && MovingUtility.isMoving() && verticalSpeed != 0.0D) {
            verticalSpeed /= FULL_PHASE_VERTICAL_DIVISOR;
        }

        double horizontalSpeed = phase.is(Phase.FULL) && phasing
                ? (horizontalJitter.getValue() && zoomies ? JITTER_PHASE_HORIZONTAL_SPEED : PHASE_HORIZONTAL_SPEED)
                : (horizontalJitter.getValue() && zoomies ? JITTER_HORIZONTAL_SPEED : NORMAL_HORIZONTAL_SPEED);
        Vec3 horizontalVelocity = MovingUtility.horizontalVelocity(horizontalSpeed, mc.player.getYRot());

        updateFactor();
        int packetFactor = mode.getValue().usesFactor() ? (int) Math.ceil(lastFactor) : 1;
        Vec3 movement = Vec3.ZERO;
        for (int packetIndex = 1; packetIndex <= packetFactor; packetIndex++) {
            if (packetIndex > lastFactor && mode.getValue().usesFactor()) {
                break;
            }

            double conceal = mc.player.getY() < concealY.getValue() && MovingUtility.isMoving()
                    ? concealMultiplier.getValue()
                    : 1.0D;
            movement = new Vec3(
                    horizontalVelocity.x * packetIndex * conceal * horizontalMultiplier.getValue(),
                    verticalSpeed * packetIndex * verticalMultiplier.getValue(),
                    horizontalVelocity.z * packetIndex * conceal * horizontalMultiplier.getValue()
            );
            mc.player.setDeltaMovement(movement);
            sendPackets(movement.x, movement.y, movement.z, !mode.is(Mode.SETBACK));
        }

        applyLocalMovement(movement);
        updateZoomies();
    }

    private double verticalSpeed(boolean phasing) {
        if (mc.options.keyJump.isDown() && (phasing || !MovingUtility.isMoving())) {
            if (antiKick.getValue() && !phasing) {
                return checkPackets(mode.is(Mode.SETBACK) ? ANTI_KICK_SETBACK_INTERVAL : ANTI_KICK_INTERVAL)
                        ? ANTI_KICK_DOWNWARD_SPEED
                        : ANTI_KICK_UPWARD_SPEED;
            }

            return verticalJitter.getValue() && zoomies ? UPWARD_JITTER_SPEED : UPWARD_SPEED;
        }

        if (mc.options.keyShift.isDown()) {
            return verticalJitter.getValue() && zoomies ? DOWNWARD_JITTER_SPEED : DOWNWARD_SPEED;
        }

        if (!phasing && checkPackets(IDLE_PACKET_INTERVAL) && antiKick.getValue()) {
            return ANTI_KICK_IDLE_SPEED;
        }

        return 0.0D;
    }

    private void updateFactor() {
        if (!mode.is(Mode.INCREMENT)) {
            lastFactor = factor.getValue();
            return;
        }

        if (lastFactor >= factor.getValue()) {
            lastFactor = 1.0D;
        } else {
            lastFactor = Math.min(lastFactor + 1.0D, factor.getValue());
        }
    }

    private void applyLocalMovement(Vec3 movement) {
        if (!mode.is(Mode.SETBACK) && teleportId.get() == 0) {
            return;
        }

        if (zeroSpeed.getValue()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            return;
        }

        mc.player.setDeltaMovement(
                movement.x,
                zeroY.getValue() ? 0.0D : movement.y,
                movement.z
        );
    }

    private void updateZoomies() {
        zoomTimer++;
        if (zoomTimer > zoomer.getValue()) {
            zoomies = !zoomies;
            zoomTimer = 0;
        }
    }

    private void sendPackets(double x, double y, double z, boolean confirm) {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }

        Vec3 targetPosition = mc.player.position().add(x, y, z);
        Vec3 invalidPosition = type.getValue().createOutOfBounds(targetPosition, invalidOffset.getValue());
        sendMovePacket(new ServerboundMovePlayerPacket.Pos(
                targetPosition.x,
                targetPosition.y,
                targetPosition.z,
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));

        if (!mc.isLocalServer()) {
            for (int packetIndex = 0; packetIndex < invalidPackets.getValue(); packetIndex++) {
                sendMovePacket(new ServerboundMovePlayerPacket.Pos(
                        invalidPosition.x,
                        invalidPosition.y,
                        invalidPosition.z,
                        mc.player.onGround(),
                        mc.player.horizontalCollision
                ));
                invalidPosition = type.getValue().createOutOfBounds(invalidPosition, invalidOffset.getValue());
            }
        }

        if (confirm && (zeroTeleport.getValue() || teleportId.get() != 0)) {
            for (int packetIndex = 0; packetIndex < teleportPackets.getValue(); packetIndex++) {
                sendConfirmTeleport(targetPosition);
            }
        }

        if (elytra.getValue()) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
        }
    }

    private void sendConfirmTeleport(Vec3 position) {
        int nextTeleportId = teleportId.incrementAndGet();
        mc.player.connection.send(new ServerboundAcceptTeleportationPacket(nextTeleportId));
        pendingTeleports.put(nextTeleportId, new TimedPosition(position, System.currentTimeMillis()));
    }

    private void sendMovePacket(ServerboundMovePlayerPacket packet) {
        sentMovePackets.add(packet);
        mc.player.connection.send(packet);
    }

    private boolean isInsideBlock() {
        double collisionOffset = boundingBoxOffset.getValue() ? BOUNDING_BOX_OFFSET : 0.0D;
        return !mc.level.noBlockCollision(mc.player, mc.player.getBoundingBox().inflate(collisionOffset));
    }

    private boolean checkPackets(int interval) {
        if (++packetCounter >= interval) {
            packetCounter = 0;
            return true;
        }

        return false;
    }

    private void updateNoPhysics() {
        if (noPhysicsPlayer != mc.player) {
            restoreNoPhysics();
            noPhysicsPlayer = mc.player;
            originalNoPhysics = mc.player.noPhysics;
        }

        boolean shouldPhase = !phase.is(Phase.OFF) && (phase.is(Phase.SEMI) || isInsideBlock());
        mc.player.noPhysics = shouldPhase || originalNoPhysics;
    }

    private void restoreNoPhysics() {
        if (noPhysicsPlayer != null) {
            noPhysicsPlayer.noPhysics = originalNoPhysics;
        }

        noPhysicsPlayer = null;
        originalNoPhysics = false;
    }

    private void prunePendingTeleports() {
        long oldestAcceptedTime = System.currentTimeMillis() - PENDING_TELEPORT_MAX_AGE_MS;
        pendingTeleports.entrySet().removeIf(entry -> entry.getValue().createdAt < oldestAcceptedTime);
    }

    private void clearState() {
        pendingTeleports.clear();
        sentMovePackets.clear();
        teleportId.set(0);
        packetCounter = 0;
        zoomies = false;
        lastFactor = 1.0D;
        zoomTimer = 0;
    }

    public enum Mode {
        SETBACK("Setback"),
        FAST("Fast"),
        FACTOR("Factor"),
        SLOW("Slow"),
        INCREMENT("Increment"),
        COMPATIBILITY("Compatibility");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public boolean usesFactor() {
            return this == FACTOR || this == SLOW || this == INCREMENT;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum Phase {
        OFF("Off"),
        SEMI("Semi"),
        FULL("Full");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum Type {
        DOWN("Down") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(0.0D, -offset, 0.0D);
            }
        },
        UP("Up") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(0.0D, offset, 0.0D);
            }
        },
        PRESERVE("Preserve") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(randomCoordinate(), 0.0D, randomCoordinate());
            }
        },
        SWITCH("Switch") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(0.0D, ThreadLocalRandom.current().nextBoolean() ? -offset : offset, 0.0D);
            }
        },
        X("X") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(offset, 0.0D, 0.0D);
            }
        },
        Z("Z") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(0.0D, 0.0D, offset);
            }
        },
        XZ("XZ") {
            @Override
            public Vec3 createOutOfBounds(Vec3 position, int offset) {
                return position.add(offset, 0.0D, offset);
            }
        };

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        private static int randomCoordinate() {
            int coordinate = ThreadLocalRandom.current().nextInt(PRESERVE_COORDINATE_RANGE);
            return ThreadLocalRandom.current().nextBoolean() ? coordinate : -coordinate;
        }

        public abstract Vec3 createOutOfBounds(Vec3 position, int offset);

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class TimedPosition {
        private final Vec3 position;
        private final long createdAt;

        private TimedPosition(Vec3 position, long createdAt) {
            this.position = position;
            this.createdAt = createdAt;
        }

        private boolean matches(Vec3 other) {
            return position.x == other.x && position.y == other.y && position.z == other.z;
        }
    }
}
