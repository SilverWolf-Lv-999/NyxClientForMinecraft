package io.github.seraphina.nyx.client.module.player;

import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;
import net.minecraft.world.item.Items;


@ModuleInfo(name = "nyxclient.module.autototem.name", description = "nyxclient.module.autototem.description", category = Category.PLAYER)
public class AutoTotem extends Module {
    public static final AutoTotem INSTANCE = new AutoTotem();

    public final EnumValue<AutoType> autoType = ValueBuild.enumSetting("auto type", AutoType.NORMAL, this);

    public final BoolValue legit = ValueBuild.boolSetting("legit", false, this);

    public final IntValue life = ValueBuild.intSetting("life", 5, 3, 20, 1, () -> autoType.getValue() == AutoType.SMART, this);

    @EventTarget
    public void onTick(TickEvent.Post event) {
        if (!canRun() || !shouldMoveTotem() || InventoryUtility.getOffhandStack().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int totemSlot = InventoryUtility.findInventorySlot(Items.TOTEM_OF_UNDYING);
        if (totemSlot == InventoryUtility.NOT_FOUND) {
            return;
        }

        InventoryUtility.moveInventorySlotToOffhand(totemSlot);
    }

    private boolean canRun() {
        return mc.player != null
                && mc.level != null
                && mc.gameMode != null
                && !mc.player.isSpectator()
                && !InventoryUtility.hasCarriedStack()
                && (!legit.getValue() || InventoryUtility.isOpenInventory());
    }

    private boolean shouldMoveTotem() {
        return autoType.getValue() == AutoType.NORMAL || mc.player.getHealth() < life.getValue();
    }

    public enum AutoType {
        NORMAL,
        SMART
    }
}
