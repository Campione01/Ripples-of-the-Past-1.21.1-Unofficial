package com.github.standobyte.jojo.modcompat;

import net.neoforged.fml.ModList;

public class OptionalDependencyHelper {
	private static final IVampirismModIntegration VAMPIRISM = ModList.get().isLoaded("vampirism")
			? new ReflectiveVampirismModIntegration()
			: new IVampirismModIntegration.Dummy();

	public static IVampirismModIntegration vampirism() {
		return VAMPIRISM;
	}
}
