package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.utility.player.PlayerUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(name = "nyxclient.module.spearthrust.name", description = "nyxclient.module.spearthrust.description", category = Category.COMBAT)
public class SpearThrust extends Module {
    public static final SpearThrust INSTANCE = new SpearThrust();

    public final DoubleValue speed = ValueBuild.doubleValue("speed", 1.0, 0.1, 10.0, 0.1, this);
    public final IntValue range = ValueBuild.intSetting("range", 128, 3, 256, 1, this);

    private LivingEntity thrustTarget;

    @Override
    public void onDisable() {
        resetThrust();
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            resetThrust();
            return;
        }

        ItemStack useItem = mc.player.getUseItem();
        KineticWeapon kineticWeapon = getSpearKineticWeapon(useItem);
        if (!mc.player.isUsingItem() || kineticWeapon == null) {
            resetThrust();
            return;
        }

        if (mc.player.getTicksUsingItem() < kineticWeapon.delayTicks()) {
            thrustTarget = null;
            return;
        }

        thrustTarget = crosshairLivingTarget();

        if (thrustTarget != null && thrustTarget.isAlive()) {
            MovingUtility.setMomentumToward(thrustTarget, speed.getValue());
        }
    }

    private LivingEntity crosshairLivingTarget() {
        if (mc.player == null) return null;
        HitResult hitResult = PlayerUtility.raycastForEntity(mc.level, mc.player, range.getValue(), true);
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
            return livingEntity;
        }

        return null;
    }

    private KineticWeapon getSpearKineticWeapon(ItemStack stack) {
        KineticWeapon kineticWeapon = stack.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon == null || !stack.has(DataComponents.PIERCING_WEAPON)) {
            return null;
        }

        return kineticWeapon;
    }

    private void resetThrust() {
        thrustTarget = null;
    }
}
