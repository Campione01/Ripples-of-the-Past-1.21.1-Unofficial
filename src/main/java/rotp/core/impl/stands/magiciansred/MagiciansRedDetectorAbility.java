package rotp.core.impl.stands.magiciansred;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

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
