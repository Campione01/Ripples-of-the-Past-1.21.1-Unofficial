package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.customobjects.RoadRollerEntity;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.github.standobyte.jojo.mrpresident.MrPresidentRoomStateOwner;
import com.github.standobyte.jojo.modcompat.ModInteractionUtil;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

public class GoldExperienceCreateLifeformAbility extends GoldExperienceUtilityAbility {
    private static final int MAX_LIFEFORM_ID_LENGTH = 256;
    private static final float STAMINA_COST_TICK = 0.2F;
    public static final int MAX_CREATED_LIFEFORMS = 16;
    private static final double SOURCE_ENTITY_TARGET_RANGE = 8.0D;
    private static final double SOURCE_BLOCK_TARGET_RANGE = 10.0D;
    private static final double SOURCE_TARGET_PRECISION = 0.0D;
    private static final ResourceLocation ENCH_TABLE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "enchanting_table");
    static final String CREATE_LIFEFORM_ABILITY_NAME = "create_lifeform";

    public GoldExperienceCreateLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        StandPower standPower = user != null ? StandPower.get(user) : null;
        if (user == null || standPower == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (user.level().isClientSide() && GoldExperienceLifeformState.get(user).selectedLifeformSubtype(user.level()).isEmpty()) {
            return ConditionCheck.createNegative("choose_lifeform");
        }

        if (!canCreateMoreLifeforms(standPower)) {
            return ConditionCheck.createNegative("ge_too_many_mobs");
        }

        ItemTracker markedItem = GoldExperienceMarkItemAbility.getTargetedMarkedItem(standPower, user);
        if (isValidMarkedItem(markedItem)) {
            return sourceConditions(context);
        }

        LivingEntity aimingEntity = getControlledEntity(user, standPower);
        if (isSyncedLifeformTargetTooFar(user.level(), aimingEntity)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        ActionTarget aimTarget = findLifeformTarget(user.level(), aimingEntity);
        LifeformSource targetSource = targetedSource(user.level(), user, aimTarget);
        if (targetSource != null) {
            return sourceConditions(context);
        }
        ConditionCheck invalidEntityTarget = invalidEntityTargetCondition(aimTarget);
        if (!invalidEntityTarget.isPositive()) {
            return invalidEntityTarget;
        }

        ItemStack offHandItem = user.getOffhandItem();
        if (!offHandItem.isEmpty() && canGiveLifeTo(offHandItem)) {
            return sourceConditions(context);
        }

        if (aimTarget.getType() == ActionTarget.TargetType.BLOCK) {
            return ConditionCheck.createNegative("ge_lifeform_material_block");
        }
        if (offHandItem.isEmpty()) {
            return ConditionCheck.createNegative("ge_lifeform_material");
        }
        return ConditionCheck.createNegative("ge_lifeform_material_item");
    }

    private ConditionCheck sourceConditions(Power<?> context) {
        return super.checkSpecificConditions(context);
    }

    @Override
    public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
        String selectedLifeformId = GoldExperienceLifeformState.get(user).selectedOrFirstMetId(user.level());
        serverboundBuf.writeBoolean(selectedLifeformId != null);
        if (selectedLifeformId != null) {
            serverboundBuf.writeUtf(selectedLifeformId, MAX_LIFEFORM_ID_LENGTH);
        }

        StandPower standPower = StandPower.get(user);
        ItemTracker markedItem = standPower != null ? GoldExperienceMarkItemAbility.getTargetedMarkedItem(standPower, user) : null;
        serverboundBuf.writeBoolean(markedItem != null);
        if (markedItem != null) {
            serverboundBuf.writeUUID(markedItem.trackerId);
        }
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        StandPower standPower = StandPower.get(user);
        if (standPower == null) {
            return;
        }

        LifeformInput input = readLifeformInput(extraClientInput, user);
        String selectedLifeformInput = input.selectedLifeformId();
        UUID markedItemTrackerInput = input.markedItemTrackerId();

        LivingEntity performer = getControlledEntity(user, standPower);
        LifeformSource source = markedItemSource(serverLevel, standPower, user, markedItemTrackerInput);
        ActionTarget target = ActionTarget.EMPTY;
        if (source == null) {
            target = findLifeformTarget(level, performer);
            source = targetedSource(level, user, target);
        }
        if (source == null && target.getType() == ActionTarget.TargetType.ENTITY) {
            return;
        }
        if (source == null) {
            source = offhandSource(level, user);
        }
        if (source == null) {
            return;
        }

        Optional<EntitySubtype<?>> selectedLifeform = selectedLifeformSubtype(level, user, selectedLifeformInput);
        if (selectedLifeform.isEmpty()) {
            ConditionCheck.sendActionFailedMessage(this, ConditionCheck.createNegative("choose_lifeform"), user);
            return;
        }

        Entity createdEntity = createLifeformEntity(level, user, selectedLifeform.get());
        if (createdEntity == null) {
            ConditionCheck.sendActionFailedMessage(this, ConditionCheck.createNegative("ge_lifeform_spawn"), user);
            return;
        }
        if (!source.canStillConsume(level, user)) {
            return;
        }

        GETransformationEntity transformation = ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get().create(level);
        if (transformation == null) {
            return;
        }

        int ticks = getTicksToCreate(user, standPower, createdEntity);
        source.applyCustomName(createdEntity);
        LifeformFollowTarget followTarget = chooseFollowTarget(source, level, createdEntity, user, standPower);

        placeTransformationEntity(transformation, createdEntity, source, performer, user);
        transformation.withTransformationTarget(createdEntity).withDuration(ticks);
        source.applyToTransformation(transformation);
        applyFollowTarget(transformation, followTarget, user);
        if (!source.consume(level, user)) {
            return;
        }

        if (!standPower.isUserCreative()) {
            int cooldown = Math.max(ticks / 2, 1);
            transformation.actionCooldown = cooldown;
            standPower.setAbilityCooldown(CREATE_LIFEFORM_ABILITY_NAME, cooldown, cooldown);
        }
        level.addFreshEntity(transformation);
        JojoModUtil.sayVoiceLine(user, ModSoundEvents.GIORNO_NEW_LIFE);

        GECreatedLifeformEffect createdLifeform = ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().create(level);
        source.applyToEffect(createdLifeform);
        createdLifeform.setFollowTarget(transformation.getFollowTarget(), transformation.getFollowTargetMode());
        createdLifeform.withTarget(transformation);
        standPower.userStandEffects.addEffect(createdLifeform);
        enterMrPresidentRoomOnCreation(serverLevel, user, standPower, transformation, createdEntity, source);
        if (source.markedItemTracker() != null) {
            standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get())
                    .toList()
                    .forEach(standPower.userStandEffects::removeEffect);
        }
    }

    private static LifeformInput readLifeformInput(@Nullable FriendlyByteBuf extraClientInput, LivingEntity user) {
        if (extraClientInput == null || extraClientInput.readableBytes() <= 0) {
            return LifeformInput.EMPTY;
        }
        try {
            String selectedLifeformId = extraClientInput.readBoolean()
                    ? extraClientInput.readUtf(MAX_LIFEFORM_ID_LENGTH)
                    : null;
            UUID markedItemTrackerId = extraClientInput.readBoolean() ? extraClientInput.readUUID() : null;
            return new LifeformInput(selectedLifeformId, markedItemTrackerId);
        }
        catch (RuntimeException e) {
            JojoMod.getLogger().warn("Ignoring malformed Gold Experience create lifeform input from {}.",
                    user.getName().getString(), e);
            return LifeformInput.EMPTY;
        }
    }

    private static record LifeformInput(@Nullable String selectedLifeformId, @Nullable UUID markedItemTrackerId) {
        private static final LifeformInput EMPTY = new LifeformInput(null, null);
    }

    public static boolean canGiveLifeTo(ItemStack item) {
        return !isItemLivingMatter(item) || isFishBucketItem(item.getItem());
    }

    public static boolean canCreateMoreLifeforms(StandPower standPower) {
        long mobsCreated = standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get()).count();
        return mobsCreated < MAX_CREATED_LIFEFORMS;
    }

    public static boolean hasSelectedLifeform(LivingEntity user) {
        return GoldExperienceLifeformState.get(user).selectedLifeformSubtype(user.level()).isPresent();
    }

    @Nullable
    public static Entity createSelectedLifeformEntity(Level level, LivingEntity user) {
        return createLifeformEntity(level, user, (String) null);
    }

    @Nullable
    static Entity createLifeformEntity(Level level, LivingEntity user, @Nullable String requestedLifeformId) {
        return selectedLifeformSubtype(level, user, requestedLifeformId)
                .map(subtype -> createLifeformEntity(level, user, subtype))
                .orElse(null);
    }

    static Optional<EntitySubtype<?>> selectedLifeformSubtype(Level level, LivingEntity user, @Nullable String requestedLifeformId) {
        GoldExperienceLifeformState state = GoldExperienceLifeformState.get(user);
        if (requestedLifeformId != null && state.hasMetLifeform(requestedLifeformId)) {
            return GoldExperienceLifeforms.subtypeFromId(requestedLifeformId)
                    .filter(subtype -> GoldExperienceLifeforms.isValidLifeform(subtype, level));
        }
        return state.selectedLifeformSubtype(level);
    }

    @Nullable
    private static Entity createLifeformEntity(Level level, LivingEntity user, EntitySubtype<?> subtype) {
        return GoldExperienceLifeforms.createEntity(subtype, level, user);
    }

    private static boolean isItemLivingMatter(ItemStack itemStack) {
        return HamonUtil.isItemLivingMatter(itemStack);
    }

    private static boolean isFishBucketItem(Item item) {
        return item == Items.COD_BUCKET
                || item == Items.SALMON_BUCKET
                || item == Items.TROPICAL_FISH_BUCKET
                || item == Items.PUFFERFISH_BUCKET;
    }

    private static boolean isBlockLiving(BlockState blockState) {
        Block block = blockState.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String blockName = id.getPath();
        if (blockName.contains("dead")) {
            return false;
        }

        return blockState.is(BlockTags.LOGS)
                || block instanceof BushBlock
                || block instanceof CactusBlock
                || block instanceof LeavesBlock
                || block instanceof SnowyDirtBlock
                || blockName.contains("mossy")
                || blockName.contains("coral")
                || blockName.contains("bamboo");
    }

    @Nullable
    private static LifeformSource targetedSource(Level level, LivingEntity user, ActionTarget target) {
        return switch (target.getType()) {
            case ENTITY -> {
                Entity entity = target.getMainEntity();
                if (entity instanceof ItemEntity itemEntity && canGiveLifeTo(itemEntity.getItem())) {
                    ItemStack sourceItem = transformedInventorySourceItem(itemEntity.getItem());
                    Entity projectileSource = sourceEntityFromInventoryItem(level, user, sourceItem);
                    CompoundTag sourceEntityNbt = projectileSource != null ? saveSourceEntity(projectileSource) : null;
                    ItemStack sourceEntityItemView = projectileSource != null ? sourceItem.copy() : ItemStack.EMPTY;
                    yield new LifeformSource(SourceType.ITEM_ENTITY, sourceItem, null, null, sourceEntityNbt, sourceEntityItemView,
                            null, itemEntity, null, null, itemEntityThrowerFollowTarget(itemEntity), null);
                }
                if (isConvertibleEntitySource(entity)) {
                    yield new LifeformSource(SourceType.ENTITY, ItemStack.EMPTY, null, null,
                            saveSourceEntity(entity), sourceEntityItemView(entity), null, null, entity, null, null, null);
                }
                yield null;
            }
            case BLOCK -> {
                BlockPos blockPos = target.getBlockPos();
                BlockState blockState = level.getBlockState(blockPos);
                if (isUsableBlockSource(level, user, blockPos, blockState)) {
                    CompoundTag sourceBlockEntityNbt = captureBlockEntityNbt(level, blockPos, blockState);
                    yield new LifeformSource(SourceType.BLOCK, ItemStack.EMPTY, blockState, sourceBlockEntityNbt,
                            null, ItemStack.EMPTY, blockPos, null, null, null, null, null);
                }
                yield null;
            }
            default -> null;
        };
    }

    private static ConditionCheck invalidEntityTargetCondition(ActionTarget target) {
        if (target.getType() != ActionTarget.TargetType.ENTITY) {
            return ConditionCheck.POSITIVE;
        }
        Entity entity = target.getMainEntity();
        if (entity instanceof ItemEntity itemEntity && !canGiveLifeTo(itemEntity.getItem())) {
            return ConditionCheck.createNegative("ge_lifeform_material_item");
        }
        return ConditionCheck.NEGATIVE;
    }

    @Nullable
    private static LifeformSource offhandSource(Level level, LivingEntity user) {
        ItemStack offHandItem = user.getOffhandItem();
        if (!offHandItem.isEmpty() && canGiveLifeTo(offHandItem)) {
            return itemBackedSource(SourceType.OFFHAND_ITEM, offHandItem, level, user, null);
        }
        return null;
    }

    @Nullable
    private static LifeformSource markedItemSource(ServerLevel serverLevel, StandPower standPower, LivingEntity user,
            @Nullable UUID requestedTrackerId) {
        ItemTracker itemTracker = requestedTrackerId != null
                ? ItemTracking.getItemTracker(requestedTrackerId, serverLevel)
                : GoldExperienceMarkItemAbility.getTargetedMarkedItem(standPower, user);
        if (!GoldExperienceMarkItemAbility.hasMarkedItemTracker(standPower, itemTracker)
                || !isValidMarkedItem(itemTracker)
                || !itemTracker.checkItemIsThere(serverLevel)) {
            return null;
        }
        LifeformSource stuckProjectileSource = markedStuckProjectileSource(serverLevel, user, itemTracker);
        if (stuckProjectileSource != null) {
            return stuckProjectileSource;
        }
        return itemBackedSource(SourceType.MARKED_ITEM, itemTracker.getItem(), serverLevel, user, itemTracker,
                markedItemEntityThrowerFollowTarget(serverLevel, itemTracker));
    }

    private static LifeformSource itemBackedSource(SourceType type, ItemStack itemStack, Level level,
            LivingEntity wouldBeThrower, @Nullable ItemTracker markedItemTracker) {
        return itemBackedSource(type, itemStack, level, wouldBeThrower, markedItemTracker, null);
    }

    private static LifeformSource itemBackedSource(SourceType type, ItemStack itemStack, Level level,
            LivingEntity wouldBeThrower, @Nullable ItemTracker markedItemTracker,
            @Nullable LifeformFollowTarget followTarget) {
        ItemStack sourceItem = itemStack.copy();
        sourceItem = transformedInventorySourceItem(sourceItem);
        Entity projectileSource = sourceEntityFromInventoryItem(level, wouldBeThrower, sourceItem);
        CompoundTag sourceEntityNbt = projectileSource != null ? saveSourceEntity(projectileSource) : null;
        ItemStack sourceEntityItemView = projectileSource != null ? sourceItem.copy() : ItemStack.EMPTY;
        return new LifeformSource(type, sourceItem, null, null, sourceEntityNbt, sourceEntityItemView,
                null, null, null, markedItemTracker, followTarget, null);
    }

    @Nullable
    private static LifeformFollowTarget markedItemEntityThrowerFollowTarget(Level level, ItemTracker itemTracker) {
        if (itemTracker.getItemState() != KnownItemState.ENTITY_IS_ITEM) {
            return null;
        }
        Entity trackedEntity = itemTracker.getAtEntity(level);
        return trackedEntity instanceof ItemEntity itemEntity ? itemEntityThrowerFollowTarget(itemEntity) : null;
    }

    @Nullable
    private static LifeformFollowTarget itemEntityThrowerFollowTarget(ItemEntity itemEntity) {
        Entity thrower = itemEntity.getOwner();
        return thrower != null
                ? new LifeformFollowTarget(thrower.getUUID(), GETransformationEntity.FollowTargetMode.TRACK)
                : null;
    }

    @Nullable
    private static LifeformSource markedStuckProjectileSource(Level level, LivingEntity user, ItemTracker itemTracker) {
        KnownItemState itemState = itemTracker.getItemState();
        if (itemState != KnownItemState.STUCK_ARROW && itemState != KnownItemState.STUCK_KNIFE) {
            return null;
        }
        Entity holderEntity = itemTracker.getAtEntity(level);
        if (!(holderEntity instanceof LivingEntity holder)) {
            return null;
        }
        SourceType sourceType = itemState == KnownItemState.STUCK_ARROW ? SourceType.STUCK_ARROW : SourceType.STUCK_KNIFE;
        if (!hasStuckProjectile(holder, sourceType)) {
            return null;
        }

        ItemStack projectileItem = itemTracker.getItem() != null ? itemTracker.getItem().copy() : ItemStack.EMPTY;
        if (projectileItem.isEmpty()) {
            projectileItem = defaultStuckProjectileItem(sourceType);
        }
        projectileItem.setCount(1);

        AbstractArrow projectileSource = sourceEntityFromStuckProjectile(sourceType, level, user, projectileItem);
        return new LifeformSource(sourceType, ItemStack.EMPTY, null, null,
                saveSourceEntity(projectileSource), projectileItem, null, null, null, itemTracker,
                new LifeformFollowTarget(holder.getUUID(), GETransformationEntity.FollowTargetMode.AGGRO_TRACK),
                holder);
    }

    private static void enterMrPresidentRoomOnCreation(ServerLevel level, LivingEntity user, StandPower standPower,
            GETransformationEntity transformation, Entity createdEntity, LifeformSource source) {
        if (!(createdEntity instanceof CocoJumboTurtleEntity turtle)) {
            return;
        }
        StandPower turtleStand = StandPower.get(turtle);
        if (turtleStand == null || !turtleStand.hasPower() || turtleStand.getPowerType() != ModStands.MR_PRESIDENT.get()) {
            return;
        }

        MrPresidentRoomStateOwner rooms = MrPresidentRoomStateOwner.get(level.getServer());
        rooms.rememberTurtlePosition(turtle);
        Entity standEntity = standPower.getSummonedStandEntity();
        ArrayList<Entity> targets = new ArrayList<>(CocoJumboTurtleEntity.findTargets(turtle,
                target -> target != transformation && target != user && target != standEntity));
        Entity nonUserItemHolder = source.nonUserItemHolder(level, user);
        if (nonUserItemHolder != null && !targets.contains(nonUserItemHolder)) {
            targets.add(nonUserItemHolder);
        }
        rooms.enterTargets(level, turtle, targets);
    }

    private static AbstractArrow sourceEntityFromStuckProjectile(SourceType sourceType, Level level,
            LivingEntity user, ItemStack projectileItem) {
        AbstractArrow projectile = switch (sourceType) {
            case STUCK_ARROW -> new Arrow(level, user, projectileItem.copy(), null);
            case STUCK_KNIFE -> new KnifeEntity(level, user, projectileItem.copy());
            default -> throw new IllegalArgumentException("Unsupported stuck projectile source: " + sourceType);
        };
        projectile.pickup = AbstractArrow.Pickup.ALLOWED;
        return projectile;
    }

    private static ItemStack defaultStuckProjectileItem(SourceType sourceType) {
        return switch (sourceType) {
            case STUCK_ARROW -> new ItemStack(Items.ARROW);
            case STUCK_KNIFE -> new ItemStack(ModItems.KNIFE.get());
            default -> ItemStack.EMPTY;
        };
    }

    private static boolean isValidMarkedItem(@Nullable ItemTracker itemTracker) {
        return itemTracker != null
                && itemTracker.getItem() != null
                && !itemTracker.getItem().isEmpty()
                && canGiveLifeTo(itemTracker.getItem());
    }

    private static ActionTarget findLifeformTarget(Level level, LivingEntity aiming) {
        ActionTarget syncedTarget = getSyncedLookTarget(level, aiming);
        if (!syncedTarget.isEmpty(level)
                && HitResultUtil.isTargetWithinRange(syncedTarget, aiming, level,
                        SOURCE_BLOCK_TARGET_RANGE, SOURCE_ENTITY_TARGET_RANGE)) {
            return syncedTarget;
        }
        return HitResultUtil.clip(
                aiming.getEyePosition(),
                aiming.getLookAngle(),
                SOURCE_BLOCK_TARGET_RANGE,
                SOURCE_ENTITY_TARGET_RANGE,
                level,
                GoldExperienceCreateLifeformAbility::isConvertibleEntitySource,
                aiming,
                SOURCE_TARGET_PRECISION);
    }

    private static ActionTarget getSyncedLookTarget(Level level, LivingEntity aiming) {
        var aim = LivingComponentAction.getAim(aiming);
        if (aim == null) {
            return ActionTarget.EMPTY;
        }
        ActionTarget target = aim.getTarget();
        return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
    }

    private static boolean isSyncedLifeformTargetTooFar(Level level, LivingEntity aiming) {
        ActionTarget syncedTarget = getSyncedLookTarget(level, aiming);
        return !syncedTarget.isEmpty(level)
                && !HitResultUtil.isTargetWithinRange(syncedTarget, aiming, level,
                        SOURCE_BLOCK_TARGET_RANGE, SOURCE_ENTITY_TARGET_RANGE);
    }

	private static boolean isConvertibleEntitySource(Entity entity) {
		return entity instanceof ItemEntity
				|| entity instanceof PrimedTnt
				|| entity instanceof EndCrystal
				|| entity instanceof Boat
				|| entity instanceof RoadRollerEntity;
	}

    static CompoundTag saveSourceEntity(Entity entity) {
        CompoundTag nbt = new CompoundTag();
        String entityId = entity.getEncodeId();
        if (entityId != null) {
            nbt.putString("id", entityId);
            entity.saveWithoutId(nbt);
            nbt.remove("Passengers");
        }
        return nbt;
    }

    static ItemStack sourceEntityItemView(Entity entity) {
        if (entity instanceof PrimedTnt) {
            return new ItemStack(Items.TNT);
        }
        if (entity instanceof EndCrystal) {
            return new ItemStack(Items.END_CRYSTAL);
        }
		if (entity instanceof Boat boat) {
			return new ItemStack(boat.getDropItem());
		}
		if (entity.getType() == ModEntityTypes.ROAD_ROLLER.get()) {
			return new ItemStack(ModItems.ROAD_ROLLER.get());
		}
		return ItemStack.EMPTY;
	}

    @Nullable
    private static Entity sourceEntityFromInventoryItem(Level level, LivingEntity wouldBeThrower, ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            ThrownPotion potion = new ThrownPotion(level, wouldBeThrower);
            potion.setItem(itemStack.copy());
            return potion;
        }
        if (item == Items.ENDER_PEARL) {
            return new ThrownEnderpearl(level, wouldBeThrower);
        }
        return null;
    }

    private static ItemStack transformedInventorySourceItem(ItemStack itemStack) {
        ItemStack transformedItem = null;
        if (itemStack.getItem() instanceof BucketItem bucketItem) {
            Fluid fluid = bucketItem.content;
            Item bucketWithoutFish = fluid.getBucket();
            if (bucketWithoutFish != Items.AIR) {
                transformedItem = new ItemStack(bucketWithoutFish);
            }
        }
        if (transformedItem == null) {
            transformedItem = itemStack.copy();
        }
        if (itemStack.has(DataComponents.CUSTOM_NAME)) {
            transformedItem.set(DataComponents.CUSTOM_NAME, itemStack.get(DataComponents.CUSTOM_NAME));
        }
        transformedItem.setCount(1);
        return transformedItem;
    }

    private static void checkBucketExtraContent(Level level, LivingEntity user, ItemStack itemStack, BlockPos pos) {
        if (itemStack.getItem() instanceof BucketItem bucketItem && user instanceof Player player) {
            bucketItem.checkExtraContent(player, level, itemStack, pos);
        }
    }

    private static boolean isUsableBlockSource(Level level, LivingEntity user, BlockPos blockPos, BlockState blockState) {
        return JojoModUtil.breakingBlocksEnabled(level)
                && !blockState.isAir()
                && (!blockState.hasBlockEntity() || isUsableBlockEntitySource(level, blockPos))
                && blockState.getDestroySpeed(level, blockPos) >= 0
                && !isBlockLiving(blockState)
                && (!(level instanceof ServerLevel serverLevel) || JojoModUtil.canEntityDestroy(serverLevel, blockPos, blockState, user));
    }

    private static boolean isUsableBlockEntitySource(Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        return blockEntity != null;
    }

    @Nullable
    private static CompoundTag captureBlockEntityNbt(Level level, BlockPos blockPos, BlockState blockState) {
        if (!blockState.hasBlockEntity()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity != null && shouldIgnoreBlockEntityNbt(blockEntity)) {
            return null;
        }
        return blockEntity != null ? blockEntity.saveWithFullMetadata(level.registryAccess()) : null;
    }

    private static boolean shouldIgnoreBlockEntityNbt(BlockEntity blockEntity) {
        ResourceLocation blockEntityId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return ModInteractionUtil.isModLoaded("apotheosis") && ENCH_TABLE_ID.equals(blockEntityId);
    }

    private static LivingEntity getControlledEntity(LivingEntity user, StandPower standPower) {
        StandEntity standEntity = standPower.getSummonedStandEntity();
        return standEntity != null && standEntity.isManuallyControlled() ? standEntity : user;
    }

    private static void placeTransformationEntity(GETransformationEntity transformation, Entity createdEntity,
            LifeformSource source, LivingEntity performer, LivingEntity user) {
        Vec3 pos = source.spawnPos(performer, user, createdEntity);
        transformation.moveTo(pos.x, pos.y, pos.z, performer.getYRot(), 0.0F);
        createdEntity.moveTo(pos.x, pos.y, pos.z, performer.getYRot(), 0.0F);
        if (createdEntity instanceof LivingEntity livingCreated) {
            livingCreated.setYHeadRot(livingCreated.getYRot());
        }
    }

    @Nullable
    private static LifeformFollowTarget chooseFollowTarget(LifeformSource source, Level level, Entity createdEntity,
            LivingEntity user, StandPower standPower) {
        LifeformFollowTarget followTarget = source.followTarget();
        if (followTarget == null) {
            followTarget = source.deliveryFollowTarget(level, createdEntity);
        }
        LivingEntity hurtTarget = getLastHurtTarget(user, standPower.getSummonedStandEntity());
        if (hurtTarget != null) {
            UUID hitTargetId = hurtTarget.getUUID();
            if (followTarget == null || followTarget.entityId().equals(hitTargetId)
                    && followTarget.mode() != GETransformationEntity.FollowTargetMode.AGGRO_TRACK) {
                followTarget = new LifeformFollowTarget(hitTargetId, GETransformationEntity.FollowTargetMode.AGGRO_FORGETFUL);
            }
        }
        return followTarget;
    }

    @Nullable
    private static LivingEntity getLastHurtTarget(LivingEntity user, @Nullable StandEntity standEntity) {
        if (standEntity == null) {
            return user.getLastHurtMob();
        }

        LivingEntity userTarget = user.getLastHurtMob();
        LivingEntity standTarget = standEntity.getLastHurtMob();
        if (userTarget == null) {
            return standTarget;
        }
        if (standTarget == null) {
            return userTarget;
        }

        int hurtByUserTime = user.tickCount - user.getLastHurtMobTimestamp();
        int hurtByStandTime = standEntity.tickCount - standEntity.getLastHurtMobTimestamp();
        return hurtByUserTime < hurtByStandTime ? userTarget : standTarget;
    }

    private static void applyFollowTarget(GETransformationEntity transformation,
            @Nullable LifeformFollowTarget followTarget, LivingEntity user) {
        if (followTarget != null) {
            transformation.withFollowTarget(followTarget.entityId(), followTarget.mode(), user);
        }
    }

    public static int getTicksToCreate(LivingEntity user, StandPower standPower, Entity targetEntity) {
        double entityStrength = getAttackStrength(targetEntity);
        float volume = getVolume(targetEntity);
        double standSpeed = 0.0;
        if (standPower != null && standPower.hasPower() && standPower.getPowerType() != null) {
            standSpeed = standPower.getPowerType().getStandStats().speed();
        }

            double value = 240.0 / Math.max(standSpeed, 1.0)
                + Mth.ceil(volume * (1.0 + entityStrength * 0.125)
                        * Mth.clamp(100.0 - standSpeed * 2.0, 0.0, 100.0));
        if (GoldExperienceLifeforms.isNativeLifeform(targetEntity, user.level(), user)) {
            value *= 0.5D;
        }
        return Math.max((int) value, 1);
    }

    public static float getStaminaCostTicking(StandPower standPower, @Nullable Entity lifeform) {
        if (lifeform != null) {
            double entityStrength = getAttackStrength(lifeform);
            float volume = getVolume(lifeform);

            float entityMultiplier = Mth.clamp(volume, 1.0F, 3.0F);
            if (entityStrength > 0.0) {
                entityMultiplier *= Mth.clamp((float) entityStrength, 2.0F, 6.0F) * 0.45F + 0.3F;
            }
            return STAMINA_COST_TICK * entityMultiplier;
        }
        return STAMINA_COST_TICK;
    }

    public static float getVolume(Entity entity) {
        return GoldExperienceLifeforms.getVolume(entity);
    }

    public static double getAttackStrength(Entity entity) {
        return GoldExperienceLifeforms.getAttackStrength(entity);
    }

    private static enum SourceType {
        OFFHAND_ITEM,
        ITEM_ENTITY,
        MARKED_ITEM,
        ENTITY,
        STUCK_ARROW,
        STUCK_KNIFE,
        BLOCK
    }

    private static record LifeformFollowTarget(UUID entityId, GETransformationEntity.FollowTargetMode mode) {}

    private static record LifeformSource(SourceType type, ItemStack sourceItem, @Nullable BlockState sourceBlock,
            @Nullable CompoundTag sourceBlockEntityNbt, @Nullable CompoundTag sourceEntityNbt,
            ItemStack sourceEntityItemView, @Nullable BlockPos sourceBlockPos, @Nullable ItemEntity sourceItemEntity,
            @Nullable Entity sourceEntity, @Nullable ItemTracker markedItemTracker,
            @Nullable LifeformFollowTarget followTarget, @Nullable LivingEntity stuckProjectileHolder) {
        boolean canStillConsume(Level level, LivingEntity user) {
            return switch (type) {
                case OFFHAND_ITEM -> {
                    ItemStack offhand = user.getOffhandItem();
                    yield !offhand.isEmpty() && canGiveLifeTo(offhand);
                }
                case ITEM_ENTITY -> sourceItemEntity != null
                        && sourceItemEntity.isAlive()
                        && !sourceItemEntity.getItem().isEmpty()
                        && canGiveLifeTo(sourceItemEntity.getItem());
                case ENTITY -> sourceEntity != null
                        && sourceEntity.isAlive()
                        && isConvertibleEntitySource(sourceEntity);
                case STUCK_ARROW, STUCK_KNIFE -> markedItemTracker != null
                        && level instanceof ServerLevel serverLevel
                        && markedItemTracker.checkItemIsThere(serverLevel)
                        && stuckProjectileHolder != null
                        && stuckProjectileHolder.isAlive()
                        && hasStuckProjectile(stuckProjectileHolder, type);
                case MARKED_ITEM -> markedItemTracker != null
                        && level instanceof ServerLevel serverLevel
                        && markedItemTracker.checkItemIsThere(serverLevel)
                        && markedItemTracker.getItem() != null
                        && !markedItemTracker.getItem().isEmpty()
                        && canGiveLifeTo(markedItemTracker.getItem());
                case BLOCK -> sourceBlockPos != null
                        && sourceBlock != null
                        && level.getBlockState(sourceBlockPos).is(sourceBlock.getBlock())
                        && isUsableBlockSource(level, user, sourceBlockPos, level.getBlockState(sourceBlockPos));
            };
        }

        boolean consume(Level level, LivingEntity user) {
            return switch (type) {
                case OFFHAND_ITEM -> GoldExperienceHealAbility.spendHealingMaterial(user);
                case ITEM_ENTITY -> {
                    if (sourceItemEntity == null) {
                        yield false;
                    }
                    ItemStack stack = sourceItemEntity.getItem();
                    if (stack.isEmpty() || !canGiveLifeTo(stack)) {
                        yield false;
                    }
                    checkBucketExtraContent(level, user, stack, sourceItemEntity.blockPosition());
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        sourceItemEntity.discard();
                    }
                    else {
                        sourceItemEntity.setItem(stack);
                    }
                    yield true;
                }
                case ENTITY -> {
                    if (sourceEntity == null || !sourceEntity.isAlive() || !isConvertibleEntitySource(sourceEntity)) {
                        yield false;
                    }
                    sourceEntity.discard();
                    yield true;
                }
                case STUCK_ARROW -> consumeStuckProjectile(level, markedItemTracker, stuckProjectileHolder, SourceType.STUCK_ARROW);
                case STUCK_KNIFE -> consumeStuckProjectile(level, markedItemTracker, stuckProjectileHolder, SourceType.STUCK_KNIFE);
                case MARKED_ITEM -> {
                    if (markedItemTracker == null || !(level instanceof ServerLevel serverLevel)
                            || markedItemTracker.getItem() == null
                            || markedItemTracker.getItem().isEmpty()
                            || !canGiveLifeTo(markedItemTracker.getItem())) {
                        yield false;
                    }
                    Vec3 markerPos = markedItemTracker.getPos(level, 1.0F);
                    BlockPos bucketPos = markerPos != null ? BlockPos.containing(markerPos) : user.blockPosition();
                    checkBucketExtraContent(level, user, markedItemTracker.getItem(), bucketPos);
                    ItemStack consumed = markedItemTracker.clearAndCopyItem(serverLevel);
                    yield consumed != null && !consumed.isEmpty() && canGiveLifeTo(consumed);
                }
                case BLOCK -> sourceBlockPos != null
                        && sourceBlock != null
                        && isUsableBlockSource(level, user, sourceBlockPos, level.getBlockState(sourceBlockPos))
                        && GEContainerDropGuard.removeBlockKeepingContainerItems(level, sourceBlockPos,
                                level.getBlockEntity(sourceBlockPos));
            };
        }

        Vec3 spawnPos(LivingEntity performer, LivingEntity user, Entity createdEntity) {
            if (sourceItemEntity != null) {
                return sourceItemEntity.position();
            }
            if (sourceEntity != null) {
                return sourceEntity.position();
            }
            if (stuckProjectileHolder != null) {
                return stuckProjectileHolder.position();
            }
            if (sourceBlockPos != null) {
                return Vec3.atBottomCenterOf(sourceBlockPos);
            }
            if (markedItemTracker != null) {
                Vec3 markerPos = markedItemTracker.getPos(user.level(), 1.0F);
                if (markerPos != null) {
                    return markerPos;
                }
            }
            double distance = createdEntity.getBbWidth() + 1.0;
            Vec3 look = performer.getLookAngle();
            return performer.position().add(look.x * distance, 0.0D, look.z * distance);
        }

        void applyToTransformation(@Nullable GETransformationEntity transformation) {
            if (transformation == null) {
                return;
            }
            if (!sourceItem.isEmpty()) {
                transformation.withSourceItem(sourceItem);
            }
            if (sourceEntityNbt != null) {
                transformation.withSourceEntity(sourceEntityNbt, sourceEntityItemView);
            }
            if (sourceBlock != null) {
                transformation.withSourceBlock(sourceBlock, sourceBlockEntityNbt);
            }
            if (stuckProjectileHolder != null) {
                transformation.withHost(stuckProjectileHolder);
            }
        }

        void applyCustomName(Entity createdEntity) {
            if (!sourceItem.isEmpty() && sourceItem.has(DataComponents.CUSTOM_NAME)) {
                createdEntity.setCustomName(sourceItem.getHoverName());
            }
        }

        @Nullable
        LifeformFollowTarget deliveryFollowTarget(Level level, Entity createdEntity) {
            if (type != SourceType.BLOCK || sourceBlockPos == null || !isPigeonLifeform(createdEntity)) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(sourceBlockPos);
            if (!(blockEntity instanceof Container container)) {
                return null;
            }
            return deliveryTargetFromContainer(level, container)
                    .map(id -> new LifeformFollowTarget(id, GETransformationEntity.FollowTargetMode.DELIVERY))
                    .orElse(null);
        }

        void applyToEffect(GECreatedLifeformEffect effect) {
            if (!sourceItem.isEmpty()) {
                effect.setSourceItem(sourceItem);
            }
            if (sourceEntityNbt != null) {
                effect.setSourceEntity(sourceEntityNbt, sourceEntityItemView);
            }
            if (sourceBlock != null) {
                effect.setSourceBlock(sourceBlock, sourceBlockEntityNbt);
            }
        }

        @Nullable
        Entity nonUserItemHolder(Level level, LivingEntity user) {
            if (type != SourceType.MARKED_ITEM || markedItemTracker == null
                    || markedItemTracker.getItemState() != KnownItemState.ENTITY_HAS_ITEM) {
                return null;
            }
            Entity holder = markedItemTracker.getAtEntity(level);
            return holder != null && holder != user ? holder : null;
        }
    }

    private static boolean isPigeonLifeform(Entity createdEntity) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(createdEntity.getType());
        return entityId != null && entityId.getPath().contains("pigeon");
    }

    private static Optional<UUID> deliveryTargetFromContainer(Level level, Container container) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack item = container.getItem(slot);
            if (!item.isEmpty() && item.is(Items.NAME_TAG) && item.has(DataComponents.CUSTOM_NAME)) {
                String name = item.getHoverName().getString();
                if (name.isBlank()) {
                    continue;
                }
                ServerPlayer onlinePlayer = serverLevel.getServer().getPlayerList().getPlayerByName(name);
                return Optional.of(onlinePlayer != null ? onlinePlayer.getUUID() : UUIDUtil.createOfflinePlayerUUID(name));
            }
        }
        return Optional.empty();
    }

    private static boolean consumeStuckProjectile(Level level, @Nullable ItemTracker markedItemTracker,
            @Nullable LivingEntity holder, SourceType sourceType) {
        if (!(level instanceof ServerLevel serverLevel) || markedItemTracker == null || holder == null
                || !holder.isAlive() || !markedItemTracker.checkItemIsThere(serverLevel)
                || !hasStuckProjectile(holder, sourceType)) {
            return false;
        }
        switch (sourceType) {
            case STUCK_ARROW -> holder.setArrowCount(Math.max(holder.getArrowCount() - 1, 0));
            case STUCK_KNIFE -> {
                if (!GEStuckObjectsState.get(holder).decrementStuckKnife()) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        ItemStack consumed = markedItemTracker.clearAndCopyItem(serverLevel);
        return consumed != null && !consumed.isEmpty();
    }

    private static boolean hasStuckProjectile(LivingEntity holder, SourceType sourceType) {
        return switch (sourceType) {
            case STUCK_ARROW -> holder.getArrowCount() > 0;
            case STUCK_KNIFE -> GEStuckObjectsState.get(holder).getStuckKnives() > 0;
            default -> false;
        };
    }
}
