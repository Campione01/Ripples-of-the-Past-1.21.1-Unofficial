package rotp.addons.ultimate_lifeform;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.ultimate_lifeform.network.UltimateHamonEntityChargePayload;
import rotp.core.entityattachment.SynchronizableEntityData;
import rotp.core.entityattachment.TickingEntityData;
import rotp.core.init.ModDataAttachmentTypes;

/**
 * 1.16 EntityUltimateHamonChargeCap: the entity attachment that carries an Ultimate Lifeform charge (Hamon Organism
 * Infusion, the chicken of a charged egg). It is separate from the core's EntityHamonChargeState, so the two charges
 * neither block nor replace each other. The client draws its sparks from UltimateLifeformClientGameEvents.
 */
public final class UltimateHamonEntityCharge
        implements TickingEntityData, SynchronizableEntityData, INBTSerializable<CompoundTag> {
    private final Entity entity;
    @Nullable private UltimateHamonCharge hamonCharge;
    private boolean clHasCharge;

    public UltimateHamonEntityCharge(Entity entity) {
        this.entity = entity;
        addTicking(entity);
        addSynchronization(entity);
    }

    public static UltimateHamonEntityCharge get(Entity entity) {
        return entity.getData(UltimateLifeformAddon.ENTITY_ULTIMATE_HAMON_CHARGE);
    }

    public static Optional<UltimateHamonEntityCharge> getExisting(Entity entity) {
        return entity.getExistingData(UltimateLifeformAddon.ENTITY_ULTIMATE_HAMON_CHARGE);
    }

    @Override
    public void tick() {
        if (entity.level().isClientSide() || hamonCharge == null) {
            return;
        }
        // 1.16 Entity.canUpdate(): the charge waits while its entity is stopped in time
        var timeStop = ModDataAttachmentTypes.TIME_STOP.get();
        if (entity.level().hasData(timeStop) && entity.level().getData(timeStop).shouldFreeze(entity)) {
            return;
        }
        hamonCharge.tick(entity, null, entity.level(), entity.getBoundingBox().inflate(1.0D));
        if (hamonCharge.shouldBeRemoved()) {
            setHamonCharge(null);
        }
    }

    public void setHamonCharge(float tickDamage, int chargeTicks, @Nullable LivingEntity hamonUser, float energySpent) {
        setHamonCharge(new UltimateHamonCharge(tickDamage, chargeTicks, hamonUser, energySpent));
    }

    public void setHamonCharge(@Nullable UltimateHamonCharge charge) {
        hamonCharge = charge;
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                    new UltimateHamonEntityChargePayload(entity.getId(), hamonCharge != null));
        }
    }

    public boolean hasHamonCharge() {
        return entity.level().isClientSide() ? clHasCharge : hamonCharge != null && !hamonCharge.shouldBeRemoved();
    }

    @Nullable
    public UltimateHamonCharge getHamonCharge() {
        return hasHamonCharge() ? hamonCharge : null;
    }

    @Override
    public void syncToTracking(ServerPlayer trackingPlayer) {
        if (hasHamonCharge()) {
            PacketDistributor.sendToPlayer(trackingPlayer, new UltimateHamonEntityChargePayload(entity.getId(), true));
        }
    }

    public void setClientHasCharge(boolean hasCharge) {
        clHasCharge = hasCharge;
    }

    public boolean clientHasCharge() {
        return clHasCharge;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        if (hamonCharge != null) {
            nbt.put("HamonCharge", hamonCharge.toNBT());
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("HamonCharge", Tag.TAG_COMPOUND)) {
            hamonCharge = UltimateHamonCharge.fromNBT(nbt.getCompound("HamonCharge"));
        }
    }
}
