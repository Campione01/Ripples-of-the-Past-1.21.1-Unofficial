package rotp.addons.badcompany.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.ability.BadCompanyCommandAbility.Command;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BadCompanyStandEntity extends StandEntity {
    public static final int SOLDIER_CAP = 10;
    public static final int TANK_CAP = 2;
    public static final int HELICOPTER_CAP = 2;
    public static final int MINE_CAP = 10;
    public static final int RECONCILE_INTERVAL_TICKS = 20;
    public static final double LEGACY_MAX_RANGE = 60.0D;
    // 1.16 SetTarget.checkRangeAndTarget: rangeSq 2500
    public static final double SET_TARGET_RANGE = 50.0D;
    public static final double VOLLEY_RADIUS = 3.0D;

    private static final int ALL_UNIT_MASK =
            BadCompanyUnitType.SOLDIER.mask()
                    | BadCompanyUnitType.TANK.mask()
                    | BadCompanyUnitType.HELICOPTER.mask();

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> SELECTED_UNIT =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORMATION =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WAIT_MASK =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSE_MASK =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SUMMON_MASK =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EXPLOSIVE_MISSILES =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VOLLEY_MODE =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>>
            SOLDIER_TARGET =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            TANK_TARGET =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            HELICOPTER_TARGET =
            SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<BlockPos>>
            SOLDIER_DESTINATION = SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>>
            TANK_DESTINATION = SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>>
            HELICOPTER_DESTINATION = SynchedEntityData.defineId(
                    BadCompanyStandEntity.class,
                    EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private final Map<UnitKey, UUID> memberUuids =
            new LinkedHashMap<>();
    private final Map<UnitKey, BlockPos> memberLastPositions =
            new HashMap<>();
    private final Map<UUID, BlockPos> mineUuids =
            new LinkedHashMap<>();
    private final Set<UUID> transientUuids = new HashSet<>();
    private final Set<UnitKey> deathSuppressedSlots = new HashSet<>();
    private final Map<BadCompanyUnitType, Long> destinationRevisions =
            new EnumMap<>(BadCompanyUnitType.class);

    public BadCompanyStandEntity(
            EntityType<? extends BadCompanyStandEntity> type,
            Level level) {
        super(type, level);
        // 1.16 BadCompanyStandEntity.offsetDefault (0, 1.0, 0); its y was
        // also the y of every task offset that set none.
        Y_OFFSET = 1.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
        noPhysics = true;
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(
                SELECTED_UNIT,
                BadCompanyUnitType.ALL.ordinal());
        builder.define(
                FORMATION,
                BadCompanyFormation.RANDOM.ordinal());
        builder.define(WAIT_MASK, 0);
        builder.define(CLOSE_MASK, 0);
        builder.define(SUMMON_MASK, ALL_UNIT_MASK);
        builder.define(EXPLOSIVE_MISSILES, true);
        builder.define(VOLLEY_MODE, false);
        builder.define(SOLDIER_TARGET, Optional.empty());
        builder.define(TANK_TARGET, Optional.empty());
        builder.define(HELICOPTER_TARGET, Optional.empty());
        builder.define(SOLDIER_DESTINATION, Optional.empty());
        builder.define(TANK_DESTINATION, Optional.empty());
        builder.define(HELICOPTER_DESTINATION, Optional.empty());
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
            cleanupKnownDependents(serverLevel);
            discard();
            return;
        }
        releaseUnavailableManualControl(serverLevel, owner);
        if (explosiveMissiles()
                && !serverLevel.getGameRules().getBoolean(
                        GameRules.RULE_MOBGRIEFING)) {
            entityData.set(EXPLOSIVE_MISSILES, false);
        }
        if (tickCount <= 1
                || tickCount % RECONCILE_INTERVAL_TICKS == 0) {
            reconcileClosure(serverLevel, owner);
            pruneTransientIds(serverLevel);
            pruneMineIds(serverLevel);
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
                        == BadCompanyAddon.BAD_COMPANY.get()
                && power.getSummonedStandEntity() == this;
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    private void releaseUnavailableManualControl(
            ServerLevel level, LivingEntity owner) {
        if (!isManuallyControlled()
                && EntityComponentController.getControlTarget(owner) != this) {
            return;
        }
        StandPower power = StandPower.get(owner);
        Ability manualControl = power != null && power.getMoveset() != null
                ? power.getMoveset().getAbility("manual_control")
                : null;
        // Reuse the ability's creative, temporary Resolve, skill, and type-policy checks.
        if (manualControl == null || !manualControl.isAbilityAvailable(power)) {
            StandEntityManualControlToggle.off(level, this, false);
        }
    }

    @Override
    public boolean canMoveManually() {
        LivingEntity owner = getUser();
        return isActiveFor(owner)
                && isManuallyControlled()
                && EntityComponentController.getControlTarget(owner) == this
                && super.canMoveManually();
    }

    public Optional<UUID> ownerUuid() {
        return entityData.get(OWNER_UUID);
    }

    public BadCompanyUnitType selectedUnitType() {
        return BadCompanyUnitType.byOrdinal(
                entityData.get(SELECTED_UNIT));
    }

    public BadCompanyFormation formation() {
        return BadCompanyFormation.byOrdinal(
                entityData.get(FORMATION));
    }

    public boolean isVolleyMode() {
        return entityData.get(VOLLEY_MODE);
    }

    public void setVolleyMode(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(VOLLEY_MODE, active);
        }
    }

    public boolean explosiveMissiles() {
        return entityData.get(EXPLOSIVE_MISSILES);
    }

    public boolean selectUnitType(
            LivingEntity owner,
            BadCompanyUnitType type) {
        if (!(level() instanceof ServerLevel)
                || !isActiveFor(owner)
                || type == null) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        if (power == null
                || power.getResolveLevel() < 2
                || power.getResolveLevel()
                        < type.minimumResolve()) {
            return false;
        }
        entityData.set(SELECTED_UNIT, type.ordinal());
        return true;
    }

    public boolean selectFormation(
            LivingEntity owner,
            BadCompanyFormation selectedFormation) {
        if (!(level() instanceof ServerLevel)
                || !isActiveFor(owner)
                || selectedFormation == null) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        if (power == null
                || power.getResolveLevel() < 1) {
            return false;
        }
        entityData.set(
                FORMATION, selectedFormation.ordinal());
        return true;
    }

    public boolean isWaiting(BadCompanyUnitType type) {
        return type != BadCompanyUnitType.ALL
                && (entityData.get(WAIT_MASK) & type.mask()) != 0;
    }

    public boolean isStayingClose(BadCompanyUnitType type) {
        return type != BadCompanyUnitType.ALL
                && (entityData.get(CLOSE_MASK) & type.mask()) != 0;
    }

    public boolean isUnitSummoned(BadCompanyUnitType type) {
        return type != BadCompanyUnitType.ALL
                && (entityData.get(SUMMON_MASK) & type.mask()) != 0;
    }

    public Optional<BlockPos> destination(
            BadCompanyUnitType type) {
        return switch (type) {
            case SOLDIER -> entityData.get(SOLDIER_DESTINATION);
            case TANK -> entityData.get(TANK_DESTINATION);
            case HELICOPTER ->
                    entityData.get(HELICOPTER_DESTINATION);
            case ALL -> Optional.empty();
        };
    }

    public long destinationRevision(BadCompanyUnitType type) {
        return destinationRevisions.getOrDefault(type, 0L);
    }

    @Nullable
    public LivingEntity commandTarget(
            BadCompanyUnitType type) {
        Optional<UUID> targetUuid = switch (type) {
            case SOLDIER -> entityData.get(SOLDIER_TARGET);
            case TANK -> entityData.get(TANK_TARGET);
            case HELICOPTER -> entityData.get(HELICOPTER_TARGET);
            case ALL -> Optional.<UUID>empty();
        };
        Entity target = !targetUuid.isEmpty()
                && level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(targetUuid.get())
                : null;
        return target instanceof LivingEntity living
                ? living
                : null;
    }

    public void executeCommand(
            Command command,
            LivingEntity owner,
            @Nullable ActionTarget target) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !isActiveFor(owner)) {
            return;
        }
        BadCompanyUnitType selected = selectedUnitType();
        switch (command) {
            case SELECT_UNITS -> {}
            case SELECT_FORMATION -> {}
            case SUMMON_UNITS ->
                    toggleSummoned(serverLevel, owner, selected);
            case ORDER_WAIT ->
                    setMask(WAIT_MASK, selected, true);
            case ORDER_NO_WAIT -> {
                setMask(WAIT_MASK, selected, false);
                setDestination(selected, Optional.empty());
            }
            case STAY_CLOSE ->
                    setMask(CLOSE_MASK, selected, true);
            case STOP_STAY_CLOSE ->
                    setMask(CLOSE_MASK, selected, false);
            case CLEAR_TARGET -> clearTargets(selected);
            case SET_TARGET ->
                    setTarget(owner, selected, target);
            case GO_TO_BLOCK ->
                    setDestination(owner, selected, target);
            case PUT_MINES -> putMines(serverLevel, owner);
            case EXPLODE_MINES ->
                    explodeMines(serverLevel, owner);
            case MISSILE_MODE -> {
                boolean canEnable = serverLevel.getGameRules()
                        .getBoolean(GameRules.RULE_MOBGRIEFING);
                entityData.set(
                        EXPLOSIVE_MISSILES,
                        !explosiveMissiles() && canEnable);
            }
        }
    }

    private void setTarget(
            LivingEntity owner,
            BadCompanyUnitType selected,
            @Nullable ActionTarget target) {
        Entity entity = target != null
                ? target.resolveEntityId(level()).getEntity()
                : null;
        // guard only: BadCompanyCommandAbility.checkSpecificConditions applies the same rules first
        if (!(entity instanceof LivingEntity living)
                || !canBeSetAsTarget(owner, living)
                || isSetTargetTooFar(this, living)) {
            return;
        }
        forEachSelected(selected, type ->
                targetAccessor(type).set(
                        Optional.of(living.getUUID())));
    }

    public static boolean canBeSetAsTarget(
            LivingEntity owner, LivingEntity target) {
        return target.isAlive()
                && target != owner
                && !target.isAlliedTo(owner);
    }

    /** 1.16 SetTarget: 50 blocks from the Stand (the performer) to the target's box, 25 without line of sight. */
    public static boolean isSetTargetTooFar(
            LivingEntity performer, Entity target) {
        double rangeSq = SET_TARGET_RANGE * SET_TARGET_RANGE;
        if (!performer.hasLineOfSight(target)) {
            rangeSq /= 4.0D;
        }
        double distance = distanceToBox(
                performer, target.getBoundingBox());
        return distance * distance > rangeSq;
    }

    /** Go To Block range, measured from the owner to the block centre. */
    public static boolean isDestinationTooFar(
            LivingEntity owner, BlockPos position) {
        return owner.distanceToSqr(Vec3.atCenterOf(position))
                > LEGACY_MAX_RANGE * LEGACY_MAX_RANGE;
    }

    // 1.16 JojoModUtil.getDistance(Entity, AxisAlignedBB)
    private static double distanceToBox(
            Entity entity, AABB box) {
        Vec3 start = entity.getEyePosition(1.0F);
        if (box.contains(start)) {
            return 0.0D;
        }
        Vec3 end = new Vec3(
                Mth.lerp(0.5D, box.minX, box.maxX),
                Mth.lerp(entity.getBbHeight() == 0.0F
                                ? 0.0D
                                : entity.getEyeHeight()
                                        / entity.getBbHeight(),
                        box.minY, box.maxY),
                Mth.lerp(0.5D, box.minZ, box.maxZ));
        return box.clip(start, end)
                .map(hit -> start.distanceTo(hit)
                        - entity.getBbWidth() / 2.0D)
                .orElse(-1.0D);
    }

    private void setDestination(
            LivingEntity owner,
            BadCompanyUnitType selected,
            @Nullable ActionTarget target) {
        BlockPos position = target != null
                && target.getType()
                        == ActionTarget.TargetType.BLOCK
                        ? target.getBlockPos()
                        : null;
        if (position == null
                || isDestinationTooFar(owner, position)
                || !level().hasChunkAt(position)) {
            return;
        }
        forEachSelected(selected, type -> {
            BlockPos destination = position.above(
                    type == BadCompanyUnitType.HELICOPTER ? 3 : 1);
            destinationAccessor(type).set(Optional.of(destination));
            // Reissuing the same coordinate is still a new order.
            destinationRevisions.put(type, destinationRevision(type) + 1L);
        });
    }

    private void clearTargets(BadCompanyUnitType selected) {
        forEachSelected(selected, type -> {
            targetAccessor(type).set(Optional.empty());
            for (BadCompanyUnitEntity unit :
                    loadedUnits(type)) {
                unit.clearCombatTarget();
            }
        });
    }

    private void toggleSummoned(
            ServerLevel level,
            LivingEntity owner,
            BadCompanyUnitType selected) {
        int current = entityData.get(SUMMON_MASK);
        int updated;
        if (selected == BadCompanyUnitType.ALL) {
            updated = owner.isShiftKeyDown()
                    ? 0
                    : ALL_UNIT_MASK;
        } else {
            updated = current ^ selected.mask();
        }
        entityData.set(SUMMON_MASK, updated);
        for (BadCompanyUnitType type : concreteTypes()) {
            if (!selected.includes(type)) {
                continue;
            }
            if ((updated & type.mask()) == 0) {
                discardMembers(level, type);
            } else {
                deathSuppressedSlots.removeIf(
                        key -> key.type() == type);
            }
        }
        reconcileClosure(level, owner);
    }

    private void setMask(
            EntityDataAccessor<Integer> accessor,
            BadCompanyUnitType selected,
            boolean enabled) {
        int mask = entityData.get(accessor);
        for (BadCompanyUnitType type : concreteTypes()) {
            if (selected.includes(type)) {
                mask = enabled
                        ? mask | type.mask()
                        : mask & ~type.mask();
            }
        }
        entityData.set(accessor, mask);
    }

    private void setDestination(
            BadCompanyUnitType selected,
            Optional<BlockPos> value) {
        forEachSelected(selected, type ->
                destinationAccessor(type).set(value));
    }

    private void forEachSelected(
            BadCompanyUnitType selected,
            java.util.function.Consumer<BadCompanyUnitType>
                    consumer) {
        for (BadCompanyUnitType type : concreteTypes()) {
            if (selected.includes(type)) {
                consumer.accept(type);
            }
        }
    }

    private MutableTargetAccessor targetAccessor(
            BadCompanyUnitType type) {
        EntityDataAccessor<Optional<UUID>> accessor = switch (type) {
            case SOLDIER -> SOLDIER_TARGET;
            case TANK -> TANK_TARGET;
            case HELICOPTER -> HELICOPTER_TARGET;
            case ALL -> throw new IllegalArgumentException("all");
        };
        return value -> entityData.set(accessor, value);
    }

    private MutableDestinationAccessor destinationAccessor(
            BadCompanyUnitType type) {
        EntityDataAccessor<Optional<BlockPos>> accessor =
                switch (type) {
                    case SOLDIER -> SOLDIER_DESTINATION;
                    case TANK -> TANK_DESTINATION;
                    case HELICOPTER -> HELICOPTER_DESTINATION;
                    case ALL ->
                            throw new IllegalArgumentException("all");
                };
        return value -> entityData.set(accessor, value);
    }

    public Vec3 formationPosition(
            LivingEntity owner,
            BadCompanyUnitType type,
            int slot) {
        if (isVolleyMode()
                && type == BadCompanyUnitType.SOLDIER) {
            return linePosition(
                    owner, type, slot, 0.42D, 1.0D);
        }
        return switch (formation()) {
            case RANDOM -> randomPosition(owner, type, slot);
            case CIRCLE -> circlePosition(owner, type, slot);
            case LINE -> linePosition(
                    owner,
                    type,
                    slot,
                    type == BadCompanyUnitType.SOLDIER
                            ? 0.42D
                            : 2.5D,
                    type == BadCompanyUnitType.SOLDIER
                            ? 1.0D
                            : 2.0D);
        };
    }

    private static Vec3 randomPosition(
            LivingEntity owner,
            BadCompanyUnitType type,
            int slot) {
        long seed = owner.getUUID().getMostSignificantBits()
                ^ ((long) type.ordinal() << 32)
                ^ slot * 0x9E3779B97F4A7C15L;
        double x = (((seed >>> 16) & 1023) / 1023.0D
                - 0.5D) * 10.0D;
        double z = (((seed >>> 38) & 1023) / 1023.0D
                - 0.5D) * 10.0D;
        return owner.position().add(x, 0.0D, z);
    }

    private static Vec3 circlePosition(
            LivingEntity owner,
            BadCompanyUnitType type,
            int slot) {
        int cap = Math.max(1, type.cap());
        double radius = type == BadCompanyUnitType.SOLDIER
                ? 1.5D
                : 2.5D;
        double angle = Mth.TWO_PI * slot / cap;
        return owner.position().add(
                Mth.cos((float) angle) * radius,
                0.0D,
                Mth.sin((float) angle) * radius);
    }

    private static Vec3 linePosition(
            LivingEntity owner,
            BadCompanyUnitType type,
            int slot,
            double spacing,
            double behind) {
        int columns = type == BadCompanyUnitType.SOLDIER
                ? 5
                : 2;
        int row = slot / columns;
        int column = slot % columns - (columns - 1) / 2;
        Vec3 forward = Vec3.directionFromRotation(
                0.0F, owner.getYRot());
        Vec3 right = new Vec3(
                -forward.z, 0.0D, forward.x);
        return owner.position()
                .subtract(forward.scale(behind + row * 0.55D))
                .add(right.scale(column * spacing));
    }

    public boolean hasVolleyUnits(LivingEntity owner) {
        return isActiveFor(owner)
                && loadedUnits(BadCompanyUnitType.SOLDIER)
                        .stream()
                        .anyMatch(soldier ->
                                soldier.distanceToSqr(owner)
                                        <= VOLLEY_RADIUS
                                                * VOLLEY_RADIUS);
    }

    public int commandVolley(LivingEntity owner) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !isActiveFor(owner)) {
            return 0;
        }
        Vec3 direction = owner.getLookAngle();
        int fired = 0;
        for (BadCompanyUnitEntity unit :
                loadedUnits(BadCompanyUnitType.SOLDIER)) {
            if (unit instanceof BadCompanySoldierEntity soldier
                    && soldier.distanceToSqr(owner)
                            <= VOLLEY_RADIUS * VOLLEY_RADIUS
                    && soldier.fire(owner, direction, false)) {
                fired++;
            }
        }
        if (fired > 0) {
            serverLevel.playSound(
                    null,
                    owner.blockPosition(),
                    BadCompanyAddon.SOLDIER_BARRAGE.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        return fired;
    }

    public void observeMember(BadCompanyUnitEntity member) {
        if (!member.isBoundTo(getUUID())) {
            return;
        }
        UnitKey key = new UnitKey(
                member.unitType(), member.slot());
        UUID known = memberUuids.get(key);
        if (known == null || known.equals(member.getUUID())) {
            memberUuids.put(key, member.getUUID());
            memberLastPositions.put(
                    key, member.blockPosition());
        }
    }

    public void onMemberRemoved(
            BadCompanyUnitEntity member,
            RemovalReason reason) {
        UnitKey key = new UnitKey(
                member.unitType(), member.slot());
        memberUuids.remove(key, member.getUUID());
        if (reason == RemovalReason.KILLED) {
            StandPower power = getUser() != null
                    ? StandPower.get(getUser())
                    : null;
            float cost = member.unitType()
                    == BadCompanyUnitType.SOLDIER
                            ? 50.0F
                            : 100.0F;
            if (power == null
                    || !isUnitSummoned(member.unitType())
                    || !power.consumeStamina(cost)) {
                deathSuppressedSlots.add(key);
            }
        }
    }

    public void trackTransient(Entity entity) {
        if (!level().isClientSide()
                && entity != null) {
            transientUuids.add(entity.getUUID());
        }
    }

    public void untrackTransient(UUID uuid) {
        transientUuids.remove(uuid);
    }

    public void trackMine(BadCompanyMineEntity mine) {
        if (!level().isClientSide()
                && mine != null
                && mineUuids.size() < MINE_CAP) {
            mineUuids.put(
                    mine.getUUID(), mine.blockPosition());
        }
    }

    public void untrackMine(UUID uuid) {
        mineUuids.remove(uuid);
    }

    private void putMines(
            ServerLevel level, LivingEntity owner) {
        pruneMineIds(level);
        for (BadCompanyUnitEntity unit :
                loadedUnits(BadCompanyUnitType.SOLDIER)) {
            if (mineUuids.size() >= MINE_CAP) {
                break;
            }
            BadCompanyMineEntity mine =
                    BadCompanyAddon.BAD_MINE.get().create(level);
            if (mine == null) {
                continue;
            }
            mine.bindClosure(
                    owner.getUUID(), getUUID());
            mine.moveTo(
                    unit.getX(),
                    unit.getY(),
                    unit.getZ(),
                    0.0F,
                    0.0F);
            level.addFreshEntity(mine);
            trackMine(mine);
        }
    }

    private void explodeMines(
            ServerLevel level, LivingEntity owner) {
        for (UUID uuid : List.copyOf(mineUuids.keySet())) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof BadCompanyMineEntity mine) {
                mine.detonate(owner, 1.5F);
            }
        }
        mineUuids.clear();
    }

    private void reconcileClosure(
            ServerLevel level, LivingEntity owner) {
        StandPower power = StandPower.get(owner);
        int resolve = power != null
                ? power.getResolveLevel()
                : 0;
        for (BadCompanyUnitType type : concreteTypes()) {
            boolean enabled = isUnitSummoned(type)
                    && resolve >= type.minimumResolve();
            if (!enabled) {
                discardMembers(level, type);
                continue;
            }
            for (int slot = 0; slot < type.cap(); slot++) {
                UnitKey key = new UnitKey(type, slot);
                UUID uuid = memberUuids.get(key);
                Entity existing = uuid != null
                        ? level.getEntity(uuid)
                        : null;
                if (existing instanceof BadCompanyUnitEntity unit
                        && unit.isAlive()
                        && unit.unitType() == type
                        && unit.slot() == slot
                        && unit.isOwnedBy(owner.getUUID())
                        && unit.isBoundTo(getUUID())) {
                    memberLastPositions.put(
                            key, unit.blockPosition());
                    continue;
                }
                if (uuid != null) {
                    BlockPos last = memberLastPositions.get(key);
                    if (last == null || !level.hasChunkAt(last)) {
                        continue;
                    }
                    memberUuids.remove(key);
                }
                if (!deathSuppressedSlots.contains(key)) {
                    spawnMember(level, owner, key);
                }
            }
        }
    }

    private void spawnMember(
            ServerLevel level,
            LivingEntity owner,
            UnitKey key) {
        BadCompanyUnitEntity member = switch (key.type()) {
            case SOLDIER ->
                    BadCompanyAddon.BAD_SOLDIER.get().create(level);
            case TANK ->
                    BadCompanyAddon.BAD_TANK.get().create(level);
            case HELICOPTER ->
                    BadCompanyAddon.BAD_HELICOPTER.get().create(level);
            case ALL -> null;
        };
        if (member == null) {
            return;
        }
        member.bindClosure(
                owner.getUUID(), getUUID(), key.slot());
        member.initializeDestinationRevision(destinationRevision(key.type()));
        Vec3 position = formationPosition(
                owner, key.type(), key.slot())
                .add(
                        0.0D,
                        key.type()
                                == BadCompanyUnitType.HELICOPTER
                                        ? 2.0D
                                        : 0.0D,
                        0.0D);
        member.moveTo(
                position.x,
                position.y,
                position.z,
                owner.getYRot(),
                0.0F);
        level.addFreshEntity(member);
        memberUuids.put(key, member.getUUID());
        memberLastPositions.put(key, member.blockPosition());
    }

    private List<BadCompanyUnitEntity> loadedUnits(
            BadCompanyUnitType type) {
        List<BadCompanyUnitEntity> units =
                new ArrayList<>(type.cap());
        if (!(level() instanceof ServerLevel serverLevel)) {
            return units;
        }
        for (Map.Entry<UnitKey, UUID> entry :
                memberUuids.entrySet()) {
            if (entry.getKey().type() != type) {
                continue;
            }
            Entity entity = serverLevel.getEntity(entry.getValue());
            if (entity instanceof BadCompanyUnitEntity unit
                    && unit.isAlive()) {
                units.add(unit);
            }
        }
        return units;
    }

    private void discardMembers(
            ServerLevel level,
            BadCompanyUnitType type) {
        List<UnitKey> keys = memberUuids.keySet().stream()
                .filter(key -> key.type() == type)
                .toList();
        for (UnitKey key : keys) {
            UUID uuid = memberUuids.remove(key);
            Entity entity = uuid != null
                    ? level.getEntity(uuid)
                    : null;
            if (entity != null) {
                entity.discard();
            }
            memberLastPositions.remove(key);
        }
    }

    private void pruneTransientIds(ServerLevel level) {
        transientUuids.removeIf(uuid ->
                level.getEntity(uuid) == null);
    }

    private void pruneMineIds(ServerLevel level) {
        mineUuids.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof BadCompanyMineEntity mine
                    && mine.isAlive()) {
                entry.setValue(mine.blockPosition());
                return false;
            }
            return level.hasChunkAt(entry.getValue());
        });
    }

    private void cleanupKnownDependents(ServerLevel level) {
        for (UUID uuid : List.copyOf(memberUuids.values())) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID uuid : List.copyOf(mineUuids.keySet())) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID uuid : List.copyOf(transientUuids)) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof BadCompanyMissileEntity missile
                    && missile.usesOwnerLifetime()) {
                missile.captureCoordinatorMode(this);
                continue;
            }
            if (entity != null) {
                entity.discard();
            }
        }
        memberUuids.clear();
        memberLastPositions.clear();
        mineUuids.clear();
        transientUuids.clear();
        deathSuppressedSlots.clear();
    }

    public static void cleanupOwnerDependents(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return;
        }
        LivingEntity owner = server.getPlayerList()
                .getPlayer(ownerUuid);
        StandPower power = owner != null
                ? StandPower.get(owner)
                : null;
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof BadCompanyStandEntity coordinator
                && coordinator.isOwnedBy(ownerUuid)
                && coordinator.level()
                        instanceof ServerLevel level) {
            coordinator.cleanupKnownDependents(level);
        }
    }

    private static BadCompanyUnitType[] concreteTypes() {
        return new BadCompanyUnitType[] {
                BadCompanyUnitType.SOLDIER,
                BadCompanyUnitType.TANK,
                BadCompanyUnitType.HELICOPTER
        };
    }

    @Override
    public double getMaxRange() {
        return LEGACY_MAX_RANGE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
    public void remove(RemovalReason reason) {
        StandEntityManualControlToggle.off(level(), this, false);
        if (level() instanceof ServerLevel serverLevel) {
            cleanupKnownDependents(serverLevel);
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID)
                .ifPresent(uuid ->
                        tag.putUUID("BadCompanyOwner", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("BadCompanyOwner")
                        ? Optional.of(
                                tag.getUUID("BadCompanyOwner"))
                        : Optional.empty());
    }

    private record UnitKey(
            BadCompanyUnitType type, int slot) {}

    @FunctionalInterface
    private interface MutableTargetAccessor {
        void set(Optional<UUID> value);
    }

    @FunctionalInterface
    private interface MutableDestinationAccessor {
        void set(Optional<BlockPos> value);
    }
}
