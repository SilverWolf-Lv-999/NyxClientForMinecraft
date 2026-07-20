package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.TPUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.ValueGroup;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.tpbowgod.name", description = "nyxclient.module.tpbowgod.description", category = Category.COMBAT)
public class TPBowGod extends Module {
    public static final TPBowGod INSTANCE = new TPBowGod();

    private final ValueGroup generalGroup = ValueBuild.settingGroup("general", this);
    private final ValueGroup bypassGroup = ValueBuild.settingGroup("bypass", this);

    public final DoubleValue arrowTridentDistance = ValueBuild
            .doubleSetting("arrow trident distance", 20.0D, 0.0D, 128.0D, 1.0D, this)
            .group(generalGroup);
    public final DoubleValue pearlDistance = ValueBuild
            .doubleSetting("pearl wind distance", 20.0D, 0.0D, 128.0D, 1.0D, this)
            .group(generalGroup);
    private final EnumValue<BypassMode> bypassMode = ValueBuild
            .enumSetting("bypass mode", BypassMode.STEP_TP, this)
            .group(bypassGroup);
    private final DoubleValue stepSize = ValueBuild
            .doubleSetting("step size", 30.0D, 1.0D, 50.0D, 1.0D,
                    () -> !bypassMode.is(BypassMode.DIRECT), this)
            .group(bypassGroup);
    private final BoolValue sendBackImmediate = ValueBuild
            .boolSetting("send back immediate", true, this)
            .group(bypassGroup);

    private boolean modifyingPacket;
    public boolean inPacketMove;

    @Override
    public void onDisable() {
        modifyingPacket = false;
        inPacketMove = false;
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (modifyingPacket || mc.player == null || !TPUtility.allowSendP()) {
            return;
        }

        if (event.getPacket() instanceof ServerboundUseItemPacket packet) {
            Item item = packet.getHand() == InteractionHand.MAIN_HAND
                    ? mc.player.getMainHandItem().getItem()
                    : mc.player.getOffhandItem().getItem();
            if (!isPearlOrWind(item)) {
                return;
            }

            teleportAndSend(event, pearlDistance.getValue());
            return;
        }

        if (event.getPacket() instanceof ServerboundPlayerActionPacket packet
                && packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            Item item = mc.player.getMainHandItem().getItem();
            if (item == Items.BOW || item == Items.TRIDENT) {
                teleportAndSend(event, arrowTridentDistance.getValue());
            }
        }
    }

    private void teleportAndSend(PacketEvent.Send event, double distance) {
        Vec3 playerPos = mc.player.position();
        Vec3 targetPos = playerPos.add(mc.player.getViewVector(1.0F).normalize().scale(-distance));

        modifyingPacket = true;
        try {
            doBypassTp(playerPos, targetPos);
            mc.player.connection.send(event.getPacket());
            if (sendBackImmediate.getValue()) {
                doBypassTp(targetPos, playerPos);
            }
        } finally {
            modifyingPacket = false;
        }

        event.setCancelled(true);
    }

    private boolean isPearlOrWind(Item item) {
        return item == Items.ENDER_PEARL || item == Items.WIND_CHARGE;
    }

    private void doBypassTp(Vec3 from, Vec3 to) {
        switch (bypassMode.getValue()) {
            case DIRECT -> TPUtility.tp(from, to);
            case STEP_TP, SMOOTH_MOVE -> {
                double distance = from.distanceTo(to);
                int steps = (int) Math.ceil(distance / stepSize.getValue());
                for (int i = 1; i <= steps; i++) {
                    TPUtility.moveP(from.lerp(to, (double) i / steps));
                }
            }
        }
    }

    public enum BypassMode {
        DIRECT("\u76f4\u63a5\u4f20\u9001"),
        STEP_TP("\u5206\u6b65\u4f20\u9001"),
        SMOOTH_MOVE("\u5e73\u6ed1\u79fb\u52a8");

        private final String name;

        BypassMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
