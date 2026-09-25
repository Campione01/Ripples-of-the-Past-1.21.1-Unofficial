package rotp.core.api.stand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed factors on any Stand user's stamina, applied to the final StandPower#getMaxStamina and
 * StandPower#getStaminaTickGain (1.16 add-ons injected at their RETURN, e.g. the D4C Saint Corpse
 * StandPowerMixin and StaminaRegenMixin). Factors run on both sides, multiply in registration order and
 * should be cheap and side-effect free; a non-finite or negative factor is ignored.
 */
public final class StandStaminaModifiers {
	@FunctionalInterface
	public interface Factor {
		float factor(StandPower power);
	}

	private static final Map<ResourceLocation, Factor> MAX_STAMINA = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Factor> TICK_GAIN = new LinkedHashMap<>();
	// read every tick, so lock-free snapshots
	private static volatile List<Map.Entry<ResourceLocation, Factor>> maxStaminaSnapshot = List.of();
	private static volatile List<Map.Entry<ResourceLocation, Factor>> tickGainSnapshot = List.of();

	private StandStaminaModifiers() {}

	/** Multiplies a Stand user's max stamina. */
	public static synchronized void registerMaxStamina(ResourceLocation owner, Factor factor) {
		put(MAX_STAMINA, owner, factor, "max stamina");
		maxStaminaSnapshot = List.copyOf(MAX_STAMINA.entrySet());
	}

	/** Multiplies a Stand user's stamina gain per tick. */
	public static synchronized void registerTickGain(ResourceLocation owner, Factor factor) {
		put(TICK_GAIN, owner, factor, "stamina tick gain");
		tickGainSnapshot = List.copyOf(TICK_GAIN.entrySet());
	}

	@ApiStatus.Internal
	public static float applyToMaxStamina(StandPower power, float maxStamina) {
		return apply(maxStaminaSnapshot, power, maxStamina, "max stamina");
	}

	@ApiStatus.Internal
	public static float applyToTickGain(StandPower power, float tickGain) {
		return apply(tickGainSnapshot, power, tickGain, "stamina tick gain");
	}

	private static void put(Map<ResourceLocation, Factor> map, ResourceLocation owner, Factor factor, String kind) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(factor, "factor");
		if (map.putIfAbsent(owner, factor) != null) {
			throw new IllegalStateException("Duplicate Stand " + kind + " modifier: " + owner);
		}
	}

	private static float apply(List<Map.Entry<ResourceLocation, Factor>> snapshot, StandPower power,
			float value, String kind) {
		if (snapshot.isEmpty() || power == null) {
			return value;
		}
		float result = value;
		for (Map.Entry<ResourceLocation, Factor> entry : snapshot) {
			try {
				float factor = entry.getValue().factor(power);
				if (Float.isFinite(factor) && factor >= 0) {
					result *= factor;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Stand {} modifier {} failed.", kind, entry.getKey(), error);
			}
		}
		return result;
	}
}
