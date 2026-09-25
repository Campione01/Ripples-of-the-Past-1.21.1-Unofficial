package rotp.core.api.timestop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed modifiers of a time-stop blink's reach in ticks, applied to the final
 * TimeStopBlinkAbility#getMaxImpliedTicks (1.16 add-ons injected at the RETURN of
 * TimeStopInstant#getMaxImpliedTicks). Modifiers run in registration order, each on the previous
 * result; a result of 0 means the user cannot blink. Modifiers should be side-effect free.
 */
public final class TimeStopTicksModifiers {
	@FunctionalInterface
	public interface Modifier {
		int modify(LivingEntity user, int ticks);
	}

	private static final Map<ResourceLocation, Modifier> MODIFIERS = new LinkedHashMap<>();

	private TimeStopTicksModifiers() {}

	public static synchronized void register(ResourceLocation owner, Modifier modifier) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(modifier, "modifier");
		if (MODIFIERS.putIfAbsent(owner, modifier) != null) {
			throw new IllegalStateException("Duplicate time-stop ticks modifier: " + owner);
		}
	}

	@ApiStatus.Internal
	public static int applyToBlink(LivingEntity user, int ticks) {
		if (user == null) {
			return ticks;
		}
		List<Map.Entry<ResourceLocation, Modifier>> snapshot;
		synchronized (TimeStopTicksModifiers.class) {
			if (MODIFIERS.isEmpty()) {
				return ticks;
			}
			snapshot = new ArrayList<>(MODIFIERS.entrySet());
		}
		int result = ticks;
		for (Map.Entry<ResourceLocation, Modifier> entry : snapshot) {
			try {
				// never below 0
				result = Math.max(0, entry.getValue().modify(user, result));
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Time-stop ticks modifier {} failed.", entry.getKey(), error);
			}
		}
		return result;
	}
}
