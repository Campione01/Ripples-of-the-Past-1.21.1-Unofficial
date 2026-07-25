package com.github.standobyte.jojoimpl.stands.magiciansred.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.client.entityrender.stand.StandOpacityPolicy;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRRedBindEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class MRRedBindRenderer extends EntityRenderer<MRRedBindEntity> {
	private static final ResourceLocation MR_FIRE_0 = JojoMod.resLoc("block/mr_fire_0");
	private static final ResourceLocation MR_FIRE_1 = JojoMod.resLoc("block/mr_fire_1");
	private static final float REPEATING_PART_LENGTH = 3F / 16F;
	private static final List<Flame> ROPE_FLAMES = new ArrayList<>();

	private static final Vec3 bodyWindup = scaleDegrees(-49.1066F, -20.7048F, 22.2077F);
	private static final Vec3 leftArmWindup = new Vec3(0.3054F, 0.0F, -0.2182F);
	private static final Vec3 leftForeArmWindup = new Vec3(-2.0944F, -0.2618F, 1.0472F);
	private static final Vec3 rightArmWindup = new Vec3(0.1309F, 0.0F, 0.4363F);
	private static final Vec3 rightForeArmWindup = new Vec3(-2.3562F, 0.2618F, -1.8326F);

	private static final Vec3 bodyKickStart = scaleDegrees(-54.7356F, -30F, 35.2644F);
	private static final Vec3 leftArmKickStart = scaleDegrees(-60F, 0.0F, -45F);
	private static final Vec3 leftForeArmKickStart = scaleDegrees(0.0F, 0.0F, 50F);
	private static final Vec3 rightArmKickStart = scaleDegrees(45F, -10F, 10F);
	private static final Vec3 rightForeArmKickStart = scaleDegrees(0F, 0F, 0F);

	private static final Vec3 bodyKickEnd = scaleDegrees(-59.3179F, -27.034F, 37.4537F);
	private static final Vec3 leftArmKickEnd = scaleDegrees(-135F, -15F, 30F);
	private static final Vec3 leftForeArmKickEnd = scaleDegrees(0.0F, 0.0F, 50F);
	private static final Vec3 rightArmKickEnd = scaleDegrees(-180F, 30F, -45F);
	private static final Vec3 rightForeArmKickEnd = scaleDegrees(0F, 0F, -45F);

	private static final Map<ModelPart, Vec3[]> ROTATIONS = Map.of(
			ModelPart.BODY, new Vec3[] { bodyWindup, bodyKickStart, bodyKickEnd },
			ModelPart.LEFT_ARM, new Vec3[] { leftArmWindup, leftArmKickStart, leftArmKickEnd },
			ModelPart.LEFT_FOREARM, new Vec3[] { leftForeArmWindup, leftForeArmKickStart, leftForeArmKickEnd },
			ModelPart.RIGHT_ARM, new Vec3[] { rightArmWindup, rightArmKickStart, rightArmKickEnd },
			ModelPart.RIGHT_FOREARM, new Vec3[] { rightForeArmWindup, rightForeArmKickStart, rightForeArmKickEnd });

	static {
		addFlame(0, 1.0F, 0, 2F, 3F, Direction.NORTH);
	}

	public MRRedBindRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(MRRedBindEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}

	@Override
	public void render(MRRedBindEntity entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("mr_red_bind_render_gate", entity, "MR Red Bind renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			int flameLight = ClientUtil.MAX_LIGHT;
			float alpha = getAlpha(entity, partialTick);
			VisualPipelineDiagnostics.logOnce("mr_red_bind_render",
					"MR Red Bind renderer reached: entityId={}, alpha={}, kickAttack={}, pos={}.",
					entity.getId(), alpha, entity.isInKickAttack(), entity.position());
			renderRedBind(entity, partialTick, poseStack, buffer, false, flameLight, alpha);
			if (entity.isInKickAttack()) {
				renderRedBind(entity, partialTick, poseStack, buffer, true, flameLight, alpha);
			}
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	private float getAlpha(MRRedBindEntity entity, float partialTick) {
		StandEntity stand = entity.getOwner() instanceof StandEntity ownerStand ? ownerStand : null;
		float lifecycleAlpha = 1.0F;
		if (entity.standDamage() && stand != null) {
			float modelAlpha = stand.modelAlpha.lerp(partialTick);
			float rangeAlpha = (float) stand.rangeEfficiency * modelAlpha;
			lifecycleAlpha = Mth.clamp(Math.max(rangeAlpha, modelAlpha * 0.15F), 0.0F, 1.0F);
		}
		return StandOpacityPolicy.apply(lifecycleAlpha, stand != null ? stand.getUser() : null);
	}

	private void renderRedBind(MRRedBindEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			boolean second, int packedLight, float alpha) {
		Vec3 entityPos = entity.getPosition(partialTick);
		Vec3 originPos = getOriginPos(entity, partialTick, second);
		Vec3 extentVec = entityPos.subtract(originPos);
		float length = (float) extentVec.length();
		if (length <= 1.0E-5F) {
			return;
		}

		poseStack.pushPose();
		Vec3 originRelativeToEntity = originPos.subtract(entityPos);
		poseStack.translate(originRelativeToEntity.x, originRelativeToEntity.y, originRelativeToEntity.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extentVec)));
		poseStack.mulPose(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extentVec)));
		RenderType renderType = alpha > 0.0F
				? ModRenderTypes.standTranslucent(InventoryMenu.BLOCK_ATLAS)
				: buffer instanceof OutlineBufferSource ? RenderType.outline(InventoryMenu.BLOCK_ATLAS) : null;
		if (renderType != null) {
			renderRepeatingRope(length, poseStack, buffer.getBuffer(renderType), packedLight, alpha);
		}
		poseStack.popPose();
	}

	private Vec3 getOriginPos(MRRedBindEntity entity, float partialTick, boolean second) {
		Vec3 originPos = entity.getOriginPoint(partialTick);
		if (entity.isInKickAttack() && entity.getOwner() instanceof StandEntity stand) {
			EntityActionInstance action = stand.getCurStandAction();
			if (action != null) {
				ActionPhase phase = action.getPhase();
				float anim = action.getCurPhaseLength() > 0 ? action.getPhaseRatio() : 1.0F;
				originPos = stand.getPosition(partialTick).add(0, stand.getBbHeight() * 0.75F, 0);
				Vec3 shoulder = new Vec3(0, -0.234375, 0);
				Vec3 foreArm = new Vec3(0, -0.234375, 0);
				float yRot = 180F - Mth.lerp(partialTick, stand.yRotO, stand.getYRot());
				if (second) {
					Vec3 posArmLeft = new Vec3(-0.3515625, -0.1171875, 0);
					posArmLeft = rotateVec(posArmLeft, getPartRotation(ModelPart.BODY, phase, anim), true);
					shoulder = rotateVec(shoulder, getPartRotation(ModelPart.BODY, phase, anim), true);
					shoulder = rotateVec(shoulder, getPartRotation(ModelPart.LEFT_ARM, phase, anim), true);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.BODY, phase, anim), true);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.LEFT_ARM, phase, anim), true);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.LEFT_FOREARM, phase, anim), true);
					originPos = originPos.add(posArmLeft.add(shoulder).add(foreArm).yRot(yRot * MathUtil.DEG_TO_RAD));
				}
				else {
					Vec3 posArmRight = new Vec3(0.3515625, -0.1171875, 0);
					posArmRight = rotateVec(posArmRight, getPartRotation(ModelPart.BODY, phase, anim), false);
					shoulder = rotateVec(shoulder, getPartRotation(ModelPart.BODY, phase, anim), false);
					shoulder = rotateVec(shoulder, getPartRotation(ModelPart.RIGHT_ARM, phase, anim), false);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.BODY, phase, anim), false);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.RIGHT_ARM, phase, anim), false);
					foreArm = rotateVec(foreArm, getPartRotation(ModelPart.RIGHT_FOREARM, phase, anim), false);
					originPos = originPos.add(posArmRight.add(shoulder).add(foreArm).yRot(yRot * MathUtil.DEG_TO_RAD));
				}
			}
		}
		return originPos;
	}

	private static Vec3 getPartRotation(ModelPart part, ActionPhase phase, float completion) {
		Vec3[] partRotations = ROTATIONS.get(part);
		return switch (phase) {
		case WINDUP -> lerp(partRotations[0], partRotations[1], completion);
		case PERFORM -> lerp(partRotations[1], partRotations[2], completion);
		case RECOVERY -> partRotations[2];
		default -> Vec3.ZERO;
		};
	}

	private static Vec3 lerp(Vec3 from, Vec3 to, float completion) {
		return new Vec3(
				Mth.lerp(completion, from.x, to.x),
				Mth.lerp(completion, from.y, to.y),
				Mth.lerp(completion, from.z, to.z));
	}

	private static Vec3 rotateVec(Vec3 vec, Vec3 rotations, boolean leftSide) {
		return vec.zRot((float) (leftSide ? rotations.z : -rotations.z))
				.yRot((float) -rotations.y)
				.xRot((float) rotations.x);
	}

	private static Vec3 scaleDegrees(float x, float y, float z) {
		return new Vec3(x * MathUtil.DEG_TO_RAD, y * MathUtil.DEG_TO_RAD, z * MathUtil.DEG_TO_RAD);
	}

	private static void renderRepeatingRope(float length, PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, float alpha) {
		TextureAtlasSprite fire0 = fireSprite(MR_FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(MR_FIRE_1);
		float modelLength = length;
		while (modelLength >= REPEATING_PART_LENGTH) {
			renderRopeSegment(poseStack, vertexBuilder, fire0, fire1, packedLight, alpha);
			modelLength -= REPEATING_PART_LENGTH;
			poseStack.translate(0, 0, REPEATING_PART_LENGTH);
		}
		renderRopeSegment(poseStack, vertexBuilder, fire0, fire1, packedLight, alpha);
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void addFlame(float x, float y, float z, float width, float height, Direction flameDirection) {
		ROPE_FLAMES.add(new Flame(x, y, z, width, height, flameDirection));
	}

	private static void renderRopeSegment(PoseStack poseStack, VertexConsumer vertexBuilder, TextureAtlasSprite fire0,
			TextureAtlasSprite fire1, int packedLight, float alpha) {
		for (Flame flame : ROPE_FLAMES) {
			renderFlame(poseStack, vertexBuilder, flame, fire0, fire1, packedLight, alpha);
		}
	}

	private static void renderFlame(PoseStack poseStack, VertexConsumer vertexBuilder, Flame flame, TextureAtlasSprite fire0,
			TextureAtlasSprite fire1, int packedLight, float alpha) {
		poseStack.pushPose();
		poseStack.translate(flame.x, flame.y, flame.z);
		switch (flame.flameDirection) {
		case UP:
			poseStack.mulPose(Axis.XP.rotationDegrees(180));
			break;
		case NORTH:
			poseStack.mulPose(Axis.XN.rotationDegrees(90));
			break;
		case EAST:
			poseStack.mulPose(Axis.ZP.rotationDegrees(90));
			break;
		case SOUTH:
			poseStack.mulPose(Axis.XP.rotationDegrees(90));
			break;
		case WEST:
			poseStack.mulPose(Axis.ZN.rotationDegrees(90));
			break;
		case DOWN:
		default:
			break;
		}

		for (int yRot = 0; yRot < 4; yRot++) {
			poseStack.pushPose();
			float flameScale = flame.width * 1.4F;
			poseStack.scale(flameScale, flameScale, flameScale);
			float xHalfWidth = 0.5F;
			float xOffset = 0.0F;
			float heightLeft = flame.height / flameScale;
			float yOffset = 0.0F;
			poseStack.mulPose(Axis.YP.rotation(yRot * Mth.HALF_PI));
			poseStack.translate(0.0D, 0.0D, -0.4F + (int) heightLeft * 0.02F);
			float zOffset = 0.0F;
			for (int i = 0; heightLeft > 0.0F; i++) {
				TextureAtlasSprite sprite = i % 2 == 0 ? fire0 : fire1;
				float texU0 = sprite.getU0();
				float texV0 = sprite.getV0();
				float texU1 = sprite.getU1();
				float texV1 = sprite.getV1();
				if (i / 2 % 2 == 0) {
					float tmp = texU1;
					texU1 = texU0;
					texU0 = tmp;
				}
				vertex(poseStack, vertexBuilder, packedLight, alpha, xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU1, texV1);
				vertex(poseStack, vertexBuilder, packedLight, alpha, -xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU0, texV1);
				vertex(poseStack, vertexBuilder, packedLight, alpha, -xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU0, texV0);
				vertex(poseStack, vertexBuilder, packedLight, alpha, xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU1, texV0);
				heightLeft -= 0.45F;
				yOffset -= 0.45F;
				xHalfWidth *= 0.9F;
				zOffset += 0.03F;
			}
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, float alpha,
			float x, float y, float z, float u, float v) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(255, 255, 255, Mth.clamp((int) (alpha * 255.0F), 0, 255))
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, 0, 1, 0);
	}

	private enum ModelPart {
		BODY,
		LEFT_ARM,
		LEFT_FOREARM,
		RIGHT_ARM,
		RIGHT_FOREARM
	}

	private static class Flame {
		private final double x;
		private final double y;
		private final double z;
		private final float width;
		private final float height;
		private final Direction flameDirection;

		private Flame(float x, float y, float z, float width, float height, Direction flameDirection) {
			this.x = x / 16F;
			this.y = y / 16F;
			this.z = z / 16F;
			this.width = width / 16F;
			this.height = height / 16F;
			this.flameDirection = flameDirection;
		}
	}
}
