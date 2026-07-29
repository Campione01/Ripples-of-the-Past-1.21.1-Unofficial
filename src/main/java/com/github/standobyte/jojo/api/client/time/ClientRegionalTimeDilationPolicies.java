package com.github.standobyte.jojo.api.client.time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed regional client time-dilation policies.
 *
 * <p>Providers are evaluated in owner-id order and combine by taking the
 * smallest valid factor. Missing, failed, or invalid providers preserve
 * vanilla timing. This registry does not retain mutable timer state.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientRegionalTimeDilationPolicies {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Entry> REGISTERED =
			new LinkedHashMap<>();
	private static final Comparator<Entry> ORDERING =
			Comparator.comparing(entry -> entry.owner().toString());
	private static final ProviderFailureReporter LOGGING_REPORTER =
			new ProviderFailureReporter() {
				@Override
				public void invalidFactor(
						ResourceLocation owner,
						float factor) {
					JojoMod.getLogger().error(
							"Client regional time-dilation provider {} "
									+ "returned invalid factor {}.",
							owner,
							factor);
				}

				@Override
				public void exception(
						ResourceLocation owner,
						RuntimeException error) {
					JojoMod.getLogger().error(
							"Client regional time-dilation provider {} "
									+ "failed.",
							owner,
							error);
				}
			};
	private static volatile List<Entry> snapshot = List.of();

	public static Registration register(
			ResourceLocation owner,
			ClientRegionalTimeDilationProvider provider) {
		Entry entry = new Entry(
				Objects.requireNonNull(owner, "owner"),
				Objects.requireNonNull(provider, "provider"),
				new ProviderFailureState());
		synchronized (LOCK) {
			if (REGISTERED.putIfAbsent(owner, entry) != null) {
				throw new IllegalStateException(
						"A client regional time-dilation provider is "
								+ "already registered as "
								+ owner);
			}
			publishSnapshot();
		}
		return new Registration(entry);
	}

	public static float resolve(
			ClientRegionalTimeDilationQuery query) {
		return resolve(query, LOGGING_REPORTER);
	}

	static float resolve(
			ClientRegionalTimeDilationQuery query,
			ProviderFailureReporter failureReporter) {
		Objects.requireNonNull(query, "query");
		Objects.requireNonNull(failureReporter, "failureReporter");
		float resolved = 1.0F;
		for (Entry entry : snapshot) {
			try {
				float factor = entry.provider().factor(query);
				if (!Float.isFinite(factor)
						|| factor <= 0.0F
						|| factor > 1.0F) {
					if (entry.failureState().recordFailure()) {
						failureReporter.invalidFactor(
								entry.owner(),
								factor);
					}
					continue;
				}
				entry.failureState().recordSuccess();
				resolved = Math.min(resolved, factor);
			}
			catch (RuntimeException error) {
				if (entry.failureState().recordFailure()) {
					failureReporter.exception(
							entry.owner(),
							error);
				}
			}
		}
		return resolved;
	}

	private static void publishSnapshot() {
		List<Entry> next = new ArrayList<>(REGISTERED.values());
		next.sort(ORDERING);
		snapshot = List.copyOf(next);
	}

	static List<ResourceLocation> registeredOwners() {
		return snapshot.stream().map(Entry::owner).toList();
	}

	static void resetForTests() {
		synchronized (LOCK) {
			REGISTERED.clear();
			publishSnapshot();
		}
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

	private record Entry(
			ResourceLocation owner,
			ClientRegionalTimeDilationProvider provider,
			ProviderFailureState failureState) {}

	static final class ProviderFailureState {
		private final AtomicBoolean failed = new AtomicBoolean();

		boolean recordFailure() {
			return failed.compareAndSet(false, true);
		}

		void recordSuccess() {
			if (failed.get()) {
				failed.set(false);
			}
		}
	}

	interface ProviderFailureReporter {
		void invalidFactor(ResourceLocation owner, float factor);

		void exception(
				ResourceLocation owner,
				RuntimeException error);
	}

	private ClientRegionalTimeDilationPolicies() {}
}
