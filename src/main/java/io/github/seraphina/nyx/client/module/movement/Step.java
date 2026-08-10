package io.github.seraphina.nyx.client.module.movement;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.combat.Surround;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

@ModuleInfo(name = "nyxclient.module.step.name", description = "nyxclient.module.step.description", category = Category.MOVEMENT)
public final class Step extends Module {
    public static final Step INSTANCE = new Step();

    private static final double MIN_PACKET_STEP_HEIGHT = 0.75D;
    private static final double STEP_HEIGHT_EPSILON = 1.0E-3D;

    private LocalPlayer playerWithOriginalStepHeight;
    private double originalStepHeight;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private double lastPlayerZ = Double.NaN;

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.VANILLA, this);
    public final DoubleValue height = ValueBuild.doubleSetting("height", 1.0D, 0.6D, 2.5D, 0.25D, this);
    public final BoolValue onlyMoving = ValueBuild.boolSetting("only moving", true, this);
    public final BoolValue sneakingPause = ValueBuild.boolSetting("sneaking pause", true, this);
    public final BoolValue fluidPause = ValueBuild.boolSetting("fluid pause", true, this);
    public final BoolValue webPause = ValueBuild.boolSetting("web pause", true, this);
    public final BoolValue surroundPause = ValueBuild.boolSetting("surround pause", true, this);

    @Override
    public void onEnable() {
        resetLastPosition();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null || mc.level == null) {
            resetLastPosition();
            return;
        }

        LocalPlayer player = mc.player;
        AttributeInstance stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight == null) {
            rememberPlayerPosition(player);
            return;
        }

        if (playerWithOriginalStepHeight != player) {
            playerWithOriginalStepHeight = player;
            originalStepHeight = stepHeight.getBaseValue();
            resetLastPosition();
        }

        boolean canStep = canStep(player);
        double targetStepHeight = canStep ? height.getValue() : originalStepHeight;
        if (Double.compare(stepHeight.getBaseValue(), targetStepHeight) != 0) {
            stepHeight.setBaseValue(targetStepHeight);
        }

        if (canStep) {
            sendStepPackets(player);
        }
        rememberPlayerPosition(player);
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.player == playerWithOriginalStepHeight) {
            AttributeInstance stepHeight = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (stepHeight != null) {
                stepHeight.setBaseValue(originalStepHeight);
            }
        }

        playerWithOriginalStepHeight = null;
        resetLastPosition();
    }

    private boolean canStep(LocalPlayer player) {
        return player.onGround()
                && !player.getAbilities().flying
                && !player.isSpectator()
                && !player.isPassenger()
                && !player.isFallFlying()
                && (!onlyMoving.getValue() || MovingUtility.isMoving())
                && (!sneakingPause.getValue() || !player.isShiftKeyDown())
                && (!fluidPause.getValue() || (!player.isInWater() && !player.isInLava()))
                && (!webPause.getValue() || !isInsideCobweb(player))
                && (!surroundPause.getValue() || !Surround.INSTANCE.isEnabled());
    }

    private void sendStepPackets(LocalPlayer player) {
        if (mode.is(Mode.VANILLA)
                || player.connection == null
                || Double.isNaN(lastPlayerY)) {
            return;
        }

        double steppedHeight = player.getY() - lastPlayerY;
        if (steppedHeight <= MIN_PACKET_STEP_HEIGHT
                || steppedHeight > height.getValue() + STEP_HEIGHT_EPSILON) {
            return;
        }

        double[] offsets = getStepOffsets(steppedHeight);
        if (offsets == null) {
            return;
        }

        for (double offset : offsets) {
            player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    lastPlayerX,
                    lastPlayerY + offset,
                    lastPlayerZ,
                    false,
                    false
            ));
        }
    }

    private double[] getStepOffsets(double steppedHeight) {
        boolean strict = mode.is(Mode.NCP);
        if (isStepHeight(steppedHeight, 0.75D)) {
            return strict ? new double[]{0.42D, 0.753D, 0.75D} : new double[]{0.42D, 0.753D};
        }
        if (isStepHeight(steppedHeight, 0.8125D)) {
            return strict ? new double[]{0.39D, 0.7D, 0.8125D} : new double[]{0.39D, 0.7D};
        }
        if (isStepHeight(steppedHeight, 0.875D)) {
            return strict ? new double[]{0.39D, 0.7D, 0.875D} : new double[]{0.39D, 0.7D};
        }
        if (isStepHeight(steppedHeight, 1.0D)) {
            return strict ? new double[]{0.42D, 0.753D, 1.0D} : new double[]{0.42D, 0.753D};
        }
        if (isStepHeight(steppedHeight, 1.5D)) {
            return new double[]{0.42D, 0.75D, 1.0D, 1.16D, 1.23D, 1.2D};
        }
        if (isStepHeight(steppedHeight, 2.0D)) {
            return new double[]{0.42D, 0.78D, 0.63D, 0.51D, 0.9D, 1.21D, 1.45D, 1.43D};
        }
        if (isStepHeight(steppedHeight, 2.5D)) {
            return new double[]{0.425D, 0.821D, 0.699D, 0.599D, 1.022D, 1.372D, 1.652D, 1.869D, 2.019D, 1.907D};
        }
        return null;
    }

    private boolean isStepHeight(double value, double expected) {
        return Math.abs(value - expected) <= STEP_HEIGHT_EPSILON;
    }

    private boolean isInsideCobweb(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(Math.nextDown(box.maxX));
        int maxY = Mth.floor(Math.nextDown(box.maxY));
        int maxZ = Mth.floor(Math.nextDown(box.maxZ));
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    position.set(x, y, z);
                    if (mc.level.getBlockState(position).is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void rememberPlayerPosition(LocalPlayer player) {
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
        lastPlayerZ = player.getZ();
    }

    private void resetLastPosition() {
        lastPlayerX = Double.NaN;
        lastPlayerY = Double.NaN;
        lastPlayerZ = Double.NaN;
    }

    public enum Mode {
        VANILLA("Vanilla"),
        OLD_NCP("OldNCP"),
        NCP("NCP");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
