package rotp.core.mixin.client.firstperson;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.client.firstperson.FirstPersonRender;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.mechanics.clothes.client.layer.HumanoidClothesLayer;
import rotp.core.mechanics.clothes.client.layer.HumanoidClothesRSExtension;
import rotp.core.compat.v1_21_4.renderstate.ExtractRSExtensionManually;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	
	public PlayerRendererMixin(Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "renderHand", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;"
					+ "setModelProperties("
					+ "Lnet/minecraft/client/player/AbstractClientPlayer;"
					+ ")V",
			shift = At.Shift.AFTER))
	private void jojo_ripples$hideOuterLayer(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, 
			AbstractClientPlayer player, ModelPart rendererArm, ModelPart rendererArmwear, CallbackInfo ci) {
		ExtractRSExtensionManually.extractClothes(player);
		HumanoidClothesLayer.disablePlayerOuterLayer(this.getModel(), HumanoidClothesRSExtension.getCurRenderData());
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jojo_ripples$afterRenderHand(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, 
			AbstractClientPlayer player, ModelPart rendererArm, ModelPart rendererArmwear, CallbackInfo ci) {
		HumanoidArm side = rendererArm == ((HumanoidModel<?>) model).leftArm ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		FirstPersonRender.renderLayers(this, player, poseStack, buffer, combinedLight, side, ClientUtil.partialTick());
	}
}
