package rotp.core.client.ui.hud_power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 1.16 ActionsOverlayGui.resolveVisibleActionInSlot: a hotbar slot showed its Shift variation while Shift was held,
 * whatever the power, so a Stand hotbar shows what Shift + the use key will actually do (the input side already
 * resolves a Stand hotbar with the held modifier). A locked variation leaves the base (HotbarShiftFallbackSmokeTest).
 */
public final class StandHotbarShiftDisplaySmokeTest {
	private StandHotbarShiftDisplaySmokeTest() {}

	public static void run() {
		check(PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.SHIFT) == KeyModifier.SHIFT,
				"a Stand hotbar must show its Shift variations while Shift is held");
		check(PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.NONE) == KeyModifier.NONE
				&& PowerHudControlsElement.hotbarDisplayModifier(true, KeyModifier.CONTROL) == KeyModifier.NONE,
				"any other modifier leaves a Stand hotbar on its base abilities");
		check(PowerHudControlsElement.hotbarDisplayModifier(false, KeyModifier.CONTROL) == KeyModifier.CONTROL
				&& PowerHudControlsElement.hotbarDisplayModifier(false, KeyModifier.SHIFT) == KeyModifier.SHIFT,
				"other hotbars keep following the held modifier");

		String hud = squash(read("src/main/java/rotp/core/client/ui/hud_power/PowerHudControlsElement.java"));
		check(hud.contains(squash("KeyModifier hotbarDisplayModifier = hotbarDisplayModifier(standHotbarDisplay, modifier);"))
				&& hud.contains(squash("AbilityControlsEntry abilityEntry = slot.getBinds().getFirst(hotbarDisplayModifier, inputMethod);"))
				&& !hud.contains(squash("slot.getBaseBind(inputMethod)")),
				"the hotbar HUD must resolve every hotbar slot with the display modifier");
	}

	private static String read(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
