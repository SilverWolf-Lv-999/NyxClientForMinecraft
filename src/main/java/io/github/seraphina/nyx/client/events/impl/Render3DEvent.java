package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import org.joml.Matrix4f;

public class Render3DEvent implements Event {

    @Getter
    private final PoseStack poseStack;
    private final Matrix4f projectionMatrix;
    @Getter
    private final float partialTick;

    public Render3DEvent(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick) {
        this.poseStack = poseStack;
        this.projectionMatrix = new Matrix4f(projectionMatrix);
        this.partialTick = partialTick;
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

}
