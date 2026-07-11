package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Invoker("renderSlot")
    void nyx$renderSlot(GuiGraphics graphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed);
}
