package rotp.addons.diverdown.ability;

import java.util.ArrayList;
import java.util.List;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DiverDownDisassembleMob (049 :59-106, InitStands :74): a StandEntityHeavyAttack the deform dash gave the heavy key
 * to while Diver Down was inside a target at a quarter of its health or less (DiverDownDeformDash.replaceAction
 * :71-84); pressed only if that health was at most the Stand's attack damage; at its perform the bonus loot, then a
 * heavy hit on the target with 0.5 + the finisher value as its knockback.
 */
public final class MobDisassembleAbility extends StandEntityHeavyPunchAbility {
    public static final float STAMINA_COST = 300.0F;
    public static final float HEALTH_RATIO_THRESHOLD = 0.25F;
    public static final int BONUS_LOOT_ROLLS = 5;
    public static final float BONUS_LOOT_LUCK = 5.0F;

    public MobDisassembleAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 diver_down_mob_disassemble needed no Stand part
        partsRequired();
        // .standSound(DIVER_DOWN_DEFORM_DASH) at the perform; the impact is the builder's default stand_punch_heavy
        heavyPunchPerformSound(DiverDownAddon.DIVER_DOWN_DEFORM_DASH);
    }

    @Override
    public EntityActionInstance createActionObj() {
        return new DisassembleAction(this);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 mob_disassemble did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(
                        context != null
                                ? context.getUser()
                                : null);
        if (!qualifies(stand)) {
            return ConditionCheck.createNegative(
                    "diver_down_disassemble_target");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 DiverDownDisassembleMob.checkStandConditions (:65-72) did not call super: the heavy attack's melee check
    // (StandEntityHeavyAttack :125-127) and the port's heavy grab rule were not asked at the press
    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        return ConditionCheck.POSITIVE;
    }

    // only the deform dash hands the heavy key over to it
    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        return this;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level,
            LivingEntity owner, LivingEntity performer) {
        super.initActionFromConfig(action, level, owner, performer);
        if (!level.isClientSide() && owner != null) {
            sayShout(owner, DiverDownAddon.ANASUI_DIVER_DEFORM_DASH);
        }
    }

    /** 1.16 DiverDownDeformDash.replaceAction: Diver Down inside a target at a quarter of its health or less. */
    public static boolean replacesDeformDash(DiverDownStandEntity stand) {
        LivingEntity target =
                stand != null && stand.isEmbedded() ? stand.getEmbeddedTarget() : null;
        return target != null
                && target.getHealth() / target.getMaxHealth()
                        <= HEALTH_RATIO_THRESHOLD;
    }

    public static boolean qualifies(DiverDownStandEntity stand) {
        LivingEntity target =
                stand != null ? stand.getEmbeddedTarget() : null;
        LivingEntity owner = stand != null ? stand.getUser() : null;
        return target != null
                && owner != null
                && DiverDownAuthority.canAffect(
                        owner, target, stand)
                && target.getHealth() / target.getMaxHealth()
                        <= HEALTH_RATIO_THRESHOLD
                && target.getHealth()
                        <= (float) stand.getAttackDamage();
    }

    public static final class DisassembleAction extends StandEntityHeavyPunch {
        public DisassembleAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previous) {
            ActionTarget target = getActionTargetSnapshot(level());
            if (getPerformer() instanceof DiverDownStandEntity stand
                    && stand.getEmbeddedTarget() != null) {
                target = new ActionTarget(stand.getEmbeddedTarget());
            }
            super.onActionSet(previous);
            // 1.16 punchEntity took the target Diver Down was inside, whatever the aim met
            setActionTargetSnapshot(target);
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            return getActionTargetSnapshot(stand.level());
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer() instanceof DiverDownStandEntity stand)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            ActionTarget punch = getPunchTarget(stand);
            if (!(punch.getMainEntity() instanceof LivingEntity target)
                    || stand.getEmbeddedTarget() != target
                    || !DiverDownAuthority.validController(
                            owner, stand, DiverDownAddon.DISASSEMBLE_MOB_NAME)
                    || !DiverDownAuthority.canAffect(owner, target, stand)) {
                startRecovery();
                return;
            }
            StandPower power = StandPower.get(owner);
            playHeavyPunchPerformSound(stand);
            DamageSource damage = makePunchDamageSource();
            dropLoot(level, stand, owner, target, damage);
            disableShieldChance = disableShieldChance(stand, target);
            stand.clearEmbeddedTarget();
            boolean playImpactSound = StandEntityPunchAbility.playHitSound(punch, level);
            Vec3 impactSoundPos = punch.getCenterPos();
            ((DamageSourceModified) damage).jojo_ripples$setStandInvulTicks(10);
            lastDamageDealtToLiving = 0;
            impactSoundDue = null;
            hitEntity(punch, level, stand, damage,
                    StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage()),
                    calcExplosionRadius(stand));
            // 1.16 StandEntity.attackTarget: the impact sound after the hit, only if it landed
            if (playImpactSound && (impactSoundDue == null || impactSoundDue)) {
                StandUtil.broadcastSound(
                        level, impactSoundPos, getHeavyPunchImpactSound(punch),
                        true, power, stand.getSoundSource(), 1.0F, 1.0F);
            }
            punchedTarget = punch;
            standRotationTarget = punch;
            // 1.16 StandEntityTask paid the cost and ignored the result: a short bar only drops to 0
            StandAbilityStamina.consume(ability, power, STAMINA_COST, false);
        }

        private float disableShieldChance;

        // 1.16 punchEntity: .disableBlocking(proximity ratio - 0.25)
        private static float disableShieldChance(StandEntity stand, LivingEntity target) {
            double range = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            double proximity = range > 0.0D
                    ? 1.0D - ActionTargetRange.getDistance(stand, target.getBoundingBox()) / range
                    : 0.0D;
            return Mth.clamp((float) proximity - 0.25F, 0.0F, 1.0F);
        }

        @Override
        protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving,
                DamageSource dmgSource, float dmgAmount, boolean hurt) {
            disableShieldOnHit(targetLiving, hurt, disableShieldChance);
        }

        // 1.16 addKnockback(0.5 + finisher) replaced the heavy attack's own
        @Override
        protected float getAdditionalHeavyPunchKnockback(StandEntity stand) {
            return 0.5F + finisherValue;
        }

        // 1.16 isFreeRecovery: only a finisher's recovery took other actions, and this was none
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return false;
        }

        private static void dropLoot(ServerLevel level, DiverDownStandEntity stand,
                LivingEntity owner, LivingEntity target, DamageSource source) {
            if (target instanceof Player playerTarget) {
                List<Integer> occupied = new ArrayList<>();
                for (int slot = 0;
                        slot < playerTarget.getInventory()
                                .getContainerSize();
                        slot++) {
                    if (!playerTarget.getInventory()
                            .getItem(slot).isEmpty()) {
                        occupied.add(slot);
                    }
                }
                if (!occupied.isEmpty()) {
                    int chosen = occupied.get(
                            target.getRandom()
                                    .nextInt(occupied.size()));
                    ItemStack removed =
                            playerTarget.getInventory().removeItem(
                                    chosen,
                                    playerTarget.getInventory()
                                            .getItem(chosen)
                                            .getCount());
                    if (!removed.isEmpty()) {
                        // 1.16 Entity.spawnAtLocation
                        playerTarget.spawnAtLocation(removed);
                    }
                }
                return;
            }
            var table = level.getServer().reloadableRegistries().getLootTable(target.getLootTable());
            var builder = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, target)
                    .withParameter(LootContextParams.ORIGIN, target.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, owner)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, stand)
                    .withLuck(BONUS_LOOT_LUCK);
            if (owner instanceof Player playerOwner) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, playerOwner);
            }
            var params = builder.create(LootContextParamSets.ENTITY);
            for (int roll = 0; roll < BONUS_LOOT_ROLLS; roll++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.is(Items.SADDLE)) {
                        target.spawnAtLocation(stack);
                    }
                }
            }
        }
    }
}
