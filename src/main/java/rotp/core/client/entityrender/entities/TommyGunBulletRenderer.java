package rotp.core.client.entityrender.entities;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TommyGunBulletRenderer extends EntityRenderer<TommyGunBulletEntity> {
	private static final ResourceLocation TRAIL_TEX = JojoMod.resLoc("textures/entity/projectiles/bullet_trace.png");
	private static final double MAX_TRAIL_LEN = 4.0D;
	private static final float BEAM_WIDTH = 0.015F;
	private static final double BULLET_U = 0.015625D;

	public TommyGunBulletRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(TommyGunBulletEntity entity) {
		return TRAIL_TEX;
	}

	// 1.16: the trail stays drawn after the bullet's own box has left the screen
	@Override
	public boolean shouldRender(TommyGunBulletEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		return super.shouldRender(entity, frustum, camX, camY, camZ)
				|| entity.initialPos != null && frustum.isVisible(new AABB(entity.initialPos, entity.position()));
	}

	@Override
	public void render(TommyGunBulletEntity entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		List<Vec3> trace = entity.tracePos;
		if (trace.isEmpty()) {
			trace = new ArrayList<>();
			Vec3 pos = entity.position();
			Vec3 movement = entity.getDeltaMovement();
			Vec3 start = movement.lengthSqr() > 1.0E-7D
					? pos.subtract(movement.normalize().scale(MAX_TRAIL_LEN * BULLET_U))
					: pos;
			trace.add(start);
			trace.add(pos);
		}

		poseStack.pushPose();
		poseStack.translate(0.0D, entity.getBbHeight() / 2.0D, 0.0D);
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(entity)));
		double traceLen = MAX_TRAIL_LEN;
		boolean first = true;
		for (int i = trace.size() - 1; i > 0 && traceLen > 0.0D; i--) {
			Vec3 posCur = trace.get(i);
			Vec3 posPrev = trace.get(i - 1);
			float u1 = (float) (traceLen / MAX_TRAIL_LEN);
			Vec3 diffBack = posPrev.subtract(posCur);
			double len = diffBack.length();

			double bulletStart = MAX_TRAIL_LEN * (1.0D - BULLET_U);
			if (i == 1 && len < traceLen - bulletStart && len > 1.0E-7D) {
				double ratio = (traceLen - bulletStart) / len;
				len = traceLen - bulletStart;
				posPrev = posCur.add(diffBack.scale(ratio));
			}
			if (len > traceLen && len > 1.0E-7D) {
				posPrev = posCur.add(diffBack.normalize().scale(traceLen));
				traceLen = 0.0D;
			}
			else {
				traceLen -= len;
			}
			float u0 = (float) (traceLen / MAX_TRAIL_LEN);
			trailSegment(posPrev, posCur, u0, u1, poseStack, vertexBuilder, first);
			first = false;
		}
		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	private static void trailSegment(Vec3 pos1, Vec3 pos2, float u0, float u1, PoseStack poseStack,
			VertexConsumer vertexBuilder, boolean first) {
		poseStack.pushPose();
		Vec3 trailSegmentVec = pos1.subtract(pos2);
		if (trailSegmentVec.lengthSqr() < 1.0E-7D) {
			poseStack.popPose();
			return;
		}
		float yRot = MathUtil.yRotDegFromVec(trailSegmentVec);
		float xRot = MathUtil.xRotDegFromVec(trailSegmentVec);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F - yRot));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-xRot));
		poseStack.scale(1.0F, BEAM_WIDTH, BEAM_WIDTH);
		PoseStack.Pose pose = poseStack.last();
		// 1.16 lit every trail vertex as world-up, so the normal is not turned by the segment's pose
		TommyGunTrailShell.emit((float) trailSegmentVec.length(), u0, u1, first, (x, y, z, u, v) ->
				vertexBuilder.addVertex(pose.pose(), x, y, z)
						.setColor(0xFFFFFFFF)
						.setUv(u, v)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(ClientUtil.MAX_LIGHT)
						.setNormal(0.0F, 1.0F, 0.0F));
		poseStack.popPose();
		poseStack.translate(trailSegmentVec.x, trailSegmentVec.y, trailSegmentVec.z);
	}
}
