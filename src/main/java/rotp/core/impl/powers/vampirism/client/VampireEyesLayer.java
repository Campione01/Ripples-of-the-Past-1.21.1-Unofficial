package rotp.core.impl.powers.vampirism.client;

import rotp.core.client.util.functions.ClientUtil;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.impl.powers.zombie.ZombiePowerType;
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

public class VampireEyesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/vampire_eyes.png");

	public VampireEyesLayer(RenderLayerParent<T, M> renderer) {
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

		PlayerPower playerPower = PlayerPower.get(entity);
		if (playerPower == null) {
			return false;
		}

		return playerPower.getCurTypeData(VampirismPowerType.VAMPIRISM)
				.map(vampirism -> VampirismPowerType.VAMPIRISM.get().isHighOnBlood(entity))
				.orElse(false)
				|| playerPower.getCurTypeData(ZombiePowerType.ZOMBIE)
						.map(ZombieData::isDisguiseEnabled)
						.orElse(false);
	}
}
