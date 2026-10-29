package rotp.addons.echoes.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

/**
 * 092 ACT3 barrage: the ACT3 voice is the cry (set in EchoesAct3Stand),
 * one random voice of three plays at the start, and hits have no impact sound.
 */
public final class EchoesAct3BarrageAbility extends StandEntityBarrageAbility {

    public EchoesAct3BarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, Act3Barrage::new);
    }

    public static final class Act3Barrage extends StandEntityBarrage {

        public Act3Barrage(EntityActionType ability) {
            super(ability);
        }

        // 092 standPerform: one voice at the Stand as the barrage starts
        @Override
        protected void onBarrageSet(Level level, StandEntity stand) {
            super.onBarrageSet(level, stand);
            if (!level.isClientSide()) {
                level.playSound(null, stand.getX(), stand.getY(), stand.getZ(),
                        EchoesSounds.randomBarrageHitSound(),
                        stand.getSoundSource(), 0.8F, 1.0F);
            }
        }

        // 092 barrageHitSound(null): no impact sound on hits
        @Nullable
        @Override
        protected Holder<SoundEvent> getBarrageHitSound() {
            return null;
        }
    }
}
