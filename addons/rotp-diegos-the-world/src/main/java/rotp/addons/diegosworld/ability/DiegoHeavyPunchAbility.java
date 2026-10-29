package rotp.addons.diegosworld.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.diegosworld.DiegoGameEvents;
import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class DiegoHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public DiegoHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = DiegoHeavyPunch::new;
        // donor heavy punch was a SHIFT variation: shouts while sneaking
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
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, DiegosTheWorldAddon.DIEGO_DIE);
        }
    }

    public static final class DiegoHeavyPunch
            extends StandEntityHeavyPunch {
        public DiegoHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected Holder<SoundEvent> getHeavyPunchImpactSound(
                ActionTarget target) {
            return target.getType() == TargetType.ENTITY
                    ? ModSoundEvents
                            .THE_WORLD_PUNCH_HEAVY_ENTITY
                    : ModSoundEvents.STAND_PUNCH_HEAVY;
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
            if (!hurt
                    || !(target.level()
                            instanceof ServerLevel serverLevel)) {
                return;
            }
            if (!target.isAlive()) {
                LivingEntity user = stand.getUser();
                if (user != null) {
                    JojoModUtil.sayVoiceLine(
                            user,
                            DiegosTheWorldAddon
                                    .DIEGO_THIS_IS_THE_WORLD,
                            1.0F,
                            1.0F,
                            0,
                            true);
                }
            }
            TimeStopState state = serverLevel.getData(
                    ModDataAttachmentTypes.TIME_STOP.get());
            if (state.shouldFreeze(target)) {
                DiegoGameEvents.markHeavyPunchInTimeStop(
                        stand, target);
                state.queueOnTimeResume(
                        target,
                        () -> target.playSound(
                                ModSoundEvents
                                        .THE_WORLD_PUNCH_HEAVY_TS_IMPACT
                                        .get(),
                                1.0F,
                                1.0F));
            }
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified knockback =
                    (DamageSourceModified) damageSource;
            knockback.jojo_ripples$modifyKnockback(5.0F, 1.0F);
            knockback.jojo_ripples$knockbackYRot(-90.0F);
            knockback.jojo_ripples$knockbackXRot(0.0F);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            if (target.getMainEntity()
                    instanceof LivingEntity livingTarget) {
                float armorPiercing =
                        (float) stand.getAttackDamage() * 0.01F;
                damage = DamageUtil.addArmorPiercing(
                        damage,
                        armorPiercing,
                        livingTarget,
                        damageSource);
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }
    }
}
