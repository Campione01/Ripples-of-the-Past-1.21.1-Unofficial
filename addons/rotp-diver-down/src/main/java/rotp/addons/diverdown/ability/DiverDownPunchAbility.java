package rotp.addons.diverdown.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.diverdown.DiverDownAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 diver_down_punch: .shout(ANASUI_DIVER_PUNCH) from the user, not a Stand cry.
 */
public final class DiverDownPunchAbility
        extends StandEntityPunchAbility {

    public DiverDownPunchAbility(
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
            sayShout(standUser, DiverDownAddon.ANASUI_DIVER_PUNCH_LIGHT);
        }
    }
}
