package rotp.addons.whitesnake.entity.projectile;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.DiscPayload;
import rotp.addons.whitesnake.disc.DiscPayload.Type;
import rotp.addons.whitesnake.disc.MemoryDiscPayloadItem;
import rotp.addons.whitesnake.standdisc.StandDiscService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public final class DiscProjectile extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 100;
    public static final float BASE_DAMAGE = 1.0F;
    public static final float MAX_HARDNESS = 0.0F;

    private static final String TYPE_TAG = "TYPE";
    private static final String STAND_PAYLOAD_TAG = "disc_stand";
    private static final String MEMORY_PAYLOAD_TAG = "player_data";
    private static final String MEMORY_ITEM_TAG = "memory_disc_item";
    private static final String DROP_ON_FAILURE_TAG = "drop_on_failure";

    private Type payloadType = Type.NONE;
    private ItemStack carrier = ItemStack.EMPTY;
    private CompoundTag memoryPayload = new CompoundTag();
    private boolean dropOnFailure = true;
    private boolean payloadConsumed;
    private boolean payloadDropped;

    public DiscProjectile(
            EntityType<? extends DiscProjectile> type, Level level) {
        super(type, level);
    }

    public DiscProjectile(LivingEntity shooter, Level level) {
        super(WhitesnakeAddon.DISC_ENTITY.get(), shooter, level);
    }

    public void setPayload(
            DiscPayload payload, boolean dropOnFailure) {
        this.payloadType = payload.type();
        this.carrier = payload.carrier();
        this.memoryPayload = payload.memoryPayload();
        this.dropOnFailure = dropOnFailure;
    }

    public Type getPayloadType() {
        return payloadType;
    }

    public ItemStack getCarrierItem() {
        return carrier.copy();
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    public void tick() {
        LivingEntity owner = getOwner();
        if (!level().isClientSide()
                && (tickCount > ticksLifespan()
                        || owner != null && !owner.isAlive())) {
            dropPayload();
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (!level().isClientSide() && !payloadConsumed) {
            switch (payloadType) {
                case STAND_DISC -> applyStandPayload(target);
                case MEMORY_DISC -> applyMemoryPayload(target);
                case NONE -> {
                }
            }
        }
        return super.hurtTarget(target, owner);
    }

    private void applyStandPayload(Entity target) {
        LivingEntity targetUser = normalizeLivingTarget(target);
        StandInstance replacement =
                StandDiscItem.getStandInstance(carrier);
        if (targetUser == null
                || replacement == null
                || !replacement.standExists()) {
            return;
        }

        if (StandDiscService.isAuthoritative(carrier)) {
            StandDiscService.Result restoration =
                    StandDiscService.restore(carrier, targetUser);
            if (!restoration.applied()) {
                return;
            }
            finishStandHit(targetUser);
            return;
        }

        StandPower targetPower =
                PowerClass.STAND.attachGet(targetUser);
        StandPowerTransitions.Result result =
                targetPower.getStandInstance()
                        .filter(StandInstance::standExists)
                        .map(current -> StandPowerTransitions.replace(
                                targetPower,
                                current.getStandId(),
                                replacement))
                        .orElseGet(() -> StandPowerTransitions.insert(
                                targetPower, replacement));
        if (!result.applied()) {
            return;
        }
        // 1.16 giveStandFromInstance(standExistedInWorld): not counted twice
        StandDiscItem.takePutOut(targetUser, carrier);
        if (result.previous().isPresent()) {
            // 1.16 putOutStand cleared Resolve along with the old Stand
            targetPower.resolveCounter.resetResolveValue(targetPower);
        }

        result.previous()
                .filter(StandInstance::standExists)
                .map(StandDiscItem::withStand)
                .ifPresent(disc -> {
                    // 1.16 putOutStand: the knocked-out Stand stays taken
                    StandDiscItem.markPutOut(targetUser, disc);
                    ItemUtil.giveItemTo(targetUser, disc, true);
                });
        finishStandHit(targetUser);
    }

    private void finishStandHit(LivingEntity targetUser) {
        targetUser.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, 20, 0, true, false));
        targetUser.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 100, 0, true, false));
        consumePayload();
    }

    private void applyMemoryPayload(Entity target) {
        LivingEntity targetUser = normalizeLivingTarget(target);
        if (targetUser == null
                || !(carrier.getItem()
                        instanceof MemoryDiscPayloadItem memoryDisc)) {
            return;
        }
        if (memoryDisc.restoreMemory(
                carrier.copy(), targetUser, memoryPayload.copy())) {
            consumePayload();
        }
    }

    @Nullable
    private static LivingEntity normalizeLivingTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return null;
        }
        LivingEntity normalized = StandUtil.getStandUser(living);
        return normalized != null && normalized.isAlive()
                ? normalized
                : null;
    }

    private void consumePayload() {
        payloadConsumed = true;
        carrier = ItemStack.EMPTY;
        memoryPayload = new CompoundTag();
    }

    private void dropPayload() {
        if (payloadConsumed || payloadDropped) {
            return;
        }
        payloadDropped = true;
        if (dropOnFailure && !carrier.isEmpty()) {
            ItemEntity dropped = new ItemEntity(
                    level(), getX(), getY(), getZ(), carrier.copy());
            dropped.setPickUpDelay(5);
            LivingEntity owner = getOwner();
            if (owner != null
                    && (payloadType != Type.STAND_DISC
                            || StandDiscService.isAuthoritative(carrier))) {
                LivingEntity user = StandUtil.getStandUser(owner);
                if (user != null) {
                    dropped.setTarget(user.getUUID());
                }
            }
            level().addFreshEntity(dropped);
        }
        carrier = ItemStack.EMPTY;
        memoryPayload = new CompoundTag();
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (!level().isClientSide()) {
            dropPayload();
            level().playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    WhitesnakeAddon.WHITESNAKE_PUNCH.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        super.breakProjectile(targetType, hitTarget);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeUtf(payloadType.serializedName());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, carrier);
        buffer.writeNbt(memoryPayload.copy());
        buffer.writeBoolean(dropOnFailure);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        payloadType = Type.fromSerializedName(buffer.readUtf());
        carrier = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        CompoundTag payload = buffer.readNbt();
        memoryPayload =
                payload != null ? payload : new CompoundTag();
        dropOnFailure = buffer.readBoolean();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString(TYPE_TAG, payloadType.serializedName());
        nbt.putBoolean(DROP_ON_FAILURE_TAG, dropOnFailure);
        if (payloadType == Type.STAND_DISC && !carrier.isEmpty()) {
            nbt.put(
                    STAND_PAYLOAD_TAG,
                    carrier.save(registryAccess()));
        } else if (payloadType == Type.MEMORY_DISC) {
            if (!carrier.isEmpty()) {
                nbt.put(
                        MEMORY_ITEM_TAG,
                        carrier.save(registryAccess()));
            }
            nbt.put(MEMORY_PAYLOAD_TAG, memoryPayload.copy());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        payloadType = Type.fromSerializedName(
                nbt.getString(TYPE_TAG));
        dropOnFailure = !nbt.contains(DROP_ON_FAILURE_TAG)
                || nbt.getBoolean(DROP_ON_FAILURE_TAG);
        carrier = switch (payloadType) {
            case STAND_DISC -> ItemStack.parseOptional(
                    registryAccess(),
                    nbt.getCompound(STAND_PAYLOAD_TAG));
            case MEMORY_DISC -> ItemStack.parseOptional(
                    registryAccess(),
                    nbt.getCompound(MEMORY_ITEM_TAG));
            case NONE -> ItemStack.EMPTY;
        };
        memoryPayload = payloadType == Type.MEMORY_DISC
                ? nbt.getCompound(MEMORY_PAYLOAD_TAG).copy()
                : new CompoundTag();
    }
}
