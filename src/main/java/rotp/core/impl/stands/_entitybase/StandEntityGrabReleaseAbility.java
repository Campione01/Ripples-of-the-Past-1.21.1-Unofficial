package rotp.core.impl.stands._entitybase;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class StandEntityGrabReleaseAbility extends Ability {

	public StandEntityGrabReleaseAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		usageGroup = AbilityUsageGroup.GRAB;
		spriteName = "stand_grab_release";
		useAbilityTypeResourceNamespace();
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && StandUtil.getStandGrabTarget(context) != null;
	}

	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			StandEntity standEntity = StandUtil.getSummonedStand(user);
			if (standEntity != null) {
				LivingComponentGrab standGrab = standEntity.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
				if (standGrab != null) {
					standGrab.setGrabTarget(null);
				}
			}
		}
	}
}
