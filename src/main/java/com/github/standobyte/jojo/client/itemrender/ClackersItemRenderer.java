package com.github.standobyte.jojo.client.itemrender;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.entityrender.entities.ClackersModel;
import com.github.standobyte.jojo.client.itemrender.custommodel.CustomItemRenderer;
import com.github.standobyte.jojo.client.itemrender.custommodel.ISTERWithEntity;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.ClackersItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ClackersItemRenderer extends BlockEntityWithoutLevelRenderer implements ISTERWithEntity {
	private static final ResourceLocation CLACKERS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			JojoMod.MOD_ID, "textures/entity/projectiles/clackers.png");

	@Nullable
	private LivingEntity entity;
	@Nullable
	private ClackersModel clackersModel;
	private int holdTick = -1;

	public ClackersItemRenderer(Minecraft mc) {
		super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
	}

	@Override
	public void setEntity(@Nullable LivingEntity entity) {
		this.entity = entity;
	}

	@Override
	public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource buffer, int light, int overlay) {
		if (displayContext.firstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
			renderInHand(itemStack, displayContext, poseStack, buffer, light, overlay);
			return;
		}

		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		BakedModel model = itemRenderer.getModel(itemStack, null, entity, 0);
		CustomItemRenderer.renderItemNormally(poseStack, itemStack, displayContext, buffer, light, overlay, model);
	}

	private void renderInHand(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource buffer, int light, int overlay) {
		ClackersModel model = getClackersModel();
		boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();

		setupModel(itemStack, model, leftHand, partialTick);

		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		if (displayContext.firstPerson()) {
			poseStack.translate(leftHand ? -0.1F : 0.1F, -0.2F, -0.05F);
			poseStack.scale(0.85F, 0.85F, 0.85F);
		}
		else {
			poseStack.translate(0.0F, -0.35F, 0.0F);
		}

		VertexConsumer vertex = ItemRenderer.getFoilBufferDirect(
				buffer, RenderType.entityCutout(CLACKERS_TEXTURE), false, itemStack.hasFoil());
		model.renderToBuffer(poseStack, vertex, light, overlay, 0xFFFFFFFF);
		poseStack.popPose();
	}

	private ClackersModel getClackersModel() {
		if (clackersModel == null) {
			clackersModel = new ClackersModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModEntityTypeRenderers.CLACKERS));
		}
		return clackersModel;
	}

	private void setupModel(ItemStack itemStack, ClackersModel model, boolean leftHand, float partialTick) {
		model.resetPose();
		if (entity != null && entity.isUsingItem() && entity.getUseItem().is(itemStack.getItem())) {
			setupUsingAnimation(model, itemStack, partialTick);
		}
		else {
			setupIdleAnimation(model, leftHand, partialTick);
		}
	}

	private void setupUsingAnimation(ClackersModel model, ItemStack itemStack, float partialTick) {
		int useTicks = itemStack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
		float angle;
		boolean clack;

		if (useTicks > ClackersItem.TICKS_MAX_POWER) {
			float loopLen = 2.0F;
			float loopPos = (useTicks % loopLen + partialTick) / loopLen;
			loopPos = 1.0F - loopPos;
			loopPos *= loopPos;
			loopPos = 1.0F - loopPos;
			if (useTicks % (loopLen * 2.0F) < loopLen) {
				loopPos = 1.0F - loopPos;
			}

			angle = (float) Math.PI * ((1.0F - loopPos) * 7.0F / 8.0F + 1.0F / 16.0F);
			clack = holdTick != useTicks && useTicks % loopLen == 0.0F;
		}
		else {
			float loopLen = 5.0F;
			float loopPos = (useTicks % loopLen + partialTick) / loopLen;
			loopPos = loopPos < 0.5F ? loopPos * 2.0F : 2.0F - loopPos * 2.0F;
			loopPos = 1.0F - loopPos;
			loopPos *= loopPos;
			loopPos = 1.0F - loopPos;

			float amplitude = (float) Math.PI * (1.0F / 6.0F + 1.0F / 12.0F * (useTicks / loopLen) - 1.0F / 16.0F);
			angle = amplitude * loopPos + (float) Math.PI / 16.0F;
			clack = holdTick != useTicks && useTicks % loopLen == 0.0F;
		}

		holdTick = useTicks;
		model.setStringAngles(0.0F, 0.0F, angle, 0.0F, 0.0F, (float) Math.PI - angle);
		if (clack && entity.level().isClientSide()) {
			ClackersItem.playClackSound(entity.level(), entity);
		}
	}

	private void setupIdleAnimation(ClackersModel model, boolean leftHand, float partialTick) {
		float limbSwing = 0.0F;
		float limbSwingAmount = 0.0F;
		if (entity != null && entity.isAlive()) {
			limbSwing = entity.walkAnimation.position(partialTick);
			limbSwingAmount = Math.min(entity.walkAnimation.speed(partialTick), 1.0F);
			if (entity.isBaby()) {
				limbSwing *= 3.0F;
			}
		}

		float xRotAdd = Mth.cos(limbSwing * 0.6664F + (float) Math.PI) * limbSwingAmount;
		if (leftHand) {
			xRotAdd *= -1.0F;
		}
		float xRot1 = (float) Math.PI / 2.0F + (float) Math.PI / 32.0F + xRotAdd;
		float xRot2 = -((float) Math.PI / 2.0F + (float) Math.PI / 32.0F) + xRotAdd;
		model.setStringAngles(xRot1, (float) Math.PI / 16.0F, 0.0F, xRot2, -(float) Math.PI / 16.0F, 0.0F);

		ModelPart clackers = model.getMainPart();
		clackers.xRot = xRotAdd;
	}
}
