package rotp.addons.echoes.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EchoesFreezeState
        implements INBTSerializable<CompoundTag> {
    public enum Kind {
        TARGET,
        HAND,
        FINISHER
    }

    private static final int SERIALIZATION_VERSION = 2;

    @Nullable
    private UUID owner;
    private Vec3 anchor = Vec3.ZERO;
    private boolean originalNoGravity;
    private int slowAmplifier;
    private Kind kind = Kind.TARGET;
    private long expiresAt;
    // A frozen player's mayfly from before the freeze, which takes it away;
    // null when a save from before this field did not record it.
    @Nullable
    private Boolean originalMayfly;

    public boolean active() {
        return owner != null;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public Vec3 anchor() {
        return anchor;
    }

    public int slowAmplifier() {
        return slowAmplifier;
    }

    public Kind kind() {
        return kind;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public void initializeLegacyDeadline(long gameTime) {
        expiresAt = EchoesFreezeTiming.initializeLegacyDeadline(
                expiresAt, gameTime,
                active() && (kind == Kind.TARGET || kind == Kind.HAND));
    }

    public void activate(
            LivingEntity target, UUID newOwner, int newSlowAmplifier) {
        activate(target, newOwner, newSlowAmplifier, Kind.TARGET, 0L);
    }

    public void activate(
            LivingEntity target,
            UUID newOwner,
            int newSlowAmplifier,
            Kind newKind,
            long newExpiresAt) {
        owner = newOwner;
        anchor = target.position();
        originalNoGravity = target.isNoGravity();
        slowAmplifier = newSlowAmplifier;
        kind = newKind;
        expiresAt = Math.max(0L, newExpiresAt);
        originalMayfly = target instanceof Player player
                ? player.getAbilities().mayfly
                : null;
    }

    // A frozen entity that sinks in water is held where it sank to.
    public void moveAnchor(Vec3 position) {
        if (active()) {
            anchor = position;
        }
    }

    public void clear(LivingEntity target) {
        target.setNoGravity(originalNoGravity);
        if (target instanceof Player player) {
            restoreFlight(player);
        }
        owner = null;
        anchor = Vec3.ZERO;
        originalNoGravity = false;
        slowAmplifier = 0;
        kind = Kind.TARGET;
        expiresAt = 0L;
        originalMayfly = null;
    }

    // 092 DisablerTagHandler.enable and HandFreezeHandler.unfreezeEntity set
    // mayfly on every released player, which let survival players fly; the
    // port gives back the mayfly the player had before the freeze, and
    // flying stays off as in 092.
    private void restoreFlight(Player player) {
        Abilities abilities = player.getAbilities();
        boolean mayfly = originalMayfly != null
                ? originalMayfly
                : player.isCreative() || player.isSpectator();
        if (abilities.mayfly != mayfly || abilities.flying) {
            abilities.mayfly = mayfly;
            abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SERIALIZATION_VERSION);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
            tag.putBoolean("OriginalNoGravity", originalNoGravity);
            tag.putInt("SlowAmplifier", slowAmplifier);
            tag.putString("Kind", kind.name());
            tag.putLong("ExpiresAt", expiresAt);
            if (originalMayfly != null) {
                tag.putBoolean("OriginalMayfly", originalMayfly);
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        owner = null;
        anchor = Vec3.ZERO;
        originalNoGravity = false;
        slowAmplifier = 0;
        kind = Kind.TARGET;
        expiresAt = 0L;
        originalMayfly = null;
        if (tag.getInt("Version") != SERIALIZATION_VERSION
                || !tag.hasUUID("Owner")) {
            return;
        }
        owner = tag.getUUID("Owner");
        anchor = new Vec3(
                tag.getDouble("AnchorX"),
                tag.getDouble("AnchorY"),
                tag.getDouble("AnchorZ"));
        originalNoGravity = tag.getBoolean("OriginalNoGravity");
        slowAmplifier = Math.max(
                0, Math.min(10, tag.getInt("SlowAmplifier")));
        try {
            kind = Kind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
            kind = Kind.TARGET;
        }
        expiresAt = Math.max(0L, tag.getLong("ExpiresAt"));
        if (tag.contains("OriginalMayfly")) {
            originalMayfly = tag.getBoolean("OriginalMayfly");
        }
    }
}
