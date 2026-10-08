package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 ActionsOverlayGui.renderActionIcon draws the icon of an action that cannot be used with
 * glColor(0.2, 0.2, 0.2, 0.5 * hotbarAlpha). The ARGB multiplier of the port is 0x80333333, applied to the
 * hotbar tint (0xFFFFFFFF, or 0x40FFFFFF while the controls are disabled); the selection wheel uses the same value.
 */
public final class UnusableAbilityIconTintSmokeTest {
	private UnusableAbilityIconTintSmokeTest() {}

	public static void main(String[] args) throws Exception {
		ResourceLocation namespace = ResourceLocation.fromNamespaceAndPath("rotp_test", "power");
		AbilityType<Ability> type = new AbilityType<>(ResourceLocation.fromNamespaceAndPath("rotp_test", "tint"), Ability::new);
		Ability ability = type.createInstance(new AbilityId(null, namespace, "tint"));
		AbilityConditionCheck usable = check(ability, ConditionCheck.POSITIVE);
		AbilityConditionCheck refused = check(ability, ConditionCheck.NEGATIVE);
		AbilityConditionCheck keepsHolding = check(ability, ConditionCheck.NEGATIVE_CONTINUE_HOLD);

		int opaque = 0xFFFFFFFF;
		int dimmedControls = 0x40FFFFFF;
		assertEquals(opaque, PowerHudControlsElement.abilityColor(opaque, usable), "a usable icon must keep the plain tint");
		assertEquals(0x80333333, PowerHudControlsElement.abilityColor(opaque, refused),
				"an unusable icon must be drawn at brightness 0.2 and alpha 0.5");
		assertEquals(0x80333333, PowerHudControlsElement.abilityColor(opaque, keepsHolding),
				"an unusable icon must be drawn at brightness 0.2 and alpha 0.5");
		assertEquals(0x20333333, PowerHudControlsElement.abilityColor(dimmedControls, refused),
				"the alpha of the unusable look must be scaled by the hotbar alpha");
		assertEquals(dimmedControls, PowerHudControlsElement.abilityColor(dimmedControls, usable),
				"a usable icon must keep the hotbar alpha");

		Path root = Path.of(System.getProperty("user.dir"));
		String wheel = read(root.resolve("src/main/java/rotp/core/client/ui/AbilitySelectionWheel.java"));
		int icon = wheel.indexOf("int iconColor = ");
		int iconEnd = wheel.indexOf(';', icon);
		check(icon >= 0 && iconEnd > icon
				&& wheel.substring(icon, iconEnd).contains("PowerHudControlsElement.abilityColor("),
				"the selection wheel must take the unusable icon tint from the hotbar rule");
		check(!wheel.contains("0xFF606060"), "the selection wheel still has its own unusable icon colour");

		System.out.println("Unusable ability icon tint smoke test passed");
	}

	private static AbilityConditionCheck check(Ability ability, ConditionCheck condition) throws Exception {
		Constructor<AbilityConditionCheck> constructor = AbilityConditionCheck.class.getDeclaredConstructor(Ability.class);
		constructor.setAccessible(true);
		AbilityConditionCheck check = constructor.newInstance(ability);
		check.conditionCheck = condition;
		return check;
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected 0x" + Integer.toHexString(expected)
					+ " but was 0x" + Integer.toHexString(actual));
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
