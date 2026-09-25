package rotp.core.impl.stands.silverchariot;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.standpower.ArmoredStandStats;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

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

	/** Rapier as ability checks see it on either side (1.16 synced HAS_RAPIER). */
	public static boolean hasRapier(@Nullable StandPower power) {
		return readEquipment(power, null, true, isClientView(power));
	}

	/** Armor as ability checks see it on either side (1.16 synced HAS_ARMOR). */
	public static boolean hasArmor(@Nullable StandPower power, @Nullable StandEntity stand) {
		return readEquipment(power, stand, false, isClientView(power));
	}

	/**
	 * The attachment is server-owned and never synced, so a client view reads the
	 * summoned Stand's synced equipment flags; the server always reads the attachment.
	 * Public with an explicit side for gametests.
	 */
	public static boolean readEquipment(@Nullable StandPower power, @Nullable StandEntity stand,
			boolean rapier, boolean clientView) {
		LivingEntity user = power != null ? power.getUser() : null;
		if (user == null) {
			return true;
		}
		if (clientView) {
			StandEntity shown = stand != null ? stand : power.getSummonedStandEntity();
			if (shown != null) {
				return rapier ? shown.isSilverChariotRapierVisible() : shown.isSilverChariotArmorVisible();
			}
		}
		SilverChariotState state = get(user);
		return state == null || (rapier ? state.hasRapier() : state.hasArmor());
	}

	// Gametest seam: forces the client view on the calling thread only.
	private static final ThreadLocal<Boolean> FORCED_CLIENT_VIEW = new ThreadLocal<>();

	/** Runs {@code body} with ability checks on this thread reading the client view. */
	public static <T> T withForcedClientView(Supplier<T> body) {
		Boolean previous = FORCED_CLIENT_VIEW.get();
		FORCED_CLIENT_VIEW.set(Boolean.TRUE);
		try {
			return body.get();
		}
		finally {
			if (previous == null) {
				FORCED_CLIENT_VIEW.remove();
			}
			else {
				FORCED_CLIENT_VIEW.set(previous);
			}
		}
	}

	private static boolean isClientView(@Nullable StandPower power) {
		if (Boolean.TRUE.equals(FORCED_CLIENT_VIEW.get())) {
			return true;
		}
		LivingEntity user = power != null ? power.getUser() : null;
		return user != null && user.level().isClientSide();
	}
}
