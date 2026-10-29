package rotp.addons.spicegirl.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.spicegirl.SpiceGirlAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SpiceGirlFinisherPunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final int ELASTIC_DURATION_TICKS = 300;
    public static final int ELASTIC_AMPLIFIER = 1;

    public SpiceGirlFinisherPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = SpiceGirlFinisherPunch::new;
        // 1.16 SHIFT variation (or its finisher): shouts on sneak too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 spice_girl_finisher_punch .shout(SG_PUNCH_FINISHER_VOICE)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, SpiceGirlAddon.SG_PUNCH_FINISHER_VOICE);
        }
    }

    public static final class SpiceGirlFinisherPunch
            extends SpiceGirlHeavyPunchAbility.SpiceGirlHeavyPunch {
        public SpiceGirlFinisherPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected int elasticDurationTicks() {
            return ELASTIC_DURATION_TICKS;
        }

        @Override
        protected int elasticAmplifier() {
            return ELASTIC_AMPLIFIER;
        }
    }
}
