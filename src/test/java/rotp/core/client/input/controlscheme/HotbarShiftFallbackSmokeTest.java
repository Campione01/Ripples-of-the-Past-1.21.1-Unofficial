package rotp.core.client.input.controlscheme;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.InputsByKeyModifier;
import rotp.core.powersystem.ability.controls.InputMethod;

import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 1.16 (ActionsOverlayGui#resolveVisibleActionInSlot) switched a hotbar slot to its SHIFT
 * variation only once that variation was unlocked; otherwise SHIFT kept the base ability.
 * Separate binds keep the port's plain fallback (SHIFT only falls back when nothing is bound).
 */
public final class HotbarShiftFallbackSmokeTest {
	private static final AbilityControlsEntry BASE = new AbilityControlsEntry(null, "base");
	private static final AbilityControlsEntry BASE_CLICK = new AbilityControlsEntry(null, "base_click");
	private static final AbilityControlsEntry SHIFT_VARIANT = new AbilityControlsEntry(null, "shift_variant");
	private static final AbilityControlsEntry CONTROL_VARIANT = new AbilityControlsEntry(null, "control_variant");
	private static final Predicate<AbilityControlsEntry> SHIFT_AVAILABLE = entry -> entry == SHIFT_VARIANT;
	private static final Predicate<AbilityControlsEntry> NONE_AVAILABLE = entry -> false;

	private HotbarShiftFallbackSmokeTest() {}

	public static void run() {
		InputsByKeyModifier slot = new ClientControlScheme.HotbarSlot(0).binds;
		bind(slot, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(slot, KeyModifier.NONE, InputMethod.CLICK, BASE_CLICK);
		bind(slot, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		bind(slot, KeyModifier.CONTROL, InputMethod.HOLD, CONTROL_VARIANT);

		check(slot.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(SHIFT_VARIANT)),
				"an available SHIFT variation must be used");
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(BASE)),
				"an unavailable SHIFT variation must leave SHIFT on the base ability");
		// Entries without a power class are never available: the production availability check.
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.HOLD).equals(List.of(BASE))
				&& slot.getFirst(KeyModifier.SHIFT, InputMethod.HOLD) == BASE,
				"the public lookup must fall back through the real availability check");
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).equals(List.of(BASE_CLICK)),
				"a missing SHIFT bind must fall back to the base bind of the same input method");
		check(slot.getAll(KeyModifier.NONE, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(BASE))
				&& slot.getAll(KeyModifier.NONE, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(BASE)),
				"without a modifier the base bind must be used whatever is available");
		check(slot.getAll(KeyModifier.CONTROL, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(CONTROL_VARIANT)),
				"only SHIFT falls back on availability; CONTROL keeps its variation");

		InputsByKeyModifier noBase = new ClientControlScheme.HotbarSlot(1).binds;
		bind(noBase, KeyModifier.NONE, InputMethod.CLICK, BASE_CLICK);
		bind(noBase, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		check(noBase.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(SHIFT_VARIANT)),
				"without a base bind for that input method the SHIFT entry must stay");

		InputsByKeyModifier shiftOnly = new ClientControlScheme.HotbarSlot(2).binds;
		bind(shiftOnly, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		check(shiftOnly.getAll(KeyModifier.NONE, InputMethod.HOLD, SHIFT_AVAILABLE).isEmpty()
				&& shiftOnly.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(SHIFT_VARIANT)),
				"a slot without base binds must not invent one");

		InputsByKeyModifier separate = new InputsByKeyModifier();
		bind(separate, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(separate, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		check(separate.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(SHIFT_VARIANT))
				&& separate.getAll(KeyModifier.SHIFT, InputMethod.HOLD).equals(List.of(SHIFT_VARIANT)),
				"separate binds must keep an unavailable SHIFT entry");
		check(separate.getAll(KeyModifier.SHIFT, InputMethod.CLICK, NONE_AVAILABLE).isEmpty(),
				"separate binds must not borrow another input method");
		InputsByKeyModifier separateNoShift = new InputsByKeyModifier();
		bind(separateNoShift, KeyModifier.NONE, InputMethod.HOLD, BASE);
		check(separateNoShift.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(BASE)),
				"separate binds must still fall back when no SHIFT entry is bound");
	}

	private static void bind(InputsByKeyModifier binds, KeyModifier modifier,
			InputMethod inputMethod, AbilityControlsEntry entry) {
		Map<InputMethod, List<AbilityControlsEntry>> byMethod = binds.movesByModifier
				.computeIfAbsent(modifier, m -> new EnumMap<>(InputMethod.class));
		byMethod.put(inputMethod, List.of(entry));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
