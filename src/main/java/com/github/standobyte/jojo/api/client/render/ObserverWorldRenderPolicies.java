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

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Context;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed observer world render policies for the vanilla render pipeline.
 *
 * <p>Policies are resolved once per {@code LevelRenderer.renderLevel} frame.
 * Providers may only suppress the declared passes; an absent, null, or failed
 * provider leaves vanilla rendering unchanged. Renderer replacements that
 * bypass the guarded vanilla draw methods are intentionally unaffected.</p>
 */
public final class ObserverWorldRenderPolicies {
	private static final Map<ResourceLocation,
			ObserverWorldRenderPolicyProvider> PROVIDERS =
					new LinkedHashMap<>();
	private static final ThreadLocal<Deque<FrameScope>> ACTIVE_FRAMES =
			ThreadLocal.withInitial(ArrayDeque::new);

	private ObserverWorldRenderPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			ObserverWorldRenderPolicyProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate observer world render policy provider: "
							+ owner);
		}
	}

	@ApiStatus.Internal
	public static FrameScope beginFrame(@Nullable Context context) {
		return pushSnapshot(context != null
				? resolve(context)
				: Set.of());
	}

	@ApiStatus.Internal
	public static boolean suppresses(Pass pass) {
		if (pass == null) {
			return false;
		}
		FrameScope active = ACTIVE_FRAMES.get().peek();
		return active != null && active.suppressedPasses.contains(pass);
	}

	static Set<Pass> resolve(@Nullable Context context) {
		List<Map.Entry<ResourceLocation,
				ObserverWorldRenderPolicyProvider>> snapshot;
		synchronized (ObserverWorldRenderPolicies.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		EnumSet<Pass> suppressed = EnumSet.noneOf(Pass.class);
		for (Map.Entry<ResourceLocation,
				ObserverWorldRenderPolicyProvider> entry : snapshot) {
			try {
				Set<Pass> requested =
						entry.getValue().suppressedPasses(context);
				if (requested != null) {
					for (Pass pass : requested) {
						if (pass != null) {
							suppressed.add(pass);
						}
					}
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Observer world render policy provider {} failed.",
						entry.getKey(),
						error);
			}
		}
		return immutableSnapshot(suppressed);
	}

	static FrameScope pushSnapshot(Set<Pass> suppressedPasses) {
		FrameScope scope = new FrameScope(
				immutableSnapshot(suppressedPasses));
		ACTIVE_FRAMES.get().push(scope);
		return scope;
	}

	private static Set<Pass> immutableSnapshot(
			@Nullable Set<Pass> passes) {
		if (passes == null || passes.isEmpty()) {
			return Set.of();
		}
		EnumSet<Pass> copy = EnumSet.noneOf(Pass.class);
		for (Pass pass : passes) {
			if (pass != null) {
				copy.add(pass);
			}
		}
		return copy.isEmpty()
				? Set.of()
				: Collections.unmodifiableSet(copy);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
		ACTIVE_FRAMES.remove();
	}

	public static final class FrameScope implements AutoCloseable {
		private final Set<Pass> suppressedPasses;
		private boolean closed;

		private FrameScope(Set<Pass> suppressedPasses) {
			this.suppressedPasses = suppressedPasses;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			Deque<FrameScope> frames = ACTIVE_FRAMES.get();
			if (frames.peek() == this) {
				frames.pop();
			}
			else {
				frames.remove(this);
			}
			if (frames.isEmpty()) {
				ACTIVE_FRAMES.remove();
			}
		}
	}
}
