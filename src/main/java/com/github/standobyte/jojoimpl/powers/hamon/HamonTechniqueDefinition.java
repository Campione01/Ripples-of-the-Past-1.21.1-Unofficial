package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojoimpl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public final class HamonTechniqueDefinition {
	private final String name;
	private final List<String> skillIds;
	private final List<String> perksOnPick;
	private final Map<HamonSkillBranch, Float> branchEfficiencies;
	@Nullable
	private final Supplier<? extends Holder<SoundEvent>> musicOnPick;

	public HamonTechniqueDefinition(String name, List<String> skillIds, List<String> perksOnPick,
			Map<HamonSkillBranch, Float> branchEfficiencies, @Nullable Supplier<? extends Holder<SoundEvent>> musicOnPick) {
		this.name = name;
		this.skillIds = List.copyOf(skillIds);
		this.perksOnPick = List.copyOf(perksOnPick);
		this.branchEfficiencies = Map.copyOf(branchEfficiencies);
		this.musicOnPick = musicOnPick;
	}

	public String name() {
		return name;
	}

	public List<String> skillIds() {
		return skillIds;
	}

	public List<String> perksOnPick() {
		return perksOnPick;
	}

	public Map<HamonSkillBranch, Float> branchEfficiencies() {
		return branchEfficiencies;
	}

	@Nullable
	public Holder<SoundEvent> musicOnPick() {
		return musicOnPick != null ? musicOnPick.get() : null;
	}

	public boolean isTechniqueSkill(String skillName) {
		return skillIds.contains(skillName) || perksOnPick.contains(skillName);
	}

	public boolean isTechniquePerk(String skillName) {
		return perksOnPick.contains(skillName);
	}

	public float getAddSkillEfficiency(HamonSkill skill) {
		if (skill == null || skill.getDefinition() == null) {
			return 0.0F;
		}
		return branchEfficiencies.getOrDefault(skill.getDefinition().branch(), 0.0F);
	}
}
