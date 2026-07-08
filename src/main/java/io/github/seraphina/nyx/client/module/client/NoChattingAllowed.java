package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "nyxclient.module.nochattingallowed.name", description = "nyxclient.module.nochattingallowed.description", category = Category.CLIENT)
public class NoChattingAllowed extends Module {
    public static final NoChattingAllowed INSTANCE = new NoChattingAllowed();

    public boolean shouldBypassChatRestriction(Minecraft minecraft) {
        if (!this.isEnabled()) {
            return false;
        }

        return isAccountChatBlocked(minecraft.getChatStatus(), minecraft.isLocalServer());
    }

    public boolean shouldBypassMessageBlocking(Minecraft minecraft) {
        if (!this.isEnabled()) {
            return false;
        }

        return isAccountChatBlocked(minecraft.getChatStatus(), false);
    }

    private static boolean isAccountChatBlocked(Minecraft.ChatStatus chatStatus, boolean localServer) {
        return chatStatus != Minecraft.ChatStatus.DISABLED_BY_OPTIONS && !chatStatus.isChatAllowed(localServer);
    }
}
