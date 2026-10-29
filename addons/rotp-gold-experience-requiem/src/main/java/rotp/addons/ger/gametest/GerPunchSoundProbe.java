package rotp.addons.ger.gametest;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * Asks the core punch actions which impact and cry sounds a registered
 * ability resolves to. The cry plays on clients only, so a server test can
 * read it only through this resolution.
 */
final class GerPunchSoundProbe {
    private GerPunchSoundProbe() {}

    static Holder<SoundEvent> lightImpact(Ability ability, ActionTarget target) {
        return new Light(ability).impact(target);
    }

    @Nullable
    static SoundEvent lightCry(Ability ability, StandEntity stand) {
        return new Light(ability).cry(stand);
    }

    static Holder<SoundEvent> heavyImpact(Ability ability, ActionTarget target) {
        return new Heavy(ability).impact(target);
    }

    static SoundEvent heavyCry(Ability ability) {
        return new Heavy(ability).cry();
    }

    private static final class Light extends StandEntityPunch {
        Light(Ability ability) {
            super((EntityActionType) ability);
        }

        Holder<SoundEvent> impact(ActionTarget target) {
            return getPunchImpactSound(target);
        }

        @Nullable
        SoundEvent cry(StandEntity stand) {
            return getStandCrySound(stand);
        }
    }

    private static final class Heavy extends StandEntityHeavyPunch {
        Heavy(Ability ability) {
            super((EntityActionType) ability);
        }

        Holder<SoundEvent> impact(ActionTarget target) {
            return getHeavyPunchImpactSound(target);
        }

        SoundEvent cry() {
            return getHeavyPunchCrySound();
        }
    }
}
