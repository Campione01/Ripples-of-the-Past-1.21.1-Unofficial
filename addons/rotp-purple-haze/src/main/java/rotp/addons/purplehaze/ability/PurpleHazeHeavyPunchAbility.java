package rotp.addons.purplehaze.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PurpleHazeHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    private Holder<SoundEvent> userShoutSound;

    public PurpleHazeHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 SHIFT variation of the punch: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    public PurpleHazeHeavyPunchAbility userShoutSound(
            Holder<SoundEvent> sound) {
        userShoutSound = sound;
        return this;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck parent = super.checkStandEntityConditions(
                standPower, standEntity);
        if (!parent.isPositive()) {
            return parent;
        }
        return standEntity instanceof PurpleHazeEntity purpleHaze
                && !purpleHaze.isMad()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "cant_control_stand");
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide()
                && standUser != null
                && userShoutSound != null
                && (!(standEntity instanceof PurpleHazeEntity purpleHaze)
                        || !purpleHaze.isAutomationDispatch())) {
            sayShout(standUser, userShoutSound);
        }
    }
}
