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
	private static final Method IRIS_GET_INSTANCE;
	private static final Method IRIS_IS_SHADER_PACK_IN_USE;

	static {
		Method getInstance = null;
		Method isShaderPackInUse = null;
		try {
			Class<?> irisApi = Class.forName(
					IRIS_API,
					false,
					ClientRenderCompatibility.class.getClassLoader());
			getInstance = irisApi.getMethod("getInstance");
			isShaderPackInUse =
					irisApi.getMethod("isShaderPackInUse");
		}
		catch (ReflectiveOperationException | LinkageError ignored) {}
		IRIS_GET_INSTANCE = getInstance;
		IRIS_IS_SHADER_PACK_IN_USE = isShaderPackInUse;
	}

	public static Snapshot snapshot() {
		ModList mods = ModList.get();
		boolean irisLoaded = mods.isLoaded("iris");
		return new Snapshot(
				irisLoaded,
				irisLoaded && isIrisShaderPackInUse(),
				mods.isLoaded("super_resolution"));
	}

	private static boolean isIrisShaderPackInUse() {
		if (IRIS_GET_INSTANCE == null
				|| IRIS_IS_SHADER_PACK_IN_USE == null) {
			return false;
		}
		try {
			Object irisApi = IRIS_GET_INSTANCE.invoke(null);
			return irisApi != null && Boolean.TRUE.equals(
					IRIS_IS_SHADER_PACK_IN_USE.invoke(irisApi));
		}
		catch (ReflectiveOperationException | LinkageError ignored) {
			return false;
		}
	}

	public record Snapshot(
			boolean irisLoaded,
			boolean irisShaderPackInUse,
			boolean superResolutionLoaded) {}

	private ClientRenderCompatibility() {}
}
