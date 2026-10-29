package rotp.addons.killerqueenlion.btd;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import rotp.core.api.rollback.AtomicRollbackJournal.Status;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Ephemeral server-process proof for one automatic Bites the Dust commit.
 * Signing authority lives exclusively in {@link BitesTheDustService}; this
 * value only carries and canonicalizes signed data.
 */
public final class BitesTheDustActivationReceipt {
    public static final int FORMAT_VERSION = 2;
    static final int SEAL_BYTES = 32;

    private final UUID receiptId;
    private final UUID authorityEpoch;
    private final UUID markerEpoch;
    private final UUID ownerUuid;
    private final UUID victimUuid;
    private final UUID attackerUuid;
    private final ResourceLocation markerDimension;
    private final ResourceLocation sourceDimension;
    private final long markerDayTime;
    private final long markerGameTime;
    private final long sourceDayTimeBefore;
    private final long sourceDayTimeAfter;
    private final int triggerStartTick;
    private final int commitTick;
    private final int snapshotCount;
    private final int teleportedPlayers;
    private final float ownerHealthBefore;
    private final float ownerHealthAfter;
    private final float ownerMaxHealth;
    private final float attackerHealthBefore;
    private final float attackerHealthAfter;
    private final boolean attackerAliveAfter;
    private final boolean markerStillPresent;
    private final boolean victimStillBound;
    private final String journalStatus;
    private final int journalEntryCount;
    private final int journalAttemptedEntries;
    private final byte[] seal;

    BitesTheDustActivationReceipt(
            UUID receiptId,
            UUID authorityEpoch,
            UUID markerEpoch,
            UUID ownerUuid,
            UUID victimUuid,
            UUID attackerUuid,
            ResourceLocation markerDimension,
            ResourceLocation sourceDimension,
            long markerDayTime,
            long markerGameTime,
            long sourceDayTimeBefore,
            long sourceDayTimeAfter,
            int triggerStartTick,
            int commitTick,
            int snapshotCount,
            int teleportedPlayers,
            float ownerHealthBefore,
            float ownerHealthAfter,
            float ownerMaxHealth,
            float attackerHealthBefore,
            float attackerHealthAfter,
            boolean attackerAliveAfter,
            boolean markerStillPresent,
            boolean victimStillBound,
            String journalStatus,
            int journalEntryCount,
            int journalAttemptedEntries,
            byte[] seal) {
        this.receiptId = receiptId;
        this.authorityEpoch = authorityEpoch;
        this.markerEpoch = markerEpoch;
        this.ownerUuid = ownerUuid;
        this.victimUuid = victimUuid;
        this.attackerUuid = attackerUuid;
        this.markerDimension = markerDimension;
        this.sourceDimension = sourceDimension;
        this.markerDayTime = markerDayTime;
        this.markerGameTime = markerGameTime;
        this.sourceDayTimeBefore = sourceDayTimeBefore;
        this.sourceDayTimeAfter = sourceDayTimeAfter;
        this.triggerStartTick = triggerStartTick;
        this.commitTick = commitTick;
        this.snapshotCount = snapshotCount;
        this.teleportedPlayers = teleportedPlayers;
        this.ownerHealthBefore = ownerHealthBefore;
        this.ownerHealthAfter = ownerHealthAfter;
        this.ownerMaxHealth = ownerMaxHealth;
        this.attackerHealthBefore = attackerHealthBefore;
        this.attackerHealthAfter = attackerHealthAfter;
        this.attackerAliveAfter = attackerAliveAfter;
        this.markerStillPresent = markerStillPresent;
        this.victimStillBound = victimStillBound;
        this.journalStatus = journalStatus;
        this.journalEntryCount = journalEntryCount;
        this.journalAttemptedEntries = journalAttemptedEntries;
        this.seal = seal != null ? seal.clone() : new byte[0];
    }

    /**
     * Checks integrity for the current process. Runtime acceptance must use
     * the service's state-bound exactly-once consume operation as well.
     */
    public boolean verified() {
        return BitesTheDustService.receiptIntegrityValid(this);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", FORMAT_VERSION);
        tag.putUUID("Receipt", receiptId);
        tag.putUUID("AuthorityEpoch", authorityEpoch);
        tag.putUUID("MarkerEpoch", markerEpoch);
        tag.putUUID("Owner", ownerUuid);
        tag.putUUID("Victim", victimUuid);
        tag.putUUID("Attacker", attackerUuid);
        tag.putString("MarkerDimension", markerDimension.toString());
        tag.putString("SourceDimension", sourceDimension.toString());
        tag.putLong("MarkerDayTime", markerDayTime);
        tag.putLong("MarkerGameTime", markerGameTime);
        tag.putLong("SourceDayTimeBefore", sourceDayTimeBefore);
        tag.putLong("SourceDayTimeAfter", sourceDayTimeAfter);
        tag.putInt("TriggerStartTick", triggerStartTick);
        tag.putInt("CommitTick", commitTick);
        tag.putInt("SnapshotCount", snapshotCount);
        tag.putInt("TeleportedPlayers", teleportedPlayers);
        tag.putFloat("OwnerHealthBefore", ownerHealthBefore);
        tag.putFloat("OwnerHealthAfter", ownerHealthAfter);
        tag.putFloat("OwnerMaxHealth", ownerMaxHealth);
        tag.putFloat("AttackerHealthBefore", attackerHealthBefore);
        tag.putFloat("AttackerHealthAfter", attackerHealthAfter);
        tag.putBoolean("AttackerAliveAfter", attackerAliveAfter);
        tag.putBoolean("MarkerStillPresent", markerStillPresent);
        tag.putBoolean("VictimStillBound", victimStillBound);
        tag.putString("JournalStatus", journalStatus);
        tag.putInt("JournalEntryCount", journalEntryCount);
        tag.putInt(
                "JournalAttemptedEntries", journalAttemptedEntries);
        tag.putByteArray("Seal", seal);
        return tag;
    }

    static Optional<BitesTheDustActivationReceipt> parseAuthenticated(
            CompoundTag tag) {
        BitesTheDustActivationReceipt receipt = fromTag(tag);
        return receipt != null && receipt.verified()
                ? Optional.of(receipt)
                : Optional.empty();
    }

    private static BitesTheDustActivationReceipt fromTag(
            CompoundTag tag) {
        if (tag == null
                || tag.getInt("Format") != FORMAT_VERSION
                || !tag.hasUUID("Receipt")
                || !tag.hasUUID("AuthorityEpoch")
                || !tag.hasUUID("MarkerEpoch")
                || !tag.hasUUID("Owner")
                || !tag.hasUUID("Victim")
                || !tag.hasUUID("Attacker")
                || !tag.contains("Seal", Tag.TAG_BYTE_ARRAY)) {
            return null;
        }
        ResourceLocation markerDimension = ResourceLocation.tryParse(
                tag.getString("MarkerDimension"));
        ResourceLocation sourceDimension = ResourceLocation.tryParse(
                tag.getString("SourceDimension"));
        byte[] seal = tag.getByteArray("Seal");
        if (markerDimension == null
                || sourceDimension == null
                || seal.length != SEAL_BYTES) {
            return null;
        }
        return new BitesTheDustActivationReceipt(
                tag.getUUID("Receipt"),
                tag.getUUID("AuthorityEpoch"),
                tag.getUUID("MarkerEpoch"),
                tag.getUUID("Owner"),
                tag.getUUID("Victim"),
                tag.getUUID("Attacker"),
                markerDimension,
                sourceDimension,
                tag.getLong("MarkerDayTime"),
                tag.getLong("MarkerGameTime"),
                tag.getLong("SourceDayTimeBefore"),
                tag.getLong("SourceDayTimeAfter"),
                tag.getInt("TriggerStartTick"),
                tag.getInt("CommitTick"),
                tag.getInt("SnapshotCount"),
                tag.getInt("TeleportedPlayers"),
                tag.getFloat("OwnerHealthBefore"),
                tag.getFloat("OwnerHealthAfter"),
                tag.getFloat("OwnerMaxHealth"),
                tag.getFloat("AttackerHealthBefore"),
                tag.getFloat("AttackerHealthAfter"),
                tag.getBoolean("AttackerAliveAfter"),
                tag.getBoolean("MarkerStillPresent"),
                tag.getBoolean("VictimStillBound"),
                tag.getString("JournalStatus"),
                tag.getInt("JournalEntryCount"),
                tag.getInt("JournalAttemptedEntries"),
                seal);
    }

    public UUID receiptId() {
        return receiptId;
    }

    public UUID authorityEpoch() {
        return authorityEpoch;
    }

    public UUID markerEpoch() {
        return markerEpoch;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public UUID victimUuid() {
        return victimUuid;
    }

    public UUID attackerUuid() {
        return attackerUuid;
    }

    public ResourceLocation markerDimension() {
        return markerDimension;
    }

    public ResourceLocation sourceDimension() {
        return sourceDimension;
    }

    public long markerDayTime() {
        return markerDayTime;
    }

    public long markerGameTime() {
        return markerGameTime;
    }

    public long sourceDayTimeBefore() {
        return sourceDayTimeBefore;
    }

    public long sourceDayTimeAfter() {
        return sourceDayTimeAfter;
    }

    public int triggerStartTick() {
        return triggerStartTick;
    }

    public int commitTick() {
        return commitTick;
    }

    public int snapshotCount() {
        return snapshotCount;
    }

    public int teleportedPlayers() {
        return teleportedPlayers;
    }

    public float ownerHealthBefore() {
        return ownerHealthBefore;
    }

    public float ownerHealthAfter() {
        return ownerHealthAfter;
    }

    public float ownerMaxHealth() {
        return ownerMaxHealth;
    }

    public float attackerHealthBefore() {
        return attackerHealthBefore;
    }

    public float attackerHealthAfter() {
        return attackerHealthAfter;
    }

    public boolean attackerAliveAfter() {
        return attackerAliveAfter;
    }

    public boolean markerStillPresent() {
        return markerStillPresent;
    }

    public boolean victimStillBound() {
        return victimStillBound;
    }

    public String journalStatus() {
        return journalStatus;
    }

    public int journalEntryCount() {
        return journalEntryCount;
    }

    public int journalAttemptedEntries() {
        return journalAttemptedEntries;
    }

    boolean structurallyValid() {
        return receiptId != null
                && authorityEpoch != null
                && markerEpoch != null
                && ownerUuid != null
                && victimUuid != null
                && attackerUuid != null
                && !ownerUuid.equals(victimUuid)
                && !ownerUuid.equals(attackerUuid)
                && !victimUuid.equals(attackerUuid)
                && markerDimension != null
                && sourceDimension != null
                && markerDayTime >= 0L
                && markerGameTime >= 0L
                && sourceDayTimeBefore >= 0L
                && sourceDayTimeAfter == markerDayTime
                && commitTick - triggerStartTick
                        >= BitesTheDustService.TRIGGER_DELAY_TICKS
                && snapshotCount >= 1
                && snapshotCount <= 256
                && teleportedPlayers >= 0
                && teleportedPlayers <= snapshotCount
                && finiteNonNegative(ownerHealthBefore)
                && finitePositive(ownerMaxHealth)
                && ownerHealthBefore <= ownerMaxHealth
                && Float.compare(ownerHealthAfter, ownerMaxHealth) == 0
                && finitePositive(attackerHealthBefore)
                && finiteNonPositive(attackerHealthAfter)
                && !attackerAliveAfter
                && markerStillPresent
                && victimStillBound
                && Status.APPLIED.name().equals(journalStatus)
                && journalEntryCount == teleportedPlayers + 2
                && journalAttemptedEntries == journalEntryCount
                && seal.length == SEAL_BYTES;
    }

    BitesTheDustActivationReceipt withSeal(byte[] newSeal) {
        return new BitesTheDustActivationReceipt(
                receiptId,
                authorityEpoch,
                markerEpoch,
                ownerUuid,
                victimUuid,
                attackerUuid,
                markerDimension,
                sourceDimension,
                markerDayTime,
                markerGameTime,
                sourceDayTimeBefore,
                sourceDayTimeAfter,
                triggerStartTick,
                commitTick,
                snapshotCount,
                teleportedPlayers,
                ownerHealthBefore,
                ownerHealthAfter,
                ownerMaxHealth,
                attackerHealthBefore,
                attackerHealthAfter,
                attackerAliveAfter,
                markerStillPresent,
                victimStillBound,
                journalStatus,
                journalEntryCount,
                journalAttemptedEntries,
                newSeal);
    }

    void updateCanonicalDigest(MessageDigest digest) {
        update(digest, FORMAT_VERSION);
        update(digest, receiptId);
        update(digest, authorityEpoch);
        update(digest, markerEpoch);
        update(digest, ownerUuid);
        update(digest, victimUuid);
        update(digest, attackerUuid);
        update(digest, markerDimension.toString());
        update(digest, sourceDimension.toString());
        update(digest, markerDayTime);
        update(digest, markerGameTime);
        update(digest, sourceDayTimeBefore);
        update(digest, sourceDayTimeAfter);
        update(digest, triggerStartTick);
        update(digest, commitTick);
        update(digest, snapshotCount);
        update(digest, teleportedPlayers);
        update(digest, Float.floatToIntBits(ownerHealthBefore));
        update(digest, Float.floatToIntBits(ownerHealthAfter));
        update(digest, Float.floatToIntBits(ownerMaxHealth));
        update(digest, Float.floatToIntBits(attackerHealthBefore));
        update(digest, Float.floatToIntBits(attackerHealthAfter));
        update(digest, attackerAliveAfter);
        update(digest, markerStillPresent);
        update(digest, victimStillBound);
        update(digest, journalStatus);
        update(digest, journalEntryCount);
        update(digest, journalAttemptedEntries);
    }

    byte[] seal() {
        return seal.clone();
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > 0.0F;
    }

    private static boolean finiteNonNegative(float value) {
        return Float.isFinite(value) && value >= 0.0F;
    }

    private static boolean finiteNonPositive(float value) {
        return Float.isFinite(value) && value <= 0.0F;
    }

    private static void update(MessageDigest digest, UUID value) {
        update(digest, value.getMostSignificantBits());
        update(digest, value.getLeastSignificantBits());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(value)
                .array());
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES)
                .putLong(value)
                .array());
    }
}
