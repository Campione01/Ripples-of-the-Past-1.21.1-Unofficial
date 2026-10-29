package rotp.addons.madeinheaven.state;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;

public final class TimeAccelerationState {
    @Nullable private UUID ownerUuid;
    private boolean active;
    private int phase;
    private int tickCounter;
    // 1.16 kept one count per time stopper across accelerations and only
    // reset it when it cut that time stop short.
    private final Map<LivingEntity, Integer> timeStopTicks =
            new WeakHashMap<>();

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    public boolean active() {
        return active;
    }

    public int phase() {
        return phase;
    }

    public int tickCounter() {
        return tickCounter;
    }

    public boolean isOwner(UUID candidate) {
        return active
                && ownerUuid != null
                && ownerUuid.equals(candidate);
    }

    public void begin(UUID owner) {
        ownerUuid = owner;
        active = true;
        phase = 0;
        tickCounter = 0;
    }

    public void advance(int nextPhase) {
        phase = nextPhase;
        tickCounter++;
    }

    public int countTimeStopTick(LivingEntity entity) {
        int ticks = timeStopTicks.getOrDefault(entity, 0) + 1;
        timeStopTicks.put(entity, ticks);
        return ticks;
    }

    public void resetTimeStopTicks(LivingEntity entity) {
        timeStopTicks.put(entity, 0);
    }

    public void clear() {
        ownerUuid = null;
        active = false;
        phase = 0;
        tickCounter = 0;
    }

    public void applyClientSnapshot(
            @Nullable UUID owner,
            boolean nextActive,
            int nextPhase) {
        ownerUuid = owner;
        active = nextActive;
        phase = nextPhase;
        tickCounter = 0;
    }
}
