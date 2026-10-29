package rotp.addons.tusk.ability;

import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.particle.NailSwipeParticleOptions;
import rotp.addons.tusk.state.TuskNailState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class TuskNailScratchAbility extends EntityActionAbility {
    public static final float STAMINA_COST = 100.0F;
    public static final float BONUS_DAMAGE = 1.0F;
    public static final float ARMOR_PIERCING = 2.0F;
    public static final double KNOCKBACK = 0.75D;
    public static final int WINDUP_TICKS = 4;
    public static final int RECOVERY_TICKS = 6;

    private static final ResourceLocation PLAYER_ANIM_SET =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "addons/rotp_t/tusk_player");
    private static final ActionAnimIdentifier SCRATCH_LEFT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_nail_scratch_left", false);
    private static final ActionAnimIdentifier SCRATCH_RIGHT =
            ActionAnimIdentifier.getOrCreate(
                    "tusk_nail_scratch_right", false);

    public TuskNailScratchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, NailScratchAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || !TuskAddon.isTuskUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 PowerBaseImpl.checkRequirements: the target, then checkHeldItems (the hands), then the nail count.
        // The HUD and the client's own press check run on the user's client, whose copy of the user holds no stored
        // aim (1.16: the mouse target).
        ActionTarget target = LivingComponentAction.getAimTarget(player);
        if (target.getType() != ActionTarget.TargetType.ENTITY
                || !(target.getMainEntity() instanceof LivingEntity living)
                || !JojoModUtil.canHarm(player, living)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.scratch_target"));
        }
        if (!player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()) {
            return ConditionCheck.createNegative("hands");
        }
        if (TuskAddon.nailState(player).nails() <= 0) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.no_nails"));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        int nails = context.getUser() instanceof Player player
                ? TuskAddon.nailState(player).nails()
                : 0;
        return abilityName(
                context, "", nails, TuskNailState.MAX_NAILS);
    }

    @Override
    public ResourceLocation getEntityAnimSet(LivingEntity user) {
        return PLAYER_ANIM_SET;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        LivingEntity user = action.getPerformer();
        return user != null && user.getMainArm() == HumanoidArm.LEFT
                ? SCRATCH_LEFT
                : SCRATCH_RIGHT;
    }

    public static final class NailScratchAction
            extends EntityActionInstance {
        public NailScratchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            LivingEntity user = getPerformer();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(getPerformer() instanceof Player player)
                    || !TuskAddon.isTuskUser(player)
                    || TuskAddon.nailState(player).nails() <= 0
                    || !player.getMainHandItem().isEmpty()
                    || !player.getOffhandItem().isEmpty()) {
                return;
            }

            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(player), STAMINA_COST, false);

            ActionTarget target = getActionTargetSnapshot(serverLevel);
            if (target.getType() != ActionTarget.TargetType.ENTITY
                    || !(target.getMainEntity()
                            instanceof LivingEntity livingTarget)
                    || !JojoModUtil.canHarm(player, livingTarget)) {
                return;
            }

            DamageSource damageSource = serverLevel.damageSources()
                    .playerAttack(player);
            float damage = (float) player.getAttributeValue(
                    Attributes.ATTACK_DAMAGE) + BONUS_DAMAGE;
            damage = DamageUtil.addArmorPiercing(
                    damage,
                    ARMOR_PIERCING,
                    livingTarget,
                    damageSource);
            if (!livingTarget.hurt(damageSource, damage)) {
                return;
            }

            serverLevel.playSound(
                    null,
                    livingTarget,
                    TuskAddon.SCRATCH_SOUND.get(),
                    livingTarget.getSoundSource(),
                    1.0F,
                    1.0F);
            livingTarget.knockback(
                    KNOCKBACK,
                    player.getX() - livingTarget.getX(),
                    player.getZ() - livingTarget.getZ());
            KnockbackCollisionImpact impact =
                    KnockbackCollisionImpact.getHandler(livingTarget);
            if (impact != null) {
                impact.onPunchSetKnockbackImpact(
                        livingTarget.getDeltaMovement(), player);
            }

            Vec3 particlePos = player.getEyePosition()
                    .lerp(livingTarget.getBoundingBox().getCenter(), 0.5D);
            serverLevel.sendParticles(
                    new NailSwipeParticleOptions(player.getUUID()),
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
