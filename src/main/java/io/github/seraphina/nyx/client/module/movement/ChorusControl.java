package io.github.seraphina.nyx.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.LevelUpdateEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.Render3DEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.Render3DUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.ColorValue;
import io.github.seraphina.nyx.client.value.impl.KeyBindValue;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

@ModuleInfo(name = "nyxclient.module.choruscontrol.name", description = "nyxclient.module.choruscontrol.description", category = Category.MOVEMENT)
public class ChorusControl extends Module {
    public static final ChorusControl INSTANCE = new ChorusControl();

    private static final int CHORUS_GRACE_TICKS = 5;
    private static final double MARKER_HALF_WIDTH = 0.3D;
    private static final double MARKER_HEIGHT = 1.85D;
    private static final int MOUSE_BIND_OFFSET = -2;

    public final KeyBindValue confirmKey = ValueBuild.keybindSetting("confirm key", GLFW.GLFW_KEY_UNKNOWN, this);
    public final ColorValue color = ValueBuild.colorSetting("color", new Color(255, 255, 255, 100), this);

    private volatile ClientboundPlayerPositionPacket savedPacket;
    private volatile Vec3 savedPosition;
    private int chorusTicks;

    @Override
    public void onEnable() {
        clearSavedTeleport();
        chorusTicks = 0;
    }

    @Override
    public void onDisable() {
        applySavedTeleport();
        chorusTicks = 0;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()) {
            clearSavedTeleport();
            chorusTicks = 0;
            return;
        }

        if (mc.player.isUsingItem() && mc.player.getUseItem().is(Items.CHORUS_FRUIT)) {
            chorusTicks = CHORUS_GRACE_TICKS;
        } else if (chorusTicks > 0) {
            chorusTicks--;
        }

        if (isConfirmKeyDown()) {
            applySavedTeleport();
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)
                || mc.player == null
                || (!isEatingChorus() && savedPacket == null)) {
            return;
        }

        savedPacket = packet;
        savedPosition = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(mc.player),
                packet.change(),
                packet.relatives()
        ).position();
        event.setCancelled(true);
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (savedPacket != null && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSendPosition(SendPositionEvent event) {
        if (savedPacket != null) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (savedPacket == null) {
            return;
        }

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Vec3 position = savedPosition;
        if (position == null) {
            return;
        }

        AABB box = new AABB(
                position.x - MARKER_HALF_WIDTH,
                position.y,
                position.z - MARKER_HALF_WIDTH,
                position.x + MARKER_HALF_WIDTH,
                position.y + MARKER_HEIGHT,
                position.z + MARKER_HALF_WIDTH
        );
        PoseStack poseStack = event.getPoseStack();
        Render3DUtility.renderFilledBoxNoDepth(poseStack, box, color.getValue());
    }

    @EventTarget
    public void onLevelUpdate(LevelUpdateEvent event) {
        clearSavedTeleport();
        chorusTicks = 0;
    }

    private boolean isEatingChorus() {
        return chorusTicks > 0;
    }

    private boolean isConfirmKeyDown() {
        int key = confirmKey.getValue();
        if (key == GLFW.GLFW_KEY_UNKNOWN || mc.getWindow() == null) {
            return false;
        }

        if (key <= MOUSE_BIND_OFFSET) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), MOUSE_BIND_OFFSET - key) == GLFW.GLFW_PRESS;
        }

        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    private void applySavedTeleport() {
        ClientboundPlayerPositionPacket packet = savedPacket;
        clearSavedTeleport();

        if (packet != null && mc.getConnection() != null) {
            packet.handle(mc.getConnection());
        }
    }

    private void clearSavedTeleport() {
        savedPacket = null;
        savedPosition = null;
    }
}
