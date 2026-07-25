package com.github.standobyte.jojo.mixin.client.v1_21_1_modelanim.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.github.standobyte.jojo.client.entityrender.RipplesPlayerRenderState;
import com.github.standobyte.jojo.client.entityrender.RipplesPlayerRenderState.RipplesRenderStateExtensionMixin;
import com.github.standobyte.jojoimpl.powers.hamon.HamonZoomPunchState;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanStoneFormLayer;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.LivingEntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	public PlayerRendererMixin(Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	private HumanoidRenderState jojo_ripples$reusedState = new HumanoidRenderState();

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
					+ "render("
					+ "Lnet/minecraft/world/entity/LivingEntity;"
					+ "FF"
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/MultiBufferSource;"
					+ "I)V"))
	private void jojo_ripples$renderWithPlayerRenderState(
			PlayerRenderer renderer,
			LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light,
			Operation<Void> original) {
		if (!(entity instanceof AbstractClientPlayer player)) {
			original.call(renderer, entity, entityYaw, partialTick, poseStack, buffer, light);
			return;
		}
		final HumanoidRenderState reusedState = jojo_ripples$reusedState;
		LivingEntityRenderState.extract(player, reusedState, this, entityRenderDispatcher, partialTick);
		HumanoidRenderState.extractHumanoidRenderState(player, reusedState, partialTick);
		RipplesPlayerRenderState.extract(player, reusedState, ((RipplesRenderStateExtensionMixin) reusedState).get(), partialTick);
		RenderStateCrutches.Snapshot crutchSnapshot = RenderStateCrutches.pushEntity(reusedState);
		boolean hideZoomPunchArm = HamonZoomPunchState.isUsingZoomPunch(player);
		ModelPart arm = null;
		ModelPart sleeve = null;
		boolean armVisible = true;
		boolean sleeveVisible = true;
		if (hideZoomPunchArm) {
			PlayerModel<AbstractClientPlayer> model = renderer.getModel();
			if (player.getMainArm() == HumanoidArm.LEFT) {
				arm = model.leftArm;
				sleeve = model.leftSleeve;
			}
			else {
				arm = model.rightArm;
				sleeve = model.rightSleeve;
			}
			armVisible = arm.visible;
			sleeveVisible = sleeve.visible;
			arm.visible = false;
			sleeve.visible = false;
		}
		PillarmanStoneFormLayer.OuterLayerVisibility stoneFormOuterLayer =
				PillarmanStoneFormLayer.captureOuterLayerVisibility(player, renderer.getModel());
		try {
			if (stoneFormOuterLayer != null) {
				stoneFormOuterLayer.hide();
			}
			original.call(renderer, entity, entityYaw, partialTick, poseStack, buffer, light);
		}
		finally {
			if (stoneFormOuterLayer != null) {
				stoneFormOuterLayer.restore();
			}
			if (hideZoomPunchArm) {
				arm.visible = armVisible;
				sleeve.visible = sleeveVisible;
			}
			RenderStateCrutches.restore(crutchSnapshot);
		}
	}
	
	
	@Inject(method = "renderHand", at = @At("HEAD"))
	public void jojo_ripples$fix1stPersonArmBend(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, 
			AbstractClientPlayer player, ModelPart rendererArm, ModelPart rendererArmwear, CallbackInfo ci) {
		rendererArm.resetPose();
		rendererArmwear.resetPose();
	}
}
