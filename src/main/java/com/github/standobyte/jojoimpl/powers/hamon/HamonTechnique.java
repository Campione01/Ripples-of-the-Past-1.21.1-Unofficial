package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class HamonTechnique {
	private final ResourceLocation registryKey;
	private final HamonTechniqueDefinition definition;

	public HamonTechnique(ResourceLocation registryKey, HamonTechniqueDefinition definition) {
		this.registryKey = registryKey;
		this.definition = definition;
	}
	
	public ResourceLocation getRegistryKey() {
		return registryKey;
	}

	public String getName() {
		return definition.name();
	}

	public List<String> getSkillIds() {
		return definition.skillIds();
	}

	public List<String> getPerksOnPick() {
		return definition.perksOnPick();
	}

	@Nullable
	public Holder<SoundEvent> getMusicOnPick() {
		return definition.musicOnPick();
	}

	public float getAddSkillEfficiency(HamonSkill skill) {
		return definition.getAddSkillEfficiency(skill);
	}

	public boolean isTechniqueSkill(String skillName) {
		return definition.isTechniqueSkill(skillName);
	}

	public boolean isTechniquePerk(String skillName) {
		return definition.isTechniquePerk(skillName);
	}

	public boolean canPick(HamonData hamon) {
		return hamon != null && hamon.techniquesEnabled() && hamon.getCharacterTechnique() == null;
	}
	
}
