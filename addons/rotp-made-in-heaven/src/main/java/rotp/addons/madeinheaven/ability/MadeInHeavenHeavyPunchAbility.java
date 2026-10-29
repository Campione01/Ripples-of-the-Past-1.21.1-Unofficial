package rotp.addons.madeinheaven.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 mih_heavy_punch: a user shout, no Stand cry. */
public final class MadeInHeavenHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public MadeInHeavenHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 SHIFT variation of mih_punch / mih_barrage: shouts while sneaking too
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
        // 1.16 mih_heavy_punch .shout(MIH_HEAVY_PUNCH_USER)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, MadeInHeavenAddon.MIH_HEAVY_PUNCH_USER);
        }
    }

    // 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent: with the
    // meter full or a Chop running, the heavy input is Chop even when Chop
    // cannot be used then, so its cooldown or the running Chop refuses it.
    // The core finisher check only puts in a Chop that can be used.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability variation =
                super.replaceWithSubAbility(context, abilities);
        if (variation != this) {
            return variation;
        }
        StandPower standPower = PowerClass.STAND.cast(context);
        StandEntity stand = standPower != null
                ? standPower.getSummonedStandEntity() : null;
        Ability chop = stand != null
                ? context.getMoveset().getAbility(
                        MadeInHeavenAddon.CHOP_NAME)
                : null;
        if (chop == null
                || chop.isStandFinisherOf == null
                || !name().equals(
                        chop.isStandFinisherOf.baseAbilityName())) {
            return this;
        }
        EntityActionInstance current = stand.getCurStandAction();
        boolean chopRunning = current != null && !current.isOver()
                && current.ability instanceof MadeInHeavenChopAbility;
        return chopRunning || stand.getFinisherMeter()
                >= chop.isStandFinisherOf.minFinisherValue()
                ? chop : this;
    }
}
