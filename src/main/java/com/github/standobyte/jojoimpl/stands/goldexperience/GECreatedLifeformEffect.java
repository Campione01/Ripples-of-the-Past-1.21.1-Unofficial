package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.ObjectEntity;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.util.functions.NBTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class GECreatedLifeformEffect extends StandEffectInstance {
    private static final double MAX_LIFEFORM_DISTANCE = 128.0;
    private ItemStack sourceItem = ItemStack.EMPTY;
    private ItemStack sourceEntityItemView = ItemStack.EMPTY;
    @Nullable private CompoundTag sourceEntityNbt;
    private BlockState sourceBlock;
    @Nullable private CompoundTag sourceBlockEntityNbt;
    private UUID followTarget;
    private GETransformationEntity.FollowTargetMode followTargetMode;
    private Component sourceName = Component.empty();
    private boolean productEffectsApplied;
    private boolean toothSource;

    public GECreatedLifeformEffect(EntityCustomEffectType<?> effectType) {
        super(effectType);
        needsTarget = true;
    }

    public void setSourceItem(ItemStack sourceItem) {
        this.sourceItem = sourceItem.copy();
        if (!this.sourceItem.isEmpty()) {
            this.sourceItem.setCount(1);
        }
        this.toothSource = false;
        refreshSourceName();
    }

    public void setSourceBlock(BlockState sourceBlock) {
        setSourceBlock(sourceBlock, null);
    }

    public void setSourceBlock(BlockState sourceBlock, @Nullable CompoundTag sourceBlockEntityNbt) {
        this.sourceBlock = sourceBlock;
        this.sourceBlockEntityNbt = sourceBlockEntityNbt != null ? sourceBlockEntityNbt.copy() : null;
        this.toothSource = false;
        refreshSourceName();
    }

    public void setSourceEntity(CompoundTag sourceEntityNbt, ItemStack sourceEntityItemView) {
        this.sourceEntityNbt = sourceEntityNbt.copy();
        this.sourceEntityItemView = sourceEntityItemView.copy();
        if (!this.sourceEntityItemView.isEmpty()) {
            this.sourceEntityItemView.setCount(1);
        }
        this.toothSource = ObjectEntity.isToothSourceTag(this.sourceEntityNbt);
        refreshSourceName();
    }

    public void setFollowTarget(UUID followTarget, GETransformationEntity.FollowTargetMode followTargetMode) {
        this.followTarget = followTarget;
        this.followTargetMode = followTargetMode;
    }

    public ItemStack getItemView() {
        if (!sourceItem.isEmpty()) {
            return sourceItem.copy();
        }
        if (!sourceEntityItemView.isEmpty()) {
            return sourceEntityItemView.copy();
        }
        if (sourceBlock != null) {
            return new ItemStack(sourceBlock.getBlock());
        }
        return ItemStack.EMPTY;
    }

    public Component getSourceName() {
        return sourceName;
    }

    public boolean isToothSource() {
        return toothSource;
    }

    @Override
    protected void start() {}

    @Override
    public void updateTarget(Level level) {
        if (!level.isClientSide()) {
            Entity target = getTarget();
            if (target instanceof GETransformationEntity transformation && !target.isAlive()) {
                Entity transformationTarget = transformation.getTransformationTarget();
                if (transformationTarget != null && transformationTarget.isAlive()) {
                    setTargetEntity(transformationTarget);
                }
                else {
                    clearTarget();
                    return;
                }
            }
        }
        super.updateTarget(level);
        if (!level.isClientSide()) {
            applyLifeformSpawnEffects(getTarget());
        }
    }

    @Override
    protected void tick() {
        if (!level.isClientSide()) {
            Entity target = getTarget();
            Entity lifeform = target instanceof GETransformationEntity transformation
                    && transformation.getTransformationTarget() != null ? transformation.getTransformationTarget() : target;
            if (userPower != null
                    && !userPower.consumeStamina(GoldExperienceCreateLifeformAbility.getStaminaCostTicking(userPower, lifeform), true)) {
                remove();
                return;
            }
            if (target != null && target.distanceToSqr(getStandUser()) > MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE) {
                remove();
                return;
            }
            if (target != null && followTarget != null && followTargetMode != null && level instanceof ServerLevel serverLevel) {
                Entity followTargetEntity = serverLevel.getEntity(followTarget);
                if (followTargetMode == GETransformationEntity.FollowTargetMode.DELIVERY
                        && followTargetEntity != null && followTargetEntity.distanceToSqr(target) < 4.0) {
                    remove();
                    return;
                }
                if (target instanceof Mob mob && followTargetEntity != null && !mob.isAggressive()) {
                    mob.getNavigation().moveTo(followTargetEntity, 1.0);
                }
            }
        }
    }

    private void applyLifeformSpawnEffects(@Nullable Entity target) {
        if (target instanceof Mob mob) {
            LivingEntity standUser = getStandUser();
            if (standUser != null) {
                makeMobNeutralTo(mob, standUser);
            }
        }
        applyProductEffects(target);
    }

    private void applyProductEffects(@Nullable Entity target) {
        if (productEffectsApplied || target instanceof GETransformationEntity || !(target instanceof LivingEntity living)) {
            return;
        }

        List<MobEffectInstance> effects = GEProductEffectsState.getItemEffects(sourceItem);
        if (effects.isEmpty() && !sourceEntityItemView.isEmpty()) {
            effects = GEProductEffectsState.getItemEffects(sourceEntityItemView);
        }
        if (!effects.isEmpty()) {
            GEProductEffectsState.get(living).setProductEffects(effects);
        }
        productEffectsApplied = true;
    }

    private static void makeMobNeutralTo(Mob mob, LivingEntity neutralTo) {
        UUID neutralUuid = neutralTo.getUUID();
        if (clearTargetIfNeutral(mob, neutralUuid)) {
            for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
                if (goal.isRunning()) {
                    goal.stop();
                }
            }
        }
        for (WrappedGoal goal : mob.targetSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof NeutralToEntityGoal neutralGoal && neutralGoal.isNeutralTo(neutralUuid)) {
                return;
            }
        }
        mob.targetSelector.addGoal(0, new NeutralToEntityGoal(mob, neutralUuid));
    }

    private static boolean clearTargetIfNeutral(Mob mob, UUID neutralUuid) {
        LivingEntity target = mob.getTarget();
        if (target != null && neutralUuid.equals(target.getUUID())) {
            mob.setTarget(null);
            return true;
        }
        return false;
    }

    private static final class NeutralToEntityGoal extends Goal {
        private final Mob mob;
        private final UUID neutralUuid;

        private NeutralToEntityGoal(Mob mob, UUID neutralUuid) {
            this.mob = mob;
            this.neutralUuid = neutralUuid;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private boolean isNeutralTo(UUID uuid) {
            return neutralUuid.equals(uuid);
        }

        @Override
        public boolean canUse() {
            return hasNeutralTarget();
        }

        @Override
        public boolean canContinueToUse() {
            return hasNeutralTarget();
        }

        @Override
        public void start() {
            clearTargetIfNeutral(mob, neutralUuid);
        }

        @Override
        public void tick() {
            clearTargetIfNeutral(mob, neutralUuid);
        }

        private boolean hasNeutralTarget() {
            LivingEntity target = mob.getTarget();
            return target != null && neutralUuid.equals(target.getUUID());
        }
    }

    @Override
    protected void stop() {
        if (!level.isClientSide()) {
            Entity target = getTarget();
            if (target != null) {
                GETransformationEntity.restoreEntitySource(target, getStandUser(), sourceItem, sourceBlock,
                        sourceBlockEntityNbt, sourceEntityNbt, sourceEntityItemView);
            }
        }
    }

    @Override
    public void writeAdditionalPacketData(FriendlyByteBuf buf, boolean sendingToUser) {
        super.writeAdditionalPacketData(buf, sendingToUser);
        if (sendingToUser) {
            writeSourceItem(buf);
            if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(registryBuf, getSourceName());
            }
            buf.writeBoolean(toothSource);
        }
    }

    @Override
    public void readAdditionalPacketData(FriendlyByteBuf buf, boolean clientIsUser) {
        super.readAdditionalPacketData(buf, clientIsUser);
        if (clientIsUser) {
            sourceItem = readSourceItem(buf);
            if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
                sourceName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(registryBuf);
            }
            toothSource = buf.readBoolean();
        }
    }

    @Override
    protected void writeAdditionalSaveData(CompoundTag nbt) {
        super.writeAdditionalSaveData(nbt);
        if (!sourceItem.isEmpty()) {
            nbt.put("GESourceItem", sourceItem.save(level.registryAccess()));
        }
        if (sourceEntityNbt != null) {
            nbt.put("GESourceEntity", sourceEntityNbt.copy());
        }
        if (!sourceEntityItemView.isEmpty()) {
            nbt.put("GESourceEntityItemView", sourceEntityItemView.save(level.registryAccess()));
        }
        NBTUtil.put(nbt, "GESourceBlock", sourceBlock, BlockState.CODEC);
        if (sourceBlockEntityNbt != null) {
            nbt.put("GESourceBlockEntity", sourceBlockEntityNbt.copy());
        }
        if (followTarget != null && followTargetMode != null) {
            nbt.putUUID("GEFollowTarget", followTarget);
            nbt.putString("GEFollowMode", followTargetMode.name());
        }
        nbt.putBoolean("GEProductEffectsApplied", productEffectsApplied);
        nbt.putBoolean("GEToothSource", toothSource);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("GESourceItem", Tag.TAG_COMPOUND)) {
            sourceItem = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("GESourceItem"));
        }
        sourceEntityNbt = nbt.contains("GESourceEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceEntity").copy() : null;
        if (nbt.contains("GESourceEntityItemView", Tag.TAG_COMPOUND)) {
            sourceEntityItemView = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("GESourceEntityItemView"));
        }
        sourceBlock = NBTUtil.getOptional(nbt, "GESourceBlock", BlockState.CODEC).orElse(null);
        sourceBlockEntityNbt = nbt.contains("GESourceBlockEntity", Tag.TAG_COMPOUND)
                ? nbt.getCompound("GESourceBlockEntity").copy() : null;
        followTarget = nbt.hasUUID("GEFollowTarget") ? nbt.getUUID("GEFollowTarget") : null;
        followTargetMode = null;
        if (followTarget != null && nbt.contains("GEFollowMode", Tag.TAG_STRING)) {
            try {
                followTargetMode = GETransformationEntity.FollowTargetMode.valueOf(nbt.getString("GEFollowMode"));
            }
            catch (IllegalArgumentException ignored) {
                followTarget = null;
            }
        }
        productEffectsApplied = nbt.getBoolean("GEProductEffectsApplied");
        toothSource = nbt.contains("GEToothSource", Tag.TAG_BYTE)
                ? nbt.getBoolean("GEToothSource")
                : ObjectEntity.isToothSourceTag(sourceEntityNbt);
        refreshSourceName();
    }

    private void writeSourceItem(FriendlyByteBuf buf) {
        ItemStack itemView = getItemView();
        buf.writeBoolean(!itemView.isEmpty());
        if (!itemView.isEmpty()) {
            buf.writeNbt(itemView.save(level.registryAccess()));
        }
    }

    private ItemStack readSourceItem(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        }
        CompoundTag itemTag = buf.readNbt();
        return itemTag != null ? ItemStack.parseOptional(level.registryAccess(), itemTag) : ItemStack.EMPTY;
    }

    private void refreshSourceName() {
        sourceName = makeSourceName();
    }

    private Component makeSourceName() {
        if (sourceEntityNbt != null && level != null) {
            Entity sourceEntity = EntityType.create(sourceEntityNbt, level).orElse(null);
            if (sourceEntity != null) {
                return sourceEntity.getDisplayName();
            }
        }
        if (!sourceEntityItemView.isEmpty()) {
            return sourceEntityItemView.getHoverName();
        }
        if (!sourceItem.isEmpty()) {
            return sourceItem.getHoverName();
        }
        if (sourceBlock != null) {
            return sourceBlock.getBlock().getName();
        }
        return Component.empty();
    }
}
