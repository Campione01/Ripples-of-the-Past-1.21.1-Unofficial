package com.github.standobyte.jojoimpl.stands.boyiiman;

import java.util.Optional;

import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.mojang.datafixers.util.Pair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

public class BoyIIManStandPartTakenEffect extends StandEffectInstance {
	private StandInstance partsTaken;

	public BoyIIManStandPartTakenEffect(StandInstance partsTaken) {
		this(ModStandAbilities.EFFECT_BIIM_STAND_PART_TAKE.get());
		this.partsTaken = partsTaken;
	}

	public BoyIIManStandPartTakenEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		needsTarget = true;
	}

	public StandInstance getPartsTaken() {
		return partsTaken;
	}

	@Override
	protected void start() {}

	@Override
	protected void tick() {}

	@Override
	protected void stop() {
		if (!level.isClientSide() && partsTaken != null) {
			LivingEntity target = getTargetLiving();
			if (target != null) {
				StandPower targetPower = PowerClass.STAND.attachGet(target);
				if (!targetPower.hasPower()) {
					targetPower.setStandInstance(Optional.of(partsTaken.copy()));
				}
				else {
					targetPower.getStandInstance().ifPresent(stand -> {
						if (stand.getStandId().equals(partsTaken.getStandId())) {
							partsTaken.getAllParts().forEach(part -> {
								if (!stand.hasPart(part)) {
									stand.addPart(part);
								}
							});
						}
					});
				}
			}
		}
	}

	@Override
	public void writeAdditionalPacketData(FriendlyByteBuf buf, boolean sendingToUser) {
		super.writeAdditionalPacketData(buf, sendingToUser);
		buf.writeBoolean(partsTaken != null);
		if (partsTaken != null) {
			StandInstance.NetworkData.NETWORK_CODEC.encode(buf, StandInstance.NetworkData.wrap(partsTaken));
		}
	}

	@Override
	public void readAdditionalPacketData(FriendlyByteBuf buf, boolean clientIsUser) {
		super.readAdditionalPacketData(buf, clientIsUser);
		if (buf.readBoolean()) {
			partsTaken = StandInstance.NetworkData.NETWORK_CODEC.decode(buf).get();
		}
		else {
			partsTaken = null;
		}
	}

	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		super.writeAdditionalSaveData(nbt);
		if (partsTaken != null) {
			StandInstance.CODEC.encodeStart(NbtOps.INSTANCE, partsTaken)
					.ifSuccess(partsTag -> nbt.put("PartsTaken", partsTag));
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("PartsTaken")) {
			StandInstance.CODEC.decode(NbtOps.INSTANCE, nbt.get("PartsTaken")).result()
					.map(Pair::getFirst)
					.ifPresent(stand -> this.partsTaken = stand);
		}
	}
}
