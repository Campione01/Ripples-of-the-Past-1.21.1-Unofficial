package com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.github.standobyte.jojo.client.firstperson.FirstPersonRender;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.item.AjaStoneItem;
import com.github.standobyte.jojo.item.GlovesItem;
import com.github.standobyte.jojo.item.OilItem;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonCutterAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonLifeMagnetismAbility;
import com.github.standobyte.jojoimpl.powers.hamon.client.EnergyRippleLayer;
import com.github.standobyte.jojoimpl.powers.hamon.client.EnergyRippleLayer.HamonEnergyRippleHandler;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class FirstPersonHamonAura {
	private static final String SUNLIGHT_YELLOW_OVERDRIVE = "sunlight_yellow_overdrive";
	private static boolean renderingExtraSunlightYellowArm;

	private final Queue<FirstPersonPseudoParticle> particlesToAdd = Queues.newArrayDeque();
	private final Map<ParticleRenderType, Map<HumanoidArm, Queue<FirstPersonPseudoParticle>>> particles = Maps.newIdentityHashMap();

	private FirstPersonHamonAura() {}

	private static FirstPersonHamonAura instance;

	public static void init() {
		instance = new FirstPersonHamonAura();
	}

	public static FirstPersonHamonAura getInstance() {
		if (instance == null) {
			init();
		}
		return instance;
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && !mc.isPaused()) {
			getInstance().tick();
		}
	}

	@SubscribeEvent
	public static void renderEmptyHandAura(RenderArmEvent event) {
		InteractionHandHolder hand = InteractionHandHolder.fromArm(event.getPlayer(), event.getArm());
		if (hand != null && event.getPlayer().getItemInHand(hand.hand()).isEmpty()) {
			getInstance().renderParticles(event.getPoseStack(), event.getMultiBufferSource(), event.getArm());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void renderSunlightYellowOffHandAura(RenderHandEvent event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (event.isCanceled()
				|| renderingExtraSunlightYellowArm
				|| event.getHand() != InteractionHand.MAIN_HAND
				|| player == null
				|| mc.getCameraEntity() != player
				|| player.isInvisible()
				|| !event.getItemStack().isEmpty()
				|| !player.getMainHandItem().isEmpty()
				|| !player.getOffhandItem().isEmpty()
				|| !ClientModSettings.getSettingsReadOnly().firstPersonHamonAura
				|| FirstPersonRender.vanillaRendersBothMapArms(event)
				|| !isSunlightYellowOverdrive(player)) {
			return;
		}

		HumanoidArm offHandSide = player.getMainArm().getOpposite();
		if (!getInstance().hasDrawableParticles(
				player.getOffhandItem(), offHandSide)) {
			return;
		}

		renderingExtraSunlightYellowArm = true;
		try {
			FirstPersonRender.renderExtraPlayerArm(
					event, player, InteractionHand.OFF_HAND);
		}
		finally {
			renderingExtraSunlightYellowArm = false;
		}
	}

	private static boolean isSunlightYellowOverdrive(LivingEntity entity) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		return action != null
				&& action.ability != null
				&& action.ability.getAbilityId() != null
				&& SUNLIGHT_YELLOW_OVERDRIVE.equals(
						action.ability.getAbilityId().nameInMoveset());
	}

	private boolean hasDrawableParticles(
			ItemStack itemStack, HumanoidArm handSide) {
		for (Map.Entry<ParticleRenderType, Map<HumanoidArm,
				Queue<FirstPersonPseudoParticle>>> particlesByRenderType
				: particles.entrySet()) {
			ParticleRenderType renderType = particlesByRenderType.getKey();
			if (renderType == ParticleRenderType.NO_RENDER
					|| renderType == HamonAuraParticleRenderType.HAMON_AURA
					&& (!ClientModSettings.getSettingsReadOnly()
							.firstPersonHamonAura
							|| !auraRendersAtItem(itemStack, handSide))) {
				continue;
			}
			Queue<FirstPersonPseudoParticle> particlesForHand =
					particlesByRenderType.getValue().get(handSide);
			if (particlesForHand != null && !particlesForHand.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public void add(FirstPersonPseudoParticle particle) {
		this.particlesToAdd.add(particle);
	}

	public void tick() {
		for (Map<HumanoidArm, Queue<FirstPersonPseudoParticle>> particlesByHand : this.particles.values()) {
			tickParticles(particlesByHand.get(HumanoidArm.LEFT));
			tickParticles(particlesByHand.get(HumanoidArm.RIGHT));
		}

		FirstPersonPseudoParticle particle;
		while ((particle = particlesToAdd.poll()) != null) {
			Map<HumanoidArm, Queue<FirstPersonPseudoParticle>> particlesByRenderType = this.particles.computeIfAbsent(
					particle.getRenderType(), type -> {
						Map<HumanoidArm, Queue<FirstPersonPseudoParticle>> map = new EnumMap<>(HumanoidArm.class);
						map.put(HumanoidArm.LEFT, EvictingQueue.create(16384));
						map.put(HumanoidArm.RIGHT, EvictingQueue.create(16384));
						return map;
					});
			particlesByRenderType.get(particle.handSide).add(particle);
		}
	}

	private static void tickParticles(Queue<FirstPersonPseudoParticle> particles) {
		if (particles == null || particles.isEmpty()) {
			return;
		}
		Iterator<FirstPersonPseudoParticle> iterator = particles.iterator();
		while (iterator.hasNext()) {
			FirstPersonPseudoParticle particle = iterator.next();
			try {
				particle.tick();
			} catch (Throwable throwable) {
				CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking Particle");
				CrashReportCategory category = crashreport.addCategory("Particle being ticked");
				category.setDetail("Particle", particle::toString);
				category.setDetail("Particle Type", particle.getRenderType()::toString);
				throw new ReportedException(crashreport);
			}
			if (!particle.isAlive()) {
				iterator.remove();
			}
		}
	}

	public static boolean auraRendersAtItem(ItemStack itemStack, HumanoidArm handSide) {
		if (itemStack.isEmpty()) {
			return true;
		}

		if (!(Minecraft.getInstance().getCameraEntity() instanceof LivingEntity entity)) {
			return false;
		}

		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null) {
			return false;
		}

		Item item = itemStack.getItem();
		return entity.getMainArm() == handSide
				&& (hamon.isSkillLearned(ModHamonSkills.METAL_SILVER_OVERDRIVE.get())
						|| OilItem.remainingOiledUses(itemStack).isPresent())
				&& isItemWeapon(itemStack)
				|| hamon.isSkillLearned(ModHamonSkills.PLANT_ITEM_INFUSION.get()) && HamonUtil.isItemLivingMatter(itemStack)
				|| hamon.isSkillLearned(ModHamonSkills.THROWABLES_INFUSION.get()) && isHamonThrowable(itemStack)
				|| hamon.isSkillLearned(ModHamonSkills.ARROW_INFUSION.get()) && isArrowInfusionItem(itemStack)
				|| hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && item == ModItems.CLACKERS.get()
				|| hamon.isSkillLearned(ModHamonSkills.AJA_STONE_KEEPER.get()) && item instanceof AjaStoneItem
				|| hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get()) && item == ModItems.SATIPOROJA_SCARF.get()
				|| rendersAuraForRewardAction(hamon, itemStack);
	}

	private static boolean rendersAuraForRewardAction(HamonData hamon, ItemStack itemStack) {
		return hamon.isSkillLearned(ModHamonSkills.HAMON_CUTTER.get()) && HamonCutterAbility.canUse(itemStack)
				|| hamon.isSkillLearned(ModHamonSkills.BUBBLE_LAUNCHER.get()) && itemStack.is(ModItems.SOAP.get())
				|| hamon.isSkillLearned(ModHamonSkills.BUBBLE_CUTTER.get()) && itemStack.is(ModItems.SOAP.get())
				|| hamon.isSkillLearned(ModHamonSkills.BUBBLE_BARRIER.get()) && itemStack.is(ModItems.SOAP.get())
				|| hamon.isSkillLearned(ModHamonSkills.LIFE_MAGNETISM.get()) && HamonLifeMagnetismAbility.isLeavesItem(itemStack);
	}

	private static boolean isHamonThrowable(ItemStack itemStack) {
		Item item = itemStack.getItem();
		if (item == Items.EGG || item == Items.SNOWBALL || item == ModItems.MOLOTOV.get()) {
			return true;
		}
		if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
			PotionContents potionContents = itemStack.get(DataComponents.POTION_CONTENTS);
			return potionContents != null && potionContents.is(Potions.WATER);
		}
		return false;
	}

	private static boolean isArrowInfusionItem(ItemStack itemStack) {
		Item item = itemStack.getItem();
		return item instanceof ProjectileWeaponItem
				|| item instanceof TridentItem
				|| item == ModItems.KNIFE.get();
	}

	private static boolean isItemWeapon(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof GlovesItem) {
			return false;
		}
		if (stack.getItem() instanceof TieredItem) {
			return true;
		}
		return stack.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
				entry.attribute().is(Attributes.ATTACK_DAMAGE)
				&& entry.slot().test(EquipmentSlot.MAINHAND)
				&& entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
				&& entry.modifier().amount() > 0.0D);
	}

	public static void itemMatrixTransform(PoseStack poseStack, HumanoidArm handSide, ItemStack itemStack) {
		boolean rightHand = handSide != HumanoidArm.LEFT;
		float side = rightHand ? 1.0F : -1.0F;
		poseStack.translate(side * 0.64D, -0.6D, -0.712D);
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 45.0F));
		poseStack.translate(-side, 3.6D, 3.5D);
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 120.0F));
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(200.0F));
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * -135.0F));
		poseStack.translate(side * 5.3D, 0.0D, 0.0D);
	}

	public void renderParticles(PoseStack poseStack, MultiBufferSource bufferSource, HumanoidArm handSide) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		float partialTick = ClientUtil.partialTick();
		Tesselator tesselator = Tesselator.getInstance();

		for (Map.Entry<ParticleRenderType, Map<HumanoidArm, Queue<FirstPersonPseudoParticle>>> particlesByRenderType : particles.entrySet()) {
			ParticleRenderType renderType = particlesByRenderType.getKey();
			if (renderType == ParticleRenderType.NO_RENDER
					|| renderType == HamonAuraParticleRenderType.HAMON_AURA
					&& !ClientModSettings.getSettingsReadOnly().firstPersonHamonAura) {
				continue;
			}

			Queue<FirstPersonPseudoParticle> particlesByHand = particlesByRenderType.getValue().get(handSide);
			if (particlesByHand == null || particlesByHand.isEmpty()) {
				continue;
			}

			ItemStack itemInHand = mc.player.getItemInHand(UtilFunctions.getHand(mc.player, handSide));
			if (renderType == HamonAuraParticleRenderType.HAMON_AURA && !auraRendersAtItem(itemInHand, handSide)) {
				continue;
			}

			BufferBuilder buffer = renderType.begin(tesselator, mc.getTextureManager());
			if (buffer == null) {
				continue;
			}

			try {
				for (FirstPersonPseudoParticle particle : particlesByHand) {
					try {
						float x = (float) Mth.lerp(partialTick, particle.xo, particle.x);
						float y = (float) Mth.lerp(partialTick, particle.yo, particle.y);
						float z = (float) Mth.lerp(partialTick, particle.zo, particle.z);
						float scale = particle.getQuadSize(partialTick);
						int light = particle.getLightColor(partialTick);
						renderParticle(particle, buffer, poseStack.last(), x, y, z, scale, light, partialTick, particle.renderRot);
					} catch (Throwable throwable) {
						CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering Particle");
						CrashReportCategory category = crashreport.addCategory("Particle being rendered");
						category.setDetail("Particle", particle::toString);
						category.setDetail("Particle Type", renderType::toString);
						throw new ReportedException(crashreport);
					}
				}

				var mesh = buffer.build();
				if (mesh != null) {
					BufferUploader.drawWithShader(mesh);
				}
			}
			finally {
				HamonAuraParticleRenderType.endAuraBatchIfOpen();
			}
		}

		Collection<HamonEnergyRippleHandler.SparkPseudoParticle> hamonSparks =
				EnergyRippleLayer.getSparksToRenderFirstPerson(mc.player, handSide);
		if (hamonSparks != null && !hamonSparks.isEmpty()) {
			BufferBuilder sparkBuffer = HamonEnergyRippleHandler.SparkPseudoParticle.RENDER_TYPE.begin(tesselator, mc.getTextureManager());
			if (sparkBuffer != null) {
				float xOffset = handSide == HumanoidArm.LEFT ? 0.35F : -0.35F;
				Quaternionf renderRot = handSide == HumanoidArm.LEFT ? FirstPersonPseudoParticle.LEFT_ROT : FirstPersonPseudoParticle.RIGHT_ROT;
				for (HamonEnergyRippleHandler.SparkPseudoParticle particle : hamonSparks) {
					float x = (float) particle.x + xOffset;
					float y = (float) -particle.y + 0.125F;
					float z = (float) particle.z;
					renderParticle(particle, sparkBuffer, poseStack.last(), x, y, z, particle.scale,
							HamonEnergyRippleHandler.SparkPseudoParticle.PARTICLE_LIGHT, partialTick, renderRot);
				}
				var mesh = sparkBuffer.build();
				if (mesh != null) {
					BufferUploader.drawWithShader(mesh);
				}
			}
		}

		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	private static void renderParticle(IFirstPersonParticle particle, VertexConsumer buffer, PoseStack.Pose pose, 
			float x, float y, float z, float scale, int light, float partialTick, Quaternionf renderRot) {
		Vector3f[] vertices = new Vector3f[] {
				new Vector3f(-1.0F, -1.0F, 0.0F),
				new Vector3f(-1.0F, 1.0F, 0.0F),
				new Vector3f(1.0F, 1.0F, 0.0F),
				new Vector3f(1.0F, -1.0F, 0.0F)
		};

		for (Vector3f vertex : vertices) {
			vertex.rotate(renderRot);
			vertex.mul(scale);
			vertex.add(x, y, z);
		}

		particle.renderSprite(pose, buffer, light, partialTick, vertices);
	}

	public interface IFirstPersonParticle {
		void renderSprite(PoseStack.Pose pose, VertexConsumer buffer, int light, float partialTick, Vector3f[] vertices);
	}

	public static abstract class FirstPersonPseudoParticle implements IFirstPersonParticle {
		protected static final Random RANDOM = new Random();
		protected double xo;
		protected double yo;
		protected double zo;
		protected double x;
		protected double y;
		protected double z;
		protected double xd;
		protected double yd;
		protected double zd;
		protected boolean removed;
		protected int age;
		protected int lifetime = (int) (4.0F / (RANDOM.nextFloat() * 0.9F + 0.1F));
		protected float rCol = 1.0F;
		protected float gCol = 1.0F;
		protected float bCol = 1.0F;
		protected float alpha = 1.0F;
		protected float quadSize = 0.1F * (RANDOM.nextFloat() * 0.5F + 0.5F) * 2.0F;
		protected TextureAtlasSprite sprite;
		protected final SpriteSet sprites;
		protected final Quaternionf renderRot;
		protected final HumanoidArm handSide;
		protected float yRot;
		protected float xRot;

		protected static final float RIGHT_Y_ROT = 57.5F * MathUtil.DEG_TO_RAD;
		protected static final float RIGHT_X_ROT = -62.5F * MathUtil.DEG_TO_RAD;
		protected static final float LEFT_Y_ROT = -RIGHT_Y_ROT;
		protected static final float LEFT_X_ROT = RIGHT_X_ROT;
		protected static final Quaternionf RIGHT_ROT = new Quaternionf().rotateY(RIGHT_Y_ROT).rotateX(RIGHT_X_ROT);
		protected static final Quaternionf LEFT_ROT = new Quaternionf().rotateY(LEFT_Y_ROT).rotateX(LEFT_X_ROT);

		public FirstPersonPseudoParticle(double x, double y, double z, SpriteSet sprites, HumanoidArm handSide) {
			setPos(x, y, z);
			this.xo = x;
			this.yo = y;
			this.zo = z;
			this.sprites = sprites;
			this.handSide = handSide;
			switch (handSide) {
			case LEFT:
				yRot = LEFT_Y_ROT;
				xRot = LEFT_X_ROT;
				renderRot = new Quaternionf(LEFT_ROT);
				break;
			case RIGHT:
			default:
				yRot = RIGHT_Y_ROT;
				xRot = RIGHT_X_ROT;
				renderRot = new Quaternionf(RIGHT_ROT);
				break;
			}
		}

		public abstract ParticleRenderType getRenderType();

		protected void remove() {
			this.removed = true;
		}

		public boolean isAlive() {
			return !this.removed;
		}

		protected void setPos(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		protected void move(double x, double y, double z) {
			if (x != 0.0D || y != 0.0D || z != 0.0D) {
				Vec3 moveVec = new Vec3(x, y, z);
				moveVec = moveVec.xRot(-xRot);
				moveVec = moveVec.yRot(yRot);
				this.x += moveVec.x;
				this.y += moveVec.y;
				this.z += moveVec.z;
			}
		}

		@Override
		public void renderSprite(PoseStack.Pose pose, VertexConsumer buffer, int light, float partialTick, Vector3f[] vertices) {
			float u0 = sprite.getU0();
			float u1 = sprite.getU1();
			float v0 = sprite.getV0();
			float v1 = sprite.getV1();
			buffer.addVertex(pose.pose(), vertices[0].x(), vertices[0].y(), vertices[0].z()).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
			buffer.addVertex(pose.pose(), vertices[1].x(), vertices[1].y(), vertices[1].z()).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
			buffer.addVertex(pose.pose(), vertices[2].x(), vertices[2].y(), vertices[2].z()).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
			buffer.addVertex(pose.pose(), vertices[3].x(), vertices[3].y(), vertices[3].z()).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
		}

		protected float getQuadSize(float scaleFactor) {
			return quadSize;
		}

		protected int getLightColor(float partialTick) {
			return ClientUtil.MAX_LIGHT;
		}

		public void tick() {
			xo = x;
			yo = y;
			zo = z;
			if (age++ >= lifetime) {
				remove();
			}
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + ", Pos (" + this.x + "," + this.y + "," + this.z
					+ "), RGBA (" + this.rCol + "," + this.gCol + "," + this.bCol + "," + this.alpha + "), Age " + this.age;
		}
	}

	public static class HamonAuraPseudoParticle extends FirstPersonPseudoParticle {
		protected final double fallSpeed;
		protected final int startingSpriteRandom;

		public HamonAuraPseudoParticle(double x, double y, double z, SpriteSet sprites, HumanoidArm handSide) {
			super(x, y, z, sprites, handSide);
			this.lifetime = (int) (4.0F / (RANDOM.nextFloat() * 0.9F + 0.1F));
			this.xd = (Math.random() * 2.0D - 1.0D) * 0.4D;
			this.yd = (Math.random() * 2.0D - 1.0D) * 0.4D;
			this.zd = (Math.random() * 2.0D - 1.0D) * 0.4D;
			double speed = (Math.random() + Math.random() + 1.0D) * 0.15D;
			double length = Math.sqrt(xd * xd + yd * yd + zd * zd);
			this.xd = xd / length * speed * 0.4D;
			this.yd = yd / length * speed * 0.4D + 0.1D;
			this.zd = zd / length * speed * 0.4D;

			this.fallSpeed = 0.0005D;
			this.xd *= 0.05D;
			this.yd *= 0.1D;
			this.zd *= 0.05D;
			float sizeRandom = 1.2F + 0.6F * RANDOM.nextFloat();
			this.quadSize *= 0.75F * sizeRandom;
			this.lifetime = 25 + RANDOM.nextInt(10);
			this.startingSpriteRandom = RANDOM.nextInt(lifetime);
			setSpriteFromAge(sprites);
			this.alpha = 0.25F;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return HamonAuraParticleRenderType.HAMON_AURA;
		}

		@Override
		protected int getLightColor(float partialTick) {
			return ClientUtil.MAX_LIGHT;
		}

		@Override
		public void tick() {
			super.tick();
			if (!removed) {
				setSpriteFromAge(sprites);
				yd += fallSpeed;
				move(xd, yd, zd);
				if (y == yo) {
					xd *= 1.1D;
					zd *= 1.1D;
				}

				xd *= 0.96D;
				yd *= 0.96D;
				zd *= 0.96D;
			}
		}

		protected static final float ALPHA_MIN = 0.05F;
		protected static final float ALPHA_DIFF = 0.3F;

		@Override
		public void renderSprite(PoseStack.Pose pose, VertexConsumer buffer, int light, float partialTick, Vector3f[] vertices) {
			float ageF = ((float) age + partialTick) / (float) lifetime;
			float alphaFunc = ageF <= 0.5F ? ageF * 2.0F : (1.0F - ageF) * 2.0F;
			this.alpha = ALPHA_MIN + alphaFunc * ALPHA_DIFF;
			super.renderSprite(pose, buffer, light, partialTick, vertices);
		}

		protected void setSpriteFromAge(SpriteSet spriteSet) {
			setSprite(spriteSet.get((age + startingSpriteRandom) % lifetime, lifetime));
		}

		protected void setSprite(TextureAtlasSprite sprite) {
			this.sprite = sprite;
		}

		@Override
		protected float getQuadSize(float scaleFactor) {
			return quadSize * Mth.clamp(((float) age + scaleFactor) / (float) lifetime * 32.0F, 0.0F, 1.0F);
		}
	}

	private record InteractionHandHolder(net.minecraft.world.InteractionHand hand) {
		private static InteractionHandHolder fromArm(LivingEntity entity, HumanoidArm arm) {
			return new InteractionHandHolder(UtilFunctions.getHand(entity, arm));
		}
	}
}
