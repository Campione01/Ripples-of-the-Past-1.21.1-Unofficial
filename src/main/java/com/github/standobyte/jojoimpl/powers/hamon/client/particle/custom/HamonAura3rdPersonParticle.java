package com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom;

import org.joml.Vector3f;

import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityanim.pose.AnimatedEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.client.particle.HamonAuraParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class HamonAura3rdPersonParticle extends HamonAuraParticle {
	private static final String BODY_BONE = "body";
	private static final double MODEL_UNIT = 1.0D / 16.0D;
	private final LivingEntity user;
	private final AbstractClientPlayer userAsPlayer;
	private Vec3 userPositionPrev;

	protected HamonAura3rdPersonParticle(ClientLevel level, LivingEntity entity, 
			double x, double y, double z, double xda, double yda, double zda,
			SpriteSet sprites) {
		super(level, x, y, z, xda, yda, zda, sprites);
		this.user = entity;
		this.userPositionPrev = entity.position();
		this.userAsPlayer = user instanceof AbstractClientPlayer player ? player : null;
	}

	@Override
	public void render(VertexConsumer vertexBuilder, Camera camera, float partialTick) {
		if (!ClientModSettings.getSettingsReadOnly().thirdPersonHamonAura) return;

		if (user != null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.cameraEntity == user && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
				return;
			}
		}

		Vec3 playerAnimPos = userAsPlayer != null ? getBodyPos(userAsPlayer, partialTick) : Vec3.ZERO;
		x += playerAnimPos.x;
		y += playerAnimPos.y;
		z += playerAnimPos.z;
		xo += playerAnimPos.x;
		yo += playerAnimPos.y;
		zo += playerAnimPos.z;

		super.render(vertexBuilder, camera, partialTick);

		x -= playerAnimPos.x;
		y -= playerAnimPos.y;
		z -= playerAnimPos.z;
		xo -= playerAnimPos.x;
		yo -= playerAnimPos.y;
		zo -= playerAnimPos.z;
	}
	
	public static Vec3 getBodyPos(AbstractClientPlayer player, float partialTick) {
		AnimFramePose pose = ((AnimatedEntity) player).jojo_ripples$getModelPose(AnimatedEntity.PoseType.FINAL);
		if (pose == null) {
			return Vec3.ZERO;
		}
		AnimFramePose.ModelPartFrame bodyPose = pose.getIfPresent(BODY_BONE);
		if (bodyPose == null) {
			return Vec3.ZERO;
		}
		Vector3f bodyOffset = bodyPose.positionOffset;
		if (bodyOffset.lengthSquared() == 0.0F) {
			return Vec3.ZERO;
		}
		Vec3 modelOffset = new Vec3(
				bodyOffset.x * MODEL_UNIT,
				-bodyOffset.y * MODEL_UNIT,
				-bodyOffset.z * MODEL_UNIT);
		float yRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
		return modelOffset.yRot(-yRot * MathUtil.DEG_TO_RAD);
	}

	@Override
	public void tick() {
		super.tick();
		if (user != null) {
			Vec3 offset = user.position().subtract(userPositionPrev);
			move(offset.x, offset.y, offset.z);
			this.userPositionPrev = user.position();
		}
	}


	public static HamonAuraParticle createCustomParticle(SpriteSet sprites, ClientLevel level, 
			LivingEntity entity, double x, double y, double z) {
		HamonAuraParticle particle = new HamonAura3rdPersonParticle(level, entity, x, y, z, 0, 0, 0, sprites);
		return particle;
	}
}
