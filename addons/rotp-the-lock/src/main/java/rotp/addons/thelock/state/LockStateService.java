package rotp.addons.thelock.state;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.entity.TheLockStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class LockStateService {
    private LockStateService() {}

    public static LivingEntity stateTarget(LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static boolean lock(
            LivingEntity rawTarget, LivingEntity owner) {
        LivingEntity target = stateTarget(rawTarget);
        TheLockStandEntity controller =
                owner != null
                        ? TheLockAddon.controller(owner)
                        : null;
        if (target == null
                || owner == null
                || target.level().isClientSide()
                || !target.isAlive()
                || !owner.isAlive()
                || !TheLockAddon.isTheLockUser(owner)
                || owner.level() != target.level()
                || owner.distanceToSqr(target)
                        > LockRules.OWNER_RANGE
                                * LockRules.OWNER_RANGE
                || (controller != null
                        && (!controller.isAlive()
                                || controller.level() != target.level()))) {
            return false;
        }
        LockState state = TheLockAddon.lockState(target);
        if (state.active()) {
            return false;
        }
        state.lock(owner.getUUID(),
                controller != null ? controller.getUUID() : null);
        TheLockAddon.syncLockState(target);
        return true;
    }

    public static boolean tick(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)
                || !target.hasData(TheLockAddon.LOCK_STATE)) {
            return false;
        }
        LockState state = TheLockAddon.lockState(target);
        if (!state.active()) {
            removeLockEffects(target);
            return false;
        }

        LivingEntity owner = leaseOwner(level, target, state);
        if (!target.isAlive()
                || owner == null) {
            clear(target);
            return false;
        }

        int oldAmplifier = LockRules.amplifier(state.guilt());
        state.addGuilt(LockRules.PASSIVE_GUILT_PER_TICK);
        int newAmplifier = LockRules.amplifier(state.guilt());
        if (target.tickCount
                        % LockRules.EFFECT_REFRESH_INTERVAL == 0
                || mirroredEffectNeedsRefresh(
                        target, newAmplifier)) {
            refreshEffect(target, owner, newAmplifier);
        }
        if (oldAmplifier != newAmplifier) {
            refreshEffect(target, owner, newAmplifier);
            TheLockAddon.syncLockState(target);
        }
        return true;
    }

    public static boolean addAttackGuilt(
            LivingEntity rawAttacker,
            LivingEntity rawVictim) {
        LivingEntity attacker = stateTarget(rawAttacker);
        LivingEntity victim = stateTarget(rawVictim);
        if (attacker == null
                || victim == null
                || attacker.level().isClientSide()
                || !attacker.hasData(TheLockAddon.LOCK_STATE)
                || !attacker.hasEffect(TheLockAddon.LOCK_EFFECT)) {
            return false;
        }
        LockState state = TheLockAddon.lockState(attacker);
        if (!state.active()) {
            return false;
        }
        return addGuilt(
                attacker,
                LockRules.attackGuilt(
                        state.ownerUuid().equals(
                                victim.getUUID())));
    }

    public static boolean chargeChat(
            Player speaker, String rawMessage) {
        if (speaker == null
                || rawMessage == null
                || rawMessage.isBlank()
                || speaker.level().isClientSide()) {
            return false;
        }
        // 1.16 GameplayHandler.nearestEntity picks the nearest entity with
        // the lock effect whose plain name (getName, ignoring case; not the
        // team- or nickname-decorated display name) is a word of the
        // message, and only then onServerChat checks the chat flag: an
        // already-charged nearest target absorbs the message instead of
        // passing it to the next one.
        List<LivingEntity> candidates =
                speaker.level().getEntitiesOfClass(
                        LivingEntity.class,
                        speaker.getBoundingBox().inflate(
                                LockRules.CHAT_RANGE),
                        target -> target != speaker
                                && target.hasEffect(
                                        TheLockAddon.LOCK_EFFECT)
                                && mentionsName(
                                        rawMessage,
                                        target.getName().getString()));
        LivingEntity target = candidates.stream()
                .min(Comparator.comparingDouble(
                        speaker::distanceToSqr))
                .orElse(null);
        if (target == null
                || !target.hasData(TheLockAddon.LOCK_STATE)) {
            return false;
        }
        LockState state = TheLockAddon.lockState(target);
        if (!state.markChatCharged()) {
            return false;
        }
        state.addGuilt(LockRules.CHAT_GUILT);
        TheLockAddon.syncLockState(target);
        return true;
    }

    public static boolean clear(LivingEntity rawTarget) {
        LivingEntity target = stateTarget(rawTarget);
        if (target == null || target.level().isClientSide()) {
            return false;
        }
        boolean changed = target.hasData(TheLockAddon.LOCK_STATE)
                && TheLockAddon.lockState(target).clear();
        boolean effectChanged = removeLockEffects(target);
        if (changed || effectChanged) {
            TheLockAddon.syncLockState(target);
            return true;
        }
        return false;
    }

    public static int clearOwnedBy(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return 0;
        }
        int cleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.hasData(
                                TheLockAddon.LOCK_STATE)
                        && TheLockAddon.lockState(living)
                                .isOwnedBy(ownerUuid)
                        && clear(living)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    public static boolean mentionsName(
            String rawMessage, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String rawToken : rawMessage.split("\\s+")) {
            String token = rawToken.replaceFirst("[.,!?;:]$", "");
            if (token.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean addGuilt(
            LivingEntity target, float amount) {
        LockState state = TheLockAddon.lockState(target);
        int oldAmplifier = LockRules.amplifier(state.guilt());
        if (!state.addGuilt(amount)) {
            return false;
        }
        int newAmplifier = LockRules.amplifier(state.guilt());
        LivingEntity owner = target.level()
                instanceof ServerLevel level
                ? leaseOwner(level, target, state)
                : null;
        if (owner == null) {
            clear(target);
            return false;
        }
        if (oldAmplifier != newAmplifier) {
            refreshEffect(target, owner, newAmplifier);
        }
        TheLockAddon.syncLockState(target);
        return true;
    }

    private static void refreshEffect(
            LivingEntity target,
            LivingEntity owner,
            int amplifier) {
        addLockEffect(target, owner, amplifier);
        StandEntity stand = mirroredStand(target);
        if (stand != null) {
            addLockEffect(stand, owner, amplifier);
        }
    }

    private static void addLockEffect(
            LivingEntity target,
            LivingEntity owner,
            int amplifier) {
        target.addEffect(
                new MobEffectInstance(
                        TheLockAddon.LOCK_EFFECT,
                        LockRules.EFFECT_DURATION,
                        amplifier,
                        false,
                        false,
                        false),
                owner);
    }

    private static boolean mirroredEffectNeedsRefresh(
            LivingEntity target, int amplifier) {
        MobEffectInstance targetEffect =
                target.getEffect(TheLockAddon.LOCK_EFFECT);
        StandEntity stand = mirroredStand(target);
        if (targetEffect == null || stand == null) {
            return false;
        }
        MobEffectInstance standEffect =
                stand.getEffect(TheLockAddon.LOCK_EFFECT);
        return standEffect == null
                || standEffect.getAmplifier() != amplifier;
    }

    private static boolean removeLockEffects(
            LivingEntity target) {
        boolean changed =
                target.removeEffect(TheLockAddon.LOCK_EFFECT);
        StandEntity stand = mirroredStand(target);
        if (stand != null) {
            changed |= stand.removeEffect(
                    TheLockAddon.LOCK_EFFECT);
        }
        return changed;
    }

    private static StandEntity mirroredStand(
            LivingEntity target) {
        StandPower power = StandPower.get(target);
        return power != null
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                && stand != target
                ? stand
                : null;
    }

    private static LivingEntity owner(
            ServerLevel level, UUID ownerUuid) {
        Entity owner = ownerUuid != null
                ? level.getEntity(ownerUuid)
                : null;
        return owner instanceof LivingEntity living
                ? living
                : null;
    }

    private static LivingEntity leaseOwner(
            ServerLevel level,
            LivingEntity target,
            LockState state) {
        LivingEntity owner = owner(level, state.ownerUuid());
        return owner != null
                && owner.isAlive()
                && owner.level() == target.level()
                && owner.distanceToSqr(target)
                        <= LockRules.OWNER_RANGE
                                * LockRules.OWNER_RANGE
                ? owner
                : null;
    }
}
