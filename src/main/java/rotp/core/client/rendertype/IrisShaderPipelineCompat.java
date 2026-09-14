package rotp.core.client.rendertype;

import rotp.core.api.client.render.ClientRenderCompatibility;

final class IrisShaderPipelineCompat {
	static boolean isShaderPackInUse() {
		return ClientRenderCompatibility.snapshot().irisShaderPackInUse();
	}

	private IrisShaderPipelineCompat() {}
}
