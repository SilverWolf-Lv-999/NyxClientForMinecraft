package io.github.seraphina.nyx.client.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.seraphina.nyx.client.events.api.events.callables.EventCancellable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

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

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public void setPoseStack(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public void setPackedLight(int packedLight) {
        this.packedLight = packedLight;
    }

    public ItemDisplayContext getDisplayContext() {
        return displayContext;
    }

    public void setDisplayContext(ItemDisplayContext displayContext) {
        this.displayContext = displayContext;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    public SubmitNodeCollector getNodeCollector() {
        return nodeCollector;
    }

    public void setNodeCollector(SubmitNodeCollector nodeCollector) {
        this.nodeCollector = nodeCollector;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getScale() {
        return scale;
    }

    public void setXPos(double xPos) {
        this.xPos = xPos;
    }

    public double getXPos() {
        return xPos;
    }

    public void setYPos(double yPos) {
        this.yPos = yPos;
    }

    public double getYPos() {
        return yPos;
    }

    public void setZPos(double zPos) {
        this.zPos = zPos;
    }

    public double getZPos() {
        return zPos;
    }

    public void setXRot(double xRot) {
        this.xRot = xRot;
    }

    public double getXRot() {
        return xRot;
    }

    public void setYRot(double yRot) {
        this.yRot = yRot;
    }

    public double getYRot() {
        return yRot;
    }

    public void setZRot(double zRot) {
        this.zRot = zRot;
    }

    public double getZRot() {
        return zRot;
    }
}
