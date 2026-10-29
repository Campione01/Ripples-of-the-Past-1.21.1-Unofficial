package rotp.addons.echoes.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EchoesUserState
        implements INBTSerializable<CompoundTag> {
    public enum ActiveSkill {
        NONE,
        FREEZE_TARGET,
        HAND_FREEZE,
        FREEZE_SELF,
        GRAVITY_INFUSE
    }

    private static final int SERIALIZATION_VERSION = 1;

    private ActiveSkill activeSkill = ActiveSkill.NONE;
    @Nullable
    private UUID frozenTarget;
    private long skillSoundCooldownUntil;
    private long autoActionCooldownUntil;
    private long lastCommandTime = Long.MIN_VALUE;
    private long lastActiveTalk = Long.MIN_VALUE;
    private int ignoreCount;

    public ActiveSkill activeSkill() {
        return activeSkill;
    }

    @Nullable
    public UUID frozenTarget() {
        return frozenTarget;
    }

    public void activateTargetFreeze(UUID target) {
        activeSkill = ActiveSkill.FREEZE_TARGET;
        frozenTarget = target;
    }

    public void activateHandFreeze(UUID target) {
        activeSkill = ActiveSkill.HAND_FREEZE;
        frozenTarget = target;
    }

    public void activateSelfFreeze() {
        activeSkill = ActiveSkill.FREEZE_SELF;
        frozenTarget = null;
    }

    public void activateGravityInfuse() {
        activeSkill = ActiveSkill.GRAVITY_INFUSE;
        frozenTarget = null;
    }

    public void clearActiveSkill() {
        activeSkill = ActiveSkill.NONE;
        frozenTarget = null;
    }

    public boolean skillSoundReady(long gameTime) {
        return gameTime >= skillSoundCooldownUntil;
    }

    public void resetSkillSoundCooldown(long gameTime) {
        skillSoundCooldownUntil = gameTime + 100L;
    }

    public boolean autoActionReady(long gameTime) {
        return gameTime >= autoActionCooldownUntil;
    }

    public void resetAutoActionCooldown(long gameTime) {
        autoActionCooldownUntil = gameTime + 100L;
    }

    public long lastCommandTime() {
        return lastCommandTime;
    }

    public void setLastCommandTime(long gameTime) {
        lastCommandTime = gameTime;
    }

    public long lastActiveTalk() {
        return lastActiveTalk;
    }

    public void setLastActiveTalk(long gameTime) {
        lastActiveTalk = gameTime;
    }

    public int ignoreCount() {
        return ignoreCount;
    }

    public void setIgnoreCount(int value) {
        ignoreCount = Math.max(0, Math.min(3, value));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SERIALIZATION_VERSION);
        tag.putString("ActiveSkill", activeSkill.name());
        if (frozenTarget != null) {
            tag.putUUID("FrozenTarget", frozenTarget);
        }
        tag.putLong("SkillSoundCooldownUntil", skillSoundCooldownUntil);
        tag.putLong("AutoActionCooldownUntil", autoActionCooldownUntil);
        tag.putLong("LastCommandTime", lastCommandTime);
        tag.putLong("LastActiveTalk", lastActiveTalk);
        tag.putInt("IgnoreCount", ignoreCount);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clearActiveSkill();
        skillSoundCooldownUntil = 0L;
        autoActionCooldownUntil = 0L;
        lastCommandTime = Long.MIN_VALUE;
        lastActiveTalk = Long.MIN_VALUE;
        ignoreCount = 0;
        if (tag.getInt("Version") != SERIALIZATION_VERSION) {
            return;
        }
        try {
            activeSkill = ActiveSkill.valueOf(tag.getString("ActiveSkill"));
        } catch (IllegalArgumentException ignored) {
            activeSkill = ActiveSkill.NONE;
        }
        if ((activeSkill == ActiveSkill.FREEZE_TARGET
                        || activeSkill == ActiveSkill.HAND_FREEZE)
                && tag.hasUUID("FrozenTarget")) {
            frozenTarget = tag.getUUID("FrozenTarget");
        } else if (activeSkill == ActiveSkill.FREEZE_TARGET
                || activeSkill == ActiveSkill.HAND_FREEZE) {
            activeSkill = ActiveSkill.NONE;
        }
        skillSoundCooldownUntil =
                Math.max(0L, tag.getLong("SkillSoundCooldownUntil"));
        autoActionCooldownUntil =
                Math.max(0L, tag.getLong("AutoActionCooldownUntil"));
        lastCommandTime = tag.getLong("LastCommandTime");
        lastActiveTalk = tag.getLong("LastActiveTalk");
        setIgnoreCount(tag.getInt("IgnoreCount"));
    }
}
