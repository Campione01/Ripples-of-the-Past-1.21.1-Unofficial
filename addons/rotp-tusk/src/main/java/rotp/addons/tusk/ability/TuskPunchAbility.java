package rotp.addons.tusk.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 tusk_punch: .shout(JOHNNY_LIGHT_ATTACK) from the user.
 */
public final class TuskPunchAbility
        extends StandEntityPunchAbility {

    public TuskPunchAbility(
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
            sayShout(standUser, TuskAddon.JOHNNY_LIGHT_ATTACK);
        }
    }
}
