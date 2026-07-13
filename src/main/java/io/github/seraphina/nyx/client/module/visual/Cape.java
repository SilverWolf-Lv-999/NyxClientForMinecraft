package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

@ModuleInfo(name = "nyxclient.module.cape.name", description = "nyxclient.module.cape.description", category = Category.VISUAL)
public class Cape extends Module {
    public static final Cape INSTANCE = new Cape();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.SELF, this);
    public final EnumValue<CapeType> capeType = ValueBuild.enumSetting("cape type", CapeType.NYX, this);

    public PlayerSkin overrideSkin(PlayerInfo playerInfo, PlayerSkin original) {
        if (!shouldReplaceCape(playerInfo) || original == null) {
            return original;
        }

        return withCape(original);
    }

    public PlayerSkin overrideSkin(AbstractClientPlayer player, PlayerSkin original) {
        if (!shouldReplaceCape(player) || original == null) {
            return original;
        }

        return withCape(original);
    }

    public boolean shouldForceCape(Avatar avatar) {
        return shouldReplaceCape(avatar);
    }

    private PlayerSkin withCape(PlayerSkin original) {
        return PlayerSkin.insecure(original.body(), capeType.getValue().texture(), original.elytra(), original.model());
    }

    private boolean shouldReplaceCape(PlayerInfo playerInfo) {
        if (!isEnabled() || playerInfo == null) {
            return false;
        }

        return mode.is(Mode.ALL_PLAYERS) || isLocalPlayer(playerInfo.getProfile().id());
    }

    private boolean shouldReplaceCape(Avatar avatar) {
        if (!isEnabled() || avatar == null) {
            return false;
        }

        return mode.is(Mode.ALL_PLAYERS) || isLocalPlayer(avatar.getUUID());
    }

    private boolean isLocalPlayer(UUID uuid) {
        return mc.getUser() != null && uuid != null && uuid.equals(mc.getUser().getProfileId());
    }

    public enum Mode {
        SELF("Self"),
        ALL_PLAYERS("All Players");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum CapeType {
        NYX("Nyx", "cape.png"),
        LIQUID_BOUNCE("LiquidBounce", "liquidbounce.png"),
        MIO("Mio", "mio.png"),
        CRUCIFIX("CrucifiX", "opal.png"),
        RISE("Rise", "rise.png"),
        VAPE_LITE("VapeLite", "vapelite.png"),
        VAPE_V4("VapeV4", "vapev4.png"),
        NUM_15("15", "15.png"),
        AIXIN("aixin", "aixin.png"),
        LIU_3("Liu3", "liu3.png"),
        LIU_HUA("LiuHua", "liuhua.png"),
        LIU_HUA_2("LiuHua2", "liuhua2.png"),
        SAN_JIU("SanJiu", "sanjiu.png");

        private final String displayName;
        private final ClientAsset.ResourceTexture texture;

        CapeType(String displayName, String fileName) {
            this.displayName = displayName;
            String path = "cape/" + fileName;
            String textureId = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
            this.texture = new ClientAsset.ResourceTexture(
                    Identifier.fromNamespaceAndPath("nyxclient", textureId),
                    Identifier.fromNamespaceAndPath("nyxclient", path)
            );
        }

        private ClientAsset.ResourceTexture texture() {
            return texture;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
