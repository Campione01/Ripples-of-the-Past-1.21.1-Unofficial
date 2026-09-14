package rotp.core.impl.powers.vampirism.client;

import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class SRSEEyesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/srse_eyes.png");
	private static final String SRSE_ABILITY = "vampirism_space_ripper_stingy_eyes";

	public SRSEEyesLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (livingEntity.isInvisible() || !eyesEnabled(livingEntity)) {
			return;
		}

		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
		getParentModel().renderToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT,
				LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F));
	}

	private static boolean eyesEnabled(LivingEntity entity) {
		if (entity instanceof Player player && PlayerClientBroadcastedSettings.getPlayerSettings(player)
				.map(settings -> !settings.vampireGlowingEyes)
				.orElse(false)) {
			return false;
		}

		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		return action != null
				&& action.ability instanceof Ability ability
				&& SRSE_ABILITY.equals(ability.getAbilityId().nameInMoveset());
	}
}
