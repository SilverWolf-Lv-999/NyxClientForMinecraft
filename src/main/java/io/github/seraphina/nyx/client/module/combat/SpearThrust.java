package io.github.seraphina.nyx.client.module.combat;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.DoubleValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(name = "nyxclient.module.spearthrust.name", description = "nyxclient.module.spearthrust.description", category = Category.COMBAT)
public class SpearThrust extends Module {
    public static final SpearThrust INSTANCE = new SpearThrust();

    public final DoubleValue speed = ValueBuild.doubleValue("speed", 1.0, 0.1, 10.0, 0.1, this);

    private boolean usingSpear;

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            usingSpear = false;
            return;
        }

        boolean currentlyUsingSpear = mc.player.isUsingItem() && isSpear(mc.player.getUseItem());
        if (!currentlyUsingSpear) {
            usingSpear = false;
            return;
        }

        if (usingSpear) {
            return;
        }

        usingSpear = true;
        LivingEntity target = crosshairLivingTarget();
        if (target != null) {
            MovingUtility.addMomentumToward(target, speed.getValue());
        }
    }

    private LivingEntity crosshairLivingTarget() {
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
            return livingEntity;
        }

        return null;
    }

    private boolean isSpear(ItemStack stack) {
        return stack.has(DataComponents.KINETIC_WEAPON) && stack.has(DataComponents.PIERCING_WEAPON);
    }
}
