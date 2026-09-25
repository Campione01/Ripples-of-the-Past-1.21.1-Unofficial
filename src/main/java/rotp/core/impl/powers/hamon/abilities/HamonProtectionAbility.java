package rotp.core.impl.powers.hamon.abilities;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonCharge;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HamonProtectionAbility extends Ability {

	public HamonProtectionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return ConditionCheck.NEGATIVE;
		}
		return hamon.isProtectionEnabled() || hamon.hasEnergy(1.0F)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("some_energy");
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (level.isClientSide()) {
			return;
		}
		Power<?> power = getUserPower(user);
		HamonData hamon = getHamonData(power);
		if (hamon != null) {
			hamon.toggleHamonProtection();
			hamon.syncOnUpdate(user);
		}
	}

	@Override
	public String getSpriteName(Power<?> context) {
		return isProtectionEnabled(context) ? "hamon_protection_on" : super.getSpriteName(context);
	}

	public static boolean isProtectionEnabled(Power<?> context) {
		HamonData hamon = getHamonData(context);
		return hamon != null && hamon.isProtectionEnabled();
	}

	public static float reduceDamageAmount(Power<?> power, LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		if (!isProtectionEnabled(power)) {
			return dmgAmount;
		}
		return protectionCut(power, user, dmgSource, dmgAmount);
	}

	/** Hamon Protection's cut whether or not it is on: 1.16 Rebuff Overdrive gave it to hits its charge let through. */
	public static float protectionCut(Power<?> power, LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		HamonData hamon = getHamonData(power);
		if (hamon == null || user == null || dmgAmount <= 0.0F) {
			return dmgAmount;
		}

		float energyCost = dmgAmount * 75.0F;
		float efficiency = hamon.getHamonEnergyUsageEfficiency(energyCost, true, user);
		if (efficiency <= 0.0F) {
			return dmgAmount;
		}

		float controlRatio = (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
		float baseReduction = 0.4F + controlRatio * 0.2F;
		float damageReductionMult = Mth.clamp(baseReduction * efficiency, 0.0F, 1.0F);
		float damageReduced = dmgAmount * damageReductionMult;
		hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, energyCost * efficiency);
		hamon.syncOnUpdate(user);
		HamonUtil.emitHamonSparkParticles(user.level(), null, damageSparkPosition(user, dmgSource), damageReduced * 0.25F);
		return dmgAmount - damageReduced;
	}

	/** 1.16 HamonUtil.cancelDamageFromBlock: cactus and sweet berry bush damage, paid once per touching block. */
	public static boolean cancelDamageFromBlock(LivingEntity entity, DamageSource dmgSource, float dmgAmount) {
		Class<? extends Block> blockType = damagingBlockType(dmgSource);
		if (blockType == null || entity.level().isClientSide()) {
			return false;
		}
		Level level = entity.level();
		boolean protectedFromDamage = true;
		boolean fromBlocks = false;
		AABB hitbox = entity.getBoundingBox();
		BlockPos posMin = BlockPos.containing(hitbox.minX + 0.001D, hitbox.minY + 0.001D, hitbox.minZ + 0.001D);
		BlockPos posMax = BlockPos.containing(hitbox.maxX - 0.001D, hitbox.maxY - 0.001D, hitbox.maxZ - 0.001D);
		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
		if (level.hasChunksAt(posMin, posMax)) {
			for (int x = posMin.getX(); x <= posMax.getX() && protectedFromDamage; ++x) {
				for (int y = posMin.getY(); y <= posMax.getY() && protectedFromDamage; ++y) {
					for (int z = posMin.getZ(); z <= posMax.getZ() && protectedFromDamage; ++z) {
						blockPos.set(x, y, z);
						BlockState blockState = level.getBlockState(blockPos);
						if (blockType.isInstance(blockState.getBlock())) {
							protectedFromDamage &= preventBlockDamage(entity, dmgSource, dmgAmount, blockPos, blockState);
							fromBlocks = true;
						}
					}
				}
			}
		}
		if (!fromBlocks) {
			protectedFromDamage = preventBlockDamage(entity, dmgSource, dmgAmount, null, null);
		}
		return protectedFromDamage;
	}

	@Nullable
	private static Class<? extends Block> damagingBlockType(DamageSource source) {
		if (source.is(DamageTypes.CACTUS)) {
			return CactusBlock.class;
		}
		if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
			return SweetBerryBushBlock.class;
		}
		return null;
	}

	public static boolean preventBlockDamage(LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		return preventBlockDamage(user, dmgSource, dmgAmount, null, null);
	}

	public static boolean preventBlockDamage(LivingEntity user, DamageSource dmgSource, float dmgAmount,
			@Nullable BlockPos blockPos, @Nullable BlockState blockState) {
		if (user == null || user.level().isClientSide() || dmgAmount <= 0.0F) {
			return false;
		}
		boolean damagePrevented = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).map(hamon -> {
			// 1.16: a Hamon Master takes no block damage at all
			if (user instanceof HamonMasterEntity) {
				return true;
			}
			float energyCost = dmgAmount * 0.5F;
			float energy = hamon.getEnergy();
			if (energy >= energyCost) {
				hamon.setEnergy(energy - energyCost);
				hamon.syncOnUpdate(user);
				return true;
			}
			if (energy > 0.0F) {
				hamon.setEnergy(0.0F);
				hamon.syncOnUpdate(user);
			}
			return false;
		}).orElse(false);

		// hasData first: get() would attach a ticking, saved charge state to every entity a cactus hits
		if (!damagePrevented && user.hasData(ModDataAttachmentTypes.HAMON_CHARGE)) {
			HamonCharge charge = EntityHamonChargeState.get(user).getHamonCharge();
			if (charge != null) {
				charge.decreaseTicks(Math.max((int) dmgAmount, 1));
				damagePrevented = true;
			}
		}

		if (damagePrevented) {
			Vec3 sparkPos = blockContactPosition(user, blockPos, blockState);
			HamonUtil.emitHamonSparkParticles(user.level(), null, sparkPos != null ? sparkPos : damageSparkPosition(user, dmgSource),
					Math.min(dmgAmount * 0.25F, 1.0F));
		}
		return damagePrevented;
	}

	// 1.16: a random point where the hitbox meets the block's collision box
	@Nullable
	private static Vec3 blockContactPosition(LivingEntity user, @Nullable BlockPos blockPos, @Nullable BlockState blockState) {
		if (blockPos == null || blockState == null) {
			return null;
		}
		VoxelShape blockShape = blockState.getCollisionShape(user.level(), blockPos);
		if (blockShape.isEmpty()) {
			return null;
		}
		AABB blockBox = blockShape.bounds().move(blockPos);
		AABB hitbox = user.getBoundingBox();
		double minX = Math.max(hitbox.minX, blockBox.minX), maxX = Math.min(hitbox.maxX, blockBox.maxX);
		double minY = Math.max(hitbox.minY, blockBox.minY), maxY = Math.min(hitbox.maxY, blockBox.maxY);
		double minZ = Math.max(hitbox.minZ, blockBox.minZ), maxZ = Math.min(hitbox.maxZ, blockBox.maxZ);
		return new Vec3(
				Mth.lerp(Math.random(), minX, maxX),
				Mth.lerp(Math.random(), minY, maxY),
				Mth.lerp(Math.random(), minZ, maxZ));
	}

	private static Vec3 damageSparkPosition(LivingEntity user, DamageSource dmgSource) {
		Entity sourceEntity = dmgSource.getDirectEntity();
		if (sourceEntity == null) {
			return user.getBoundingBox().getCenter();
		}
		Vec3 sourcePos = sourceEntity.getEyePosition(1.0F);
		AABB userHitbox = user.getBoundingBox();
		if (userHitbox.contains(sourcePos)) {
			return sourcePos;
		}
		return userHitbox.clip(sourcePos, sourcePos.add(sourceEntity.getLookAngle().scale(16.0D)))
				.orElse(user.getEyePosition(1.0F));
	}

	private static HamonData getHamonData(Power<?> context) {
		return context != null
				&& context.getDataForPowerType(
						ModPlayerPowers.HAMON.get().getId())
						instanceof HamonData hamon
								? hamon
								: null;
	}
}
