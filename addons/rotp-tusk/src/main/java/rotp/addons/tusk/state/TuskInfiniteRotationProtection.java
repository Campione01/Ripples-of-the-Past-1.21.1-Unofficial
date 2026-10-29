package rotp.addons.tusk.state;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

public final class TuskInfiniteRotationProtection {
    private boolean capturedPlayer;
    private boolean playerInvulnerable;
    @Nullable private GameType playerGameType;
    private boolean capturedEntity;
    private boolean entityInvulnerable;
    private boolean legacyPlayerFlight;
    private boolean legacyPlayerMayFly;
    private boolean legacyMobAi;
    private boolean legacyMobNoAi;

    public void capturePlayer(boolean invulnerable, GameType gameType) {
        if (!capturedPlayer) {
            capturedPlayer = true;
            playerInvulnerable = invulnerable;
            playerGameType = gameType;
        }
    }

    public void captureEntity(boolean invulnerable) {
        if (!capturedEntity) {
            capturedEntity = true;
            entityInvulnerable = invulnerable;
        }
    }

    public boolean releasePlayer(boolean currentInvulnerable, GameType gameType) {
        if (!capturedPlayer) {
            return currentInvulnerable;
        }
        boolean restored = playerGameType == gameType
                ? currentInvulnerable || playerInvulnerable
                : nativeAbilities(gameType).invulnerable;
        capturedPlayer = false;
        playerInvulnerable = false;
        playerGameType = null;
        return restored;
    }

    public boolean releaseEntity(boolean currentInvulnerable) {
        boolean restored = currentInvulnerable
                || capturedEntity && entityInvulnerable;
        capturedEntity = false;
        entityInvulnerable = false;
        return restored;
    }

    public boolean migratePlayerFlight(boolean currentMayFly, GameType gameType) {
        // Legacy data has no game mode, so its old true value cannot grant survival flight.
        boolean restored = currentMayFly
                || legacyPlayerFlight && nativeAbilities(gameType).mayfly;
        legacyPlayerFlight = false;
        legacyPlayerMayFly = false;
        return restored;
    }

    public boolean migrateMobAi(boolean currentNoAi) {
        boolean restored = currentNoAi || legacyMobAi && legacyMobNoAi;
        legacyMobAi = false;
        legacyMobNoAi = false;
        return restored;
    }

    public void clearLegacyControls() {
        legacyPlayerFlight = false;
        legacyPlayerMayFly = false;
        legacyMobAi = false;
        legacyMobNoAi = false;
    }

    public void reset() {
        capturedPlayer = false;
        playerInvulnerable = false;
        playerGameType = null;
        capturedEntity = false;
        entityInvulnerable = false;
        clearLegacyControls();
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("CapturedPlayerInvulnerability", capturedPlayer);
        tag.putBoolean("PlayerInvulnerable", playerInvulnerable);
        if (playerGameType != null) {
            tag.putInt("PlayerGameType", playerGameType.getId());
        } else {
            tag.remove("PlayerGameType");
        }
        tag.putBoolean("CapturedEntityInvulnerability", capturedEntity);
        tag.putBoolean("EntityInvulnerable", entityInvulnerable);
        tag.remove("CapturedPlayerFlight");
        tag.remove("PlayerMayFly");
        tag.remove("CapturedMobAi");
        tag.remove("MobNoAi");
        if (legacyPlayerFlight) {
            tag.putBoolean("CapturedPlayerFlight", true);
            tag.putBoolean("PlayerMayFly", legacyPlayerMayFly);
        }
        if (legacyMobAi) {
            tag.putBoolean("CapturedMobAi", true);
            tag.putBoolean("MobNoAi", legacyMobNoAi);
        }
    }

    public void load(CompoundTag tag) {
        reset();
        capturedPlayer = tag.getBoolean("CapturedPlayerInvulnerability");
        playerInvulnerable = tag.getBoolean("PlayerInvulnerable");
        if (tag.contains("PlayerGameType", Tag.TAG_INT)) {
            int gameTypeId = tag.getInt("PlayerGameType");
            for (GameType gameType : GameType.values()) {
                if (gameType.getId() == gameTypeId) {
                    playerGameType = gameType;
                    break;
                }
            }
        }
        capturedEntity = tag.getBoolean("CapturedEntityInvulnerability");
        entityInvulnerable = tag.getBoolean("EntityInvulnerable");
        legacyPlayerFlight = tag.getBoolean("CapturedPlayerFlight");
        legacyPlayerMayFly = tag.getBoolean("PlayerMayFly");
        legacyMobAi = tag.getBoolean("CapturedMobAi");
        legacyMobNoAi = tag.getBoolean("MobNoAi");
    }

    private static Abilities nativeAbilities(GameType gameType) {
        Abilities abilities = new Abilities();
        gameType.updatePlayerAbilities(abilities);
        return abilities;
    }
}
