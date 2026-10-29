package rotp.addons.madeinheaven.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 mih_barrage: the Stand cries mih_barrage, the user shouts. */
public final class MadeInHeavenBarrageAbility
        extends StandEntityBarrageAbility {
    public MadeInHeavenBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 mih_barrage .shout(MIH_BARRAGE_USER)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, MadeInHeavenAddon.MIH_BARRAGE_USER);
        }
    }
}
