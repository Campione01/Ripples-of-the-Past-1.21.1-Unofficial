package com.github.standobyte.jojo.client.util;

import org.lwjgl.glfw.GLFW;

import com.github.standobyte.jojo.mixin.client.controls.MouseHandlerInvoker;

import net.minecraft.client.Minecraft;

public final class ClientCursorUtil {
	private static final String VISIBLE_ACCEPTANCE_PROPERTY =
			"rotp.addonApiSmoke.clientRenderVisibleOnly";
	private static final String SELFTEST_ENV = "ROTP_ADDON_CLIENT_SELFTEST";
	private static final String SELFTEST_ID =
			"rotp_addon_suite_client_render_profile_v1";
	private static final String BRIDGE_ISOLATED_PROPERTY =
			"mineclientBridge.isolatedInput";
	private static final String BRIDGE_ISOLATED_ENV =
			"MINECLIENT_BRIDGE_ISOLATED_INPUT";
	private static final String BRIDGE_DESKTOP_PROPERTY =
			"mineclientBridge.desktopName";
	private static final String BRIDGE_DESKTOP_ENV =
			"MINECLIENT_BRIDGE_DESKTOP_NAME";

	private ClientCursorUtil() {}

	public static boolean suppressesNativeCursor() {
		return suppressesNativeCursor(
				System.getProperty(BRIDGE_ISOLATED_PROPERTY),
				System.getenv(BRIDGE_ISOLATED_ENV),
				System.getProperty(BRIDGE_DESKTOP_PROPERTY),
				System.getenv(BRIDGE_DESKTOP_ENV),
				Boolean.getBoolean(VISIBLE_ACCEPTANCE_PROPERTY),
				System.getenv(SELFTEST_ENV));
	}

	static boolean suppressesNativeCursor(
			String isolatedProperty, String isolatedEnvironment,
			String desktopProperty, String desktopEnvironment,
			boolean visibleAcceptance, String selftest) {
		return visibleAcceptance && SELFTEST_ID.equals(selftest)
				|| hasIsolatedBridgeInput(isolatedProperty, isolatedEnvironment,
						desktopProperty, desktopEnvironment);
	}

	private static boolean hasIsolatedBridgeInput() {
		return hasIsolatedBridgeInput(
				System.getProperty(BRIDGE_ISOLATED_PROPERTY),
				System.getenv(BRIDGE_ISOLATED_ENV),
				System.getProperty(BRIDGE_DESKTOP_PROPERTY),
				System.getenv(BRIDGE_DESKTOP_ENV));
	}

	static boolean hasIsolatedBridgeInput(
			String isolatedProperty, String isolatedEnvironment,
			String desktopProperty, String desktopEnvironment) {
		String isolated = configuredValue(isolatedProperty, isolatedEnvironment);
		if (isolated != null) {
			return Boolean.parseBoolean(isolated);
		}
		String desktop = configuredValue(desktopProperty, desktopEnvironment);
		return desktop != null && !"Default".equalsIgnoreCase(desktop);
	}

	private static String configuredValue(String property, String environment) {
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		return environment != null && !environment.isBlank()
				? environment.trim() : null;
	}

	public static void moveCursor(Minecraft minecraft, double x, double y) {
		if (hasIsolatedBridgeInput()) {
			// Menu positioning is not an input callback or a native cursor warp.
			MouseHandlerInvoker mouse = (MouseHandlerInvoker) minecraft.mouseHandler;
			mouse.jojo_ripples$setXpos(x);
			mouse.jojo_ripples$setYpos(y);
			return;
		}
		long window = minecraft.getWindow().getWindow();
		if (suppressesNativeCursor()) {
			((MouseHandlerInvoker) minecraft.mouseHandler)
					.jojo_ripples$onMove(window, x, y);
		}
		else {
			GLFW.glfwSetCursorPos(window, x, y);
		}
	}
}
