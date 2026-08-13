package io.github.seraphina.nyx.client.utility;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemSpoofVisual {
    private static final Map<Object, ItemStack> MAIN_HAND_ITEMS = new LinkedHashMap<>();

    public static void enable(Object owner, ItemStack stack) {
        if (owner != null && stack != null) {
            MAIN_HAND_ITEMS.putIfAbsent(owner, stack.copy());
        }
    }

    public static void clear(Object owner) {
        MAIN_HAND_ITEMS.remove(owner);
    }

    public static boolean isEnabled() {
        return !MAIN_HAND_ITEMS.isEmpty();
    }

    public static ItemStack getMainHandItem() {
        ItemStack item = ItemStack.EMPTY;
        for (ItemStack stack : MAIN_HAND_ITEMS.values()) {
            item = stack;
        }
        return item;
    }
}
