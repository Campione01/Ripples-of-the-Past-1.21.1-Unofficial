package rotp.core.impl.powers.hamon.client;

import java.util.List;

import rotp.core.client.entityanim.IHumanoidAnimModel;
import rotp.core.client.entityanim.playerbend.IPlayerBendModel;
import rotp.core.client.entityanim.playerbend.IPlayerLimbBend;
import rotp.core.client.entityanim.playerbend.PlayerModelBends;
import rotp.core.client.entityanim.pose.AnimatedEntity;
import rotp.core.compat.v1_21_4.renderstate.EntityRenderState;
import rotp.core.compat.v1_21_4.renderstate.HumanoidRenderState;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class HamonMasterModel extends HumanoidModel<HamonMasterEntity> {
	public final ModelPart leftSleeve;
	public final ModelPart rightSleeve;
	public final ModelPart leftPants;
	public final ModelPart rightPants;
	public final ModelPart jacket;

	private final ModelPart rightCapeBinding;
	private final ModelPart rightCape;
	private final ModelPart lowRightCape;
	private final ModelPart leftCapeBinding;
	private final ModelPart leftCape;
	private final ModelPart lowLeftCape;
	// carries the precomputed sitting pose; mob renderers get no player render state
	private final HumanoidRenderState sittingPoseState = new HumanoidRenderState();

	public HamonMasterModel(ModelPart root) {
		super(root);
		leftSleeve = root.getChild("left_sleeve");
		rightSleeve = root.getChild("right_sleeve");
		leftPants = root.getChild("left_pants");
		rightPants = root.getChild("right_pants");
		jacket = root.getChild("jacket");

		rightCapeBinding = body.getChild("right_cape_binding");
		rightCape = body.getChild("right_cape");
		lowRightCape = rightCape.getChild("low_right_cape");
		leftCapeBinding = body.getChild("left_cape_binding");
		leftCape = body.getChild("left_cape");
		lowLeftCape = leftCape.getChild("low_left_cape");

		// the outer layer bends with its limb, as the 1.16 KosmX applier did
		IPlayerBendModel bends = (IPlayerBendModel) this;
		((IPlayerLimbBend) (Object) jacket).jojo_ripples$setBendBone(bends.jojo_ripples$animTorsoBend(), true);
		((IPlayerLimbBend) (Object) rightSleeve).jojo_ripples$setBendBone(bends.jojo_ripples$animRightArmBend(), false);
		((IPlayerLimbBend) (Object) leftSleeve).jojo_ripples$setBendBone(bends.jojo_ripples$animLeftArmBend(), false);
		((IPlayerLimbBend) (Object) rightPants).jojo_ripples$setBendBone(bends.jojo_ripples$animRightLegBend(), false);
		((IPlayerLimbBend) (Object) leftPants).jojo_ripples$setBendBone(bends.jojo_ripples$animLeftLegBend(), false);
	}

	public static LayerDefinition createBodyLayer() {
		return createLayer(false);
	}

	public static LayerDefinition createExtraLayer() {
		return createLayer(true);
	}

	private static LayerDefinition createLayer(boolean extraLayer) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();

		if (extraLayer) {
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		}
		else {
			root.addOrReplaceChild("left_arm",
					CubeListBuilder.create().texOffs(32, 48)
							.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
					PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_leg",
					CubeListBuilder.create().texOffs(16, 48)
							.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
					PartPose.offset(1.9F, 12.0F, 0.0F));
		}

		CubeDeformation outer = new CubeDeformation(0.25F);
		root.addOrReplaceChild("left_sleeve", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(48, 48)
						.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_sleeve", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(40, 32)
						.addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(-5.0F, 2.0F, 10.0F));
		root.addOrReplaceChild("left_pants", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(0, 48)
						.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("right_pants", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(0, 32)
						.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
				PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("jacket", extraLayer ? CubeListBuilder.create()
				: CubeListBuilder.create().texOffs(16, 32)
						.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer),
				PartPose.ZERO);

		PartDefinition body = root.getChild("body");
		CubeDeformation bindingDeformation = new CubeDeformation(0.225F);
		body.addOrReplaceChild("right_cape_binding", extraLayer
				? CubeListBuilder.create().texOffs(36, 36)
						.addBox(-2.0F, -0.8F, -2.0F, 4.0F, 3.0F, 4.0F, bindingDeformation)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(-2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0873F));
		PartDefinition rightCape = body.addOrReplaceChild("right_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(36, 45).addBox(-2.0F, 0.0F, -0.35F, 4.0F, 4.0F, 0.0F)
						.texOffs(44, 40).addBox(-2.0F, 0.0F, -5.35F, 0.0F, 4.0F, 5.0F)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(-2.0F, 12.0F, 2.0F, 0.2182F, 0.0F, 0.1745F));
		rightCape.addOrReplaceChild("low_right_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(44, 44).addBox(0.0F, 0.0F, -5.0F, 0.0F, 7.0F, 5.0F)
						.texOffs(36, 49).addBox(0.0F, 0.0F, 0.0F, 4.0F, 7.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offset(-2.0F, 4.0F, -0.35F));

		body.addOrReplaceChild("left_cape_binding", extraLayer
				? CubeListBuilder.create().texOffs(4, 36)
						.addBox(-2.0F, -0.8F, -2.0F, 4.0F, 3.0F, 4.0F, bindingDeformation)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.0873F));
		PartDefinition leftCape = body.addOrReplaceChild("left_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(2, 40).addBox(2.0F, 0.0F, -5.35F, 0.0F, 4.0F, 5.0F)
						.texOffs(12, 45).addBox(-2.0F, 0.0F, -0.35F, 4.0F, 4.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offsetAndRotation(2.0F, 12.0F, 2.0F, 0.2182F, 0.0F, -0.1745F));
		leftCape.addOrReplaceChild("low_left_cape", extraLayer
				? CubeListBuilder.create()
						.texOffs(2, 44).addBox(0.0F, 0.0F, -5.0F, 0.0F, 7.0F, 5.0F)
						.texOffs(12, 49).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 7.0F, 0.0F)
				: CubeListBuilder.create(),
				PartPose.offset(2.0F, 4.0F, -0.35F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HamonMasterEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch) {
		// the player renderer path resets and poses in LivingEntityRendererMixin; this mob does it here
		EntityRenderState.resetPose(this);
		super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		sittingPoseState.get().entityAction.pose =
				((AnimatedEntity) entity).jojo_ripples$getModelPose(AnimatedEntity.PoseType.FINAL);
		IHumanoidAnimModel animModel = (IHumanoidAnimModel) this;
		animModel.jojo_ripples$setupHumanoidAnim(sittingPoseState);
		if (animModel.jojo_rippes$isPlayingAnimation()) {
			// 1.16 HamonMasterModel.setupAnim: the cape spreads around the crossed legs
			setDegrees(leftCapeBinding, 35.298F, 2.2454F, 4.6285F - 5);
			setDegrees(leftCape, 56.8726F + 12.5F, 6.8027F, 9.3505F - 10);
			setDegrees(lowLeftCape, 14.85F, 0F, 0F);
			setDegrees(rightCapeBinding, 37.5939F, -0.8553F, -3.8097F + 5);
			setDegrees(rightCape, 52.5177F + 12.5F, -9.1032F, -9.1693F + 10);
			setDegrees(lowRightCape, 21.39F, 0F, 0F);
		}
		setupOuterLayer();
	}

	private static void setDegrees(ModelPart part, float x, float y, float z) {
		part.xRot = x * Mth.DEG_TO_RAD;
		part.yRot = y * Mth.DEG_TO_RAD;
		part.zRot = z * Mth.DEG_TO_RAD;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		if (!((IHumanoidAnimModel) this).jojo_rippes$isPlayingAnimation()) {
			super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
			return;
		}
		// PlayerModelBends.renderWithBends, plus the hat and outer layer it only draws for PlayerModel
		IPlayerBendModel bends = (IPlayerBendModel) this;
		poseStack.pushPose();
		ModelPart mainBody = bends.jojo_ripples$animMainBody();
		mainBody.translateAndRotate(poseStack);
		translateBack(mainBody, poseStack);
		leftLeg.render(poseStack, buffer, packedLight, packedOverlay, color);
		leftPants.render(poseStack, buffer, packedLight, packedOverlay, color);
		rightLeg.render(poseStack, buffer, packedLight, packedOverlay, color);
		rightPants.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.pushPose();
		ModelPart torso = bends.jojo_ripples$animTorso();
		translateBack(torso, poseStack);
		torso.translateAndRotate(poseStack);
		PlayerModelBends.rotateAndTranslateBack(bends.jojo_ripples$animTorsoBend(), poseStack);
		body.render(poseStack, buffer, packedLight, packedOverlay, color);
		jacket.render(poseStack, buffer, packedLight, packedOverlay, color);
		head.render(poseStack, buffer, packedLight, packedOverlay, color);
		hat.render(poseStack, buffer, packedLight, packedOverlay, color);
		leftArm.render(poseStack, buffer, packedLight, packedOverlay, color);
		leftSleeve.render(poseStack, buffer, packedLight, packedOverlay, color);
		rightArm.render(poseStack, buffer, packedLight, packedOverlay, color);
		rightSleeve.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.popPose();
		poseStack.popPose();
	}

	private static void translateBack(ModelPart part, PoseStack poseStack) {
		poseStack.translate(-part.getInitialPose().x / 16, -part.getInitialPose().y / 16, -part.getInitialPose().z / 16);
	}

	public void copyPropertiesTo(HamonMasterModel model) {
		super.copyPropertiesTo(model);
		model.leftSleeve.copyFrom(leftSleeve);
		model.rightSleeve.copyFrom(rightSleeve);
		model.leftPants.copyFrom(leftPants);
		model.rightPants.copyFrom(rightPants);
		model.jacket.copyFrom(jacket);
		model.leftCapeBinding.copyFrom(leftCapeBinding);
		model.leftCape.copyFrom(leftCape);
		model.lowLeftCape.copyFrom(lowLeftCape);
		model.rightCapeBinding.copyFrom(rightCapeBinding);
		model.rightCape.copyFrom(rightCape);
		model.lowRightCape.copyFrom(lowRightCape);
	}

	@Override
	public void setAllVisible(boolean visible) {
		super.setAllVisible(visible);
		leftSleeve.visible = visible;
		rightSleeve.visible = visible;
		leftPants.visible = visible;
		rightPants.visible = visible;
		jacket.visible = visible;
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return List.of(body, rightArm, leftArm, rightLeg, leftLeg, hat,
				leftPants, rightPants, leftSleeve, rightSleeve, jacket);
	}

	private void setupOuterLayer() {
		leftPants.copyFrom(leftLeg);
		rightPants.copyFrom(rightLeg);
		leftSleeve.copyFrom(leftArm);
		rightSleeve.copyFrom(rightArm);
		jacket.copyFrom(body);
	}
}
