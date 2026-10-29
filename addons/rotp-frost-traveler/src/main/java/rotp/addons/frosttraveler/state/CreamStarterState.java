package rotp.addons.frosttraveler.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class CreamStarterState
        implements INBTSerializable<CompoundTag> {
    public static final int DEFAULT_AMMO = 300;
    public static final int MAX_AMMO = 600;

    private int ammo = DEFAULT_AMMO;
    private SprayMode mode = SprayMode.ATTACK;
    private String standSkin = "";

    public int ammo() {
        return ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = Math.max(0, Math.min(MAX_AMMO, ammo));
    }

    public boolean consumeAmmo(int amount) {
        if (amount <= 0 || ammo < amount) {
            return false;
        }
        ammo -= amount;
        return true;
    }

    public SprayMode mode() {
        return mode;
    }

    public void toggleMode() {
        mode = mode == SprayMode.ATTACK
                ? SprayMode.HEAL
                : SprayMode.ATTACK;
    }

    public String standSkin() {
        return standSkin;
    }

    public void setStandSkin(String standSkin) {
        this.standSkin = standSkin != null ? standSkin : "";
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Ammo", ammo);
        tag.putString("Mode", mode.name());
        tag.putString("StandSkin", standSkin);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        setAmmo(tag.contains("Ammo")
                ? tag.getInt("Ammo")
                : DEFAULT_AMMO);
        try {
            mode = SprayMode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            mode = SprayMode.ATTACK;
        }
        standSkin = tag.getString("StandSkin");
    }

    public enum SprayMode {
        ATTACK,
        HEAL
    }
}
