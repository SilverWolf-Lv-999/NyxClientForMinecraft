package io.github.seraphina.nyx.client.module.other;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(name = "nyxclient.module.target.name", description = "nyxclient.module.target.description", category = Category.OTHER)
public class Target extends Module {
    public static final Target INSTANCE = new Target();

    public final BoolValue player = ValueBuild.boolSetting("player", true, this);

    public final BoolValue monster = ValueBuild.boolSetting("monster", false, this);

    public final BoolValue animal = ValueBuild.boolSetting("animal", false, this);

    public final BoolValue villager = ValueBuild.boolSetting("villager", false, this);

    public final BoolValue dead = ValueBuild.boolSetting("dead", false, this);

    public static boolean isTarget(Entity entity) {
        return entity instanceof LivingEntity livingEntity && isTarget(livingEntity);
    }

    public static boolean isTarget(LivingEntity entity) {
        if (entity == null) return false;
        if (entity.isDeadOrDying() && !INSTANCE.dead.getValue()) return false;
        return switch (entity) {
            case Player ignored2 when INSTANCE.player.getValue() -> true;
            case Monster ignored1 when INSTANCE.monster.getValue() -> true;
            case Villager ignored when INSTANCE.villager.getValue() -> true;
            default -> entity instanceof Animal && INSTANCE.animal.getValue();
        };
    }
}
