package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.mechanics.BleedingEffect;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.mojang.datafixers.util.Pair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class GEHealingEffect extends StandEffectInstance {
	public int regenLevel;
	public int fullHpTicks;
	public MobEffectInstance prevEffect;

	public GEHealingEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		needsTarget = true;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {
		if (!level.isClientSide()) {
			LivingEntity entity = getTargetLiving();
			if (entity == null || !entity.hasEffect(GoldExperienceHealAbility.regenEffectFor(entity))) {
				remove();
				return;
			}

			if (entity.getHealth() >= BleedingEffect.getMaxHealthWithoutBleeding(entity) && --fullHpTicks <= 0) {
				remove();
			}
		}
	}

	@Override
	protected void stop() {
		if (!level.isClientSide()) {
			LivingEntity entity = getTargetLiving();
			if (entity != null) {
				Holder<MobEffect> regenEffect = GoldExperienceHealAbility.regenEffectFor(entity);
				MobEffectInstance regenEff = entity.getEffect(regenEffect);
				if (regenEff != null && regenEff.getAmplifier() == regenLevel) {
					entity.removeEffect(regenEffect);
					if (prevEffect != null) {
						entity.addEffect(prevEffect);
					}
				}
			}
		}
	}

	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		super.writeAdditionalSaveData(nbt);
		nbt.putInt("RegenLvl", regenLevel);
		nbt.putInt("FullHpTime", fullHpTicks);
		if (prevEffect != null) {
			MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, prevEffect)
					.ifSuccess(prevEffectTag -> nbt.put("PrevEff", prevEffectTag));
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		regenLevel = nbt.getInt("RegenLvl");
		fullHpTicks = nbt.getInt("FullHpTime");
		if (nbt.contains("PrevEff")) {
			MobEffectInstance.CODEC.decode(NbtOps.INSTANCE, nbt.get("PrevEff")).result()
					.map(Pair::getFirst)
					.ifPresent(effect -> prevEffect = effect);
		}
	}
}
