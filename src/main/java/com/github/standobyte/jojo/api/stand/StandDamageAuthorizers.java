package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed callbacks for addon-defined Stand damage authorization.
 * Callbacks must be side-effect free.
 */
public final class StandDamageAuthorizers {
	private static final Map<ResourceLocation, StandDamageAuthorizer>
			AUTHORIZERS = new LinkedHashMap<>();

	private StandDamageAuthorizers() {}

	public static synchronized void register(
			ResourceLocation owner,
			StandDamageAuthorizer authorizer) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(authorizer, "authorizer");
		if (AUTHORIZERS.putIfAbsent(owner, authorizer) != null) {
			throw new IllegalStateException(
					"Duplicate Stand damage authorizer: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean canHurtStand(DamageSource source) {
		Objects.requireNonNull(source, "source");
		Entity causingEntity = source.getEntity();
		if (!(causingEntity instanceof LivingEntity attacker)) {
			return false;
		}
		return evaluate(new StandDamageQuery(attacker, source));
	}

	@ApiStatus.Internal
	static boolean evaluate(StandDamageQuery query) {
		List<Map.Entry<ResourceLocation, StandDamageAuthorizer>> snapshot;
		synchronized (StandDamageAuthorizers.class) {
			snapshot = new ArrayList<>(AUTHORIZERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, StandDamageAuthorizer>
				entry : snapshot) {
			try {
				if (entry.getValue().canHurtStand(query)) {
					return true;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Stand damage authorizer {} failed.",
						entry.getKey(),
						e);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(AUTHORIZERS.keySet());
	}

	static synchronized void resetForTests() {
		AUTHORIZERS.clear();
	}
}
