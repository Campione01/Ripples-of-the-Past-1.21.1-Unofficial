package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;

/**
 * A thrown item that was invisible in the hand stays invisible in flight.
 *
 * <p>1.16 decided this in ProjectileInvisibilityHelper: a projectile is invisible when the item it was thrown with
 * carries the tag. The port dropped that side of it, so an invisible snowball, egg, ender pearl or potion became
 * visible the moment it left the hand. ThrownItemRenderer draws every ItemSupplier projectile from its own item
 * stack, which is exactly the stack 1.16 tested, so one injection covers that whole family.
 */
@Mixin(ThrownItemRenderer.class)
public abstract class ThrownItemRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void rotp_ab$hideTaggedProjectile(
            Entity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci) {
        if (entity instanceof ItemSupplier supplier
                && AchtungBabyService.isInvisible(supplier.getItem())) {
            ci.cancel();
        }
    }
}
