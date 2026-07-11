package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

public class Render3DEvent implements Event {

    private final PoseStack poseStack;
    private final Matrix4f projectionMatrix;

    public Render3DEvent(PoseStack poseStack, Matrix4f projectionMatrix) {
        this.poseStack = poseStack;
        this.projectionMatrix = new Matrix4f(projectionMatrix);
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

}
