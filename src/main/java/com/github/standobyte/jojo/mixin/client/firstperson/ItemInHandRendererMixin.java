package com.github.standobyte.jojo.mixin.client.firstperson;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.firstperson.FirstPersonRender;
import com.github.standobyte.jojo.client.firstperson.FirstPersonItemRendererAccess;
import com.github.standobyte.jojo.client.polaroid.PolaroidHelper;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojoimpl.powers.hamon.HamonZoomPunchState;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin
		implements FirstPersonItemRendererAccess {
	@Shadow @Final private Minecraft minecraft;
	@Shadow private float mainHandHeight;
	@Shadow private float oMainHandHeight;
	@Shadow private float offHandHeight;
	@Shadow private float oOffHandHeight;
	@Shadow private ItemStack offHandItem;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void jojo_ripples$on1stPersonRendererInit(CallbackInfo ci) {
		FirstPersonRender.init((ItemInHandRenderer) (Object) this);
	}

	@Override
	public float jojo_ripples$getEquipProgress(
			InteractionHand hand,
			float partialTick) {
		return hand == InteractionHand.MAIN_HAND
				? 1.0F - Mth.lerp(
						partialTick,
						oMainHandHeight,
						mainHandHeight)
				: 1.0F - Mth.lerp(
						partialTick,
						oOffHandHeight,
						offHandHeight);
	}

	@Override
	public boolean jojo_ripples$vanillaRendersBothMapArms(
			ItemStack mainHandItem) {
		return mainHandItem.getItem() instanceof MapItem
				&& offHandItem.isEmpty();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	public void jojo_ripples$on1stPersonRendererTick(CallbackInfo ci) {
		FirstPersonRender.getInstance().tick();
	}

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	public void jojo_ripples$renderControlledEntityHand(float partialTicks, PoseStack poseStack, BufferSource buffer, 
			LocalPlayer playerEntity, int combinedLight, CallbackInfo ci) {
		if (FirstPersonRender.onFirstPersonRender(minecraft, partialTicks, poseStack, buffer, combinedLight)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	public void jojo_ripples$renderPhotoInHand(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer,
			int combinedLight, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && HamonZoomPunchState.isUsingZoomPunch(player)) {
			ci.cancel();
			return;
		}
		if (stack.is(ModItems.PHOTO.get())) {
			HumanoidArm handSide = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			PolaroidHelper.renderPhotoInHand(player, poseStack, buffer, combinedLight, equippedProgress, handSide, swingProgress, stack, partialTicks);
			ci.cancel();
		}
	}
}
