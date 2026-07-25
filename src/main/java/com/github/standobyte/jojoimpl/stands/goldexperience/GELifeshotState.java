package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.network.s2c.TrGESplitConsciousnessPacket;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class GELifeshotState implements TickingEntityData, INBTSerializable<CompoundTag> {
	private final LivingEntity entity;
	private float lifeShotResist;
	private int lifeShotResistTicks;
	private int sendLifeshotKBTicks;

	public GELifeshotState(LivingEntity entity) {
		this.entity = entity;
		addTicking(entity);
	}

	public int onLifeShot(int maxDuration) {
		if (lifeShotResistTicks > 0) {
			lifeShotResist = Math.min(lifeShotResist + GoldExperienceEntityLifeshotAbility.REDUCTION_SHORT_DELAY, maxDuration);
		}
		else if (lifeShotResist > 0) {
			lifeShotResist = Math.min(lifeShotResist + GoldExperienceEntityLifeshotAbility.REDUCTION_LONG_DELAY, maxDuration);
		}
		lifeShotResistTicks = GoldExperienceEntityLifeshotAbility.RESIST_TICKS;
		return maxDuration - (int) lifeShotResist;
	}

	@Override
	public void tick() {
		tickLifeshotKnockback();
		if (lifeShotResistTicks > 0) {
			--lifeShotResistTicks;
		}
		if (lifeShotResistTicks == 0) {
			lifeShotResist = Math.max(lifeShotResist - GoldExperienceEntityLifeshotAbility.RESIST_TICK_DOWN, 0);
		}
	}

	public void setSendLifeshotNextTick() {
		sendLifeshotKBTicks = 2;
	}

	private void tickLifeshotKnockback() {
		if (sendLifeshotKBTicks > 0
				&& --sendLifeshotKBTicks == 0
				&& entity instanceof ServerPlayer player
				&& entity.hasEffect(ModStatusEffects.SENSORY_OVERLOAD)) {
			PacketDistributor.sendToPlayer(player, new TrGESplitConsciousnessPacket(entity.getDeltaMovement()));
		}
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("LifeShotTicks", lifeShotResistTicks);
		tag.putFloat("LifeShotResist", lifeShotResist);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		lifeShotResistTicks = tag.getInt("LifeShotTicks");
		lifeShotResist = tag.getFloat("LifeShotResist");
	}

	public static GELifeshotState get(LivingEntity entity) {
		return entity.getData(ModDataAttachmentTypes.GE_LIFESHOT_STATE);
	}
}
