package rotp.core.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import rotp.core.impl.powers.hamon.HamonData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Tab-name tooltip lines of the JoJo menu (1.16 HamonScreen tab tooltips).
 * No client classes here, so gametests can call it.
 */
public final class JojoMenuTabTooltips {
	public static final String TECHNIQUE_AVAILABLE_KEY = "hamon.technique_available";
	public static final int NAME_WIDTH = 170;
	public static final int EXTRA_LINE_WIDTH = 100;

	private JojoMenuTabTooltips() {}

	// black name (Tooltip.splitTooltip width), then each extra line split at 100 px like 1.16
	public static <T> List<T> lines(Component name, List<Component> extraLines,
			BiFunction<Component, Integer, List<T>> split) {
		List<T> lines = new ArrayList<>(split.apply(name.copy().withStyle(ChatFormatting.BLACK), NAME_WIDTH));
		for (Component line : extraLines) {
			lines.addAll(split.apply(line, EXTRA_LINE_WIDTH));
		}
		return lines;
	}

	// 1.16 HamonTechniqueManager.canLearnNewTechniqueSkill: a free slot whose level is reached
	public static boolean canLearnNewTechniqueSkill(@Nullable HamonData data) {
		if (data == null) {
			return false;
		}
		int learned = data.getLearnedTechniqueSkillCount();
		return learned < HamonData.techniqueSlotsCount() && data.hasTechniqueLevel(learned);
	}

	// 1.16 HamonTechniqueTabGui: italic gray on a dark tooltip; dark gray reads on the paper one
	public static List<Component> hamonTechniqueLines(@Nullable HamonData data) {
		if (!canLearnNewTechniqueSkill(data)) {
			return List.of();
		}
		return List.of(Component.translatable(TECHNIQUE_AVAILABLE_KEY)
				.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
	}
}
