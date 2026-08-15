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

	private ClientCursorUtil() {}

	public static boolean suppressesNativeCursor() {
		return Boolean.getBoolean(VISIBLE_ACCEPTANCE_PROPERTY)
				&& SELFTEST_ID.equals(System.getenv(SELFTEST_ENV));
	}

	public static void moveCursor(Minecraft minecraft, double x, double y) {
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
