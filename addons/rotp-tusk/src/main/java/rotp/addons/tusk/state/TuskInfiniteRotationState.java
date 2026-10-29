package rotp.addons.tusk.state;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class TuskInfiniteRotationState
        implements INBTSerializable<CompoundTag> {
    private boolean active;
    private Vec3 anchor = Vec3.ZERO;
    @Nullable private UUID sourceId;
    @Nullable private ResourceLocation sourceDimension;
    private final TuskInfiniteRotationProtection protection =
            new TuskInfiniteRotationProtection();

    public boolean isActive() {
        return active;
    }

    public Vec3 anchor() {
        return anchor;
    }

    public void initialize(
            LivingEntity target, @Nullable Entity source) {
        if (!active) {
            active = true;
            anchor = target.position();
            sourceId = source != null ? source.getUUID() : null;
            sourceDimension = source != null
                    ? source.level().dimension().location()
                    : null;
        }
        normalizeLegacyControls(target);
        if (target instanceof Player player) {
            protection.capturePlayer(
                    player.getAbilities().invulnerable,
                    currentGameType(player));
        } else {
            protection.captureEntity(target.isInvulnerable());
        }
    }

    public Optional<Entity> resolveSource(ServerLevel level) {
        if (sourceId == null
                || sourceDimension == null
                || !sourceDimension.equals(
                        level.dimension().location())) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.getEntity(sourceId));
    }

    public boolean clear(LivingEntity target) {
        if (!active) {
            return false;
        }
        normalizeLegacyControls(target);
        if (target instanceof Player player) {
            boolean restored = protection.releasePlayer(
                    player.getAbilities().invulnerable,
                    currentGameType(player));
            if (player.getAbilities().invulnerable != restored) {
                player.getAbilities().invulnerable = restored;
                player.onUpdateAbilities();
            }
        } else {
            boolean restored = protection.releaseEntity(target.isInvulnerable());
            if (target.isInvulnerable() != restored) {
                target.setInvulnerable(restored);
            }
        }
        resetFields();
        return true;
    }

    private void resetFields() {
        active = false;
        anchor = Vec3.ZERO;
        sourceId = null;
        sourceDimension = null;
        protection.reset();
    }

    private void normalizeLegacyControls(LivingEntity target) {
        if (target instanceof Player player) {
            boolean restored = protection.migratePlayerFlight(
                    player.getAbilities().mayfly, currentGameType(player));
            if (player.getAbilities().mayfly != restored) {
                player.getAbilities().mayfly = restored;
                player.onUpdateAbilities();
            }
        } else if (target instanceof Mob mob) {
            boolean restored = protection.migrateMobAi(mob.isNoAi());
            if (mob.isNoAi() != restored) {
                mob.setNoAi(restored);
            }
        }
        protection.clearLegacyControls();
    }

    private static GameType currentGameType(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer();
        }
        return player.isSpectator() ? GameType.SPECTATOR
                : player.isCreative() ? GameType.CREATIVE : GameType.SURVIVAL;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Active", active);
        if (!active) {
            return tag;
        }
        tag.putDouble("AnchorX", anchor.x);
        tag.putDouble("AnchorY", anchor.y);
        tag.putDouble("AnchorZ", anchor.z);
        if (sourceId != null) {
            tag.putUUID("SourceId", sourceId);
        }
        if (sourceDimension != null) {
            tag.putString(
                    "SourceDimension", sourceDimension.toString());
        }
        protection.save(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        resetFields();
        active = tag.getBoolean("Active");
        if (!active) {
            return;
        }
        anchor = new Vec3(
                tag.getDouble("AnchorX"),
                tag.getDouble("AnchorY"),
                tag.getDouble("AnchorZ"));
        sourceId = tag.hasUUID("SourceId")
                ? tag.getUUID("SourceId")
                : null;
        if (tag.contains("SourceDimension")) {
            sourceDimension = ResourceLocation.tryParse(
                    tag.getString("SourceDimension"));
        }
        protection.load(tag);
    }
}
