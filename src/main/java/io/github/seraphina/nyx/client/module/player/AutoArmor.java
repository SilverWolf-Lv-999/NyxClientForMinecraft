package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.module.movement.ElytraFly;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

@ModuleInfo(name = "nyxclient.module.autoarmor.name", description = "nyxclient.module.autoarmor.description", category = Category.PLAYER)
public final class AutoArmor extends Module {
    public static final AutoArmor INSTANCE = new AutoArmor();

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    public final BoolValue noMove = ValueBuild.boolSetting("no move", false, this);
    public final IntValue delay = ValueBuild.intSetting("delay", 3, 0, 10, 1, this);
    public final BoolValue autoElytra = ValueBuild.boolSetting("auto elytra", true, this);
    public final BoolValue snowBug = ValueBuild.boolSetting("snow bug", true, this);

    private int tickDelay;

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (!canRun() || (noMove.getValue() && MovingUtility.isMoving())) {
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }
        tickDelay = delay.getValue();

        for (EquipmentSlot equipmentSlot : ARMOR_SLOTS) {
            int candidateSlot = findBestInventorySlot(equipmentSlot);
            if (candidateSlot == InventoryUtility.NOT_FOUND) {
                continue;
            }

            int armorSlot = InventoryUtility.equipmentSlotToInventorySlot(equipmentSlot);
            if (InventoryUtility.swapInventorySlots(candidateSlot, armorSlot)) {
                return;
            }
        }
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && !mc.player.isSpectator()
                && mc.player.containerMenu == mc.player.inventoryMenu
                && !InventoryUtility.hasCarriedStack();
    }

    private int findBestInventorySlot(EquipmentSlot equipmentSlot) {
        int equippedSlot = InventoryUtility.equipmentSlotToInventorySlot(equipmentSlot);
        ItemStack equippedStack = InventoryUtility.getStack(equippedSlot);

        if (equipmentSlot == EquipmentSlot.CHEST && autoElytra.getValue() && ElytraFly.INSTANCE.isEnabled()) {
            if (isUsableElytra(equippedStack)) {
                return InventoryUtility.NOT_FOUND;
            }

            return InventoryUtility.findInventorySlot(this::isUsableElytra);
        }

        if (equipmentSlot == EquipmentSlot.FEET
                && snowBug.getValue()
                && mc.player.hurtTime > 1
                && !equippedStack.is(Items.LEATHER_BOOTS)) {
            int leatherBootSlot = InventoryUtility.findInventorySlot(stack -> stack.is(Items.LEATHER_BOOTS));
            if (leatherBootSlot != InventoryUtility.NOT_FOUND) {
                return leatherBootSlot;
            }
        }

        double bestArmor = getArmorValue(equippedStack, equipmentSlot);
        int bestSlot = InventoryUtility.NOT_FOUND;
        for (int inventorySlot = InventoryUtility.HOTBAR_START; inventorySlot < InventoryUtility.MAIN_INVENTORY_END; inventorySlot++) {
            ItemStack stack = InventoryUtility.getStack(inventorySlot);
            if (!isArmorForSlot(stack, equipmentSlot)) {
                continue;
            }

            double armor = getArmorValue(stack, equipmentSlot);
            if (armor > bestArmor) {
                bestArmor = armor;
                bestSlot = inventorySlot;
            }
        }

        return bestSlot;
    }

    private boolean isUsableElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA) && !stack.nextDamageWillBreak();
    }

    private boolean isArmorForSlot(ItemStack stack, EquipmentSlot equipmentSlot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null
                && equippable.slot() == equipmentSlot
                && getArmorValue(stack, equipmentSlot) > 0.0D;
    }

    private double getArmorValue(ItemStack stack, EquipmentSlot equipmentSlot) {
        if (stack.isEmpty()) {
            return -1.0D;
        }

        double[] armorValue = {0.0D};
        stack.forEachModifier(equipmentSlot, (attribute, modifier) -> {
            if (attribute == Attributes.ARMOR) {
                armorValue[0] += modifier.amount();
            }
        });

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var enchantment : enchantments.entrySet()) {
            if (enchantment.getKey().is(Enchantments.PROTECTION)) {
                armorValue[0] += enchantment.getIntValue();
            }
        }

        return armorValue[0];
    }
}
