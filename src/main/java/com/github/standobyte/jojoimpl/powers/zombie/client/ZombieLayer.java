package com.github.standobyte.jojoimpl.powers.zombie.client;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.zombie.ZombiePowerType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class ZombieLayer<T extends LivingEntity, M extends HumanoidModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/sated_zombie.png");

	public ZombieLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (livingEntity.isInvisible() || getTexture(livingEntity) == null) {
			return;
		}

		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
		getParentModel().renderToBuffer(poseStack, vertexBuilder, packedLight,
				LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F));
	}

	@Nullable
	private static ResourceLocation getTexture(LivingEntity entity) {
		PlayerPower playerPower = PlayerPower.get(entity);
		return playerPower != null && playerPower.getCurTypeData(ZombiePowerType.ZOMBIE)
				.filter(zombie -> !zombie.isDisguiseEnabled())
				.isPresent()
				? TEXTURE : null;
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
