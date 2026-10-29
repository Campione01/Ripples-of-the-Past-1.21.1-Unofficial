package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rotp_ab$hideTaggedItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay,
            BakedModel model,
            CallbackInfo ci) {
        if (AchtungBabyService.isInvisible(stack)) {
            ci.cancel();
        }
    }
}
