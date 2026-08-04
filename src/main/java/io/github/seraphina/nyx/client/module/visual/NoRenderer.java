package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.ValueGroup;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(name = "nyxclient.module.norenderer.name", description = "nyxclient.module.norenderer.description", category = Category.VISUAL)
public class NoRenderer extends Module {
    public static final NoRenderer INSTANCE = new NoRenderer();
    private static final Identifier PUMPKIN_BLUR_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/pumpkinblur.png");

    private final ValueGroup overlayGroup = ValueBuild.settingGroup("overlay", this);
    private final ValueGroup hudGroup = ValueBuild.settingGroup("hud", this);
    private final ValueGroup worldGroup = ValueBuild.settingGroup("world", this);
    private final ValueGroup entityGroup = ValueBuild.settingGroup("entity", this);

    public final BoolValue noHurtCamera = ValueBuild.boolSetting("nohurtcamera", false, this).group(overlayGroup);
    public final BoolValue noView = ValueBuild.boolSetting("noview", false, this).group(overlayGroup);
    public final BoolValue portalOverlay = ValueBuild.boolSetting("portal overlay", false, this).group(overlayGroup);
    public final BoolValue spyglassOverlay = ValueBuild.boolSetting("spyglass overlay", false, this).group(overlayGroup);
    public final BoolValue nauseaOverlay = ValueBuild.boolSetting("nausea overlay", false, this).group(overlayGroup);
    public final BoolValue pumpkin = ValueBuild.boolSetting("pumpkin", false, this).group(overlayGroup);
    public final BoolValue powderedSnowOverlay = ValueBuild.boolSetting("powdered snow overlay", false, this).group(overlayGroup);
    public final BoolValue fireOverlay = ValueBuild.boolSetting("fire overlay", false, this).group(overlayGroup);
    public final BoolValue liquidOverlay = ValueBuild.boolSetting("liquid overlay", false, this).group(overlayGroup);
    public final BoolValue inWallOverlay = ValueBuild.boolSetting("in wall overlay", false, this).group(overlayGroup);
    public final BoolValue vignette = ValueBuild.boolSetting("vignette", false, this).group(overlayGroup);
    public final BoolValue totemAnimation = ValueBuild.boolSetting("totem animation", false, this).group(overlayGroup);

    public final BoolValue bossBar = ValueBuild.boolSetting("boss bar", false, this).group(hudGroup);
    public final BoolValue scoreboard = ValueBuild.boolSetting("scoreboard", false, this).group(hudGroup);
    public final BoolValue crosshair = ValueBuild.boolSetting("crosshair", false, this).group(hudGroup);
    public final BoolValue title = ValueBuild.boolSetting("title", false, this).group(hudGroup);
    public final BoolValue heldItemName = ValueBuild.boolSetting("held item name", false, this).group(hudGroup);
    public final BoolValue potionIcons = ValueBuild.boolSetting("potion icons", false, this).group(hudGroup);

    public final BoolValue noParticles = ValueBuild.boolSetting("noparticles", false, this).group(worldGroup);
    public final EnumValue<ParticlesType> particleType = ValueBuild
        .enumSetting("particles no render type", ParticlesType.All, noParticles::getValue, this)
        .group(worldGroup);
    public final BoolValue weather = ValueBuild.boolSetting("weather", false, this).group(worldGroup);
    public final BoolValue worldBorder = ValueBuild.boolSetting("world border", false, this).group(worldGroup);
    public final BoolValue beaconBeams = ValueBuild.boolSetting("beacon beams", false, this).group(worldGroup);
    public final BoolValue fallingBlocks = ValueBuild.boolSetting("falling blocks", false, this).group(worldGroup);

    public final BoolValue deathEntity = ValueBuild.boolSetting("death entity", false, this).group(entityGroup);
    public final BoolValue armor = ValueBuild.boolSetting("armor", false, this).group(entityGroup);
    public final BoolValue nameTags = ValueBuild.boolSetting("name tags", false, this).group(entityGroup);

    public boolean shouldDisableTotemAnimation(ItemStack stack) {
        return shouldDisable(totemAnimation) && stack.has(DataComponents.DEATH_PROTECTION);
    }

    public boolean shouldHideDeathEntity(float deathTime) {
        return shouldDisable(deathEntity) && deathTime > 0.0F;
    }

    public boolean shouldDisablePumpkinOverlay(Identifier texture) {
        return shouldDisable(pumpkin) && PUMPKIN_BLUR_TEXTURE.equals(texture);
    }

    public boolean shouldDisablePortalOverlay() {
        return shouldDisable(portalOverlay);
    }

    public boolean shouldDisableSpyglassOverlay() {
        return shouldDisable(spyglassOverlay);
    }

    public boolean shouldDisableNauseaOverlay() {
        return shouldDisable(nauseaOverlay);
    }

    public boolean shouldDisablePowderedSnowOverlay() {
        return shouldDisable(powderedSnowOverlay);
    }

    public boolean shouldDisableFireOverlay() {
        return shouldDisable(fireOverlay);
    }

    public boolean shouldDisableLiquidOverlay() {
        return shouldDisable(liquidOverlay);
    }

    public boolean shouldDisableInWallOverlay() {
        return shouldDisable(inWallOverlay);
    }

    public boolean shouldDisableVignette() {
        return shouldDisable(vignette);
    }

    public boolean shouldDisableBossBar() {
        return shouldDisable(bossBar);
    }

    public boolean shouldDisableScoreboard() {
        return shouldDisable(scoreboard);
    }

    public boolean shouldDisableCrosshair() {
        return shouldDisable(crosshair);
    }

    public boolean shouldDisableTitle() {
        return shouldDisable(title);
    }

    public boolean shouldDisableHeldItemName() {
        return shouldDisable(heldItemName);
    }

    public boolean shouldDisablePotionIcons() {
        return shouldDisable(potionIcons);
    }

    public boolean shouldDisableWeather() {
        return shouldDisable(weather);
    }

    public boolean shouldDisableWorldBorder() {
        return shouldDisable(worldBorder);
    }

    public boolean shouldDisableBeaconBeams() {
        return shouldDisable(beaconBeams);
    }

    public boolean shouldDisableFallingBlocks() {
        return shouldDisable(fallingBlocks);
    }

    public boolean shouldDisableArmor() {
        return shouldDisable(armor);
    }

    public boolean shouldDisableNameTags() {
        return shouldDisable(nameTags);
    }

    public boolean shouldDisableParticle(ParticleOptions particleOptions) {
        if (!shouldFilterParticles()) {
            return false;
        }

        return switch (particleType.getValue()) {
            case All -> true;
            case OnlyNoRenderExplosion -> isExplosionParticle(particleOptions);
            case OnlyKeepTotem -> !isTotemParticle(particleOptions);
        };
    }

    public boolean shouldDisableParticleAdd(boolean createdFromAllowedOptions) {
        if (!shouldFilterParticles()) {
            return false;
        }

        return switch (particleType.getValue()) {
            case All -> true;
            case OnlyNoRenderExplosion -> false;
            case OnlyKeepTotem -> !createdFromAllowedOptions;
        };
    }

    public boolean shouldClearParticleEngine() {
        return shouldFilterParticles() && particleType.is(ParticlesType.All);
    }

    public boolean shouldTrackAllowedParticleAdds() {
        return shouldFilterParticles() && particleType.is(ParticlesType.OnlyKeepTotem);
    }

    private boolean shouldFilterParticles() {
        return shouldDisable(noParticles);
    }

    private boolean shouldDisable(BoolValue value) {
        return isEnabled() && value.getValue();
    }

    private boolean isExplosionParticle(ParticleOptions particleOptions) {
        ParticleType<?> type = particleOptions.getType();
        return type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER;
    }

    private boolean isTotemParticle(ParticleOptions particleOptions) {
        return particleOptions.getType() == ParticleTypes.TOTEM_OF_UNDYING;
    }

    public enum ParticlesType {
        All("All"),
        OnlyNoRenderExplosion("Only Explosion"),
        OnlyKeepTotem("Only Keep Totem");

        private final String name;

        ParticlesType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
