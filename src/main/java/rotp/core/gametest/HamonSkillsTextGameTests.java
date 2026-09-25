package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonSkillDefinition.HamonSkillBranch;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.client.HamonSkillsText;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Hamon skill tabs: branch names wrap at 75 px instead of being cut with "...", the 18 px strip
 * above each 68 px column shows hamon.skills.<branch>.desc, Overdrive adds the Hamon Beat line, and the
 * detail pane names prerequisites and abilities by translation instead of registry id.
 * The screen is client-only, so its bytecode is read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonSkillsTextGameTests {
	private HamonSkillsTextGameTests() {}

	private static final String PKG = "rotp/core/impl/powers/hamon/client/";
	private static final String TEXT = PKG + "HamonSkillsText.";
	private static final int TREE_START_Y = 114;

	@GameTest(template = "empty")
	public static void hamonBranchHeadersWrapAndShowDescriptions(GameTestHelper helper) {
		List<Object> split = HamonSkillsText.branchTitleLines(HamonSkillBranch.ATTRACTANT_REPELLENT,
				(title, width) -> List.of(title, width));
		helper.assertTrue("hamon.skills.attractant_repellent".equals(key((Component) split.get(0))),
				"branch header must split the full translated name, got " + split.get(0));
		helper.assertTrue(Integer.valueOf(75).equals(split.get(1)), "branch header must wrap at 75 px, got " + split.get(1));
		helper.assertTrue(HamonSkillsText.BRANCH_TITLE_LINE_HEIGHT == 9, "wrapped header lines must be 9 px apart");

		Map<HamonSkillBranch, String> keys = Map.of(
				HamonSkillBranch.OVERDRIVE, "hamon.skills.overdrive",
				HamonSkillBranch.INFUSION, "hamon.skills.infusion",
				HamonSkillBranch.FLEXIBILITY, "hamon.skills.flexibility",
				HamonSkillBranch.HEALING, "hamon.skills.life",
				HamonSkillBranch.ATTRACTANT_REPELLENT, "hamon.skills.attractant_repellent",
				HamonSkillBranch.BODY_MANIPULATION, "hamon.skills.body_manipulation");
		for (Map.Entry<HamonSkillBranch, String> entry : keys.entrySet()) {
			Component desc = HamonSkillsText.branchDesc(entry.getKey());
			helper.assertTrue((entry.getValue() + ".desc").equals(key(desc)),
					entry.getKey() + " description key is " + key(desc));
			helper.assertTrue(desc.getStyle().isItalic(), entry.getKey() + " description must be italic");
		}
		helper.assertTrue(HamonSkillsText.BRANCH_DESC_WRAP == 200, "branch description must wrap at 200 px");

		// strip [START_Y-18, START_Y), columns 68 px wide from x 6
		int top = TREE_START_Y - 18;
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(6, top, TREE_START_Y, 3) == 0, "strip top-left must hit column 0");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(73, TREE_START_Y - 1, TREE_START_Y, 3) == 0, "x 73 must still be column 0");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(74, top + 4, TREE_START_Y, 3) == 1, "x 74 must be column 1");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(6 + 2 * 68, top + 9, TREE_START_Y, 3) == 2, "x 142 must be column 2");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(6 + 3 * 68, top + 9, TREE_START_Y, 3) == -1, "no fourth column");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(5, top + 9, TREE_START_Y, 3) == -1, "left of x 6 is outside the strip");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(10, top - 1, TREE_START_Y, 3) == -1, "above the strip must not hit");
		helper.assertTrue(HamonSkillsText.hoveredBranchColumn(10, TREE_START_Y, TREE_START_Y, 3) == -1, "the node row is not the strip");

		Set<String> screen = methodRefs(helper, PKG + "HamonSkillsScreen");
		helper.assertTrue(hasRef(screen, TEXT + "branchTitleLines("), "HamonSkillsScreen no longer wraps the branch headers");
		helper.assertTrue(hasRef(screen, TEXT + "hoveredBranchColumn("), "HamonSkillsScreen no longer checks the branch strip hover");
		helper.assertTrue(hasRef(screen, TEXT + "branchDesc("), "HamonSkillsScreen no longer shows the branch description");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hamonSkillDetailsUseTranslatedText(GameTestHelper helper) {
		helper.assertTrue(keys(HamonSkillsText.descLines(ModHamonSkills.OVERDRIVE_DEF))
						.equals(List.of("hamonSkill.overdrive.desc", "hamonSkill.overdrive_strong.desc")),
				"Overdrive must add the Hamon Beat line");
		helper.assertTrue(keys(HamonSkillsText.descLines(ModHamonSkills.SENDO_OVERDRIVE_DEF))
						.equals(List.of("hamonSkill.sendo_overdrive.desc")),
				"other skills show only their own description");

		Component prerequisites = HamonSkillsText.prerequisiteNames(ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_DEF.prerequisiteSkills());
		helper.assertTrue(keys(prerequisites.getSiblings())
						.equals(List.of("hamonSkill.sendo_overdrive.name", "hamonSkill.turquoise_blue_overdrive.name")),
				"prerequisites must be translated skill names, got " + keys(prerequisites.getSiblings()));
		helper.assertTrue(prerequisites.getSiblings().size() == 3
				&& ", ".equals(prerequisites.getSiblings().get(1).getString()), "prerequisites must be joined with ', '");

		Component abilities = HamonSkillsText.abilityNames(ModHamonSkills.OVERDRIVE_DEF.unlocksAbilities(), id -> null);
		helper.assertTrue(keys(abilities.getSiblings())
						.equals(List.of("jojo_ripples.ability.hamon_overdrive", "jojo_ripples.ability.hamon_beat")),
				"abilities must fall back to core ability keys, got " + keys(abilities.getSiblings()));
		Component addon = HamonSkillsText.abilityNames(List.of("x"), id -> "addon.ability." + id);
		helper.assertTrue(keys(addon.getSiblings()).equals(List.of("addon.ability.x")),
				"a moveset ability's own key must win over the core key");

		Set<String> screen = methodRefs(helper, PKG + "HamonSkillsScreen");
		helper.assertTrue(hasRef(screen, TEXT + "descLines("), "HamonSkillsScreen no longer uses the skill description lines");
		helper.assertTrue(hasRef(screen, TEXT + "prerequisiteNames("), "HamonSkillsScreen lists raw prerequisite ids");
		helper.assertTrue(hasRef(screen, TEXT + "abilityNames("), "HamonSkillsScreen lists raw ability ids");
		helper.succeed();
	}

	// 1.16 HamonTechniqueTabGui: empty techniqueSkillRequirements leaves the locked tab blank
	@GameTest(template = "empty")
	public static void hamonTechniquesLockedTextHiddenWhenDisabled(GameTestHelper helper) {
		helper.assertTrue(HamonSkillsText.techniquesLockedLines(false, Integer.MAX_VALUE).isEmpty(),
				"disabled techniques must show no unlock level text");
		List<Component> locked = HamonSkillsText.techniquesLockedLines(true, 2);
		helper.assertTrue(locked.size() == 1 && "hamon.techniques_locked".equals(key(locked.get(0))),
				"enabled techniques must show hamon.techniques_locked, got " + locked);
		Object[] args = ((TranslatableContents) locked.get(0).getContents()).getArgs();
		helper.assertTrue(args.length == 1 && Integer.valueOf(2).equals(args[0]),
				"unlock text must carry the first slot requirement");

		Set<String> screen = methodRefs(helper, PKG + "HamonSkillsScreen");
		helper.assertTrue(hasRef(screen, TEXT + "techniquesLockedLines("), "HamonSkillsScreen builds the locked text itself");
		helper.assertTrue(hasRef(screen, "rotp/core/impl/powers/hamon/HamonData.techniquesEnabled("),
				"HamonSkillsScreen no longer checks whether techniques are enabled");
		helper.succeed();
	}

	private static String key(Component component) {
		return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
	}

	private static List<String> keys(List<Component> components) {
		List<String> out = new ArrayList<>();
		for (Component component : components) {
			String key = key(component);
			if (key != null) {
				out.add(key);
			}
		}
		return out;
	}

	private static boolean hasRef(Set<String> refs, String prefix) {
		return refs.stream().anyMatch(ref -> ref.startsWith(prefix));
	}

	// owner.name+descriptor of every method ref in the class constant pool
	private static Set<String> methodRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = HamonSkillsTextGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HamonSkillsTextGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 3, 4 -> in.readInt();
				case 5, 6 -> { in.readLong(); i++; }
				case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
				case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
				default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			Set<String> refs = new HashSet<>();
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10 || tag[i] == 11) {
					String owner = utf[a[a[i]]];
					int nameAndType = b[i];
					refs.add(owner + "." + utf[a[nameAndType]] + utf[b[nameAndType]]);
				}
			}
			return refs;
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return Set.of();
		}
	}
}
