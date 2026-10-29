package rotp.addons.shadowtheworld.ability;

import java.util.Comparator;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShadowSuperHeavyAbility
        extends StandEntityAbility {
    private static final float STAMINA = 150.0F;
    private static final ActionAnimIdentifier ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "knockbackPunch", false);

    public ShadowSuperHeavyAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SuperHeavyAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 6);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 5);
        cooldown(60);
        resolveLevelToUnlock(3);
        // 1.16 InitStands super_heavy_punch partsRequired(MAIN_BODY, ARMS)
        partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 super_heavy_punch .shout(SHADOW_WORLD_KNIVES_THROW)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, ShadowTheWorldAddon.SHADOW_WORLD_KNIVES_THROW);
        }
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIM;
    }

    private static final class SuperHeavyAction
            extends EntityActionInstance {
        private SuperHeavyAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer()
                            instanceof StandEntity stand)
                    || !(ability
                            instanceof ShadowSuperHeavyAbility
                                    superHeavy)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                forceStop();
                return;
            }

            Vec3 forward = user.getLookAngle().normalize();
            double dash = Math.min(
                    5.0D,
                    Math.max(
                            0.0D,
                            stand.getMaxRange()
                                    - stand.distanceTo(user)));
            stand.setPos(stand.position().add(
                    forward.scale(dash)));
            LivingEntity target = level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(
                                    stand.position(),
                                    stand.position())
                                    .inflate(2.8D),
                            candidate -> candidate != user
                                    && candidate != stand
                                    && candidate.isAlive()
                                    && !candidate.isAlliedTo(user))
                    .stream()
                    .min(Comparator.comparingDouble(
                            candidate -> candidate
                                    .distanceToSqr(stand)))
                    .orElse(null);
            if (target != null) {
                float damage =
                        (float) stand.getAttackDamage() * 1.5F;
                // 1.16 SuperHeavyPunch hurt the target directly, so the
                // Stand damage multiplier never scaled it.
                DamageSource source = makePunchDamageSource();
                ((DamageSourceModified) source)
                        .jojo_ripples$setFixedDamage(true);
                if (standEntityAttack(
                        stand,
                        target,
                        source,
                        damage)) {
                    target.addEffect(new MobEffectInstance(
                            MobEffects.CONFUSION,
                            100,
                            1));
                    target.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            120,
                            2));
                    target.addEffect(new MobEffectInstance(
                            MobEffects.BLINDNESS,
                            60,
                            0));
                    target.push(
                            forward.x * 0.8D,
                            0.3D,
                            forward.z * 0.8D);
                    target.playSound(
                            ShadowTheWorldAddon
                                    .SHADOW_WORLD_SUPER_PUNCH
                                    .get(),
                            1.5F,
                            0.7F + stand.getRandom()
                                    .nextFloat() * 0.6F);
                }
            }
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    (Ability) superHeavy,
                    power,
                    STAMINA,
                    false);
            superHeavy.setCooldownOnUse(power);
            ShadowAbilitySupport.unsummonIfUnstable(user);
        }
    }
}
