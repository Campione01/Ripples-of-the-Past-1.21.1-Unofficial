package rotp.addons.d4c.entity;

import net.minecraft.nbt.CompoundTag;

public final class D4CMinionLifetime {
    public static final int MAX_LIFE_TICKS = 6000;
    private int ticks;

    public boolean tickAndShouldExpire() {
        if (ticks <= MAX_LIFE_TICKS) {
            ticks++;
        }
        return ticks > MAX_LIFE_TICKS;
    }

    public void save(CompoundTag tag) {
        tag.putInt("LifeTime", ticks);
    }

    public void load(CompoundTag tag) {
        ticks = Math.max(0, Math.min(
                tag.getInt("LifeTime"), MAX_LIFE_TICKS + 1));
    }
}
