package io.github.seraphina.nyx.client.mixins;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> nyx$getChildren();

    @Accessor("cubes")
    List<ModelPart.Cube> nyx$getCubes();
}
