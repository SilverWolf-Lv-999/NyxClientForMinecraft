package io.github.seraphina.nyx.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.seraphina.nyx.client.events.api.EventManager;
import io.github.seraphina.nyx.client.events.impl.RenderItemInHandEvent;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    @Final
    private ItemModelResolver itemModelResolver;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight) {
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
}
