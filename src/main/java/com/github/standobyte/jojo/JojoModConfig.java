package com.github.standobyte.jojo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mechanics.resolve.ResolveCounter;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.network.s2c.CommonConfigPacket;
import com.github.standobyte.jojo.network.s2c.ResetSyncedCommonConfigPacket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

public class JojoModConfig {
	public static final ModConfigSpec COMMON_SPEC;
	private static final Common COMMON_FROM_FILE;
	private static final Common COMMON_SYNCED_TO_CLIENT;

	static {
		Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
		COMMON_FROM_FILE = specPair.getLeft();
		COMMON_SPEC = specPair.getRight();
		COMMON_SYNCED_TO_CLIENT = Common.syncedDefaults();
	}

	public static Common getCommonConfigInstance(boolean isClientSide) {
		return isClientSide ? COMMON_SYNCED_TO_CLIENT : COMMON_FROM_FILE;
	}

	public static float getResolveLevelMax(boolean isClientSide, int level) {
		List<? extends Double> values = getCommonConfigInstance(isClientSide).resolveLvlPoints.get();
		if (values.isEmpty()) {
			return ResolveCounter.DEFAULT_MAX_RESOLVE_VALUES[0];
		}
		int index = Mth.clamp(level, 0, values.size() - 1);
		return values.get(index).floatValue();
	}

	public static void syncWithClient(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new CommonConfigPacket(new Common.SyncedValues(COMMON_FROM_FILE)));
	}

	public static void resetClientSyncedConfig(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new ResetSyncedCommonConfigPacket());
	}

	public static void applySyncedConfig(Common.SyncedValues values) {
		COMMON_SYNCED_TO_CLIENT.apply(values);
	}

	public static void resetSyncedConfig() {
		COMMON_SYNCED_TO_CLIENT.resetSyncedValues();
	}

	public static class Common {
		public final ConfigValue<Boolean> keepStandOnDeath;
		public final ConfigValue<Boolean> keepVampirismOnDeath;
		public final ConfigValue<Boolean> dropStandDisc;
		public final ConfigValue<Boolean> skipStandProgression;
		public final ConfigValue<List<? extends Double>> resolveLvlPoints;
		public final ConfigValue<Integer> timeStopChunkRange;
		public final ConfigValue<Double> timeStopDamageMultiplier;
		public final ConfigValue<Double> standDamageMultiplier;
		public final ConfigValue<Double> standResistanceMultiplier;
		public final ConfigValue<Double> hamonPointsMultiplier;
		public final ConfigValue<Double> breathingTrainingMultiplier;
		public final ConfigValue<Boolean> breathingTrainingDeterioration;
		public final ConfigValue<Boolean> hamonEnergyTicksDown;
		public final ConfigValue<List<? extends Double>> maxBloodMultiplier;
		public final ConfigValue<List<? extends Double>> bloodDrainMultiplier;
		public final ConfigValue<List<? extends Double>> bloodTickDown;
		public final ConfigValue<List<? extends Double>> bloodHealCost;
		public final ConfigValue<Boolean> vampiresAggroMobs;
		public final ConfigValue<Boolean> undeadMobsSunDamage;
		public final ConfigValue<Integer> vampirismCuringDuration;
		public final ConfigValue<Boolean> standStamina;
		public final ConfigValue<Boolean> soulAscension;
		public final ConfigValue<Boolean> saveDestroyedBlocks;
		public final ConfigValue<Boolean> endermenBeyondTimeSpace;

		private Common(ModConfigSpec.Builder builder) {
			builder.push("Keep Powers After Death");
			keepStandOnDeath = ConfigValue.fromSpec(
					builder.translation("jojo.config.keepStandOnDeath")
					.define("keepStandOnDeath", true), true);
			keepVampirismOnDeath = ConfigValue.fromSpec(
					builder.comment("The weak vampirism version from the Blood Gift ability will not be kept.")
					.translation("jojo.config.keepVampirismOnDeath")
					.define("keepVampirismOnDeath", true), true);
			dropStandDisc = ConfigValue.fromSpec(
					builder.comment("If enabled, Stand users drop their Stand's Disc upon death.",
							"Works only when keepStandOnDeath is set to false.")
					.translation("jojo.config.dropStandDisc")
					.define("dropStandDisc", false), false);
			builder.pop();

			builder.push("Stand settings");
			builder.push("Stand Progression");
			skipStandProgression = ConfigValue.fromSpec(
					builder.comment("Whether or not all of the abilities are unlocked after gaining a Stand in Survival.")
					.translation("jojo.config.skipStandProgression")
					.define("skipStandProgression", false), false);
			resolveLvlPoints = ConfigValue.fromSpec(
					builder.comment("Max resolve points at each Resolve level (starting from 0).",
							"Decrease these values to make getting to each level easier.",
							"All values must be higher than 0.")
					.translation("jojo.config.resolveLvlPoints")
					.defineList("resolveLvlPoints", JojoModConfig::defaultResolveLvlPoints, JojoModConfig::isPositiveFiniteDouble),
					defaultResolveLvlPoints());
			builder.pop();

			builder.push("Time Stop");
			timeStopChunkRange = ConfigValue.fromSpec(
					builder.comment("Range of Time Stop ability in chunks.",
							"If set to 0, the whole dimension is frozen in time.",
							"Defaults to 12.")
					.translation("jojo.config.timeStopChunkRange")
					.defineInRange("timeStopChunkRange", 12, 0, Integer.MAX_VALUE), 12);
			timeStopDamageMultiplier = ConfigValue.fromSpec(
					builder.comment("Damage multiplier for entities frozen in time.")
					.translation("jojo.config.timeStopDamageMultiplier")
					.defineInRange("timeStopDamageMultiplier", 1.0D, 0.0D, 1.0D), 1.0D);
			builder.pop();

			standDamageMultiplier = ConfigValue.fromSpec(
					builder.comment("Damage multiplier applied to all Stands.")
					.translation("jojo.config.standPowerMultiplier")
					.defineInRange("standPowerMultiplier", 1.0D, 0.0D, 128.0D), 1.0D);
			standResistanceMultiplier = ConfigValue.fromSpec(
					builder.comment("Damage resistance applied to all Stands.")
					.translation("jojo.config.standResistanceMultiplier")
					.defineInRange("standResistanceMultiplier", 1.0D, 1.0D, 128.0D), 1.0D);

			builder.push("Hamon settings");
			hamonPointsMultiplier = ConfigValue.fromSpec(
					builder.comment("Multiplier for Hamon Strength and Control training from actions.")
					.translation("jojo.config.hamonPointsMultiplier")
					.defineInRange("hamonPointsMultiplier", 1.0D, 0.0D, 5000.0D), 1.0D);
			breathingTrainingMultiplier = ConfigValue.fromSpec(
					builder.comment("Multiplier for positive Hamon breathing training progress.")
					.translation("jojo.config.breathingTrainingMultiplier")
					.defineInRange("breathingTrainingMultiplier", 1.0D, 0.0D, 100.0D), 1.0D);
			breathingTrainingDeterioration = ConfigValue.fromSpec(
					builder.comment("If enabled, Hamon breathing training can decrease on days without enough exercises.")
					.translation("jojo.config.breathingTrainingDeterioration")
					.define("breathingTrainingDeterioration", true), true);
			hamonEnergyTicksDown = ConfigValue.fromSpec(
					builder.comment("Whether or not Hamon energy ticks down a few seconds after the user performs Hamon Breath.")
					.translation("jojo.config.hamonEnergyTicksDown")
					.define("hamonEnergyTicksDown", true), true);
			builder.pop();
			maxBloodMultiplier = ConfigValue.fromSpec(
					builder.comment("Max vampire blood multiplier by difficulty.",
							"Values are used for Peaceful, Easy, Normal and Hard respectively.")
					.translation("jojo.config.maxBloodMultiplier")
					.defineList("maxBloodMultiplier", JojoModConfig::defaultMaxBloodMultiplier,
							JojoModConfig::isPositiveFiniteDouble), defaultMaxBloodMultiplier());
			bloodDrainMultiplier = ConfigValue.fromSpec(
					builder.comment("Blood and energy gain multiplier from blood-draining abilities by difficulty.",
							"Values are used for Peaceful, Easy, Normal and Hard respectively.")
					.translation("jojo.config.bloodDrainMultiplier")
					.defineList("bloodDrainMultiplier", JojoModConfig::defaultBloodDrainMultiplier,
							JojoModConfig::isNonNegativeFiniteDouble), defaultBloodDrainMultiplier());
			bloodTickDown = ConfigValue.fromSpec(
					builder.comment("Vampire blood decrease per tick by difficulty.",
							"Values are used for Peaceful, Easy, Normal and Hard respectively.")
					.translation("jojo.config.bloodTickDown")
					.defineList("bloodTickDown", JojoModConfig::defaultBloodTickDown,
							JojoModConfig::isNonNegativeFiniteDouble), defaultBloodTickDown());
			bloodHealCost = ConfigValue.fromSpec(
					builder.comment("Blood consumed for each point of natural healing by difficulty.",
							"Values are used for Peaceful, Easy, Normal and Hard respectively.")
					.translation("jojo.config.bloodHealCost")
					.defineList("bloodHealCost", JojoModConfig::defaultBloodHealCost,
							JojoModConfig::isNonNegativeFiniteDouble), defaultBloodHealCost());
			vampiresAggroMobs = ConfigValue.fromSpec(
					builder.comment("Whether hostile mobs target vampire players normally.")
					.translation("jojo.config.vampiresAggroMobs")
					.define("vampiresAggroMobs", false), false);
			undeadMobsSunDamage = ConfigValue.fromSpec(
					builder.comment("Whether non-player undead mobs take sunlight damage from this mod.")
					.translation("jojo.config.undeadMobsSunDamage")
					.define("undeadMobsSunDamage", false), false);
			vampirismCuringDuration = ConfigValue.fromSpec(
					builder.comment("Duration of the vampirism curing process in ticks.")
					.translation("jojo.config.vampirismCuringDuration")
					.defineInRange("vampirismCuringDuration", 48000, 1, Integer.MAX_VALUE), 48000);
			standStamina = ConfigValue.fromSpec(
					builder.comment("Whether or not Stand stamina mechanic is enabled.")
					.translation("jojo.config.standStamina")
					.define("standStamina", true), true);
			soulAscension = ConfigValue.fromSpec(
					builder.translation("jojo.config.soulAscension")
					.define("soulAscension", true), true);
			builder.pop();

			saveDestroyedBlocks = ConfigValue.fromSpec(
					builder.comment("Whether or not blocks which can be restored by Crazy Diamond's terrain restoration ability are saved in the save files.",
							"It may cause longer saving and loading time for larger worlds.")
					.translation("jojo.config.saveDestroyedBlocks")
					.define("saveDestroyedBlocks", false), false);
			endermenBeyondTimeSpace = ConfigValue.fromSpec(
					builder.comment("Disable this to make endermen also be frozen in stopped time.",
							"But what if there is an in-mod lore reason for this...")
					.translation("jojo.config.endermenBeyondTimeSpace")
					.define("endermenBeyondTimeSpace", true), true);
		}

		private Common() {
			keepStandOnDeath = ConfigValue.synced(true);
			keepVampirismOnDeath = ConfigValue.synced(true);
			dropStandDisc = ConfigValue.synced(false);
			skipStandProgression = ConfigValue.synced(false);
			resolveLvlPoints = ConfigValue.synced(defaultResolveLvlPoints());
			timeStopChunkRange = ConfigValue.synced(12);
			timeStopDamageMultiplier = ConfigValue.synced(1.0D);
			standDamageMultiplier = ConfigValue.synced(1.0D);
			standResistanceMultiplier = ConfigValue.synced(1.0D);
			hamonPointsMultiplier = ConfigValue.synced(1.0D);
			breathingTrainingMultiplier = ConfigValue.synced(1.0D);
			breathingTrainingDeterioration = ConfigValue.synced(true);
			hamonEnergyTicksDown = ConfigValue.synced(true);
			maxBloodMultiplier = ConfigValue.synced(defaultMaxBloodMultiplier());
			bloodDrainMultiplier = ConfigValue.synced(defaultBloodDrainMultiplier());
			bloodTickDown = ConfigValue.synced(defaultBloodTickDown());
			bloodHealCost = ConfigValue.synced(defaultBloodHealCost());
			vampiresAggroMobs = ConfigValue.synced(false);
			undeadMobsSunDamage = ConfigValue.synced(false);
			vampirismCuringDuration = ConfigValue.synced(48000);
			standStamina = ConfigValue.synced(true);
			soulAscension = ConfigValue.synced(true);
			saveDestroyedBlocks = ConfigValue.synced(false);
			endermenBeyondTimeSpace = ConfigValue.synced(true);
		}

		private static Common syncedDefaults() {
			return new Common();
		}

		private void apply(SyncedValues values) {
			keepStandOnDeath.set(values.keepStandOnDeath);
			keepVampirismOnDeath.set(values.keepVampirismOnDeath);
			dropStandDisc.set(values.dropStandDisc);
			skipStandProgression.set(values.skipStandProgression);
			resolveLvlPoints.set(List.copyOf(values.resolveLvlPoints));
			timeStopChunkRange.set(values.timeStopChunkRange);
			standDamageMultiplier.set(values.standDamageMultiplier);
			standResistanceMultiplier.set(values.standResistanceMultiplier);
			hamonPointsMultiplier.set(values.hamonPointsMultiplier);
			breathingTrainingMultiplier.set(values.breathingTrainingMultiplier);
			breathingTrainingDeterioration.set(values.breathingTrainingDeterioration);
			hamonEnergyTicksDown.set(values.hamonEnergyTicksDown);
			maxBloodMultiplier.set(List.copyOf(values.maxBloodMultiplier));
			bloodDrainMultiplier.set(List.copyOf(values.bloodDrainMultiplier));
			bloodTickDown.set(List.copyOf(values.bloodTickDown));
			vampirismCuringDuration.set(values.vampirismCuringDuration);
			standStamina.set(values.standStamina);
			soulAscension.set(values.soulAscension);
			saveDestroyedBlocks.set(values.saveDestroyedBlocks);
			endermenBeyondTimeSpace.set(values.endermenBeyondTimeSpace);
		}

		private void resetSyncedValues() {
			keepStandOnDeath.clearCache();
			keepVampirismOnDeath.clearCache();
			dropStandDisc.clearCache();
			skipStandProgression.clearCache();
			resolveLvlPoints.clearCache();
			timeStopChunkRange.clearCache();
			timeStopDamageMultiplier.clearCache();
			standDamageMultiplier.clearCache();
			standResistanceMultiplier.clearCache();
			hamonPointsMultiplier.clearCache();
			breathingTrainingMultiplier.clearCache();
			breathingTrainingDeterioration.clearCache();
			hamonEnergyTicksDown.clearCache();
			maxBloodMultiplier.clearCache();
			bloodDrainMultiplier.clearCache();
			bloodTickDown.clearCache();
			vampirismCuringDuration.clearCache();
			standStamina.clearCache();
			soulAscension.clearCache();
			saveDestroyedBlocks.clearCache();
			endermenBeyondTimeSpace.clearCache();
		}

		public static class SyncedValues {
			private static final int MAX_SYNCED_DOUBLE_VALUES = 64;
			private final boolean keepStandOnDeath;
			private final boolean keepVampirismOnDeath;
			private final boolean dropStandDisc;
			private final boolean skipStandProgression;
			private final List<Double> resolveLvlPoints;
			private final int timeStopChunkRange;
			private final double standDamageMultiplier;
			private final double standResistanceMultiplier;
			private final double hamonPointsMultiplier;
			private final double breathingTrainingMultiplier;
			private final boolean breathingTrainingDeterioration;
			private final boolean hamonEnergyTicksDown;
			private final List<Double> maxBloodMultiplier;
			private final List<Double> bloodDrainMultiplier;
			private final List<Double> bloodTickDown;
			private final int vampirismCuringDuration;
			private final boolean standStamina;
			private final boolean soulAscension;
			private final boolean saveDestroyedBlocks;
			private final boolean endermenBeyondTimeSpace;

			public SyncedValues(Common config) {
				this.keepStandOnDeath = config.keepStandOnDeath.get();
				this.keepVampirismOnDeath = config.keepVampirismOnDeath.get();
				this.dropStandDisc = config.dropStandDisc.get();
				this.skipStandProgression = config.skipStandProgression.get();
				this.resolveLvlPoints = config.resolveLvlPoints.get().stream().map(Double::valueOf).toList();
				this.timeStopChunkRange = config.timeStopChunkRange.get();
				this.standDamageMultiplier = config.standDamageMultiplier.get();
				this.standResistanceMultiplier = config.standResistanceMultiplier.get();
				this.hamonPointsMultiplier = config.hamonPointsMultiplier.get();
				this.breathingTrainingMultiplier = config.breathingTrainingMultiplier.get();
				this.breathingTrainingDeterioration = config.breathingTrainingDeterioration.get();
				this.hamonEnergyTicksDown = config.hamonEnergyTicksDown.get();
				this.maxBloodMultiplier = config.maxBloodMultiplier.get().stream().map(Double::valueOf).toList();
				this.bloodDrainMultiplier = config.bloodDrainMultiplier.get().stream().map(Double::valueOf).toList();
				this.bloodTickDown = config.bloodTickDown.get().stream().map(Double::valueOf).toList();
				this.vampirismCuringDuration = config.vampirismCuringDuration.get();
				this.standStamina = config.standStamina.get();
				this.soulAscension = config.soulAscension.get();
				this.saveDestroyedBlocks = config.saveDestroyedBlocks.get();
				this.endermenBeyondTimeSpace = config.endermenBeyondTimeSpace.get();
			}

			public SyncedValues(RegistryFriendlyByteBuf buf) {
				byte flags = buf.readByte();
				this.keepStandOnDeath = (flags & 1) > 0;
				this.dropStandDisc = (flags & 2) > 0;
				this.skipStandProgression = (flags & 4) > 0;
				this.soulAscension = (flags & 8) > 0;
				this.saveDestroyedBlocks = (flags & 16) > 0;
				this.standStamina = (flags & 32) > 0;
				this.endermenBeyondTimeSpace = (flags & 64) > 0;
				this.breathingTrainingDeterioration = (flags & 0x80) != 0;
				this.keepVampirismOnDeath = buf.readBoolean();
				this.standDamageMultiplier = buf.readDouble();
				this.standResistanceMultiplier = buf.readDouble();
				this.hamonPointsMultiplier = buf.readDouble();
				this.breathingTrainingMultiplier = buf.readDouble();
				this.hamonEnergyTicksDown = buf.readBoolean();
				this.timeStopChunkRange = Math.max(0, buf.readVarInt());
				int resolvePointsCount = NetworkPayloadValidation.requireCollectionSize(
						buf.readVarInt(), MAX_SYNCED_DOUBLE_VALUES,
						"resolve level point");
				List<Double> resolvePoints = new ArrayList<>(resolvePointsCount);
				for (int i = 0; i < resolvePointsCount; i++) {
					double value = buf.readDouble();
					if (value > 0 && Double.isFinite(value)) {
						resolvePoints.add(value);
					}
				}
				this.resolveLvlPoints = resolvePoints.isEmpty() ? defaultResolveLvlPoints() : List.copyOf(resolvePoints);
				this.maxBloodMultiplier = readDoubleList(buf, defaultMaxBloodMultiplier(), true);
				this.bloodDrainMultiplier = readDoubleList(buf, defaultBloodDrainMultiplier(), false);
				this.bloodTickDown = readDoubleList(buf, defaultBloodTickDown(), false);
				this.vampirismCuringDuration = Math.max(1, buf.readVarInt());
			}

			public void writeToBuf(RegistryFriendlyByteBuf buf) {
				byte flags = 0;
				if (keepStandOnDeath) flags |= 1;
				if (dropStandDisc) flags |= 2;
				if (skipStandProgression) flags |= 4;
				if (soulAscension) flags |= 8;
				if (saveDestroyedBlocks) flags |= 16;
				if (standStamina) flags |= 32;
				if (endermenBeyondTimeSpace) flags |= 64;
				if (breathingTrainingDeterioration) flags |= (byte) 0x80;
				buf.writeByte(flags);
				buf.writeBoolean(keepVampirismOnDeath);
				buf.writeDouble(standDamageMultiplier);
				buf.writeDouble(standResistanceMultiplier);
				buf.writeDouble(hamonPointsMultiplier);
				buf.writeDouble(breathingTrainingMultiplier);
				buf.writeBoolean(hamonEnergyTicksDown);
				buf.writeVarInt(timeStopChunkRange);
				NetworkPayloadValidation.requireOutboundCollectionSize(
						resolveLvlPoints.size(), MAX_SYNCED_DOUBLE_VALUES,
						"resolve level point");
				buf.writeVarInt(resolveLvlPoints.size());
				for (double value : resolveLvlPoints) {
					buf.writeDouble(value);
				}
				writeDoubleList(buf, maxBloodMultiplier);
				writeDoubleList(buf, bloodDrainMultiplier);
				writeDoubleList(buf, bloodTickDown);
				buf.writeVarInt(vampirismCuringDuration);
			}

			private static List<Double> readDoubleList(RegistryFriendlyByteBuf buf, List<Double> defaults,
					boolean positiveOnly) {
				int count = NetworkPayloadValidation.requireCollectionSize(
						buf.readVarInt(), MAX_SYNCED_DOUBLE_VALUES,
						"synced multiplier");
				List<Double> values = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					double value = buf.readDouble();
					if (Double.isFinite(value) && (positiveOnly ? value > 0 : value >= 0)) {
						values.add(value);
					}
				}
				return values.isEmpty() ? defaults : List.copyOf(values);
			}

			private static void writeDoubleList(RegistryFriendlyByteBuf buf, List<Double> values) {
				NetworkPayloadValidation.requireOutboundCollectionSize(
						values.size(), MAX_SYNCED_DOUBLE_VALUES,
						"synced multiplier");
				buf.writeVarInt(values.size());
				for (double value : values) {
					buf.writeDouble(value);
				}
			}
		}
	}

	public static class ConfigValue<T> {
		private final T defaultValue;
		private final Supplier<T> getter;
		private final Consumer<T> setter;
		private T syncedValue;
		private boolean syncedValueSet;

		private ConfigValue(T defaultValue, Supplier<T> getter, Consumer<T> setter) {
			this.defaultValue = defaultValue;
			this.getter = getter;
			this.setter = setter;
		}

		private static <T> ConfigValue<T> fromSpec(ModConfigSpec.ConfigValue<T> value, T defaultValue) {
			return new ConfigValue<>(defaultValue, value::get, null);
		}

		private static <T> ConfigValue<T> synced(T defaultValue) {
			return new ConfigValue<>(defaultValue, null, null);
		}

		public T get() {
			if (getter != null) {
				try {
					T value = getter.get();
					return value != null ? value : defaultValue;
				}
				catch (IllegalStateException | NullPointerException e) {
					JojoMod.LOGGER.debug("ROTP common config value was requested before config load; using default.", e);
				}
			}
			return syncedValueSet ? syncedValue : defaultValue;
		}

		public void set(T value) {
			if (setter != null) {
				setter.accept(value);
			}
			else {
				this.syncedValue = value;
				this.syncedValueSet = true;
			}
		}

		public void clearCache() {
			this.syncedValue = null;
			this.syncedValueSet = false;
		}
	}

	private static List<Double> defaultResolveLvlPoints() {
		List<Double> values = new ArrayList<>(ResolveCounter.DEFAULT_MAX_RESOLVE_VALUES.length);
		for (float value : ResolveCounter.DEFAULT_MAX_RESOLVE_VALUES) {
			values.add((double) value);
		}
		return List.copyOf(values);
	}

	private static List<Double> defaultBloodDrainMultiplier() {
		return List.of(0.0D, 1.0D, 1.75D, 2.5D);
	}

	private static List<Double> defaultMaxBloodMultiplier() {
		return List.of(1.0D, 1.0D, 1.0D, 1.0D);
	}

	private static List<Double> defaultBloodTickDown() {
		return List.of(0.13889D, 0.00278D, 0.00278D, 0.0D);
	}

	private static List<Double> defaultBloodHealCost() {
		return List.of(10.0D, 4.0D, 2.0D, 1.0D);
	}

	private static boolean isPositiveFiniteDouble(Object value) {
		if (value instanceof Double doubleValue) {
			return doubleValue > 0 && Double.isFinite(doubleValue);
		}
		if (value instanceof Integer intValue) {
			return intValue > 0;
		}
		return false;
	}

	private static boolean isNonNegativeFiniteDouble(Object value) {
		if (value instanceof Double doubleValue) {
			return doubleValue >= 0 && Double.isFinite(doubleValue);
		}
		if (value instanceof Integer intValue) {
			return intValue >= 0;
		}
		return false;
	}
}
