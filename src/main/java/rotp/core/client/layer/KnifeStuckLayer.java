package rotp.core.client.layer;

import rotp.core.client.layer.StuckProjectiles.Type;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 1.16 KnifeLayer: knives stuck in a player, placed like vanilla stuck arrows.
public class KnifeStuckLayer<T extends LivingEntity, M extends PlayerModel<T>> extends StuckInBodyLayer<T, M> {

	public KnifeStuckLayer(LivingEntityRenderer<T, M> renderer) {
		super(renderer);
	}

	@Override
	protected int numStuck(T entity) {
		return StuckProjectiles.numStuck(Type.KNIFE, entity);
	}

	@Override
	protected void renderStuckItem(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			Entity entity, float x, float y, float z, float partialTick) {
		MobStuckArrowLayer.renderProjectile(Type.KNIFE, poseStack, buffer, packedLight, entity, x, y, z, partialTick);
	}
}
