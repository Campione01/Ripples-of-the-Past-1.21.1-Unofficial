package rotp.addons.stickyfingers.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.stickyfingers.StickyFingersAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 sticky_fingers_punch: .shout(BRUNO_PUNCH) from the user,
 * silent while sneaking (it was not a SHIFT variation).
 */
public final class StickyFingersPunchAbility
        extends StandEntityPunchAbility {

    public StickyFingersPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, StickyFingersAddon.BRUNO_PUNCH);
        }
    }
}
