package rotp.addons.softandwet.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.softandwet.SoftAndWetAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 soft_and_wet_heavy_punch: .shout(JOSUKE_PUNCH_HEAVY) from the user.
 * It was a SHIFT variation of the punch, so it shouts while sneaking too.
 */
public final class SoftAndWetHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {

    public SoftAndWetHeavyPunchAbility(
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
            sayShout(standUser, SoftAndWetAddon.JOSUKE_PUNCH_HEAVY);
        }
    }
}
