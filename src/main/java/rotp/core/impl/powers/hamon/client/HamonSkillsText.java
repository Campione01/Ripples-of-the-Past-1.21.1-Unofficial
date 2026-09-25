package rotp.core.impl.powers.hamon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonSkillDefinition;
import rotp.core.impl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Hamon skill tab text rules from 1.16 (HamonGeneralSkillsTabGui, AbstractHamonSkill.getDescTranslated).
 * Kept free of client classes so gametests can call it.
 */
public final class HamonSkillsText {
	private HamonSkillsText() {}

	// branch headers wrap at 75 px, one font line apart
	public static final int BRANCH_TITLE_WRAP = 75;
	public static final int BRANCH_TITLE_LINE_HEIGHT = 9;
	// hover strip above the tree: columns from x 6, 68 wide, 18 high
	public static final int BRANCH_STRIP_X = 6;
	public static final int BRANCH_COLUMN_WIDTH = 68;
	public static final int BRANCH_STRIP_HEIGHT = 18;
	public static final int BRANCH_DESC_WRAP = 200;

	public static String branchKey(HamonSkillBranch branch) {
		return switch (branch) {
		case OVERDRIVE -> "hamon.skills.overdrive";
		case INFUSION -> "hamon.skills.infusion";
		case FLEXIBILITY -> "hamon.skills.flexibility";
		case HEALING -> "hamon.skills.life";
		case ATTRACTANT_REPELLENT -> "hamon.skills.attractant_repellent";
		case BODY_MANIPULATION -> "hamon.skills.body_manipulation";
		case CHARACTER_TECHNIQUE -> "hamon.techniques.tab";
		};
	}

	public static Component branchTitle(HamonSkillBranch branch) {
		return Component.translatable(branchKey(branch));
	}

	// full header split into lines, never trimmed
	public static <T> List<T> branchTitleLines(HamonSkillBranch branch, BiFunction<Component, Integer, List<T>> split) {
		return split.apply(branchTitle(branch), BRANCH_TITLE_WRAP);
	}

	public static Component branchDesc(HamonSkillBranch branch) {
		return Component.translatable(branchKey(branch) + ".desc").withStyle(ChatFormatting.ITALIC);
	}

	// column under the mouse in the strip above the tree, or -1; coordinates relative to the tree origin
	public static int hoveredBranchColumn(int relX, int relY, int treeStartY, int columns) {
		if (relY < treeStartY - BRANCH_STRIP_HEIGHT || relY >= treeStartY || relX < BRANCH_STRIP_X) {
			return -1;
		}
		int column = (relX - BRANCH_STRIP_X) / BRANCH_COLUMN_WIDTH;
		return column < columns ? column : -1;
	}

	// skill description, plus the Hamon Beat line for Overdrive
	public static List<Component> descLines(HamonSkillDefinition skill) {
		List<Component> lines = new ArrayList<>(2);
		lines.add(Component.translatable("hamonSkill." + skill.name() + ".desc"));
		if (ModHamonSkills.OVERDRIVE_DEF.name().equals(skill.name())) {
			lines.add(Component.translatable("hamonSkill.overdrive_strong.desc"));
		}
		return lines;
	}

	public static Component prerequisiteNames(List<String> skillIds) {
		return joined(skillIds, id -> "hamonSkill." + id + ".name");
	}

	// abilityKey gives the moveset ability's translation key, or null for the core key
	public static Component abilityNames(List<String> abilityIds, Function<String, String> abilityKey) {
		return joined(abilityIds, id -> {
			String key = abilityKey.apply(id);
			return key != null ? key : JojoMod.MOD_ID + ".ability." + id;
		});
	}

	// 1.16 HamonTechniqueTabGui.tabLockedLines: no unlock level text while techniques are disabled
	public static List<Component> techniquesLockedLines(boolean techniquesEnabled, int firstRequirement) {
		return techniquesEnabled ? List.of(Component.translatable("hamon.techniques_locked", firstRequirement)) : List.of();
	}

	private static Component joined(List<String> ids, Function<String, String> key) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(Component.translatable(key.apply(ids.get(i))));
		}
		return out;
	}
}
