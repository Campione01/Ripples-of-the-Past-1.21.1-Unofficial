package rotp.addons.killerqueen.state;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.btd.BitesTheDustActivationReceipt;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class KillerQueenState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 4;
    public static final int MAX_BTD_PLAYER_SNAPSHOTS = 256;
    private static final int MAX_CONSUMED_RECEIPT_IDS = 32;

    private static final String VERSION_TAG = "Version";
    private static final String PRIMARY_TARGET_TAG = "PrimaryTarget";
    private static final String PRIMARY_DIMENSION_TAG = "PrimaryDimension";
    private static final String BLOCK_TARGET_TAG = "BlockTarget";
    private static final String BLOCK_DIMENSION_TAG = "BlockDimension";
    private static final String SHEER_HEART_TAG = "SheerHeart";
    private static final String SHEER_HEART_DIMENSION_TAG =
            "SheerHeartDimension";
    private static final String BTD_MARKED_TAG = "BtdMarked";
    private static final String BTD_ARMED_TAG = "BtdArmed";
    private static final String BTD_VICTIM_TAG = "BtdVictim";
    private static final String BTD_VICTIM_DIMENSION_TAG =
            "BtdVictimDimension";
    private static final String BTD_MARKER_TIME_TAG = "BtdMarkerTime";
    private static final String BTD_MARKER_GAME_TIME_TAG =
            "BtdMarkerGameTime";
    private static final String BTD_MARKER_DIMENSION_TAG =
            "BtdMarkerDimension";
    private static final String BTD_MARKER_EPOCH_TAG =
            "BtdMarkerEpoch";
    private static final String BTD_PLAYER_SNAPSHOTS_TAG =
            "BtdPlayerSnapshots";
    private static final String BTD_TIME_STOP_TAG = "BtdTimeStop";
    private static final String BTD_AWAKENED_TAG = "BtdAwakened";

    @Nullable
    private UUID primaryTargetUuid;
    @Nullable
    private ResourceLocation primaryTargetDimension;
    @Nullable
    private BlockPos blockBombPos;
    @Nullable
    private ResourceLocation blockBombDimension;
    @Nullable
    private UUID sheerHeartUuid;
    @Nullable
    private ResourceLocation sheerHeartDimension;

    @Nullable
    private UUID bitesTheDustVictimUuid;
    @Nullable
    private ResourceLocation bitesTheDustVictimDimension;
    private long bitesTheDustMarkerTime = -1L;
    private long bitesTheDustMarkerGameTime = -1L;
    @Nullable
    private ResourceLocation bitesTheDustMarkerDimension;
    @Nullable
    private UUID bitesTheDustMarkerEpoch;
    private final Map<UUID, BitesTheDustPlayerSnapshot>
            bitesTheDustPlayerSnapshots = new LinkedHashMap<>();
    private int linkedTimeStopId = -1;
    private boolean bitesTheDustAwakened;
    private boolean legacyAwakeningMigrationPending;
    @Nullable
    private BitesTheDustActivationReceipt
            latestBitesTheDustActivationReceipt;
    private final Set<UUID> consumedReceiptIds =
            new LinkedHashSet<>();

    @Nullable
    private UUID bitesTheDustTriggerOwnerUuid;
    @Nullable
    private UUID bitesTheDustTriggerVictimUuid;
    private int bitesTheDustTriggerTicks = -1;

    public boolean hasPrimaryBomb() {
        return primaryTargetUuid != null
                && primaryTargetDimension != null;
    }

    @Nullable
    public UUID primaryTargetUuid() {
        return primaryTargetUuid;
    }

    @Nullable
    public ResourceLocation primaryTargetDimension() {
        return primaryTargetDimension;
    }

    public boolean hasBlockBomb() {
        return blockBombPos != null
                && blockBombDimension != null;
    }

    @Nullable
    public BlockPos blockBombPos() {
        return blockBombPos;
    }

    @Nullable
    public ResourceLocation blockBombDimension() {
        return blockBombDimension;
    }

    public boolean hasSheerHeart() {
        return sheerHeartUuid != null
                && sheerHeartDimension != null;
    }

    @Nullable
    public UUID sheerHeartUuid() {
        return sheerHeartUuid;
    }

    @Nullable
    public ResourceLocation sheerHeartDimension() {
        return sheerHeartDimension;
    }

    public boolean hasBitesTheDustMarker() {
        return bitesTheDustMarkerTime >= 0L
                && bitesTheDustMarkerGameTime >= 0L
                && bitesTheDustMarkerDimension != null
                && bitesTheDustMarkerEpoch != null;
    }

    public boolean hasBitesTheDustVictim() {
        return hasBitesTheDustMarker()
                && bitesTheDustVictimUuid != null
                && bitesTheDustVictimDimension != null;
    }

    public boolean bitesTheDustArmed() {
        return hasBitesTheDustVictim();
    }

    /** 1.16 BitesZaDustHandler.userToVictim: an armed Bites the Dust locks the fighting kit until quit_dust. */
    public static boolean bitesTheDustLocksKit(@Nullable Entity user) {
        return user instanceof Player player
                && player.hasData(KillerQueenAddon.KILLER_QUEEN_STATE)
                && KillerQueenAddon.state(player).hasBitesTheDustVictim();
    }

    @Nullable
    public UUID bitesTheDustVictimUuid() {
        return bitesTheDustVictimUuid;
    }

    @Nullable
    public ResourceLocation bitesTheDustVictimDimension() {
        return bitesTheDustVictimDimension;
    }

    public long bitesTheDustMarkerTime() {
        return bitesTheDustMarkerTime;
    }

    public long bitesTheDustMarkerGameTime() {
        return bitesTheDustMarkerGameTime;
    }

    @Nullable
    public ResourceLocation bitesTheDustMarkerDimension() {
        return bitesTheDustMarkerDimension;
    }

    @Nullable
    public UUID bitesTheDustMarkerEpoch() {
        return bitesTheDustMarkerEpoch;
    }

    public boolean bitesTheDustAwakened() {
        return bitesTheDustAwakened;
    }

    public boolean legacyAwakeningMigrationPending() {
        return legacyAwakeningMigrationPending;
    }

    public List<BitesTheDustPlayerSnapshot>
            bitesTheDustPlayerSnapshots() {
        return bitesTheDustPlayerSnapshots.values().stream()
                .sorted(Comparator.comparing(
                        snapshot -> snapshot.playerUuid().toString()))
                .toList();
    }

    @Nullable
    public BitesTheDustPlayerSnapshot bitesTheDustPlayerSnapshot(
            UUID playerUuid) {
        return bitesTheDustPlayerSnapshots.get(playerUuid);
    }

    public int linkedTimeStopId() {
        return linkedTimeStopId;
    }

    @Nullable
    public BitesTheDustActivationReceipt
            latestBitesTheDustActivationReceipt() {
        return latestBitesTheDustActivationReceipt;
    }

    public boolean hasBitesTheDustTrigger() {
        return bitesTheDustTriggerOwnerUuid != null
                && bitesTheDustTriggerVictimUuid != null
                && bitesTheDustTriggerTicks >= 0;
    }

    @Nullable
    public UUID bitesTheDustTriggerOwnerUuid() {
        return bitesTheDustTriggerOwnerUuid;
    }

    @Nullable
    public UUID bitesTheDustTriggerVictimUuid() {
        return bitesTheDustTriggerVictimUuid;
    }

    public int bitesTheDustTriggerTicks() {
        return bitesTheDustTriggerTicks;
    }

    public boolean setPrimaryBomb(
            UUID targetUuid, ResourceLocation dimension) {
        if (targetUuid == null || dimension == null) {
            return clearPrimaryBomb();
        }
        boolean changed = !targetUuid.equals(primaryTargetUuid)
                || !dimension.equals(primaryTargetDimension);
        primaryTargetUuid = targetUuid;
        primaryTargetDimension = dimension;
        return changed;
    }

    public boolean clearPrimaryBomb() {
        boolean changed = primaryTargetUuid != null
                || primaryTargetDimension != null;
        primaryTargetUuid = null;
        primaryTargetDimension = null;
        return changed;
    }

    public boolean setBlockBomb(
            BlockPos position, ResourceLocation dimension) {
        if (position == null || dimension == null) {
            return clearBlockBomb();
        }
        BlockPos immutable = position.immutable();
        boolean changed = !immutable.equals(blockBombPos)
                || !dimension.equals(blockBombDimension);
        blockBombPos = immutable;
        blockBombDimension = dimension;
        return changed;
    }

    public boolean clearBlockBomb() {
        boolean changed = blockBombPos != null
                || blockBombDimension != null;
        blockBombPos = null;
        blockBombDimension = null;
        return changed;
    }

    public boolean setSheerHeart(
            UUID entityUuid, ResourceLocation dimension) {
        if (entityUuid == null || dimension == null) {
            return clearSheerHeart();
        }
        boolean changed = !entityUuid.equals(sheerHeartUuid)
                || !dimension.equals(sheerHeartDimension);
        sheerHeartUuid = entityUuid;
        sheerHeartDimension = dimension;
        return changed;
    }

    public boolean clearSheerHeart() {
        boolean changed = sheerHeartUuid != null
                || sheerHeartDimension != null;
        sheerHeartUuid = null;
        sheerHeartDimension = null;
        return changed;
    }

    public boolean setBitesTheDustMarker(
            long markerTime,
            long markerGameTime,
            ResourceLocation markerDimension,
            List<BitesTheDustPlayerSnapshot> playerSnapshots) {
        return setBitesTheDustMarker(
                markerTime,
                markerGameTime,
                markerDimension,
                legacyMarkerEpoch(
                        markerTime,
                        markerGameTime,
                        markerDimension),
                playerSnapshots);
    }

    public boolean setBitesTheDustMarker(
            long markerTime,
            long markerGameTime,
            ResourceLocation markerDimension,
            UUID markerEpoch,
            List<BitesTheDustPlayerSnapshot> playerSnapshots) {
        if (markerTime < 0L
                || markerGameTime < 0L
                || markerDimension == null
                || markerEpoch == null) {
            return clearBitesTheDust();
        }
        boolean changed = markerTime != bitesTheDustMarkerTime
                || markerGameTime != bitesTheDustMarkerGameTime
                || !markerDimension.equals(
                        bitesTheDustMarkerDimension)
                || !markerEpoch.equals(bitesTheDustMarkerEpoch)
                || bitesTheDustVictimUuid != null
                || bitesTheDustVictimDimension != null
                || linkedTimeStopId != -1
                || latestBitesTheDustActivationReceipt != null
                || !bitesTheDustPlayerSnapshots.isEmpty();
        bitesTheDustMarkerTime = markerTime;
        bitesTheDustMarkerGameTime = markerGameTime;
        bitesTheDustMarkerDimension = markerDimension;
        bitesTheDustMarkerEpoch = markerEpoch;
        bitesTheDustVictimUuid = null;
        bitesTheDustVictimDimension = null;
        linkedTimeStopId = -1;
        latestBitesTheDustActivationReceipt = null;
        bitesTheDustPlayerSnapshots.clear();
        if (playerSnapshots != null) {
            playerSnapshots.stream()
                    .filter(BitesTheDustPlayerSnapshot::valid)
                    .sorted(Comparator.comparing(
                            snapshot ->
                                    snapshot.playerUuid().toString()))
                    .limit(MAX_BTD_PLAYER_SNAPSHOTS)
                    .forEach(snapshot ->
                            bitesTheDustPlayerSnapshots.put(
                                    snapshot.playerUuid(), snapshot));
        }
        return changed;
    }

    public boolean trackBitesTheDustPlayer(Player player) {
        return player != null
                && trackBitesTheDustPlayer(
                        BitesTheDustPlayerSnapshot.capture(player));
    }

    public boolean trackBitesTheDustPlayer(
            BitesTheDustPlayerSnapshot snapshot) {
        if (!hasBitesTheDustMarker()
                || snapshot == null
                || !snapshot.valid()
                || !bitesTheDustPlayerSnapshots.containsKey(
                                snapshot.playerUuid())
                        && bitesTheDustPlayerSnapshots.size()
                                >= MAX_BTD_PLAYER_SNAPSHOTS) {
            return false;
        }
        BitesTheDustPlayerSnapshot previous =
                bitesTheDustPlayerSnapshots.put(
                        snapshot.playerUuid(), snapshot);
        return !snapshot.equals(previous);
    }

    public boolean selectBitesTheDustVictim(
            UUID victimUuid, ResourceLocation victimDimension) {
        if (!hasBitesTheDustMarker()
                || victimUuid == null
                || victimDimension == null) {
            return false;
        }
        boolean changed = !victimUuid.equals(
                        bitesTheDustVictimUuid)
                || !victimDimension.equals(
                        bitesTheDustVictimDimension)
                || linkedTimeStopId != -1;
        bitesTheDustVictimUuid = victimUuid;
        bitesTheDustVictimDimension = victimDimension;
        linkedTimeStopId = -1;
        return changed;
    }

    public boolean updateBitesTheDustVictimDimension(
            UUID victimUuid, ResourceLocation victimDimension) {
        if (!hasBitesTheDustVictim()
                || victimUuid == null
                || victimDimension == null
                || !victimUuid.equals(bitesTheDustVictimUuid)
                || victimDimension.equals(
                        bitesTheDustVictimDimension)) {
            return false;
        }
        bitesTheDustVictimDimension = victimDimension;
        return true;
    }

    public boolean clearBitesTheDustVictim() {
        boolean changed = bitesTheDustVictimUuid != null
                || bitesTheDustVictimDimension != null
                || linkedTimeStopId != -1;
        bitesTheDustVictimUuid = null;
        bitesTheDustVictimDimension = null;
        linkedTimeStopId = -1;
        return changed;
    }

    public boolean linkTimeStop(int instanceId) {
        if (!bitesTheDustArmed() || instanceId < 0) {
            return false;
        }
        if (linkedTimeStopId == instanceId) {
            return false;
        }
        linkedTimeStopId = instanceId;
        return true;
    }

    public boolean unlinkTimeStop(int instanceId) {
        if (linkedTimeStopId != instanceId) {
            return false;
        }
        linkedTimeStopId = -1;
        return true;
    }

    public boolean setBitesTheDustAwakened(boolean awakened) {
        boolean changed = bitesTheDustAwakened != awakened
                || legacyAwakeningMigrationPending;
        bitesTheDustAwakened = awakened;
        legacyAwakeningMigrationPending = false;
        return changed;
    }

    public boolean completeLegacyAwakeningMigration(
            boolean retainUnlockedSkill) {
        if (!legacyAwakeningMigrationPending) {
            return false;
        }
        legacyAwakeningMigrationPending = false;
        if (retainUnlockedSkill) {
            bitesTheDustAwakened = true;
        }
        return true;
    }

    public boolean installBitesTheDustActivationReceipt(
            Object authority,
            UUID ownerUuid,
            BitesTheDustActivationReceipt receipt) {
        if (!BitesTheDustService
                        .authorizesReceiptStateMutation(authority)
                || !receiptMatchesCurrentBinding(ownerUuid, receipt)
                || consumedReceiptIds.contains(receipt.receiptId())) {
            return false;
        }
        boolean changed = latestBitesTheDustActivationReceipt == null
                || !latestBitesTheDustActivationReceipt.receiptId()
                        .equals(receipt.receiptId());
        latestBitesTheDustActivationReceipt = receipt;
        return changed;
    }

    @Nullable
    public BitesTheDustActivationReceipt
            consumeBitesTheDustActivationReceipt(
                    Object authority,
                    UUID ownerUuid,
                    UUID expectedVictimUuid,
                    UUID expectedAttackerUuid) {
        BitesTheDustActivationReceipt receipt =
                latestBitesTheDustActivationReceipt;
        if (!BitesTheDustService
                        .authorizesReceiptStateMutation(authority)
                || !receiptMatchesCurrentBinding(ownerUuid, receipt)
                || expectedVictimUuid == null
                || expectedAttackerUuid == null
                || !expectedVictimUuid.equals(
                        receipt.victimUuid())
                || !expectedAttackerUuid.equals(
                        receipt.attackerUuid())
                || consumedReceiptIds.contains(
                        receipt.receiptId())) {
            return null;
        }
        latestBitesTheDustActivationReceipt = null;
        consumedReceiptIds.add(receipt.receiptId());
        while (consumedReceiptIds.size()
                > MAX_CONSUMED_RECEIPT_IDS) {
            UUID oldest = consumedReceiptIds.iterator().next();
            consumedReceiptIds.remove(oldest);
        }
        return receipt;
    }

    public boolean clearLatestBitesTheDustActivationReceipt(
            Object authority) {
        if (!BitesTheDustService
                .authorizesReceiptStateMutation(authority)) {
            return false;
        }
        boolean changed = latestBitesTheDustActivationReceipt != null;
        latestBitesTheDustActivationReceipt = null;
        return changed;
    }

    public boolean clearBitesTheDust() {
        boolean changed = bitesTheDustVictimUuid != null
                || bitesTheDustVictimDimension != null
                || bitesTheDustMarkerTime != -1L
                || bitesTheDustMarkerGameTime != -1L
                || bitesTheDustMarkerDimension != null
                || bitesTheDustMarkerEpoch != null
                || !bitesTheDustPlayerSnapshots.isEmpty()
                || linkedTimeStopId != -1
                || latestBitesTheDustActivationReceipt != null;
        bitesTheDustVictimUuid = null;
        bitesTheDustVictimDimension = null;
        bitesTheDustMarkerTime = -1L;
        bitesTheDustMarkerGameTime = -1L;
        bitesTheDustMarkerDimension = null;
        bitesTheDustMarkerEpoch = null;
        bitesTheDustPlayerSnapshots.clear();
        linkedTimeStopId = -1;
        latestBitesTheDustActivationReceipt = null;
        return changed;
    }

    private boolean receiptMatchesCurrentBinding(
            UUID ownerUuid,
            @Nullable BitesTheDustActivationReceipt receipt) {
        return ownerUuid != null
                && receipt != null
                && receipt.verified()
                && hasBitesTheDustVictim()
                && ownerUuid.equals(receipt.ownerUuid())
                && bitesTheDustVictimUuid.equals(
                        receipt.victimUuid())
                && bitesTheDustMarkerEpoch.equals(
                        receipt.markerEpoch())
                && bitesTheDustMarkerDimension.equals(
                        receipt.markerDimension())
                && bitesTheDustMarkerTime
                        == receipt.markerDayTime()
                && bitesTheDustMarkerGameTime
                        == receipt.markerGameTime();
    }

    public boolean setBitesTheDustTrigger(
            UUID ownerUuid, UUID victimUuid, int triggerTicks) {
        if (ownerUuid == null
                || victimUuid == null
                || triggerTicks < 0) {
            return clearBitesTheDustTrigger();
        }
        boolean changed = !ownerUuid.equals(
                        bitesTheDustTriggerOwnerUuid)
                || !victimUuid.equals(
                        bitesTheDustTriggerVictimUuid)
                || triggerTicks != bitesTheDustTriggerTicks;
        bitesTheDustTriggerOwnerUuid = ownerUuid;
        bitesTheDustTriggerVictimUuid = victimUuid;
        bitesTheDustTriggerTicks = triggerTicks;
        return changed;
    }

    public boolean clearBitesTheDustTrigger() {
        boolean changed = bitesTheDustTriggerOwnerUuid != null
                || bitesTheDustTriggerVictimUuid != null
                || bitesTheDustTriggerTicks != -1;
        bitesTheDustTriggerOwnerUuid = null;
        bitesTheDustTriggerVictimUuid = null;
        bitesTheDustTriggerTicks = -1;
        return changed;
    }

    /** What a logout or a dimension change drops; the primary bomb and Sheer Heart Attack stay. */
    public boolean clearSessionState() {
        return clearBlockBomb();
    }

    public boolean clearOperationalState() {
        boolean changed = clearPrimaryBomb();
        changed = clearSessionState() || changed;
        return clearSheerHeart() || changed;
    }

    public boolean clearAll() {
        return clearBitesTheDust()
                | clearOperationalState()
                | clearBitesTheDustTrigger();
    }

    public void applyClientSnapshot(
            @Nullable UUID newPrimaryTarget,
            @Nullable ResourceLocation newPrimaryDimension,
            @Nullable BlockPos newBlockBombPos,
            @Nullable ResourceLocation newBlockBombDimension,
            @Nullable UUID newSheerHeartUuid,
            @Nullable ResourceLocation newSheerHeartDimension,
            boolean newBitesTheDustMarked,
            @Nullable UUID newBitesTheDustVictim,
            @Nullable ResourceLocation newBitesTheDustVictimDimension,
            long newBitesTheDustMarkerTime,
            long newBitesTheDustMarkerGameTime,
            @Nullable ResourceLocation newBitesTheDustMarkerDimension,
            @Nullable UUID newBitesTheDustMarkerEpoch,
            int newLinkedTimeStopId,
            @Nullable UUID newBitesTheDustTriggerOwner,
            @Nullable UUID newBitesTheDustTriggerVictim,
            int newBitesTheDustTriggerTicks) {
        clearAll();
        if (newPrimaryTarget != null
                && newPrimaryDimension != null) {
            setPrimaryBomb(
                    newPrimaryTarget, newPrimaryDimension);
        }
        if (newBlockBombPos != null
                && newBlockBombDimension != null) {
            setBlockBomb(
                    newBlockBombPos, newBlockBombDimension);
        }
        if (newSheerHeartUuid != null
                && newSheerHeartDimension != null) {
            setSheerHeart(
                    newSheerHeartUuid, newSheerHeartDimension);
        }
        if (newBitesTheDustMarked
                && newBitesTheDustMarkerTime >= 0L
                && newBitesTheDustMarkerGameTime >= 0L
                && newBitesTheDustMarkerDimension != null
                && newBitesTheDustMarkerEpoch != null) {
            setBitesTheDustMarker(
                    newBitesTheDustMarkerTime,
                    newBitesTheDustMarkerGameTime,
                    newBitesTheDustMarkerDimension,
                    newBitesTheDustMarkerEpoch,
                    List.of());
            if (newBitesTheDustVictim != null
                    && newBitesTheDustVictimDimension != null) {
                selectBitesTheDustVictim(
                        newBitesTheDustVictim,
                        newBitesTheDustVictimDimension);
            }
            if (newLinkedTimeStopId >= 0) {
                linkTimeStop(newLinkedTimeStopId);
            }
        }
        if (newBitesTheDustTriggerOwner != null
                && newBitesTheDustTriggerVictim != null
                && newBitesTheDustTriggerTicks >= 0) {
            setBitesTheDustTrigger(
                    newBitesTheDustTriggerOwner,
                    newBitesTheDustTriggerVictim,
                    newBitesTheDustTriggerTicks);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        if (hasPrimaryBomb()) {
            tag.putUUID(PRIMARY_TARGET_TAG, primaryTargetUuid);
            tag.putString(
                    PRIMARY_DIMENSION_TAG,
                    primaryTargetDimension.toString());
        }
        if (hasBlockBomb()) {
            tag.putLong(BLOCK_TARGET_TAG, blockBombPos.asLong());
            tag.putString(
                    BLOCK_DIMENSION_TAG,
                    blockBombDimension.toString());
        }
        if (hasSheerHeart()) {
            tag.putUUID(SHEER_HEART_TAG, sheerHeartUuid);
            tag.putString(
                    SHEER_HEART_DIMENSION_TAG,
                    sheerHeartDimension.toString());
        }
        if (bitesTheDustAwakened) {
            tag.putBoolean(BTD_AWAKENED_TAG, true);
        }
        if (hasBitesTheDustMarker()) {
            tag.putBoolean(BTD_MARKED_TAG, true);
            tag.putLong(BTD_MARKER_TIME_TAG, bitesTheDustMarkerTime);
            tag.putLong(
                    BTD_MARKER_GAME_TIME_TAG,
                    bitesTheDustMarkerGameTime);
            tag.putString(
                    BTD_MARKER_DIMENSION_TAG,
                    bitesTheDustMarkerDimension.toString());
            tag.putUUID(
                    BTD_MARKER_EPOCH_TAG,
                    bitesTheDustMarkerEpoch);
            ListTag snapshots = new ListTag();
            for (BitesTheDustPlayerSnapshot snapshot :
                    bitesTheDustPlayerSnapshots()) {
                snapshots.add(snapshot.serialize());
            }
            tag.put(BTD_PLAYER_SNAPSHOTS_TAG, snapshots);
            if (hasBitesTheDustVictim()) {
                tag.putBoolean(BTD_ARMED_TAG, true);
                tag.putUUID(
                        BTD_VICTIM_TAG, bitesTheDustVictimUuid);
                tag.putString(
                        BTD_VICTIM_DIMENSION_TAG,
                        bitesTheDustVictimDimension.toString());
            }
            if (linkedTimeStopId >= 0) {
                tag.putInt(BTD_TIME_STOP_TAG, linkedTimeStopId);
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clearAll();
        bitesTheDustAwakened = false;
        legacyAwakeningMigrationPending = false;
        consumedReceiptIds.clear();
        int version = tag.getInt(VERSION_TAG);
        if (version < 1 || version > SERIALIZATION_VERSION) {
            return;
        }
        bitesTheDustAwakened = version >= 4
                && tag.getBoolean(BTD_AWAKENED_TAG);
        legacyAwakeningMigrationPending = version < 4;

        ResourceLocation primaryDimension = parseLocation(
                tag.getString(PRIMARY_DIMENSION_TAG));
        if (tag.hasUUID(PRIMARY_TARGET_TAG)
                && primaryDimension != null) {
            setPrimaryBomb(
                    tag.getUUID(PRIMARY_TARGET_TAG),
                    primaryDimension);
        }

        if (version >= 2) {
            ResourceLocation blockDimension = parseLocation(
                    tag.getString(BLOCK_DIMENSION_TAG));
            if (tag.contains(BLOCK_TARGET_TAG)
                    && blockDimension != null) {
                setBlockBomb(
                        BlockPos.of(tag.getLong(BLOCK_TARGET_TAG)),
                        blockDimension);
            }
            ResourceLocation sheerHeartDimension = parseLocation(
                    tag.getString(SHEER_HEART_DIMENSION_TAG));
            if (tag.hasUUID(SHEER_HEART_TAG)
                    && sheerHeartDimension != null) {
                setSheerHeart(
                        tag.getUUID(SHEER_HEART_TAG),
                        sheerHeartDimension);
            }
        }

        ResourceLocation markerDimension = parseLocation(
                tag.getString(BTD_MARKER_DIMENSION_TAG));
        boolean marked = version >= 3
                ? tag.getBoolean(BTD_MARKED_TAG)
                : tag.getBoolean(BTD_ARMED_TAG);
        long markerTime = tag.getLong(BTD_MARKER_TIME_TAG);
        long markerGameTime = version >= 3
                ? tag.getLong(BTD_MARKER_GAME_TIME_TAG)
                : markerTime;
        UUID markerEpoch = version >= 4
                && tag.hasUUID(BTD_MARKER_EPOCH_TAG)
                        ? tag.getUUID(BTD_MARKER_EPOCH_TAG)
                        : legacyMarkerEpoch(
                                markerTime,
                                markerGameTime,
                                markerDimension);
        if (marked
                && markerDimension != null
                && markerTime >= 0L
                && markerGameTime >= 0L
                && markerEpoch != null) {
            List<BitesTheDustPlayerSnapshot> snapshots =
                    new ArrayList<>();
            if (version >= 3) {
                ListTag list = tag.getList(
                        BTD_PLAYER_SNAPSHOTS_TAG,
                        Tag.TAG_COMPOUND);
                for (int index = 0;
                        index < list.size()
                                && snapshots.size()
                                < MAX_BTD_PLAYER_SNAPSHOTS;
                        index++) {
                    BitesTheDustPlayerSnapshot snapshot =
                            BitesTheDustPlayerSnapshot.deserialize(
                                    list.getCompound(index));
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                }
            }
            setBitesTheDustMarker(
                    markerTime,
                    markerGameTime,
                    markerDimension,
                    markerEpoch,
                    snapshots);
            ResourceLocation victimDimension = version >= 3
                    ? parseLocation(tag.getString(
                            BTD_VICTIM_DIMENSION_TAG))
                    : markerDimension;
            if (tag.getBoolean(BTD_ARMED_TAG)
                    && tag.hasUUID(BTD_VICTIM_TAG)
                    && victimDimension != null) {
                selectBitesTheDustVictim(
                        tag.getUUID(BTD_VICTIM_TAG),
                        victimDimension);
            }
            if (tag.contains(BTD_TIME_STOP_TAG)
                    && tag.getInt(BTD_TIME_STOP_TAG) >= 0) {
                linkTimeStop(tag.getInt(BTD_TIME_STOP_TAG));
            }
        }
    }

    public record BitesTheDustPlayerSnapshot(
            UUID playerUuid,
            ResourceLocation dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot) {
        private static final String UUID_TAG = "Player";
        private static final String DIMENSION_TAG = "Dimension";
        private static final String X_TAG = "X";
        private static final String Y_TAG = "Y";
        private static final String Z_TAG = "Z";
        private static final String Y_ROT_TAG = "YRot";
        private static final String X_ROT_TAG = "XRot";

        public static BitesTheDustPlayerSnapshot capture(
                Player player) {
            return new BitesTheDustPlayerSnapshot(
                    player.getUUID(),
                    player.level().dimension().location(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot());
        }

        public boolean valid() {
            return playerUuid != null
                    && dimension != null
                    && Double.isFinite(x)
                    && Double.isFinite(y)
                    && Double.isFinite(z)
                    && Float.isFinite(yRot)
                    && Float.isFinite(xRot);
        }

        private CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(UUID_TAG, playerUuid);
            tag.putString(DIMENSION_TAG, dimension.toString());
            tag.putDouble(X_TAG, x);
            tag.putDouble(Y_TAG, y);
            tag.putDouble(Z_TAG, z);
            tag.putFloat(Y_ROT_TAG, yRot);
            tag.putFloat(X_ROT_TAG, xRot);
            return tag;
        }

        @Nullable
        private static BitesTheDustPlayerSnapshot deserialize(
                CompoundTag tag) {
            ResourceLocation dimension = parseLocation(
                    tag.getString(DIMENSION_TAG));
            if (!tag.hasUUID(UUID_TAG) || dimension == null) {
                return null;
            }
            BitesTheDustPlayerSnapshot snapshot =
                    new BitesTheDustPlayerSnapshot(
                            tag.getUUID(UUID_TAG),
                            dimension,
                            tag.getDouble(X_TAG),
                            tag.getDouble(Y_TAG),
                            tag.getDouble(Z_TAG),
                            tag.getFloat(Y_ROT_TAG),
                            tag.getFloat(X_ROT_TAG));
            return snapshot.valid() ? snapshot : null;
        }
    }

    @Nullable
    private static ResourceLocation parseLocation(String value) {
        return value == null || value.isBlank()
                ? null
                : ResourceLocation.tryParse(value);
    }

    @Nullable
    private static UUID legacyMarkerEpoch(
            long markerTime,
            long markerGameTime,
            @Nullable ResourceLocation markerDimension) {
        if (markerTime < 0L
                || markerGameTime < 0L
                || markerDimension == null) {
            return null;
        }
        String canonical = markerDimension + "|"
                + markerTime + "|" + markerGameTime;
        return UUID.nameUUIDFromBytes(
                canonical.getBytes(StandardCharsets.UTF_8));
    }
}
