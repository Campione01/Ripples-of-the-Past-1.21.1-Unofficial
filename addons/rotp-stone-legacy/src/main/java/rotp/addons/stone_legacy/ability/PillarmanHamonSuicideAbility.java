package rotp.addons.stone_legacy.ability;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonBlastExplosion;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBreathAbility;
import rotp.core.impl.powers.pillarman.PillarmanData;

import javax.annotation.Nullable;

import rotp.addons.stone_legacy.StoneLegacyAddon;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PillarmanHamonSuicideAbility
        extends EntityActionAbility {
    public static final int HOLD_TO_FIRE_TICKS = 100;

    public PillarmanHamonSuicideAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                HamonSuicideInstance::new);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setIgnoresPerformerStun();
    }

    @Override
    public String getResourceNamespace() {
        return StoneLegacyAddon.MOD_ID;
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    // 1.16 holdToFire(100, false) held the suicide until it fired, so a
    // release from any bind still cancels the windup. Its own Hamon
    // self-damage never stopped the hold: 1.16 did not cancel it on hits.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && isHamonOriginPillarman(context);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!isHamonOriginPillarman(context)) {
            return ConditionCheck.NEGATIVE;
        }
        return checkNotInStoneForm(context);
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkNotInStoneForm(context);
    }

    // 1.16 PillarmanAction.checkConditions (canBeUsedInStone = false),
    // on the press and on every held tick.
    private static ConditionCheck checkNotInStoneForm(
            Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        boolean stoneForm = user != null && PlayerPower
                .getPowerData(user, ModPlayerPowers.PILLAR_MAN)
                .map(PillarmanData::isStoneFormEnabled)
                .orElse(false);
        return stoneForm
                ? ConditionCheck.createNegative("stone_form")
                : ConditionCheck.POSITIVE;
    }

    // 1.16 HamonEnergySound: energy / max energy (at least 1);
    // 1 with no Pillar Man data.
    public static float energySoundVolume(
            @Nullable PillarmanData data,
            @Nullable LivingEntity user) {
        if (data == null || user == null) {
            return 1.0F;
        }
        return Mth.clamp(
                data.getEnergy()
                        / Math.max(data.getMaxEnergy(user), 1.0F),
                0.0F,
                1.0F);
    }

    private static boolean isHamonOriginPillarman(
            Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        return context instanceof PlayerPower playerPower
                && user != null
                && playerPower.getPowerType()
                        == ModPlayerPowers.PILLAR_MAN.get()
                && StoneLegacyAddon.state(user).hamonOrigin();
    }

    public static final class HamonSuicideInstance
            extends EntityActionInstance {
        public HamonSuicideInstance(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.WINDUP
                    && level().isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    // 1.16 HamonEnergySound: follows the energy bar
                    // while held, then -0.1 per tick.
                    ClientsideSoundsHelper.playLoopingActionSound(
                            ModSoundEvents
                                    .HAMON_CONCENTRATION.get(),
                            user,
                            this,
                            ActionPhase.WINDUP,
                            1.0F,
                            1.0F,
                            0,
                            HamonBreathAbility.BREATH_SOUND_FADE_STEP,
                            () -> energySoundVolume());
                }
            }
        }

        public float energySoundVolume() {
            LivingEntity user = getPowerUser();
            return PillarmanHamonSuicideAbility.energySoundVolume(
                    user != null
                            ? PlayerPower.getPowerData(
                                            user,
                                            ModPlayerPowers.PILLAR_MAN)
                                    .orElse(null)
                            : null,
                    user);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.WINDUP) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null || level().isClientSide()) {
                return;
            }
            int tick = (int) getPhaseTick();
            if (tick % 10 == 5) {
                HamonAbilityHelpers.hamonHurt(
                        user, 4.0F, user, user);
            }
            if (tick == 30) {
                user.addEffect(new MobEffectInstance(
                        ModStatusEffects.HAMON_SPREAD,
                        100,
                        1,
                        false,
                        false,
                        true));
            }
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            HamonAbilityHelpers.hamonHurt(
                    user, 200.0F, user, user);
            Vec3 center = user.getBoundingBox().getCenter();
            HamonBlastExplosion explosion =
                    new HamonBlastExplosion(
                            level,
                            user,
                            HamonAbilityHelpers
                                    .hamonDamageSource(
                                            level, user, user),
                            center.x,
                            center.y,
                            center.z,
                            6.0F);
            explosion.setHamonDamage(6.0F);
            CustomExplosion.explode(explosion);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }
    }
}
