package com.github.standobyte.jojoimpl.powers.pillarman.client;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class PillarmanStoneFormLayer<T extends LivingEntity, M extends HumanoidModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/pillarman_layer.png");

	public PillarmanStoneFormLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (livingEntity.isInvisible() || getTexture(livingEntity) == null) {
			return;
		}

		M model = getParentModel();
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
		model.renderToBuffer(poseStack, vertexBuilder, packedLight,
				LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F));
	}

	@Nullable
	public static OuterLayerVisibility captureOuterLayerVisibility(LivingEntity entity, PlayerModel<?> model) {
		if (getTexture(entity) == null) {
			return null;
		}
		return new OuterLayerVisibility(model);
	}

	public static final class OuterLayerVisibility {
		private final PlayerModel<?> model;
		private final boolean hatVisible;
		private final boolean jacketVisible;
		private final boolean leftSleeveVisible;
		private final boolean rightSleeveVisible;
		private final boolean leftPantsVisible;
		private final boolean rightPantsVisible;

		private OuterLayerVisibility(PlayerModel<?> model) {
			this.model = model;
			this.hatVisible = model.hat.visible;
			this.jacketVisible = model.jacket.visible;
			this.leftSleeveVisible = model.leftSleeve.visible;
			this.rightSleeveVisible = model.rightSleeve.visible;
			this.leftPantsVisible = model.leftPants.visible;
			this.rightPantsVisible = model.rightPants.visible;
		}

		public void hide() {
			model.hat.visible = false;
			model.jacket.visible = false;
			model.leftSleeve.visible = false;
			model.rightSleeve.visible = false;
			model.leftPants.visible = false;
			model.rightPants.visible = false;
		}

		public void restore() {
			model.hat.visible = hatVisible;
			model.jacket.visible = jacketVisible;
			model.leftSleeve.visible = leftSleeveVisible;
			model.rightSleeve.visible = rightSleeveVisible;
			model.leftPants.visible = leftPantsVisible;
			model.rightPants.visible = rightPantsVisible;
		}
	}

	@Nullable
	private static ResourceLocation getTexture(LivingEntity entity) {
		PlayerPower playerPower = PlayerPower.get(entity);
		if (playerPower != null && playerPower.getCurTypeData(PillarmanPowerType.PILLAR_MAN)
				.filter(PillarmanData::isStoneFormEnabled)
				.isPresent()) {
			return TEXTURE;
		}
		return null;
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
			LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		if (entityRenderer.getModel() instanceof HumanoidModel<?> model) {
			FirstPersonModelLayer.defaultRender(side, poseStack, buffer, light, entity, entityRenderer,
					model, getTexture(entity), partialTick);
		}
	}
}
