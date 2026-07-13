package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.PlayerTickEvent;
import io.github.seraphina.nyx.client.events.impl.SendPositionEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

@ModuleInfo(name = "nyxclient.module.nofall.name", description = "nyxclient.module.nofall.description", category = Category.PLAYER)
public class NoFall extends Module {
    public static final NoFall INSTANCE = new NoFall();

    private static final double DELTA_Y = 9.0E-8D;
    private static final int VELOCITY_PROTECT_TICKS = 10;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.LAZY_MODE, this);
    public final DoubleValue safeDistance = ValueBuild.doubleSetting("safe distance", 0.0D, -3.0D, 8.0D, 0.1D, this);
    public final BoolValue disableWithElytra = ValueBuild.boolSetting("disable with elytra", true, this);
    public final BoolValue equipmentBypass = ValueBuild.boolSetting("equipment bypass", true, this);

    private double lastOnGroundHeight = Double.NEGATIVE_INFINITY;
    private double lastServerY;
    private int ticks;
    private int lastNoFallTick = -1000;
    private boolean sendingNoFallPacket;
    private boolean grimJumpQueued;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (isNull()) {
            return;
        }

        ticks++;
        updateFallReference();
        if (isBypassDisabled()) {
            return;
        }

        if (normalizedMode() == Mode.BYPASS_GRIM && isUnsafeFall() && !mc.player.onGround()) {
            sendPosition(mc.player.getY() + 1.0E-9D, false);
            markHandledFall();
            return;
        }

        if (grimJumpQueued) {
            grimJumpQueued = false;
            mc.options.keyJump.setDown(false);
            mc.player.setOnGround(false);
        }
    }

    @EventTarget
    public void onSendPosition(SendPositionEvent event) {
        if (isNull() || sendingNoFallPacket) {
            return;
        }

        if (event.getY() != lastServerY) {
            lastServerY = event.getY();
        }

        if (isBypassDisabled() || !isUnsafeFall()) {
            return;
        }

        switch (normalizedMode()) {
            case NO_BYPASS -> {
                event.setOnGround(true);
                markHandledFall();
            }
            case LAZY_MODE -> {
                event.setOnGround(false);
                sendPosition(lastServerY + DELTA_Y, false);
                markHandledFall();
            }
            case BYPASS_GRIM -> {
                event.setCancelled(true);
                mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                mc.player.setOnGround(true);
                grimJumpQueued = true;
                markHandledFall();
            }
            case LAZY_BYPASS_GRIM, LAZY_GRIM_PLUS, LAZY_GRIM_PLUS_2, TEST, TEST2 -> {
                event.setOnGround(false);
                sendPosition(mc.player.getY() + DELTA_Y, false);
                markHandledFall();
            }
            case PACKET, GRIM -> {
            }
        }
    }

    @EventTarget
    public void onPacketReceive(PacketEvent.Receive event) {
        if (isNull() || !normalizedMode().usesVelocityProtection()) {
            return;
        }

        if (ticks <= lastNoFallTick + VELOCITY_PROTECT_TICKS
                && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && packet.getId() == mc.player.getId()
                && packet.getMovement().y < 0.0D) {
            event.setCancelled(true);
        }
    }

    private void updateFallReference() {
        if (!mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava()) {
            if (lastOnGroundHeight == Double.NEGATIVE_INFINITY || mc.player.getY() > lastOnGroundHeight) {
                lastOnGroundHeight = mc.player.getY();
            }
        } else {
            lastOnGroundHeight = mc.player.getY();
            lastServerY = mc.player.getY();
        }
    }

    private boolean isUnsafeFall() {
        if (lastOnGroundHeight == Double.NEGATIVE_INFINITY) {
            return false;
        }

        double distance = Math.max(0.0D, mc.player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) + safeDistance.getValue());
        return mc.player.getY() <= lastOnGroundHeight - distance || mc.player.fallDistance >= distance;
    }

    private boolean isBypassDisabled() {
        if (mc.player.getAbilities().invulnerable || mc.player.isFallFlying()) {
            return true;
        }

        if (mc.player.getMainHandItem().is(Items.MACE)) {
            return true;
        }

        if (disableWithElytra.getValue()) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (mc.player.getItemBySlot(slot).is(Items.ELYTRA)) {
                    return true;
                }
            }
        }

        return equipmentBypass.getValue() && hasInvulnerableEquipment();
    }

    private boolean hasInvulnerableEquipment() {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
                if (path.matches("^SLIME.*_BOOTS$")) {
                    return true;
                }
            }
        }

        return false;
    }

    private Mode normalizedMode() {
        return switch (mode.getValue()) {
            case PACKET -> Mode.NO_BYPASS;
            case GRIM -> Mode.BYPASS_GRIM;
            default -> mode.getValue();
        };
    }

    private void sendPosition(double y, boolean onGround) {
        if (mc.player.connection == null) {
            return;
        }

        sendingNoFallPacket = true;
        try {
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    y,
                    mc.player.getZ(),
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    onGround,
                    mc.player.horizontalCollision
            ));
        } finally {
            sendingNoFallPacket = false;
        }
    }

    private void markHandledFall() {
        lastNoFallTick = ticks;
        lastOnGroundHeight = lastServerY;
        mc.player.resetFallDistance();
    }

    private void resetState() {
        lastOnGroundHeight = Double.NEGATIVE_INFINITY;
        lastServerY = 0.0D;
        ticks = 0;
        lastNoFallTick = -1000;
        sendingNoFallPacket = false;
        grimJumpQueued = false;
    }

    public enum Mode {
        NO_BYPASS("NoBypass"),
        LAZY_MODE("LazyMode"),
        BYPASS_GRIM("BypassGrim"),
        LAZY_BYPASS_GRIM("LazyBypassGrim"),
        LAZY_GRIM_PLUS("LazyGrimPlus"),
        LAZY_GRIM_PLUS_2("LazyGrimPlus2"),
        TEST("Test"),
        TEST2("Test2"),
        PACKET("Packet"),
        GRIM("Grim");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        public boolean usesVelocityProtection() {
            return this == LAZY_BYPASS_GRIM || this == LAZY_GRIM_PLUS || this == LAZY_GRIM_PLUS_2 || this == TEST || this == TEST2;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
