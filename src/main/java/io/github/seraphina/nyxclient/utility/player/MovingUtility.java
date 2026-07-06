package io.github.seraphina.nyxclient.utility.player;

import io.github.seraphina.nyxclient.utility.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class MovingUtility implements IMinecraft {
    public static boolean isMoving() {
        if (mc.player == null || mc.level == null) return false;
        return mc.player.xxa != 0.0F || mc.player.zza != 0.0F;
    }

    public static double[] predictMovement() {
        double strafeInput = strafeVal() * 0.98f;
        double forwardInput = forwardVal() * 0.98f;
        double inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude >= 1.0E-4f) {
            inputMagnitude = Math.sqrt(inputMagnitude);
            if (inputMagnitude < 1.0f) {
                inputMagnitude = 1.0f;
            }
            inputMagnitude = horizontalClamp() / inputMagnitude;
            float sinYaw = Mth.sin(mc.player.getYRot() * (float) Math.PI / 180.0f);
            float cosYaw = Mth.cos(mc.player.getYRot() * (float) Math.PI / 180.0f);
            strafeInput *= inputMagnitude;
            forwardInput *= inputMagnitude;
            return new double[]{strafeInput * cosYaw - forwardInput * sinYaw, forwardInput * cosYaw + strafeInput * sinYaw};
        }
        return new double[]{0.0, 0.0};
    }

    public static float horizontalClamp() {
        float slipperiness = mc.level.getBlockState(new BlockPos(Mth.floor(mc.player.getX()), Mth.floor(mc.player.getY()) - 1, Mth.floor(mc.player.getZ()))).getBlock().getFriction() * 0.91f;
        return mc.player.getSpeed() * (0.16277136f / (slipperiness * slipperiness * slipperiness));
    }

    public static double[] strafe(float speed) {
        double[] strafes = predictMovement();
        double x = speed * strafes[0];
        double y = speed * strafes[1];
        mc.player.setDeltaMovement(x, mc.player.getDeltaMovement().y, y);
        return new double[]{x, y};
    }

    public static int forwardVal() {
        int forwardValue = 0;
        if (mc.options.keyUp.isDown()) {
            ++forwardValue;
        }
        if (mc.options.keyDown.isDown()) {
            --forwardValue;
        }
        return forwardValue;
    }

    public static int strafeVal() {
        int leftValue = 0;
        if (mc.options.keyLeft.isDown()) {
            ++leftValue;
        }
        if (mc.options.keyRight.isDown()) {
            --leftValue;
        }
        return leftValue;
    }

    public static boolean canMove(double x, double z, double y) {
        AABB boundingBox = mc.player.getBoundingBox().move(x, y, z);
        return mc.level.noBlockCollision(mc.player, boundingBox);
    }
}
