package com.github.standobyte.jojoimpl.stands.magiciansred.client;

import static com.github.standobyte.jojoimpl.stands.magiciansred.MRDetectorEntity.DETECTION_RADIUS;

import java.util.EnumMap;
import java.util.Map;

import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRDetectorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class MRDetectorModel extends EntityModel<MRDetectorEntity> {
	private static final ResourceLocation MR_FIRE_0 = JojoMod.resLoc("block/mr_fire_0");
	private static final ResourceLocation MR_FIRE_1 = JojoMod.resLoc("block/mr_fire_1");

	private final ModelPart detector;
	private final Map<Direction, Float> flamesStrength = new EnumMap<>(Direction.class);

	public MRDetectorModel() {
		super(RenderType::entityCutout);
		this.detector = createDetectorLayer().bakeRoot().getChild("detector");
		for (Direction direction : Direction.values()) {
			flamesStrength.put(direction, -1.0F);
		}
	}

	private static LayerDefinition createDetectorLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("detector", CubeListBuilder.create()
				.texOffs(0, 9).addBox(-4.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F))
				.texOffs(0, 11).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 8.0F, 1.0F, new CubeDeformation(-0.2F))
				.texOffs(0, 0).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(-0.2F)),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(MRDetectorEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		if (entity.isEntityDetected()) {
			Vec3 directionVec = entity.getDetectedDirection();
			for (Map.Entry<Direction, Float> entry : flamesStrength.entrySet()) {
				Direction direction = entry.getKey();
				float distance = switch (direction.getAxis()) {
				case X -> (float) directionVec.x;
				case Y -> (float) directionVec.y;
				case Z -> (float) directionVec.z;
				};
				if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE && distance >= -1.0F
						|| direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE && distance <= 1.0F) {
					entry.setValue(((float) DETECTION_RADIUS - Math.abs(distance)) / (float) DETECTION_RADIUS);
				}
				else {
					entry.setValue(-1.0F);
				}
			}
		}
		else {
			for (Map.Entry<Direction, Float> entry : flamesStrength.entrySet()) {
				entry.setValue(-1.0F);
			}
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		detector.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.popPose();
	}

	public void renderFlames(PoseStack poseStack, MultiBufferSource buffer, Camera camera) {
		for (Map.Entry<Direction, Float> entry : flamesStrength.entrySet()) {
			float strength = entry.getValue();
			if (strength > 0.0F) {
				Vec3i normal = entry.getKey().getNormal();
				renderFlame(poseStack, buffer, Vec3.atLowerCornerOf(normal).scale(0.25D), strength, camera);
			}
		}
	}

	private static void renderFlame(PoseStack poseStack, MultiBufferSource buffer, Vec3 offset, float strength, Camera camera) {
		TextureAtlasSprite fire0 = fireSprite(MR_FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(MR_FIRE_1);
		poseStack.pushPose();
		poseStack.translate(offset.x, offset.y, offset.z);
		float scale = strength * 0.2F;
		poseStack.scale(scale, scale, scale);
		float xHalfWidth = 0.5F;
		float heightLeft = 0.5F;
		float yOffset = 0.0F;
		poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
		poseStack.translate(0.0D, 0.0D, -0.3F + (int) heightLeft * 0.02F);
		float zOffset = 0.0F;
		VertexConsumer vertexBuilder = buffer.getBuffer(Sheets.translucentCullBlockSheet());

		for (int i = 0; heightLeft > 0.0F; ++i) {
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

			vertex(poseStack, vertexBuilder, strength, xHalfWidth, -yOffset, zOffset, texU1, texV1);
			vertex(poseStack, vertexBuilder, strength, -xHalfWidth, -yOffset, zOffset, texU0, texV1);
			vertex(poseStack, vertexBuilder, strength, -xHalfWidth, 1.4F - yOffset, zOffset, texU0, texV0);
			vertex(poseStack, vertexBuilder, strength, xHalfWidth, 1.4F - yOffset, zOffset, texU1, texV0);
			heightLeft -= 0.45F;
			yOffset -= 0.45F;
			xHalfWidth *= 0.9F;
			zOffset += 0.03F;
		}

		poseStack.popPose();
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, float alpha,
			float x, float y, float z, float u, float v) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F))
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(ClientUtil.MAX_LIGHT)
				.setNormal(pose, 0, 1, 0);
	}
}
