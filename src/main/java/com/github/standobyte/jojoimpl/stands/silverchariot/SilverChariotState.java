package com.github.standobyte.jojoimpl.stands.silverchariot;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.powersystem.standpower.ArmoredStandStats;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Slice 5b SC family follow-up — per-user Silver Chariot state.
 *
 * <p>Stores armor / rapier flags and the current {@link ArmoredStandStats}
 * snapshot, attached to the user so actions and the summoned entity share one
 * authoritative state. A fresh summon resets the equipment to the legacy
 * entity defaults: rapier on, armor on.</p>
 */
public class SilverChariotState implements INBTSerializable<CompoundTag> {

	private boolean hasRapier = true;
	private boolean hasArmor = true;
	private int ticksAfterArmorRemoval;

	@Nullable
	private ArmoredStandStats armoredStats;

	public SilverChariotState() {
	}

	public boolean hasRapier() {
		return hasRapier;
	}

	public void setHasRapier(boolean v) {
		this.hasRapier = v;
	}

	public boolean hasArmor() {
		return hasArmor;
	}

	public void setHasArmor(boolean v) {
		if (this.hasArmor == v) {
			return;
		}
		this.hasArmor = v;
		if (v) {
			this.ticksAfterArmorRemoval = 0;
		}
	}

	public int ticksAfterArmorRemoval() {
		return ticksAfterArmorRemoval;
	}

	public void incrementTicksAfterArmorRemoval() {
		this.ticksAfterArmorRemoval++;
	}

	public void resetTicksAfterArmorRemoval() {
		this.ticksAfterArmorRemoval = 0;
	}

	@Nullable
	public ArmoredStandStats armoredStats() {
		return armoredStats;
	}

	public void setArmoredStats(@Nullable ArmoredStandStats armoredStats) {
		this.armoredStats = armoredStats;
	}

	public void resetEquipmentForSummon() {
		hasRapier = true;
		hasArmor = true;
		ticksAfterArmorRemoval = 0;
		armoredStats = null;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("HasRapier", hasRapier);
		tag.putBoolean("HasArmor", hasArmor);
		tag.putInt("TicksAfterArmorRemoval", ticksAfterArmorRemoval);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		this.hasRapier = !tag.contains("HasRapier") || tag.getBoolean("HasRapier");
		this.hasArmor = !tag.contains("HasArmor") || tag.getBoolean("HasArmor");
		this.ticksAfterArmorRemoval = tag.getInt("TicksAfterArmorRemoval");
	}

	public static SilverChariotState get(LivingEntity user) {
		return user.getData(ModDataAttachmentTypes.SILVER_CHARIOT_STATE);
	}
}
