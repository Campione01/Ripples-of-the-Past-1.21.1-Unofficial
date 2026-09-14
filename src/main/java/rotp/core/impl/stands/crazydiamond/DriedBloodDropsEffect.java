package rotp.core.impl.stands.crazydiamond;

import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class DriedBloodDropsEffect extends StandEffectInstance {
	private int disappearTicks = 0;

	public DriedBloodDropsEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		needsTarget = true;
		removeOnUserLogout = false;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide()) {
			Entity target = getTarget();
			if (target != null) {
				++disappearTicks;
				if (target.isInWaterOrBubble()) {
					disappearTicks += 29;
				}
				else if (target.isInWaterOrRain()) {
					++disappearTicks;
				}
			}
			if (disappearTicks >= 6000) {
				remove();
			}
		}
	}

	public void resetTicks() {
		disappearTicks = 0;
	}

	@Override
	protected void stop() {}

	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		super.writeAdditionalSaveData(nbt);
		nbt.putInt("BloodTicks", disappearTicks);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		disappearTicks = nbt.getInt("BloodTicks");
	}
	
	
	public static boolean onPossibleBloodSplash(boolean confirmedSplash, LivingEntity bleedingEntity, LivingEntity targetEntity, float bleedAmount) {
		if (confirmedSplash || bleedingEntity != null && targetEntity.getRandom().nextFloat() < bleedAmount / 5) {
			StandPower power = StandPower.get(bleedingEntity);
			if (power != null && power.hasPower()
					&& power.isAbilityUnlocked("blood_cutter")
					&& CrazyDBloodCutterEntity.canHaveBloodDropsOn(targetEntity, power)) {
				/*DriedBloodDropsEffect bloodDrops = */ power.userStandEffects.getOrCreateEffect(ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get(), targetEntity);
				return true;
			}
		}

		return false;
	}

}
