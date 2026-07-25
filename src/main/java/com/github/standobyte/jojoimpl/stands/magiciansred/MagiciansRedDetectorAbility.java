package com.github.standobyte.jojoimpl.stands.magiciansred;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class MagiciansRedDetectorAbility extends Ability {

	public MagiciansRedDetectorAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		partsRequired(StandPart.MAIN_BODY);
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (level.isClientSide()) {
			return;
		}
		StandPower standPower = StandPower.get(user);
		if (standPower == null) {
			return;
		}
		autoSummonStand(user, standPower);
		var existing = level.getEntitiesOfClass(MRDetectorEntity.class, user.getBoundingBox().inflate(5.0D),
				detector -> detector.isOwner(user));
		if (!existing.isEmpty()) {
			existing.forEach(MRDetectorEntity::discard);
		}
		else {
			MRDetectorEntity detector = new MRDetectorEntity(user, level);
			detector.copyPosition(user);
			level.addFreshEntity(detector);
		}
	}

	private static void autoSummonStand(LivingEntity user, StandPower standPower) {
		if (!(standPower.getPowerType() instanceof EntityStandType entityStandType)) {
			return;
		}
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity == null) {
			entityStandType.summon(user, standPower, entity -> {}, true);
		}
		else if (standEntity.isArmsOnlyMode()) {
			standEntity.fullSummonFromArms();
			entityStandType.triggerFullSummonAdvancement(user, standEntity);
		}
	}
}
