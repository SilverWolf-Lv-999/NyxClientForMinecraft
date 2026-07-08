package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(name = "nyxclient.module.useclick.name", description = "nyxclient.module.useclick.description", category = Category.COMBAT)
public class UseClick extends Module {
    public static final UseClick INSTANCE = new UseClick();

    public boolean canClickWhileUsing(LocalPlayer player) {
        return isEnabled() && player != null && player.isUsingItem();
    }

    public boolean shouldProcessClicksWhileUsing(LocalPlayer player, boolean useKeyDown) {
        return canClickWhileUsing(player) && useKeyDown;
    }
}
