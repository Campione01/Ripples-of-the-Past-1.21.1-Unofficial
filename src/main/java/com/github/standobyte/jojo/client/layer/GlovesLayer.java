package com.github.standobyte.jojo.client.layer;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.item.GlovesItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class GlovesLayer<T extends LivingEntity, M extends HumanoidModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final CubeDeformation MODEL_DEFORMATION = new CubeDeformation(0.3F);

	private final PlayerModel<T> glovesModel;
	private final boolean slim;

	public GlovesLayer(RenderLayerParent<T, M> renderer, boolean slim) {
		super(renderer);
		this.glovesModel = new PlayerModel<>(LayerDefinition.create(
				PlayerModel.createMesh(MODEL_DEFORMATION, slim), 64, 64).bakeRoot(), slim);
		this.slim = slim;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		ItemStack glovesItemStack = getRenderedGlovesItem(entity);
		if (glovesItemStack.isEmpty()) {
			return;
		}

		glovesModel.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
		getParentModel().copyPropertiesTo(glovesModel);
		glovesModel.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		syncArmVisibility();

		VertexConsumer vertexBuilder = foilBuffer(bufferSource, glovesItemStack);
		glovesModel.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
	}

	private void syncArmVisibility() {
		glovesModel.leftArm.visible = getParentModel().leftArm.visible;
		glovesModel.leftSleeve.visible = getParentModel().leftArm.visible;
		glovesModel.rightArm.visible = getParentModel().rightArm.visible;
		glovesModel.rightSleeve.visible = getParentModel().rightArm.visible;
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer,
			int light, LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		ItemStack glovesItemStack = getRenderedGlovesItem(entity);
		if (glovesItemStack.isEmpty()) {
			return;
		}

		FirstPersonModelLayer.setupForFirstPersonRender(glovesModel, entity, partialTick);
		FirstPersonModelLayer.renderArmAndOuter(glovesModel, side, poseStack,
				foilBuffer(buffer, glovesItemStack), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
				FirstPersonModelLayer.isRipplesAnimPlaying(glovesModel));
	}

	private VertexConsumer foilBuffer(MultiBufferSource buffer, ItemStack glovesItemStack) {
		ResourceLocation texture = getTexture(glovesItemStack);
		return ItemRenderer.getFoilBuffer(buffer, RenderType.armorCutoutNoCull(texture),
				false, glovesItemStack.hasFoil());
	}

	private ResourceLocation getTexture(ItemStack glovesItemStack) {
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(glovesItemStack.getItem());
		return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(),
				"textures/entity/layer/" + itemId.getPath() + (slim ? "_slim" : "") + ".png");
	}

	public static ItemStack getRenderedGlovesItem(LivingEntity entity) {
		ItemStack checkedItem = entity.getMainHandItem();
		if (areGloves(checkedItem)) {
			return checkedItem;
		}
		checkedItem = entity.getOffhandItem();
		if (areGloves(checkedItem)) {
			return checkedItem;
		}
		return ItemStack.EMPTY;
	}

	public static boolean areGloves(ItemStack itemStack) {
		return !itemStack.isEmpty() && itemStack.getItem() instanceof GlovesItem;
	}
}
