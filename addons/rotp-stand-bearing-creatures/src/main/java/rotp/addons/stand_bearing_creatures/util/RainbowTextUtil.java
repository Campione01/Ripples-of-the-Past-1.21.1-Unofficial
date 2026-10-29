package rotp.addons.stand_bearing_creatures.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class RainbowTextUtil {
    private static final ChatFormatting[] RAINBOW_COLORS = {
            ChatFormatting.RED,
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.AQUA,
            ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.OBFUSCATED,
            ChatFormatting.DARK_RED,
            ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.OBFUSCATED
    };

    public static MutableComponent rainbow(String text, boolean bold) {
        int index = (int) ((System.currentTimeMillis() / 1000L)
                % RAINBOW_COLORS.length);
        MutableComponent component = Component.literal(text)
                .withStyle(RAINBOW_COLORS[index]);
        return bold
                ? component.withStyle(ChatFormatting.BOLD)
                : component;
    }

    public static int rainbowArgb() {
        ChatFormatting color = RAINBOW_COLORS[
                (int) ((System.currentTimeMillis() / 1000L)
                        % RAINBOW_COLORS.length)];
        Integer rgb = color.getColor();
        return 0xFF000000 | (rgb != null ? rgb : 0xFFFFFF);
    }

    private RainbowTextUtil() {}
}
