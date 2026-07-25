package com.github.standobyte.jojo.command.commands;

import java.util.HashSet;
import java.util.Set;

import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class JojoCommandsCommand {
	private static final Set<String> COMMANDS = new HashSet<>();
	private static final Set<String> TOP_LEVEL_COMMANDS = Set.of(
			"standdisc",
			"standlevel",
			"hamonstat",
			"jojoenergy",
			"pillarman",
			"rockpaperscissors",
			"rps",
			"jojocontrols");

	public static void addCommand(String literal) {
		COMMANDS.add(literal);
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("jojocommands")
				.executes(JojoCommandsCommand::writeContents));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID)
				.then(Commands.literal("commands_list")
						.executes(JojoCommandsCommand::writeContents)));
	}

	private static int writeContents(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(JojoCommandsCommand::buildContents, false);
		return COMMANDS.size();
	}

	private static MutableComponent buildContents() {
		MutableComponent text = Component.empty();
		boolean first = true;
		for (String literal : COMMANDS.stream().sorted().toList()) {
			if (!first) {
				text.append("\n");
			}
			text.append(commandLine(literal));
			first = false;
		}
		return text;
	}

	private static Component commandLine(String literal) {
		String command = commandPath(literal);
		MutableComponent commandText = Component.literal(command).withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
		return Component.translatable("jojo.command_description",
				commandText,
				Component.translatable("jojo.command.desc." + literal));
	}

	private static String commandPath(String literal) {
		return "/" + (TOP_LEVEL_COMMANDS.contains(literal) ? literal : JojoMod.MOD_ID + " " + literal);
	}
}
