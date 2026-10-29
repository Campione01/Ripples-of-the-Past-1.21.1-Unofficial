package rotp.addons.heavensdoor.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 heavens_door_turn_into_a_book: Rohan shouts, the Stand stays silent. */
public final class HeavensDoorTurnIntoBookAbility
        extends StandEntityPunchAbility {
    public HeavensDoorTurnIntoBookAbility(
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
        // 1.16 .shout(HEAVENS_DOOR_PUNCH_LIGHT) = rohan_light_attack
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, HeavensDoorAddon.ROHAN_LIGHT_ATTACK);
        }
    }
}
