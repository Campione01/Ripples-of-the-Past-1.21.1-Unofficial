package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.client.shader.core.RenderTargetState;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * A client-only, addon-owned PostChain with a bounded lifecycle.
 *
 * <p>The core loads the chain lazily, rebinds it to the current Minecraft main
 * target, and releases it on deactivation, resource reload, world exit,
 * replacement, or client shutdown. It never acquires or mutates Iris or Super
 * Resolution framebuffers.</p>
 *
 * <p>When Super Resolution is installed, every reachable chain location
 * returned by {@link #registeredPostChains()} must still be listed in that
 * installation's {@code inject_post_chain_blacklist}. Registration records
 * the requirement; it does not rewrite another mod's configuration.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class AddonPostEffect implements AutoCloseable {
	private static final Map<ResourceLocation, AddonPostEffect> REGISTERED =
			new LinkedHashMap<>();

	private final ResourceLocation id;
	private final RenderLevelStageEvent.Stage stage;
	private final UniformUpdater uniformUpdater;
	private final Set<ResourceLocation> declaredPostChains;
	private volatile ResourceLocation postChainLocation;
	private volatile boolean active;
	private volatile boolean closed;
	@Nullable private volatile PostChain postChain;
	@Nullable private volatile RenderTarget boundTarget;
	@Nullable private volatile ResourceLocation failedLocation;
	private int targetWidth = -1;
	private int targetHeight = -1;
	private boolean runtimeFailureLogged;

	private AddonPostEffect(
			ResourceLocation id,
			ResourceLocation postChainLocation,
			Set<ResourceLocation> declaredPostChains,
			RenderLevelStageEvent.Stage stage,
			UniformUpdater uniformUpdater) {
		this.id = Objects.requireNonNull(id);
		this.postChainLocation = Objects.requireNonNull(postChainLocation);
		this.declaredPostChains = Set.copyOf(declaredPostChains);
		this.stage = Objects.requireNonNull(stage);
		this.uniformUpdater = Objects.requireNonNull(uniformUpdater);
	}

	public static AddonPostEffect register(
			ResourceLocation id,
			ResourceLocation postChainLocation,
			RenderLevelStageEvent.Stage stage) {
		return register(id, postChainLocation, stage, UniformUpdater.NONE);
	}

	public static synchronized AddonPostEffect register(
			ResourceLocation id,
			ResourceLocation postChainLocation,
			RenderLevelStageEvent.Stage stage,
			UniformUpdater uniformUpdater) {
		ResourceLocation canonical = canonicalPostChain(postChainLocation);
		return registerRoutes(
				id, Set.of(canonical), canonical, stage, uniformUpdater);
	}

	/**
	 * Registers every post-chain route that this effect may select at runtime.
	 * The returned inventory is stable and can be copied verbatim into Super
	 * Resolution's injected-chain blacklist.
	 */
	public static AddonPostEffect registerRoutes(
			ResourceLocation id,
			Set<ResourceLocation> postChainLocations,
			ResourceLocation initialPostChainLocation,
			RenderLevelStageEvent.Stage stage) {
		return registerRoutes(
				id,
				postChainLocations,
				initialPostChainLocation,
				stage,
				UniformUpdater.NONE);
	}

	public static synchronized AddonPostEffect registerRoutes(
			ResourceLocation id,
			Set<ResourceLocation> postChainLocations,
			ResourceLocation initialPostChainLocation,
			RenderLevelStageEvent.Stage stage,
			UniformUpdater uniformUpdater) {
		if (REGISTERED.containsKey(id)) {
			throw new IllegalStateException(
					"An addon post effect is already registered as " + id);
		}
		Set<ResourceLocation> canonicalLocations =
				postChainLocations.stream()
						.map(AddonPostEffect::canonicalPostChain)
						.collect(Collectors.toCollection(
								LinkedHashSet::new));
		if (canonicalLocations.isEmpty()) {
			throw new IllegalArgumentException(
					"An addon post effect must declare at least one chain");
		}
		ResourceLocation canonicalInitial =
				canonicalPostChain(initialPostChainLocation);
		if (!canonicalLocations.contains(canonicalInitial)) {
			throw new IllegalArgumentException(
					"The initial addon post chain must be declared");
		}
		AddonPostEffect effect = new AddonPostEffect(
				id,
				canonicalInitial,
				canonicalLocations,
				stage,
				uniformUpdater);
		REGISTERED.put(id, effect);
		return effect;
	}

	public ResourceLocation id() {
		return id;
	}

	public ResourceLocation postChainLocation() {
		return postChainLocation;
	}

	public RenderLevelStageEvent.Stage stage() {
		return stage;
	}

	public boolean isActive() {
		return active && !closed;
	}

	public boolean isLoaded() {
		return postChain != null;
	}

	public void setActive(boolean active) {
		requireOpen();
		this.active = active;
		if (!active && postChain != null) {
			releaseOnClientThread();
		}
	}

	public void setPostChainLocation(ResourceLocation postChainLocation) {
		requireOpen();
		ResourceLocation next = canonicalPostChain(postChainLocation);
		if (!declaredPostChains.contains(next)) {
			throw new IllegalArgumentException(
					"Addon post effect "
							+ id
							+ " did not declare chain "
							+ next);
		}
		if (!next.equals(this.postChainLocation)) {
			this.postChainLocation = next;
			failedLocation = null;
			runtimeFailureLogged = false;
			if (postChain != null) {
				releaseOnClientThread();
			}
		}
	}

	public ClientRenderCompatibility.Snapshot compatibilitySnapshot() {
		return ClientRenderCompatibility.snapshot();
	}

	public static synchronized Set<ResourceLocation> registeredPostChains() {
		return REGISTERED.values().stream()
				.flatMap(effect -> effect.declaredPostChains.stream())
				.collect(Collectors.toUnmodifiableSet());
	}

	static ResourceLocation canonicalPostChain(
			ResourceLocation location) {
		ResourceLocation value = Objects.requireNonNull(location);
		String path = value.getPath();
		if (!path.startsWith("shaders/post/")) {
			path = "shaders/post/" + path;
		}
		if (!path.endsWith(".json")) {
			path += ".json";
		}
		return value.withPath(path);
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		active = false;
		synchronized (AddonPostEffect.class) {
			REGISTERED.remove(id, this);
		}
		if (postChain != null) {
			releaseOnClientThread();
		}
	}

	private void requireOpen() {
		if (closed) {
			throw new IllegalStateException(
					"Addon post effect " + id + " is closed");
		}
	}

	private void releaseOnClientThread() {
		Minecraft minecraft = Minecraft.getInstance();
		if (RenderSystem.isOnRenderThread()) {
			release();
		}
		else {
			minecraft.execute(this::release);
		}
	}

	private void render(float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isActive()
				|| minecraft.level == null
				|| minecraft.player == null) {
			if (postChain != null) {
				release();
			}
			return;
		}

		RenderTarget currentTarget = minecraft.getMainRenderTarget();
		RenderStateScope renderState =
				RenderStateScope.capture(currentTarget);
		try {
			if (boundTarget != null && boundTarget != currentTarget) {
				releaseRaw();
			}
			if (postChain == null && !loadRaw(currentTarget)) {
				return;
			}

			int width = currentTarget.viewWidth;
			int height = currentTarget.viewHeight;
			if (width != targetWidth || height != targetHeight) {
				postChain.resize(width, height);
				targetWidth = width;
				targetHeight = height;
			}
			postChain.screenTarget = currentTarget;
			uniformUpdater.update(
					postChain,
					partialTick,
					ClientRenderCompatibility.snapshot());
			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();
			postChain.process(partialTick);
		}
		catch (RuntimeException exception) {
			if (!runtimeFailureLogged) {
				runtimeFailureLogged = true;
				JojoMod.getLogger().error(
						"Addon post effect {} failed and was released.",
						id,
						exception);
			}
			releaseRaw();
		}
		finally {
			renderState.restore();
		}
	}

	private boolean loadRaw(RenderTarget target) {
		ResourceLocation selected = postChainLocation;
		if (selected.equals(failedLocation)) {
			return false;
		}
		if (Minecraft.getInstance()
				.getResourceManager()
				.getResource(selected)
				.isEmpty()) {
			failedLocation = selected;
			JojoMod.getLogger().warn(
					"Addon post effect {} is missing chain {}.",
					id,
					selected);
			return false;
		}
		postChain = ModShaders.loadPostShaderChain(selected, target);
		if (postChain == null) {
			failedLocation = selected;
			JojoMod.getLogger().warn(
					"Addon post effect {} could not load chain {}.",
					id,
					selected);
			return false;
		}
		boundTarget = target;
		targetWidth = target.viewWidth;
		targetHeight = target.viewHeight;
		postChain.resize(targetWidth, targetHeight);
		return true;
	}

	private void release() {
		if (postChain == null) {
			boundTarget = null;
			targetWidth = -1;
			targetHeight = -1;
			return;
		}
		RenderTarget currentTarget =
				Minecraft.getInstance().getMainRenderTarget();
		RenderStateScope renderState =
				RenderStateScope.capture(currentTarget);
		try {
			releaseRaw();
		}
		finally {
			renderState.restore();
		}
	}

	private void releaseRaw() {
		if (postChain != null) {
			postChain.close();
			postChain = null;
		}
		boundTarget = null;
		targetWidth = -1;
		targetHeight = -1;
	}

	private void resizeLoaded(int width, int height) {
		if (postChain == null) {
			return;
		}
		RenderTarget currentTarget =
				Minecraft.getInstance().getMainRenderTarget();
		RenderStateScope renderState =
				RenderStateScope.capture(currentTarget);
		try {
			postChain.resize(width, height);
			targetWidth = width;
			targetHeight = height;
		}
		finally {
			renderState.restore();
		}
	}

	private static synchronized List<AddonPostEffect> snapshotEffects() {
		return new ArrayList<>(REGISTERED.values());
	}

	@ApiStatus.Internal
	public static void onFrame(RenderLevelStageEvent event) {
		float partialTick =
				Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
		for (AddonPostEffect effect : snapshotEffects()) {
			if (effect.stage == event.getStage()) {
				effect.render(partialTick);
			}
		}
	}

	@ApiStatus.Internal
	public static void onClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			return;
		}
		for (AddonPostEffect effect : snapshotEffects()) {
			effect.release();
		}
	}

	@ApiStatus.Internal
	public static void onResourceManagerReload(ResourceManager resourceManager) {
		for (AddonPostEffect effect : snapshotEffects()) {
			effect.failedLocation = null;
			effect.runtimeFailureLogged = false;
			effect.release();
		}
	}

	@ApiStatus.Internal
	public static void resize(int width, int height) {
		for (AddonPostEffect effect : snapshotEffects()) {
			effect.resizeLoaded(width, height);
		}
	}

	@ApiStatus.Internal
	public static void closeLoadedChains() {
		for (AddonPostEffect effect : snapshotEffects()) {
			effect.release();
		}
	}

	@FunctionalInterface
	public interface UniformUpdater {
		UniformUpdater NONE = (postChain, partialTick, compatibility) -> {};

		void update(
				PostChain postChain,
				float partialTick,
				ClientRenderCompatibility.Snapshot compatibility);
	}

	private static final class RenderStateScope {
		private static final int TRACKED_TEXTURE_UNITS = 12;

		private final RenderTarget filterTarget;
		private final int filterMode;
		private final RenderTargetState targetState;
		private final GlStateBackup glState;
		private final float[] shaderColor;
		private final int activeTexture;
		private final int[] textureBindings;
		private final int[] shaderTextures;

		private RenderStateScope(
				RenderTarget filterTarget,
				int filterMode,
				RenderTargetState targetState,
				GlStateBackup glState,
				float[] shaderColor,
				int activeTexture,
				int[] textureBindings,
				int[] shaderTextures) {
			this.filterTarget = filterTarget;
			this.filterMode = filterMode;
			this.targetState = targetState;
			this.glState = glState;
			this.shaderColor = shaderColor;
			this.activeTexture = activeTexture;
			this.textureBindings = textureBindings;
			this.shaderTextures = shaderTextures;
		}

		private static RenderStateScope capture(
				RenderTarget filterTarget) {
			RenderTargetState targetState = new RenderTargetState();
			GlStateBackup glState = new GlStateBackup();
			float[] shaderColor =
					RenderSystem.getShaderColor().clone();
			int activeTexture = GlStateManager._getActiveTexture();
			int[] textureBindings =
					new int[TRACKED_TEXTURE_UNITS];
			int[] shaderTextures =
					new int[TRACKED_TEXTURE_UNITS];
			for (int i = 0; i < TRACKED_TEXTURE_UNITS; i++) {
				GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
				textureBindings[i] = GL11.glGetInteger(
						GL11.GL_TEXTURE_BINDING_2D);
				shaderTextures[i] =
						RenderSystem.getShaderTexture(i);
			}
			GlStateManager._activeTexture(activeTexture);
			int filterMode = filterTarget.filterMode;
			targetState.capture();
			RenderSystem.backupGlState(glState);
			return new RenderStateScope(
					filterTarget,
					filterMode,
					targetState,
					glState,
					shaderColor,
					activeTexture,
					textureBindings,
					shaderTextures);
		}

		private void restore() {
			if (filterTarget.filterMode != filterMode) {
				filterTarget.setFilterMode(filterMode);
			}
			targetState.restore();
			RenderSystem.restoreGlState(glState);
			RenderSystem.setShaderColor(
					shaderColor[0],
					shaderColor[1],
					shaderColor[2],
					shaderColor[3]);
			for (int i = 0; i < TRACKED_TEXTURE_UNITS; i++) {
				GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
				GlStateManager._bindTexture(textureBindings[i]);
				RenderSystem.setShaderTexture(i, shaderTextures[i]);
			}
			GlStateManager._activeTexture(activeTexture);
		}
	}
}
