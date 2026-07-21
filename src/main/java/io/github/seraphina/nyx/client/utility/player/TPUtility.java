package io.github.seraphina.nyx.client.utility.player;

import io.github.seraphina.nyx.client.module.player.AntiLag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Position-packet teleport helpers adapted from Mirror's TPUtils.
 */
public final class TPUtility {
    private TPUtility() {
    }

    public static boolean allowSendP() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static double getMoveD() {
        double moveDistance = AntiLag.INSTANCE.moveDistance.getValue();
        return moveDistance > 0.0D ? moveDistance : Double.MAX_VALUE;
    }

    public static void tp(Vec3 from, Vec3 to, float yaw, float pitch) {
        int steps = getSteps(from, to, getMoveD());
        for (int i = 0; i < steps; i++) {
            moveP();
        }
        moveP(to, yaw, pitch);
    }

    public static void tp(Vec3 from, Vec3 to) {
        int steps = getSteps(from, to, getMoveD());
        for (int i = 0; i < steps; i++) {
            moveP();
        }
        moveP(to);
    }

    public static void doTp(Vec3 from, Vec3 to, float yaw, float pitch) {
        doTp(from, to, yaw, pitch, getMoveD());
    }

    public static void doTp(Vec3 from, Vec3 to) {
        doTp(from, to, getMoveD());
    }

    public static void doTp(Vec3 from, Vec3 to, float yaw, float pitch, double moveDistance) {
        int steps = getSteps(from, to, moveDistance);
        for (int i = 0; i < steps; i++) {
            moveP(from);
        }
        moveP(to, yaw, pitch);
    }

    public static void doTp(Vec3 from, Vec3 to, double moveDistance) {
        int steps = getSteps(from, to, moveDistance);
        for (int i = 0; i < steps; i++) {
            moveP(from);
        }
        moveP(to);
    }

    public static void moveP(double x, double y, double z) {
        if (!allowSendP()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(
                new ServerboundMovePlayerPacket.Pos(x, y, z, false, false)
        );
    }

    public static void moveP(float yaw, float pitch) {
        if (!allowSendP()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, false)
        );
    }

    public static void moveP(double x, double y, double z, float yaw, float pitch) {
        if (!allowSendP()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(
                new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, false, false)
        );
    }

    public static void moveP() {
        if (!allowSendP()) {
            return;
        }
        Minecraft.getInstance().getConnection().send(
                new ServerboundMovePlayerPacket.StatusOnly(false, false)
        );
    }

    public static void moveP(Vec3 position) {
        moveP(position.x, position.y, position.z);
    }

    public static void moveP(Vec3 position, float yaw, float pitch) {
        moveP(position.x, position.y, position.z, yaw, pitch);
    }

    private static int getSteps(Vec3 from, Vec3 to, double moveDistance) {
        double effectiveMoveDistance = moveDistance > 0.0D ? moveDistance : Double.MAX_VALUE;
        return (int) Math.ceil(from.distanceTo(to) / effectiveMoveDistance);
    }
}
