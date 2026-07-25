package com.github.standobyte.jojoimpl.powers.vampirism;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.block.WoodenCoffinBlock;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.mechanics.VampireSunBurnEffect;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

public final class SunWeakness {
	public static final float SUN_DAMAGE = 4.0F;

	private SunWeakness() {
	}

	public static boolean isSunny(Level level) {
		if (level.isClientSide()) {
			return false;
		}
		return level.dimensionType().hasSkyLight()
				&& !level.dimensionType().hasCeiling()
				&& level.isDay()
				&& !level.isRaining()
				&& !level.isThundering();
	}

	public static void tickSunBurn(LivingEntity entity, Level level) {
		if (level.isClientSide() || entity.invulnerableTime > 10 || !entityTakesSunDamage(entity)) {
			return;
		}
		float sunDamage = getSunDamage(entity, level);
		if (sunDamage > 0.0F && entity.hurt(DamageUtil.make(level, ModDamageTypes.ULTRAVIOLET), sunDamage)) {
			incSunBurn(entity, 1);
		}
	}

	public static void incSunBurn(LivingEntity entity, int tickUpAmount) {
		MobEffectInstance sunBurnEffect = entity.getEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
		int duration;
		int amplifier;
		if (sunBurnEffect == null) {
			duration = 60 * tickUpAmount;
			amplifier = tickUpAmount - 1;
		}
		else {
			int difficulty = Math.max(entity.level().getDifficulty().getId(), 1);
			duration = sunBurnEffect.getDuration() + 60 * tickUpAmount / difficulty;
			amplifier = duration / 60;
		}
		VampireSunBurnEffect.giveEffectTo(entity, duration, amplifier);
	}

	private static float getSunDamage(LivingEntity entity, Level level) {
		if (isSunny(level) && entity.getLightLevelDependentMagicValue() > 0.5F && level.canSeeSky(sunCheckPos(entity))) {
			return SUN_DAMAGE;
		}
		return 0.0F;
	}

	private static BlockPos sunCheckPos(LivingEntity entity) {
		BlockPos pos = BlockPos.containing(entity.getX(), Math.round(entity.getY(1.0D)), entity.getZ());
		return entity.getVehicle() instanceof Boat ? pos.above() : pos;
	}

	private static boolean entityTakesSunDamage(LivingEntity entity) {
		if (!JojoDefinitions.isUndeadOrVampiric(entity)
				|| WoodenCoffinBlock.isSleepingInCoffin(entity)
				|| entity.getType() == EntityType.WITHER
				|| entity.hasEffect(ModStatusEffects.SUN_RESISTANCE)) {
			return false;
		}
		if (!(entity instanceof Player)
				&& !JojoModConfig.getCommonConfigInstance(false).undeadMobsSunDamage.get()) {
			return false;
		}
		return PlayerPower.getPowerData(entity, ModPlayerPowers.PILLAR_MAN)
				.map(PillarmanData::isStoneFormEnabled)
				.map(stoneForm -> !stoneForm)
				.orElse(true);
	}
}
