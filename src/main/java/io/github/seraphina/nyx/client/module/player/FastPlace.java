package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;

@ModuleInfo(name = "nyxclient.module.fastplace.name", description = "nyxclient.module.fastplace.description", category = Category.PLAYER)
public class FastPlace extends Module {
    public static final FastPlace INSTANCE = new FastPlace();

    public final IntValue delay = ValueBuild.intSetting("delay", 2, 0, 10, 1, this);

    public final BoolValue onlyBlocks  = ValueBuild.boolSetting("onlyBlocks", false, this);

    public FastPlace() {
    }

    @EventTarget
    public void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return;
        if (onlyBlocks.getValue() && !isHasBlock(mc.player)) return;
        mc.rightClickDelay = delay.getValue();
    }

    static boolean isHasBlock(LocalPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlockItem ||
                player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlockItem;
    }
}
