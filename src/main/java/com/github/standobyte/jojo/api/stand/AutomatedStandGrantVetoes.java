package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AutomatedStandGrantVetoes {
	private static final Map<ResourceLocation, AutomatedStandGrantVeto>
			VETOES = new LinkedHashMap<>();

	private AutomatedStandGrantVetoes() {}

	public static synchronized void register(
			ResourceLocation owner,
			AutomatedStandGrantVeto veto) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(veto, "veto");
		if (VETOES.putIfAbsent(owner, veto) != null) {
			throw new IllegalStateException(
					"Duplicate automated Stand grant veto: " + owner);
		}
	}

	public static boolean isVetoed(
			ResourceLocation source,
			ServerPlayer player) {
		return evaluate(new AutomatedStandGrantQuery(
				Objects.requireNonNull(source, "source"),
				Objects.requireNonNull(player, "player")));
	}

	static boolean evaluate(AutomatedStandGrantQuery query) {
		List<Map.Entry<ResourceLocation, AutomatedStandGrantVeto>>
				snapshot;
		synchronized (AutomatedStandGrantVetoes.class) {
			snapshot = new ArrayList<>(VETOES.entrySet());
		}
		for (Map.Entry<ResourceLocation, AutomatedStandGrantVeto>
				entry : snapshot) {
			try {
				if (entry.getValue().vetoes(query)) {
					return true;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Automated Stand grant veto {} failed.",
						entry.getKey(),
						e);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(VETOES.keySet());
	}

	static synchronized void resetForTests() {
		VETOES.clear();
	}
}
