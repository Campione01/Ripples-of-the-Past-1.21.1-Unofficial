package rotp.addons.harvest.stand;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

/**
 * The swarm state 1.16 kept on Harvest's user (051 LivingUtilCap :31-36) rather than on the summoned Harvest, so it
 * outlived an unsummon: Be Closer, and the swarm status a Search left when it fired with no Harvest out. LivingUtilCap
 * wrote nothing to NBT (toNBT :115-121) and nothing copied it to a respawned player, so neither is saved here and
 * HarvestGameEvents forgets both when the player entity is replaced.
 */
public final class HarvestStandData extends StandTypePersistentData {
    private static final Map<Power<?>, boolean[]> AWAITING_HARVEST = new WeakHashMap<>();

    private boolean closer;
    private boolean searchLeftStandless;

    HarvestStandData(HarvestStandType type) {
        super(type);
    }

    @Nullable
    public static HarvestStandData of(@Nullable LivingEntity user) {
        return user != null ? of(StandPower.get(user)) : null;
    }

    @Nullable
    public static HarvestStandData of(@Nullable Power<?> power) {
        return power instanceof StandPower standPower
                        && standPower.getCurTypeData()
                                instanceof HarvestStandData data
                ? data
                : null;
    }

    public boolean isCloser() {
        return closer;
    }

    // 1.16 LivingUtilCap.setCloser :82-87 sent the new value to the user and
    // everyone tracking it.
    public void setCloser(LivingEntity user, boolean closer) {
        this.closer = closer;
        syncOnUpdate(user);
    }

    // 1.16 Search.perform :47-56 set FINDING_ITEM on the user with no Harvest
    // out; the next summon's swarm took it up (HarvestMainEntity.tick :242-246).
    public void markSearchLeftStandless() {
        searchLeftStandless = true;
    }

    public boolean takeSearchLeftStandless() {
        boolean left = searchLeftStandless;
        searchLeftStandless = false;
        return left;
    }

    public boolean hasSearchLeftStandless() {
        return searchLeftStandless;
    }

    // 1.16 HarvestStandType.unsummonStand :75-79 reset the status to STAYING.
    public void clearSwarmStatus() {
        searchLeftStandless = false;
    }

    public void forget() {
        closer = false;
        searchLeftStandless = false;
    }

    // 1.16 StandPower.clear and fullStandClear left the player's LivingUtilCap
    // alone, but a full clear drops this data: both wait with the user's power
    // (not a new player entity's) until Harvest is given again.
    @Override
    public void onPowerCleared(Power<?> userPower, @Nullable PowerType newType) {
        super.onPowerCleared(userPower, newType);
        if (closer || searchLeftStandless) {
            AWAITING_HARVEST.put(userPower, new boolean[] {closer, searchLeftStandless});
        }
        forget();
    }

    @Override
    public void onPowerGiven(Power<?> userPower, @Nullable PowerType oldType, @Nullable PowerData oldData) {
        super.onPowerGiven(userPower, oldType, oldData);
        boolean[] kept = AWAITING_HARVEST.remove(userPower);
        if (kept != null) {
            closer = kept[0];
            searchLeftStandless = kept[1];
        }
    }

    @Override
    public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
        super.toBuf(buf, isSentToTracking);
        buf.writeBoolean(closer);
    }

    @Override
    public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
        super.fromBuf(buf, isSentToTracking);
        closer = buf.readBoolean();
    }
}
