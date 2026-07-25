package com.github.standobyte.jojo.command.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

public class JojoControlsCommand {
    public static final String LITERAL = "jojocontrols";

    private static final Component[] TEXT_PAGES = {
            Component.translatable("jojo.chat.command.controls.overlay",
                    keybind("jojo_ripples.key.stand_mode"),
                    keybind("jojo_ripples.key.non_stand_mode")).withStyle(ChatFormatting.GRAY),
            Component.translatable("jojo.chat.command.controls.overlay.scroll",
                    keybind("jojo_ripples.key.ability_hotbar")).withStyle(ChatFormatting.GRAY),
            Component.translatable("jojo.chat.command.controls.overlay.use",
                    keybind("key.attack"),
                    keybind("jojo_ripples.key.use_special_ability")).withStyle(ChatFormatting.GRAY),
            Component.translatable("jojo.chat.command.controls.stand",
                    keybind("jojo_ripples.key.toggle_stand"),
                    Component.translatable("jojo_ripples.skill.manual_control.controls").withStyle(ChatFormatting.ITALIC))
                    .withStyle(ChatFormatting.GRAY),
            Component.translatable("jojo.chat.command.controls.layout_edit",
                    keybind("jojo_ripples.key.jojo_menu")).withStyle(ChatFormatting.GRAY),
            Component.translatable("jojo.chat.command.controls.changeable").withStyle(ChatFormatting.GRAY)
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(LITERAL)
                .executes(ctx -> writePage(1, ctx))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
                        .executes(ctx -> writePage(IntegerArgumentType.getInteger(ctx, "page"), ctx))));
        JojoCommandsCommand.addCommand(LITERAL);
    }

    private static Component keybind(String key) {
        return Component.keybind(key).withStyle(ChatFormatting.ITALIC);
    }

    private static int writePage(int requestedPage, CommandContext<CommandSourceStack> ctx) {
        int page = Mth.clamp(requestedPage, 1, TEXT_PAGES.length);
        MutableComponent text = Component.translatable("jojo.chat.command.controls.page",
                String.valueOf(page), String.valueOf(TEXT_PAGES.length)).withStyle(ChatFormatting.DARK_GRAY);
        text.append("\n");
        text.append(TEXT_PAGES[page - 1].copy());
        if (page < TEXT_PAGES.length) {
            int nextPage = page + 1;
            String command = "/" + LITERAL + " " + nextPage;
            text.append("\n");
            text.append(Component.translatable("jojo.chat.command.controls.next_page").withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("jojo.chat.controls.tooltip")))));
        }
        ctx.getSource().sendSuccess(() -> text, false);
        return 1;
    }
}
