package rotp.addons.thelock.state;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.Tags;

public final class LockRules {
    public static final float MAX_GUILT = 1000.0F;
    public static final float PASSIVE_GUILT_PER_TICK = 0.1F;
    public static final float OWNER_ATTACK_GUILT = 100.0F;
    public static final float OTHER_ATTACK_GUILT = 50.0F;
    public static final float CHAT_GUILT = 100.0F;
    public static final double OWNER_RANGE = 50.0D;
    public static final double CHAT_RANGE = 16.0D;
    public static final int EFFECT_REFRESH_INTERVAL = 30;
    public static final int EFFECT_DURATION = 40;

    private LockRules() {}

    public static float clampGuilt(float guilt) {
        return Float.isFinite(guilt)
                ? Math.clamp(guilt, 0.0F, MAX_GUILT)
                : 0.0F;
    }

    public static int amplifier(float guilt) {
        float normalized = clampGuilt(guilt);
        if (normalized > 900.0F) {
            return 4;
        }
        if (normalized > 650.0F) {
            return 3;
        }
        if (normalized > 400.0F) {
            return 2;
        }
        if (normalized > 200.0F) {
            return 1;
        }
        return 0;
    }

    public static float attackGuilt(
            boolean victimIsLockOwner) {
        return victimIsLockOwner
                ? OWNER_ATTACK_GUILT
                : OTHER_ATTACK_GUILT;
    }

    public static boolean isValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Rarity rarity = stack.getRarity();
        return rarity == Rarity.UNCOMMON
                || rarity == Rarity.RARE
                || rarity == Rarity.EPIC
                || stack.is(Tags.Items.INGOTS)
                || stack.is(Tags.Items.GEMS)
                || stack.is(Tags.Items.STORAGE_BLOCKS);
    }
}
