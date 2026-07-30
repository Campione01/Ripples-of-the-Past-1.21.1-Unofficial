package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HamonPayloadContractSmokeTest {
	private HamonPayloadContractSmokeTest() {}

	public static void run() {
		Set<String> known = HamonTeachersSkillsPacket.knownTeacherSkills(
				Set.of("overdrive", "not_a_registered_hamon_skill"),
				"overdrive"::equals);
		check(known.equals(Set.of("overdrive")),
				"teacher-skill handler allowlist must retain known skills and remove unknown names");

		Set<String> maximumTeacherSkills = new HashSet<>();
		for (int i = 0; i < 128; i++) {
			maximumTeacherSkills.add("extension_skill_" + i);
		}
		HamonTeachersSkillsPacket maximumTeacherPacket =
				new HamonTeachersSkillsPacket(maximumTeacherSkills);
		check(maximumTeacherPacket.teacherSkills().size() == 128,
				"fixed teacher-skill wire capacity must allow extension headroom");
		maximumTeacherSkills.add("extension_skill_128");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonTeachersSkillsPacket(maximumTeacherSkills),
				"teacher-skill packet must reject count above fixed wire capacity");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonTeachersSkillsPacket(Set.of("x".repeat(129))),
				"teacher-skill packet must reject names above fixed wire capacity");

		List<String> maximumLearnableSkills = new ArrayList<>();
		for (int i = 0; i < 128; i++) {
			maximumLearnableSkills.add("extension_skill_" + i);
		}
		HamonStatFeedbackPacket maximumFeedback = new HamonStatFeedbackPacket(
				1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
				false, maximumLearnableSkills);
		check(maximumFeedback.newlyLearnableSkills().size() == 128,
				"fixed learnable-skill wire capacity must allow extension headroom");
		maximumLearnableSkills.add("extension_skill_128");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonStatFeedbackPacket(
						1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
						false, maximumLearnableSkills),
				"learnable-skill feedback must reject count above fixed wire capacity");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonStatFeedbackPacket(
						1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
						false, List.of("x".repeat(129))),
				"learnable-skill feedback must reject names above fixed wire capacity");
	}

	private static void expectThrows(
			Class<? extends Throwable> expected, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
