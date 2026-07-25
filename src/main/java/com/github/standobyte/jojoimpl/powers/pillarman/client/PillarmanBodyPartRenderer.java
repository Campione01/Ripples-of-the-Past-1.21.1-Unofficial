package com.github.standobyte.jojoimpl.powers.pillarman.client;

import java.util.Collections;
import java.util.EnumSet;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanExtendingBodyPartEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanHornEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanRibEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanVeinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class PillarmanBodyPartRenderer<T extends PillarmanExtendingBodyPartEntity> extends EntityRenderer<T> {
	private static final float PIXEL = 1.0F / 16.0F;
	private static final float FIRST_SEGMENT_LENGTH = 2.0F * PIXEL;
	private static final float SEGMENT_LENGTH = 8.0F * PIXEL;
	private static final float START_Z = -0.5F / 16.0F;
	private static final float END_Z = 7.5F / 16.0F;
	private static final EnumSet<Direction> ALL_FACES = EnumSet.allOf(Direction.class);

	private final ResourceLocation texture;
	private final ModelPart firstSegment;
	private final ModelPart repeatingSegment;
	private final float halfWidth;

	public static PillarmanBodyPartRenderer<PillarmanHornEntity> horn(EntityRendererProvider.Context context) {
		return new PillarmanBodyPartRenderer<>(context,
				JojoMod.resLoc("textures/entity/projectiles/pm_horn.png"),
				box(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
				box(-1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 8.0F));
	}

	public static PillarmanBodyPartRenderer<PillarmanRibEntity> rib(EntityRendererProvider.Context context) {
		return new PillarmanBodyPartRenderer<>(context,
				JojoMod.resLoc("textures/entity/projectiles/pillarman_ribs.png"),
				box(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 8.0F),
				box(-1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 8.0F));
	}

	public static PillarmanBodyPartRenderer<PillarmanVeinEntity> vein(EntityRendererProvider.Context context) {
		return new PillarmanBodyPartRenderer<>(context,
				JojoMod.resLoc("textures/entity/projectiles/pillarman_veins.png"),
				box(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 8.0F),
				box(-1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 8.0F));
	}

	public static <E extends PillarmanExtendingBodyPartEntity> PillarmanBodyPartRenderer<E> satiporojaScarf(EntityRendererProvider.Context context) {
		return new PillarmanBodyPartRenderer<>(context,
				JojoMod.resLoc("textures/entity/satiporoja_scarf.png"), 2.0F / 16.0F);
	}

	private PillarmanBodyPartRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float halfWidth) {
		super(context);
		this.texture = texture;
		this.firstSegment = null;
		this.repeatingSegment = null;
		this.halfWidth = halfWidth;
	}

	private PillarmanBodyPartRenderer(EntityRendererProvider.Context context, ResourceLocation texture,
			ModelPart firstSegment, ModelPart repeatingSegment) {
		super(context);
		this.texture = texture;
		this.firstSegment = firstSegment;
		this.repeatingSegment = repeatingSegment;
		this.halfWidth = 0.0F;
	}

	private static ModelPart box(float x, float y, float z, float xSize, float ySize, float zSize) {
		ModelPart.Cube cube = new ModelPart.Cube(
				0, 0, x, y, z, xSize, ySize, zSize,
				0.0F, 0.0F, 0.0F, false, 32.0F, 32.0F, ALL_FACES);
		return new ModelPart(Collections.singletonList(cube), Collections.emptyMap());
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return texture;
	}

	@Override
	public void render(T entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			renderRepeatingBodyPart(entity, partialTick, poseStack,
					buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),
					getOwnerPackedLight(entity, partialTick, packedLight));
		}
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	private int getOwnerPackedLight(T entity, float partialTick, int fallbackLight) {
		LivingEntity owner = entity.getOwner();
		return owner != null ? entityRenderDispatcher.getPackedLightCoords(owner, partialTick) : fallbackLight;
	}

	private void renderRepeatingBodyPart(T entity, float partialTick, PoseStack poseStack,
			VertexConsumer vertexBuilder, int packedLight) {
		Vec3 entityPos = entity.getPosition(partialTick);
		Vec3 originPos = entity.getOriginPoint(partialTick);
		Vec3 extentVec = entityPos.subtract(originPos);
		float length = (float) extentVec.length();
		if (length <= 1.0E-5F) {
			return;
		}

		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extentVec)));
		poseStack.mulPose(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extentVec)));

		if (firstSegment != null) {
			renderOriginalSegments(length, poseStack, vertexBuilder, packedLight);
		}
		else {
			float modelLength = length;
			while (modelLength >= SEGMENT_LENGTH) {
				renderSegment(poseStack, vertexBuilder, packedLight);
				modelLength -= SEGMENT_LENGTH;
				poseStack.translate(0.0F, 0.0F, SEGMENT_LENGTH);
			}
			renderSegment(poseStack, vertexBuilder, packedLight);
		}
		poseStack.popPose();
	}

	private void renderOriginalSegments(float modelLength, PoseStack poseStack,
			VertexConsumer vertexBuilder, int packedLight) {
		if (modelLength >= FIRST_SEGMENT_LENGTH) {
			firstSegment.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
			modelLength -= FIRST_SEGMENT_LENGTH;
		}
		while (modelLength >= SEGMENT_LENGTH) {
			repeatingSegment.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
			modelLength -= SEGMENT_LENGTH;
			poseStack.translate(0.0F, 0.0F, SEGMENT_LENGTH);
		}
		repeatingSegment.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
	}

	private void renderSegment(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight) {
		float x0 = -halfWidth;
		float x1 = halfWidth;
		float y0 = -halfWidth;
		float y1 = halfWidth;
		float z0 = START_Z;
		float z1 = END_Z;

		quad(poseStack, vertexBuilder, packedLight, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
		quad(poseStack, vertexBuilder, packedLight, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
		quad(poseStack, vertexBuilder, packedLight, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);
		quad(poseStack, vertexBuilder, packedLight, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0);
		quad(poseStack, vertexBuilder, packedLight, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
		quad(poseStack, vertexBuilder, packedLight, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
	}

	private static void quad(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float normalX, float normalY, float normalZ) {
		vertex(poseStack, vertexBuilder, packedLight, x0, y0, z0, 1, 1, normalX, normalY, normalZ);
		vertex(poseStack, vertexBuilder, packedLight, x1, y1, z1, 0, 1, normalX, normalY, normalZ);
		vertex(poseStack, vertexBuilder, packedLight, x2, y2, z2, 0, 0, normalX, normalY, normalZ);
		vertex(poseStack, vertexBuilder, packedLight, x3, y3, z3, 1, 0, normalX, normalY, normalZ);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight,
			float x, float y, float z, float u, float v,
			float normalX, float normalY, float normalZ) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, normalX, normalY, normalZ);
	}
}
