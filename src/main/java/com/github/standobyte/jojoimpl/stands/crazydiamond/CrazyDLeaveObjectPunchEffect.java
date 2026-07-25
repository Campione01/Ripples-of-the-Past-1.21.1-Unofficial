package com.github.standobyte.jojoimpl.stands.crazydiamond;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowItem;
import com.github.standobyte.jojo.modcompat.ModInteractionUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

public class CrazyDLeaveObjectPunchEffect extends StandEffectInstance {
	private static final int SHORT_DURATION = 100;
	private static final int LONG_DURATION = 200;
	private static final int LONGER_DURATION = 400;

	public CrazyDLeaveObjectPunchEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		isFromStandAction = true;
	}

	public static boolean canUseItem(ItemStack item) {
		return item.is(Items.CHORUS_FRUIT)
				|| item.is(Items.ENDER_EYE)
				|| item.is(Items.FIREWORK_ROCKET)
				|| item.is(Items.SNOWBALL)
				|| item.is(Items.SNOW)
				|| item.is(Items.SNOW_BLOCK)
				|| item.is(Items.ICE)
				|| item.is(Items.PACKED_ICE)
				|| item.is(Items.BLUE_ICE)
				|| item.is(Items.FIRE_CHARGE)
				|| item.is(Items.BLAZE_POWDER)
				|| item.is(Items.BLAZE_ROD)
				|| item.is(Items.LAVA_BUCKET)
				|| item.is(Items.GLOWSTONE_DUST)
				|| item.is(Items.SPECTRAL_ARROW)
				|| item.is(Items.GLOWSTONE)
				|| item.is(Items.EXPERIENCE_BOTTLE)
				|| item.is(Items.MILK_BUCKET)
				|| item.get(DataComponents.POTION_CONTENTS) != null
				|| item.get(DataComponents.FOOD) != null
				|| item.getItem() instanceof StandArrowItem;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide()) {
			if (standAction == null) {
				remove();
				return;
			}
			else if (standAction.getActionTicksLeft() <= 1 && standAction.punchedTarget != null && standAction.punchedTarget.getEntity() instanceof LivingEntity target) {
				LivingEntity user = getStandUser();
				if (user == null) {
					remove();
					return;
				}
				if (target instanceof Skeleton || target instanceof StandEntity) {
					remove();
					return;
				}
				ItemStack item = standAction.getPerformer() instanceof StandEntity stand ? stand.getMainHandItem() : ItemStack.EMPTY;
				if (item.isEmpty() || !canUseItem(item)) {
					remove();
					return;
				}
				LivingEntity affected = StandUtil.getStandUser(target);
				if (applyItemEffect(affected, item, user)) {
					item.shrink(1);
					affected.heal(standAction.lastDamageDealtToLiving * 0.5F);
				}
				remove();
			}
			else if (standAction.isOver()) {
				remove();
			}
		}
		else {
			tickClientRestorationVisuals();
		}
	}

	private void tickClientRestorationVisuals() {
		if (standAction == null || standAction.isOver() || standAction.punchedTarget == null) {
			return;
		}
		Entity entity = standAction.punchedTarget.getEntity();
		if (entity instanceof LivingEntity target
				&& target.isAlive()
				&& !(target instanceof Skeleton)
				&& !(target instanceof StandEntity)) {
			CrazyDHealAbility.addParticlesAround(StandUtil.getStandUser(target));
			if (tickCount == 1) {
				if (standAction.getPerformer() instanceof StandEntity stand) {
					if (ClientGlobals.canHearStand(stand)) {
						level.playLocalSound(target.getX(), target.getY(0.5), target.getZ(),
								ClientsideSoundsHelper.withStandSkin(ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), stand),
								stand.getSoundSource(), 1.0F, 1.0F, false);
					}
				}
				else {
					level.playLocalSound(target.getX(), target.getY(0.5), target.getZ(),
							ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), target.getSoundSource(), 1.0F, 1.0F, false);
				}
			}
		}
	}

	private static boolean applyItemEffect(LivingEntity target, ItemStack item, LivingEntity user) {
		if (item.is(Items.CHORUS_FRUIT)) {
			chorusFruitTeleport(target, user);
		}
		else if (item.is(Items.ENDER_EYE)) {
			return enderEyeFlight(target, item, user);
		}
		else if (item.is(Items.FIREWORK_ROCKET)) {
			fireworkFlight(target, item);
		}
		else if (item.is(Items.SNOWBALL)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, 40, 0, false, false, true));
		}
		else if (item.is(Items.SNOW)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, 80, 0, false, false, true));
		}
		else if (item.is(Items.SNOW_BLOCK)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, 120, 1, false, false, true));
		}
		else if (item.is(Items.ICE)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, LONG_DURATION, 1, false, false, true));
		}
		else if (item.is(Items.PACKED_ICE)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, LONG_DURATION, 2, false, false, true));
		}
		else if (item.is(Items.BLUE_ICE)) {
			target.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, LONG_DURATION, 3, false, false, true));
		}
		else if (item.is(Items.FIRE_CHARGE)) {
			DamageUtil.setOnFire(target, 5 * 20, false);
		}
		else if (item.is(Items.BLAZE_POWDER)) {
			DamageUtil.setOnFire(target, 10 * 20, false);
		}
		else if (item.is(Items.BLAZE_ROD) || item.is(Items.LAVA_BUCKET)) {
			DamageUtil.setOnFire(target, 20 * 20, false);
		}
		else if (item.is(Items.GLOWSTONE_DUST)) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, SHORT_DURATION, 0, false, false, true));
		}
		else if (item.is(Items.SPECTRAL_ARROW)) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, LONG_DURATION, 0, false, false, true));
		}
		else if (item.is(Items.GLOWSTONE)) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, LONGER_DURATION, 0, false, false, true));
		}
		else if (item.is(Items.EXPERIENCE_BOTTLE)) {
			giveXp(target);
		}
		else if (item.is(Items.MILK_BUCKET)) {
			target.removeAllEffects();
		}
		else if (item.getItem() instanceof StandArrowItem) {
			return pierceWithStandArrow(target, item, user);
		}
		else {
			PotionContents potionContents = item.get(DataComponents.POTION_CONTENTS);
			if (potionContents != null && potionContents.hasEffects()) {
				potionContents.forEachEffect(effect -> target.addEffect(new MobEffectInstance(effect)));
			}
			else if (item.get(DataComponents.FOOD) != null) {
				target.eat(target.level(), item.copyWithCount(1));
				if (item.is(Items.ENCHANTED_GOLDEN_APPLE)) {
					VampirismData.startCuringFromEnchantedGoldenApple(target);
				}
			}
			else {
				return false;
			}
		}
		return true;
	}

	private static void chorusFruitTeleport(LivingEntity target, LivingEntity user) {
		if (ModInteractionUtil.isEntityEnderman(target)) {
			target.heal(6);
			return;
		}

		double xPrev = target.getX();
		double yPrev = target.getY();
		double zPrev = target.getZ();

		for (int tpTry = 0; tpTry < 16; tpTry++) {
			double y = Mth.clamp(target.getY() + target.getRandom().nextInt(16) - 8.0D,
					target.level().getMinBuildHeight(), target.level().getMaxBuildHeight() - 1.0D);
			double x;
			double z;
			if (user != null) {
				Vec3 middlePos = target.position().add(user.getLookAngle().scale(12.0D));
				x = middlePos.x + (target.getRandom().nextDouble() - 0.5D) * 8.0D;
				z = middlePos.z + (target.getRandom().nextDouble() - 0.5D) * 8.0D;
			}
			else {
				x = xPrev + (target.getRandom().nextDouble() + 1.0D) * (target.getRandom().nextBoolean() ? 1 : -1) * 8.0D;
				z = zPrev + (target.getRandom().nextDouble() + 1.0D) * (target.getRandom().nextBoolean() ? 1 : -1) * 8.0D;
			}

			if (target.isPassenger()) {
				target.stopRiding();
			}

			EntityTeleportEvent.ChorusFruit event = EventHooks.onChorusFruitTeleport(target, x, y, z);
			if (!event.isCanceled() && target.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
				var sound = target instanceof Fox ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;
				target.setYRot(target.getRandom().nextFloat() * 360.0F);
				target.yRotO = target.getYRot();
				target.level().playSound(null, xPrev, yPrev, zPrev, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
				target.playSound(sound, 1.0F, 1.0F);
				break;
			}
		}
	}

	private static void giveXp(LivingEntity target) {
		target.level().levelEvent(2002, target.blockPosition().above(), PotionContents.getColor(Potions.WATER));
		int xp = 3 + target.level().random.nextInt(5) + target.level().random.nextInt(5);
		if (target instanceof Player player) {
			var entry = EnchantmentHelper.getRandomItemWith(DataComponents.ENCHANTMENTS, player, ItemStack::isDamaged);
			if (entry.isPresent()) {
				EnchantedItemInUse enchantedItem = entry.get();
				ItemStack itemStack = enchantedItem.itemStack();
				if (!itemStack.isEmpty() && itemStack.isDamaged()) {
					int repair = Math.min((int) (xp * itemStack.getXpRepairRatio()), itemStack.getDamageValue());
					xp -= repair / 2;
					itemStack.setDamageValue(itemStack.getDamageValue() - repair);
				}
			}
			if (xp > 0) {
				player.giveExperiencePoints(xp);
			}
		}
		else if (target.level() instanceof ServerLevel serverLevel) {
			while (xp > 0) {
				int xpThisOrb = ExperienceOrb.getExperienceValue(xp);
				xp -= xpThisOrb;
				serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, target.getX(), target.getY(), target.getZ(), xpThisOrb));
			}
		}
	}

	private static boolean enderEyeFlight(LivingEntity target, ItemStack item, LivingEntity user) {
		if (!(target.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		BlockPos strongholdPos = serverLevel.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, target.blockPosition(), 100, false);
		if (strongholdPos == null) {
			return false;
		}
		CrazyDEyeOfEnderInsideEntity eyeOfEnder = new CrazyDEyeOfEnderInsideEntity(serverLevel, target);
		eyeOfEnder.setItem(item.copyWithCount(1));
		eyeOfEnder.signalTo(strongholdPos);
		serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDER_EYE_LAUNCH,
				SoundSource.NEUTRAL, 0.5F, 0.4F / (target.getRandom().nextFloat() * 0.4F + 0.8F));
		serverLevel.levelEvent(null, 1003, target.blockPosition(), 0);
		serverLevel.addFreshEntity(eyeOfEnder);
		if (target instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.USED_ENDER_EYE.trigger(serverPlayer, strongholdPos);
		}
		if (user instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.USED_ENDER_EYE.trigger(serverPlayer, strongholdPos);
		}
		return true;
	}

	private static void fireworkFlight(LivingEntity target, ItemStack item) {
		FireworkRocketEntity firework = new CrazyDFireworkInsideEntity(target.level(), item.copyWithCount(1), target);
		target.level().addFreshEntity(firework);
	}

	private static boolean pierceWithStandArrow(LivingEntity target, ItemStack item, LivingEntity user) {
		if (target.level().isClientSide() || StandUtil.isEntityStandUser(target)) {
			return false;
		}
		boolean pierced = StandArrowItem.onPiercedByArrow(target, item, target.level(), java.util.Optional.ofNullable(user));
		Entity direct = user != null ? user : target;
		if (pierced && !StandArrowItem.isInvulnerable(target)) {
			StandArrowItem.dealDamageFromArrow(target, item, direct, direct, false, false);
		}
		if (pierced && target.level() instanceof ServerLevel serverLevel) {
			ItemStack arrowSaved = item.copy();
			item.hurtAndBreak(1, serverLevel, target, itemType -> StandArrowItem.onBreakArrow(
					serverLevel, target, null, null, itemType, arrowSaved));
		}
		return pierced;
	}

	@Override
	protected void stop() {}

}
