package rotp.addons.stickyfingers.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.stickyfingers.StickyFingersAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 sticky_fingers_heavy_punch: .shout(BRUNO_HEAVY_PUNCH) from the user.
 * It was a SHIFT variation of the punch, so it shouts while sneaking too.
 */
public final class StickyFingersHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {

    public StickyFingersHeavyPunchAbility(
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
            sayShout(standUser, StickyFingersAddon.BRUNO_HEAVY_PUNCH);
        }
    }
}
