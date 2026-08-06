package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.events.impl.RenderItemInHandEvent;
import io.github.seraphina.nyx.client.module.visual.Animations;
import io.github.seraphina.nyx.client.utility.ItemSpoofVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ItemModelResolver itemModelResolver;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equippedProgress) {
    }

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void nyx$applyAnimationEquipProgress(CallbackInfo info) {
        if (this.minecraft.player == null) {
            return;
        }

        boolean spoofItem = ItemSpoofVisual.isEnabled();
        boolean disableEquipProgress = Animations.INSTANCE.shouldDisableEquipProgress();
        boolean oldHit = Animations.INSTANCE.shouldUseOldHit() && !this.minecraft.player.isHandsBusy();
        if (!spoofItem && !disableEquipProgress && !oldHit) {
            return;
        }

        ItemStack mainStack = spoofItem
                ? ItemSpoofVisual.getMainHandItem()
                : this.minecraft.player.getMainHandItem();
        this.mainHandItem = mainStack;
        this.mainHandHeight = 1.0F;
        this.oMainHandHeight = 1.0F;

        if (disableEquipProgress) {
            ItemStack offStack = this.minecraft.player.getOffhandItem();
            this.offHandItem = offStack;
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void nyx$renderSmoothSwing(float swingProgress, PoseStack poseStack, int direction, HumanoidArm arm, CallbackInfo info) {
        if (!Animations.INSTANCE.shouldUseSmoothSwing()) {
            return;
        }

        this.applyItemArmAttackTransform(poseStack, arm, swingProgress);
        info.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void nyx$renderBlockAnimation(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equippedProgress,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            CallbackInfo info
    ) {
        if (!Animations.INSTANCE.isEnabled()
                || player.isScoping()
                || !player.isUsingItem()
                || player.getUseItemRemainingTicks() <= 0
                || player.getUsedItemHand() != hand
                || item.getUseAnimation() != ItemUseAnimation.BLOCK
                || !item.is(ItemTags.SWORDS)) {
            return;
        }

        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        info.cancel();
        poseStack.pushPose();
        this.applyItemArmTransform(poseStack, arm, equippedProgress);
        this.applyBlockAnimation(
                poseStack,
                swingProgress,
                arm,
                direction,
                Animations.INSTANCE.blockMode.getValue()
        );
        this.renderItem(
                player,
                item,
                direction == 1 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack,
                nodeCollector,
                packedLight
        );
        poseStack.popPose();
    }

    private void applyBlockAnimation(
            PoseStack poseStack,
            float swingProgress,
            HumanoidArm arm,
            int direction,
            Animations.BlockMode blockMode
    ) {
        float progress = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        float progressSquared = Mth.sin(swingProgress * swingProgress * (float) Math.PI);

        switch (blockMode) {
            case ONE_SEVEN -> {
                this.applyItemArmAttackTransform(poseStack, arm, swingProgress);
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
            }
            case STELLA -> {
                this.applyItemArmAttackTransform(poseStack, arm, swingProgress);
                poseStack.translate(-0.15F, 0.1F, -0.06F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-95.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-278.0F));
            }
            case LEAKED -> {
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 7.365F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 78.05F));
                poseStack.mulPose(Axis.XP.rotationDegrees(progress * -10.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * progress * 30.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * progress * -13.0F));
            }
            case SIDE_DOWN -> {
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * progressSquared * -20.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * progress * -20.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(progress * -80.0F));
            }
            case STYLES -> {
                this.applyItemArmAttackTransform(poseStack, arm, 0.0F);
                poseStack.translate(0.08F * direction, 0.02F, 0.0F);
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-progress * 41.0F));
            }
            case FLUX -> {
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.translate(0.0F, 0.0F, progress * -0.25F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(progress * -15.0F));
            }
            case SPIN -> {
                float spin = (System.currentTimeMillis() % 720L) / 2.0F;
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * (16.0F + spin)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
            }
            case SCREW -> {
                float spin = (System.currentTimeMillis() % 1000L) / 2.7F;
                poseStack.translate(direction * -0.15F, 0.05F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * spin * 2.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(spin));
            }
            case SWANG -> {
                this.applyItemArmAttackTransform(poseStack, arm, swingProgress);
                poseStack.translate(direction * -0.15F, 0.15F, 0.1F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-105.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * 16.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -278.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(progress * 40.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(direction * progress * -10.0F));
            }
        }
    }

    @Redirect(
            method = "applyEatTransform",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0)
    )
    private void nyx$disableEatBobbing(PoseStack poseStack, float x, float y, float z) {
        if (!Animations.INSTANCE.shouldDisableEatBobbing()) {
            poseStack.translate(x, y, z);
        }
    }

    @Redirect(
            method = "applyEatTransform",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1)
    )
    private void nyx$disableEatCenter(PoseStack poseStack, float x, float y, float z) {
        if (!Animations.INSTANCE.shouldDisableEatCenter()) {
            poseStack.translate(x, y, z);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight) {
        if (this.nyx$shouldRenderSpoofedMainHand(entity, displayContext)) {
            stack = ItemSpoofVisual.getMainHandItem();
        }

        if (!stack.isEmpty()) {
            RenderItemInHandEvent event = new RenderItemInHandEvent(entity, stack, displayContext, poseStack, nodeCollector, packedLight);
            EventManager.call(event);
            if (event.isCancelled() || event.getStack().isEmpty()) {
                return;
            }

            entity = event.getEntity();
            stack = event.getStack();
            displayContext = event.getDisplayContext();
            poseStack = event.getPoseStack();
            nodeCollector = event.getNodeCollector();
            packedLight = event.getPackedLight();

            ItemStackRenderState itemstackrenderstate = new ItemStackRenderState();
            poseStack.pushPose();
            poseStack.translate(event.getXPos(), event.getYPos(), event.getZPos());
            poseStack.mulPose(Axis.XP.rotationDegrees((float) event.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) event.getYRot()));
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) event.getZRot()));
            poseStack.scale(event.getScale(), event.getScale(), event.getScale());
            this.itemModelResolver.updateForTopItem(itemstackrenderstate, stack, displayContext, entity.level(), entity, entity.getId() + displayContext.ordinal());
            itemstackrenderstate.submit(poseStack, nodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

    }

    private boolean nyx$shouldRenderSpoofedMainHand(LivingEntity entity, ItemDisplayContext displayContext) {
        if (!ItemSpoofVisual.isEnabled() || entity != this.minecraft.player || this.minecraft.player == null) {
            return false;
        }

        ItemDisplayContext mainHandContext = this.minecraft.player.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        return displayContext == mainHandContext;
    }
}
