package rotp.addons.d4c.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import rotp.addons.d4c.client.MinionAiSelectorLayout;

/** 1.16 minion AI modes (ag.bh/bk), stored per owner. */
public final class D4CMinionAiMode {
    public static final int RANGED = 0;
    public static final int MELEE = 1;
    public static final int WAIT = 2;
    public static final int FOLLOW = 3;
    // 1.16 "stand attack": bare-handed melee
    public static final int UNARMED = 4;
    public static final int SMART = 5;
    public static final int COUNT = 6;
    // 1.16 ag.aa(UUID): getOrDefault(uuid, 5)
    public static final int DEFAULT = SMART;
    public static final String OWNER_KEY = "rotp_d4c.minion_ai_mode";

    public static final int TACTIC_RANGED = 0;
    public static final int TACTIC_MELEE = 1;

    public enum Weapon { KEEP, COLT, SWORD, EMPTY }

    private D4CMinionAiMode() {}

    public static int sanitize(int mode) {
        return mode >= 0 && mode < COUNT ? mode : DEFAULT;
    }

    public static int next(int mode) {
        return (sanitize(mode) + 1) % COUNT;
    }

    public static int stored(CompoundTag ownerData) {
        return ownerData.contains(OWNER_KEY, Tag.TAG_INT)
                ? sanitize(ownerData.getInt(OWNER_KEY))
                : DEFAULT;
    }

    public static void store(CompoundTag ownerData, int mode) {
        ownerData.putInt(OWNER_KEY, sanitize(mode));
    }

    // the name the mode screen shows, so the action-bar line cannot name it differently
    public static String langKey(int mode) {
        return MinionAiSelectorLayout.MODE_KEYS[sanitize(mode)];
    }

    // wait and follow never pick a target
    public static boolean fights(int mode) {
        int m = sanitize(mode);
        return m != WAIT && m != FOLLOW;
    }

    public static boolean followsOwner(int mode) {
        return sanitize(mode) != WAIT;
    }

    // 1.16 ag.bk
    public static Weapon weapon(int mode) {
        return switch (sanitize(mode)) {
            case RANGED, SMART -> Weapon.COLT;
            case MELEE -> Weapon.SWORD;
            case UNARMED -> Weapon.EMPTY;
            default -> Weapon.KEEP;
        };
    }

    // 1.16 ag.ad.aa(double): Colt from 8 blocks, sword within 2.5
    public static int smartTactic(int current, double distance) {
        if (distance <= 2.5D) {
            return TACTIC_MELEE;
        }
        if (distance >= 8.0D) {
            return TACTIC_RANGED;
        }
        if (current == TACTIC_MELEE && distance > 3.5D) {
            return TACTIC_RANGED;
        }
        if (current == TACTIC_RANGED && distance < 7.0D) {
            return TACTIC_MELEE;
        }
        return current;
    }
}
