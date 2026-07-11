package io.github.seraphina.nyx.client.module.visual;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
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

    public final BoolValue nohurtcamera = ValueBuild.boolSetting("nohurtcamera", false, this);

    public final BoolValue noview = ValueBuild.boolSetting("noview", false, this);

    public final BoolValue noparticles = ValueBuild.boolSetting("noparticles", false, this);

    public final EnumValue<ParticlesType> particlesType = ValueBuild.enumSetting("particles no render type", ParticlesType.All, noparticles::getValue, this);

    public final BoolValue totemAnimation = ValueBuild.boolSetting("totem animation", false, this);

    public final BoolValue deathEntity = ValueBuild.boolSetting("death entity", false, this);

    public final BoolValue pumpkin = ValueBuild.boolSetting("pumpkin", false, this);

    public boolean shouldDisableTotemAnimation(ItemStack stack) {
        return isEnabled() && totemAnimation.getValue() && stack.has(DataComponents.DEATH_PROTECTION);
    }

    public boolean shouldHideDeathEntity(float deathTime) {
        return isEnabled() && deathEntity.getValue() && deathTime > 0.0F;
    }

    public boolean shouldDisablePumpkinOverlay(Identifier texture) {
        return isEnabled() && pumpkin.getValue() && PUMPKIN_BLUR_TEXTURE.equals(texture);
    }

    public boolean shouldDisableParticle(ParticleOptions particleOptions) {
        if (!shouldFilterParticles()) {
            return false;
        }

        return switch (particlesType.getValue()) {
            case All -> true;
            case OnlyNoRenderExplosion -> isExplosionParticle(particleOptions);
            case OnlyKeepTotem -> !isTotemParticle(particleOptions);
        };
    }

    public boolean shouldDisableParticleAdd(boolean createdFromAllowedOptions) {
        if (!shouldFilterParticles()) {
            return false;
        }

        return switch (particlesType.getValue()) {
            case All -> true;
            case OnlyNoRenderExplosion -> false;
            case OnlyKeepTotem -> !createdFromAllowedOptions;
        };
    }

    public boolean shouldClearParticleEngine() {
        return shouldFilterParticles() && particlesType.is(ParticlesType.All);
    }

    public boolean shouldTrackAllowedParticleAdds() {
        return shouldFilterParticles() && particlesType.is(ParticlesType.OnlyKeepTotem);
    }

    private boolean shouldFilterParticles() {
        return isEnabled() && noparticles.getValue();
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
