package rotp.addons.harvest.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.HarvestSelectionPredicates;
import rotp.addons.harvest.ability.HarvestCommandAbility.Command;
import rotp.addons.harvest.stand.HarvestStandData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HarvestStandEntity extends StandEntity {
    public static final int MEMBER_CAP = 27;
    public static final int SPAWNS_PER_TICK = 3;
    public static final int COMMAND_ENTITY_RANGE = 7;
    public static final int COMMAND_BLOCK_RANGE = 15;
    public static final double CARRY_UP_RANGE = 5.0D;
    public static final double MAX_OWNER_DISTANCE = 32.0D;
    // 1.16 HarvestFindAndCollectItemGoal searched a 200-block box.
    public static final double ITEM_SEARCH_RADIUS = 200.0D;
    // Scanned first so the capped scan keeps nearby items.
    public static final double ITEM_NEAR_SEARCH_RADIUS = 24.0D;
    public static final int ITEM_SCAN_LIMIT = 32;
    public static final int ITEM_SCAN_INTERVAL_TICKS = 20;
    public static final double AUTO_TARGET_RADIUS = 16.0D;
    public static final int AUTO_TARGET_SCAN_LIMIT = 32;
    public static final int AUTO_TARGET_SCAN_INTERVAL_TICKS = 15;
    public static final int COMMAND_VALIDATION_INTERVAL_TICKS = 10;
    public static final int MEMBER_RESPAWN_DELAY_TICKS = 20;
    public static final float MEMBER_RESPAWN_STAMINA = 300.0F;
    public static final int POTION_USES_PER_BOTTLE = 4;
    public static final int MISSING_MEMBER_GRACE_TICKS = 40;
    private static final String POTION_USES_TAG =
            "RotpHarvestPotionUses";

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte>
            COMMAND_MODE = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean>
            CLOSE_FORMATION = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>>
            COMMAND_TARGET = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos>
            COMMAND_DESTINATION = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLED_MEMBER_UUID = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            CONTROLLED_MEMBER_ID = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            CARRY_UP_MEMBER_UUID = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            CARRY_UP_MEMBER_ID = SynchedEntityData.defineId(
                    HarvestStandEntity.class,
                    EntityDataSerializers.INT);

    private static final Set<ResourceLocation> ALLOWED_SKINS =
            Set.of(
                    HarvestAddon.id("rabbit"),
                    HarvestAddon.id("purple"),
                    HarvestAddon.id("blue"),
                    HarvestAddon.id("red"),
                    HarvestAddon.id("ryomen_sukuna"),
                    HarvestAddon.id("sukuna_itadori"),
                    HarvestAddon.id("sukuna_megumi"));

    private final Map<Integer, UUID> childUuids = new HashMap<>();
    private final long[] pendingRespawnAt = new long[MEMBER_CAP];
    private final boolean[] permanentlyLost = new boolean[MEMBER_CAP];
    private final int[] missingMemberTicks = new int[MEMBER_CAP];
    @Nullable
    private UUID automaticTargetUuid;
    private int lastOwnerHurtMobTimestamp;
    private int lastOwnerHurtByMobTimestamp;
    private int nextInitialSlot;
    private boolean cleaningUp;

    public HarvestStandEntity(
            EntityType<? extends HarvestStandEntity> type,
            Level level) {
        super(type, level);
        // 1.16 HarvestStandEntity.offsetDefault (0, 1.0, 0); its y was also
        // the y of every task offset that set none.
        Y_OFFSET = 1.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
        noPhysics = true;
        setInvisible(true);
        Arrays.fill(pendingRespawnAt, -1L);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(
                COMMAND_MODE,
                (byte) HarvestMemberMode.FOLLOW.ordinal());
        builder.define(CLOSE_FORMATION, false);
        builder.define(COMMAND_TARGET, Optional.empty());
        builder.define(COMMAND_DESTINATION, BlockPos.ZERO);
        builder.define(CONTROLLED_MEMBER_UUID, Optional.empty());
        builder.define(CONTROLLED_MEMBER_ID, -1);
        builder.define(CARRY_UP_MEMBER_UUID, Optional.empty());
        builder.define(CARRY_UP_MEMBER_ID, -1);
    }

    @Override
    public void setUserAndPower(
            LivingEntity user, StandPower power) {
        super.setUserAndPower(user, power);
        if (!level().isClientSide() && user != null) {
            entityData.set(
                    OWNER_UUID, Optional.of(user.getUUID()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setInvisible(true);
        if (isRemoved()
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        LivingEntity owner = getUser();
        if (owner != null
                && entityData.get(OWNER_UUID).isEmpty()) {
            entityData.set(
                    OWNER_UUID, Optional.of(owner.getUUID()));
        }
        if (!isActiveFor(owner)) {
            cleanupDependents();
            discard();
            return;
        }
        followUserSwarmState(owner);

        int spawnBudget = SPAWNS_PER_TICK;
        while (nextInitialSlot < MEMBER_CAP && spawnBudget > 0) {
            if (!spawnMember(
                    serverLevel, owner, nextInitialSlot)) {
                break;
            }
            nextInitialSlot++;
            spawnBudget--;
        }
        if (spawnBudget > 0) {
            processRespawns(serverLevel, owner, spawnBudget);
        }
        maintainCarryUp(owner, serverLevel);

        if (tickCount % COMMAND_VALIDATION_INTERVAL_TICKS == 0) {
            validateCommand(owner, serverLevel);
            auditMemberBindings(serverLevel);
            synchronizeSkin();
        }
        updateAutomaticTarget(serverLevel, owner);
        if (commandMode() == HarvestMemberMode.SEARCH
                && tickCount % ITEM_SCAN_INTERVAL_TICKS == 0) {
            assignSearchTargets(serverLevel, owner);
        }
    }

    public boolean isActiveFor(@Nullable LivingEntity owner) {
        if (owner == null
                || !owner.isAlive()
                || owner.level() != level()
                || !isOwnedBy(owner.getUUID())) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == HarvestAddon.HARVEST_STAND.get()
                && power.getSummonedStandEntity() == this;
    }

    public boolean isOwnedBy(@Nullable UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    public HarvestMemberMode commandMode() {
        return HarvestMemberMode.byId(
                entityData.get(COMMAND_MODE));
    }

    public boolean closeFormation() {
        return entityData.get(CLOSE_FORMATION);
    }

    public Optional<UUID> commandTarget() {
        return entityData.get(COMMAND_TARGET);
    }

    public BlockPos commandDestination() {
        return entityData.get(COMMAND_DESTINATION);
    }

    public Optional<UUID> controlledMemberUuid() {
        return entityData.get(CONTROLLED_MEMBER_UUID);
    }

    public int controlledMemberId() {
        return entityData.get(CONTROLLED_MEMBER_ID);
    }

    public Optional<UUID> carryUpMemberUuid() {
        return entityData.get(CARRY_UP_MEMBER_UUID);
    }

    public int carryUpMemberId() {
        return entityData.get(CARRY_UP_MEMBER_ID);
    }

    public boolean isCarryUpActive() {
        return carryUpMemberUuid().isPresent();
    }

    public boolean isCarryUpMember(HarvestMemberEntity member) {
        return member != null
                && carryUpMemberUuid()
                        .filter(member.getUUID()::equals)
                        .isPresent();
    }

    public void executeCommand(
            Command command, LivingEntity owner) {
        if (level().isClientSide() || !isActiveFor(owner)) {
            return;
        }
        switch (command) {
            case SET_TARGET -> commandSetTarget(owner);
            case FORGET_TARGET -> commandRecall();
            case GO_TO -> commandGoTo(owner);
            case STAY_WITH -> commandStayWith(owner);
            case SEARCH -> commandSearch();
            case BE_CLOSER -> toggleCloser(owner);
            case GIVE_POTION -> commandGivePotion(owner);
            case REMOVE_POTION -> commandRemovePotion(owner);
            case CARRY_UP -> commandCarryUp(owner);
            case CONTROL_HARVEST -> commandControlHarvest(owner);
        }
    }

    public boolean canExecuteCommand(
            Command command, LivingEntity owner) {
        if (!isActiveFor(owner)) {
            return false;
        }
        return switch (command) {
            case SET_TARGET -> {
                LivingEntity target = resolveLookTarget(owner, true);
                yield target != null
                        && JojoModUtil.canHarm(owner, target);
            }
            case GO_TO -> blockLookTarget(owner) != null;
            case STAY_WITH ->
                    resolveLookTarget(owner, false) != null;
            case GIVE_POTION -> {
                HarvestMemberEntity member =
                        resolveOwnedMemberLookTarget(owner);
                int effectCount = potionEffectCount(owner);
                yield member != null
                        && member.canAcceptPotionPayload(
                                effectCount);
            }
            case REMOVE_POTION -> {
                HarvestMemberEntity member =
                        resolveOwnedMemberLookTarget(owner);
                yield member != null
                        && member.potionPayloadCount() > 0;
            }
            case CONTROL_HARVEST ->
                    owner instanceof Player
                            && (controlledMemberUuid().isPresent()
                                    || resolveOwnedMemberLookTarget(
                                            owner) != null);
            case CARRY_UP -> owner instanceof Player;
            case FORGET_TARGET, SEARCH, BE_CLOSER -> true;
        };
    }

    private void commandSetTarget(LivingEntity owner) {
        LivingEntity target = resolveLookTarget(owner, true);
        if (target == null
                || !JojoModUtil.canHarm(owner, target)) {
            return;
        }
        setCommand(
                HarvestMemberMode.ATTACK,
                Optional.of(target.getUUID()),
                BlockPos.ZERO);
        dispatchToMembers(member ->
                member.attackTarget(target.getUUID()));
    }

    private void commandRecall() {
        automaticTargetUuid = null;
        setCommand(
                HarvestMemberMode.FOLLOW,
                Optional.empty(),
                BlockPos.ZERO);
        dispatchToMembers(HarvestMemberEntity::recall);
    }

    // 1.16's STAYING status (HarvestStandType.unsummonStand :75-79) ended an
    // attack or a search; a go-to or a stay-with was STAYING already.
    public void resetSwarmStatus() {
        if (level().isClientSide()) {
            return;
        }
        HarvestMemberMode mode = commandMode();
        if (mode == HarvestMemberMode.ATTACK
                || mode == HarvestMemberMode.SEARCH) {
            commandRecall();
        }
    }

    private void commandGoTo(LivingEntity owner) {
        BlockHitResult blockHit = blockLookTarget(owner);
        if (blockHit == null) {
            return;
        }
        BlockPos destination = blockHit.getBlockPos()
                .relative(blockHit.getDirection());
        setCommand(
                HarvestMemberMode.GO_TO,
                Optional.empty(),
                destination);
        dispatchToMembers(member ->
                member.goTo(destination));
    }

    private void commandStayWith(LivingEntity owner) {
        LivingEntity target = resolveLookTarget(owner, false);
        if (target == null) {
            return;
        }
        setCommand(
                HarvestMemberMode.STAY_WITH,
                Optional.of(target.getUUID()),
                BlockPos.ZERO);
        dispatchToMembers(member ->
                member.stayWith(target.getUUID()));
    }

    private void commandSearch() {
        setCommand(
                HarvestMemberMode.SEARCH,
                Optional.empty(),
                BlockPos.ZERO);
        dispatchToMembers(HarvestMemberEntity::search);
    }

    // 1.16 BeCloser.perform :255-259 toggled the flag on the user
    private void toggleCloser(LivingEntity owner) {
        HarvestStandData data = HarvestStandData.of(owner);
        boolean closer = !(data != null
                ? data.isCloser()
                : closeFormation());
        if (data != null) {
            data.setCloser(owner, closer);
        }
        applyCloseFormation(closer);
    }

    private void applyCloseFormation(boolean closer) {
        entityData.set(CLOSE_FORMATION, closer);
        dispatchToMembers(member ->
                member.setCloseFormation(closer));
    }

    // 1.16 HarvestMainEntity.tick :242-249: every Harvest took the swarm
    // status and Be Closer from its owner on each tick, so both outlived an
    // unsummon.
    private void followUserSwarmState(LivingEntity owner) {
        HarvestStandData data = HarvestStandData.of(owner);
        if (data == null) {
            return;
        }
        if (data.isCloser() != closeFormation()) {
            applyCloseFormation(data.isCloser());
        }
        if (data.takeSearchLeftStandless()) {
            commandSearch();
        }
    }

    private void commandGivePotion(LivingEntity owner) {
        HarvestMemberEntity member =
                resolveOwnedMemberLookTarget(owner);
        if (member == null) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack potion = owner.getItemInHand(hand);
            if (!potion.is(Items.POTION)) {
                continue;
            }
            PotionContents contents =
                    potion.get(DataComponents.POTION_CONTENTS);
            if (contents == null || !contents.hasEffects()) {
                return;
            }
            List<MobEffectInstance> effects = new ArrayList<>();
            for (MobEffectInstance effect
                    : contents.getAllEffects()) {
                effects.add(new MobEffectInstance(effect));
            }
            if (!member.tryAddPotionPayload(effects)) {
                return;
            }
            int uses = Mth.clamp(
                    potion.getOrDefault(
                                    DataComponents.CUSTOM_DATA,
                                    CustomData.EMPTY)
                            .copyTag()
                            .getInt(POTION_USES_TAG),
                    0,
                    POTION_USES_PER_BOTTLE - 1) + 1;
            if (uses >= POTION_USES_PER_BOTTLE) {
                owner.setItemInHand(
                        hand, new ItemStack(Items.GLASS_BOTTLE));
            }
            else {
                CustomData.update(
                        DataComponents.CUSTOM_DATA,
                        potion,
                        tag -> tag.putInt(POTION_USES_TAG, uses));
            }
            return;
        }
    }

    private void commandRemovePotion(LivingEntity owner) {
        HarvestMemberEntity member =
                resolveOwnedMemberLookTarget(owner);
        if (member != null) {
            member.clearPotionPayload();
        }
    }

    private void commandCarryUp(LivingEntity owner) {
        if (!(owner instanceof Player player)) {
            deactivateCarryUp(owner);
            return;
        }
        double rangeSqr = CARRY_UP_RANGE * CARRY_UP_RANGE;
        List<HarvestMemberEntity> candidates = activeMembers().stream()
                .filter(member ->
                        owner.distanceToSqr(member) <= rangeSqr)
                .toList();
        if (candidates.isEmpty()) {
            deactivateCarryUp(player);
            return;
        }
        if (isCarryUpActive()) {
            deactivateCarryUp(player);
            return;
        }

        HarvestMemberEntity selected = candidates.get(
                owner.getRandom().nextInt(candidates.size()));
        entityData.set(
                CARRY_UP_MEMBER_UUID,
                Optional.of(selected.getUUID()));
        entityData.set(CARRY_UP_MEMBER_ID, selected.getId());
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.startRiding(selected);
        player.setForcedPose(Pose.SWIMMING);
    }

    private void maintainCarryUp(
            LivingEntity owner, ServerLevel level) {
        UUID selectedUuid = carryUpMemberUuid().orElse(null);
        if (selectedUuid == null) {
            return;
        }
        Entity entity = level.getEntity(selectedUuid);
        boolean valid =
                owner instanceof Player
                        && entity
                                instanceof HarvestMemberEntity member
                        && member.isAlive()
                        && member.isOwnedBy(owner.getUUID())
                        && member.isBoundTo(getUUID())
                        && selectedUuid.equals(
                                childUuids.get(member.slot()));
        if (!valid) {
            deactivateCarryUp(owner);
            return;
        }
        HarvestMemberEntity member =
                (HarvestMemberEntity) entity;
        if (carryUpMemberId() != member.getId()) {
            entityData.set(CARRY_UP_MEMBER_ID, member.getId());
        }
        ((Player) owner).setForcedPose(Pose.SWIMMING);
    }

    private void deactivateCarryUp(
            @Nullable LivingEntity owner) {
        boolean wasActive = isCarryUpActive();
        if (owner instanceof Player player) {
            if (player.getVehicle()
                    instanceof HarvestMemberEntity member
                    && member.isBoundTo(getUUID())) {
                player.stopRiding();
                player.setSwimming(false);
            }
            if (wasActive
                    && player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
        }
        entityData.set(CARRY_UP_MEMBER_UUID, Optional.empty());
        entityData.set(CARRY_UP_MEMBER_ID, -1);
        dispatchToMembers(member ->
                member.setCarryingUp(false));
    }

    private void commandControlHarvest(LivingEntity owner) {
        if (!(owner instanceof Player)) {
            clearControlledMember();
            return;
        }
        if (controlledMemberUuid().isPresent()) {
            clearControlledMember();
            return;
        }
        HarvestMemberEntity member =
                resolveOwnedMemberLookTarget(owner);
        if (member != null) {
            entityData.set(
                    CONTROLLED_MEMBER_UUID,
                    Optional.of(member.getUUID()));
            entityData.set(CONTROLLED_MEMBER_ID, member.getId());
        }
    }

    private void clearControlledMember() {
        entityData.set(CONTROLLED_MEMBER_UUID, Optional.empty());
        entityData.set(CONTROLLED_MEMBER_ID, -1);
    }

    @Nullable
    public LivingEntity previewLookTarget(
            LivingEntity owner, boolean hostileTarget) {
        return resolveLookTarget(owner, hostileTarget);
    }

    @Nullable
    public BlockHitResult previewBlockLookTarget(
            LivingEntity owner) {
        return blockLookTarget(owner);
    }

    private void setCommand(
            HarvestMemberMode mode,
            Optional<UUID> target,
            BlockPos destination) {
        entityData.set(COMMAND_MODE, (byte) mode.ordinal());
        entityData.set(
                COMMAND_TARGET,
                target != null ? target : Optional.empty());
        entityData.set(
                COMMAND_DESTINATION,
                destination != null ? destination : BlockPos.ZERO);
    }

    // 1.16 HarvestUtil.getEntityByRayTrace: the 7-block entity ray of
    // set_target and stay_with ran through blocks.
    @Nullable
    private LivingEntity resolveLookTarget(
            LivingEntity owner, boolean hostileTarget) {
        AbilityId command = new AbilityId(
                PowerClass.STAND,
                HarvestAddon.HARVEST_STAND.getId(),
                hostileTarget
                        ? HarvestAddon.SET_TARGET_NAME
                        : HarvestAddon.STAY_WITH_NAME);
        ActionTarget target = HitResultUtil.clip(
                owner.getEyePosition(),
                owner.getLookAngle(),
                COMMAND_ENTITY_RANGE,
                COMMAND_ENTITY_RANGE,
                owner.level(),
                entity -> entity instanceof LivingEntity
                        && entity != owner
                        && !(entity instanceof HarvestMemberEntity)
                        && !(entity instanceof StandEntity),
                owner,
                0.0D,
                StandSkillWalls.wallsHideEntities(
                        owner.level(), command));
        Entity entity = target.getMainEntity();
        if (!(entity instanceof LivingEntity living)
                || !living.isAlive()) {
            return null;
        }
        return !hostileTarget || !living.isAlliedTo(owner)
                ? living
                : null;
    }

    @Nullable
    private BlockHitResult blockLookTarget(LivingEntity owner) {
        HitResult hit = owner.pick(
                COMMAND_BLOCK_RANGE, 1.0F, false);
        return hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK
                        ? blockHit
                        : null;
    }

    @Nullable
    private HarvestMemberEntity resolveOwnedMemberLookTarget(
            LivingEntity owner) {
        ActionTarget target = HitResultUtil.clip(
                owner.getEyePosition(),
                owner.getLookAngle(),
                COMMAND_ENTITY_RANGE,
                COMMAND_ENTITY_RANGE,
                owner.level(),
                entity -> entity instanceof HarvestMemberEntity member
                        && member.isAlive()
                        && member.isOwnedBy(owner.getUUID())
                        && member.isBoundTo(getUUID())
                        && HarvestSelectionPredicates
                                .isValidMemberSlot(
                                        member.slot(), MEMBER_CAP),
                owner,
                0.2D);
        Entity entity = target.getMainEntity();
        if (!(entity instanceof HarvestMemberEntity member)
                || owner.distanceToSqr(member)
                        > COMMAND_ENTITY_RANGE
                                * COMMAND_ENTITY_RANGE) {
            return null;
        }
        UUID expected = childUuids.get(member.slot());
        return HarvestSelectionPredicates.memberUuidMatches(
                        level().isClientSide(),
                        expected,
                        member.getUUID())
                ? member
                : null;
    }

    private int potionEffectCount(LivingEntity owner) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack potion = owner.getItemInHand(hand);
            if (!potion.is(Items.POTION)) {
                continue;
            }
            PotionContents contents =
                    potion.get(DataComponents.POTION_CONTENTS);
            if (contents == null || !contents.hasEffects()) {
                return 0;
            }
            int count = 0;
            for (MobEffectInstance ignored
                    : contents.getAllEffects()) {
                count++;
            }
            return count;
        }
        return 0;
    }

    private void validateCommand(
            LivingEntity owner, ServerLevel level) {
        HarvestMemberMode mode = commandMode();
        if (mode == HarvestMemberMode.ATTACK
                || mode == HarvestMemberMode.STAY_WITH) {
            Entity target = commandTarget()
                    .map(level::getEntity)
                    .orElse(null);
            boolean invalid = !(target instanceof LivingEntity living)
                    || !living.isAlive()
                    || living.level() != level
                    || owner.distanceToSqr(living)
                            > MAX_OWNER_DISTANCE
                                    * MAX_OWNER_DISTANCE;
            if (invalid) {
                commandRecall();
            }
        }
        else if (mode == HarvestMemberMode.GO_TO) {
            Vec3 destination =
                    Vec3.atCenterOf(commandDestination());
            if (owner.position().distanceToSqr(destination)
                    > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
                commandRecall();
            }
        }
        controlledMemberUuid().ifPresent(uuid -> {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof HarvestMemberEntity member)
                    || !member.isAlive()
                    || !member.isOwnedBy(owner.getUUID())
                    || !member.isBoundTo(getUUID())
                    || owner.distanceToSqr(member)
                            > MAX_OWNER_DISTANCE
                                    * MAX_OWNER_DISTANCE
                    || !uuid.equals(childUuids.get(member.slot()))) {
                clearControlledMember();
            }
            else if (entityData.get(CONTROLLED_MEMBER_ID)
                    != member.getId()) {
                entityData.set(
                        CONTROLLED_MEMBER_ID, member.getId());
            }
        });
        if (isCarryUpActive()) {
            maintainCarryUp(owner, level);
        }
    }

    private void updateAutomaticTarget(
            ServerLevel level, LivingEntity owner) {
        if (commandMode() != HarvestMemberMode.FOLLOW) {
            automaticTargetUuid = null;
            lastOwnerHurtMobTimestamp =
                    owner.getLastHurtMobTimestamp();
            lastOwnerHurtByMobTimestamp =
                    owner.getLastHurtByMobTimestamp();
            return;
        }

        LivingEntity retaliation = null;
        int hurtMobTimestamp = owner.getLastHurtMobTimestamp();
        if (hurtMobTimestamp != lastOwnerHurtMobTimestamp) {
            lastOwnerHurtMobTimestamp = hurtMobTimestamp;
            retaliation = owner.getLastHurtMob();
        }
        int hurtByTimestamp = owner.getLastHurtByMobTimestamp();
        if (hurtByTimestamp != lastOwnerHurtByMobTimestamp) {
            lastOwnerHurtByMobTimestamp = hurtByTimestamp;
            if (hurtByTimestamp >= hurtMobTimestamp) {
                retaliation = owner.getLastHurtByMob();
            }
        }
        if (isValidAutomaticTarget(owner, retaliation)) {
            automaticTargetUuid = retaliation.getUUID();
            return;
        }

        LivingEntity current = resolveAutomaticTarget(owner);
        if (current != null
                || tickCount % AUTO_TARGET_SCAN_INTERVAL_TICKS
                        != 0) {
            return;
        }

        List<LivingEntity> candidates =
                new ArrayList<>(AUTO_TARGET_SCAN_LIMIT);
        AABB searchBox = owner.getBoundingBox()
                .inflate(AUTO_TARGET_RADIUS);
        level.getEntities().get(
                EntityTypeTest.forClass(LivingEntity.class),
                searchBox,
                candidate -> {
                    if (candidate instanceof Enemy
                            && isValidAutomaticTarget(
                                    owner, candidate)) {
                        candidates.add(candidate);
                    }
                    return candidates.size()
                                    >= AUTO_TARGET_SCAN_LIMIT
                            ? Continuation.ABORT
                            : Continuation.CONTINUE;
                });
        automaticTargetUuid = candidates.stream()
                .min(Comparator.comparingDouble(
                        owner::distanceToSqr))
                .map(Entity::getUUID)
                .orElse(null);
    }

    @Nullable
    public LivingEntity resolveAutomaticTarget(
            LivingEntity owner) {
        if (!(level() instanceof ServerLevel level)
                || automaticTargetUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(automaticTargetUuid);
        if (entity instanceof LivingEntity living
                && isValidAutomaticTarget(owner, living)) {
            return living;
        }
        automaticTargetUuid = null;
        return null;
    }

    private boolean isValidAutomaticTarget(
            LivingEntity owner,
            @Nullable LivingEntity candidate) {
        return candidate != null
                && candidate != owner
                && candidate.isAlive()
                && candidate.level() == level()
                && owner.distanceToSqr(candidate)
                        <= MAX_OWNER_DISTANCE
                                * MAX_OWNER_DISTANCE
                && !candidate.isAlliedTo(owner)
                && !candidate.isInvulnerable()
                && !(candidate instanceof Player player
                        && player.getAbilities().invulnerable)
                && JojoModUtil.canHarm(owner, candidate);
    }

    public void onTargetDefeated(UUID targetUuid) {
        if (targetUuid == null || level().isClientSide()) {
            return;
        }
        if (targetUuid.equals(automaticTargetUuid)) {
            automaticTargetUuid = null;
        }
        if (commandMode() == HarvestMemberMode.ATTACK
                && commandTarget()
                        .filter(targetUuid::equals)
                        .isPresent()) {
            commandRecall();
        }
    }

    private void auditMemberBindings(ServerLevel level) {
        long gameTime = level.getGameTime();
        for (int slot = 0; slot < MEMBER_CAP; slot++) {
            UUID uuid = childUuids.get(slot);
            if (uuid == null) {
                missingMemberTicks[slot] = 0;
                continue;
            }
            Entity entity = level.getEntity(uuid);
            if (entity instanceof HarvestMemberEntity member
                    && member.isAlive()
                    && member.isBoundTo(getUUID())
                    && member.slot() == slot) {
                missingMemberTicks[slot] = 0;
                continue;
            }
            missingMemberTicks[slot] +=
                    COMMAND_VALIDATION_INTERVAL_TICKS;
            if (missingMemberTicks[slot]
                    < MISSING_MEMBER_GRACE_TICKS) {
                continue;
            }
            childUuids.remove(slot);
            missingMemberTicks[slot] = 0;
            if (!permanentlyLost[slot]
                    && pendingRespawnAt[slot] < 0L) {
                pendingRespawnAt[slot] = gameTime;
            }
        }
    }

    private void applyCurrentCommand(HarvestMemberEntity member) {
        member.setCloseFormation(closeFormation());
        member.setCarryingUp(
                isCarryUpActive()
                        && member.canUseCarryUpBehavior());
        switch (commandMode()) {
            case ATTACK -> commandTarget()
                    .ifPresentOrElse(
                            member::attackTarget,
                            member::recall);
            case GO_TO -> member.stayAtDestination(commandDestination());
            case STAY_WITH -> commandTarget()
                    .ifPresentOrElse(
                            member::stayWith,
                            member::recall);
            case SEARCH, RETURN_ITEM -> member.search();
            case FOLLOW -> member.recall();
        }
    }

    private boolean spawnMember(
            ServerLevel level,
            LivingEntity owner,
            int slot) {
        if (slot < 0 || slot >= MEMBER_CAP) {
            return false;
        }
        HarvestMemberEntity member =
                HarvestAddon.HARVEST_MEMBER.get().create(level);
        if (member == null) {
            return false;
        }
        member.bindClosure(
                owner.getUUID(),
                this,
                slot,
                normalizeSkin(getStandSkin()));
        Vec3 position = HarvestMemberEntity.formationPosition(
                owner, slot, closeFormation());
        member.moveTo(
                position.x,
                position.y,
                position.z,
                owner.getYRot(),
                0.0F);
        applyCurrentCommand(member);
        if (level.addFreshEntity(member)) {
            childUuids.put(slot, member.getUUID());
            pendingRespawnAt[slot] = -1L;
            missingMemberTicks[slot] = 0;
            return true;
        }
        return false;
    }

    private int processRespawns(
            ServerLevel level,
            LivingEntity owner,
            int spawnBudget) {
        long gameTime = level.getGameTime();
        for (int slot = 0;
                slot < MEMBER_CAP && spawnBudget > 0;
                slot++) {
            if (!permanentlyLost[slot]
                    && pendingRespawnAt[slot] >= 0L
                    && gameTime >= pendingRespawnAt[slot]) {
                spawnMember(level, owner, slot);
                spawnBudget--;
            }
        }
        return spawnBudget;
    }

    public void onMemberDeath(
            int slot, UUID memberUuid) {
        if (!(level() instanceof ServerLevel level)
                || slot < 0
                || slot >= MEMBER_CAP
                || !memberUuid.equals(childUuids.get(slot))
                || cleaningUp) {
            return;
        }
        childUuids.remove(slot);
        missingMemberTicks[slot] = 0;
        if (controlledMemberUuid()
                .filter(memberUuid::equals)
                .isPresent()) {
            clearControlledMember();
        }
        if (carryUpMemberUuid()
                .filter(memberUuid::equals)
                .isPresent()) {
            deactivateCarryUp(getUser());
        }
        LivingEntity owner = getUser();
        StandPower power =
                owner != null ? StandPower.get(owner) : null;
        if (owner != null
                && isActiveFor(owner)
                && power != null
                && power.consumeStamina(
                        MEMBER_RESPAWN_STAMINA)) {
            pendingRespawnAt[slot] =
                    level.getGameTime()
                            + MEMBER_RESPAWN_DELAY_TICKS;
        }
        else {
            permanentlyLost[slot] = true;
            pendingRespawnAt[slot] = -1L;
        }
    }

    private void assignSearchTargets(
            ServerLevel level, LivingEntity owner) {
        List<HarvestMemberEntity> available = activeMembers().stream()
                .filter(member ->
                        member.getMode() == HarvestMemberMode.SEARCH)
                .filter(member ->
                        member.assignedItemUuid().isEmpty())
                .filter(member ->
                        member.getMainHandItem().isEmpty())
                .sorted(Comparator.comparingInt(
                        HarvestMemberEntity::slot))
                .toList();
        if (available.isEmpty()) {
            return;
        }

        Set<UUID> assigned = new HashSet<>();
        activeMembers().forEach(member ->
                member.assignedItemUuid().ifPresent(assigned::add));

        List<ItemEntity> candidates =
                new ArrayList<>(ITEM_SCAN_LIMIT);
        scanItems(level,
                owner.getBoundingBox()
                        .inflate(ITEM_NEAR_SEARCH_RADIUS),
                assigned,
                candidates);
        // Widen to the 1.16 box only when the near ring runs short.
        if (candidates.size() < available.size()) {
            scanItems(level,
                    owner.getBoundingBox()
                            .inflate(ITEM_SEARCH_RADIUS),
                    assigned,
                    candidates);
        }
        candidates.sort(Comparator.comparingDouble(
                owner::distanceToSqr));

        int count = Math.min(
                available.size(), candidates.size());
        for (int i = 0; i < count; i++) {
            available.get(i).assignSearchTarget(
                    candidates.get(i).getUUID());
        }
    }

    // skip holds claimed and already listed items (no duplicates).
    private static void scanItems(
            ServerLevel level,
            AABB searchBox,
            Set<UUID> skip,
            List<ItemEntity> candidates) {
        if (candidates.size() >= ITEM_SCAN_LIMIT) {
            return;
        }
        level.getEntities().get(
                EntityTypeTest.forClass(ItemEntity.class),
                searchBox,
                item -> {
                    if (item.isAlive()
                            && !item.getItem().isEmpty()
                            && !item.getPersistentData()
                                    .getBoolean(
                                            HarvestMemberEntity
                                                    .RETURNED_ITEM_TAG)
                            && skip.add(item.getUUID())) {
                        candidates.add(item);
                    }
                    return candidates.size() >= ITEM_SCAN_LIMIT
                            ? Continuation.ABORT
                            : Continuation.CONTINUE;
                });
    }

    private List<HarvestMemberEntity> activeMembers() {
        if (!(level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<HarvestMemberEntity> members =
                new ArrayList<>(MEMBER_CAP);
        for (int slot = 0; slot < MEMBER_CAP; slot++) {
            UUID uuid = childUuids.get(slot);
            Entity entity = uuid != null
                    ? level.getEntity(uuid)
                    : null;
            if (entity instanceof HarvestMemberEntity member
                    && member.isAlive()
                    && member.isBoundTo(getUUID())
                    && member.slot() == slot) {
                members.add(member);
            }
        }
        return members;
    }

    private void dispatchToMembers(
            Consumer<HarvestMemberEntity> command) {
        activeMembers().forEach(command);
    }

    private void synchronizeSkin() {
        Optional<ResourceLocation> skin =
                normalizeSkin(getStandSkin());
        activeMembers().forEach(member ->
                member.setSelectedSkin(skin));
    }

    private static Optional<ResourceLocation> normalizeSkin(
            Optional<ResourceLocation> skin) {
        if (skin == null || skin.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = skin.get();
        return ALLOWED_SKINS.contains(id)
                ? Optional.of(id)
                : Optional.empty();
    }

    @Override
    public void setSelectedSkin(
            Optional<ResourceLocation> skin) {
        Optional<ResourceLocation> normalized =
                normalizeSkin(skin);
        super.setSelectedSkin(normalized);
        if (!level().isClientSide()) {
            dispatchToMembers(member ->
                    member.setSelectedSkin(normalized));
        }
    }

    public void cleanupDependents() {
        if (cleaningUp) {
            return;
        }
        cleaningUp = true;
        deactivateCarryUp(getUser());
        if (level() instanceof ServerLevel level) {
            for (UUID uuid : List.copyOf(
                    childUuids.values())) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof HarvestMemberEntity member
                        && member.isBoundTo(getUUID())) {
                    member.releaseCarriedItem();
                    member.discard();
                }
            }
        }
        childUuids.clear();
        automaticTargetUuid = null;
        clearControlledMember();
        Arrays.fill(pendingRespawnAt, -1L);
        Arrays.fill(missingMemberTicks, 0);
        cleaningUp = false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !cleaningUp) {
            cleanupDependents();
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID).ifPresent(uuid ->
                tag.putUUID("HarvestOwner", uuid));
        tag.putByte(
                "HarvestCommandMode",
                entityData.get(COMMAND_MODE));
        tag.putBoolean(
                "HarvestCloseFormation",
                closeFormation());
        commandTarget().ifPresent(uuid ->
                tag.putUUID("HarvestCommandTarget", uuid));
        tag.putLong(
                "HarvestCommandDestination",
                commandDestination().asLong());
        controlledMemberUuid().ifPresent(uuid ->
                tag.putUUID("HarvestControlledMember", uuid));
        carryUpMemberUuid().ifPresent(uuid ->
                tag.putUUID("HarvestCarryUpMember", uuid));
        getStandSkin().ifPresent(skin ->
                tag.putString(
                        "HarvestSelectedSkin", skin.toString()));

        ListTag children = new ListTag();
        childUuids.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MEMBER_CAP)
                .forEach(entry -> {
                    CompoundTag child = new CompoundTag();
                    child.putInt("Slot", entry.getKey());
                    child.putUUID("Uuid", entry.getValue());
                    children.add(child);
                });
        tag.put("HarvestChildren", children);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("HarvestOwner")
                        ? Optional.of(
                                tag.getUUID("HarvestOwner"))
                        : Optional.empty());
        entityData.set(
                COMMAND_MODE,
                (byte) Mth.clamp(
                        tag.getByte("HarvestCommandMode"),
                        0,
                        HarvestMemberMode.values().length - 1));
        entityData.set(
                CLOSE_FORMATION,
                tag.getBoolean("HarvestCloseFormation"));
        entityData.set(
                COMMAND_TARGET,
                tag.hasUUID("HarvestCommandTarget")
                        ? Optional.of(tag.getUUID(
                                "HarvestCommandTarget"))
                        : Optional.empty());
        entityData.set(
                COMMAND_DESTINATION,
                BlockPos.of(tag.getLong(
                        "HarvestCommandDestination")));
        entityData.set(
                CONTROLLED_MEMBER_UUID,
                tag.hasUUID("HarvestControlledMember")
                        ? Optional.of(tag.getUUID(
                                "HarvestControlledMember"))
                        : Optional.empty());
        entityData.set(CONTROLLED_MEMBER_ID, -1);
        entityData.set(
                CARRY_UP_MEMBER_UUID,
                tag.hasUUID("HarvestCarryUpMember")
                        ? Optional.of(tag.getUUID(
                                "HarvestCarryUpMember"))
                        : Optional.empty());
        entityData.set(CARRY_UP_MEMBER_ID, -1);
        ResourceLocation skin = tag.contains(
                "HarvestSelectedSkin")
                ? ResourceLocation.tryParse(tag.getString(
                        "HarvestSelectedSkin"))
                : null;
        setSelectedSkin(Optional.ofNullable(skin));

        childUuids.clear();
        ListTag children = tag.getList(
                "HarvestChildren",
                Tag.TAG_COMPOUND);
        for (int i = 0;
                i < Math.min(children.size(), MEMBER_CAP);
                i++) {
            CompoundTag child = children.getCompound(i);
            int slot = child.getInt("Slot");
            if (slot >= 0
                    && slot < MEMBER_CAP
                    && child.hasUUID("Uuid")) {
                childUuids.putIfAbsent(
                        slot, child.getUUID("Uuid"));
            }
        }
        nextInitialSlot = MEMBER_CAP;
    }
}
