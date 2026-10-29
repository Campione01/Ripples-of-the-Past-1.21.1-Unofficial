package rotp.addons.ger.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerFieldEntity;
import rotp.addons.ger.entity.GerStandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class GerDisablerAbility extends StandEntityAbility {
    public static final double LEGACY_RADIUS = 128.0D;
    public static final int LEGACY_DURATION_TICKS = 180;
    public static final int LEGACY_COOLDOWN_TICKS = 360;
    public static final float LEGACY_STAMINA_COST = 100.0F;
    public static final float VISUAL_RADIUS = 20.0F;
    public static final int VISUAL_LIFETIME_TICKS = 12;

    public GerDisablerAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DisablerAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 8);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(LEGACY_COOLDOWN_TICKS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return GerActionAnimations.resolve(
                getAbilityId(), super.getEntityAnim(action));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null
                || GoldExperienceRequiemAddon.activeStand(user)
                        == null
                || !(context instanceof StandPower power)) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 Disabler.checkSpecificConditions, which Resolve does not lift.
        return power.usesStamina()
                        && power.getStamina() < LEGACY_STAMINA_COST
                ? ConditionCheck.createNegative("no_stamina")
                : ConditionCheck.POSITIVE;
    }

    public static final class DisablerAction
            extends EntityActionInstance {
        public DisablerAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof GerStandEntity stand)
                    || GoldExperienceRequiemAddon.activeStand(owner)
                            != stand) {
                return;
            }
            StandPower power = StandPower.get(owner);
            // 1.16 Disabler.standPerform: a bar emptied since the press
            // pays what it has and disables nothing, without a message.
            if (!StandAbilityStamina.consume(
                    ability,
                    power,
                    LEGACY_STAMINA_COST,
                    false)) {
                return;
            }

            serverLevel.getEntitiesOfClass(
                            LivingEntity.class,
                            owner.getBoundingBox().inflate(
                                    LEGACY_RADIUS),
                            target -> target != owner
                                    && target.isAlive())
                    .forEach(target ->
                            GoldExperienceRequiemAddon
                                    .livingState(target)
                                    .applyDisabler(
                                            owner.getUUID(),
                                            LEGACY_DURATION_TICKS));

            StandUtil.playStandEntitySound(
                    stand,
                    GoldExperienceRequiemAddon.GER_DISABLER_SOUND,
                    1.0F,
                    1.0F);
            serverLevel.addFreshEntity(new GerFieldEntity(
                    owner,
                    GerFieldEntity.DISABLER,
                    VISUAL_RADIUS,
                    VISUAL_LIFETIME_TICKS));
        }
    }
}
