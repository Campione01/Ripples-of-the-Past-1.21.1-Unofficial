package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.impl.powers.hamon.HamonPowerType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

public class TornadoOverdriveEffectLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	private static final ResourceLocation TEXTURE = ResourceLocation.parse("minecraft:textures/entity/trident_riptide.png");
	private final ModelPart box;
	private static LivingEntity rotatedBody;
	private static float rotatedBodyAngle;

	public TornadoOverdriveEffectLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
		this.box = createLayer().bakeRoot().getChild("box");
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (!isTornadoOverdriveActive(livingEntity)) {
			return;
		}

		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		for (int i = 0; i < 3; ++i) {
			poseStack.pushPose();
			float rotation = ageInTicks * -(45.0F + i * 5.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
			float scale = 0.75F * i;
			poseStack.scale(scale, scale, scale);
			poseStack.translate(0.0D, -0.2F + 0.6F * i, 0.0D);
			box.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
	}

	public static boolean isTornadoOverdriveActive(LivingEntity entity) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		return action != null
				&& !action.isOver()
				&& action.ability instanceof Ability ability
				&& (ability.abilityType == HamonPowerType.HAMON_TORNADO_OVERDRIVE.get()
						|| "tornado_overdrive".equals(ability.getAbilityId().nameInMoveset()));
	}

	public static void rotateBody(PoseStack poseStack, LivingEntity entity, float partialTick) {
		// Entity age keeps advancing while the held action's phase timer stays at its endpoint.
		float angle = (entity.tickCount + partialTick) * 2.0F % ((float) Math.PI * 2.0F);
		poseStack.mulPose(Axis.YP.rotation(angle));
		rotatedBody = entity;
		rotatedBodyAngle = angle;
	}

	public static void endBodyRotation(LivingEntity entity) {
		if (rotatedBody == entity) {
			rotatedBody = null;
		}
	}

	// 1.16 ClientEventHandler.onRenderNameplate: the name tag is drawn on the spun pose, so it is turned back.
	@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
	public static class NameTagEvents {

		@SubscribeEvent(priority = EventPriority.HIGHEST)
		public static void counterRotateNameTag(RenderNameTagEvent event) {
			if (rotatedBody != null && event.getEntity() == rotatedBody) {
				event.getPoseStack().mulPose(Axis.YP.rotation(-rotatedBodyAngle));
			}
		}
	}

	private static LayerDefinition createLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition root = meshDefinition.getRoot();
		root.addOrReplaceChild("box",
				CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 32.0F, 16.0F),
				PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}
}
