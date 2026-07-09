package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.AttackEntityEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(name = "nyxclient.module.instantswitch.name", description = "nyxclient.module.instantswitch.description", category = Category.PLAYER)
public class InstantSwitch extends Module {
    public static final InstantSwitch INSTANCE = new InstantSwitch();

    private static final int SWITCH_DELAY_TICKS = 1;

    public final BoolValue maceToSword = ValueBuild.boolSetting("mace to sword", true, this);

    public final BoolValue maceToAxe = ValueBuild.boolSetting("mace to axe", true, this);

    private PendingSwitch pendingSwitch;

    @Override
    public void onDisable() {
        pendingSwitch = null;
    }

    @EventTarget(4)
    public void onAttack(AttackEntityEvent event) {
        if (event.getPlayer() != mc.player) {
            return;
        }

        pendingSwitch = null;
        if (!canRun()
                || !(event.getEntity() instanceof LivingEntity target)
                || !InventoryUtility.getMainHandStack().is(Items.MACE)) {
            return;
        }

        TagKey<Item> weaponTag = getSwitchWeaponTag(target);
        if (weaponTag != null) {
            pendingSwitch = new PendingSwitch(weaponTag, SWITCH_DELAY_TICKS);
        }
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (pendingSwitch == null) {
            return;
        }

        if (!canRun()) {
            pendingSwitch = null;
            return;
        }

        if (pendingSwitch.delayTicks > 0) {
            pendingSwitch.delayTicks--;
            if (pendingSwitch.delayTicks > 0) {
                return;
            }
        }

        executeSwitch(pendingSwitch.weaponTag);
        pendingSwitch = null;
    }

    private TagKey<Item> getSwitchWeaponTag(LivingEntity target) {
        if (maceToAxe.getValue() && isUsingShield(target)) {
            return ItemTags.AXES;
        }

        return maceToSword.getValue() ? ItemTags.SWORDS : null;
    }

    private boolean isUsingShield(LivingEntity target) {
        ItemStack blockingStack = target.getItemBlockingWith();
        return (blockingStack != null && blockingStack.is(Items.SHIELD))
                || (target.isUsingItem() && target.getUseItem().is(Items.SHIELD));
    }

    private void executeSwitch(TagKey<Item> weaponTag) {
        int weaponSlot = findBestWeaponSlot(weaponTag);
        if (weaponSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        if (Inventory.isHotbarSlot(weaponSlot)) {
            InventoryUtility.selectHotbarSlot(weaponSlot);
            return;
        }

        if (!InventoryUtility.isMainInventorySlot(weaponSlot)) {
            return;
        }

        int hotbarSlot = findHotbarTargetSlot();
        if (!Inventory.isHotbarSlot(hotbarSlot)) {
            return;
        }

        if (InventoryUtility.moveInventorySlotToHotbar(weaponSlot, hotbarSlot)) {
            InventoryUtility.selectHotbarSlot(hotbarSlot);
        }
    }

    private int findBestWeaponSlot(TagKey<Item> weaponTag) {
        Inventory inventory = InventoryUtility.inventory();
        if (inventory == null) {
            return InventoryUtility.NOT_FOUND;
        }

        int bestSlot = InventoryUtility.NOT_FOUND;
        double bestDamage = Double.NEGATIVE_INFINITY;

        for (int slot = InventoryUtility.HOTBAR_START; slot < InventoryUtility.MAIN_INVENTORY_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!canSwitchToWeapon(stack, weaponTag)) {
                continue;
            }

            double damage = getAttackDamage(stack);
            if (bestSlot == InventoryUtility.NOT_FOUND
                    || damage > bestDamage
                    || (damage == bestDamage && isPreferredSlot(slot, bestSlot))) {
                bestSlot = slot;
                bestDamage = damage;
            }
        }

        return bestSlot;
    }

    private boolean canSwitchToWeapon(ItemStack stack, TagKey<Item> weaponTag) {
        return !stack.isEmpty()
                && stack.is(weaponTag)
                && stack.isItemEnabled(mc.level.enabledFeatures());
    }

    private double getAttackDamage(ItemStack stack) {
        double baseDamage = mc.player == null ? 1.0D : mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double[] addValue = {0.0D};
        double[] addMultipliedBase = {0.0D};
        double[] totalMultiplier = {1.0D};

        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() != Attributes.ATTACK_DAMAGE.value()) {
                return;
            }

            switch (modifier.operation()) {
                case ADD_VALUE -> addValue[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> addMultipliedBase[0] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier[0] *= 1.0D + modifier.amount();
            }
        });

        return (baseDamage + addValue[0] + baseDamage * addMultipliedBase[0]) * totalMultiplier[0];
    }

    private boolean isPreferredSlot(int slot, int bestSlot) {
        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (slot == selectedSlot) {
            return true;
        }

        if (bestSlot == selectedSlot) {
            return false;
        }

        boolean slotHotbar = Inventory.isHotbarSlot(slot);
        boolean bestSlotHotbar = Inventory.isHotbarSlot(bestSlot);
        if (slotHotbar != bestSlotHotbar) {
            return slotHotbar;
        }

        return slot < bestSlot;
    }

    private int findHotbarTargetSlot() {
        int emptySlot = InventoryUtility.findEmptyHotbarSlot();
        if (Inventory.isHotbarSlot(emptySlot)) {
            return emptySlot;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (Inventory.isHotbarSlot(selectedSlot)) {
            return selectedSlot;
        }

        return InventoryUtility.HOTBAR_START;
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && mc.screen == null
                && !mc.player.isSpectator();
    }

    private static final class PendingSwitch {
        private final TagKey<Item> weaponTag;
        private int delayTicks;

        private PendingSwitch(TagKey<Item> weaponTag, int delayTicks) {
            this.weaponTag = weaponTag;
            this.delayTicks = delayTicks;
        }
    }
}
