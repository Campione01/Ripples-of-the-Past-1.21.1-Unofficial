package com.github.standobyte.jojo.command.commands;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import com.github.standobyte.jojo.command.MultipleTargetsCommandResult;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StandSkillsCommand {
	private static final MultipleTargetsCommandResult UNLOCK_MSG = new MultipleTargetsCommandResult("commands.standskills.unlock");
	private static final MultipleTargetsCommandResult UNLOCK_ALL_MSG = new MultipleTargetsCommandResult("commands.standskills.unlock_all");
	private static final MultipleTargetsCommandResult RESET_MSG = new MultipleTargetsCommandResult("commands.standskills.reset");
	private static final DynamicCommandExceptionType UNKNOWN_SKILL_EXCEPTION = new DynamicCommandExceptionType(
			skill -> Component.translatable("commands.standskills.unlock.unknown", skill));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(JojoMod.MOD_ID).then(standSkillsCommand("stand_skills")));
		JojoCommandsCommand.addCommand("stand_skills");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> standSkillsCommand(String literal) {
		return Commands.literal(literal).requires(src -> src.hasPermission(2))
				.then(Commands.literal("unlock")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("skill", StringArgumentType.word())
										.suggests(StandSkillsCommand::suggestKnownSkills)
										.executes(ctx -> unlockSkill(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												StringArgumentType.getString(ctx, "skill"))))))
				.then(Commands.literal("unlock_all")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> unlockAllSkills(ctx.getSource(),
										EntityArgument.getPlayers(ctx, "targets")))))
				.then(Commands.literal("reset")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> resetSkills(ctx.getSource(),
										EntityArgument.getPlayers(ctx, "targets")))));
	}

	private static int unlockSkill(CommandSourceStack source, Collection<ServerPlayer> targets, String skillName) throws CommandSyntaxException {
		Collection<StandPower> stands = StandCommand.getStands(targets);
		int successful = 0;
		boolean knownSkill = false;
		for (StandPower stand : stands) {
			StandTypePersistentData data = stand.getCurTypeData();
			if (data == null || !data.getAllSkills().containsKey(skillName)) {
				continue;
			}
			knownSkill = true;
			if (data._setSkillUnlocked(skillName, true, false)) {
				data.syncOnUpdate(stand.getUser());
				successful++;
			}
		}
		if (!knownSkill) {
			throw UNKNOWN_SKILL_EXCEPTION.create(skillName);
		}
		return UNLOCK_MSG.trySend(source, true, targets, successful, skillName);
	}

	private static int unlockAllSkills(CommandSourceStack source, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Collection<StandPower> stands = StandCommand.getStands(targets);
		for (StandPower stand : stands) {
			stand.skipProgression();
		}
		return UNLOCK_ALL_MSG.success.send(source, true, targets, stands.size(), new Object[] {}, new Object[] {});
	}

	private static int resetSkills(CommandSourceStack source, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		Collection<StandPower> stands = StandCommand.getStands(targets);
		for (StandPower stand : stands) {
			StandTypePersistentData data = stand.getCurTypeData();
			if (data != null) {
				data.resetUnlockedSkills(stand);
			}
		}
		return RESET_MSG.success.send(source, true, targets, stands.size(), new Object[] {}, new Object[] {});
	}

	private static CompletableFuture<Suggestions> suggestKnownSkills(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		Set<String> suggestions = new TreeSet<>();
		try {
			for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
				StandPower stand = StandPower.get(player);
				StandTypePersistentData data = stand != null ? stand.getCurTypeData() : null;
				if (stand != null && stand.hasPower() && data != null) {
					suggestions.addAll(data.getAllSkills().keySet());
				}
			}
		}
		catch (CommandSyntaxException ignored) {}
		return SharedSuggestionProvider.suggest(suggestions, builder);
	}
}
