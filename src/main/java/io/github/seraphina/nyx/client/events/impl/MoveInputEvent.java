package io.github.seraphina.nyx.client.events.impl;

import io.github.seraphina.nyx.client.events.api.events.Event;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.player.Input;

@Setter
@Getter
public class MoveInputEvent implements Event {

    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public MoveInputEvent(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        float f = forward == backward ? 0.0F : (forward ? 1.0F : -1.0F);
        float g = left == right ? 0.0F : (left ? 1.0F : -1.0F);
        this.forward = f;
        this.strafe = g;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    public Input toNewInput() {
        return new Input(
                this.forward > 0,
                this.forward < 0,
                this.strafe > 0,
                this.strafe < 0,
                this.jump,
                this.sneak,
                this.sprint
        );
    }

}
