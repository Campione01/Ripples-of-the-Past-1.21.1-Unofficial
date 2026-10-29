package rotp.addons.tusk.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 tusk_heavy_punch: .shout(JOHNNY_HEAVY_ATTACK) from the user.
 * It was a SHIFT variation of the punch, so it shouts while sneaking too.
 */
public final class TuskHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {

    public TuskHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        setPlaysVoiceLineOnSneak();
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
            sayShout(standUser, TuskAddon.JOHNNY_HEAVY_ATTACK);
        }
    }
}
