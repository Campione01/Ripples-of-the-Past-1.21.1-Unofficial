package rotp.addons.gratefuldead.service;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class AgingText {
    private static final int NAME_LENGTH = 8;

    private AgingText() {}

    public static Component obfuscatedName(
            Component original) {
        String value = original != null
                ? original.getString()
                : "";
        value = ChatFormatting.stripFormatting(value);
        if (value == null) {
            value = "";
        }
        StringBuilder normalized =
                new StringBuilder(value);
        if (normalized.length() > NAME_LENGTH) {
            normalized.setLength(NAME_LENGTH);
        }
        while (normalized.length() < NAME_LENGTH) {
            normalized.append('X');
        }
        return Component.literal(normalized.toString())
                .withStyle(ChatFormatting.OBFUSCATED);
    }

    public static Component obfuscatedMessage(
            String original) {
        String value = ChatFormatting.stripFormatting(
                original != null ? original : "");
        return Component.literal(value != null ? value : "")
                .withStyle(ChatFormatting.OBFUSCATED);
    }
}
