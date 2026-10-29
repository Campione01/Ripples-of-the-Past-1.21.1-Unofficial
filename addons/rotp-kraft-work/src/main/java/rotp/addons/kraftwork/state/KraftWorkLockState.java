package rotp.addons.kraftwork.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.kraftwork.KraftWorkSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class KraftWorkLockState
        implements INBTSerializable<CompoundTag> {
    private static final String LOCKED = "Locked";
    private static final String OWNER = "Owner";
    private static final String DIMENSION = "Dimension";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String ORIGIN_Z = "OriginZ";
    private static final String MOTION_X = "MotionX";
    private static final String MOTION_Y = "MotionY";
    private static final String MOTION_Z = "MotionZ";
    private static final String ENERGY = "Energy";
    private static final String READY = "Ready";
    private static final String NO_GRAVITY = "NoGravity";
    private static final String NO_AI = "NoAi";
    private static final String EQUIPMENT_MASK = "EquipmentMask";
    private static final String FLIGHT_TICKS = "FlightTicks";
    private static final String RELEASED_ENERGY = "ReleasedEnergy";
    private static final String HOLD_DELAY = "HoldDelay";

    private boolean locked;
    @Nullable
    private UUID owner;
    private String dimension = "";
    private Vec3 origin = Vec3.ZERO;
    private Vec3 storedMotion = Vec3.ZERO;
    private int energy;
    private boolean ready;
    private boolean hadNoGravity;
    private boolean hadNoAi;
    private int equipmentMask;
    private int flightTicks;
    private int releasedEnergy;
    // 1.16's knives placed in flight (setTimeStopFlightTicks(1)) moved this
    // many ticks of their own before they held
    private int holdDelay;
    // 1.16 ProjectileUtilCap confirmation: a projectile the guard locked
    // costs the guard once more while it still faces it. Not saved.
    private boolean guardCharge;

    public boolean locked() {
        return locked;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String dimension() {
        return dimension;
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 storedMotion() {
        return storedMotion;
    }

    public int energy() {
        return energy;
    }

    public boolean ready() {
        return ready;
    }

    public boolean hadNoGravity() {
        return hadNoGravity;
    }

    public boolean hadNoAi() {
        return hadNoAi;
    }

    public int equipmentMask() {
        return equipmentMask;
    }

    public void addEquipmentMask(int mask) {
        equipmentMask |= mask;
    }

    public int flightTicks() {
        return flightTicks;
    }

    public int releasedEnergy() {
        return releasedEnergy;
    }

    public int holdDelay() {
        return holdDelay;
    }

    public void delayHold(int ticks) {
        holdDelay = Math.max(0, ticks);
    }

    // the hold begins where the flight left the projectile
    public void tickHoldDelay(Vec3 position, Vec3 motion) {
        if (holdDelay > 0 && --holdDelay == 0) {
            origin = position;
            storedMotion = motion;
        }
    }

    // stopped by an entity in its flight: it holds where it began
    public void endHoldDelayInPlace() {
        holdDelay = 0;
    }

    public void markGuardCharge() {
        guardCharge = true;
    }

    public boolean takeGuardCharge() {
        boolean pending = guardCharge;
        guardCharge = false;
        return pending;
    }

    public void lock(
            UUID owner,
            String dimension,
            Vec3 origin,
            Vec3 storedMotion,
            boolean hadNoGravity,
            boolean hadNoAi,
            int equipmentMask) {
        this.locked = true;
        this.owner = owner;
        this.dimension = dimension;
        this.origin = origin;
        this.storedMotion = storedMotion;
        this.hadNoGravity = hadNoGravity;
        this.hadNoAi = hadNoAi;
        this.equipmentMask = equipmentMask;
        this.holdDelay = 0;
        this.flightTicks = 0;
        this.releasedEnergy = 0;
        this.guardCharge = false;
    }

    public void addEnergy() {
        energy = Math.max(
                0, energy + KraftWorkSpec.ENERGY_PER_TICK);
        ready = true;
    }

    public void beginRelease(int flightTicks) {
        int accumulatedEnergy = energy;
        boolean originalNoGravity = hadNoGravity;
        boolean originalNoAi = hadNoAi;
        clearLockFields();
        this.flightTicks = Math.max(0, flightTicks);
        this.releasedEnergy = Math.max(0, accumulatedEnergy);
        this.hadNoGravity = originalNoGravity;
        this.hadNoAi = originalNoAi;
    }

    public void tickFlight() {
        if (flightTicks > 0) {
            flightTicks--;
        }
    }

    public void finishFlight() {
        flightTicks = 0;
        releasedEnergy = 0;
        hadNoGravity = false;
        hadNoAi = false;
    }

    public void clear() {
        clearLockFields();
        flightTicks = 0;
        releasedEnergy = 0;
        hadNoGravity = false;
        hadNoAi = false;
    }

    private void clearLockFields() {
        locked = false;
        owner = null;
        dimension = "";
        origin = Vec3.ZERO;
        storedMotion = Vec3.ZERO;
        energy = 0;
        ready = false;
        holdDelay = 0;
        equipmentMask = 0;
        guardCharge = false;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LOCKED, locked);
        if (owner != null) {
            tag.putUUID(OWNER, owner);
        }
        tag.putString(DIMENSION, dimension);
        tag.putDouble(ORIGIN_X, origin.x);
        tag.putDouble(ORIGIN_Y, origin.y);
        tag.putDouble(ORIGIN_Z, origin.z);
        tag.putDouble(MOTION_X, storedMotion.x);
        tag.putDouble(MOTION_Y, storedMotion.y);
        tag.putDouble(MOTION_Z, storedMotion.z);
        tag.putInt(ENERGY, energy);
        tag.putBoolean(READY, ready);
        tag.putBoolean(NO_GRAVITY, hadNoGravity);
        tag.putBoolean(NO_AI, hadNoAi);
        tag.putInt(EQUIPMENT_MASK, equipmentMask);
        tag.putInt(FLIGHT_TICKS, flightTicks);
        tag.putInt(RELEASED_ENERGY, releasedEnergy);
        tag.putInt(HOLD_DELAY, holdDelay);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        locked = tag.getBoolean(LOCKED);
        owner = tag.hasUUID(OWNER)
                ? tag.getUUID(OWNER)
                : null;
        dimension = tag.getString(DIMENSION);
        origin = new Vec3(
                tag.getDouble(ORIGIN_X),
                tag.getDouble(ORIGIN_Y),
                tag.getDouble(ORIGIN_Z));
        storedMotion = new Vec3(
                tag.getDouble(MOTION_X),
                tag.getDouble(MOTION_Y),
                tag.getDouble(MOTION_Z));
        energy = Math.max(0, tag.getInt(ENERGY));
        ready = tag.getBoolean(READY);
        hadNoGravity = tag.getBoolean(NO_GRAVITY);
        hadNoAi = tag.getBoolean(NO_AI);
        equipmentMask = Math.max(0, tag.getInt(EQUIPMENT_MASK));
        flightTicks = Math.max(0, tag.getInt(FLIGHT_TICKS));
        releasedEnergy =
                Math.max(0, tag.getInt(RELEASED_ENERGY));
        holdDelay = Math.max(0, tag.getInt(HOLD_DELAY));
        if (locked && owner == null) {
            clearLockFields();
        }
    }
}
