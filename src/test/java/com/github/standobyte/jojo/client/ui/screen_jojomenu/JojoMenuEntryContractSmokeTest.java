package com.github.standobyte.jojo.client.ui.screen_jojomenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JojoMenuEntryContractSmokeTest {
	private JojoMenuEntryContractSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		Path menuPackage = root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/ui/"
						+ "screen_jojomenu");

		String menuTabs = read(menuPackage.resolve("JojoMenuTabs.java"));
		check(menuTabs.contains("powerClass.get(player)"),
				"menu power lookup must fall back to live player data");
		check(menuTabs.contains("return EDIT_CONTROL_SCHEMES;"),
				"menu key must have a non-null controls fallback");
		check(menuTabs.contains("ClientPowerCache was empty"),
				"stale power-cache recovery must be logged");
		check(menuTabs.contains(
				"power-backed category is currently active"),
				"controls fallback must be logged");
		String menuKeyContract = between(
				menuTabs,
				"public static Tab getTabToOpenOnMenuKey()",
				"public static Tab getTabToOpen(TabCategory category)");
		check(menuKeyContract.contains(
				"getPowerCategoryToOpenOnMenuKey(active)"),
				"menu key must select a power-backed category");
		check(menuKeyContract.contains("return EDIT_CONTROL_SCHEMES;")
				&& !menuKeyContract.contains("active.get(0)"),
				"global categories must not replace the controls fallback");

		String powerPriorityContract = between(
				menuTabs,
				"private static TabCategory getPowerCategoryToOpenOnMenuKey(",
				"public static Tab getTabToOpen(TabCategory category)");
		int selectedPower = powerPriorityContract.indexOf(
				"curCategory.powerClass != null");
		int standFallback = powerPriorityContract.indexOf(
				"active.contains(CATEGORY_STAND)");
		check(selectedPower >= 0 && standFallback > selectedPower,
				"an active selected power tab must precede the Stand fallback");
		check(powerPriorityContract.contains("return CATEGORY_STAND;")
				&& powerPriorityContract.contains(
						"category.powerClass != null"),
				"Stand must precede other power-backed categories");

		String controlsContract = between(
				menuTabs,
				"public static final Tab EDIT_CONTROL_SCHEMES",
				"\n\t\n\n}");
		check(controlsContract.contains(
				"new Tab(CATEGORY_CONTROLS, null, null)"),
				"controls fallback tab must remain registered");
		check(!controlsContract.contains("boolean isActive()"),
				"controls fallback must not depend on a power cache");

		String tab = read(menuPackage.resolve("Tab.java"));
		String category = read(menuPackage.resolve("TabCategory.java"));
		String controlScreen =
				read(menuPackage.resolve("ControlSchemeScreen.java"));
		check(tab.contains("JojoMenuTabs.getPowerForMenu(powerClass)"),
				"tab activation must use the resilient menu lookup");
		check(category.contains(
				"JojoMenuTabs.getPowerForMenu(powerClass)"),
				"category activation must use the resilient menu lookup");
		check(controlScreen.contains(
				"JojoMenuTabs.getPowerForMenu(powerClass)"),
				"controls screen must tolerate a stale cache");

		String standInfoScreen = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/"
						+ "standpower/client_screens/StandInfoScreen.java"));
		String standSkillsScreen = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/"
						+ "standpower/client_screens/StandSkillsScreen.java"));
		check(standInfoScreen.contains(
				"JojoMenuTabs.getPowerForMenu(PowerClass.STAND)"),
				"stand info screen must tolerate a stale cache");
		check(standSkillsScreen.contains(
				"JojoMenuTabs.getPowerForMenu(PowerClass.STAND)"),
				"stand skills screen must tolerate a stale cache");
		check(!standInfoScreen.contains(
				"ClientPowerCache.getPower(PowerClass.STAND)")
				&& !standSkillsScreen.contains(
						"ClientPowerCache.getPower(PowerClass.STAND)"),
				"stand menu screens must not re-enter the stale cache");
		check(menuTabs.contains(
				".withScreen(JojoMenuTabs::createStandInfoScreen)")
				&& menuTabs.contains(
						".withScreen(JojoMenuTabs::createStandSkillsScreen)"),
				"stand tabs must validate and pass one resolved power");

		String keybinds = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/input/"
						+ "VanillaKeybinds.java"));
		check(keybinds.contains("Failed to open the JoJo menu"),
				"menu open failure must emit a targeted error");
	}

	private static String between(
			String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0) {
			throw new AssertionError(
					"failed to isolate menu controls contract");
		}
		return source.substring(start, end);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
