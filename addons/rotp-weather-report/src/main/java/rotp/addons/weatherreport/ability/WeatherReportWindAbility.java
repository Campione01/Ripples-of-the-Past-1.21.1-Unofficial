package rotp.addons.weatherreport.ability;

import javax.annotation.Nullable;

import rotp.core.init.ModBlocks;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.block.BloodPuddleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WeatherReportWindAbility extends StandEntityAbility {
    public static final double RANGE = 24.0D;
    public static final int MAX_HOLD_TICKS = 120;
    public static final float STAMINA_COST_TICK = 2.0F;
    public static final int COOLDOWN_TICKS = 80;
    public static final double CONE_DOT_THRESHOLD = 0.886D;
    public static final double BASE_PUSH = 0.5D;

    private static final ActionAnimIdentifier WIND_BLOW_ANIM =
            ActionAnimIdentifier.getOrCreate("wind_blow", false);

    private final double range;
    private final int maxHoldTicks;
    private final float staminaCostTick;
    private final boolean cold;

    public WeatherReportWindAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        this(
                abilityType,
                abilityId,
                RANGE,
                MAX_HOLD_TICKS,
                STAMINA_COST_TICK,
                COOLDOWN_TICKS,
                false);
    }

    protected WeatherReportWindAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            double range,
            int maxHoldTicks,
            float staminaCostTick,
            int cooldownTicks,
            boolean cold) {
        super(abilityType, abilityId, WindAction::new);
        this.range = range;
        this.maxHoldTicks = maxHoldTicks;
        this.staminaCostTick = staminaCostTick;
        this.cold = cold;
        setDefaultPhaseLength(ActionPhase.PERFORM, maxHoldTicks);
        partsRequired(cold ? StandPart.MAIN_BODY : StandPart.ARMS);
        cooldown(cooldownTicks);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 holdType: PowerBaseImpl.tickHeldAction rechecked the blowing wind
    // on every held tick (a stunned Stand or a lost Stand part ended it).
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        int cooldown = super.getCooldown(context, ticksHeld);
        if (cooldown <= 0) {
            return 0;
        }
        int held = Mth.clamp(ticksHeld, 0, maxHoldTicks);
        return cooldown * held / maxHoldTicks;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return WIND_BLOW_ANIM;
    }

    private void playActionSound(StandEntity stand) {
        StandUtil.playStandEntitySound(
                stand,
                cold
                        ? WeatherReportAddon
                                .WEATHER_REPORT_COLD_WIND_SOUND
                        : WeatherReportAddon
                                .WEATHER_REPORT_WIND_SOUND,
                1.0F,
                1.0F);
    }

    public static final class WindAction
            extends EntityActionInstance {
        private boolean cooldownSet;
        private float heldTicksPassed;

        public WindAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 weather_report_wind and _cold_wind standOffsetFront(),
            // turned by the view yaw
            setStandOffset(
                    0.0D,
                    0.5D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
            if (!level().isClientSide()
                    && performer instanceof StandEntity stand
                    && ability
                            instanceof WeatherReportWindAbility wind) {
                wind.playActionSound(stand);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.PERFORM) {
                heldTicksPassed = getFullTicksPassed();
            }
            if (getPhase() != ActionPhase.PERFORM
                    || !(performer instanceof StandEntity stand)
                    || !(ability
                            instanceof WeatherReportWindAbility wind)) {
                return;
            }
            // 1.16 PowerBaseImpl.tickHeldAction counted the hold maximum from the press, through the summon lock
            if (!level().isClientSide()
                    && heldTicksPassed + getTicksHeldInSummonLock()
                            >= wind.maxHoldTicks) {
                startRecovery();
                return;
            }

            LivingEntity user = getPowerUser();
            if (!(level() instanceof ServerLevel serverLevel)) {
                // 1.16 aimed the cold wind on the client too, so the Stand is seen turning to the aim.
                if (wind.cold) {
                    WeatherReportAim.aimWithStandOrUser(stand, user, wind.range);
                }
                return;
            }
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask paid the ticking cost every perform tick
            // with the result ignored: the wind blows on an empty bar.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    wind.staminaCostTick,
                    true);

            applyWind(serverLevel, stand, wind);
            spawnWindParticles(serverLevel, stand, user, wind.cold);
            if (wind.cold) {
                extinguishBlocks(serverLevel, stand, user, wind.range);
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                setCooldownFromHeldTicks();
            }
        }

        // 1.16 stopHeldAction: a replaced, unsummoned or force-stopped hold still gets its cooldown.
        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            setCooldownFromHeldTicks();
        }

        private void setCooldownFromHeldTicks() {
            if (cooldownSet || level().isClientSide()
                    || !(ability
                            instanceof WeatherReportWindAbility wind)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower =
                    user != null ? StandPower.get(user) : null;
            if (standPower != null) {
                // forceStop clears the phase first; use the last held tick then.
                float ticksPassed = (getPhase() != null
                        ? getFullTicksPassed()
                        : heldTicksPassed) + getTicksHeldInSummonLock();
                int ticksHeld = Mth.floor(Math.min(
                        ticksPassed,
                        wind.maxHoldTicks));
                wind.setCooldownOnUse(standPower, ticksHeld);
                cooldownSet = true;
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 WeatherReportWind and WeatherReportColdWind (StandEntityAction, no isChainable) refused their own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        // 1.16 weather_report_wind holdType(120) and weather_report_cold_wind
        // holdType(80) were holdType Stand actions
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }

    private static void applyWind(
            ServerLevel level,
            StandEntity stand,
            WeatherReportWindAbility ability) {
        Vec3 look = stand.getLookAngle().normalize();
        Vec3 standPos = stand.position();
        for (Entity entity : level.getEntities(
                stand,
                stand.getBoundingBox().inflate(ability.range),
                entity -> {
                    Vec3 relative = entity.position()
                            .subtract(standPos);
                    return relative.lengthSqr() > 0.5D
                            && look.dot(relative.normalize())
                                    > CONE_DOT_THRESHOLD;
                })) {
            if (!canWindAffect(level, entity)) {
                continue;
            }

            double distance = entity.distanceTo(stand);
            Vec3 push = look.scale(
                    BASE_PUSH * stand.staminaCondition);
            entity.setDeltaMovement(distance > 2.0D
                    ? entity.getDeltaMovement().add(
                            push.scale(2.0D / distance))
                    : push.scale(Math.max(distance - 1.0D, 0.0D)));
            entity.hasImpulse = true;
            entity.hurtMarked = true;

            if (ability.cold
                    && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        ModStatusEffects.FREEZE,
                        WeatherReportColdWindAbility
                                .FREEZE_DURATION_TICKS,
                        0));
                if (living.isOnFire()) {
                    living.setRemainingFireTicks(
                            WeatherReportColdWindAbility
                                    .REMAINING_FIRE_TICKS);
                }
                if (living instanceof Blaze) {
                    living.hurt(
                            living.damageSources().drown(),
                            WeatherReportColdWindAbility
                                    .BLAZE_DAMAGE);
                }
            }
        }
    }

    private static boolean canWindAffect(
            ServerLevel level, Entity entity) {
        return !level.hasData(
                ModDataAttachmentTypes.TIME_STOP.get())
                || !level.getData(
                        ModDataAttachmentTypes.TIME_STOP.get())
                        .shouldFreeze(entity);
    }

    private static void spawnWindParticles(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            boolean cold) {
        Vec3 look = stand.getLookAngle();
        for (int i = 0; i < 5; i++) {
            Vec3 userPos = stand.position().add(
                    (stand.getRandom().nextDouble() - 0.5D)
                            * (user.getBbWidth() + 1.0D),
                    stand.getRandom().nextDouble()
                            * (user.getBbHeight() + 1.0D),
                    (stand.getRandom().nextDouble() - 0.5D)
                            * (user.getBbWidth() + 1.0D));
            Vec3 particleOffset = look.scale(2.0D)
                    .xRot((float) ((stand.getRandom().nextDouble()
                            * 2.0D - 1.0D) * Math.PI / 6.0D))
                    .yRot((float) ((stand.getRandom().nextDouble()
                            * 2.0D - 1.0D) * Math.PI / 6.0D));
            Vec3 particlePos = userPos.add(particleOffset);
            Vec3 towardStand = userPos.subtract(particlePos)
                    .normalize()
                    .scale(0.75D);
            sendVelocityParticle(
                    level,
                    ModParticles.AIR_STREAM.get(),
                    particlePos,
                    towardStand);
            if (cold) {
                sendVelocityParticle(
                        level,
                        ParticleTypes.CLOUD,
                        particlePos,
                        towardStand.scale(-1.0D));
            }
        }
    }

    private static <T extends ParticleOptions> void sendVelocityParticle(
            ServerLevel level,
            T particle,
            Vec3 position,
            Vec3 velocity) {
        level.sendParticles(
                particle,
                position.x,
                position.y,
                position.z,
                0,
                velocity.x,
                velocity.y,
                velocity.z,
                1.0D);
    }

    private static void extinguishBlocks(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            double range) {
        // 1.16 WeatherReportColdWind: stand.aimWithStandOrUser(RANGE, task.getTarget()), the task keeping no target.
        Vec3 targetPos = WeatherReportAim.aimWithStandOrUser(
                stand, user, range);
        BlockPos center = BlockPos.containing(targetPos);

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    extinguishBlock(
                            level, center.offset(x, y, z));
                }
            }
        }
    }

    private static void extinguishBlock(
            ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.CAMPFIRES)
                && state.hasProperty(CampfireBlock.LIT)) {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(CampfireBlock.LIT, false));
        }
        else if (state.is(Blocks.FIRE)
                || state.is(ModBlocks.MAGICIANS_RED_FIRE.get())) {
            level.setBlockAndUpdate(
                    pos, Blocks.AIR.defaultBlockState());
        }
        else if (state.is(WeatherReportAddon.BLOOD_PUDDLE.get())) {
            level.setBlockAndUpdate(
                    pos,
                    WeatherReportAddon.BLOOD_SPIKES.get()
                            .defaultBlockState()
                            .setValue(
                                    BloodPuddleBlock.STAGE,
                                    state.getValue(
                                            BloodPuddleBlock.STAGE))
                            .setValue(
                                    BloodPuddleBlock.FACING,
                                    state.getValue(
                                            BloodPuddleBlock.FACING)));
        }
    }
}
