package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;

import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill.DevStatus;

public final class HamonSkillDefinition {
	private final String name;
	private final HamonSkillBranch branch;
	private final boolean startingSkill;
	private final List<String> unlocksAbilities;
	private final List<String> prerequisiteSkills;
	private final boolean requiresTeacher;
	private final DevStatus status;

	public HamonSkillDefinition(String name, HamonSkillBranch branch, boolean startingSkill, List<String> unlocksAbilities,
			List<String> prerequisiteSkills, boolean requiresTeacher, DevStatus status) {
		this.name = name;
		this.branch = branch;
		this.startingSkill = startingSkill;
		this.unlocksAbilities = List.copyOf(unlocksAbilities);
		this.prerequisiteSkills = List.copyOf(prerequisiteSkills);
		this.requiresTeacher = requiresTeacher;
		this.status = status;
	}

	public String name() {
		return name;
	}

	public HamonSkillBranch branch() {
		return branch;
	}

	public boolean startingSkill() {
		return startingSkill;
	}

	public List<String> unlocksAbilities() {
		return unlocksAbilities;
	}

	public List<String> prerequisiteSkills() {
		return prerequisiteSkills;
	}

	public boolean requiresTeacher() {
		return requiresTeacher;
	}

	public DevStatus status() {
		return status;
	}

	public enum HamonSkillBranch {
		OVERDRIVE,
		INFUSION,
		FLEXIBILITY,
		HEALING,
		ATTRACTANT_REPELLENT,
		BODY_MANIPULATION,
		CHARACTER_TECHNIQUE
	}
}
