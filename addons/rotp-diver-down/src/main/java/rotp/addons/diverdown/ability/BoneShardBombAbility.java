package rotp.addons.diverdown.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BoneShardBombAbility extends StandEntityAbility {
    public static final int RESOLVE_LEVEL = 4;
    public static final int WINDUP_TICKS = 10;
    public static final float STAMINA_COST = 100.0F;
    public static final int DURATION_TICKS = 1200;
    public static final int AMPLIFIER = 0;
    private static final ActionAnimIdentifier HEAVY_ATTACK_ANIM =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public BoneShardBombAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BoneShardBombAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        standAutoSummonMode(AutoSummonMode.DISABLED);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        // 1.16 SHIFT variation: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 bone_shard_bomb did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(
                        context != null
                                ? context.getUser()
                                : null);
        if (stand == null || !stand.isEmbedded()) {
            return ConditionCheck.createNegative(
                    "diver_down_not_embedded");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 PowerBaseImpl.onClickAction :215-221 said the action's shout once its press was accepted
    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level, LivingEntity owner, LivingEntity performer) {
        super.initActionFromConfig(action, level, owner, performer);
        if (!level.isClientSide() && owner != null) {
            sayShout(owner, DiverDownAddon.ANASUI_DIVER_PUNCH_HEAVY);
        }
    }

    // 1.16 diver_down_bone_shard_bomb standPose(StandPose.HEAVY_ATTACK)
    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return HEAVY_ATTACK_ANIM;
    }

    public static final class BoneShardBombAction
            extends EntityActionInstance {
        public BoneShardBombAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)
                    || !(getPerformer()
                            instanceof DiverDownStandEntity stand)
                    || !(ability
                            instanceof BoneShardBombAbility boneShardBomb)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            LivingEntity target = stand.getEmbeddedTarget();
            StandPower power = owner != null ? StandPower.get(owner) : null;
            if (power == null
                    || !stand.isEmbedded()
                    || target == null
                    || !boneShardBomb.checkConditions(power).isPositive()
                    || !DiverDownAuthority.validController(
                            owner, stand, DiverDownAddon.BONE_SHARD_BOMB_NAME)
                    || !DiverDownAuthority.canAffect(owner, target, stand)) {
                forceStop();
                return;
            }
            if (DiverDownAuthority.bindEffect(
                    target,
                    stand,
                    DiverDownAuthority.EffectKind.BONE_SHARD_BOMB,
                    DURATION_TICKS,
                    AMPLIFIER)) {
                stand.clearEmbeddedTarget();
                // The donor applied the effect before its clampable completion debit.
                StandAbilityStamina.consume(
                        ability, power, STAMINA_COST, false);
            }
        }
    }
}
