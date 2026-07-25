package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.Comparator;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonPlantItemInfusionAbility;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonRopeTrapAbility;
import com.github.standobyte.jojoimpl.powers.hamon.entity.CrimsonBubbleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.neoforged.neoforge.common.Tags;

public final class HamonUtil {
	private HamonUtil() {}

	public static Set<String> nearbyTeachersSkills(LivingEntity learner) {
		Set<String> skills = new HashSet<>();
		boolean teacherNearby = false;
		for (LivingEntity entity : learner.level().getEntitiesOfClass(LivingEntity.class,
				learner.getBoundingBox().inflate(3.0D),
				entity -> entity != learner && entity.isAlive())) {
			HamonData teacherHamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
			if (teacherHamon != null) {
				teacherNearby = true;
				for (HamonSkillDefinition definition : ModHamonSkills.SKILL_DEFINITIONS) {
					if (definition.requiresTeacher() && teacherHamon.isSkillUnlocked(definition.name())) {
						skills.add(definition.name());
					}
				}
			}
		}
		return teacherNearby ? skills : null;
	}

	public static boolean interactWithHamonTeacher(Level level, Player player, Entity targetEntity) {
		if (targetEntity instanceof LivingEntity targetLiving) {
			HamonData targetHamon = PlayerPower.getPowerData(targetLiving, ModPlayerPowers.HAMON).orElse(null);
			if (targetHamon != null) {
				interactWithHamonTeacher(level, player, targetLiving, targetHamon);
				return true;
			}
		}
		return false;
	}

	public static void interactWithHamonTeacher(Level level, Player player, LivingEntity teacher, HamonData teacherHamon) {
		PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(player);
		HamonData playerHamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElse(null);
		if (playerHamon == null && !level.isClientSide()) {
			if (teacher instanceof Player) {
				teacherHamon.addNewPlayerLearner(teacher, player);
			}
			else {
				startLearningHamon(level, player, playerPower, teacher, teacherHamon);
			}
		}
		else if (playerHamon != null && !level.isClientSide() && player.getAbilities().instabuild) {
			playerHamon.setBreathingLevel(HamonData.MAX_BREATHING_LEVEL);
			playerHamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.MAX_HAMON_POINTS, true, true);
			playerHamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.MAX_HAMON_POINTS, true, true);
			playerHamon.syncOnUpdate(player);
		}
	}

	public static void startLearningHamon(Level level, Player player, PlayerPower playerPower, LivingEntity teacher, HamonData teacherHamon) {
		if (level.isClientSide()) {
			return;
		}
		if (playerPower == null) {
			playerPower = PowerClass.PLAYER_POWER.attachGet(player);
		}
		if (playerPower != null && !playerPower.hasPower()) {
			if (teacherHamon.characterIs(ModHamonSkills.CHARACTER_ZEPPELI.get())) {
				JojoModUtil.sayVoiceLine(teacher, ModSoundEvents.ZEPPELI_FORCE_BREATH.get());
				teacher.swing(InteractionHand.MAIN_HAND, true);
				if (player.getRandom().nextFloat() <= 0.01F) {
					player.hurt(DamageUtil.make(level, ModDamageTypes.SUFFOCATION),
							Math.min(10.0F, player.getHealth() - 0.0001F));
					player.setAirSupply(0);
					return;
				}
				player.hurt(DamageUtil.make(level, ModDamageTypes.SUFFOCATION),
						Math.min(0.1F, player.getHealth() - 0.0001F));
			}
			playerPower.setPowerType(ModPlayerPowers.HAMON.get());
			PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				if (player.getAbilities().instabuild) {
					hamon.setBreathingLevel(HamonData.MAX_BREATHING_LEVEL);
					hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.MAX_HAMON_POINTS, true, true);
					hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.MAX_HAMON_POINTS, true, true);
					hamon.syncOnUpdate(player);
				}
			});
			player.sendSystemMessage(Component.translatable("jojo.chat.message.learnt_hamon"));
			player.displayClientMessage(Component.translatable("jojo.chat.message.hamon_window_hint",
					Component.keybind("jojo_ripples.key.jojo_menu")), true);
		}
		else {
			player.displayClientMessage(Component.translatable("jojo.chat.message.cant_learn_hamon"), true);
		}
	}

	public static boolean isItemLivingMatter(ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return false;
		}

		Item item = itemStack.getItem();
		if (item instanceof BlockItem blockItem) {
			return isBlockLiving(blockItem.getBlock().defaultBlockState());
		}

		return item == ModItems.GE_BODY_TISSUE.get()
				|| item instanceof EggItem
				|| itemStack.is(Tags.Items.FOODS_RAW_FISH)
				|| item == Items.COD
				|| item == Items.SALMON
				|| item == Items.TROPICAL_FISH
				|| item == Items.PUFFERFISH
				|| isFishBucketItem(item);
	}

	public static boolean isFishBucketItem(Item item) {
		return item == Items.COD_BUCKET
				|| item == Items.SALMON_BUCKET
				|| item == Items.TROPICAL_FISH_BUCKET
				|| item == Items.PUFFERFISH_BUCKET;
	}

	public static boolean isWaterBottle(ThrownPotion potion) {
		ItemStack item = potion.getItem();
		PotionContents potionContents = item.get(DataComponents.POTION_CONTENTS);
		return potionContents != null && potionContents.is(Potions.WATER);
	}

	public static void tryChargeProjectile(Projectile projectile) {
		if (projectile.level().isClientSide()) {
			return;
		}
		ProjectileChargeProperties hamonChargeProperties = ProjectileChargeProperties.getChargeProperties(projectile);
		if (hamonChargeProperties == null) {
			return;
		}
		ProjectileHamonChargeState projectileCharge = ProjectileHamonChargeState.get(projectile);
		if (projectileCharge.hasHamonCharge()) {
			return;
		}
		Entity owner = projectile.getOwner();
		if (!(owner instanceof LivingEntity shooter)) {
			return;
		}

		tryChargeProjectileFromUser(projectile, shooter, projectileCharge, hamonChargeProperties);
		tryChargeProjectileFromChargedShooter(shooter, projectileCharge, hamonChargeProperties);
	}

	private static void tryChargeProjectileFromUser(Projectile projectile, LivingEntity shooter,
			ProjectileHamonChargeState projectileCharge, ProjectileChargeProperties hamonChargeProperties) {
		PlayerPower.getPowerData(shooter, ModPlayerPowers.HAMON).ifPresent(hamon -> {
			if (hamon.getEnergy() <= 0.0F) {
				return;
			}
			HamonSkill skillRequired = projectile instanceof AbstractArrow
					? ModHamonSkills.ARROW_INFUSION.get()
					: ModHamonSkills.THROWABLES_INFUSION.get();
			if (!hamon.isSkillLearned(skillRequired)) {
				return;
			}
			boolean creative = shooter instanceof Player player && player.getAbilities().instabuild;
			float efficiency = creative ? 1.0F
					: hamon.getActionEfficiency(hamonChargeProperties.energyRequired, false, skillRequired, shooter);
			if (efficiency <= 0.0F) {
				return;
			}
			float preEnergy = hamon.getEnergy();
			if (!creative && hamon.getHamonEnergyUsageEfficiency(hamonChargeProperties.energyRequired, true, shooter) <= 0.0F) {
				return;
			}
			hamonChargeProperties.applyCharge(projectileCharge,
					efficiency,
					creative ? 0.0F : Math.min(preEnergy, hamonChargeProperties.energyRequired));
			projectileCharge.setMultiplyWithUserStrength(true);
			hamon.syncOnUpdate(shooter);
		});
	}

	private static void tryChargeProjectileFromChargedShooter(LivingEntity shooter,
			ProjectileHamonChargeState projectileCharge, ProjectileChargeProperties hamonChargeProperties) {
		EntityHamonChargeState shooterChargeState = EntityHamonChargeState.get(shooter);
		HamonCharge shooterCharge = shooterChargeState.getHamonCharge();
		if (shooterCharge == null) {
			return;
		}
		shooterCharge.decreaseTicks((int) (shooterCharge.getInitialTicks() * hamonChargeProperties.energyRequired / 1000.0F));
		hamonChargeProperties.applyCharge(projectileCharge, shooterCharge.getDamage(), 0.0F);
		projectileCharge.setMultiplyWithUserStrength(false);
	}

	public static void tryChargeChickenFromChargedEgg(Chicken chicken) {
		if (!(chicken.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		serverLevel.getData(ModDataAttachmentTypes.CHARGED_HAMON_EGGS.get()).eggChargingChicken(chicken)
				.ifPresent(egg -> ProjectileHamonChargeState.transferEggChargeToChicken(chicken, egg));
	}

	public static boolean chargeItemEntity(Player throwerPlayer, ItemEntity itemEntity) {
		return HamonPlantItemInfusionAbility.chargeItemEntity(throwerPlayer, itemEntity);
	}

	public static boolean ropeTrap(LivingEntity user, BlockPos pos, BlockState blockState, Level level, HamonData hamon) {
		return HamonRopeTrapAbility.ropeTrap(user, pos, blockState, level, hamon);
	}

	public static boolean isBlockLiving(BlockState blockState) {
		Block block = blockState.getBlock();
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		String blockName = id.getPath();
		if (blockName.contains("dead")) {
			return false;
		}

		return blockState.is(BlockTags.LOGS)
				|| block instanceof BushBlock
				|| block instanceof CactusBlock
				|| block instanceof LeavesBlock
				|| block instanceof SnowyDirtBlock
				|| blockName.contains("mossy")
				|| blockName.contains("coral")
				|| blockName.contains("bamboo");
	}

	public static boolean isLiving(LivingEntity entity) {
		return !(JojoDefinitions.isUndeadOrVampiric(entity)
				|| entity instanceof AbstractGolem
				|| entity instanceof ArmorStand
				|| entity instanceof StandEntity);
	}

	public static void emitHamonSparkParticles(Level level, Player player, Vec3 pos, float intensity) {
		emitHamonSparkParticles(level, player, pos, intensity, ModSoundEvents.HAMON_SPARK.get());
	}

	public static void emitHamonSparkParticles(Level level, Player player, Vec3 pos, float intensity, SoundEvent sound) {
		if (intensity <= 0.0F || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		int count = Math.max((int) (Math.min(intensity, 4.0F) * 16.5F), 1);
		serverLevel.sendParticles(ModParticles.HAMON_SPARK.get(), pos.x, pos.y, pos.z, count, 0.05, 0.05, 0.05, 0.25);
		float volume = Math.min(intensity * 2.0F, 1.0F);
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT,
				volume, 1.0F + (player != null ? (player.getRandom().nextFloat() - 0.5F) * 0.15F : 0.0F));
	}

	public static final class ProjectileChargeProperties {
		public static final ProjectileChargeProperties ABSTRACT_ARROW = new ProjectileChargeProperties(1.5F, OptionalInt.of(10), 1000.0F);
		public static final ProjectileChargeProperties SNOWBALL = new ProjectileChargeProperties(0.75F, OptionalInt.of(20), 500.0F);
		public static final ProjectileChargeProperties EGG = new ProjectileChargeProperties(0.75F, OptionalInt.empty(), 200.0F);
		public static final ProjectileChargeProperties WATER_BOTTLE = new ProjectileChargeProperties(1.0F, OptionalInt.of(30), 750.0F);
		public static final ProjectileChargeProperties MOLOTOV = new ProjectileChargeProperties(1.0F, OptionalInt.of(200), 500.0F);

		private final float baseMultiplier;
		private final OptionalInt chargeTicks;
		public final float energyRequired;

		private ProjectileChargeProperties(float baseMultiplier, OptionalInt chargeTicks, float energyRequired) {
			this.baseMultiplier = baseMultiplier;
			this.chargeTicks = chargeTicks;
			this.energyRequired = energyRequired;
		}

		public static boolean canBeChargedWithHamon(Entity entity) {
			return entity instanceof ThrownPotion || getChargeProperties(entity) != null;
		}

		@Nullable
		public static ProjectileChargeProperties getChargeProperties(Entity projectile) {
			if (projectile instanceof AbstractArrow) {
				return ABSTRACT_ARROW;
			}
			if (projectile instanceof Snowball) {
				return SNOWBALL;
			}
			if (projectile instanceof ThrownEgg) {
				return EGG;
			}
			if (projectile instanceof ThrownPotion potion && isWaterBottle(potion)) {
				return WATER_BOTTLE;
			}
			if (projectile.getType() == ModEntityTypes.MOLOTOV.get()) {
				return MOLOTOV;
			}
			return null;
		}

		public void applyCharge(ProjectileHamonChargeState chargeState, float damageMultiplier, float spentEnergy) {
			if (chargeTicks.isPresent()) {
				chargeState.setMaxChargeTicks(chargeTicks.getAsInt());
			}
			else {
				chargeState.setInfiniteChargeTime();
			}
			chargeState.setSpentEnergy(spentEnergy);
			chargeState.setBaseDmg(baseMultiplier * damageMultiplier);
		}
	}

	public static void hamonPerksOnDeath(LivingEntity dead) {
		if (dead.level().isClientSide()) {
			return;
		}
		PlayerPower.getPowerData(dead, ModPlayerPowers.HAMON).ifPresent(hamon -> {
			if (hamon.isSkillLearned(ModHamonSkills.CRIMSON_BUBBLE.get())) {
				CrimsonBubbleEntity bubble = new CrimsonBubbleEntity(ModEntityTypes.CRIMSON_BUBBLE.get(), dead.level());
				ItemStack heldItem = dead.getMainHandItem().copy();
				if (!heldItem.isEmpty()) {
					dead.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getEyeY() - 0.3D, dead.getZ(), heldItem);
					item.setPickUpDelay(2);
					dead.level().addFreshEntity(item);
					bubble.putItem(item);
				}
				dead.level().playSound(null, dead.getX(), dead.getY(), dead.getZ(),
						ModSoundEvents.CAESAR_LAST_HAMON.get(), dead.getSoundSource(), 1.0F, 1.0F);
				bubble.moveTo(dead.getX(), dead.getEyeY(), dead.getZ(), dead.getYRot(), dead.getXRot());
				bubble.setHamonPoints(hamon.getHamonStrengthPoints(), hamon.getHamonControlPoints());
				dead.level().addFreshEntity(bubble);
			}
			else if (hamon.isSkillLearned(ModHamonSkills.DEEP_PASS.get())) {
				Player receiver = dead.level().getEntitiesOfClass(Player.class, dead.getBoundingBox().inflate(8.0D),
						player -> !player.is(dead) && PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).isPresent())
						.stream()
						.min(Comparator.comparingDouble(player -> player.distanceToSqr(dead)))
						.orElse(null);
				if (receiver != null) {
					dead.level().playSound(null, dead.getX(), dead.getY(), dead.getZ(),
							ModSoundEvents.ZEPPELI_DEEP_PASS.get(), dead.getSoundSource(), 1.0F, 1.0F);
					PlayerPower.getPowerData(receiver, ModPlayerPowers.HAMON).ifPresent(receiverHamon -> {
						receiverHamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH,
								receiverHamon.getHamonStrengthPoints() + hamon.getHamonStrengthPoints(), true, false);
						receiverHamon.setHamonStatPoints(HamonData.HamonStat.CONTROL,
								receiverHamon.getHamonControlPoints() + hamon.getHamonControlPoints(), true, false);
						receiverHamon.syncOnUpdate(receiver);
						if (receiver instanceof ServerPlayer serverReceiver) {
							receiverHamon.checkHamonMastery(serverReceiver);
						}
						if (receiverHamon.characterIs(ModHamonSkills.CHARACTER_JONATHAN.get())) {
							JojoModUtil.sayVoiceLine(receiver, ModSoundEvents.JONATHAN_DEEP_PASS_REACTION);
						}
					});
					if (receiver instanceof ServerPlayer player) {
						ModCriteriaTriggers.triggerLastHamon(player, dead);
					}
					emitHamonSparkParticles(dead.level(), receiver, receiver.position(), 1.0F,
							ModSoundEvents.HAMON_SPARKS_LONG.get());
				}
			}
		});
	}

	public static void updateCheatDeathEffect(LivingEntity user) {
		user.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModStatusEffects.CHEAT_DEATH, 120000, 0, false, false, true));
	}
}
