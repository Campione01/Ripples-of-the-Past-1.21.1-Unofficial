package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelVisibility.Part;
import com.github.standobyte.jojo.api.client.render.ScopedPlayerModelVisibility.VisibilitySnapshot;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed policies for one vanilla player base-model draw.
 *
 * <p>Requested parts are hidden only while the base model renders. The exact
 * prior visibility is restored before armor and addon layers run. Each scope
 * captures the state it entered with, so nested and interleaved player renders
 * restore in stack order without sharing per-player state. Entity-mask capture
 * rerenders read the same frame's cached decision and never redispatch addon
 * providers.</p>
 */
public final class PlayerBaseModelVisibilityPolicies {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Entry> REGISTERED =
			new LinkedHashMap<>();
	private static volatile List<Entry> snapshot = List.of();
	private static final ThreadLocal<Deque<DecisionFrame>> RENDER_FRAMES =
			ThreadLocal.withInitial(ArrayDeque::new);
	private static final Map<Object, CachedDecision> FRAME_DECISIONS =
			Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * Registers one addon-owned base-player visibility policy.
	 *
	 * @throws IllegalStateException if the owner is already registered
	 */
	public static Registration register(
			ResourceLocation owner,
			PlayerBaseModelVisibilityPolicy policy) {
		Entry entry = new Entry(
				Objects.requireNonNull(owner, "owner"),
				Objects.requireNonNull(policy, "policy"));
		synchronized (LOCK) {
			if (REGISTERED.putIfAbsent(owner, entry) != null) {
				throw new IllegalStateException(
						"A player base-model visibility policy is "
								+ "already registered as "
								+ owner);
			}
			publishSnapshot();
		}
		return new Registration(entry);
	}

	@ApiStatus.Internal
	public static RenderFrame enterRenderFrame(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			float partialTick) {
		Deque<DecisionFrame> frames = RENDER_FRAMES.get();
		DecisionFrame inherited =
				findDecision(frames, player, model);
		EnumSet<Part> hidden;
		if (inherited != null) {
			hidden = EnumSet.copyOf(inherited.hidden());
		}
		else if (EntityMaskPostEffect.isCapturePass()) {
			hidden = cachedFrameDecision(player, model);
		}
		else {
			hidden = resolveHidden(player, model, partialTick);
			cacheFrameDecision(player, model, hidden);
		}
		DecisionFrame decision =
				new DecisionFrame(player, model, hidden);
		frames.push(decision);
		return new RenderFrame(frames, decision);
	}

	@ApiStatus.Internal
	public static FrameScope beginFrame(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			float partialTick) {
		DecisionFrame decision =
				findDecision(RENDER_FRAMES.get(), player, model);
		EnumSet<Part> hidden;
		if (decision != null) {
			hidden = decision.hidden();
		}
		else if (EntityMaskPostEffect.isCapturePass()) {
			hidden = cachedFrameDecision(player, model);
		}
		else {
			hidden = resolveHidden(player, model, partialTick);
		}
		if (hidden.isEmpty()) {
			return FrameScope.NOOP;
		}
		VisibilitySnapshot original =
				VisibilitySnapshot.capture(model);
		for (Part part : hidden) {
			part.setVisible(model, false);
		}
		return new FrameScope(model, original);
	}

	private static EnumSet<Part> resolveHidden(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			float partialTick) {
		List<Entry> policies = snapshot;
		if (policies.isEmpty()) {
			return EnumSet.noneOf(Part.class);
		}

		VisibilitySnapshot original =
				VisibilitySnapshot.capture(model);
		EnumSet<Part> hidden = EnumSet.noneOf(Part.class);
		PlayerBaseModelVisibilityQuery query =
				new PlayerBaseModelVisibilityQuery(
						player, model, partialTick);
		try {
			for (Entry entry : policies) {
				try {
					Set<Part> requested = Objects.requireNonNull(
							entry.policy().hiddenParts(query),
							"hidden parts");
					for (Part part : requested) {
						hidden.add(Objects.requireNonNull(
								part, "hidden part"));
					}
				}
				catch (VirtualMachineError | ThreadDeath fatal) {
					throw fatal;
				}
				catch (Throwable throwable) {
					JojoMod.getLogger().error(
							"Player base-model visibility policy {} "
									+ "failed; continuing with later "
									+ "policies.",
							entry.owner(),
							throwable);
				}
			}
		}
		finally {
			// Policies are declarative; discard accidental model mutations.
			original.restore(model);
		}
		return hidden;
	}

	private static DecisionFrame findDecision(
			Deque<DecisionFrame> frames,
			AbstractClientPlayer player,
			PlayerModel<?> model) {
		for (DecisionFrame frame : frames) {
			if (frame.matches(player, model)) {
				return frame;
			}
		}
		return null;
	}

	private static void cacheFrameDecision(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			EnumSet<Part> hidden) {
		FRAME_DECISIONS.put(
				decisionKey(player, model),
				new CachedDecision(
						EntityPostRenderExtensions.currentFrameId(),
						EnumSet.copyOf(hidden)));
	}

	private static EnumSet<Part> cachedFrameDecision(
			AbstractClientPlayer player,
			PlayerModel<?> model) {
		CachedDecision cached =
				FRAME_DECISIONS.get(decisionKey(player, model));
		if (cached == null) {
			return EnumSet.noneOf(Part.class);
		}
		long currentFrame =
				EntityPostRenderExtensions.currentFrameId();
		if (cached.frameId() != currentFrame
				&& cached.frameId() + 1L != currentFrame) {
			return EnumSet.noneOf(Part.class);
		}
		return EnumSet.copyOf(cached.hidden());
	}

	private static Object decisionKey(
			AbstractClientPlayer player,
			PlayerModel<?> model) {
		return player != null ? player : model;
	}

	private static void publishSnapshot() {
		snapshot = List.copyOf(new ArrayList<>(REGISTERED.values()));
	}

	static List<ResourceLocation> registeredOwners() {
		return snapshot.stream().map(Entry::owner).toList();
	}

	static void resetForTests() {
		synchronized (LOCK) {
			REGISTERED.clear();
			publishSnapshot();
		}
		RENDER_FRAMES.remove();
		FRAME_DECISIONS.clear();
	}

	static int activeRenderFrameDepthForTests() {
		return RENDER_FRAMES.get().size();
	}

	public static final class Registration implements AutoCloseable {
		private final Entry entry;
		private boolean closed;

		private Registration(Entry entry) {
			this.entry = entry;
		}

		public ResourceLocation owner() {
			return entry.owner();
		}

		@Override
		public void close() {
			synchronized (LOCK) {
				if (closed) {
					return;
				}
				closed = true;
				REGISTERED.remove(entry.owner(), entry);
				publishSnapshot();
			}
		}
	}

	public static final class FrameScope implements AutoCloseable {
		private static final FrameScope NOOP =
				new FrameScope(null, null);

		private final PlayerModel<?> model;
		private final VisibilitySnapshot snapshot;
		private boolean closed;

		private FrameScope(
				PlayerModel<?> model,
				VisibilitySnapshot snapshot) {
			this.model = model;
			this.snapshot = snapshot;
		}

		@Override
		public void close() {
			if (!closed && snapshot != null) {
				closed = true;
				snapshot.restore(model);
			}
		}
	}

	public static final class RenderFrame implements AutoCloseable {
		private final Deque<DecisionFrame> frames;
		private final DecisionFrame decision;
		private boolean closed;

		private RenderFrame(
				Deque<DecisionFrame> frames,
				DecisionFrame decision) {
			this.frames = frames;
			this.decision = decision;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (frames.peek() != decision) {
				frames.remove(decision);
				if (frames.isEmpty()) {
					RENDER_FRAMES.remove();
				}
				throw new IllegalStateException(
						"Player render visibility frames closed "
								+ "out of order");
			}
			frames.pop();
			if (frames.isEmpty()) {
				RENDER_FRAMES.remove();
			}
		}
	}

	private record Entry(
			ResourceLocation owner,
			PlayerBaseModelVisibilityPolicy policy) {}

	private record DecisionFrame(
			AbstractClientPlayer player,
			PlayerModel<?> model,
			EnumSet<Part> hidden) {
		boolean matches(
				AbstractClientPlayer player,
				PlayerModel<?> model) {
			return this.player != null && player != null
					? this.player.getUUID().equals(player.getUUID())
					: this.player == null && player == null
							&& this.model == model;
		}
	}

	private record CachedDecision(
			long frameId,
			EnumSet<Part> hidden) {}

	private PlayerBaseModelVisibilityPolicies() {}
}
