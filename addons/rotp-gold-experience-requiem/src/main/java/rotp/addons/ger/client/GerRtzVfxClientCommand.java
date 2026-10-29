package rotp.addons.ger.client;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;

import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class GerRtzVfxClientCommand {
    private GerRtzVfxClientCommand() {}

    public static void register(
            RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("gerrtzvfx")
                        .executes(context -> status())
                        .then(Commands.literal("normal")
                                .then(modeArgument()
                                        .executes(context ->
                                                setNormal(
                                                        StringArgumentType
                                                                .getString(
                                                                        context,
                                                                        "mode")))))
                        .then(Commands.literal("timeerase")
                                .then(modeArgument()
                                        .executes(context ->
                                                setTimeErase(
                                                        StringArgumentType
                                                                .getString(
                                                                        context,
                                                                        "mode")))))
                        .then(Commands.literal("reset")
                                .executes(context -> reset())));
    }

    private static com.mojang.brigadier.builder
            .RequiredArgumentBuilder<
                    net.minecraft.commands.CommandSourceStack,
                    String> modeArgument() {
        return Commands.argument(
                        "mode", StringArgumentType.word())
                .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(
                                Arrays.stream(Mode.values())
                                        .map(Mode::id),
                                builder));
    }

    private static int setNormal(String value) {
        Mode mode = Mode.parse(value);
        if (mode == null) {
            return error(value);
        }
        GerRtzVfxSettings.setNormalMode(mode);
        return status();
    }

    private static int setTimeErase(String value) {
        Mode mode = Mode.parse(value);
        if (mode == null) {
            return error(value);
        }
        GerRtzVfxSettings.setTimeEraseMode(mode);
        return status();
    }

    private static int reset() {
        GerRtzVfxSettings.reset();
        return status();
    }

    private static int status() {
        message(
                "normal="
                        + GerRtzVfxSettings.normalMode().id()
                        + ", timeerase="
                        + GerRtzVfxSettings
                                .timeEraseMode()
                                .id(),
                ChatFormatting.GRAY);
        return 1;
    }

    private static int error(String value) {
        message(
                "Unknown mode " + value
                        + "; use normal, time_erase, or off",
                ChatFormatting.RED);
        return 0;
    }

    private static void message(
            String text, ChatFormatting color) {
        Minecraft.getInstance().gui.getChat().addMessage(
                Component.literal("[GER RTZ] " + text)
                        .withStyle(color));
    }
}
