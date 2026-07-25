package com.github.standobyte.jojo.client.layer;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.Util;
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

public class HamonBurnLayer<T extends LivingEntity, M extends EntityModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final Map<TextureSize, ResourceLocation[]> LAYER_TEXTURES = buildTextures(
			JojoMod.resLoc("textures/entity/layer/hamon_burn"));

	public HamonBurnLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
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
		getParentModel().renderToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT,
				LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
	}

	@Nullable
	private static ResourceLocation getTexture(EntityModel<?> model, LivingEntity entity) {
		MobEffectInstance hamonSpread = entity.getEffect(ModStatusEffects.HAMON_SPREAD);
		if (hamonSpread != null) {
			int lvl = Math.min(hamonSpread.getAmplifier(), 3);
			TextureSize size = TextureSize.getClosestTexSize(model);
			return LAYER_TEXTURES.get(size)[lvl];
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

	static Map<TextureSize, ResourceLocation[]> buildTextures(ResourceLocation texturesPath) {
		return Util.make(new EnumMap<>(TextureSize.class), map -> {
			for (TextureSize size : TextureSize.values()) {
				map.put(size, new ResourceLocation[] {
						layerTexture(texturesPath, size.path, 1),
						layerTexture(texturesPath, size.path, 2),
						layerTexture(texturesPath, size.path, 3),
						layerTexture(texturesPath, size.path, 4)
				});
			}
		});
	}

	static ResourceLocation layerTexture(ResourceLocation texturesPath, String sizePath, int level) {
		return ResourceLocation.fromNamespaceAndPath(texturesPath.getNamespace(),
				texturesPath.getPath() + "/" + sizePath + "/" + level + ".png");
	}

	public static enum TextureSize {
		_64x32("t64x32"),
		_64x64("t64x64"),
		_128x64("t128x64"),
		_128x128("t128x128"),
		_256x128("t256x128"),
		_256x256("t256x256");

		private final String path;

		private TextureSize(String path) {
			this.path = path;
		}

		public static TextureSize getClosestTexSize(EntityModel<?> model) {
			return _64x64;
		}
	}
}
