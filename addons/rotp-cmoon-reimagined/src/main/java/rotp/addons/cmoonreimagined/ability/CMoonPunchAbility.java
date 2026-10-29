package rotp.addons.cmoonreimagined.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.cmoonreimagined.CMoonAddon;
import net.minecraft.sounds.SoundEvent;

public final class CMoonPunchAbility
        extends StandEntityPunchAbility {
    public CMoonPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CMoonPunch::new);
    }

    public static final class CMoonPunch extends StandEntityPunch {
        public CMoonPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected SoundEvent getPunchSwingSound(StandEntity stand) {
            return CMoonAddon.C_MOON_BARRAGE_SWING.get();
        }
    }
}
