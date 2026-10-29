package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonDonorGameplaySpec;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.EpitaphState.PredictionKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EpitaphStateService {
    private static final double MIN_HORIZONTAL_LENGTH = 1.0E-6D;

    private EpitaphStateService() {}

    public static boolean arm(
            ServerPlayer player, PredictionKind prediction) {
        if (!KingCrimsonAddon.isKingCrimsonUser(player)
                || !player.isAlive()) {
            return false;
        }
        StandPower power = StandPower.get(player);
        long expiresAt = player.level().getGameTime()
                + KingCrimsonEpitaphForesightSpec.windowTicks(
                        power != null ? power.getResolveLevel() : 0);
        boolean changed = KingCrimsonAddon.epitaphState(player)
                .arm(prediction, expiresAt);
        if (changed) {
            KingCrimsonAddon.syncEpitaphState(player);
        }
        return changed;
    }

    public static void tick(ServerPlayer player) {
        EpitaphState state =
                KingCrimsonAddon.epitaphState(player);
        long gameTime = player.level().getGameTime();
        if (!player.isAlive()
                || !KingCrimsonAddon.isKingCrimsonUser(player)) {
            terminate(player);
            return;
        }
        EpitaphForesightService.prune(player);
        if (state.stage() == EpitaphState.Stage.IDLE) {
            return;
        }
        if (gameTime >= state.expiresAt()) {
            disarm(player);
            return;
        }
        if (state.isArmed(gameTime)) {
            EpitaphForesightService.tick(player);
        }
    }

    public static boolean tryDodge(
            ServerPlayer player, DamageSource source) {
        EpitaphState state =
                KingCrimsonAddon.epitaphState(player);
        long gameTime = player.level().getGameTime();
        if (!state.isArmed(gameTime)
                || !matchesPrediction(
                        state.predictionKind(), source)) {
            return false;
        }

        LivingEntity attacker = resolveAttacker(source);
        boolean highResolve = state.predictionKind()
                == PredictionKind.ANY_NON_BYPASSING;
        if (highResolve && isFallOrLightning(source)) {
            // 1.16 EpitaphEffect.onLivingAttack at resolve 3 and above: a fall or a lightning strike is skipped
            // outright - the Time Skip flash and sound, no stamina spent and no move
            showEpitaphTimeSkip(player);
        } else {
            // 1.16: with no attacker to appear behind (fire, lava, a cactus...) the 150 stamina still buys the
            // dodge where the user stands; only without it does the user dash back
            boolean futureSkip = highResolve
                    && (attacker != null
                            ? tryFutureSkip(player, attacker)
                            : consumeFutureStamina(player));
            if (!futureSkip) {
                moveToSafeDodgePosition(player, attacker);
                playDodgeSounds(player);
            }
        }
        ResourceLocation damageType = source.typeHolder()
                .unwrapKey()
                .map(key -> key.location())
                .orElse(null);
        state.recordDodge(
                attacker != null ? attacker.getUUID() : null,
                damageType,
                gameTime
                        + KingCrimsonPhase3Spec
                                .DODGE_FEEDBACK_TICKS);
        KingCrimsonAddon.syncEpitaphState(player);
        return true;
    }

    public static void terminate(ServerPlayer player) {
        EpitaphForesightService.releaseAll(player);
        disarm(player);
    }

    public static void disarm(ServerPlayer player) {
        // Ending the vision does not cancel the course it already showed.
        if (KingCrimsonAddon.epitaphState(player).clear()) {
            KingCrimsonAddon.syncEpitaphState(player);
        }
    }

    public static void terminateIfArmed(ServerPlayer player) {
        EpitaphState state =
                KingCrimsonAddon.epitaphState(player);
        if (state.stage() != EpitaphState.Stage.ARMED) {
            return;
        }
        disarm(player);
    }

    private static boolean matchesPrediction(
            PredictionKind prediction, DamageSource source) {
        if (source.is(
                DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (prediction == PredictionKind.ANY_NON_BYPASSING) {
            return true;
        }
        // 1.16 below resolve 3: every hit is dodged except a fall, a lightning strike and an explosion - projectiles
        // and damage with no attacker at all included
        return prediction == PredictionKind.DIRECT_ATTACK
                && !isFallOrLightning(source)
                && !source.is(DamageTypeTags.IS_EXPLOSION);
    }

    // 1.16 KingCrimsonEpitaph: DamageSource.FALL or DamageSource.LIGHTNING_BOLT (field_180137_b)
    private static boolean isFallOrLightning(DamageSource source) {
        return source.is(DamageTypes.FALL)
                || source.is(DamageTypes.LIGHTNING_BOLT);
    }

    private static boolean consumeFutureStamina(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.consumeStamina(
                        KingCrimsonDonorGameplaySpec
                                .EPITAPH_FUTURE_STAMINA);
    }

    // 1.16 VFXServerHelper.startVFX(user, true): the Time Skip flash goes to every player within 192 blocks and
    // shows for the King Crimson users among them; the epitaph_timeskip sound plays at the user
    private static void showEpitaphTimeSkip(ServerPlayer player) {
        double radiusSqr = KingCrimsonPhase5Spec
                .TIME_SKIP_PRESENTATION_RADIUS
                * KingCrimsonPhase5Spec
                        .TIME_SKIP_PRESENTATION_RADIUS;
        for (ServerPlayer observer :
                player.serverLevel().players()) {
            if (observer.distanceToSqr(player) <= radiusSqr) {
                PacketDistributor.sendToPlayer(
                        observer,
                        TimeEraseTransitionPayload.INSTANCE);
            }
        }
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                KingCrimsonAddon.EPITAPH_TIMESKIP.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    @Nullable
    private static LivingEntity resolveAttacker(
            DamageSource source) {
        LivingEntity indirect = resolveLiving(
                source.getEntity());
        return indirect != null
                ? indirect
                : resolveLiving(source.getDirectEntity());
    }

    @Nullable
    private static LivingEntity resolveLiving(
            @Nullable Entity entity) {
        if (entity instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private static boolean tryFutureSkip(
            ServerPlayer player,
            @Nullable LivingEntity attacker) {
        if (attacker == null
                || !KingCrimsonTemporalStateService
                        .canStartTimeSkip(player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getResolveLevel()
                        < KingCrimsonDonorGameplaySpec
                                .HIGH_RESOLVE_LEVEL
                || !power.consumeStamina(
                        KingCrimsonDonorGameplaySpec
                                .EPITAPH_FUTURE_STAMINA)
                || !KingCrimsonTemporalStateService
                        .beginEpitaphTimeSkip(player, attacker)) {
            return false;
        }
        attacker.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                KingCrimsonDonorGameplaySpec
                        .EPITAPH_TARGET_SLOWNESS_TICKS,
                KingCrimsonDonorGameplaySpec
                        .EPITAPH_TARGET_SLOWNESS_AMPLIFIER,
                false,
                false,
                false));
        return true;
    }

    private static void moveToSafeDodgePosition(
            ServerPlayer player,
            @Nullable LivingEntity attacker) {
        Vec3 primary = attacker != null
                ? player.position().subtract(attacker.position())
                : player.getLookAngle().scale(-1.0D);
        primary = horizontalUnit(primary, player);
        Vec3 right = new Vec3(-primary.z, 0.0D, primary.x);
        Vec3 backward = horizontalUnit(
                player.getLookAngle().scale(-1.0D), player);

        for (Vec3 direction : new Vec3[] {
                primary, right, right.scale(-1.0D), backward}) {
            Vec3 offset = direction.scale(
                    KingCrimsonPhase3Spec.DODGE_DISTANCE);
            for (int rise = 0; rise <= 1; rise++) {
                Vec3 candidate = player.position()
                        .add(offset)
                        .add(0.0D, rise, 0.0D);
                if (isSafe(player.level(), player, candidate)) {
                    player.teleportTo(
                            candidate.x,
                            candidate.y,
                            candidate.z);
                    return;
                }
            }
        }
    }

    private static Vec3 horizontalUnit(
            Vec3 vector, ServerPlayer player) {
        Vec3 horizontal = new Vec3(
                vector.x, 0.0D, vector.z);
        if (horizontal.lengthSqr() < MIN_HORIZONTAL_LENGTH) {
            float yaw = player.getYRot()
                    * ((float) Math.PI / 180.0F);
            horizontal = new Vec3(
                    Math.sin(yaw), 0.0D, -Math.cos(yaw));
        }
        return horizontal.normalize();
    }

    private static boolean isSafe(
            Level level,
            ServerPlayer player,
            Vec3 candidate) {
        BlockPos pos = BlockPos.containing(candidate);
        if (!level.hasChunkAt(pos)
                || pos.getY() < level.getMinBuildHeight()
                || pos.getY() + 2 >= level.getMaxBuildHeight()
                || level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below())
                        .isEmpty()) {
            return false;
        }
        Vec3 movement = candidate.subtract(player.position());
        return level.noCollision(
                player,
                player.getBoundingBox().move(movement));
    }

    private static void playDodgeSounds(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                KingCrimsonAddon.KINGCRIMSON_DASH.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                KingCrimsonAddon.DIAVOLO_DASH.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }
}
