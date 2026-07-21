package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;

@ModuleInfo(name = "nyxclient.module.antieffects.name", description = "nyxclient.module.antieffects.description", category = Category.PLAYER)
public class AntiEffects extends Module {
    public static final AntiEffects INSTANCE = new AntiEffects();

    public final BoolValue levitation = ValueBuild.boolSetting("levitation", true, this);
    public final BoolValue slowFalling = ValueBuild.boolSetting("slow falling", true, this);
    public final BoolValue darkness = ValueBuild.boolSetting("darkness", true, this);
    public final BoolValue slowness = ValueBuild.boolSetting("slowness", true, this);

    @Override
    public void onEnable() {
        removeSelectedEffects();
    }

    @EventTarget
    public void onTick(TickEvent.Pre event) {
        removeSelectedEffects();
    }

    private void removeSelectedEffects() {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (darkness.getValue()) {
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (slowness.getValue()) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
    }
}
