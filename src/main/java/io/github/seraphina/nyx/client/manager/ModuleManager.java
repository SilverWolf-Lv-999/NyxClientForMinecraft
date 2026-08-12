package io.github.seraphina.nyx.client.manager;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.client.*;
import io.github.seraphina.nyx.client.module.combat.*;
import io.github.seraphina.nyx.client.module.movement.*;
import io.github.seraphina.nyx.client.module.other.*;
import io.github.seraphina.nyx.client.module.player.*;
import io.github.seraphina.nyx.client.module.visual.*;
import io.github.seraphina.nyx.client.module.visual.Map;
import io.github.seraphina.nyx.client.module.visual.hud.HUD;

import java.util.*;
import java.util.stream.Collectors;

public final class ModuleManager {
    public static final Set<Module> MODULES = new LinkedHashSet<>();
    private static boolean initialized;

    public static void init() {
        if (initialized) {
            return;
        }

        //Client
        registerModule(
                ClickGui.INSTANCE,
                Client.INSTANCE,
                NoChattingAllowed.INSTANCE,
                Debug.INSTANCE,
                Friend.INSTANCE,
                ClientSpoof.INSTANCE,
                Zoom.INSTANCE,
                NetworkOptimization.INSTANCE,
                NameProtection.INSTANCE,
                EntityCulling.INSTANCE,
                BlockCulling.INSTANCE,
                ThreadRipper.INSTANCE,
                Disabler.INSTANCE
        );
        //Combat
        registerModule(
                KillAura.INSTANCE,
                Reach.INSTANCE,
                SpearThrust.INSTANCE,
                SuperSpearKill.INSTANCE,
                UseClick.INSTANCE,
                MaceKill.INSTANCE,
                MaceAura.INSTANCE,
                CrystalAura.INSTANCE,
                AnchorAura.INSTANCE,
                SpearCooldown.INSTANCE,
                TpAura.INSTANCE,
                TPBowGod.INSTANCE,
                Backtrack.INSTANCE,
                KeyPearl.INSTANCE,
                Burrow.INSTANCE,
                Surround.INSTANCE,
                AutoWeb.INSTANCE,
                Blocker.INSTANCE,
                AutoPush.INSTANCE,
                MaceSpoof.INSTANCE,
                Criticals.INSTANCE
        );
        //Movement
        registerModule(
                Scaffold.INSTANCE,
                BHop.INSTANCE,
                AutoJump.INSTANCE,
                HighJump.INSTANCE,
                Sprint.INSTANCE,
                KeepSprint.INSTANCE,
                Step.INSTANCE,
                Stuck.INSTANCE,
                Fly.INSTANCE,
                PacketFly.INSTANCE,
                ElytraFly.INSTANCE,
                FastFall.INSTANCE,
                SafeWalk.INSTANCE,
                AntiVoid.INSTANCE,
                NoSlow.INSTANCE,
                NoWeb.INSTANCE,
                NoPush.INSTANCE,
                NoInertia.INSTANCE,
                AutoIceBoot.INSTANCE,
                EntityControl.INSTANCE,
                Strafe.INSTANCE,
                ChorusControl.INSTANCE,
                RocketExtend.INSTANCE,
                VClip.INSTANCE,
                Flatten.INSTANCE,
                BlockStrafe.INSTANCE,
                HoleSnap.INSTANCE,
                MovementSync.INSTANCE
        );
        //Other
        registerModule(
                Test.INSTANCE,
                Target.INSTANCE,
                NoInteraction.INSTANCE,
                PlayerAlert.INSTANCE,
                AutoLogin.INSTANCE,
                NoDownload.INSTANCE,
                FakePlayer.INSTANCE,
                MusicPlayer.INSTANCE,
                Auto2048.INSTANCE,
                AutoNoWhite.INSTANCE,
                Nuker.INSTANCE,
                AntiElytraDamaged.INSTANCE
        );
        //Player
        registerModule(
                AutoHeal.INSTANCE,
                FastPlace.INSTANCE,
                NoJumpDelay.INSTANCE,
                NoLimit.INSTANCE,
                AutoElytra.INSTANCE,
                AutoArmor.INSTANCE,
                AutoTotem.INSTANCE,
                AutoWindCharge.INSTANCE,
                InstantSwitch.INSTANCE,
                AutoLeave.INSTANCE,
                AutoCrystal.INSTANCE,
                BedBreaker.INSTANCE,
                AutoDestroy.INSTANCE,
                PacketMine.INSTANCE,
                PacketEat.INSTANCE,
                Blink.INSTANCE,
                LagBack.INSTANCE,
                AntiLag.INSTANCE,
                AntiEffects.INSTANCE,
                AirPlace.INSTANCE,
                AntiHunger.INSTANCE,
                NoFall.INSTANCE,
                Regen.INSTANCE,
                InfiniteTrident.INSTANCE,
                XCarry.INSTANCE,
                FreeLook.INSTANCE,
                Yaw.INSTANCE,
                Freecam.INSTANCE
        );
        //Visual
        registerModule(
                Cape.INSTANCE,
                Blur.INSTANCE,
                NoRenderer.INSTANCE,
                HUD.INSTANCE,
                Watermaker.INSTANCE,
                Animations.INSTANCE,
                FullBright.INSTANCE,
                XRay.INSTANCE,
                ModernGui.INSTANCE,
                Chams.INSTANCE,
                Shader.INSTANCE,
                ContainerESP.INSTANCE,
                PlaceRender.INSTANCE,
                BreakESP.INSTANCE,
                BlockHighlight.INSTANCE,
                ESP.INSTANCE,
                Tracers.INSTANCE,
                ViewClip.INSTANCE,
                HurtMaker.INSTANCE,
                ModuleList.INSTANCE,
                Ambient.INSTANCE,
                NameTag.INSTANCE,
                Filter.INSTANCE,
                ProjectilePrediction.INSTANCE,
                Map.INSTANCE,
                KeyStrokes.INSTANCE,
                Spectrum.INSTANCE,
                MaceEffect.INSTANCE,
                MotionCamera.INSTANCE
        );

        initialized = true;
    }

    public static void registerModule(Module... module) {
        MODULES.addAll(Arrays.asList(module));
    }

    public static Set<Module> getModules() {
        return Collections.unmodifiableSet(MODULES);
    }

    public static List<Module> getModules(Category category) {
        return MODULES.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toUnmodifiableList());
    }

    public static Optional<Module> getModule(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String normalizedName = CommandManager.normalizeName(name);
        return MODULES.stream()
                .filter(module -> CommandManager.normalizeName(module.getConfigName()).equals(normalizedName)
                        || CommandManager.normalizeName(module.getName()).equals(normalizedName))
                .findFirst();
    }

    public static <T extends Module> Optional<T> getModule(Class<T> moduleClass) {
        if (moduleClass == null) {
            return Optional.empty();
        }

        return MODULES.stream()
                .filter(moduleClass::isInstance)
                .map(moduleClass::cast)
                .findFirst();
    }
}
