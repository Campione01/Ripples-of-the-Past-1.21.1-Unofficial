package com.github.standobyte.jojo.client.rendertype;

import java.lang.reflect.Method;

final class IrisShaderPipelineCompat {
	private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
	private static final Method GET_INSTANCE;
	private static final Method IS_SHADER_PACK_IN_USE;

	static {
		Method getInstance = null;
		Method isShaderPackInUse = null;
		try {
			Class<?> irisApi = Class.forName(IRIS_API, false, IrisShaderPipelineCompat.class.getClassLoader());
			getInstance = irisApi.getMethod("getInstance");
			isShaderPackInUse = irisApi.getMethod("isShaderPackInUse");
		}
		catch (ReflectiveOperationException | LinkageError ignored) {}
		GET_INSTANCE = getInstance;
		IS_SHADER_PACK_IN_USE = isShaderPackInUse;
	}

	static boolean isShaderPackInUse() {
		if (GET_INSTANCE == null || IS_SHADER_PACK_IN_USE == null) {
			return false;
		}
		try {
			Object irisApi = GET_INSTANCE.invoke(null);
			return irisApi != null && Boolean.TRUE.equals(IS_SHADER_PACK_IN_USE.invoke(irisApi));
		}
		catch (ReflectiveOperationException | LinkageError ignored) {
			return false;
		}
	}

	private IrisShaderPipelineCompat() {}
}
