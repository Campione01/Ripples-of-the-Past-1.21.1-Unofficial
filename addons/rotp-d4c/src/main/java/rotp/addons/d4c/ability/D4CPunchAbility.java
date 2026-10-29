package rotp.addons.d4c.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 d4c_punch: .shout(valentine_punch) from the user, not a Stand cry.
 */
public final class D4CPunchAbility
        extends StandEntityPunchAbility {

    public D4CPunchAbility(
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
            sayShout(standUser, D4CAddon.VALENTINE_PUNCH);
        }
    }
}
