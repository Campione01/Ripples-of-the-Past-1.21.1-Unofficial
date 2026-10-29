package rotp.addons.tusk.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.TuskOperationEntity;
import rotp.addons.tusk.entity.WormholeArmEntity;
import rotp.addons.tusk.entity.WormholeEntity;
import rotp.addons.tusk.entity.WormholePortalEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TuskPortalOperations extends SavedData {
    public static final int OPERATION_LIFETIME_TICKS = 600;
    public static final int RECONCILIATION_TICKS = 40;
    public static final int ACCELERATION_RATE_LIMIT_TICKS = 4;
    public static final double MOVE_RANGE = 12.0D;
    public static final double MOVE_XZ_FACTOR = 0.25D;
    public static final double MOVE_Y_FACTOR = 1.0D;
    public static final double GROUND_COMPENSATION = 0.7D;

    private static final String FILE_NAME = "rotp_t-portal_operations";
    private static final String OPERATIONS_TAG = "Operations";
    private static final SavedData.Factory<TuskPortalOperations> FACTORY =
            new SavedData.Factory<>(
                    TuskPortalOperations::new,
                    TuskPortalOperations::load);

    private final Map<UUID, OperationRecord> operationsById =
            new HashMap<>();
    private final Map<UUID, UUID> operationByOwner = new HashMap<>();
    private final Map<UUID, Integer> unresolvedTicks = new HashMap<>();
    private final Map<UUID, Long> lastAcceleration = new HashMap<>();
    private final Map<UUID, List<MoveReceipt>> moveReceiptsByOwner =
            new HashMap<>();
    private MinecraftServer server;

    public enum OperationKind {
        PORTAL_PAIR,
        REMOTE_ARM
    }

    public enum MemberRole {
        ENTRY,
        EXIT,
        ARM
    }

    public static TuskPortalOperations load(
            CompoundTag tag, HolderLookup.Provider registries) {
        TuskPortalOperations operations = new TuskPortalOperations();
        ListTag records = tag.getList(
                OPERATIONS_TAG, Tag.TAG_COMPOUND);
        for (Tag element : records) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            OperationRecord record =
                    OperationRecord.fromNbt(recordTag);
            if (record == null) {
                continue;
            }
            UUID previousId =
                    operations.operationByOwner.get(record.ownerUuid());
            OperationRecord previous = previousId != null
                    ? operations.operationsById.get(previousId)
                    : null;
            if (previous != null
                    && compareForRecovery(previous, record) >= 0) {
                continue;
            }
            if (previous != null) {
                operations.operationsById.remove(previous.operationUuid());
            }
            operations.operationsById.put(
                    record.operationUuid(), record);
            operations.operationByOwner.put(
                    record.ownerUuid(), record.operationUuid());
        }
        return operations;
    }

    public static TuskPortalOperations get(MinecraftServer server) {
        DimensionDataStorage storage =
                server.overworld().getDataStorage();
        TuskPortalOperations operations =
                storage.computeIfAbsent(FACTORY, FILE_NAME);
        operations.server = server;
        return operations;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        operationsById.values().stream()
                .sorted(Comparator.comparing(
                        record -> record.operationUuid().toString()))
                .map(OperationRecord::toNbt)
                .forEach(records::add);
        tag.put(OPERATIONS_TAG, records);
        return tag;
    }

    public OperationRecord getOwned(UUID ownerUuid) {
        UUID operationUuid = operationByOwner.get(ownerUuid);
        return operationUuid != null
                ? operationsById.get(operationUuid)
                : null;
    }

    public OperationRecord getOperation(UUID operationUuid) {
        return operationsById.get(operationUuid);
    }

    public int operationCount() {
        return operationsById.size();
    }

    public boolean hasOwned(UUID ownerUuid) {
        return getOwned(ownerUuid) != null;
    }

    public boolean hasActiveArm(ServerPlayer owner) {
        OperationRecord record = getOwned(owner.getUUID());
        return record != null
                && record.kind() == OperationKind.REMOTE_ARM;
    }

    public boolean hasOwnedKind(
            ServerPlayer owner, OperationKind expectedKind) {
        OperationRecord record = getOwned(owner.getUUID());
        return isOwnerValid(owner, record)
                && record.kind() == expectedKind;
    }

    public void resetMoveDiagnostics(UUID ownerUuid) {
        moveReceiptsByOwner.remove(ownerUuid);
    }

    public List<MoveReceipt> moveReceipts(UUID ownerUuid) {
        return List.copyOf(moveReceiptsByOwner.getOrDefault(
                ownerUuid, List.of()));
    }

    public boolean canCreate(ServerPlayer owner) {
        return canCreate(owner, latestSource(owner));
    }

    public boolean createPortalPair(ServerPlayer owner) {
        WormholeEntity source = latestSource(owner);
        if (!canCreate(owner, source)) {
            return false;
        }
        long expiresAt = owner.serverLevel().getGameTime()
                + OPERATION_LIFETIME_TICKS;
        UUID operationUuid = UUID.randomUUID();
        Vec3 entryPos = Vec3.atBottomCenterOf(
                owner.blockPosition().above());
        Vec3 exitPos = Vec3.atCenterOf(source.blockPosition());

        WormholePortalEntity entry = new WormholePortalEntity(
                owner.serverLevel(),
                operationUuid,
                owner.getUUID(),
                expiresAt,
                true);
        WormholePortalEntity exit = new WormholePortalEntity(
                owner.serverLevel(),
                operationUuid,
                owner.getUUID(),
                expiresAt,
                false);
        entry.setPos(entryPos);
        exit.setPos(exitPos);
        entry.setPeerUuid(exit.getUUID());
        exit.setPeerUuid(entry.getUUID());

        OperationRecord record = OperationRecord.portalPair(
                operationUuid,
                owner.getUUID(),
                owner.level().dimension().location(),
                expiresAt,
                entry.getUUID(),
                entryPos,
                exit.getUUID(),
                exitPos);
        if (!spawnAtomically(owner.serverLevel(), record, entry, exit)) {
            return false;
        }
        source.discard();
        playOpen(owner.serverLevel(), entryPos);
        playOpen(owner.serverLevel(), exitPos);
        return true;
    }

    public boolean createRemoteArm(ServerPlayer owner) {
        WormholeEntity source = latestSource(owner);
        if (!canCreate(owner, source)) {
            return false;
        }
        long expiresAt = owner.serverLevel().getGameTime()
                + OPERATION_LIFETIME_TICKS;
        UUID operationUuid = UUID.randomUUID();
        Vec3 armPos = source.position();
        boolean leftArm = armSideFor(owner) == HumanoidArm.LEFT;
        WormholeArmEntity arm = new WormholeArmEntity(
                owner.serverLevel(),
                operationUuid,
                owner.getUUID(),
                expiresAt,
                leftArm);
        arm.setPos(armPos);
        OperationRecord record = OperationRecord.remoteArm(
                operationUuid,
                owner.getUUID(),
                owner.level().dimension().location(),
                expiresAt,
                arm.getUUID(),
                armPos,
                leftArm);
        if (!spawnAtomically(owner.serverLevel(), record, arm)) {
            return false;
        }
        source.discard();
        playOpen(owner.serverLevel(), armPos);
        owner.serverLevel().playSound(
                null,
                armPos.x,
                armPos.y,
                armPos.z,
                TuskAddon.WORMHOLE_TELEPORT.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        return true;
    }

    public boolean moveOwned(
            ServerPlayer owner, OperationKind expectedKind) {
        OperationRecord record = getOwned(owner.getUUID());
        if (!isOwnerValid(owner, record)
                || record.kind() != expectedKind) {
            return false;
        }
        TuskOperationEntity member = expectedKind
                == OperationKind.PORTAL_PAIR
                ? resolvePortalMember(
                        owner.serverLevel(),
                        record.exitUuid(),
                        record,
                        MemberRole.EXIT)
                : resolveArmMember(owner.serverLevel(), record);
        if (member == null) {
            return false;
        }

        MovementTarget target = rayTarget(owner);
        Vec3 before = member.position();
        Vec3 entryBefore = record.entryPosition();
        boolean verticalCollisionBefore = member.verticalCollision;
        Vec3 difference = target.position()
                .subtract(before);
        Vec3 motion = difference.multiply(
                MOVE_XZ_FACTOR,
                MOVE_Y_FACTOR,
                MOVE_XZ_FACTOR).add(
                        0.0D,
                        target.inAir() ? 0.0D : GROUND_COMPENSATION,
                        0.0D);
        member.setDeltaMovement(motion);
        member.ownedMove(owner.serverLevel().getGameTime());
        updateMemberPosition(member);
        OperationRecord updated = getOperation(record.operationUuid());
        List<MoveReceipt> receipts = moveReceiptsByOwner.computeIfAbsent(
                owner.getUUID(), ignored -> new ArrayList<>());
        receipts.add(new MoveReceipt(
                receipts.size() + 1,
                owner.getUUID(),
                record.operationUuid(),
                member.getUUID(),
                expectedKind,
                member.memberRole(),
                target.rayStart(),
                target.rayEnd(),
                target.position(),
                target.inAir(),
                before,
                difference,
                motion,
                verticalCollisionBefore,
                member.position(),
                entryBefore,
                updated != null ? updated.entryPosition() : null));
        if (receipts.size() > 8) {
            receipts.removeFirst();
        }
        return true;
    }

    public boolean closeOwned(UUID ownerUuid) {
        OperationRecord record = getOwned(ownerUuid);
        return record != null && closeOperation(record.operationUuid());
    }

    public boolean closeOwned(
            UUID ownerUuid, OperationKind expectedKind) {
        OperationRecord record = getOwned(ownerUuid);
        return record != null
                && record.kind() == expectedKind
                && closeOperation(record.operationUuid());
    }

    public boolean closeOperation(UUID operationUuid) {
        if (operationUuid == null) {
            return false;
        }
        OperationRecord record = operationsById.remove(operationUuid);
        if (record == null) {
            return false;
        }
        operationByOwner.remove(record.ownerUuid(), operationUuid);
        unresolvedTicks.remove(operationUuid);
        lastAcceleration.remove(record.ownerUuid());
        moveReceiptsByOwner.remove(record.ownerUuid());
        setDirty();

        ServerLevel level = levelFor(record);
        boolean closeSoundPlayed = false;
        if (level != null) {
            for (UUID memberUuid : record.memberUuids()) {
                Entity member = level.getEntity(memberUuid);
                if (member instanceof TuskOperationEntity operationMember) {
                    if (!closeSoundPlayed) {
                        level.playSound(
                                null,
                                operationMember,
                                TuskAddon.WORMHOLE_CLOSE.get(),
                                operationMember.getSoundSource(),
                                1.0F,
                                1.0F);
                        closeSoundPlayed = true;
                    }
                    operationMember.discard();
                }
            }
        }
        return true;
    }

    public void tick(MinecraftServer currentServer) {
        server = currentServer;
        List<UUID> toClose = new ArrayList<>();
        for (OperationRecord record :
                List.copyOf(operationsById.values())) {
            ServerLevel level = levelFor(record);
            ServerPlayer owner = currentServer.getPlayerList()
                    .getPlayer(record.ownerUuid());
            if (level == null
                    || owner == null
                    || !isOwnerValid(owner, record)
                    || level.getGameTime() >= record.expiresAt()) {
                toClose.add(record.operationUuid());
                continue;
            }

            boolean loadedMemberMissing = false;
            boolean invalidMember = false;
            for (MemberRole role : rolesFor(record.kind())) {
                UUID memberUuid = record.memberUuid(role);
                Vec3 position = record.memberPosition(role);
                Entity member = level.getEntity(memberUuid);
                if (member == null) {
                    if (level.hasChunkAt(BlockPos.containing(position))) {
                        loadedMemberMissing = true;
                    }
                } else if (!(member
                        instanceof TuskOperationEntity operationMember)
                        || !matches(record, operationMember)) {
                    invalidMember = true;
                    break;
                }
            }
            if (invalidMember) {
                toClose.add(record.operationUuid());
                continue;
            }
            if (loadedMemberMissing) {
                int ticks = unresolvedTicks.merge(
                        record.operationUuid(), 1, Integer::sum);
                if (ticks >= RECONCILIATION_TICKS) {
                    toClose.add(record.operationUuid());
                }
            } else {
                unresolvedTicks.remove(record.operationUuid());
            }
        }
        toClose.forEach(this::closeOperation);
    }

    public boolean validateMember(TuskOperationEntity member) {
        OperationRecord record =
                operationsById.get(member.getOperationUuid());
        if (record == null) {
            return false;
        }
        if (!matches(record, member)) {
            closeOperation(record.operationUuid());
            return false;
        }
        return true;
    }

    public ServerPlayer resolveOwner(TuskOperationEntity member) {
        OperationRecord record =
                operationsById.get(member.getOperationUuid());
        if (record == null || server == null) {
            return null;
        }
        ServerPlayer owner = server.getPlayerList()
                .getPlayer(record.ownerUuid());
        return isOwnerValid(owner, record) ? owner : null;
    }

    public WormholePortalEntity resolvePeer(
            WormholePortalEntity portal) {
        OperationRecord record =
                operationsById.get(portal.getOperationUuid());
        if (record == null
                || record.kind() != OperationKind.PORTAL_PAIR
                || !matches(record, portal)
                || portal.getPeerUuid() == null) {
            return null;
        }
        ServerLevel level = levelFor(record);
        if (level == null) {
            return null;
        }
        Entity peer = level.getEntity(portal.getPeerUuid());
        return peer instanceof WormholePortalEntity portalPeer
                && matches(record, portalPeer)
                && portalPeer.isEntry() != portal.isEntry()
                && portal.getUUID().equals(portalPeer.getPeerUuid())
                ? portalPeer
                : null;
    }

    public WormholeArmEntity resolveArmForShot(ServerPlayer owner) {
        OperationRecord record = getOwned(owner.getUUID());
        return isOwnerValid(owner, record)
                        && record.kind() == OperationKind.REMOTE_ARM
                ? resolveArmMember(owner.serverLevel(), record)
                : null;
    }

    public boolean accelerateInsidePortal(ServerPlayer owner) {
        OperationRecord record = getOwned(owner.getUUID());
        if (!owner.isAlive()
                || !owner.onGround()
                || !isOwnerValid(owner, record)
                || record.kind() != OperationKind.PORTAL_PAIR) {
            return false;
        }
        long gameTime = owner.serverLevel().getGameTime();
        long last = lastAcceleration.getOrDefault(
                owner.getUUID(), Long.MIN_VALUE / 2L);
        if (gameTime - last < ACCELERATION_RATE_LIMIT_TICKS) {
            return false;
        }
        for (UUID portalUuid :
                List.of(record.entryUuid(), record.exitUuid())) {
            Entity entity = owner.serverLevel().getEntity(portalUuid);
            if (entity instanceof WormholePortalEntity portal
                    && matches(record, portal)
                    && resolvePeer(portal) != null
                    && portal.contains(owner)) {
                portal.accelerateOccupant(owner.getUUID());
                lastAcceleration.put(owner.getUUID(), gameTime);
                return true;
            }
        }
        return false;
    }

    // 1.16 rule: more than 5 nails sends the main-hand arm, else the off-hand arm.
    public static InteractionHand armHand(int nails) {
        return nails > 5
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

    public InteractionHand armHandFor(ServerPlayer owner) {
        return armHand(TuskAddon.nailState(owner).nails());
    }

    public HumanoidArm armSideFor(ServerPlayer owner) {
        boolean mainHand =
                armHandFor(owner) == InteractionHand.MAIN_HAND;
        HumanoidArm mainArm = owner.getMainArm();
        if (mainHand) {
            return mainArm;
        }
        return mainArm == HumanoidArm.RIGHT
                ? HumanoidArm.LEFT
                : HumanoidArm.RIGHT;
    }

    public void updateArmSide(WormholeArmEntity arm) {
        OperationRecord record =
                operationsById.get(arm.getOperationUuid());
        if (record == null
                || record.kind() != OperationKind.REMOTE_ARM
                || !record.armUuid().equals(arm.getUUID())) {
            return;
        }
        operationsById.put(
                record.operationUuid(),
                record.withArmState(arm.position(), arm.isLeftArm()));
        setDirty();
    }

    public void updateMemberPosition(TuskOperationEntity member) {
        OperationRecord record =
                operationsById.get(member.getOperationUuid());
        if (record == null || !matches(record, member)) {
            return;
        }
        operationsById.put(
                record.operationUuid(),
                record.withMemberPosition(
                        member.memberRole(), member.position()));
        setDirty();
    }

    private boolean canCreate(
            ServerPlayer owner, WormholeEntity source) {
        return owner.isAlive()
                && !owner.isRemoved()
                && TuskAddon.isPortalActUser(owner)
                && isSummonedPortalAct(owner)
                && TuskAddon.nailState(owner).nails() > 0
                && getOwned(owner.getUUID()) == null
                && source != null
                && !source.isRemoved()
                && owner.getUUID().equals(source.getOwnerUuid())
                && source.level() == owner.level()
                && source.getExpiresAt()
                        > owner.serverLevel().getGameTime();
    }

    private boolean spawnAtomically(
            ServerLevel level,
            OperationRecord record,
            TuskOperationEntity... members) {
        List<TuskOperationEntity> spawned = new ArrayList<>();
        for (TuskOperationEntity member : members) {
            if (!level.addFreshEntity(member)) {
                spawned.forEach(Entity::discard);
                return false;
            }
            spawned.add(member);
        }
        operationsById.put(record.operationUuid(), record);
        operationByOwner.put(record.ownerUuid(), record.operationUuid());
        setDirty();
        return true;
    }

    private WormholeEntity latestSource(ServerPlayer owner) {
        WormholeEntity latest = null;
        for (Entity entity : owner.serverLevel().getAllEntities()) {
            if (!(entity instanceof WormholeEntity candidate)
                    || candidate.isRemoved()
                    || !owner.getUUID().equals(
                            candidate.getOwnerUuid())
                    || candidate.getExpiresAt()
                            <= owner.serverLevel().getGameTime()) {
                continue;
            }
            if (latest == null
                    || candidate.getCreatedAt() > latest.getCreatedAt()
                    || candidate.getCreatedAt() == latest.getCreatedAt()
                            && candidate.getUUID().toString()
                                    .compareTo(
                                            latest.getUUID().toString())
                                    > 0) {
                latest = candidate;
            }
        }
        return latest;
    }

    private boolean isOwnerValid(
            ServerPlayer owner, OperationRecord record) {
        return owner != null
                && record != null
                && owner.isAlive()
                && !owner.isRemoved()
                && owner.getUUID().equals(record.ownerUuid())
                && owner.level().dimension().location()
                        .equals(record.dimension())
                && isSummonedPortalAct(owner);
    }

    private static boolean isSummonedPortalAct(ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        int act = power != null
                ? TuskAddon.actIndex(power.getPowerType())
                : -1;
        return power != null
                && act >= TuskAddon.ACT_3
                && act <= TuskAddon.ACT_4
                && power.isSummoned();
    }

    private static MovementTarget rayTarget(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        Vec3 start = owner.getEyePosition();
        Vec3 end = start.add(
                owner.getViewVector(1.0F).scale(MOVE_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                owner));
        Vec3 position = hit.getType() == HitResult.Type.MISS
                ? end
                : hit.getLocation();
        boolean inAir = level.getBlockState(
                BlockPos.containing(position)).isAir();
        return new MovementTarget(start, end, position, inAir);
    }

    private record MovementTarget(
            Vec3 rayStart,
            Vec3 rayEnd,
            Vec3 position,
            boolean inAir) {}

    public record MoveReceipt(
            int sequence,
            UUID ownerUuid,
            UUID operationUuid,
            UUID memberUuid,
            OperationKind kind,
            MemberRole role,
            Vec3 rayStart,
            Vec3 rayEnd,
            Vec3 target,
            boolean inAir,
            Vec3 before,
            Vec3 difference,
            Vec3 motion,
            boolean verticalCollisionBefore,
            Vec3 after,
            Vec3 entryBefore,
            Vec3 entryAfter) {}

    private WormholePortalEntity resolvePortalMember(
            ServerLevel level,
            UUID memberUuid,
            OperationRecord record,
            MemberRole role) {
        Entity entity = level.getEntity(memberUuid);
        return entity instanceof WormholePortalEntity portal
                && portal.memberRole() == role
                && matches(record, portal)
                ? portal
                : null;
    }

    private WormholeArmEntity resolveArmMember(
            ServerLevel level, OperationRecord record) {
        Entity entity = level.getEntity(record.armUuid());
        return entity instanceof WormholeArmEntity arm
                && matches(record, arm)
                ? arm
                : null;
    }

    private boolean matches(
            OperationRecord record, TuskOperationEntity member) {
        return record.operationUuid().equals(member.getOperationUuid())
                && record.ownerUuid().equals(member.getOwnerUuid())
                && record.expiresAt() == member.getExpiresAt()
                && record.memberUuid(member.memberRole())
                        .equals(member.getUUID())
                && member.level().dimension().location()
                        .equals(record.dimension());
    }

    private ServerLevel levelFor(OperationRecord record) {
        if (server == null || record == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(
                Registries.DIMENSION, record.dimension());
        return server.getLevel(key);
    }

    private static List<MemberRole> rolesFor(OperationKind kind) {
        return kind == OperationKind.PORTAL_PAIR
                ? List.of(MemberRole.ENTRY, MemberRole.EXIT)
                : List.of(MemberRole.ARM);
    }

    private static int compareForRecovery(
            OperationRecord first, OperationRecord second) {
        int expiry = Long.compare(
                first.expiresAt(), second.expiresAt());
        return expiry != 0
                ? expiry
                : first.operationUuid().toString().compareTo(
                        second.operationUuid().toString());
    }

    private static void playOpen(ServerLevel level, Vec3 position) {
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                TuskAddon.WORMHOLE_OPEN.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    public record OperationRecord(
            UUID operationUuid,
            UUID ownerUuid,
            ResourceLocation dimension,
            OperationKind kind,
            long expiresAt,
            UUID entryUuid,
            Vec3 entryPosition,
            UUID exitUuid,
            Vec3 exitPosition,
            UUID armUuid,
            Vec3 armPosition,
            boolean armLeft) {
        private static final String OPERATION_TAG = "Operation";
        private static final String OWNER_TAG = "Owner";
        private static final String DIMENSION_TAG = "Dimension";
        private static final String KIND_TAG = "Kind";
        private static final String EXPIRES_AT_TAG = "ExpiresAt";
        private static final String ENTRY_TAG = "Entry";
        private static final String ENTRY_POS_TAG = "EntryPos";
        private static final String EXIT_TAG = "Exit";
        private static final String EXIT_POS_TAG = "ExitPos";
        private static final String ARM_TAG = "Arm";
        private static final String ARM_POS_TAG = "ArmPos";
        private static final String ARM_LEFT_TAG = "ArmLeft";

        public OperationRecord {
            if (operationUuid == null
                    || ownerUuid == null
                    || dimension == null
                    || kind == null) {
                throw new IllegalArgumentException(
                        "Operation identity is required.");
            }
            if (kind == OperationKind.PORTAL_PAIR
                    && (entryUuid == null
                            || entryPosition == null
                            || exitUuid == null
                            || exitPosition == null)) {
                throw new IllegalArgumentException(
                        "Portal pair members are required.");
            }
            if (kind == OperationKind.REMOTE_ARM
                    && (armUuid == null || armPosition == null)) {
                throw new IllegalArgumentException(
                        "Remote arm member is required.");
            }
        }

        public static OperationRecord portalPair(
                UUID operationUuid,
                UUID ownerUuid,
                ResourceLocation dimension,
                long expiresAt,
                UUID entryUuid,
                Vec3 entryPosition,
                UUID exitUuid,
                Vec3 exitPosition) {
            return new OperationRecord(
                    operationUuid,
                    ownerUuid,
                    dimension,
                    OperationKind.PORTAL_PAIR,
                    expiresAt,
                    entryUuid,
                    entryPosition,
                    exitUuid,
                    exitPosition,
                    null,
                    null,
                    false);
        }

        public static OperationRecord remoteArm(
                UUID operationUuid,
                UUID ownerUuid,
                ResourceLocation dimension,
                long expiresAt,
                UUID armUuid,
                Vec3 armPosition,
                boolean armLeft) {
            return new OperationRecord(
                    operationUuid,
                    ownerUuid,
                    dimension,
                    OperationKind.REMOTE_ARM,
                    expiresAt,
                    null,
                    null,
                    null,
                    null,
                    armUuid,
                    armPosition,
                    armLeft);
        }

        public UUID memberUuid(MemberRole role) {
            return switch (role) {
                case ENTRY -> entryUuid;
                case EXIT -> exitUuid;
                case ARM -> armUuid;
            };
        }

        public Vec3 memberPosition(MemberRole role) {
            return switch (role) {
                case ENTRY -> entryPosition;
                case EXIT -> exitPosition;
                case ARM -> armPosition;
            };
        }

        public List<UUID> memberUuids() {
            return kind == OperationKind.PORTAL_PAIR
                    ? List.of(entryUuid, exitUuid)
                    : List.of(armUuid);
        }

        public OperationRecord withMemberPosition(
                MemberRole role, Vec3 position) {
            return switch (role) {
                case ENTRY -> portalPair(
                        operationUuid,
                        ownerUuid,
                        dimension,
                        expiresAt,
                        entryUuid,
                        position,
                        exitUuid,
                        exitPosition);
                case EXIT -> portalPair(
                        operationUuid,
                        ownerUuid,
                        dimension,
                        expiresAt,
                        entryUuid,
                        entryPosition,
                        exitUuid,
                        position);
                case ARM -> remoteArm(
                        operationUuid,
                        ownerUuid,
                        dimension,
                        expiresAt,
                        armUuid,
                        position,
                        armLeft);
            };
        }

        public OperationRecord withArmState(
                Vec3 position, boolean leftArm) {
            return remoteArm(
                    operationUuid,
                    ownerUuid,
                    dimension,
                    expiresAt,
                    armUuid,
                    position,
                    leftArm);
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(OPERATION_TAG, operationUuid);
            tag.putUUID(OWNER_TAG, ownerUuid);
            tag.putString(DIMENSION_TAG, dimension.toString());
            tag.putString(KIND_TAG, kind.name());
            tag.putLong(EXPIRES_AT_TAG, expiresAt);
            if (kind == OperationKind.PORTAL_PAIR) {
                tag.putUUID(ENTRY_TAG, entryUuid);
                tag.put(ENTRY_POS_TAG, writePosition(entryPosition));
                tag.putUUID(EXIT_TAG, exitUuid);
                tag.put(EXIT_POS_TAG, writePosition(exitPosition));
            } else {
                tag.putUUID(ARM_TAG, armUuid);
                tag.put(ARM_POS_TAG, writePosition(armPosition));
                tag.putBoolean(ARM_LEFT_TAG, armLeft);
            }
            return tag;
        }

        public static OperationRecord fromNbt(CompoundTag tag) {
            if (!tag.hasUUID(OPERATION_TAG)
                    || !tag.hasUUID(OWNER_TAG)
                    || !tag.contains(DIMENSION_TAG)
                    || !tag.contains(KIND_TAG)
                    || !tag.contains(EXPIRES_AT_TAG)) {
                return null;
            }
            try {
                UUID operationUuid = tag.getUUID(OPERATION_TAG);
                UUID ownerUuid = tag.getUUID(OWNER_TAG);
                ResourceLocation dimension =
                        ResourceLocation.parse(
                                tag.getString(DIMENSION_TAG));
                OperationKind kind = OperationKind.valueOf(
                        tag.getString(KIND_TAG));
                long expiresAt = tag.getLong(EXPIRES_AT_TAG);
                if (kind == OperationKind.PORTAL_PAIR
                        && tag.hasUUID(ENTRY_TAG)
                        && tag.hasUUID(EXIT_TAG)) {
                    Vec3 entryPos =
                            readPosition(tag.getCompound(ENTRY_POS_TAG));
                    Vec3 exitPos =
                            readPosition(tag.getCompound(EXIT_POS_TAG));
                    return entryPos != null && exitPos != null
                            ? portalPair(
                                    operationUuid,
                                    ownerUuid,
                                    dimension,
                                    expiresAt,
                                    tag.getUUID(ENTRY_TAG),
                                    entryPos,
                                    tag.getUUID(EXIT_TAG),
                                    exitPos)
                            : null;
                }
                if (kind == OperationKind.REMOTE_ARM
                        && tag.hasUUID(ARM_TAG)) {
                    Vec3 armPos =
                            readPosition(tag.getCompound(ARM_POS_TAG));
                    return armPos != null
                            ? remoteArm(
                                    operationUuid,
                                    ownerUuid,
                                    dimension,
                                    expiresAt,
                                    tag.getUUID(ARM_TAG),
                                    armPos,
                                    tag.getBoolean(ARM_LEFT_TAG))
                            : null;
                }
                return null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private static CompoundTag writePosition(Vec3 position) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            return tag;
        }

        private static Vec3 readPosition(CompoundTag tag) {
            return tag.contains("X")
                            && tag.contains("Y")
                            && tag.contains("Z")
                    ? new Vec3(
                            tag.getDouble("X"),
                            tag.getDouble("Y"),
                            tag.getDouble("Z"))
                    : null;
        }
    }
}
