package com.github.standobyte.jojo.client.shader;

import java.util.Random;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.resources.ResolveShadersListManager;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;

public class ResolveShaderManager {
	private final ResolveShadersListManager shadersListManager = new ResolveShadersListManager();
	private final Random random = new Random();
	@Nullable private ResourceLocation selectedShader;
	private boolean noShaderAvailable;
	private boolean resetRequested;

	public ResolveShadersListManager listManager() {
		return shadersListManager;
	}

	public void onResourceReload() {
		selectedShader = null;
		noShaderAvailable = false;
		resetRequested = true;
	}

	public void setRandomResolveShader(@Nullable StandPower standPower) {
		if (!ClientModSettings.getSettingsReadOnly().resolveShaders) {
			stopResolveShader();
			return;
		}
		if (selectedShader != null || noShaderAvailable) {
			return;
		}
		selectedShader = shadersListManager.getRandomShader(standPower, random);
		noShaderAvailable = selectedShader == null;
	}

	public void stopResolveShader() {
		if (selectedShader != null || noShaderAvailable) {
			selectedShader = null;
			noShaderAvailable = false;
			resetRequested = true;
		}
	}

	public void markShaderLoadFailed(ResourceLocation shader) {
		if (shader.equals(selectedShader)) {
			selectedShader = null;
			noShaderAvailable = true;
			resetRequested = true;
		}
	}

	public boolean active() {
		return selectedShader != null && ClientModSettings.getSettingsReadOnly().resolveShaders;
	}

	public boolean justReset() {
		boolean justReset = resetRequested;
		resetRequested = false;
		return justReset;
	}

	@Nullable
	public ResourceLocation selectedShader() {
		return selectedShader;
	}
}
