package io.github.seraphina.nyxclient.events.impl;

import io.github.seraphina.nyxclient.events.api.events.Event;

import com.mojang.blaze3d.vertex.PoseStack;

public class Render3DEvent implements Event {

    private final PoseStack poseStack;

    public Render3DEvent(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

}
