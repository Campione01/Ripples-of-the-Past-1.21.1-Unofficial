package com.github.standobyte.jojo.client.rendertype;

import com.github.standobyte.jojo.api.client.render.ClientRenderCompatibility;

final class IrisShaderPipelineCompat {
	static boolean isShaderPackInUse() {
		return ClientRenderCompatibility.snapshot().irisShaderPackInUse();
	}

	private IrisShaderPipelineCompat() {}
}
