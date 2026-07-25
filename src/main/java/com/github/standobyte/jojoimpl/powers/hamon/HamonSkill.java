package com.github.standobyte.jojoimpl.powers.hamon;

import net.minecraft.resources.ResourceLocation;

public class HamonSkill {
	private final ResourceLocation registryKey;
	private final HamonSkillDefinition definition;

	public HamonSkill(ResourceLocation registryKey) {
		this(registryKey, ModHamonSkills.definitionFor(registryKey.getPath()));
	}

	public HamonSkill(ResourceLocation registryKey, HamonSkillDefinition definition) {
		this.registryKey = registryKey;
		this.definition = definition;
	}
	
	public ResourceLocation getRegistryKey() {
		return registryKey;
	}

	public HamonSkillDefinition getDefinition() {
		return definition;
	}
	
}
