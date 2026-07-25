package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill.DevStatus;

import net.minecraft.network.chat.Component;

public class HamonUnlockableSkill extends UnlockableSkill {
	public HamonUnlockableSkill(String name) {
		super(name);
		this.textName = Component.translatable("hamonSkill." + name + ".name");
		this.textDesc = Component.translatable("hamonSkill." + name + ".desc");
		this.textControls = Component.empty();
	}

	public HamonUnlockableSkill(HamonSkillDefinition definition) {
		this(definition.name());
		if (definition.startingSkill()) {
			setIsStartingSkill();
		}
		addUnlocks(definition.unlocksAbilities());
		addPrerequisites(definition.prerequisiteSkills());
		if (definition.status() == DevStatus.WIP) {
			setIncomplete();
		}
		else if (definition.status() == DevStatus.NYI) {
			setNotYetImplemented();
		}
	}
	
	public static HamonUnlockableSkill itemReward(String name) {
		return new HamonUnlockableSkill(name);
	}

	public static HamonUnlockableSkill fromDefinition(HamonSkillDefinition definition) {
		return new HamonUnlockableSkill(definition);
	}

	@Override
	public ConditionCheck canUnlockFromMenu(Power<?> userPower, PowerData data) {
		ConditionCheck base = super.canUnlockFromMenu(userPower, data);
		if (!base.isPositive() || !(data instanceof HamonData hamon)) {
			return base;
		}
		HamonSkillDefinition definition = ModHamonSkills.definitionFor(skillName);
		if (definition != null && definition.branch() != HamonSkillDefinition.HamonSkillBranch.CHARACTER_TECHNIQUE
				&& !definition.startingSkill()) {
			HamonData.HamonStat stat = HamonData.statForSkillBranch(definition.branch());
			if (stat != null && hamon.getSkillPoints(stat) <= 0) {
				return ConditionCheck.createNegative(Component.translatable("hamon.closed.points"));
			}
			if (definition.requiresTeacher()) {
				Set<String> teacherSkills = userPower.getUser().level().isClientSide()
						? hamon.getTeacherSkills()
						: HamonUtil.nearbyTeachersSkills(userPower.getUser());
				if (teacherSkills == null) {
					return ConditionCheck.createNegative(Component.translatable("hamon.closed.teacher.required"));
				}
				if (!teacherSkills.contains(skillName)) {
					return ConditionCheck.createNegative(Component.translatable("hamon.closed.teacher.no_skill"));
				}
			}
		}
		if (!ModHamonSkills.isTechniqueSkill(skillName)) {
			return base;
		}

		HamonTechnique technique = hamon.getCharacterTechnique();
		if (technique == null || !HamonData.MIX_HAMON_TECHNIQUES && !technique.isTechniqueSkill(skillName)) {
			return ConditionCheck.createNegative(Component.translatable("hamon.closed.technique.bug"));
		}
		int learnedTechniqueSkills = hamon.getLearnedTechniqueSkillCount();
		if (learnedTechniqueSkills >= HamonData.techniqueSlotsCount()) {
			return ConditionCheck.createNegative(Component.translatable("hamon.closed.technique.max"));
		}
		if (!hamon.hasTechniqueLevel(learnedTechniqueSkills)) {
			return ConditionCheck.createNegative(Component.translatable("hamon.closed.technique.locked"));
		}
		return base;
	}

	private void addUnlocks(List<String> abilities) {
		if (!abilities.isEmpty()) {
			withAbility(abilities.get(0), abilities.subList(1, abilities.size()).toArray(String[]::new));
		}
	}

	private void addPrerequisites(List<String> prerequisites) {
		if (!prerequisites.isEmpty()) {
			prerequisiteSkill(prerequisites.get(0), prerequisites.subList(1, prerequisites.size()).toArray(String[]::new));
		}
	}
}
