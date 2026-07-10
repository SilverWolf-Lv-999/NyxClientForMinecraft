package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("yRotLast")
    void nyx$setYRotLast(float yRotLast);

    @Accessor("xRotLast")
    void nyx$setXRotLast(float xRotLast);
}
