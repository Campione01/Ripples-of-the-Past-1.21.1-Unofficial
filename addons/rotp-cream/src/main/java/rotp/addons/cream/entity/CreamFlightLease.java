package rotp.addons.cream.entity;

import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

public final class CreamFlightLease {
    private GameType initialGameType;
    private boolean previousMayFly;
    private boolean previousFlying;
    private float previousFlyingSpeed;

    public boolean isLeased() {
        return initialGameType != null;
    }

    public void capture(GameType gameType, Abilities abilities) {
        if (isLeased()) {
            return;
        }
        initialGameType = gameType;
        previousMayFly = abilities.mayfly;
        previousFlying = abilities.flying;
        previousFlyingSpeed = abilities.getFlyingSpeed();
    }

    public boolean restore(GameType gameType, Abilities abilities) {
        if (!isLeased()) {
            return false;
        }
        if (gameType == initialGameType) {
            abilities.mayfly = previousMayFly;
            abilities.flying = previousFlying;
            abilities.setFlyingSpeed(previousFlyingSpeed);
        }
        else {
            // A different game mode owns flight permissions, not the old snapshot.
            Abilities currentMode = new Abilities();
            gameType.updatePlayerAbilities(currentMode);
            abilities.mayfly = currentMode.mayfly;
            abilities.flying = currentMode.flying;
            abilities.setFlyingSpeed(currentMode.getFlyingSpeed());
        }
        clear();
        return true;
    }

    public void clear() {
        initialGameType = null;
        previousMayFly = false;
        previousFlying = false;
        previousFlyingSpeed = 0.0F;
    }
}
