package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "nyxclient.module.macespoof.name", description = "nyxclient.module.macespoof.description", category = Category.COMBAT)
public final class MaceSpoof extends Module {
    public static final MaceSpoof INSTANCE = new MaceSpoof();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.VANILLA, this);
    public final BoolValue noCrystal = ValueBuild.boolSetting("no crystal", true, () -> mode.is(Mode.SWAP), this);
    public final BoolValue inventorySwap = ValueBuild.boolSetting("inventory swap", false, () -> mode.is(Mode.SWAP), this);
    public final BoolValue onlyGround = ValueBuild.boolSetting("only ground", true, () -> mode.is(Mode.VANILLA), this);
    public final DoubleValue height = ValueBuild.doubleSetting(
            "height",
            25.0D,
            0.0D,
            2000.0D,
            0.1D,
            () -> mode.is(Mode.VANILLA),
            this
    );

    private boolean spoofing;

    private MaceSpoof() {
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null || spoofing) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (mode.is(Mode.NCP) && packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.isOnGround()) {
            event.setPacket(withoutGround(movePacket));
            return;
        }

        if (!(packet instanceof ServerboundInteractPacket interactPacket) || !isAttack(interactPacket)) {
            return;
        }

        if (mode.is(Mode.VANILLA)) {
            spoofVanillaAttack();
        } else if (mode.is(Mode.SWAP)) {
            spoofSwapAttack(event, interactPacket);
        }
    }

    private void spoofVanillaAttack() {
        if (!mc.player.getMainHandItem().is(Items.MACE)
                || isCrystalAttack()
                || onlyGround.getValue() && !mc.player.onGround() && !mc.player.getAbilities().flying
                || mc.player.isInWater()
                || mc.player.isInLava()) {
            return;
        }

        Vec3 position = mc.player.position();
        sendPosition(position.x, position.y, position.z);
        sendPosition(position.x, position.y + height.getValue(), position.z);
        sendPosition(position.x, position.y, position.z);
    }

    private void spoofSwapAttack(PacketEvent.Send event, ServerboundInteractPacket interactPacket) {
        if (event.isCancelled() || noCrystal.getValue() && isCrystalAttack()) {
            return;
        }

        int originalSlot = InventoryUtility.getSelectedHotbarSlot();
        MaceSelection selection = selectMace(originalSlot);
        if (selection == null) {
            return;
        }

        spoofing = true;
        try {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(selection.hotbarSlot()));
            mc.player.connection.send(interactPacket);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
            event.setCancelled(true);
        } finally {
            spoofing = false;
            restoreInventorySwap(selection);
        }
    }

    private MaceSelection selectMace(int originalSlot) {
        if (!Inventory.isHotbarSlot(originalSlot)) {
            return null;
        }

        int hotbarSlot = InventoryUtility.findHotbarSlot(Items.MACE);
        if (Inventory.isHotbarSlot(hotbarSlot)) {
            return new MaceSelection(hotbarSlot, InventoryUtility.NOT_FOUND);
        }

        if (!inventorySwap.getValue() || InventoryUtility.hasCarriedStack()) {
            return null;
        }

        int inventorySlot = InventoryUtility.findInventorySlot(Items.MACE);
        if (!InventoryUtility.isMainInventorySlot(inventorySlot)
                || !InventoryUtility.moveInventorySlotToHotbar(inventorySlot, originalSlot)) {
            return null;
        }

        return new MaceSelection(originalSlot, inventorySlot);
    }

    private void restoreInventorySwap(MaceSelection selection) {
        if (selection.inventorySlot() != InventoryUtility.NOT_FOUND) {
            InventoryUtility.moveInventorySlotToHotbar(selection.inventorySlot(), selection.hotbarSlot());
        }
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

    private void sendPosition(double x, double y, double z) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x,
                y,
                z,
                false,
                mc.player.horizontalCollision
        ));
    }

    private record MaceSelection(int hotbarSlot, int inventorySlot) {
    }

    public enum Mode {
        VANILLA,
        NCP,
        SWAP
    }
}
