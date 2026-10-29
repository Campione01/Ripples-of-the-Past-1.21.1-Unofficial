package rotp.addons.killerqueenlion.btd;

import javax.annotation.Nullable;

import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class BitesTheDustAwakening {
    private BitesTheDustAwakening() {}

    public static boolean unlockFromStandArrowHit(
            ServerPlayer player, @Nullable Entity directEntity) {
        return directEntity instanceof StandArrowEntity
                && unlock(player);
    }

    public static boolean unlockFromCrouchingArrowUse(
            ServerPlayer player, ItemStack itemStack) {
        return player != null
                && player.isShiftKeyDown()
                && itemStack != null
                && (itemStack.is(ModItems.STAND_ARROW)
                        || itemStack.is(
                                ModItems.STAND_ARROW_BEETLE))
                && unlock(player);
    }

    public static boolean isUnlocked(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        PowerData data = power != null
                ? power.getCurTypeData()
                : null;
        return KillerQueenLionAddon.isKillerQueenUser(player)
                && (KillerQueenLionAddon.state(player)
                        .bitesTheDustAwakened()
                        || StandPower.playerSkipsActionTraining(player))
                && data != null
                && data.isSkillUnlocked(
                        KillerQueenLionAddon.TIME_MARK_NAME);
    }

    /** Preserve the donor's progression bypass; ordinary survival still needs the Arrow. */
    public static boolean enforceStandArrowGate(
            ServerPlayer player) {
        if (player == null
                || !KillerQueenLionAddon.isKillerQueenUser(player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        PowerData data = power != null
                ? power.getCurTypeData()
                : null;
        if (data == null) {
            return false;
        }
        if (StandPower.playerSkipsActionTraining(player)) {
            return unlock(player);
        }
        KillerQueenState state = KillerQueenLionAddon.state(player);
        boolean unlocked = data.isSkillUnlocked(
                KillerQueenLionAddon.TIME_MARK_NAME);
        if (state.legacyAwakeningMigrationPending()) {
            state.completeLegacyAwakeningMigration(unlocked);
            return false;
        }
        if (!state.bitesTheDustAwakened()
                && unlocked
                && data._setSkillUnlocked(
                        KillerQueenLionAddon.TIME_MARK_NAME,
                        false,
                        true)) {
            data.syncOnUpdate(player);
            return true;
        }
        return false;
    }

    private static boolean unlock(ServerPlayer player) {
        if (player == null
                || player.level().isClientSide()
                || !KillerQueenLionAddon.isKillerQueenUser(player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        PowerData data = power != null
                ? power.getCurTypeData()
                : null;
        if (data == null) {
            return false;
        }
        KillerQueenState state = KillerQueenLionAddon.state(player);
        boolean provenanceChanged =
                state.setBitesTheDustAwakened(true);
        boolean skillChanged = false;
        if (!data.isSkillUnlocked(
                KillerQueenLionAddon.TIME_MARK_NAME)) {
            skillChanged = data._setSkillUnlocked(
                    KillerQueenLionAddon.TIME_MARK_NAME,
                    true,
                    true);
            if (!skillChanged) {
                state.setBitesTheDustAwakened(false);
                return false;
            }
        }
        if (skillChanged) {
            data.syncOnUpdate(player);
        }
        return provenanceChanged || skillChanged;
    }
}
