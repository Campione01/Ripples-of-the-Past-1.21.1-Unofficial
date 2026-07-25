package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRebuffOverdriveAbility.HamonRebuffOverdrive;
import com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom.FirstPersonHamonAura;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class EnergyRippleLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<LivingEntity, HamonEnergyRippleHandler> SPARK_HANDLERS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final String HAMON_SHOCK = "hamon_shock";
	private static final String REBUFF_OVERDRIVE = "rebuff_overdrive";
	private static final String HAMON_SENDO_WAVE_KICK = "sendo_wave_kick";
	private static final String SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE = "sunlight_yellow_overdrive_barrage";

	public EnergyRippleLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null) {
			return;
		}

		HamonEnergyRippleHandler sparksHandler = getSparksHandler(entity);
		M model = getParentModel();
		float ticks = entity.tickCount + partialTick;
		sparksHandler.updateSparks(ticks, model, hamon);

		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.getMainCamera();
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = HamonEnergyRippleHandler.SparkPseudoParticle.RENDER_TYPE.begin(tesselator, mc.getTextureManager());
		if (buffer == null) {
			return;
		}

		RenderSystem.enableDepthTest();
		Vector3f[] vertices = particleVertices(camera, Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
		sparksHandler.render(poseStack, buffer, partialTick, vertices);

		var mesh = buffer.build();
		if (mesh != null) {
			BufferUploader.drawWithShader(mesh);
		}
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		sparksHandler.postRender(ticks);
	}

	private static Vector3f[] particleVertices(Camera camera, float yBodyRot) {
		Vector3f[] vertices = new Vector3f[] {
				new Vector3f(-1.0F, -1.0F, 0.0F),
				new Vector3f(-1.0F, 1.0F, 0.0F),
				new Vector3f(1.0F, 1.0F, 0.0F),
				new Vector3f(1.0F, -1.0F, 0.0F)
		};
		Quaternionf cameraX = Axis.XP.rotationDegrees(-camera.getXRot());
		Quaternionf cameraY = Axis.YP.rotationDegrees(180.0F + camera.getYRot() - yBodyRot);
		for (Vector3f vertex : vertices) {
			vertex.add(-0.125F, 0.0F, -0.125F);
			vertex.rotate(cameraX);
			vertex.rotate(cameraY);
		}
		return vertices;
	}

	private static HamonEnergyRippleHandler getSparksHandler(LivingEntity entity) {
		return SPARK_HANDLERS.computeIfAbsent(entity, HamonEnergyRippleHandler::new);
	}

	@Nullable
	public static Collection<HamonEnergyRippleHandler.SparkPseudoParticle> getSparksToRenderFirstPerson(Player entity, HumanoidArm hand) {
		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null) {
			return null;
		}
		if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> livingRenderer)
				|| !(livingRenderer.getModel() instanceof HumanoidModel<?> model)) {
			return null;
		}

		HamonEnergyRippleHandler sparksHandler = getSparksHandler(entity);
		float ticks = entity.tickCount + ClientUtil.partialTick();
		sparksHandler.updateSparks(ticks, model, hamon);
		sparksHandler.postRender(ticks);
		return sparksHandler.sparks.get(hand == HumanoidArm.LEFT ? BipedModelPart.LEFT_ARM : BipedModelPart.RIGHT_ARM);
	}

	private static void addHamonSparks(LivingEntity entity, HamonData hamon, HumanoidModel<?> model, float timeDelta,
			HamonEnergyRippleHandler sparks) {
		float strengthRatio = Mth.clamp(hamon.getHamonStrengthLevel() / 100.0F, 0.0F, 1.0F);
		float controlRatio = Mth.clamp(hamon.getHamonControlLevel() / 100.0F, 0.0F, 1.0F);
		int particles = MathUtil.fractionRandomInc((4.0F + strengthRatio * 12.0F) * timeDelta);
		if (particles <= 0) {
			return;
		}

		String actionName = currentActionName(entity);
		ParticleType<?> particle = null;
		if (hamon.isHamonWallClimbing() || HAMON_SHOCK.equals(actionName)) {
			particle = ModParticles.HAMON_SPARK.get();
		}
		if (particle != null) {
			for (HumanoidArm hand : HumanoidArm.values()) {
				for (int i = 0; i < particles; i++) {
					Vec3 offset = new Vec3(
							(RANDOM.nextDouble() - 0.5D) * 0.4D,
							RANDOM.nextDouble() * (0.5D - 0.4D * controlRatio) - 0.65D,
							(RANDOM.nextDouble() - 0.5D) * 0.4D);
					sparks.addSpark(HamonEnergyRippleHandler.SparkPseudoParticle.armSpark(model, hand == HumanoidArm.RIGHT, particle, offset));
				}
			}
		}

		EntityActionInstance currentAction = LivingComponentAction.getCurEntityAction(entity);
		if (REBUFF_OVERDRIVE.equals(actionName)
				&& currentAction instanceof HamonRebuffOverdrive rebuff
				&& rebuff.isCounterTiming()) {
			int rebuffParticles = MathUtil.fractionRandomInc((12.0F + strengthRatio * 12.0F) * timeDelta);
			for (HumanoidArm hand : HumanoidArm.values()) {
				for (int i = 0; i < rebuffParticles; i++) {
					Vec3 offset = new Vec3(
							(RANDOM.nextDouble() - 0.5D) * 0.4D,
							RANDOM.nextDouble() * 0.5D - 0.45D,
							(RANDOM.nextDouble() - 0.5D) * 0.4D);
					sparks.addSpark(HamonEnergyRippleHandler.SparkPseudoParticle.armSpark(model, hand == HumanoidArm.RIGHT, ModParticles.HAMON_SPARK.get(), offset));
				}
			}
		}

		if (HAMON_SENDO_WAVE_KICK.equals(actionName)) {
			for (int i = 0; i < particles; i++) {
				Vec3 offset = new Vec3(
						(RANDOM.nextDouble() - 0.5D) * 0.4D,
						(RANDOM.nextDouble() - 0.5D) * (0.3D - 0.2D * controlRatio) - 0.375D,
						RANDOM.nextDouble() * 0.2D);
				sparks.addSpark(HamonEnergyRippleHandler.SparkPseudoParticle.legSpark(model, true, ModParticles.HAMON_SPARK.get(), offset));
			}
		}
	}

	private static void addHamonSparkWaves(LivingEntity entity, float time, float timeDelta, HamonEnergyRippleHandler sparks) {
		int t1 = (int) (time / HamonEnergyRippleHandler.WAVES_GAP);
		int t2 = (int) ((time - timeDelta) / HamonEnergyRippleHandler.WAVES_GAP);
		int waves = t1 - t2;
		if (waves <= 0) {
			return;
		}
		if (!SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.equals(currentActionName(entity))) {
			return;
		}
		for (int i = 0; i < waves; i++) {
			sparks.addSparkWave(new HamonEnergyRippleHandler.SparkWave(
					time - HamonEnergyRippleHandler.WAVES_GAP * (t2 + i + 1),
					ModParticles.HAMON_SPARK_YELLOW.get()));
		}
	}

	@Nullable
	private static String currentActionName(LivingEntity entity) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		if (action == null || action.ability == null || action.ability.getAbilityId() == null) {
			return null;
		}
		return action.ability.getAbilityId().nameInMoveset();
	}

	public static class HamonEnergyRippleHandler {
		private final LivingEntity entity;
		private float ticksPrev = Float.MAX_VALUE;
		private final Collection<SparkWave> sparkWaves = new LinkedList<>();
		private final Map<BipedModelPart, List<SparkPseudoParticle>> sparks = new EnumMap<>(BipedModelPart.class);

		public HamonEnergyRippleHandler(LivingEntity entity) {
			this.entity = entity;
			for (BipedModelPart part : BipedModelPart.values()) {
				sparks.put(part, new ArrayList<>());
			}
		}

		public void updateSparks(float time, HumanoidModel<?> model, HamonData entityHamon) {
			if (Minecraft.getInstance().isPaused()) {
				return;
			}
			float delta = time - ticksPrev;
			if (delta < 0.0F) {
				return;
			}
			Iterator<SparkWave> waveIterator = sparkWaves.iterator();
			while (waveIterator.hasNext()) {
				SparkWave wave = waveIterator.next();
				if (wave.addParticles(delta, model, entity, this)) {
					waveIterator.remove();
				}
			}
			addHamonSparkWaves(entity, time, delta, this);
			addHamonSparks(entity, entityHamon, model, delta, this);
		}

		void addSparkWave(SparkWave wave) {
			sparkWaves.add(wave);
		}

		void addSpark(SparkPseudoParticle spark) {
			sparks.get(spark.modelPartType).add(spark);
		}

		public void postRender(float time) {
			float delta = time - ticksPrev;
			ticksPrev = time;
			for (List<SparkPseudoParticle> sparksPart : sparks.values()) {
				Iterator<SparkPseudoParticle> iterator = sparksPart.iterator();
				while (iterator.hasNext()) {
					if (iterator.next().addTimeDelta(delta)) {
						iterator.remove();
					}
				}
			}
		}

		public void render(PoseStack poseStack, VertexConsumer buffer, float partialTick, Vector3f[] vertices) {
			for (List<SparkPseudoParticle> sparksPart : sparks.values()) {
				for (SparkPseudoParticle spark : sparksPart) {
					poseStack.pushPose();
					spark.translateTo(poseStack, spark.modelPart, spark.x, spark.y, spark.z);
					poseStack.scale(spark.scale, spark.scale, spark.scale);
					spark.renderSprite(poseStack.last(), buffer, SparkPseudoParticle.PARTICLE_LIGHT, partialTick, vertices);
					poseStack.popPose();
				}
			}
		}

		private static final float SPARK_LIFE_SPAN = 2.0F;
		private static final float WAVES_GAP = 7.5F;
		private static final float WAVE_DURATION = 20.0F;
		private static final float SPARKS_PER_TICK = 15.0F;

		private static class SparkWave {
			private final ParticleType<?> particleType;
			private float progress;

			private SparkWave(float startingDelta, ParticleType<?> particleType) {
				this.progress = startingDelta / WAVE_DURATION;
				this.particleType = particleType;
			}

			private boolean addParticles(float timeDelta, HumanoidModel<?> model, LivingEntity entity, HamonEnergyRippleHandler sparks) {
				double sparkCount = timeDelta * SPARKS_PER_TICK;
				if (0.5F < progress) {
					sparkCount *= (1.0F + (progress - 0.5F) / 0.5F) * 3.0F;
				}
				int sparkCountInt = MathUtil.fractionRandomInc(sparkCount);
				for (int i = 0; i < sparkCountInt; i++) {
					if (progress <= 0.25F) {
						double y = (progress / 0.25F - 1.0F) * 0.75D;
						sparks.addSpark(SparkPseudoParticle.legSpark(model, RANDOM.nextBoolean(), particleType, randomSideOffset(0.25D, y, 0.25D)));
					}
					else if (progress <= 0.5F) {
						double y = ((progress - 0.25F) / 0.25F - 1.0F) * 0.75D;
						sparks.addSpark(SparkPseudoParticle.torsoSpark(model, particleType, randomSideOffset(0.5D, y, 0.25D)));
						sparks.addSpark(SparkPseudoParticle.armSpark(model, entity.getMainArm() == HumanoidArm.LEFT, particleType, randomSideOffset(0.25D, y + 0.15D, 0.25D)));
						if (0.3333F <= progress) {
							sparks.addSpark(SparkPseudoParticle.headSpark(model, particleType, randomSideOffset(0.5D, y, 0.5D)));
						}
					}
					else {
						double ratio = (progress - 0.5F) / 0.5F;
						double y = ratio * -0.75D + 0.15D + (RANDOM.nextDouble() - 0.5D) * 0.375D * ratio;
						y = Math.max(y, -0.625D);
						sparks.addSpark(SparkPseudoParticle.armSpark(model, entity.getMainArm() == HumanoidArm.RIGHT, particleType, randomSideOffset(0.25D, y, 0.25D)));
					}
				}
				progress += timeDelta / WAVE_DURATION;
				return progress >= 1.0F;
			}
		}

		private static Vec3 randomSideOffset(double widthX, double y, double widthZ) {
			int side = RANDOM.nextDouble() * (widthX + widthZ) < widthX ? 0 : 1;
			if (RANDOM.nextBoolean()) {
				side += 2;
			}
			widthX += 0.05D;
			widthZ += 0.05D;
			return switch (side) {
				case 0 -> new Vec3((RANDOM.nextDouble() - 0.5D) * widthX, y, widthZ / 2.0D);
				case 1 -> new Vec3(widthX / 2.0D, y, (RANDOM.nextDouble() - 0.5D) * widthZ);
				case 2 -> new Vec3((RANDOM.nextDouble() - 0.5D) * widthX, y, -widthZ / 2.0D);
				case 3 -> new Vec3(-widthX / 2.0D, y, (RANDOM.nextDouble() - 0.5D) * widthZ);
				default -> Vec3.ZERO;
			};
		}

		private static Vec3 bendOffset(Vec3 pos, HumanoidModel<?> model, BipedModelPart bendablePart, double bendYPoint) {
			if (bendablePart == BipedModelPart.LEFT_LEG || bendablePart == BipedModelPart.RIGHT_LEG) {
				pos = bendOffset(pos, model, BipedModelPart.TORSO, bendYPoint + 0.75D);
			}
			return pos;
		}

		public static class SparkPseudoParticle implements FirstPersonHamonAura.IFirstPersonParticle {
			public static final int PARTICLE_LIGHT = 0xF000F0;
			public static final ParticleRenderType RENDER_TYPE = ParticleRenderType.PARTICLE_SHEET_OPAQUE;

			public final TextureAtlasSprite hamonSparkSprite;
			private float age;
			public final float lifeSpan = SPARK_LIFE_SPAN;
			public final BipedModelPart modelPartType;
			public final ModelPart modelPart;
			public final double x;
			public final double y;
			public final double z;
			public final float scale;

			private SparkPseudoParticle(ParticleType<?> particleType, BipedModelPart modelPartType, ModelPart modelPart, Vec3 pos) {
				this.hamonSparkSprite = CustomParticlesHelper.getSavedSpriteSet(particleType).get(RANDOM);
				this.modelPartType = modelPartType;
				this.modelPart = modelPart;
				this.x = pos.x;
				this.y = pos.y;
				this.z = pos.z;
				this.scale = 0.04F + RANDOM.nextFloat() * 0.02F;
			}

			static SparkPseudoParticle legSpark(HumanoidModel<?> model, boolean right, ParticleType<?> particleType, Vec3 offset) {
				BipedModelPart modelPartType = right ? BipedModelPart.RIGHT_LEG : BipedModelPart.LEFT_LEG;
				offset = bendOffset(offset, model, modelPartType, -0.375D);
				return new SparkPseudoParticle(particleType, modelPartType, modelPartType.getModelPart(model), offset);
			}

			static SparkPseudoParticle armSpark(HumanoidModel<?> model, boolean right, ParticleType<?> particleType, Vec3 offset) {
				BipedModelPart modelPartType = right ? BipedModelPart.RIGHT_ARM : BipedModelPart.LEFT_ARM;
				double xPivot = right ? -0.0625D : 0.0625D;
				offset = bendOffset(offset, model, modelPartType, -0.225D);
				return new SparkPseudoParticle(particleType, modelPartType, modelPartType.getModelPart(model), offset.add(xPivot, 0.0D, 0.0D));
			}

			static SparkPseudoParticle torsoSpark(HumanoidModel<?> model, ParticleType<?> particleType, Vec3 offset) {
				offset = bendOffset(offset, model, BipedModelPart.TORSO, -0.375D);
				return new SparkPseudoParticle(particleType, BipedModelPart.TORSO, model.body, offset);
			}

			static SparkPseudoParticle headSpark(HumanoidModel<?> model, ParticleType<?> particleType, Vec3 offset) {
				return new SparkPseudoParticle(particleType, BipedModelPart.HEAD, model.head, offset);
			}

			private boolean addTimeDelta(float delta) {
				age += delta;
				return age >= lifeSpan;
			}

			@Override
			public void renderSprite(PoseStack.Pose pose, VertexConsumer buffer, int light, float partialTick, Vector3f[] vertices) {
				float u0 = hamonSparkSprite.getU0();
				float u1 = hamonSparkSprite.getU1();
				float v0 = hamonSparkSprite.getV0();
				float v1 = hamonSparkSprite.getV1();
				buffer.addVertex(pose.pose(), vertices[0].x(), vertices[0].y(), vertices[0].z()).setUv(u1, v1).setColor(1.0F, 1.0F, 1.0F, 1.0F).setLight(light);
				buffer.addVertex(pose.pose(), vertices[1].x(), vertices[1].y(), vertices[1].z()).setUv(u1, v0).setColor(1.0F, 1.0F, 1.0F, 1.0F).setLight(light);
				buffer.addVertex(pose.pose(), vertices[2].x(), vertices[2].y(), vertices[2].z()).setUv(u0, v0).setColor(1.0F, 1.0F, 1.0F, 1.0F).setLight(light);
				buffer.addVertex(pose.pose(), vertices[3].x(), vertices[3].y(), vertices[3].z()).setUv(u0, v1).setColor(1.0F, 1.0F, 1.0F, 1.0F).setLight(light);
			}

			private void translateTo(PoseStack poseStack, @Nullable ModelPart modelPart, double x, double y, double z) {
				if (modelPart != null) {
					modelPart.translateAndRotate(poseStack);
				}
				poseStack.translate(x, -y, -z);
				if (modelPart != null) {
					if (modelPart.xRot != 0.0F) {
						poseStack.mulPose(Axis.XP.rotation(-modelPart.xRot));
					}
					if (modelPart.yRot != 0.0F) {
						poseStack.mulPose(Axis.YP.rotation(-modelPart.yRot));
					}
					if (modelPart.zRot != 0.0F) {
						poseStack.mulPose(Axis.ZP.rotation(-modelPart.zRot));
					}
				}
			}
		}
	}

	private enum BipedModelPart {
		HEAD,
		TORSO,
		LEFT_ARM,
		RIGHT_ARM,
		LEFT_LEG,
		RIGHT_LEG;

		public ModelPart getModelPart(HumanoidModel<?> model) {
			return switch (this) {
				case HEAD -> model.head;
				case TORSO -> model.body;
				case LEFT_ARM -> model.leftArm;
				case RIGHT_ARM -> model.rightArm;
				case LEFT_LEG -> model.leftLeg;
				case RIGHT_LEG -> model.rightLeg;
			};
		}
	}

	public static Vec3 handTipPos(HumanoidModel<?> posedModel, HumanoidArm hand, Vec3 offset, float yBodyRot) {
		double scale = 0.9375D;
		boolean right = hand == HumanoidArm.RIGHT;
		offset = offset.add(0.0D, -0.65D, 0.0D);
		BipedModelPart modelPartType = right ? BipedModelPart.RIGHT_ARM : BipedModelPart.LEFT_ARM;
		ModelPart modelPart = modelPartType.getModelPart(posedModel);
		double xPivot = right ? -0.0625D : 0.0625D;
		offset = HamonEnergyRippleHandler.bendOffset(offset, posedModel, modelPartType, -0.225D).add(xPivot, 0.0D, 0.0D);

		Vec3 pos = new Vec3(modelPart.x / 16.0D, 1.5D - modelPart.y / 16.0D, modelPart.z / 16.0D);
		if (modelPart.zRot != 0.0F) {
			pos = pos.zRot(-modelPart.zRot);
		}
		if (modelPart.yRot != 0.0F) {
			pos = pos.yRot(modelPart.yRot);
		}
		if (modelPart.xRot != 0.0F) {
			pos = pos.xRot(modelPart.xRot);
		}
		pos = pos.add(offset);
		if (modelPart.xRot != 0.0F) {
			pos = pos.xRot(-modelPart.xRot);
		}
		if (modelPart.yRot != 0.0F) {
			pos = pos.yRot(-modelPart.yRot);
		}
		if (modelPart.zRot != 0.0F) {
			pos = pos.zRot(modelPart.zRot);
		}
		return pos.yRot(-yBodyRot * MathUtil.DEG_TO_RAD).scale(scale);
	}
}
