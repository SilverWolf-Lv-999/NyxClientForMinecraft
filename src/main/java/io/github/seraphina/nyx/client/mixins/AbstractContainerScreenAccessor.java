package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int nyx$getLeftPos();

    @Accessor("topPos")
    int nyx$getTopPos();

    @Accessor("imageWidth")
    int nyx$getImageWidth();
}
