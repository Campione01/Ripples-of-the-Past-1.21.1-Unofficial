package com.github.standobyte.jojo.api.client.render;

import java.lang.reflect.Method;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

/**
 * Read-only compatibility state for addon-owned client render effects.
 *
 * <p>This API deliberately does not expose either Iris or Super Resolution
 * framebuffers. Addons should keep ownership of their own effect and use the
 * current Minecraft main target through {@link AddonPostEffect}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientRenderCompatibility {
	private static final String IRIS_API =
			"net.irisshaders.iris.api.v0.IrisApi";
	private static volatile IrisApiMethods irisApiMethods;

	public static Snapshot snapshot() {
		ModList mods = ModList.get();
		boolean irisLoaded = mods.isLoaded("iris");
		return new Snapshot(
				irisLoaded,
				irisLoaded && isIrisShaderPackInUse(),
				mods.isLoaded("super_resolution"));
	}

	private static boolean isIrisShaderPackInUse() {
		IrisApiMethods methods = irisApiMethods();
		if (methods == null) {
			return false;
		}
		try {
			Object irisApi = methods.getInstance().invoke(null);
			return irisApi != null && Boolean.TRUE.equals(
					methods.isShaderPackInUse().invoke(irisApi));
		}
		catch (ReflectiveOperationException | LinkageError ignored) {
			return false;
		}
	}

	private static IrisApiMethods irisApiMethods() {
		IrisApiMethods methods = irisApiMethods;
		if (methods != null) {
			return methods;
		}
		synchronized (ClientRenderCompatibility.class) {
			methods = irisApiMethods;
			if (methods != null) {
				return methods;
			}
			try {
				Class<?> irisApi = Class.forName(
						IRIS_API,
						false,
						ClientRenderCompatibility.class.getClassLoader());
				methods = new IrisApiMethods(
						irisApi.getMethod("getInstance"),
						irisApi.getMethod("isShaderPackInUse"));
				irisApiMethods = methods;
				return methods;
			}
			catch (ReflectiveOperationException | LinkageError ignored) {
				// Do not cache failure: Iris may not be visible until mod loading completes.
				return null;
			}
		}
	}

	private record IrisApiMethods(
			Method getInstance,
			Method isShaderPackInUse) {}

	public record Snapshot(
			boolean irisLoaded,
			boolean irisShaderPackInUse,
			boolean superResolutionLoaded) {}

	private ClientRenderCompatibility() {}
}
