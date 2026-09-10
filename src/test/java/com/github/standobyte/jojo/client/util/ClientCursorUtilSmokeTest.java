package com.github.standobyte.jojo.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientCursorUtilSmokeTest {
	private static final String LEGACY_SELFTEST =
			"rotp_addon_suite_client_render_profile_v1";
	private static int assertions;

	private ClientCursorUtilSmokeTest() {}

	public static void main(String[] args) throws IOException {
		check(false, null, null, null, null, false, null,
				"ordinary clients retain native cursor behavior");
		check(false, null, null, "Default", null, false, null,
				"the foreground desktop is not isolated");
		check(false, null, null, null, " default ", false, null,
				"foreground desktop matching ignores case and surrounding whitespace");
		check(true, "true", null, null, null, false, null,
				"the Bridge property enables isolation without a desktop name");
		check(true, null, "true", null, null, false, null,
				"the Bridge environment enables isolation without a desktop name");
		check(true, " TrUe ", null, "Default", null, false, null,
				"explicit isolation overrides the foreground desktop fallback");
		check(false, "false", "true", "ROTP-FB-test", null, false, null,
				"an explicit property takes precedence over environment and desktop");
		check(false, null, "false", "ROTP-FB-test", null, false, null,
				"an explicit environment setting takes precedence over desktop fallback");
		check(true, "true", "false", "Default", null, false, null,
				"the property has priority over a conflicting environment setting");
		check(true, null, null, "ROTP-FB-test", null, false, null,
				"modern named desktops do not need the legacy selftest flag");
		check(true, null, null, null, "ROTP-FB-test", false, null,
				"the desktop environment supplies the compatibility fallback");
		check(false, null, null, "Default", "ROTP-FB-test", false, null,
				"the desktop property takes precedence over the desktop environment");
		check(true, " ", "true", "Default", null, false, null,
				"a blank property does not hide the configured environment");
		check(true, " ", " ", " ROTP-FB-test ", null, false, null,
				"blank isolation values use the named desktop fallback");
		check(true, null, null, " ", "ROTP-FB-test", false, null,
				"a blank desktop property does not hide the environment");
		check(false, " ", " ", " ", " ", false, null,
				"blank configuration retains ordinary frontend behavior");
		check(true, null, null, null, null, true, LEGACY_SELFTEST,
				"the existing visible-acceptance guard remains supported");
		check(true, "false", "false", "Default", null, true, LEGACY_SELFTEST,
				"a Bridge override cannot disable the legacy isolation guard");
		check(false, null, null, null, null, true, "other",
				"the legacy property alone does not classify an ordinary client");
		check(false, null, null, null, null, false, LEGACY_SELFTEST,
				"the legacy environment alone does not classify an ordinary client");
		verifyLogicalCursorRoute();
		System.out.println("ClientCursorUtil isolation smoke test passed ("
				+ assertions + " assertions).");
	}

	private static void check(boolean expected,
			String isolatedProperty, String isolatedEnvironment,
			String desktopProperty, String desktopEnvironment,
			boolean visibleAcceptance, String selftest, String message) {
		assertions++;
		boolean actual = ClientCursorUtil.suppressesNativeCursor(
				isolatedProperty, isolatedEnvironment,
				desktopProperty, desktopEnvironment, visibleAcceptance, selftest);
		if (actual != expected) {
			throw new AssertionError(message);
		}
	}

	private static void verifyLogicalCursorRoute() throws IOException {
		Path root = Path.of(System.getProperty("user.dir"));
		String source = Files.readString(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/util/ClientCursorUtil.java"));
		int move = source.indexOf("public static void moveCursor(");
		int bridge = source.indexOf("if (hasIsolatedBridgeInput())", move);
		int logicalX = source.indexOf("mouse.jojo_ripples$setXpos(x);", bridge);
		int logicalY = source.indexOf("mouse.jojo_ripples$setYpos(y);", logicalX);
		int bridgeReturn = source.indexOf("return;", logicalY);
		int window = source.indexOf("minecraft.getWindow().getWindow()", move);
		int legacyCallback = source.indexOf(".jojo_ripples$onMove(window, x, y);", move);
		int nativeWarp = source.indexOf("GLFW.glfwSetCursorPos(window, x, y);", move);
		assertions++;
		if (!(move >= 0 && bridge > move && logicalX > bridge
				&& logicalY > logicalX && bridgeReturn > logicalY
				&& window > bridgeReturn && legacyCallback > window
				&& nativeWarp > legacyCallback)) {
			throw new AssertionError(
					"isolated Bridge menus must return after logical coordinates, before callbacks or native warp");
		}
		String invoker = Files.readString(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/client/controls/MouseHandlerInvoker.java"));
		assertions++;
		if (!(invoker.contains("@Accessor(\"xpos\")")
				&& invoker.contains("void jojo_ripples$setXpos(double x);")
				&& invoker.contains("@Accessor(\"ypos\")")
				&& invoker.contains("void jojo_ripples$setYpos(double y);"))) {
			throw new AssertionError("logical cursor accessors must target the mapped MouseHandler coordinates");
		}
	}
}
