package rotp.addons.scr.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class RequiemEvolutionState
        implements INBTSerializable<CompoundTag> {
    private static final String ACQUIRED_TAG =
            "AcquiredThroughBeetleArrow";
    private static final String ACQUIRED_STAND_ARROW_TAG =
            "AcquiredThroughStandArrow";
    private static final String SOURCE_STAND_TAG =
            "SourceStand";
    private static final String TRIGGER_ITEM_TAG =
            "TriggerItem";
    private static final String TRANSITION_TIME_TAG =
            "TransitionGameTime";

    private boolean acquiredThroughBeetleArrow;
    private boolean acquiredThroughStandArrow;
    private ResourceLocation sourceStand;
    private ResourceLocation triggerItem;
    private long transitionGameTime;

    public boolean acquiredThroughBeetleArrow() {
        return acquiredThroughBeetleArrow;
    }

    public ResourceLocation sourceStand() {
        return sourceStand;
    }

    public boolean acquiredThroughStandArrow() {
        return acquiredThroughStandArrow;
    }

    public ResourceLocation triggerItem() {
        return triggerItem;
    }

    public long transitionGameTime() {
        return transitionGameTime;
    }

    public void recordTransition(
            ResourceLocation source,
            ResourceLocation trigger,
            boolean beetleArrow,
            long gameTime) {
        acquiredThroughBeetleArrow = beetleArrow;
        acquiredThroughStandArrow = true;
        sourceStand = source;
        triggerItem = trigger;
        transitionGameTime = gameTime;
    }

    public void copyFrom(RequiemEvolutionState source) {
        if (source == null) {
            return;
        }
        acquiredThroughBeetleArrow =
                source.acquiredThroughBeetleArrow;
        acquiredThroughStandArrow =
                source.acquiredThroughStandArrow;
        sourceStand = source.sourceStand;
        triggerItem = source.triggerItem;
        transitionGameTime = source.transitionGameTime;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(
                ACQUIRED_TAG, acquiredThroughBeetleArrow);
        tag.putBoolean(
                ACQUIRED_STAND_ARROW_TAG,
                acquiredThroughStandArrow);
        if (sourceStand != null) {
            tag.putString(SOURCE_STAND_TAG, sourceStand.toString());
        }
        if (triggerItem != null) {
            tag.putString(
                    TRIGGER_ITEM_TAG, triggerItem.toString());
        }
        tag.putLong(TRANSITION_TIME_TAG, transitionGameTime);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        acquiredThroughBeetleArrow =
                tag.getBoolean(ACQUIRED_TAG);
        acquiredThroughStandArrow =
                tag.contains(ACQUIRED_STAND_ARROW_TAG)
                        ? tag.getBoolean(
                                ACQUIRED_STAND_ARROW_TAG)
                        : acquiredThroughBeetleArrow;
        sourceStand = tag.contains(SOURCE_STAND_TAG)
                ? ResourceLocation.tryParse(
                        tag.getString(SOURCE_STAND_TAG))
                : null;
        triggerItem = tag.contains(TRIGGER_ITEM_TAG)
                ? ResourceLocation.tryParse(
                        tag.getString(TRIGGER_ITEM_TAG))
                : null;
        transitionGameTime =
                tag.getLong(TRANSITION_TIME_TAG);
    }
}
