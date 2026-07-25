package com.github.standobyte.jojoimpl.powers.hamon;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.entityattachment.SynchronizableEntityData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class EntityHamonChargeState implements TickingEntityData, SynchronizableEntityData, INBTSerializable<CompoundTag> {
	private final Entity entity;
	@Nullable private HamonCharge charge;
	private boolean clientHasCharge;

	public EntityHamonChargeState(Entity entity) {
		this.entity = entity;
		addTicking(entity);
		addSynchronization(entity);
	}

	public boolean hasHamonCharge() {
		return entity.level().isClientSide() ? clientHasCharge : charge != null && !charge.shouldBeRemoved();
	}

	@Nullable
	public HamonCharge getHamonCharge() {
		return hasHamonCharge() ? charge : null;
	}

	public void setHamonCharge(float tickDamage, int chargeTicks, @Nullable LivingEntity hamonUser, float energySpent) {
		charge = new HamonCharge(tickDamage, chargeTicks, hamonUser, energySpent);
		syncChargeToTrackingAndSelf();
	}

	public void clear() {
		boolean hadCharge = hasHamonCharge();
		charge = null;
		clientHasCharge = false;
		if (hadCharge) {
			syncChargeToTrackingAndSelf();
		}
	}

	@Override
	public void tick() {
		if (entity.isRemoved()) {
			clear();
			return;
		}
		if (entity.level().isClientSide()) {
			if (clientHasCharge) {
				tickClientChargeEffects();
			}
			return;
		}
		if (charge == null) {
			return;
		}
		charge.tick(entity, null, entity.level(), entity.getBoundingBox().inflate(1.0D));
		if (charge.shouldBeRemoved()) {
			charge = null;
			syncChargeToTrackingAndSelf();
		}
	}

	private void tickClientChargeEffects() {
		HamonSparksLoopSound.playSparkSound(entity, entity.getBoundingBox().getCenter(), 1.0F, true);
		CustomParticlesHelper.createHamonSparkParticles(entity,
				entity.getRandomX(0.5D), entity.getRandomY(), entity.getRandomZ(0.5D), 1);
	}

	public void setClientHasCharge(boolean hasCharge) {
		this.clientHasCharge = hasCharge;
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (hasHamonCharge()) {
			PacketDistributor.sendToPlayer(trackingPlayer, TrHamonEntityChargePacket.entityCharge(entity.getId(), true));
		}
	}

	private void syncChargeToTrackingAndSelf() {
		if (!entity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
					TrHamonEntityChargePacket.entityCharge(entity.getId(), hasHamonCharge()));
		}
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		if (charge != null && !charge.shouldBeRemoved()) {
			tag.put("HamonCharge", charge.toNBT());
		}
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		if (tag.contains("HamonCharge", Tag.TAG_COMPOUND)) {
			charge = HamonCharge.fromNBT(tag.getCompound("HamonCharge"));
		}
		else {
			charge = null;
		}
	}

	public static EntityHamonChargeState get(Entity entity) {
		return entity.getData(ModDataAttachmentTypes.HAMON_CHARGE);
	}
}
