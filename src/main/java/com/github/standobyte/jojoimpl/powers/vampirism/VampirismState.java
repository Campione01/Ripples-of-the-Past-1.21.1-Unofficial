package com.github.standobyte.jojoimpl.powers.vampirism;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Slice 5b bundle 8 Vampirism per-user state holder.
 * Wraps {@link BloodEconomy} (5a API) so blood resource is persisted per user
 * across save/load and is the single point of consume/replenish for Vampirism abilities.
 */
public class VampirismState implements INBTSerializable<CompoundTag> {

	private BloodEconomy blood = new BloodEconomy();

	public VampirismState() {}

	public BloodEconomy blood() {
		return blood;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Blood", blood.current());
		tag.putFloat("MaxBlood", blood.max());
		tag.putFloat("DrainPerTick", blood.drainPerTick());
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		float max = tag.contains("MaxBlood") ? tag.getFloat("MaxBlood") : BloodEconomy.DEFAULT_MAX_BLOOD;
		this.blood = new BloodEconomy(max);
		float current = tag.contains("Blood") ? tag.getFloat("Blood") : max;
		float diff = current - blood.current();
		if (diff < 0) blood.consume(-diff);
		else if (diff > 0) blood.replenish(diff);
		if (tag.contains("DrainPerTick")) blood.setDrainPerTick(tag.getFloat("DrainPerTick"));
	}

	public static VampirismState get(LivingEntity user) {
		return user.getData(com.github.standobyte.jojo.init.ModDataAttachmentTypes.VAMPIRISM_STATE);
	}
}
