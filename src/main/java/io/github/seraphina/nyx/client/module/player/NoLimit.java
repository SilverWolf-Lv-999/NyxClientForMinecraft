package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(name = "nyxclient.module.nolimit.name", description = "nyxclient.module.nolimit.description", category = Category.PLAYER)
public class NoLimit extends Module {
    public static final NoLimit INSTANCE = new NoLimit();

    public final BoolValue portalScreens = ValueBuild.boolSetting("portal screens", true, this);
    public final BoolValue mountedInventory = ValueBuild.boolSetting("mounted inventory", true, this);

    public boolean allowsPortalScreens() {
        return isEnabled() && portalScreens.getValue();
    }

    public boolean shouldOpenPlayerInventoryWhileMounted(LocalPlayer player) {
        return isEnabled() && mountedInventory.getValue() && player != null && player.isPassenger();
    }
}
