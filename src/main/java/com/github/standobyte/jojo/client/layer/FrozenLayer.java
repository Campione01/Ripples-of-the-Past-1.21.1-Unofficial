package com.github.standobyte.jojo.client.layer;

import java.util.Map;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.client.layer.HamonBurnLayer.TextureSize;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class FrozenLayer<T extends LivingEntity, M extends EntityModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	public static final ResourceLocation BIPED_PATH = JojoMod.resLoc("textures/entity/layer/vampire_freeze/biped");
	public static final ResourceLocation NON_BIPED_PATH = JojoMod.resLoc("textures/entity/layer/vampire_freeze");

	private final Map<TextureSize, ResourceLocation[]> layerTexturesFreeze;

	public FrozenLayer(RenderLayerParent<T, M> renderer, ResourceLocation texturesPath) {
		super(renderer);
		this.layerTexturesFreeze = HamonBurnLayer.buildTextures(texturesPath);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (entity.isInvisible()) {
			return;
		}
		ResourceLocation texture = getTexture(getParentModel(), entity);
		if (texture == null) {
			return;
		}
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(texture));
		getParentModel().renderToBuffer(poseStack, vertexBuilder, packedLight,
				LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
	}

	@Nullable
	private ResourceLocation getTexture(EntityModel<?> model, LivingEntity entity) {
		MobEffectInstance freeze = entity.getEffect(ModStatusEffects.FREEZE);
		if (freeze != null) {
			int freezelvl = Math.min(freeze.getAmplifier(), 3);
			TextureSize freezesize = TextureSize.getClosestTexSize(model);
			return layerTexturesFreeze.get(freezesize)[freezelvl];
		}
		return null;
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
			LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		if (entityRenderer.getModel() instanceof HumanoidModel<?> model) {
			FirstPersonModelLayer.defaultRender(side, poseStack, buffer, light, entity, entityRenderer,
					model, getTexture(model, entity), partialTick);
		}
	}
}
