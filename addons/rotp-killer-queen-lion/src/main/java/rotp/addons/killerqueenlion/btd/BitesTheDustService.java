package rotp.addons.killerqueenlion.btd;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.core.api.rollback.AtomicRollbackJournal;
import rotp.core.api.rollback.AtomicRollbackJournal.Outcome;
import rotp.core.api.rollback.AtomicRollbackJournal.Status;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;
import com.mojang.logging.LogUtils;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import rotp.addons.killerqueenlion.state.KillerQueenState.BitesTheDustPlayerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;

/**
 * Server-owned donor Bites the Dust state machine and bounded rewind
 * transaction. The donor only restores marked players and day time, so
 * block, inventory, and general entity history are deliberately out of
 * scope.
 */
public final class BitesTheDustService {
    public static final int MARK_LIFETIME_TICKS = 24_000;
    public static final int TRIGGER_DELAY_TICKS = 40;
    public static final int USER_VOICE_TICK = 2;
    public static final int STAND_VOICE_TICK = 39;
    public static final double MANUAL_RESET_RADIUS = 100.0D;
    public static final double TRIGGER_RESET_RADIUS = 50.0D;
    public static final int MAX_PENDING_TRIGGERS = 128;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ReceiptAuthority RECEIPT_AUTHORITY =
            newReceiptAuthority();
    private static final Map<MinecraftServer,
            LinkedHashMap<UUID, PendingTrigger>> PENDING =
                    new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, CommitFault>>
            TEST_COMMIT_FAULTS = new WeakHashMap<>();
    private static final Map<MinecraftServer,
            Map<UUID, BitesTheDustActivationReceipt>>
                    COMMITTED_RECEIPTS = new WeakHashMap<>();

    private BitesTheDustService() {}

    public enum ResetResult {
        SUCCESS(""),
        NO_MARKER("killer_queen_btd_no_marker"),
        WRONG_OWNER("killer_queen_btd_wrong_owner"),
        HISTORY_NOT_READY("killer_queen_btd_history_not_ready"),
        UNLOADED_TARGET("killer_queen_btd_unloaded_target"),
        COMMIT_FAILED("killer_queen_btd_commit_failed");

        private final String conditionKey;

        ResetResult(String conditionKey) {
            this.conditionKey = conditionKey;
        }

        public String conditionKey() {
            return conditionKey;
        }
    }

    enum CommitFault {
        NONE,
        PREPARE_RECEIPT,
        TELEPORT_APPLY,
        DAY_TIME_APPLY,
        OWNER_HEALTH_APPLY,
        ATTACKER_KILL
    }

    public static Optional<BitesTheDustActivationReceipt>
            consumeLatestBitesTheDustActivationReceipt(
                    ServerPlayer owner,
                    UUID expectedVictimUuid,
                    UUID expectedAttackerUuid) {
        if (owner == null
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return Optional.empty();
        }
        MinecraftServer server = owner.getServer();
        ReceiptAuthority authority = RECEIPT_AUTHORITY;
        synchronized (COMMITTED_RECEIPTS) {
            Map<UUID, BitesTheDustActivationReceipt> receipts =
                    COMMITTED_RECEIPTS.get(server);
            BitesTheDustActivationReceipt authoritative =
                    receipts != null
                            ? receipts.get(owner.getUUID())
                            : null;
            if (authoritative == null
                    || !receiptIntegrityValid(
                            authoritative, authority)
                    || !authoritative.victimUuid().equals(
                            expectedVictimUuid)
                    || !authoritative.attackerUuid().equals(
                            expectedAttackerUuid)) {
                return Optional.empty();
            }
            BitesTheDustActivationReceipt consumed =
                    KillerQueenLionAddon.state(owner)
                            .consumeBitesTheDustActivationReceipt(
                                    authority.stateCapability(),
                                    owner.getUUID(),
                                    expectedVictimUuid,
                                    expectedAttackerUuid);
            if (consumed == null
                    || !consumed.receiptId().equals(
                            authoritative.receiptId())) {
                receipts.remove(owner.getUUID());
                if (receipts.isEmpty()) {
                    COMMITTED_RECEIPTS.remove(server);
                }
                return Optional.empty();
            }
            receipts.remove(owner.getUUID());
            if (receipts.isEmpty()) {
                COMMITTED_RECEIPTS.remove(server);
            }
            return Optional.of(consumed);
        }
    }

    /** Integrity inspection only; acceptance uses the one-shot consume API. */
    public static boolean authenticatedReceiptTag(
            net.minecraft.nbt.CompoundTag tag) {
        return BitesTheDustActivationReceipt
                .parseAuthenticated(tag)
                .isPresent();
    }

    /** No caller can obtain the process-local capability being checked. */
    public static boolean authorizesReceiptStateMutation(
            Object candidate) {
        return candidate
                == RECEIPT_AUTHORITY.stateCapability();
    }

    public static boolean markTime(ServerPlayer owner) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (state.hasBitesTheDustMarker()) {
            return false;
        }
        List<BitesTheDustPlayerSnapshot> snapshots =
                captureMarkerSnapshots(owner);
        boolean changed = state.setBitesTheDustMarker(
                owner.serverLevel().getDayTime(),
                owner.serverLevel().getGameTime(),
                owner.serverLevel().dimension().location(),
                UUID.randomUUID(),
                snapshots);
        if (changed) {
            BitesTheDustVictimIndex.get(owner.getServer())
                    .releaseOwner(owner.getUUID());
            cancelForOwner(owner.getServer(), owner.getUUID());
            discardCommittedReceipt(
                    owner.getServer(), owner.getUUID());
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    /**
     * Compatibility arming path retained for callers that already have a
     * victim UUID. The persistent victim lease is installed before the
     * player attachment so this path cannot bypass offline arbitration.
     */
    public static boolean armBitesTheDust(
            ServerPlayer owner,
            UUID victimUuid,
            long legacyMarkerGameTime,
            ResourceLocation victimDimension) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || victimUuid == null
                || legacyMarkerGameTime < 0L
                || victimDimension == null) {
            return false;
        }
        List<BitesTheDustPlayerSnapshot> snapshots =
                captureMarkerSnapshots(owner);
        if (snapshots.isEmpty()) {
            return false;
        }
        ResourceLocation markerDimension =
                owner.serverLevel().dimension().location();
        long markerGameTime = owner.serverLevel().getGameTime();
        long markerDayTime = owner.serverLevel().getDayTime();
        UUID markerEpoch = UUID.randomUUID();
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(owner.getServer());
        if (index.reconcile(
                        owner.getUUID(),
                        victimUuid,
                        victimDimension,
                        markerEpoch,
                        markerDimension,
                        markerGameTime)
                != BitesTheDustVictimIndex
                        .ReconcileResult.ACCEPTED) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        boolean changed = state.setBitesTheDustMarker(
                markerDayTime,
                markerGameTime,
                markerDimension,
                markerEpoch,
                snapshots);
        changed = state.selectBitesTheDustVictim(
                victimUuid, victimDimension) || changed;
        cancelForOwner(owner.getServer(), owner.getUUID());
        discardCommittedReceipt(
                owner.getServer(), owner.getUUID());
        forceUnsummon(owner);
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    public static ResetResult resetDay(ServerPlayer owner) {
        return rewind(
                owner,
                owner.serverLevel(),
                owner.position(),
                MANUAL_RESET_RADIUS,
                null,
                null);
    }

    public static boolean removeTimeMarker(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed =
                KillerQueenLionAddon.state(owner).clearBitesTheDust();
        BitesTheDustVictimIndex.get(owner.getServer())
                .releaseOwner(owner.getUUID());
        cancelForOwner(owner.getServer(), owner.getUUID());
        discardCommittedReceipt(
                owner.getServer(), owner.getUUID());
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    public static boolean selectVictim(
            ServerPlayer owner, LivingEntity victim) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)
                || !KillerQueenLionAddon.state(owner)
                        .hasBitesTheDustMarker()
                || !validVictim(owner, victim)) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        UUID markerEpoch = state.bitesTheDustMarkerEpoch();
        if (markerEpoch == null
                || BitesTheDustVictimIndex.get(owner.getServer())
                        .reconcile(
                                owner.getUUID(),
                                victim.getUUID(),
                                victim.level().dimension().location(),
                                markerEpoch,
                                state.bitesTheDustMarkerDimension(),
                                state.bitesTheDustMarkerGameTime())
                        != BitesTheDustVictimIndex
                                .ReconcileResult.ACCEPTED) {
            return false;
        }
        boolean changed = state
                .selectBitesTheDustVictim(
                        victim.getUUID(),
                        victim.level().dimension().location());
        if (changed) {
            discardCommittedReceipt(
                    owner.getServer(), owner.getUUID());
            forceUnsummon(owner);
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    public static boolean removeVictim(ServerPlayer owner) {
        if (!owner.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
            return false;
        }
        boolean changed = KillerQueenLionAddon.state(owner)
                .clearBitesTheDustVictim();
        BitesTheDustVictimIndex.get(owner.getServer())
                .releaseOwner(owner.getUUID());
        cancelForOwner(owner.getServer(), owner.getUUID());
        discardCommittedReceipt(
                owner.getServer(), owner.getUUID());
        if (changed) {
            KillerQueenLionAddon.syncState(owner);
        }
        return changed;
    }

    /**
     * 1.16's mouse target, which RemoveTimeMarker.replaceAction read to
     * offer put_dust and SelectHayato checked and performed on (100 :43-48,
     * :50-75): the vanilla pick (the user's synced aim) and, when that met
     * nothing and the Stand is out, the Stand's precision ray from the camera
     * (EntityStandType.clientHitResult :122-151); with the Stand away nothing
     * past the vanilla pick. A pressed block stays the target (a StandAction
     * had no overrideVanillaMouseTarget). On the server an aimed entity the
     * server cannot find is no target (PowerBaseImpl.checkTarget).
     */
    public static ActionTarget aimedTarget(Player owner) {
        if (owner == null) {
            return ActionTarget.EMPTY;
        }
        Level level = owner.level();
        ActionTargetAim aim = userAim(owner);
        if (aim != null && !level.isClientSide()
                && aim.isClientEntityMissing()) {
            return ActionTarget.EMPTY;
        }
        ActionTarget target = aim != null && aim.getTarget() != null
                ? aim.getTarget().copy().resolveEntityId(level)
                : ActionTarget.EMPTY;
        StandEntity stand = StandUtil.getSummonedStand(owner);
        if (target.getType() == ActionTarget.TargetType.EMPTY
                && stand != null) {
            LivingEntity camera =
                    stand.isManuallyControlled() ? stand : owner;
            double reach = camera == stand
                    ? stand.getAttributeValue(
                            Attributes.BLOCK_INTERACTION_RANGE)
                    : StandPrecisionRay.reach(owner, stand);
            boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(
                    level, putDust(owner));
            target = StandEntityPunchAbility.aimAssist(owner)
                    ? StandPrecisionRay.trace(camera, stand, reach, 0.0D,
                            true, wallsHideEntities)
                    : StandPrecisionRay.crosshair(camera, stand, reach,
                            true, wallsHideEntities);
        }
        return target;
    }

    // The aims only go from the user's client to the server, so that client
    // reads the aim it sends
    @Nullable
    private static ActionTargetAim userAim(Player owner) {
        ActionTargetAim aim = owner.level().isClientSide()
                        && owner == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(owner);
        return aim;
    }

    @Nullable
    private static Ability putDust(Player owner) {
        StandPower power = StandPower.get(owner);
        return power != null && power.hasPower()
                ? power.getMoveset().getAbility(
                        KillerQueenLionAddon.PUT_DUST_NAME)
                : null;
    }

    /**
     * The living entity a press of put_dust is at ({@link #aimedTarget}),
     * within 1.16's range of SelectHayato's performer, or null.
     */
    @Nullable
    public static LivingEntity aimedLiving(Player owner) {
        return aimedLiving(owner, aimedTarget(owner));
    }

    @Nullable
    private static LivingEntity aimedLiving(
            Player owner, @Nullable ActionTarget target) {
        if (owner == null || target == null) {
            return null;
        }
        Entity entity =
                target.resolveEntityId(owner.level()).getMainEntity();
        // 1.16 Action.checkRangeAndTarget (PowerBaseImpl.checkTarget): 8
        // blocks from the summoned Stand, else the user, to the target's
        // box, 4 out of the performer's sight; farther, the target emptied
        return entity instanceof LivingEntity living
                && living != owner
                && ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(owner),
                        living,
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)
                        ? living
                        : null;
    }

    @Nullable
    public static LivingEntity targetedVictim(Player owner) {
        return targetedVictim(owner, aimedTarget(owner));
    }

    @Nullable
    public static LivingEntity targetedVictim(
            Player owner, @Nullable ActionTarget target) {
        LivingEntity living = aimedLiving(owner, target);
        return living != null && validVictim(owner, living)
                ? living
                : null;
    }

    // 1.16 SelectHayato :53-55 'bites_za_has': another user already chose
    // the entity (BitesZaDustHandler.victimToUser); a server's record
    public static boolean isOtherUsersVictim(
            Player owner, LivingEntity entity) {
        MinecraftServer server = entity.getServer();
        if (entity.level().isClientSide() || server == null) {
            return false;
        }
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(server);
        index.expireBindings(server);
        BitesTheDustVictimIndex.Binding binding =
                index.observeVictimDimension(
                        entity.getUUID(),
                        entity.level().dimension().location());
        return binding != null
                && !binding.ownerUuid().equals(owner.getUUID());
    }

    // 1.16 SelectHayato :56-60 'is_stand_user': IStandPower.hadAnyStand
    public static boolean isStandUser(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && (power.hasPower()
                        || power.userStandAwakeningState
                                .hadStandBefore);
    }

    public static void handleIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()
                || victim.getServer() == null
                || event.getAmount() <= 0.0F) {
            return;
        }
        ServerPlayer owner = ownerForVictim(
                victim.getServer(), victim);
        if (owner == null
                || !ensureAutomaticTriggerReady(owner, victim)) {
            return;
        }

        event.setCanceled(true);
        LivingEntity attacker =
                resolveAttacker(event.getSource().getEntity());
        if (attacker == null
                || attacker == victim
                || attacker.getUUID().equals(owner.getUUID())) {
            return;
        }
        if (attacker instanceof ServerPlayer player) {
            scheduleTrigger(owner, victim, player);
            return;
        }
        spawnDetonationParticles(attacker);
        attacker.hurt(
                attacker.damageSources().genericKill(),
                Float.MAX_VALUE);
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        BitesTheDustVictimIndex.get(server)
                .expireBindings(server);
        expireMarkers(server);
        suppressArmedOwners(server);

        LinkedHashMap<UUID, PendingTrigger> pending =
                pending(server);
        List<PendingTrigger> ordered =
                pending.values().stream()
                        .sorted(Comparator.comparing(
                                trigger ->
                                        trigger.attackerUuid().toString()))
                        .toList();
        for (PendingTrigger trigger : ordered) {
            int age = now - trigger.startTick();
            ServerPlayer attacker =
                    server.getPlayerList().getPlayer(
                            trigger.attackerUuid());
            ServerPlayer owner =
                    server.getPlayerList().getPlayer(
                            trigger.ownerUuid());
            if (owner == null
                    || attacker == null
                    || !attacker.isAlive()
                    || !triggerStillValid(
                            owner,
                            trigger.victimUuid())) {
                pending.remove(trigger.attackerUuid());
                clearTriggerPresentation(
                        server, trigger.attackerUuid());
                continue;
            }
            syncTriggerPresentation(
                    attacker,
                    trigger,
                    Math.min(age, TRIGGER_DELAY_TICKS));
            if (age == USER_VOICE_TICK) {
                attacker.serverLevel().playSound(
                        null,
                        attacker.blockPosition(),
                        KillerQueenLionAddon.USER_BITES_THE_DUST.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
            }
            if (age == STAND_VOICE_TICK) {
                attacker.serverLevel().playSound(
                        null,
                        attacker.blockPosition(),
                        KillerQueenLionAddon.BITES_THE_DUST.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
            }
            if (age >= TRIGGER_DELAY_TICKS) {
                spawnDetonationParticles(attacker);
                ResetResult result = rewind(
                        owner,
                        attacker.serverLevel(),
                        attacker.position(),
                        TRIGGER_RESET_RADIUS,
                        attacker,
                        trigger);
                if (result != ResetResult.SUCCESS) {
                    LOGGER.warn(
                            "Bites the Dust trigger failed for owner={}, "
                                    + "victim={}, attacker={}, result={}",
                            trigger.ownerUuid(),
                            trigger.victimUuid(),
                            trigger.attackerUuid(),
                            result);
                }
                pending.remove(trigger.attackerUuid());
                clearTriggerPresentation(
                        server, trigger.attackerUuid());
            }
        }
        if (pending.isEmpty()) {
            synchronized (PENDING) {
                PENDING.remove(server);
            }
        }
    }

    public static void trackPlayerAcrossMarkers(
            ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceLocation liveDimension =
                player.level().dimension().location();
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(server);
        index.expireBindings(server);
        BitesTheDustVictimIndex.Binding observed =
                index.observeVictimDimension(
                        player.getUUID(), liveDimension);
        for (ServerPlayer owner :
                orderedPlayers(server)) {
            if (!owner.hasData(
                    KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
                continue;
            }
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            if (state.hasBitesTheDustMarker()
                    && !state.bitesTheDustPlayerSnapshots()
                            .isEmpty()) {
                state.trackBitesTheDustPlayer(player);
            }
            if (observed != null
                    && observed.ownerUuid().equals(owner.getUUID())
                    && observed.markerEpoch().equals(
                            state.bitesTheDustMarkerEpoch())
                    && state.updateBitesTheDustVictimDimension(
                            player.getUUID(), liveDimension)) {
                KillerQueenLionAddon.syncState(owner);
            }
        }
    }

    public static void reconnectOwner(ServerPlayer owner) {
        MinecraftServer server = owner.getServer();
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(server);
        index.expireBindings(server);
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!state.hasBitesTheDustMarker()) {
            index.releaseOwner(owner.getUUID());
            return;
        }
        ServerLevel markerLevel = level(
                server,
                state.bitesTheDustMarkerDimension());
        if (markerLevel == null
                || markerLevel.getGameTime()
                        - state.bitesTheDustMarkerGameTime()
                        >= MARK_LIFETIME_TICKS) {
            removeTimeMarker(owner);
            return;
        }
        for (ServerPlayer player :
                orderedPlayers(server)) {
            if (!state.bitesTheDustPlayerSnapshots().isEmpty()
                    && state.bitesTheDustPlayerSnapshot(
                            player.getUUID()) == null) {
                state.trackBitesTheDustPlayer(player);
            }
        }
        if (state.hasBitesTheDustVictim()) {
            BitesTheDustVictimIndex.Binding binding =
                    index.bindingForOwner(owner.getUUID());
            LivingEntity liveVictim = findLivingEntity(
                    server, state.bitesTheDustVictimUuid());
            if (binding == null) {
                ResourceLocation victimDimension = liveVictim != null
                        ? liveVictim.level().dimension().location()
                        : state.bitesTheDustVictimDimension();
                BitesTheDustVictimIndex.ReconcileResult result =
                        index.reconcile(
                                owner.getUUID(),
                                state.bitesTheDustVictimUuid(),
                                victimDimension,
                                state.bitesTheDustMarkerEpoch(),
                                state.bitesTheDustMarkerDimension(),
                                state.bitesTheDustMarkerGameTime());
                if (result == BitesTheDustVictimIndex
                        .ReconcileResult.ACCEPTED) {
                    binding = index.bindingForOwner(owner.getUUID());
                }
            }
            if (binding != null
                    && bindingMatchesState(
                            owner.getUUID(), binding, state)) {
                state.updateBitesTheDustVictimDimension(
                        binding.victimUuid(),
                        binding.victimDimension());
                if (state.bitesTheDustPlayerSnapshots().isEmpty()) {
                    if (!migrateZeroSnapshotMarker(
                            owner,
                            binding.victimUuid(),
                            binding.victimDimension())) {
                        state.clearBitesTheDustVictim();
                        cancelForOwner(server, owner.getUUID());
                    }
                }
                forceUnsummon(owner);
            } else {
                state.clearBitesTheDustVictim();
                cancelForOwner(
                        server, owner.getUUID());
            }
        }
        KillerQueenLionAddon.syncState(owner);
    }

    public static void playerLoggedOut(ServerPlayer player) {
        cancelForPlayer(player.getServer(), player.getUUID());
        clearTriggerPresentation(player);
    }

    public static void playerChangedDimension(ServerPlayer player) {
        cancelForPlayer(player.getServer(), player.getUUID());
        clearTriggerPresentation(player);
    }

    public static void levelUnloaded(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ResourceLocation dimension = level.dimension().location();
        List<UUID> attackers = new ArrayList<>();
        synchronized (PENDING) {
            LinkedHashMap<UUID, PendingTrigger> pending =
                    PENDING.get(server);
            if (pending == null) {
                return;
            }
            pending.entrySet().removeIf(entry -> {
                PendingTrigger trigger = entry.getValue();
                ServerPlayer owner =
                        server.getPlayerList().getPlayer(
                                trigger.ownerUuid());
                ServerPlayer attacker =
                        server.getPlayerList().getPlayer(
                                trigger.attackerUuid());
                if ((owner != null
                                && owner.serverLevel() == level)
                        || (attacker != null
                                && attacker.serverLevel() == level)) {
                    attackers.add(entry.getKey());
                    return true;
                }
                if (owner == null
                        || !owner.hasData(
                                KillerQueenLionAddon
                                        .KILLER_QUEEN_STATE)) {
                    return false;
                }
                KillerQueenState state =
                        KillerQueenLionAddon.state(owner);
                boolean canceled = dimension.equals(
                                state.bitesTheDustMarkerDimension())
                        || dimension.equals(
                                state.bitesTheDustVictimDimension());
                if (canceled) {
                    attackers.add(entry.getKey());
                }
                return canceled;
            });
            if (pending.isEmpty()) {
                PENDING.remove(server);
            }
        }
        for (UUID attackerUuid : attackers) {
            clearTriggerPresentation(server, attackerUuid);
        }
    }

    public static void entityDied(LivingEntity entity) {
        MinecraftServer server = entity.getServer();
        cancelForPlayer(server, entity.getUUID());
        if (entity instanceof ServerPlayer player
                && player.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)
                && KillerQueenLionAddon.state(player)
                        .hasBitesTheDustMarker()) {
            removeTimeMarker(player);
        }
        for (ServerPlayer owner : orderedPlayers(server)) {
            if (owner == entity
                    || !owner.hasData(
                            KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
                continue;
            }
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            if (entity.getUUID().equals(
                    state.bitesTheDustVictimUuid())) {
                removeVictim(owner);
            }
        }
        BitesTheDustVictimIndex.get(server)
                .revokeVictim(entity.getUUID());
    }

    public static void clear(MinecraftServer server) {
        List<UUID> attackers;
        synchronized (PENDING) {
            LinkedHashMap<UUID, PendingTrigger> removed =
                    PENDING.remove(server);
            attackers = removed == null
                    ? List.of()
                    : List.copyOf(removed.keySet());
        }
        for (UUID attackerUuid : attackers) {
            clearTriggerPresentation(server, attackerUuid);
        }
        synchronized (TEST_COMMIT_FAULTS) {
            TEST_COMMIT_FAULTS.remove(server);
        }
        synchronized (COMMITTED_RECEIPTS) {
            COMMITTED_RECEIPTS.remove(server);
        }
    }

    public static void cancelForOwner(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return;
        }
        List<UUID> attackers = new ArrayList<>();
        synchronized (PENDING) {
            LinkedHashMap<UUID, PendingTrigger> pending =
                    PENDING.get(server);
            if (pending == null) {
                return;
            }
            pending.entrySet().removeIf(entry -> {
                if (!ownerUuid.equals(
                        entry.getValue().ownerUuid())) {
                    return false;
                }
                attackers.add(entry.getKey());
                return true;
            });
            if (pending.isEmpty()) {
                PENDING.remove(server);
            }
        }
        for (UUID attackerUuid : attackers) {
            clearTriggerPresentation(server, attackerUuid);
        }
    }

    private static void cancelForPlayer(
            MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) {
            return;
        }
        List<UUID> attackers = new ArrayList<>();
        synchronized (PENDING) {
            LinkedHashMap<UUID, PendingTrigger> pending =
                    PENDING.get(server);
            if (pending == null) {
                return;
            }
            pending.entrySet().removeIf(entry -> {
                PendingTrigger trigger = entry.getValue();
                boolean canceled = playerUuid.equals(
                                trigger.ownerUuid())
                        || playerUuid.equals(
                                trigger.victimUuid())
                        || playerUuid.equals(
                                trigger.attackerUuid());
                if (canceled) {
                    attackers.add(entry.getKey());
                }
                return canceled;
            });
            if (pending.isEmpty()) {
                PENDING.remove(server);
            }
        }
        for (UUID attackerUuid : attackers) {
            clearTriggerPresentation(server, attackerUuid);
        }
    }

    private static void scheduleTrigger(
            ServerPlayer owner,
            LivingEntity victim,
            ServerPlayer attacker) {
        LinkedHashMap<UUID, PendingTrigger> pending =
                pending(owner.getServer());
        if (pending.containsKey(attacker.getUUID())
                || pending.size() >= MAX_PENDING_TRIGGERS) {
            return;
        }
        pending.put(
                attacker.getUUID(),
                new PendingTrigger(
                        owner.getUUID(),
                        victim.getUUID(),
                        attacker.getUUID(),
                        owner.getServer().getTickCount()));
        syncTriggerPresentation(
                attacker, pending.get(attacker.getUUID()), 0);
    }

    private static void syncTriggerPresentation(
            ServerPlayer attacker,
            PendingTrigger trigger,
            int triggerTicks) {
        if (KillerQueenLionAddon.state(attacker)
                .setBitesTheDustTrigger(
                        trigger.ownerUuid(),
                        trigger.victimUuid(),
                        triggerTicks)) {
            KillerQueenLionAddon.syncState(attacker);
        }
    }

    private static void clearTriggerPresentation(
            MinecraftServer server, UUID attackerUuid) {
        if (server == null || attackerUuid == null) {
            return;
        }
        ServerPlayer attacker = server.getPlayerList().getPlayer(
                attackerUuid);
        if (attacker != null) {
            clearTriggerPresentation(attacker);
        }
    }

    private static void clearTriggerPresentation(
            ServerPlayer attacker) {
        if (attacker.hasData(KillerQueenLionAddon.KILLER_QUEEN_STATE)
                && KillerQueenLionAddon.state(attacker)
                        .clearBitesTheDustTrigger()) {
            KillerQueenLionAddon.syncState(attacker);
        }
    }

    private static boolean triggerStillValid(
            ServerPlayer owner, UUID victimUuid) {
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        return KillerQueenLionAddon.isKillerQueenUser(owner)
                && state.hasBitesTheDustVictim()
                && victimUuid.equals(
                        state.bitesTheDustVictimUuid());
    }

    private static void expireMarkers(MinecraftServer server) {
        for (ServerPlayer owner : orderedPlayers(server)) {
            if (!owner.hasData(
                    KillerQueenLionAddon.KILLER_QUEEN_STATE)) {
                continue;
            }
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            ResourceLocation markerDimension =
                    state.bitesTheDustMarkerDimension();
            if (!state.hasBitesTheDustMarker()
                    || markerDimension == null) {
                continue;
            }
            ServerLevel markerLevel = level(
                    server, markerDimension);
            if (markerLevel == null
                    || markerLevel.getGameTime()
                            - state.bitesTheDustMarkerGameTime()
                            >= MARK_LIFETIME_TICKS) {
                removeTimeMarker(owner);
            }
        }
    }

    private static void spawnDetonationParticles(
            LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                attacker.getX(),
                attacker.getY() + 1.0D,
                attacker.getZ(),
                20,
                0.1D,
                0.2D,
                0.1D,
                0.025D);
        level.sendParticles(
                ParticleTypes.FLAME,
                attacker.getX(),
                attacker.getY() + 1.0D,
                attacker.getZ(),
                10,
                0.1D,
                0.2D,
                0.1D,
                0.1D);
    }

    private static void suppressArmedOwners(
            MinecraftServer server) {
        for (ServerPlayer owner : orderedPlayers(server)) {
            if (owner.hasData(
                            KillerQueenLionAddon.KILLER_QUEEN_STATE)
                    && KillerQueenLionAddon.state(owner)
                            .hasBitesTheDustVictim()) {
                forceUnsummon(owner);
            }
        }
    }

    private static void forceUnsummon(ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        if (power != null
                && power.getPowerType()
                        == KillerQueenLionAddon.KILLER_QUEEN.get()
                && power.getSummonedStandEntity() != null) {
            power.getPowerType().forceUnsummon(owner, power);
        }
    }

    private static ResetResult rewind(
            ServerPlayer owner,
            ServerLevel sourceLevel,
            Vec3 origin,
            double radius,
            @Nullable ServerPlayer condemned,
            @Nullable PendingTrigger automaticTrigger) {
        if (!KillerQueenLionAddon.isKillerQueenUser(owner)) {
            return ResetResult.WRONG_OWNER;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!state.hasBitesTheDustMarker()) {
            return ResetResult.NO_MARKER;
        }

        List<PlayerMutation> mutations = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (ServerPlayer player : sourceLevel.players().stream()
                .filter(ServerPlayer::isAlive)
                .filter(candidate -> condemned == null
                        || !candidate.getUUID().equals(
                                condemned.getUUID()))
                .filter(candidate ->
                        candidate.position().distanceToSqr(origin)
                                <= radiusSquared)
                .sorted(Comparator.comparing(
                        candidate ->
                                candidate.getUUID().toString()))
                .toList()) {
            BitesTheDustPlayerSnapshot destination =
                    state.bitesTheDustPlayerSnapshot(
                            player.getUUID());
            if (destination == null) {
                if (condemned != null) {
                    continue;
                }
                destination = respawnSnapshot(player);
            }
            if (destination == null) {
                return ResetResult.HISTORY_NOT_READY;
            }
            ServerLevel target = level(
                    owner.getServer(), destination.dimension());
            if (target == null
                    || !loadedDestination(target, destination)) {
                return ResetResult.UNLOADED_TARGET;
            }
            mutations.add(new PlayerMutation(
                    player, target, destination));
        }
        if (mutations.isEmpty() && condemned == null) {
            return ResetResult.HISTORY_NOT_READY;
        }

        return commit(
                owner,
                sourceLevel,
                state,
                state.bitesTheDustMarkerTime(),
                mutations,
                condemned,
                automaticTrigger)
                        ? ResetResult.SUCCESS
                        : ResetResult.COMMIT_FAILED;
    }

    private static boolean commit(
            ServerPlayer owner,
            ServerLevel sourceLevel,
            KillerQueenState state,
            long targetDayTime,
            List<PlayerMutation> mutations,
            @Nullable ServerPlayer condemned,
            @Nullable PendingTrigger automaticTrigger) {
        long inverseDayTime = sourceLevel.getDayTime();
        float inverseOwnerHealth = owner.getHealth();
        float ownerMaxHealth = owner.getMaxHealth();
        float inverseCondemnedHealth = condemned != null
                ? condemned.getHealth()
                : 0.0F;
        List<PlayerInverse> inverses = new ArrayList<>();
        List<AtomicRollbackJournal.Entry> journal =
                new ArrayList<>();
        for (PlayerMutation mutation : mutations) {
            PlayerInverse inverse = new PlayerInverse(
                    mutation.player(),
                    mutation.player().serverLevel(),
                    BitesTheDustPlayerSnapshot.capture(
                            mutation.player()),
                    mutation.player().getHealth());
            inverses.add(inverse);
            journal.add(AtomicRollbackJournal.entry(
                    "player/" + mutation.player().getUUID(),
                    () -> {
                        triggerTestFault(
                                owner.getServer(),
                                owner.getUUID(),
                                CommitFault.TELEPORT_APPLY);
                        teleportAndVerify(
                                mutation.player(),
                                mutation.targetLevel(),
                                mutation.destination(),
                                "forward");
                    },
                    () -> {
                        teleportAndVerify(
                                inverse.player(),
                                inverse.level(),
                                inverse.snapshot(),
                                "inverse");
                        inverse.player().setHealth(
                                inverse.health());
                    }));
        }
        journal.add(AtomicRollbackJournal.entry(
                "day_time",
                () -> {
                    triggerTestFault(
                            owner.getServer(),
                            owner.getUUID(),
                            CommitFault.DAY_TIME_APPLY);
                    sourceLevel.setDayTime(targetDayTime);
                    if (sourceLevel.getDayTime() != targetDayTime) {
                        throw new IllegalStateException(
                                "Bites the Dust day-time mutation "
                                        + "did not converge");
                    }
                },
                () -> sourceLevel.setDayTime(inverseDayTime)));

        journal.add(AtomicRollbackJournal.entry(
                "owner_health",
                () -> {
                    triggerTestFault(
                            owner.getServer(),
                            owner.getUUID(),
                            CommitFault.OWNER_HEALTH_APPLY);
                    owner.setHealth(owner.getMaxHealth());
                    if (Float.compare(
                            owner.getHealth(),
                            owner.getMaxHealth()) != 0) {
                        throw new IllegalStateException(
                                "Bites the Dust owner heal did not "
                                        + "converge");
                    }
                },
                () -> owner.setHealth(inverseOwnerHealth)));

        BitesTheDustActivationReceipt preparedReceipt = null;
        if (condemned != null && automaticTrigger != null) {
            try {
                triggerTestFault(
                        owner.getServer(),
                        owner.getUUID(),
                        CommitFault.PREPARE_RECEIPT);
                preparedReceipt = prepareActivationReceipt(
                        owner,
                        sourceLevel,
                        state,
                        automaticTrigger,
                        condemned,
                        targetDayTime,
                        inverseDayTime,
                        inverseOwnerHealth,
                        ownerMaxHealth,
                        inverseCondemnedHealth,
                        mutations.size(),
                        journal.size());
            }
            catch (RuntimeException exception) {
                if (injectedCommitFault(exception)) {
                    LOGGER.warn(
                            "Bites the Dust receipt preflight fault "
                                    + "injected for GameTest",
                            exception);
                } else {
                    LOGGER.error(
                            "Bites the Dust receipt preflight failed",
                            exception);
                }
                return false;
            }
            if (preparedReceipt == null
                    || !preparedReceipt.verified()) {
                LOGGER.error(
                        "Bites the Dust refused an invalid receipt "
                                + "before mutation");
                return false;
            }
        }

        Outcome outcome = AtomicRollbackJournal.apply(journal);
        if (outcome.applied()) {
            if (condemned == null || automaticTrigger == null) {
                return true;
            }
            if (preparedReceipt == null
                    || outcome.attemptedEntries()
                            != preparedReceipt
                                    .journalAttemptedEntries()
                    || !state.installBitesTheDustActivationReceipt(
                            RECEIPT_AUTHORITY.stateCapability(),
                            owner.getUUID(),
                            preparedReceipt)) {
                state.clearLatestBitesTheDustActivationReceipt(
                        RECEIPT_AUTHORITY.stateCapability());
                compensateAppliedCommit(
                        owner,
                        sourceLevel,
                        mutations,
                        inverses,
                        inverseDayTime,
                        targetDayTime,
                        inverseOwnerHealth,
                        ownerMaxHealth);
                return false;
            }
            try {
                triggerTestFault(
                        owner.getServer(),
                        owner.getUUID(),
                        CommitFault.ATTACKER_KILL);
                condemned.hurt(
                        condemned.damageSources().genericKill(),
                        Float.MAX_VALUE);
            }
            catch (RuntimeException exception) {
                if (!condemned.isAlive()) {
                    LOGGER.error(
                            "Bites the Dust attacker died despite an "
                                    + "exception from the irreversible "
                                    + "kill step; treating the commit as "
                                    + "successful",
                            exception);
                    registerCommittedReceipt(
                            owner.getServer(),
                            owner.getUUID(),
                            preparedReceipt);
                    syncCommittedState(owner);
                    return true;
                }
                state.clearLatestBitesTheDustActivationReceipt(
                        RECEIPT_AUTHORITY.stateCapability());
                compensateAppliedCommit(
                        owner,
                        sourceLevel,
                        mutations,
                        inverses,
                        inverseDayTime,
                        targetDayTime,
                        inverseOwnerHealth,
                        ownerMaxHealth);
                if (injectedCommitFault(exception)) {
                    LOGGER.warn(
                            "Bites the Dust attacker-kill fault injected "
                                    + "for GameTest",
                            exception);
                } else {
                    LOGGER.error(
                            "Bites the Dust attacker kill failed before "
                                    + "becoming irreversible",
                            exception);
                }
                return false;
            }
            if (condemned.isAlive()) {
                state.clearLatestBitesTheDustActivationReceipt(
                        RECEIPT_AUTHORITY.stateCapability());
                condemned.setHealth(inverseCondemnedHealth);
                compensateAppliedCommit(
                        owner,
                        sourceLevel,
                        mutations,
                        inverses,
                        inverseDayTime,
                        targetDayTime,
                        inverseOwnerHealth,
                        ownerMaxHealth);
                return false;
            }
            registerCommittedReceipt(
                    owner.getServer(),
                    owner.getUUID(),
                    preparedReceipt);
            syncCommittedState(owner);
            return true;
        }
        if (injectedCommitFault(outcome.failure())) {
            LOGGER.warn(
                    "Bites the Dust commit fault injected for GameTest; "
                            + "core inverse journal status={}, "
                            + "attemptedEntries={}",
                    outcome.status(),
                    outcome.attemptedEntries(),
                    outcome.failure());
        } else {
            LOGGER.error(
                    "Bites the Dust commit failed; core inverse "
                            + "journal status={}, attemptedEntries={}",
                    outcome.status(),
                    outcome.attemptedEntries(),
                    outcome.failure());
        }
        for (AtomicRollbackJournal.InverseFailure failure :
                outcome.inverseFailures()) {
            LOGGER.error(
                    "Bites the Dust inverse failed for {}",
                    failure.entryId(),
                    failure.failure());
        }
        return false;
    }

    private static void syncCommittedState(ServerPlayer owner) {
        try {
            KillerQueenLionAddon.syncState(owner);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Bites the Dust committed but its optional "
                            + "owner-state sync failed",
                    exception);
        }
    }

    private static void registerCommittedReceipt(
            MinecraftServer server,
            UUID ownerUuid,
            BitesTheDustActivationReceipt receipt) {
        synchronized (COMMITTED_RECEIPTS) {
            COMMITTED_RECEIPTS
                    .computeIfAbsent(
                            server, ignored -> new LinkedHashMap<>())
                    .put(ownerUuid, receipt);
        }
    }

    private static void discardCommittedReceipt(
            MinecraftServer server, UUID ownerUuid) {
        synchronized (COMMITTED_RECEIPTS) {
            Map<UUID, BitesTheDustActivationReceipt> receipts =
                    COMMITTED_RECEIPTS.get(server);
            if (receipts == null) {
                return;
            }
            receipts.remove(ownerUuid);
            if (receipts.isEmpty()) {
                COMMITTED_RECEIPTS.remove(server);
            }
        }
    }

    private static void compensateAppliedCommit(
            ServerPlayer owner,
            ServerLevel sourceLevel,
            List<PlayerMutation> mutations,
            List<PlayerInverse> inverses,
            long inverseDayTime,
            long committedDayTime,
            float inverseOwnerHealth,
            float committedOwnerHealth) {
        List<AtomicRollbackJournal.Entry> compensation =
                new ArrayList<>();
        compensation.add(AtomicRollbackJournal.entry(
                "restore_owner_health",
                () -> owner.setHealth(inverseOwnerHealth),
                () -> owner.setHealth(committedOwnerHealth)));
        compensation.add(AtomicRollbackJournal.entry(
                "restore_day_time",
                () -> sourceLevel.setDayTime(inverseDayTime),
                () -> sourceLevel.setDayTime(committedDayTime)));
        for (int index = mutations.size() - 1;
                index >= 0;
                index--) {
            PlayerMutation mutation = mutations.get(index);
            PlayerInverse inverse = inverses.get(index);
            compensation.add(AtomicRollbackJournal.entry(
                    "restore_player/"
                            + inverse.player().getUUID(),
                    () -> {
                        teleportAndVerify(
                                inverse.player(),
                                inverse.level(),
                                inverse.snapshot(),
                                "compensation");
                        inverse.player().setHealth(inverse.health());
                    },
                    () -> teleportAndVerify(
                            mutation.player(),
                            mutation.targetLevel(),
                            mutation.destination(),
                            "compensation_inverse")));
        }
        Outcome compensationOutcome =
                AtomicRollbackJournal.apply(compensation);
        if (!compensationOutcome.applied()) {
            throw new IllegalStateException(
                    "Bites the Dust compensation failed; refusing "
                            + "to report COMMIT_FAILED with partial "
                            + "effects",
                    compensationOutcome.failure());
        }
    }

    private static void teleportAndVerify(
            ServerPlayer player,
            ServerLevel level,
            BitesTheDustPlayerSnapshot snapshot,
            String stage) {
        if (!teleport(player, level, snapshot)
                || player.serverLevel() != level
                || player.position().distanceToSqr(
                        snapshot.x(),
                        snapshot.y(),
                        snapshot.z()) > 1.0E-6D) {
            throw new IllegalStateException(
                    "Bites the Dust " + stage
                            + " teleport was rejected: "
                            + player.getUUID());
        }
    }

    private static BitesTheDustActivationReceipt
            prepareActivationReceipt(
                    ServerPlayer owner,
                    ServerLevel sourceLevel,
                    KillerQueenState state,
                    PendingTrigger trigger,
                    ServerPlayer attacker,
                    long targetDayTime,
                    long sourceDayTimeBefore,
                    float ownerHealthBefore,
                    float ownerMaxHealth,
                    float attackerHealthBefore,
                    int teleportedPlayers,
                    int journalEntryCount) {
        UUID markerEpoch = state.bitesTheDustMarkerEpoch();
        ResourceLocation markerDimension =
                state.bitesTheDustMarkerDimension();
        boolean victimStillBound = state.hasBitesTheDustVictim()
                && trigger.victimUuid().equals(
                        state.bitesTheDustVictimUuid());
        if (markerEpoch == null
                || markerDimension == null
                || !state.hasBitesTheDustMarker()
                || !victimStillBound) {
            throw new IllegalStateException(
                    "Bites the Dust receipt lost its marker binding");
        }
        ReceiptAuthority authority = RECEIPT_AUTHORITY;
        BitesTheDustActivationReceipt unsigned =
                new BitesTheDustActivationReceipt(
                        UUID.randomUUID(),
                        authority.epoch(),
                        markerEpoch,
                        owner.getUUID(),
                        trigger.victimUuid(),
                        attacker.getUUID(),
                        markerDimension,
                        sourceLevel.dimension().location(),
                        targetDayTime,
                        state.bitesTheDustMarkerGameTime(),
                        sourceDayTimeBefore,
                        targetDayTime,
                        trigger.startTick(),
                        owner.getServer().getTickCount(),
                        state.bitesTheDustPlayerSnapshots().size(),
                        teleportedPlayers,
                        ownerHealthBefore,
                        ownerMaxHealth,
                        ownerMaxHealth,
                        attackerHealthBefore,
                        0.0F,
                        false,
                        true,
                        true,
                        Status.APPLIED.name(),
                        journalEntryCount,
                        journalEntryCount,
                        new byte[BitesTheDustActivationReceipt
                                .SEAL_BYTES]);
        return unsigned.withSeal(
                receiptSeal(unsigned, authority));
    }

    static boolean receiptIntegrityValid(
            BitesTheDustActivationReceipt receipt) {
        return receiptIntegrityValid(receipt, RECEIPT_AUTHORITY);
    }

    private static boolean receiptIntegrityValid(
            BitesTheDustActivationReceipt receipt,
            ReceiptAuthority authority) {
        return receipt != null
                && receipt.structurallyValid()
                && authority.epoch().equals(
                        receipt.authorityEpoch())
                && MessageDigest.isEqual(
                        receipt.seal(),
                        receiptSeal(receipt, authority));
    }

    private static byte[] receiptSeal(
            BitesTheDustActivationReceipt receipt,
            ReceiptAuthority authority) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(authority.key());
            receipt.updateCanonicalDigest(digest);
            return digest.digest();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static ReceiptAuthority newReceiptAuthority() {
        byte[] key =
                new byte[BitesTheDustActivationReceipt.SEAL_BYTES];
        new SecureRandom().nextBytes(key);
        return new ReceiptAuthority(
                UUID.randomUUID(), key, new Object());
    }

    static void simulateProcessAuthorityResetForTests(
            MinecraftServer server) {
        RECEIPT_AUTHORITY = newReceiptAuthority();
        synchronized (COMMITTED_RECEIPTS) {
            COMMITTED_RECEIPTS.remove(server);
        }
    }

    private static void triggerTestFault(
            MinecraftServer server,
            UUID ownerUuid,
            CommitFault point) {
        synchronized (TEST_COMMIT_FAULTS) {
            Map<UUID, CommitFault> faults =
                    TEST_COMMIT_FAULTS.get(server);
            if (faults == null || faults.get(ownerUuid) != point) {
                return;
            }
            faults.remove(ownerUuid);
            if (faults.isEmpty()) {
                TEST_COMMIT_FAULTS.remove(server);
            }
        }
        throw new InjectedCommitFaultException(
                "Injected Bites the Dust commit fault: " + point);
    }

    private static boolean injectedCommitFault(
            @Nullable Throwable failure) {
        for (Throwable cursor = failure;
                cursor != null;
                cursor = cursor.getCause()) {
            if (cursor instanceof InjectedCommitFaultException) {
                return true;
            }
        }
        return false;
    }

    private static final class InjectedCommitFaultException
            extends IllegalStateException {
        private InjectedCommitFaultException(String message) {
            super(message);
        }
    }

    static void setCommitFaultForTests(
            MinecraftServer server,
            UUID ownerUuid,
            CommitFault point) {
        synchronized (TEST_COMMIT_FAULTS) {
            if (point == null || point == CommitFault.NONE) {
                Map<UUID, CommitFault> faults =
                        TEST_COMMIT_FAULTS.get(server);
                if (faults != null) {
                    faults.remove(ownerUuid);
                    if (faults.isEmpty()) {
                        TEST_COMMIT_FAULTS.remove(server);
                    }
                }
            } else {
                TEST_COMMIT_FAULTS
                        .computeIfAbsent(
                                server,
                                ignored -> new LinkedHashMap<>())
                        .put(ownerUuid, point);
            }
        }
    }

    static int pendingAgeForTests(
            MinecraftServer server, UUID attackerUuid) {
        synchronized (PENDING) {
            LinkedHashMap<UUID, PendingTrigger> pending =
                    PENDING.get(server);
            PendingTrigger trigger = pending != null
                    ? pending.get(attackerUuid)
                    : null;
            return trigger != null
                    ? server.getTickCount() - trigger.startTick()
                    : -1;
        }
    }

    private static boolean teleport(
            ServerPlayer player,
            ServerLevel level,
            BitesTheDustPlayerSnapshot snapshot) {
        return player.teleportTo(
                level,
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                Set.of(),
                snapshot.yRot(),
                snapshot.xRot());
    }

    private static boolean loadedDestination(
            ServerLevel level,
            BitesTheDustPlayerSnapshot snapshot) {
        BlockPos position = BlockPos.containing(
                snapshot.x(), snapshot.y(), snapshot.z());
        return snapshot.valid()
                && position.getY() >= level.getMinBuildHeight()
                && position.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(position)
                && level.hasChunkAt(position);
    }

    @Nullable
    private static BitesTheDustPlayerSnapshot respawnSnapshot(
            ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server.getLevel(
                player.getRespawnDimension());
        if (level == null) {
            level = server.overworld();
        }
        BlockPos position = player.getRespawnPosition();
        if (position == null) {
            position = level.getSharedSpawnPos();
        }
        return new BitesTheDustPlayerSnapshot(
                player.getUUID(),
                level.dimension().location(),
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D,
                player.getRespawnAngle(),
                0.0F);
    }

    public static boolean validVictim(
            Player owner, LivingEntity victim) {
        return victim != null
                && victim != owner
                && victim.isAlive()
                && !(victim instanceof StandEntity)
                && !isStandUser(victim);
    }

    @Nullable
    private static ServerPlayer ownerForVictim(
            MinecraftServer server, LivingEntity victim) {
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(server);
        index.expireBindings(server);
        ResourceLocation dimension =
                victim.level().dimension().location();
        BitesTheDustVictimIndex.Binding binding =
                index.observeVictimDimension(
                        victim.getUUID(), dimension);
        if (binding == null) {
            return null;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(
                binding.ownerUuid());
        if (owner == null
                || !owner.hasData(
                        KillerQueenLionAddon.KILLER_QUEEN_STATE)
                || !KillerQueenLionAddon.isKillerQueenUser(owner)) {
            return null;
        }
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!bindingMatchesState(owner.getUUID(), binding, state)) {
            return null;
        }
        if (state.updateBitesTheDustVictimDimension(
                victim.getUUID(), dimension)) {
            KillerQueenLionAddon.syncState(owner);
        }
        return owner;
    }

    private static boolean bindingMatchesState(
            UUID ownerUuid,
            BitesTheDustVictimIndex.Binding binding,
            KillerQueenState state) {
        return ownerUuid.equals(binding.ownerUuid())
                && binding.victimUuid().equals(
                        state.bitesTheDustVictimUuid())
                && binding.markerEpoch().equals(
                        state.bitesTheDustMarkerEpoch())
                && binding.markerDimension().equals(
                        state.bitesTheDustMarkerDimension())
                && binding.markerGameTime()
                        == state.bitesTheDustMarkerGameTime();
    }

    private static boolean ensureAutomaticTriggerReady(
            ServerPlayer owner, LivingEntity victim) {
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!state.bitesTheDustPlayerSnapshots().isEmpty()) {
            return true;
        }
        return migrateZeroSnapshotMarker(
                owner,
                victim.getUUID(),
                victim.level().dimension().location());
    }

    private static boolean migrateZeroSnapshotMarker(
            ServerPlayer owner,
            UUID victimUuid,
            ResourceLocation victimDimension) {
        KillerQueenState state = KillerQueenLionAddon.state(owner);
        if (!state.hasBitesTheDustVictim()
                || !victimUuid.equals(
                        state.bitesTheDustVictimUuid())) {
            return false;
        }
        List<BitesTheDustPlayerSnapshot> snapshots =
                captureMarkerSnapshots(owner);
        if (snapshots.isEmpty()) {
            return false;
        }
        ResourceLocation markerDimension =
                owner.serverLevel().dimension().location();
        long markerGameTime = owner.serverLevel().getGameTime();
        UUID markerEpoch = UUID.randomUUID();
        BitesTheDustVictimIndex index =
                BitesTheDustVictimIndex.get(owner.getServer());
        if (index.reconcile(
                        owner.getUUID(),
                        victimUuid,
                        victimDimension,
                        markerEpoch,
                        markerDimension,
                        markerGameTime)
                != BitesTheDustVictimIndex
                        .ReconcileResult.ACCEPTED) {
            return false;
        }
        state.setBitesTheDustMarker(
                owner.serverLevel().getDayTime(),
                markerGameTime,
                markerDimension,
                markerEpoch,
                snapshots);
        state.selectBitesTheDustVictim(
                victimUuid, victimDimension);
        cancelForOwner(owner.getServer(), owner.getUUID());
        discardCommittedReceipt(
                owner.getServer(), owner.getUUID());
        forceUnsummon(owner);
        KillerQueenLionAddon.syncState(owner);
        return true;
    }

    @Nullable
    private static LivingEntity findLivingEntity(
            MinecraftServer server, UUID entityUuid) {
        ServerPlayer player = server.getPlayerList()
                .getPlayer(entityUuid);
        if (player != null) {
            return player;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static List<BitesTheDustPlayerSnapshot>
            captureMarkerSnapshots(ServerPlayer owner) {
        return owner.serverLevel().players().stream()
                .sorted(Comparator.comparing(
                        player -> player.getUUID().toString()))
                .limit(KillerQueenState.MAX_BTD_PLAYER_SNAPSHOTS)
                .map(BitesTheDustPlayerSnapshot::capture)
                .toList();
    }

    @Nullable
    private static LivingEntity resolveAttacker(
            @Nullable Entity source) {
        if (source instanceof StandEntity stand) {
            return stand.getUser();
        }
        return source instanceof LivingEntity living
                ? living
                : null;
    }

    private static List<ServerPlayer> orderedPlayers(
            MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(
                        player -> player.getUUID().toString()))
                .toList();
    }

    @Nullable
    private static ServerLevel level(
            MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(
                Registries.DIMENSION, dimension));
    }

    private static LinkedHashMap<UUID, PendingTrigger> pending(
            MinecraftServer server) {
        synchronized (PENDING) {
            return PENDING.computeIfAbsent(
                    server, ignored -> new LinkedHashMap<>());
        }
    }

    private record ReceiptAuthority(
            UUID epoch, byte[] key, Object stateCapability) {}

    private record PendingTrigger(
            UUID ownerUuid,
            UUID victimUuid,
            UUID attackerUuid,
            int startTick) {}

    private record PlayerMutation(
            ServerPlayer player,
            ServerLevel targetLevel,
            BitesTheDustPlayerSnapshot destination) {}

    private record PlayerInverse(
            ServerPlayer player,
            ServerLevel level,
            BitesTheDustPlayerSnapshot snapshot,
            float health) {}
}
