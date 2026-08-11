package io.github.seraphina.nyx.client.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Setter
@Getter
public class RenderItemInHandEvent extends EventCancellable {
    private LivingEntity entity;
    private ItemStack stack;
    private ItemDisplayContext displayContext;
    private PoseStack poseStack;
    private SubmitNodeCollector nodeCollector;
    private int packedLight;
    private float scale = 1.0f;
    private double xPos = 0.0;
    private double yPos = 0.0;
    private double zPos = 0.0;
    private double xRot = 0.0;
    private double yRot = 0.0;
    private double zRot = 0.0;

    public RenderItemInHandEvent(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight) {
        this.entity = entity;
        this.stack = stack;
        this.displayContext = displayContext;
        this.poseStack = poseStack;
        this.nodeCollector = nodeCollector;
        this.packedLight = packedLight;
    }

}
