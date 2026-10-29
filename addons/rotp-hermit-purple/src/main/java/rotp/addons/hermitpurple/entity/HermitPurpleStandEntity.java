package rotp.addons.hermitpurple.entity;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.HermitPurpleSkins;
import rotp.addons.hermitpurple.HermitVineSelectionState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HermitPurpleStandEntity extends StandEntity {
    private static final EntityDataAccessor<Integer> TARGET_MODE =
            SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TARGET =
            SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BARRIER_COUNT =
            SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            ACTIVE_HELD_VINE_ID = SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            ATTACHED_BINDING_LASH_ID =
            SynchedEntityData.defineId(
                    HermitPurpleStandEntity.class,
                    EntityDataSerializers.INT);

    private final Set<UUID> activeLashes = new LinkedHashSet<>();
    private final Set<UUID> placedBarriers = new LinkedHashSet<>();
    private UUID activeHeldVine;
    private UUID openBarrier;

    public HermitPurpleStandEntity(
            EntityType<? extends HermitPurpleStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 HermitPurpleEntity.offsetDefault (0, 0, 0); its y was also
        // the y of every task offset that set none.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
        noPhysics = true;
        setInvisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setInvisible(true);
        if (!level().isClientSide()) {
            setHermitColor(HermitPurpleSkins.color(getStandSkin()));
            removeMissingEntities();
            if (getUser() == null
                    || !HermitPurpleAddon.isHermitPurpleUser(
                            getUser())) {
                discardActiveVines();
            }
        }
    }

    public void registerLash(HermitVineProjectileEntity lash) {
        if (level().isClientSide()) {
            return;
        }
        removeMissingEntities();
        while (activeLashes.size()
                >= HermitPurpleAddon.ACTIVE_LASH_CAP) {
            Iterator<UUID> iterator = activeLashes.iterator();
            UUID oldest = iterator.next();
            iterator.remove();
            discardEntity(oldest);
        }
        activeLashes.add(lash.getUUID());
        syncActionableVineIds();
    }

    public void forgetLash(HermitVineProjectileEntity lash) {
        activeLashes.remove(lash.getUUID());
        syncActionableVineIds();
    }

    public void replaceHeldVine(HermitGrappleVineEntity vine) {
        if (level().isClientSide()) {
            return;
        }
        UUID replacement = vine.getUUID();
        if (activeHeldVine != null
                && !activeHeldVine.equals(replacement)) {
            discardEntity(activeHeldVine);
        }
        activeHeldVine = replacement;
        syncActionableVineIds();
    }

    public void forgetHeldVine(HermitGrappleVineEntity vine) {
        if (vine.getUUID().equals(activeHeldVine)) {
            activeHeldVine = null;
            syncActionableVineIds();
        }
    }

    @Nullable
    public HermitGrappleVineEntity getHeldVine() {
        return HermitVineSelectionState.resolveTrackedEntity(
                level().isClientSide(),
                entityData.get(ACTIVE_HELD_VINE_ID),
                activeHeldVine,
                id -> heldVine(level().getEntity(id)),
                id -> heldVine(entity(id)));
    }

    @Nullable
    public HermitGrappleVineEntity getAttachedGrabVine() {
        HermitGrappleVineEntity vine = getHeldVine();
        return vine != null
                && vine.isCommandGrab()
                && vine.getEntityAttachedTo() != null
                ? vine
                : null;
    }

    @Nullable
    public HermitVineProjectileEntity getAttachedBindingLash() {
        if (!level().isClientSide()) {
            return findServerAttachedBindingLash();
        }
        return HermitVineSelectionState.resolveTrackedEntity(
                true,
                entityData.get(ATTACHED_BINDING_LASH_ID),
                null,
                id -> attachedBindingLash(level().getEntity(id)),
                id -> null);
    }

    @Nullable
    private HermitVineProjectileEntity
            findServerAttachedBindingLash() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (UUID id : activeLashes) {
            HermitVineProjectileEntity vine =
                    attachedBindingLash(serverLevel.getEntity(id));
            if (vine != null) {
                return vine;
            }
        }
        return null;
    }

    public boolean canPlaceBarrier() {
        return getPlacedBarrierCount()
                < HermitPurpleSpec.BARRIER_MAX_PLACED;
    }

    public void attachBarrier(BlockPos blockPos) {
        if (!(level() instanceof ServerLevel)
                || !canPlaceBarrier()) {
            return;
        }
        HermitBarrierVineEntity open =
                barrierEntity(openBarrier);
        if (open != null) {
            if (blockPos.equals(open.getOriginBlockPos())) {
                return;
            }
            open.attachToBlock(blockPos);
            placedBarriers.add(open.getUUID());
            openBarrier = null;
            syncBarrierCount();
        }

        HermitBarrierVineEntity next =
                new HermitBarrierVineEntity(this, level());
        next.setOriginBlockPos(blockPos);
        next.setShootingPosOf(this);
        level().addFreshEntity(next);
        openBarrier = next.getUUID();
    }

    public void forgetBarrier(HermitBarrierVineEntity barrier) {
        UUID id = barrier.getUUID();
        placedBarriers.remove(id);
        if (id.equals(openBarrier)) {
            openBarrier = null;
        }
        syncBarrierCount();
    }

    public int getPlacedBarrierCount() {
        return level().isClientSide()
                ? entityData.get(BARRIER_COUNT)
                : placedBarriers.size();
    }

    public int getTargetMode() {
        return entityData.get(TARGET_MODE);
    }

    public String getTarget() {
        return entityData.get(TARGET);
    }

    public void setTargetSelection(int mode, String target) {
        if (!level().isClientSide()) {
            entityData.set(TARGET_MODE, mode);
            entityData.set(
                    TARGET,
                    target == null || target.isBlank()
                            ? "random"
                            : target);
        }
    }

    public int getHermitColor() {
        return entityData.get(COLOR);
    }

    public void setHermitColor(int color) {
        if (!level().isClientSide()) {
            entityData.set(COLOR, color & 0xFFFFFF);
        }
    }

    public void discardBarriers() {
        Set<UUID> barriers = Set.copyOf(placedBarriers);
        UUID open = openBarrier;
        placedBarriers.clear();
        openBarrier = null;
        syncBarrierCount();
        barriers.forEach(this::discardEntity);
        if (open != null) {
            discardEntity(open);
        }
    }

    public void discardActiveVines() {
        if (!(level() instanceof ServerLevel)) {
            activeLashes.clear();
            activeHeldVine = null;
            placedBarriers.clear();
            openBarrier = null;
            return;
        }
        Set<UUID> lashes = Set.copyOf(activeLashes);
        UUID held = activeHeldVine;
        activeLashes.clear();
        activeHeldVine = null;
        syncActionableVineIds();
        lashes.forEach(this::discardEntity);
        if (held != null) {
            discardEntity(held);
        }
        discardBarriers();
    }

    private void removeMissingEntities() {
        if (level() instanceof ServerLevel serverLevel) {
            activeLashes.removeIf(id -> {
                Entity entity = serverLevel.getEntity(id);
                return !(entity instanceof HermitVineProjectileEntity)
                        || !entity.isAlive();
            });
            placedBarriers.removeIf(id -> {
                Entity entity = serverLevel.getEntity(id);
                return !(entity instanceof HermitBarrierVineEntity)
                        || !entity.isAlive();
            });
            if (activeHeldVine != null
                    && heldVine(serverLevel.getEntity(
                            activeHeldVine)) == null) {
                activeHeldVine = null;
            }
            if (openBarrier != null
                    && !(serverLevel.getEntity(openBarrier)
                            instanceof HermitBarrierVineEntity)) {
                openBarrier = null;
            }
            syncBarrierCount();
            syncActionableVineIds();
        }
    }

    private void syncActionableVineIds() {
        if (level().isClientSide()) {
            return;
        }
        HermitGrappleVineEntity held = heldVine(
                entity(activeHeldVine));
        entityData.set(
                ACTIVE_HELD_VINE_ID,
                held != null ? held.getId() : -1);
        HermitVineProjectileEntity binding =
                findServerAttachedBindingLash();
        entityData.set(
                ATTACHED_BINDING_LASH_ID,
                binding != null ? binding.getId() : -1);
    }

    private void syncBarrierCount() {
        if (!level().isClientSide()) {
            entityData.set(BARRIER_COUNT, placedBarriers.size());
        }
    }

    @Nullable
    private HermitBarrierVineEntity barrierEntity(UUID id) {
        Entity entity = entity(id);
        return entity instanceof HermitBarrierVineEntity barrier
                && barrier.isAlive()
                ? barrier
                : null;
    }

    @Nullable
    private Entity entity(UUID id) {
        return id != null && level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(id)
                : null;
    }

    @Nullable
    private static HermitGrappleVineEntity heldVine(
            @Nullable Entity entity) {
        return entity instanceof HermitGrappleVineEntity vine
                && vine.isAlive()
                ? vine
                : null;
    }

    @Nullable
    private static HermitVineProjectileEntity attachedBindingLash(
            @Nullable Entity entity) {
        return entity instanceof HermitVineProjectileEntity vine
                && vine.isBinding()
                && vine.getEntityAttachedTo() != null
                && vine.isAlive()
                ? vine
                : null;
    }

    private void discardEntity(UUID id) {
        Entity entity = entity(id);
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
    }

    @Override
    public float getLeapStrength() {
        if (getUser() == null
                || !HermitHamon.hasSkill(
                        getUser(), ModHamonSkills.JUMP.get())) {
            return 0.0F;
        }
        return HermitHamon.hasSkill(
                        getUser(), ModHamonSkills.AFTERIMAGES.get())
                ? HermitPurpleSpec
                        .LEAP_STRENGTH_AFTERIMAGES
                : HermitPurpleSpec.LEAP_STRENGTH_JUMP;
    }

    @Override
    public boolean isStandBlocking() {
        EntityActionInstance action = getCurStandAction();
        return action != null
                && action.getPhase() == ActionPhase.PERFORM
                && action.ability.getAbilityId() != null
                && HermitPurpleAddon.HP_BLOCK_NAME.equals(
                        action.ability.getAbilityId()
                                .nameInMoveset());
    }

    // 1.16 HPThorns kept the idle pose, so the core never guarded the
    // user's hits; HermitPurpleGameEvents cuts them with the thorns.
    @Override
    protected boolean guardsHitsOnUser() {
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
        discardActiveVines();
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(
                TARGET_MODE,
                HermitPurpleSpec.TARGET_MODE_RANDOM);
        builder.define(TARGET, "random");
        builder.define(COLOR, HermitPurpleSpec.MAP_COLOR);
        builder.define(BARRIER_COUNT, 0);
        builder.define(ACTIVE_HELD_VINE_ID, -1);
        builder.define(ATTACHED_BINDING_LASH_ID, -1);
    }
}
