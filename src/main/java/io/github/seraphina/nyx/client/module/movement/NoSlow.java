package io.github.seraphina.nyx.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.seraphina.nyx.client.events.api.EventTarget;
import io.github.seraphina.nyx.client.events.impl.ClickEvent;
import io.github.seraphina.nyx.client.events.impl.MoveInputEvent;
import io.github.seraphina.nyx.client.events.impl.PacketEvent;
import io.github.seraphina.nyx.client.events.impl.SlowdownEvent;
import io.github.seraphina.nyx.client.events.impl.StartUseItemEvent;
import io.github.seraphina.nyx.client.events.impl.TickEvent;
import io.github.seraphina.nyx.client.mixins.MultiPlayerGameModeAccessor;
import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.utility.player.InventoryUtility;
import io.github.seraphina.nyx.client.utility.player.MovingUtility;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.EnumValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@ModuleInfo(name = "nyxclient.module.noslow.name", description = "nyxclient.module.noslow.description", category = Category.MOVEMENT)
public class NoSlow extends Module {
    public static final NoSlow INSTANCE = new NoSlow();

    public final EnumValue<Mode> mode = ValueBuild.enumSetting("mode", Mode.VANILLA, this);
    public final BoolValue food = ValueBuild.boolSetting("food", true, this);
    public final BoolValue bow = ValueBuild.boolSetting("bow", true, this);
    public final BoolValue crossbow = ValueBuild.boolSetting("crossbow", true, this);
    public final BoolValue soulSand = ValueBuild.boolSetting("soul sand", true, this);
    public final BoolValue sneak = ValueBuild.boolSetting("sneak", false, this);
    public final BoolValue climb = ValueBuild.boolSetting("climb", false, this);
    public final BoolValue guiMove = ValueBuild.boolSetting("gui move", true, this);
    public final BoolValue allowGuiSneak = ValueBuild.boolSetting("allow gui sneak", false, () -> guiMove.getValue(), this);
    public final EnumValue<ClickBypass> clickBypass = ValueBuild.enumSetting("gui move bypass", ClickBypass.NONE, this);

    private final Queue<ServerboundContainerClickPacket> storedClicks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean releasingClicks = new AtomicBoolean();

    private boolean wasUsingItem;
    private int useDelay;
    private int onGroundTicks;
    private boolean newGrimActive;
    private boolean newGrimSwappedOffhand;
    private boolean newGrimUseStarted;
    private boolean newGrimRestorePending;
    private int newGrimUseSlot = InventoryUtility.NOT_FOUND;
    private int newGrimStartTicks;

    @Override
    public void onEnable() {
        onGroundTicks = 0;
        wasUsingItem = false;
        useDelay = 0;
        resetNewGrimState();
        storedClicks.clear();
        releasingClicks.set(false);
    }

    @Override
    public void onDisable() {
        onGroundTicks = 0;
        wasUsingItem = false;
        useDelay = 0;
        restoreNewGrimSwap();
        resetNewGrimState();
        storedClicks.clear();
        releasingClicks.set(false);
    }

    @EventTarget
    public void onPostTick(TickEvent.Post event) {
        if (isNull()) {
            wasUsingItem = false;
            useDelay = 0;
            onGroundTicks = 0;
            resetNewGrimState();
            storedClicks.clear();
            return;
        }

        wasUsingItem = mc.player.isUsingItem();
        useDelay--;
        if (wasUsingItem) {
            useDelay = 2;
        }

        if (mc.player.onGround()) {
            onGroundTicks++;
        } else {
            onGroundTicks = 0;
        }

        updateNewGrimState();

        if (wasUsingItem && !mc.player.isPassenger() && !mc.player.isFallFlying()) {
            runUseBypass();
        }

        if (shouldKeepSprintThisTick() && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }

        updateGuiMoveKeys();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (sneak.getValue()) {
            event.setSneak(false);
        }

        if (canGuiMove()) {
            updateGuiMoveKeys();
            Input input = guiMoveInput();
            event.setForward(movementMultiplier(input.forward(), input.backward()));
            event.setStrafe(movementMultiplier(input.left(), input.right()));
            event.setJump(input.jump());
            if (allowGuiSneak.getValue()) {
                event.setSneak(input.shift());
            }
            event.setSprint(input.sprint());
        }

        if (mode.is(Mode.JUMP)
                && mc.player != null
                && mc.player.onGround()
                && mc.player.isUsingItem()
                && MovingUtility.isMoving()
                && isNoSlowAllowedForCurrentItem()) {
            event.setJump(true);
        }
    }

    @EventTarget
    public void onClick(ClickEvent event) {
        if (isNull()) {
            return;
        }

        if (newGrimRestorePending) {
            restoreNewGrimSwap();
            resetNewGrimState();
            return;
        }

        if (mode.is(Mode.NEW_GRIM)) {
            prepareNewGrimUse();
        }
    }

    @EventTarget
    public void onSlowdown(SlowdownEvent event) {
        if (noSlow()) {
            event.setSlowdown(false);
        }
    }

    @EventTarget
    public void onStartUseItem(StartUseItemEvent event) {
        if (isNull()) {
            return;
        }

        if (useDelay > 0) {
            mc.rightClickDelay = 0;
            event.setCancelled(true);
            return;
        }

        if (mode.is(Mode.GRIM_PACKET) && isFoodLimitedItem(mc.player.getItemInHand(InteractionHand.MAIN_HAND))) {
            clickMenuSlotOne();
        }

        updateNewGrimState();
    }

    @EventTarget
    public void onPacketSend(PacketEvent.Send event) {
        if (isNull()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (mode.is(Mode.DROP)
                && packet instanceof ServerboundUseItemPacket useItemPacket
                && useItemPacket.getHand() == InteractionHand.MAIN_HAND
                && hasFoodComponent(mc.player.getMainHandItem())) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.DROP_ITEM,
                    BlockPos.ZERO,
                    Direction.DOWN
            ));
            return;
        }

        if (!MovingUtility.isMoving() || releasingClicks.get()) {
            return;
        }

        if (packet instanceof ServerboundMovePlayerPacket && shouldRunSlimefunUseBypass()) {
            sendCarriedSlot();
        }

        if (packet instanceof ServerboundContainerClickPacket clickPacket) {
            handleClickBypass(clickPacket, event);
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket && clickBypass.is(ClickBypass.DELAY)) {
            flushStoredClicks();
        }
    }

    public boolean noSlow() {
        if (!isEnabled() || isNull() || !mc.player.isUsingItem()) {
            return false;
        }

        Mode currentMode = mode.getValue();
        if (currentMode == Mode.NONE) {
            return false;
        }

        if ((currentMode == Mode.DROP || currentMode == Mode.GRIM_PACKET) && !wasUsingItem) {
            return false;
        }

        if (currentMode == Mode.NEW_GRIM) {
            return shouldRunNewGrim();
        }

        if (!isNoSlowAllowedForCurrentItem()) {
            return false;
        }

        return switch (currentMode) {
            case GRIM_LAZY -> shouldRunGrimLazy();
            case GRIM_TICK -> shouldRunGrimTick();
            case HEYPIXEL_2_3 -> getItemUseTimeLeft() % 3 != 0 && (!isFoodLimitedItem() || getItemUseTimeLeft() <= 30);
            case GRIM_50 -> getItemUseTimeLeft() % 2 == 0 && getItemUseTimeLeft() <= 30;
            case GRIM_1_3 -> getItemUseTimeLeft() % 3 == 0 && (!isFoodLimitedItem() || getItemUseTimeLeft() <= 30);
            case JUMP -> onGroundTicks == 1 && getItemUseTimeLeft() <= 30;
            default -> true;
        };
    }

    public boolean soulSand() {
        return isEnabled() && soulSand.getValue();
    }

    public boolean climb() {
        return isEnabled() && climb.getValue();
    }

    private void runUseBypass() {
        switch (mode.getValue()) {
            case NCP -> sendCarriedSlot();
            case GRIM -> sendAlternateHandUsePacket();
            case GRIM_PACKET -> {
                clickMenuSlotOne();
                sendAlternateHandUsePacket();
            }
            default -> {
            }
        }
    }

    private void sendAlternateHandUsePacket() {
        if (mc.gameMode == null || mc.level == null || mc.player == null) {
            return;
        }

        InteractionHand hand = mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        ((MultiPlayerGameModeAccessor) mc.gameMode).nyx$startPrediction(mc.level, sequence -> new ServerboundUseItemPacket(
                hand,
                sequence,
                mc.player.getYRot(),
                mc.player.getXRot()
        ));
    }

    private void handleClickBypass(ServerboundContainerClickPacket packet, PacketEvent.Send event) {
        switch (clickBypass.getValue()) {
            case NCP -> sendNcpClickBypass(0.0656D);
            case NCP2 -> sendNcpClickBypass(2.71875E-7D);
            case GRIM -> {
                if (packet.clickType() != ClickType.PICKUP && packet.clickType() != ClickType.PICKUP_ALL) {
                    mc.player.connection.send(new ServerboundContainerClosePacket(0));
                }
            }
            case DELAY -> {
                storedClicks.add(packet);
                event.setCancelled(true);
            }
            default -> {
            }
        }
    }

    private void sendNcpClickBypass(double offset) {
        if (!mc.player.onGround()
                || mc.level == null
                || !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0.0D, offset, 0.0D))) {
            return;
        }

        boolean sprinting = mc.player.isSprinting();
        if (sprinting) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(),
                mc.player.getY() + offset,
                mc.player.getZ(),
                false,
                mc.player.horizontalCollision
        ));

        if (sprinting && clickBypass.is(ClickBypass.NCP)) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    private void flushStoredClicks() {
        releasingClicks.set(true);
        try {
            ServerboundContainerClickPacket packet;
            while ((packet = storedClicks.poll()) != null) {
                mc.player.connection.send(packet);
            }
        } finally {
            releasingClicks.set(false);
        }
    }

    private void sendCarriedSlot() {
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
        }
    }

    private void clickMenuSlotOne() {
        if (mc.gameMode != null && mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu.isValidSlotIndex(1)) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 1, 0, ClickType.PICKUP, mc.player);
        }
    }

    private boolean shouldRunSlimefunUseBypass() {
        return switch (mode.getValue()) {
            case GRIM_LAZY -> shouldRunGrimLazy();
            case GRIM_TICK -> shouldRunGrimTick();
            default -> false;
        };
    }

    private boolean shouldRunGrimLazy() {
        return isEnabled()
                && !isNull()
                && mc.player.isUsingItem()
                && !mc.player.isFallFlying()
                && MovingUtility.isMoving()
                && isNoSlowAllowedForCurrentItem()
                && !mc.player.getUseItem().isEmpty();
    }

    private boolean shouldRunGrimTick() {
        return isEnabled()
                && !isNull()
                && mc.player.isUsingItem()
                && isNoSlowAllowedForCurrentItem();
    }

    private boolean shouldKeepSprintThisTick() {
        if (!isEnabled() || isNull() || !mc.player.isUsingItem()) {
            return false;
        }

        return switch (mode.getValue()) {
            case GRIM_LAZY, GRIM_TICK, HEYPIXEL_2_3, GRIM_50, GRIM_1_3, JUMP, NEW_GRIM -> noSlow();
            default -> false;
        };
    }

    private void prepareNewGrimUse() {
        if (newGrimActive
                || mc.screen != null
                || mc.player == null
                || mc.gameMode == null
                || mc.player.connection == null
                || mc.player.isUsingItem()
                || mc.player.isPassenger()
                || mc.player.isFallFlying()
                || !mc.options.keyUse.isDown()
                || mc.rightClickDelay > 0) {
            return;
        }

        int selectedSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isHotbarSlot(selectedSlot)) {
            return;
        }

        if (isNewGrimUseItem(mc.player.getMainHandItem())) {
            return;
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!isNewGrimUseItem(offhand)) {
            return;
        }

        if (swapSelectedSlotWithOffhand(selectedSlot)) {
            beginNewGrimUse(selectedSlot, true);
        }
    }

    private void beginNewGrimUse(int useSlot, boolean swappedOffhand) {
        newGrimActive = true;
        newGrimSwappedOffhand = swappedOffhand;
        newGrimUseStarted = false;
        newGrimRestorePending = false;
        newGrimUseSlot = useSlot;
        newGrimStartTicks = 0;
    }

    private void updateNewGrimState() {
        if (!newGrimActive) {
            return;
        }

        if (!mode.is(Mode.NEW_GRIM)
                || mc.screen != null
                || mc.player == null
                || mc.gameMode == null) {
            newGrimRestorePending = true;
            return;
        }

        newGrimStartTicks++;
        boolean usingExpectedMainHand = mc.player.isUsingItem()
                && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && isNewGrimFood(mc.player.getUseItem())
                && InventoryUtility.isHotbarSlot(newGrimUseSlot);

        if (usingExpectedMainHand) {
            newGrimUseStarted = true;
            return;
        }

        if (newGrimUseStarted || newGrimStartTicks > 4) {
            newGrimRestorePending = true;
        }
    }

    private boolean shouldRunNewGrim() {
        return mc.player.isUsingItem()
                && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && food.getValue()
                && isNewGrimFood(mc.player.getUseItem())
                && newGrimActive;
    }

    private void restoreNewGrimSwap() {
        if (!newGrimSwappedOffhand || !InventoryUtility.isHotbarSlot(newGrimUseSlot)) {
            return;
        }

        if (mc.player != null && mc.gameMode != null && mc.player.connection != null) {
            swapSelectedSlotWithOffhand(newGrimUseSlot);
        }
    }

    private void resetNewGrimState() {
        newGrimActive = false;
        newGrimSwappedOffhand = false;
        newGrimUseStarted = false;
        newGrimRestorePending = false;
        newGrimUseSlot = InventoryUtility.NOT_FOUND;
        newGrimStartTicks = 0;
    }

    private boolean swapSelectedSlotWithOffhand(int hotbarSlot) {
        if (mc.player == null
                || mc.gameMode == null
                || mc.player.connection == null
                || !InventoryUtility.isHotbarSlot(hotbarSlot)) {
            return false;
        }

        int previousSlot = InventoryUtility.getSelectedHotbarSlot();
        if (!InventoryUtility.isHotbarSlot(previousSlot)) {
            return false;
        }

        boolean changedSlot = previousSlot != hotbarSlot;
        if (changedSlot && !InventoryUtility.selectHotbarSlot(hotbarSlot, true)) {
            return false;
        }

        try {
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO,
                    Direction.DOWN
            ));
            swapLocalSelectedSlotWithOffhand(hotbarSlot);
            return true;
        } finally {
            if (changedSlot) {
                InventoryUtility.selectHotbarSlot(previousSlot, true);
            }
        }
    }

    private boolean isNewGrimUseItem(ItemStack stack) {
        return food.getValue() && isNewGrimFood(stack);
    }

    private void swapLocalSelectedSlotWithOffhand(int hotbarSlot) {
        ItemStack mainHand = mc.player.getInventory().getItem(hotbarSlot);
        ItemStack offhand = mc.player.getInventory().getItem(InventoryUtility.OFFHAND_SLOT);
        mc.player.getInventory().setItem(hotbarSlot, offhand);
        mc.player.getInventory().setItem(InventoryUtility.OFFHAND_SLOT, mainHand);
    }

    private boolean isNewGrimFood(ItemStack stack) {
        return !stack.isEmpty() && hasFoodComponent(stack);
    }

    private boolean isNoSlowAllowedForCurrentItem() {
        if (!food.getValue() && isFoodLimitedItem()) {
            return false;
        }

        if (!bow.getValue() && isHolding(Items.BOW)) {
            return false;
        }

        if (!crossbow.getValue() && isHolding(Items.CROSSBOW)) {
            return false;
        }

        return !isFoodLimitedItem() || getItemUseTimeLeft() <= 30;
    }

    private int getItemUseTimeLeft() {
        return mc.player.getUseItemRemainingTicks();
    }

    private boolean isFoodLimitedItem() {
        return isFoodLimitedItem(mc.player.getMainHandItem()) || isFoodLimitedItem(mc.player.getOffhandItem());
    }

    private boolean isFoodLimitedItem(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.POTION);
    }

    private boolean hasFoodComponent(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    private boolean isHolding(Item item) {
        return mc.player.getMainHandItem().is(item) || mc.player.getOffhandItem().is(item);
    }

    private void updateGuiMoveKeys() {
        if (!canGuiMove()) {
            return;
        }

        setKeyDown(mc.options.keyDown);
        setKeyDown(mc.options.keyLeft);
        setKeyDown(mc.options.keyRight);
        setKeyDown(mc.options.keyJump);
        setKeyDown(mc.options.keyUp);
        setKeyDown(mc.options.keySprint);
        if (allowGuiSneak.getValue()) {
            setKeyDown(mc.options.keyShift);
        }
    }

    private boolean canGuiMove() {
        return guiMove.getValue() && mc.screen != null && !(mc.screen instanceof ChatScreen);
    }

    private Input guiMoveInput() {
        return new Input(
                isKeyDown(mc.options.keyUp),
                isKeyDown(mc.options.keyDown),
                isKeyDown(mc.options.keyLeft),
                isKeyDown(mc.options.keyRight),
                isKeyDown(mc.options.keyJump),
                allowGuiSneak.getValue() && isKeyDown(mc.options.keyShift),
                isKeyDown(mc.options.keySprint)
        );
    }

    private void setKeyDown(KeyMapping keyMapping) {
        keyMapping.setDown(isKeyDown(keyMapping));
    }

    private boolean isKeyDown(KeyMapping keyMapping) {
        if (mc.getWindow() == null) {
            return false;
        }

        return InputConstants.isKeyDown(mc.getWindow(), keyMapping.getKey().getValue());
    }

    private static float movementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }

        return positive ? 1.0F : -1.0F;
    }

    public enum Mode {
        VANILLA("Vanilla"),
        NCP("NCP"),
        GRIM("Grim"),
        GRIM_PACKET("GrimPacket"),
        GRIM_LAZY("GrimLazy"),
        GRIM_TICK("GrimTick"),
        DROP("Drop"),
        HEYPIXEL_2_3("Heypixel2_3"),
        GRIM_50("Grim50"),
        GRIM_1_3("Grim1_3"),
        JUMP("Jump"),
        NONE("None"),
        NEW_GRIM("New Grim"),
        ;

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum ClickBypass {
        NONE("None"),
        NCP("NCP"),
        NCP2("NCP2"),
        GRIM("Grim"),
        DELAY("Delay");

        private final String name;

        ClickBypass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
