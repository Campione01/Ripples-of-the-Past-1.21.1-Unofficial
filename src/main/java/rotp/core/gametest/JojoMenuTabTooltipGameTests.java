package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import rotp.core.client.ui.screen_jojomenu.JojoMenuTabTooltips;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import com.mojang.authlib.GameProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonTechniqueTabGui.additionalTabNameTooltipInfo: while a new technique skill can be learned,
 * the Techniques tab tooltip adds the italic 'hamon.technique_available' line under the name (split at
 * 100 px); the badge uses the same condition. The port showed the name only. The menu screens are
 * client-only, so their wiring is read from the class files.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JojoMenuTabTooltipGameTests {
	private JojoMenuTabTooltipGameTests() {}

	private static final String PKG = "rotp/core/client/ui/screen_jojomenu/";
	private static final String TOOLTIPS = PKG + "JojoMenuTabTooltips";
	private static final String HAMON_DATA = "Lrotp/core/impl/powers/hamon/HamonData;";

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void techniqueHintFollowsFreeSlot(GameTestHelper helper) {
		assertHint(helper, null, false, "No Hamon data");
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TechniqueTabHint"));
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add Hamon player");
		try {
			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
			int slots = HamonData.techniqueSlotsCount();
			helper.assertTrue(slots > 0, "Default config has no technique slots");
			int first = HamonData.techniqueSkillRequirement(0);
			int top = first;
			for (int i = 1; i < slots; i++) {
				top = Math.max(top, HamonData.techniqueSkillRequirement(i));
			}
			helper.assertTrue(first > 0 && top <= HamonData.MAX_STAT_LEVEL, "Unexpected slot levels: first " + first + ", top " + top);

			setLevels(helper, hamon, 0, 0);
			assertHint(helper, hamon, false, "Level 0/0");
			setLevels(helper, hamon, first, first - 1);
			assertHint(helper, hamon, false, "Control one level short of the first slot");
			setLevels(helper, hamon, first, first);
			assertHint(helper, hamon, true, "First slot open, nothing learned");

			List<HamonSkill> techniqueSkills = new ArrayList<>();
			for (var holder : ModHamonSkills.HAMON_SKILLS.getEntries()) {
				if (ModHamonSkills.isTechniqueSkill(holder.getId().getPath())) {
					techniqueSkills.add(holder.get());
				}
			}
			helper.assertTrue(techniqueSkills.size() >= slots, "Only " + techniqueSkills.size() + " technique skills for " + slots + " slots");

			learn(helper, hamon, techniqueSkills.get(0), 1);
			if (slots > 1 && HamonData.techniqueSkillRequirement(1) > first) {
				assertHint(helper, hamon, false, "Second slot level not reached");
			}
			setLevels(helper, hamon, top, top);
			for (int learned = 1; learned < slots; learned++) {
				assertHint(helper, hamon, true, learned + " of " + slots + " slots used");
				learn(helper, hamon, techniqueSkills.get(learned), learned + 1);
			}
			assertHint(helper, hamon, false, "All " + slots + " slots used");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tabTooltipAddsExtraLines(GameTestHelper helper) {
		Component name = Component.literal("Techniques");
		List<String> calls = new ArrayList<>();
		Component[] title = new Component[1];
		List<String> lines = JojoMenuTabTooltips.lines(name, List.of(Component.literal("a"), Component.literal("b")),
				(line, width) -> {
					if (title[0] == null) {
						title[0] = line;
					}
					calls.add(line.getString() + "@" + width);
					return List.of(line.getString());
				});
		helper.assertTrue(lines.equals(List.of("Techniques", "a", "b")), "Tooltip lines: " + lines);
		helper.assertTrue(calls.equals(List.of("Techniques@170", "a@100", "b@100")), "Split widths: " + calls);
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.BLACK).equals(title[0].getStyle().getColor()),
				"Tab name is not black on the paper tooltip");
		helper.assertTrue(name.getStyle().getColor() == null, "The shared tab name component was restyled");

		ClassRefs screen = classRefs(helper, PKG + "IJojoMenuScreen", true);
		helper.assertTrue(screen.methods.contains(PKG + "IJojoMenuTab.getTooltipExtraLines()Ljava/util/List;"),
				"The tab tooltip never asks the tab for extra lines");
		helper.assertTrue(screen.methods.contains(TOOLTIPS + ".lines(Lnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/util/function/BiFunction;)Ljava/util/List;"),
				"The tab tooltip does not build its lines with JojoMenuTabTooltips.lines");
		helper.assertTrue(screen.methods.contains("net/minecraft/client/gui/screens/Screen.setTooltipForNextRenderPass(Ljava/util/List;)V"),
				"The tab tooltip does not show the line list");

		boolean techniqueTabWired = false;
		for (int i = 1; i <= 64 && !techniqueTabWired; i++) {
			ClassRefs anon = classRefs(helper, PKG + "JojoMenuTabs$" + i, false);
			techniqueTabWired = anon != null && anon.utf.contains("getTooltipExtraLines")
					&& anon.methods.contains(TOOLTIPS + ".hamonTechniqueLines(" + HAMON_DATA + ")Ljava/util/List;");
		}
		helper.assertTrue(techniqueTabWired, "The Hamon Techniques tab does not add the technique_available line");
		ClassRefs tabs = classRefs(helper, PKG + "JojoMenuTabs", true);
		helper.assertTrue(tabs.methods.contains(TOOLTIPS + ".canLearnNewTechniqueSkill(" + HAMON_DATA + ")Z"),
				"The Techniques tab badge does not share the tooltip condition");
		helper.succeed();
	}

	private static void setLevels(GameTestHelper helper, HamonData hamon, int strength, int control) {
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.pointsAtLevel(strength), true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(control), true, true);
		helper.assertTrue(hamon.getHamonStrengthLevel() == strength && hamon.getHamonControlLevel() == control,
				"Stat levels not applied: expected " + strength + "/" + control + ", actual "
						+ hamon.getHamonStrengthLevel() + "/" + hamon.getHamonControlLevel());
	}

	private static void learn(GameTestHelper helper, HamonData hamon, HamonSkill skill, int expectedCount) {
		hamon.learnSkill(skill);
		helper.assertTrue(hamon.getLearnedTechniqueSkillCount() == expectedCount,
				"Technique skill " + skill.getRegistryKey() + " not counted: " + hamon.getLearnedTechniqueSkillCount());
	}

	private static void assertHint(GameTestHelper helper, HamonData hamon, boolean expected, String label) {
		helper.assertTrue(JojoMenuTabTooltips.canLearnNewTechniqueSkill(hamon) == expected,
				label + ": technique badge condition should be " + expected);
		List<Component> lines = JojoMenuTabTooltips.hamonTechniqueLines(hamon);
		if (!expected) {
			helper.assertTrue(lines.isEmpty(), label + ": technique_available shown without a learnable slot");
			return;
		}
		helper.assertTrue(lines.size() == 1, label + ": expected one hint line, got " + lines.size());
		Component line = lines.get(0);
		helper.assertTrue(line.getContents() instanceof TranslatableContents contents
				&& "hamon.technique_available".equals(contents.getKey()), label + ": wrong hint line " + line);
		helper.assertTrue(line.getStyle().isItalic(), label + ": hint line is not italic");
	}

	private record ClassRefs(Set<String> methods, Set<String> utf) {}

	// method refs (owner.name+descriptor) and UTF8 entries of a class constant pool; null when absent and optional
	private static ClassRefs classRefs(GameTestHelper helper, String className, boolean required) {
		String path = className + ".class";
		InputStream raw = JojoMenuTabTooltipGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = JojoMenuTabTooltipGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		if (raw == null) {
			helper.assertTrue(!required, "Missing class file " + path);
			return null;
		}
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
			Set<String> methods = new HashSet<>();
			Set<String> strings = new HashSet<>();
			for (int i = 1; i < count; i++) {
				if (tag[i] == 1) {
					strings.add(utf[i]);
				}
				else if (tag[i] == 10 || tag[i] == 11) {
					int nameAndType = b[i];
					methods.add(utf[a[a[i]]] + "." + utf[a[nameAndType]] + utf[b[nameAndType]]);
				}
			}
			return new ClassRefs(methods, strings);
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return null;
		}
	}
}
