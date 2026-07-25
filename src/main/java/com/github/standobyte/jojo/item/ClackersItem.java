package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.customobjects.entity_projectile.ClackersEntity;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClackersItem extends Item {
	public static final int TICKS_MAX_POWER = 20;
	private static final float CHARGE_TICK_COST = 5.0F;
	private static final float UPKEEP_TICK_COST = CHARGE_TICK_COST / 5.0F;
	private static final ItemAttributeModifiers CLACKERS_ATTRIBUTES = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_SPEED,
					new AttributeModifier(BASE_ATTACK_SPEED_ID, 6.0D, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
			.build();

	public ClackersItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hasClackerVolley(player)) {
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		}
		playClackSound(level, player);
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		int ticksUsed = getUseDuration(stack, entity) - remainingTicks;
		if (!level.isClientSide()) {
			float energyCost = ticksUsed <= TICKS_MAX_POWER ? CHARGE_TICK_COST : UPKEEP_TICK_COST;
			boolean canKeepCharging = PlayerPower.getPowerData(entity, HamonPowerType.HAMON)
					.map(hamon -> hasClackerVolley(entity) && hamon.consumeEnergy(energyCost, entity))
					.orElse(false);
			if (!canKeepCharging) {
				entity.releaseUsingItem();
				return;
			}
			if (ticksUsed == TICKS_MAX_POWER) {
				JojoModUtil.sayVoiceLine(entity, ModSoundEvents.JOSEPH_HAMON_CLACKER_VOLLEY);
			}
		}
	}

	public static int clackersTexVariant(int ticksUsed, int ticksMax) {
		if (ticksUsed < ticksMax / 2) {
			return ticksUsed % 20 == 10 ? 1 : 0;
		}
		if (ticksUsed < ticksMax) {
			return ticksUsed % 8 == 4 ? 1 : 0;
		}
		return 2 + ticksUsed % 2;
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksLeft) {
		int ticksUsed = getUseDuration(stack, entity) - ticksLeft;
		float power = (float) Math.min(ticksUsed, TICKS_MAX_POWER) / (float) TICKS_MAX_POWER;
		if (power > 0.0F) {
			if (power < 0.15F) {
				playClackSound(level, entity);
				if (!level.isClientSide()) {
					DamageSource source = entity instanceof Player player
							? entity.damageSources().playerAttack(player)
							: entity.damageSources().mobAttack(entity);
					entity.hurt(source, 1.0F);
					JojoModUtil.sayVoiceLine(entity, ModSoundEvents.JOSEPH_OH_NO);
				}
			}
			else if (!level.isClientSide() && power > 0.5F) {
				ItemStack projectileStack = stack.copy();
				projectileStack.setCount(1);
				ClackersEntity clackers = new ClackersEntity(level, entity, projectileStack);
				float projectileSpeed = power == 1.0F ? 3.0F : power * 2.0F;
				clackers.setHamonDamage(projectileSpeed * 0.5F);
				clackers.setHamonEnergySpent(Math.min(ticksUsed, TICKS_MAX_POWER) * CHARGE_TICK_COST
						+ Math.max(ticksUsed - TICKS_MAX_POWER, 0) * UPKEEP_TICK_COST);
				clackers.shootFromRotation(entity, projectileSpeed, 0.5F);

				ItemTracker tracker = ItemTracking.getItemTracker(projectileStack, level);
				if (tracker != null) {
					tracker.setAtEntity(projectileStack, clackers.getId(), level, KnownItemState.ENTITY_IS_ITEM, null);
				}
				level.addFreshEntity(clackers);
			}
		}
		if (power > 0.5F && !(entity instanceof Player player && player.getAbilities().instabuild)) {
			stack.shrink(1);
		}
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity user) {
		return PlayerPower.getPowerData(user, HamonPowerType.HAMON).map(hamon -> {
			if (!hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get())) {
				return false;
			}
			if (!user.level().isClientSide() && hamon.consumeEnergy(200.0F, user)) {
				HamonAbilityHelpers.hamonHurt(target, user, 0.15F);
				target.invulnerableTime = 0;
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, 200.0F);
				return true;
			}
			return user.level().isClientSide();
		}).orElse(false);
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
		return CLACKERS_ATTRIBUTES;
	}

	public static boolean hasClackerVolley(LivingEntity entity) {
		return PlayerPower.getPowerData(entity, HamonPowerType.HAMON)
				.map(hamon -> hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()))
				.orElse(false);
	}

	public static void playClackSound(Level level, LivingEntity entity) {
		level.playSound(entity instanceof Player player ? player : null,
				entity.getX(), entity.getY(), entity.getZ(),
				ModSoundEvents.CLACKERS.get(), entity.getSoundSource(),
				0.5F, 1.0F + (entity.getRandom().nextFloat() - 0.5F) * 0.1F);
	}

	public static Vec3 projectilePickupOffset(ClackersEntity clackers) {
		Vec3 movement = clackers.getDeltaMovement();
		return movement.lengthSqr() > 1.0E-7D ? movement.normalize().scale(-0.25D) : Vec3.ZERO;
	}
}
