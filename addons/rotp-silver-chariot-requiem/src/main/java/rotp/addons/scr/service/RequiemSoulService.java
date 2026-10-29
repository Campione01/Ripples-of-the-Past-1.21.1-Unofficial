package rotp.addons.scr.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.scr.SilverChariotRequiemAddon;
import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import rotp.addons.scr.state.RequiemSoulState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class RequiemSoulService {
    public static final double FIELD_RADIUS = 80.0D;
    public static final int PULSE_INTERVAL_TICKS = 20;
    public static final int SLUMBER_TICKS = 100;
    public static final int MAX_SLUMBER_TICKS = 200;
    public static final int SOUL_LINK_TICKS = 1200;
    public static final int SOURCE_GRACE_TICKS = 40;
    public static final int REENTRY_COOLDOWN_TICKS = 200;
    public static final int MAX_TARGETS_PER_PULSE = 32;

    private RequiemSoulService() {}

    public static void tickStand(
            SilverChariotRequiemEntity stand) {
        if (!(stand.level() instanceof ServerLevel level)
                || stand.tickCount % PULSE_INTERVAL_TICKS != 0) {
            return;
        }
        LivingEntity owner = stand.getUser();
        if (owner == null
                || !owner.isAlive()
                || owner.isRemoved()) {
            return;
        }
        if (SilverChariotRequiemAddon.activeStand(owner) != stand) {
            clearBySource(stand, level);
            return;
        }

        List<LivingEntity> candidates =
                new ArrayList<>(level.getEntitiesOfClass(
                        LivingEntity.class,
                        stand.getBoundingBox().inflate(FIELD_RADIUS),
                        target -> canEnterField(
                                stand, owner, target)));
        candidates.sort(
                Comparator.comparingDouble(
                                (LivingEntity target) ->
                                        stand.distanceToSqr(target))
                        .thenComparing(
                                target -> target.getUUID()
                                        .toString()));

        int admitted = 0;
        for (LivingEntity target : candidates) {
            RequiemSoulState state =
                    SilverChariotRequiemAddon.soulState(target);
            if (state.canEnterField()
                    && admitted < MAX_TARGETS_PER_PULSE) {
                state.beginSlumber(
                        stand.getUUID(), owner.getUUID());
                admitted++;
            }
        }

        List<LivingEntity> ready = candidates.stream()
                .filter(target -> isReadyForExchange(
                        stand, target))
                .toList();
        for (int i = 0; i + 1 < ready.size(); i += 2) {
            exchangePair(
                    stand, ready.get(i), ready.get(i + 1));
        }
    }

    public static void tickTarget(LivingEntity target) {
        RequiemSoulState state =
                SilverChariotRequiemAddon.soulState(target);
        state.tickPhase();
        if (!state.isActive()) {
            return;
        }

        SilverChariotRequiemEntity source =
                resolveSource(target, state);
        if (source == null) {
            if (state.markSourceMissing()
                    >= SOURCE_GRACE_TICKS) {
                clearStateAndPartner(target, state);
            }
            return;
        }
        state.markSourcePresent();
        LivingEntity owner = source.getUser();
        if (owner == null
                || !canRemainInField(source, owner, target)) {
            clearStateAndPartner(target, state);
            return;
        }

        if (state.isSlumbering()) {
            if (state.phaseTicks() > MAX_SLUMBER_TICKS) {
                clearStateAndPartner(target, state);
                return;
            }
            enforceSlumber(target);
            return;
        }
        if (state.linkTicks() <= 0) {
            clearStateAndPartner(target, state);
            return;
        }
        if (!hasValidPartner(target, state)) {
            state.clear(REENTRY_COOLDOWN_TICKS);
            return;
        }
        target.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION,
                5,
                0,
                false,
                false,
                false));
    }

    public static void clearOnDeath(LivingEntity target) {
        clearOnDeparture(target);
    }

    public static void clearOnDeparture(LivingEntity target) {
        ServerLevel level =
                target.level() instanceof ServerLevel serverLevel
                        ? serverLevel
                        : null;
        clearOnDeparture(target, level);
    }

    public static void clearOnDeparture(
            LivingEntity target, ServerLevel level) {
        RequiemSoulState state =
                SilverChariotRequiemAddon.soulState(target);
        clearStateAndPartner(target, state, level);
    }

    public static void clearBySource(
            SilverChariotRequiemEntity stand,
            ServerLevel level) {
        UUID sourceId = stand.getUUID();
        List<LivingEntity> targets =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        stand.getBoundingBox().inflate(FIELD_RADIUS),
                        target -> !(target instanceof StandEntity)
                                && sourceId.equals(
                                        SilverChariotRequiemAddon
                                                .soulState(target)
                                                .sourceStandId()));
        for (LivingEntity target : targets) {
            clearStateAndPartner(
                    target,
                    SilverChariotRequiemAddon.soulState(target),
                    level);
        }
    }

    private static boolean canEnterField(
            SilverChariotRequiemEntity stand,
            LivingEntity owner,
            LivingEntity target) {
        if (target == owner
                || target == stand
                || target instanceof StandEntity
                || !target.isAlive()
                || target.isRemoved()
                || target.isSpectator()
                || stand.distanceToSqr(target)
                        > FIELD_RADIUS * FIELD_RADIUS) {
            return false;
        }
        return !(target instanceof Player player)
                || !player.isCreative();
    }

    private static boolean canRemainInField(
            SilverChariotRequiemEntity stand,
            LivingEntity owner,
            LivingEntity target) {
        return canEnterField(stand, owner, target);
    }

    private static boolean isReadyForExchange(
            SilverChariotRequiemEntity stand,
            LivingEntity target) {
        RequiemSoulState state =
                SilverChariotRequiemAddon.soulState(target);
        return state.isSlumbering()
                && stand.getUUID().equals(
                        state.sourceStandId())
                && state.phaseTicks() >= SLUMBER_TICKS;
    }

    private static SilverChariotRequiemEntity resolveSource(
            LivingEntity target, RequiemSoulState state) {
        if (!(target.level() instanceof ServerLevel level)
                || state.sourceStandId() == null
                || state.sourceOwnerId() == null) {
            return null;
        }
        Entity sourceEntity =
                level.getEntity(state.sourceStandId());
        if (!(sourceEntity
                instanceof SilverChariotRequiemEntity source)
                || !source.isAlive()
                || source.isRemoved()
                || source.getUser() == null
                || !state.sourceOwnerId().equals(
                        source.getUser().getUUID())
                || SilverChariotRequiemAddon.activeStand(
                        source.getUser()) != source) {
            return null;
        }
        return source;
    }

    private static void enforceSlumber(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                5,
                10,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                5,
                4,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS,
                5,
                0,
                false,
                false,
                false));
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(
                0.0D, Math.min(0.0D, motion.y), 0.0D);
        target.setSprinting(false);
        target.hasImpulse = true;

        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(target);
        if (action != null) {
            action.startRecovery();
        }
        // the target's own Stand actions, the time stop among them, run in
        // its Stand-power slot, apart from its own slot
        EntityActionInstance standPowerAction =
                LivingComponentAction.getCurEntityAction(
                        target, PowerClass.STAND);
        if (standPowerAction != null && standPowerAction != action) {
            standPowerAction.startRecovery();
        }
    }

    private static void exchangePair(
            SilverChariotRequiemEntity stand,
            LivingEntity first,
            LivingEntity second) {
        if (first == second
                || first.level() != second.level()
                || !first.isAlive()
                || !second.isAlive()) {
            return;
        }
        RequiemSoulState firstState =
                SilverChariotRequiemAddon.soulState(first);
        RequiemSoulState secondState =
                SilverChariotRequiemAddon.soulState(second);
        if (!firstState.isSlumbering()
                || !secondState.isSlumbering()
                || !stand.getUUID().equals(
                        firstState.sourceStandId())
                || !stand.getUUID().equals(
                        secondState.sourceStandId())) {
            return;
        }

        BodySnapshot firstBody = BodySnapshot.capture(first);
        BodySnapshot secondBody = BodySnapshot.capture(second);
        secondBody.applyTo(first);
        firstBody.applyTo(second);
        firstState.markExchanged(
                second.getUUID(), SOUL_LINK_TICKS);
        secondState.markExchanged(
                first.getUUID(), SOUL_LINK_TICKS);
        notifyExchange(first, second);
        notifyExchange(second, first);
    }

    private static void notifyExchange(
            LivingEntity entity, LivingEntity partner) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_silver_chariot_requiem"
                                    + ".soul_exchange",
                            partner.getDisplayName()),
                    true);
        }
    }

    private static boolean hasValidPartner(
            LivingEntity target, RequiemSoulState state) {
        if (!(target.level() instanceof ServerLevel level)
                || state.partnerId() == null) {
            return false;
        }
        Entity partnerEntity =
                level.getEntity(state.partnerId());
        if (!(partnerEntity instanceof LivingEntity partner)
                || partner instanceof StandEntity
                || !partner.isAlive()
                || partner.isRemoved()) {
            return false;
        }
        RequiemSoulState partnerState =
                SilverChariotRequiemAddon.soulState(partner);
        return partnerState.isExchanged()
                && target.getUUID().equals(
                        partnerState.partnerId())
                && state.sourceStandId() != null
                && state.sourceStandId().equals(
                        partnerState.sourceStandId());
    }

    private static void clearStateAndPartner(
            LivingEntity target, RequiemSoulState state) {
        ServerLevel level =
                target.level() instanceof ServerLevel serverLevel
                        ? serverLevel
                        : null;
        clearStateAndPartner(target, state, level);
    }

    private static void clearStateAndPartner(
            LivingEntity target,
            RequiemSoulState state,
            ServerLevel level) {
        UUID partnerId = state.partnerId();
        if (partnerId != null && level != null) {
            Entity partnerEntity = level.getEntity(partnerId);
            if (partnerEntity instanceof LivingEntity partner
                    && !(partner instanceof StandEntity)) {
                RequiemSoulState partnerState =
                        SilverChariotRequiemAddon
                                .soulState(partner);
                if (target.getUUID().equals(
                        partnerState.partnerId())) {
                    partnerState.clear(
                            REENTRY_COOLDOWN_TICKS);
                }
            }
        }
        state.clear(REENTRY_COOLDOWN_TICKS);
    }

    private record BodySnapshot(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Vec3 motion,
            float healthRatio,
            float absorption,
            int air,
            int fireTicks) {
        private static BodySnapshot capture(
                LivingEntity entity) {
            return new BodySnapshot(
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.getYRot(),
                    entity.getXRot(),
                    entity.getDeltaMovement(),
                    entity.getHealth()
                            / Math.max(
                                    1.0F,
                                    entity.getMaxHealth()),
                    entity.getAbsorptionAmount(),
                    entity.getAirSupply(),
                    entity.getRemainingFireTicks());
        }

        private void applyTo(LivingEntity entity) {
            entity.teleportTo(x, y, z);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.setYHeadRot(yaw);
            entity.yBodyRot = yaw;
            entity.setDeltaMovement(motion);
            entity.setHealth(Mth.clamp(
                    healthRatio * entity.getMaxHealth(),
                    1.0F,
                    entity.getMaxHealth()));
            entity.setAbsorptionAmount(
                    Math.max(0.0F, absorption));
            entity.setAirSupply(air);
            entity.setRemainingFireTicks(
                    Math.max(0, fireTicks));
            entity.hasImpulse = true;
        }
    }
}
