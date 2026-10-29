package rotp.addons.heavensdoor.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.control.AttackOriginScope;
import rotp.core.api.control.ControlledEntityCombatLeases;
import rotp.core.api.control.ControlledMobBehaviorLeases;
import rotp.core.api.control.MobBehaviorMode;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.monster.Enemy;

public final class BookCommandLeaseService {
    private static final String PLAYER_EPOCH_TAG =
            "rotp_hd.BookCommandLeaseEpoch";
    private static final String PLAYER_ACTIVE_ISSUER_TAG =
            "rotp_hd.BookCommandLeaseIssuerActive";

    private static final Map<UUID, CombatRecord> COMBAT =
            new LinkedHashMap<>();
    private static final Map<UUID, BehaviorRecord> BEHAVIOR =
            new LinkedHashMap<>();

    private static ControlledEntityCombatLeases.Owner combatOwner;
    private static ControlledMobBehaviorLeases.Owner behaviorOwner;

    private BookCommandLeaseService() {}

    public static synchronized void initialize() {
        if (combatOwner != null || behaviorOwner != null) {
            throw new IllegalStateException(
                    "Heaven's Door command leases already initialized");
        }
        combatOwner = ControlledEntityCombatLeases.register(
                HeavensDoorAddon.id("book_combat_commands"));
        behaviorOwner = ControlledMobBehaviorLeases.register(
                HeavensDoorAddon.id("book_behavior_commands"));
    }

    public static synchronized boolean initialized() {
        return combatOwner != null && behaviorOwner != null;
    }

    public static synchronized boolean toggleCombat(
            LivingEntity subject,
            ServerPlayer forbidden,
            ServerPlayer issuer) {
        if (!initialized()
                || subject == null
                || forbidden == null
                || issuer == null
                || RestrictedCommandOwners.released(issuer)
                || !subject.isAlive()
                || !forbidden.isAlive()
                || forbidden.isSpectator()
                || issuer.isSpectator()
                || subject.level() != issuer.serverLevel()
                || forbidden.serverLevel() != issuer.serverLevel()
                || subject.getUUID().equals(
                        forbidden.getUUID())) {
            return false;
        }
        pruneInactive();
        BookCommandState state =
                HeavensDoorAddon.bookCommandState(subject);
        if (state.active()
                && !state.belongsTo(subject.getUUID())) {
            releaseSubjectPermanently(subject);
        }
        if (state.combatActive()) {
            boolean sameForbidden = state
                    .combatForbiddenUuid()
                    .equals(forbidden.getUUID());
            releaseCombatPermanently(subject);
            if (sameForbidden) {
                return true;
            }
        }
        else {
            releaseCombatRuntime(subject.getUUID());
        }

        UUID leaseId = UUID.randomUUID();
        ControlledEntityCombatLeases.AcquireResult result =
                combatOwner.acquireNoAttack(
                        subject,
                        forbidden,
                        issuer,
                        leaseId,
                        AttackOriginScope.SELF_AND_SUMMONED_STAND);
        if (!result.succeeded() || result.lease() == null) {
            return false;
        }
        state.markCombat(
                subject.getUUID(),
                issuer.getUUID(),
                forbidden.getUUID(),
                leaseId,
                playerEpoch(issuer),
                playerEpoch(forbidden),
                issuer.level().dimension().location());
        state.setCombatRestrictedEpoch(RestrictedCommandOwners.get(issuer.getServer()).begin(issuer.getUUID()));
        COMBAT.put(
                subject.getUUID(),
                new CombatRecord(
                        subject,
                        forbidden.getUUID(),
                        issuer.getUUID(),
                        result.lease()));
        markIssuerActive(issuer);
        return true;
    }

    public static synchronized boolean toggleBehavior(
            Mob subject,
            ServerPlayer issuer) {
        if (!initialized()
                || !behaviorEligible(subject)
                || issuer == null
                || !issuer.isAlive()
                || issuer.isSpectator()
                || issuer.serverLevel() != subject.level()) {
            return false;
        }
        pruneInactive();
        BookCommandState state =
                HeavensDoorAddon.bookCommandState(subject);
        if (state.active()
                && !state.belongsTo(subject.getUUID())) {
            releaseSubjectPermanently(subject);
        }
        if (state.behaviorActive()) {
            releaseBehaviorPermanently(subject);
            return true;
        }
        releaseBehaviorRuntime(subject.getUUID());

        MobBehaviorMode mode = subject instanceof Enemy
                ? MobBehaviorMode.PEACEFUL
                : MobBehaviorMode.AGGRESSIVE_NEAREST_8;
        UUID leaseId = UUID.randomUUID();
        ControlledMobBehaviorLeases.AcquireResult result =
                behaviorOwner.acquire(subject, mode, leaseId);
        if (!result.succeeded() || result.lease() == null) {
            return false;
        }
        state.markBehavior(
                subject.getUUID(),
                issuer.getUUID(),
                leaseId,
                playerEpoch(issuer),
                PageOwnerSavedData.get(issuer.getServer()).begin(issuer.getUUID()),
                issuer.level().dimension().location(),
                mode);
        BEHAVIOR.put(
                subject.getUUID(),
                new BehaviorRecord(
                        subject,
                        issuer.getUUID(),
                        result.lease()));
        markIssuerActive(issuer);
        return true;
    }

    public static boolean behaviorEligible(LivingEntity target) {
        return target instanceof Mob mob
                && !(mob instanceof TamableAnimal)
                && !(mob instanceof AmbientCreature);
    }

    public static synchronized void onBookRecast(
            ServerPlayer issuer, LivingEntity target) {
        BehaviorRecord retained =
                retainedBehaviorOnRecast(issuer, target);
        if (issuer != null) {
            invalidateIssuerCommands(issuer, retained);
        }
        if (target != null) {
            if (retained == null) {
                releaseSubjectPermanently(target);
            }
            else {
                releaseCombatPermanently(target);
                HeavensDoorAddon.bookCommandState(target)
                        .markBehavior(
                                target.getUUID(),
                                issuer.getUUID(),
                                retained.lease().leaseId(),
                                playerEpoch(issuer),
                                PageOwnerSavedData.get(issuer.getServer()).begin(issuer.getUUID()),
                                target.level().dimension().location(),
                                retained.lease().mode());
                markIssuerActive(issuer);
            }
        }
    }

    private static BehaviorRecord retainedBehaviorOnRecast(
            ServerPlayer issuer, LivingEntity target) {
        if (issuer == null
                || !behaviorEligible(target)
                || !target.isAlive()
                || target.isRemoved()
                || !target.hasData(
                        HeavensDoorAddon.BOOK_COMMAND_STATE)) {
            return null;
        }
        BookCommandState state =
                HeavensDoorAddon.bookCommandState(target);
        BehaviorRecord record = BEHAVIOR.get(target.getUUID());
        return state.behaviorActive()
                        && state.belongsTo(target.getUUID())
                        && record != null
                        && record.subject() == target
                        && issuer.getUUID().equals(record.issuerUuid())
                        && issuer.getUUID().equals(state.behaviorIssuerUuid())
                        && target.level().dimension().location()
                                .equals(state.behaviorDimension())
                        && validIssuer(
                                issuer, target, state.behaviorIssuerEpoch())
                        && PageOwnerSavedData.get(issuer.getServer()).matches(
                                issuer.getUUID(), state.behaviorIssuerDeathEpoch())
                        && record.lease().isActive()
                        && record.lease().leaseId()
                                .equals(state.behaviorLeaseId())
                        && record.lease().mode() == state.behaviorMode()
                ? record
                : null;
    }

    public static synchronized void tickSubject(
            LivingEntity subject) {
        if (subject == null
                || subject.level().isClientSide()
                || !subject.hasData(
                        HeavensDoorAddon.BOOK_COMMAND_STATE)) {
            return;
        }
        pruneInactive();
        BookCommandState state =
                HeavensDoorAddon.bookCommandState(subject);
        if (!state.active()) {
            return;
        }
        if (!state.belongsTo(subject.getUUID())
                || !subject.isAlive()) {
            releaseSubjectPermanently(subject);
            return;
        }
        if (state.combatActive()
                && !RestrictedCommandOwners.get(subject.getServer()).matches(
                        state.combatIssuerUuid(), state.combatRestrictedEpoch())) {
            releaseCombatPermanently(subject);
        }
        if (state.combatActive()
                && !COMBAT.containsKey(subject.getUUID())) {
            restoreCombat(subject, state);
        }
        if (state.behaviorActive()
                && !PageOwnerSavedData.get(subject.getServer()).matches(
                        state.behaviorIssuerUuid(), state.behaviorIssuerDeathEpoch())) {
            releaseBehaviorPermanently(subject);
        }
        if (state.behaviorActive()
                && !BEHAVIOR.containsKey(subject.getUUID())) {
            if (subject instanceof Mob mob) {
                restoreBehavior(mob, state);
            }
            else {
                releaseBehaviorPermanently(subject);
            }
        }
    }

    public static synchronized void tickIssuer(
            ServerPlayer issuer) {
        if (issuer == null) {
            return;
        }
        pruneInactive();
        WrittenCommandService.observeOwner(issuer);
        if (RestrictedCommandOwners.released(issuer)) {
            releaseRestrictedCombatOwnedBy(issuer);
        }
        if (issuer.getPersistentData().getBoolean(
                        PLAYER_ACTIVE_ISSUER_TAG)
                && (!issuer.isAlive()
                        || issuer.isSpectator())) {
            invalidateIssuerCommands(issuer);
        }
    }

    public static synchronized void invalidatePlayerLifecycle(
            ServerPlayer player) {
        if (player == null) {
            return;
        }
        advancePlayerEpoch(player);
        player.getPersistentData().remove(
                PLAYER_ACTIVE_ISSUER_TAG);
        UUID playerUuid = player.getUUID();
        for (CombatRecord record : List.copyOf(COMBAT.values())) {
            if (record.subject().getUUID().equals(playerUuid)
                    || record.forbiddenUuid().equals(playerUuid)
                    || record.issuerUuid().equals(playerUuid)) {
                releaseCombatPermanently(record.subject());
            }
        }
        for (BehaviorRecord record :
                List.copyOf(BEHAVIOR.values())) {
            if (record.subject().getUUID().equals(playerUuid)
                    || record.issuerUuid().equals(playerUuid)) {
                releaseBehaviorPermanently(record.subject());
            }
        }
        if (player.hasData(
                HeavensDoorAddon.BOOK_COMMAND_STATE)) {
            releaseSubjectPermanently(player);
        }
    }

    private static void releaseRestrictedCombatOwnedBy(ServerPlayer issuer) {
        for (CombatRecord record : List.copyOf(COMBAT.values())) {
            if (record.issuerUuid().equals(issuer.getUUID())) {
                releaseCombatPermanently(record.subject());
            }
        }
    }

    public static synchronized void onEntityDeath(
            LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            invalidatePlayerLifecycle(player);
        }
        else if (entity != null) {
            releaseSubjectPermanently(entity);
        }
    }

    public static synchronized void onEntityUnload(
            LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            invalidatePlayerLifecycle(player);
            return;
        }
        if (entity != null) {
            releaseCombatRuntime(entity.getUUID());
            releaseBehaviorRuntime(entity.getUUID());
        }
    }

    public static synchronized void releaseAllRuntime() {
        List.copyOf(COMBAT.values())
                .forEach(BookCommandLeaseService::releaseCombatRuntime);
        List.copyOf(BEHAVIOR.values())
                .forEach(BookCommandLeaseService::releaseBehaviorRuntime);
    }

    private static void restoreCombat(
            LivingEntity subject, BookCommandState state) {
        MinecraftServer server = subject.getServer();
        if (server == null
                || !subject.level().dimension().location()
                        .equals(state.combatDimension())) {
            releaseCombatPermanently(subject);
            return;
        }
        ServerPlayer issuer = server.getPlayerList()
                .getPlayer(state.combatIssuerUuid());
        ServerPlayer forbidden = server.getPlayerList()
                .getPlayer(state.combatForbiddenUuid());
        if (issuer == null || forbidden == null) {
            return;
        }
        if (!validIssuer(
                        issuer,
                        subject,
                        state.combatIssuerEpoch())
                || RestrictedCommandOwners.released(issuer)
                || !RestrictedCommandOwners.get(server).matches(issuer.getUUID(), state.combatRestrictedEpoch())
                || !forbidden.isAlive()
                || forbidden.isSpectator()
                || forbidden.serverLevel() != subject.level()
                || playerEpoch(forbidden)
                        != state.combatForbiddenEpoch()
                || forbidden.getUUID().equals(
                        subject.getUUID())) {
            releaseCombatPermanently(subject);
            return;
        }
        ControlledEntityCombatLeases.AcquireResult result =
                combatOwner.acquireNoAttack(
                        subject,
                        forbidden,
                        issuer,
                        state.combatLeaseId(),
                        AttackOriginScope.SELF_AND_SUMMONED_STAND);
        if (result.succeeded() && result.lease() != null) {
            COMBAT.put(
                    subject.getUUID(),
                    new CombatRecord(
                            subject,
                            forbidden.getUUID(),
                            issuer.getUUID(),
                            result.lease()));
            markIssuerActive(issuer);
        }
    }

    private static void restoreBehavior(
            Mob subject, BookCommandState state) {
        MinecraftServer server = subject.getServer();
        if (server == null
                || !behaviorEligible(subject)
                || !PageOwnerSavedData.get(server).matches(
                        state.behaviorIssuerUuid(), state.behaviorIssuerDeathEpoch())
                || !subject.level().dimension().location()
                        .equals(state.behaviorDimension())) {
            releaseBehaviorPermanently(subject);
            return;
        }
        ServerPlayer issuer = server.getPlayerList()
                .getPlayer(state.behaviorIssuerUuid());
        if (issuer == null) {
            return;
        }
        if (!validIssuer(
                issuer,
                subject,
                state.behaviorIssuerEpoch())) {
            releaseBehaviorPermanently(subject);
            return;
        }
        ControlledMobBehaviorLeases.AcquireResult result =
                behaviorOwner.acquire(
                        subject,
                        state.behaviorMode(),
                        state.behaviorLeaseId());
        if (result.succeeded() && result.lease() != null) {
            BEHAVIOR.put(
                    subject.getUUID(),
                    new BehaviorRecord(
                            subject,
                            issuer.getUUID(),
                            result.lease()));
            markIssuerActive(issuer);
        }
    }

    private static boolean validIssuer(
            ServerPlayer issuer,
            LivingEntity subject,
            long expectedEpoch) {
        return issuer.isAlive()
                && !issuer.isSpectator()
                && issuer.serverLevel() == subject.level()
                && playerEpoch(issuer) == expectedEpoch;
    }

    private static void invalidateIssuerCommands(
            ServerPlayer issuer) {
        invalidateIssuerCommands(issuer, null);
    }

    private static void invalidateIssuerCommands(
            ServerPlayer issuer, BehaviorRecord retainedBehavior) {
        advancePlayerEpoch(issuer);
        issuer.getPersistentData().remove(
                PLAYER_ACTIVE_ISSUER_TAG);
        UUID issuerUuid = issuer.getUUID();
        for (CombatRecord record : List.copyOf(COMBAT.values())) {
            if (record.issuerUuid().equals(issuerUuid)) {
                releaseCombatPermanently(record.subject());
            }
        }
        for (BehaviorRecord record :
                List.copyOf(BEHAVIOR.values())) {
            if (record != retainedBehavior
                    && record.issuerUuid().equals(issuerUuid)) {
                releaseBehaviorPermanently(record.subject());
            }
        }
    }

    private static void releaseSubjectPermanently(
            LivingEntity subject) {
        releaseCombatPermanently(subject);
        releaseBehaviorPermanently(subject);
    }

    private static void releaseCombatPermanently(
            LivingEntity subject) {
        if (subject == null) {
            return;
        }
        if (subject.hasData(
                HeavensDoorAddon.BOOK_COMMAND_STATE)) {
            BookCommandState state =
                    HeavensDoorAddon.bookCommandState(subject);
            state.clearCombat();
        }
        releaseCombatRuntime(subject.getUUID());
    }

    private static void releaseBehaviorPermanently(
            LivingEntity subject) {
        if (subject == null) {
            return;
        }
        if (subject.hasData(
                HeavensDoorAddon.BOOK_COMMAND_STATE)) {
            BookCommandState state =
                    HeavensDoorAddon.bookCommandState(subject);
            state.clearBehavior();
        }
        releaseBehaviorRuntime(subject.getUUID());
    }

    private static CombatRecord releaseCombatRuntime(
            UUID subjectUuid) {
        CombatRecord record = COMBAT.remove(subjectUuid);
        if (record != null && combatOwner != null) {
            combatOwner.release(record.lease());
        }
        return record;
    }

    private static void releaseCombatRuntime(
            CombatRecord record) {
        if (record != null) {
            releaseCombatRuntime(record.subject().getUUID());
        }
    }

    private static BehaviorRecord releaseBehaviorRuntime(
            UUID subjectUuid) {
        BehaviorRecord record = BEHAVIOR.remove(subjectUuid);
        if (record != null && behaviorOwner != null) {
            behaviorOwner.release(record.lease());
        }
        return record;
    }

    private static void releaseBehaviorRuntime(
            BehaviorRecord record) {
        if (record != null) {
            releaseBehaviorRuntime(
                    record.subject().getUUID());
        }
    }

    private static void pruneInactive() {
        COMBAT.values().removeIf(
                record -> !record.lease().isActive());
        BEHAVIOR.values().removeIf(
                record -> !record.lease().isActive());
    }

    private static long playerEpoch(ServerPlayer player) {
        return player.getPersistentData().getLong(
                PLAYER_EPOCH_TAG);
    }

    private static void advancePlayerEpoch(
            ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        long current = persistent.getLong(PLAYER_EPOCH_TAG);
        persistent.putLong(
                PLAYER_EPOCH_TAG,
                current == Long.MAX_VALUE
                        ? 1L
                        : Math.max(1L, current + 1L));
    }

    private static void markIssuerActive(
            ServerPlayer issuer) {
        // Keep this epoch fence even after loaded leases end: commands on
        // unloaded subjects cannot be enumerated until those subjects return.
        issuer.getPersistentData().putBoolean(
                PLAYER_ACTIVE_ISSUER_TAG, true);
    }

    private record CombatRecord(
            LivingEntity subject,
            UUID forbiddenUuid,
            UUID issuerUuid,
            ControlledEntityCombatLeases.Lease lease) {}

    private record BehaviorRecord(
            Mob subject,
            UUID issuerUuid,
            ControlledMobBehaviorLeases.Lease lease) {}
}
