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
 * The switch covered the whole slot, so a variation bound for one input method leaves nothing
 * of the base under SHIFT. Separate binds keep the port's plain fallback (SHIFT only falls back
 * when nothing is bound).
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
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).isEmpty(),
				"an available SHIFT variation takes the whole slot: no base click under SHIFT");
		check(slot.getAll(KeyModifier.SHIFT, InputMethod.CLICK, NONE_AVAILABLE).equals(List.of(BASE_CLICK)),
				"a locked SHIFT variation leaves every input method on the base");
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

		// 1.16 Heavens Door: Return Pages (instant) is the shift variation of the held Tear Out a Page.
		InputsByKeyModifier clickOverHold = new ClientControlScheme.HotbarSlot(3).binds;
		bind(clickOverHold, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(clickOverHold, KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_VARIANT);
		check(clickOverHold.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).equals(List.of(SHIFT_VARIANT))
				&& clickOverHold.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).isEmpty(),
				"a SHIFT click variation over a held base must not leave the base hold under SHIFT");
		check(clickOverHold.getAll(KeyModifier.SHIFT, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(BASE))
				&& clickOverHold.getAll(KeyModifier.SHIFT, InputMethod.HOLD).equals(List.of(BASE)),
				"a locked SHIFT click variation leaves the held base under SHIFT");
		check(clickOverHold.getAll(KeyModifier.NONE, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(BASE))
				&& clickOverHold.getAll(KeyModifier.NONE, InputMethod.CLICK, SHIFT_AVAILABLE).isEmpty(),
				"without SHIFT the slot keeps its held base only");

		// 1.16 Spice Girl: the held bounce_her is the shift variation of the instant bounce.
		InputsByKeyModifier holdOverClick = new ClientControlScheme.HotbarSlot(4).binds;
		bind(holdOverClick, KeyModifier.NONE, InputMethod.CLICK, BASE_CLICK);
		bind(holdOverClick, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		check(holdOverClick.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(SHIFT_VARIANT))
				&& holdOverClick.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).isEmpty(),
				"a SHIFT hold variation over a clicked base must not leave the base click under SHIFT");

		// A slot without a variation for the held modifier still uses its base (Shift/Ctrl held to move).
		InputsByKeyModifier plain = new ClientControlScheme.HotbarSlot(5).binds;
		bind(plain, KeyModifier.NONE, InputMethod.HOLD, BASE);
		check(plain.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(BASE))
				&& plain.getAll(KeyModifier.CONTROL, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(BASE))
				&& plain.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).isEmpty(),
				"a slot without a modifier variation must keep falling back to its base");

		InputsByKeyModifier controlOverClick = new ClientControlScheme.HotbarSlot(6).binds;
		bind(controlOverClick, KeyModifier.NONE, InputMethod.CLICK, BASE_CLICK);
		bind(controlOverClick, KeyModifier.CONTROL, InputMethod.HOLD, CONTROL_VARIANT);
		check(controlOverClick.getAll(KeyModifier.CONTROL, InputMethod.CLICK, NONE_AVAILABLE).isEmpty()
				&& controlOverClick.getAll(KeyModifier.CONTROL, InputMethod.HOLD, NONE_AVAILABLE).equals(List.of(CONTROL_VARIANT)),
				"a CONTROL variation also takes the whole slot");

		InputsByKeyModifier bothMethods = new ClientControlScheme.HotbarSlot(7).binds;
		bind(bothMethods, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(bothMethods, KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_VARIANT);
		bind(bothMethods, KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_VARIANT);
		check(bothMethods.getAll(KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_AVAILABLE).equals(List.of(SHIFT_VARIANT))
				&& bothMethods.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(SHIFT_VARIANT)),
				"a variation bound on both input methods keeps both");

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
		InputsByKeyModifier separateClickOverHold = new InputsByKeyModifier();
		bind(separateClickOverHold, KeyModifier.NONE, InputMethod.HOLD, BASE);
		bind(separateClickOverHold, KeyModifier.SHIFT, InputMethod.CLICK, SHIFT_VARIANT);
		check(separateClickOverHold.getAll(KeyModifier.SHIFT, InputMethod.HOLD, SHIFT_AVAILABLE).equals(List.of(BASE)),
				"separate binds keep the per-input-method fallback");
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
