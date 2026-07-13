package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

@ModuleInfo(name = "nyxclient.module.cape.name", description = "nyxclient.module.cape.description", category = Category.VISUAL)
public class Cape extends Module {
    public static final Cape INSTANCE = new Cape();

    private static final ClientAsset.ResourceTexture CAPE_TEXTURE = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath("nyxclient", "cape/cape"),
            Identifier.fromNamespaceAndPath("nyxclient", "cape/cape.png")
    );
    private static final ThreadLocal<Boolean> RESOLVING_LOCAL_SKIN = ThreadLocal.withInitial(() -> false);

    public PlayerSkin overrideSkin(PlayerSkin original) {
        if (!isEnabled() || original == null || RESOLVING_LOCAL_SKIN.get()) {
            return original;
        }

        PlayerSkin localSkin = localSkin(original);
        return PlayerSkin.insecure(localSkin.body(), CAPE_TEXTURE, localSkin.elytra(), localSkin.model());
    }

    public boolean shouldForceCape() {
        return isEnabled();
    }

    private PlayerSkin localSkin(PlayerSkin fallback) {
        if (mc.getConnection() == null) {
            return fallback;
        }

        PlayerInfo localInfo = mc.getConnection().getPlayerInfo(mc.getUser().getProfileId());
        if (localInfo == null) {
            return fallback;
        }

        boolean resolving = RESOLVING_LOCAL_SKIN.get();
        RESOLVING_LOCAL_SKIN.set(true);
        try {
            PlayerSkin skin = localInfo.getSkin();
            return skin == null ? fallback : skin;
        } finally {
            RESOLVING_LOCAL_SKIN.set(resolving);
        }
    }
}
