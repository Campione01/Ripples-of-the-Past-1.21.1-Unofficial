package com.github.standobyte.jojo.mixin.client.v1_21_1_modelanim.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.entityanim.playerbend.PlayerModelBends;
import com.github.standobyte.v1_21_4_stuff.OldPlayerModelJank;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

@Mixin(PlayerModel.class)
public abstract class PlayerModelJankMixin extends HumanoidModelMixin {
	@Shadow private boolean slim;
	
	@Inject(method = "<init>("
			+ "Lnet/minecraft/client/model/geom/ModelPart;"
			+ "Z)V", at = @At("RETURN"))
	protected void jojo_ripples$_onInitModel(ModelPart root, boolean slim, CallbackInfo ci) {
		OldPlayerModelJank._setOuterLayerBends((EntityModel<?>) this, this);
	}

	// if you ever animate skeletons, you'll need a separate mixin to SkeletonModel for the same reason
	@Inject(method = "translateToHand", at = @At("HEAD"))
	public void jojo_ripples$becauseTheyDidntCallSuper_translateToBentHandBefore(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
		if (this.jojo_ripples$playerAnim) {
			PlayerModelBends.translateToAnimHand1((HumanoidModel<?>) (Object) this, this, side, poseStack);
		}
	}

	@Inject(method = "translateToHand", at = @At("TAIL"))
	public void jojo_ripples$becauseTheyDidntCallSuper_translateToBentHandAfter(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
		if (this.jojo_ripples$playerAnim) {
			 PlayerModelBends.translateToAnimHand2((HumanoidModel<?>) (Object) this, this, side, poseStack);
		}
	}

}
