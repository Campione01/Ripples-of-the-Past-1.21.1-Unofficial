package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * An armour piece the ability turned invisible stays invisible once it is worn.
 *
 * <p>1.16's add-on did this with a mixin on the armour layer (ArmorLayerMixin, cancelling renderArmorPiece for a
 * stack ClientEventHandler.shouldItemBeInvisible accepted). The port kept only the held/dropped item mixin, so
 * armour made invisible was still drawn on the body. NeoForge has no armour render event, so this is the same mixin.
 *
 * <p>The target must be the <b>twelve-argument</b> overload: NeoForge adds its own
 * {@code renderArmorPiece(..., float, float, float, float, float, float)} and {@code render} calls that one directly
 * for every slot. The six-argument vanilla shim only forwards into it with zeroes and is never reached from the render
 * path, so a mixin on it injects successfully, reports nothing, and does nothing - which is exactly how this was
 * shipped broken once already.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/entity/EquipmentSlot;I"
                    + "Lnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void rotp_ab$hideTaggedArmour(
            PoseStack poseStack,
            MultiBufferSource buffer,
            LivingEntity entity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> model,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (AchtungBabyService.isInvisible(entity.getItemBySlot(slot))) {
            ci.cancel();
        }
    }
}
