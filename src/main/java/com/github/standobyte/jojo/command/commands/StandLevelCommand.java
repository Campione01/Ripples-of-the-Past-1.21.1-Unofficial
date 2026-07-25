package com.github.standobyte.jojo.command.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StandLevelCommand {
	private static final DynamicCommandExceptionType STAND_SINGLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			player -> Component.translatable("commands.stand.query.failed.single", player));
	private static final DynamicCommandExceptionType STAND_MULTIPLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.stand.query.failed.multiple", count));
	private static final DynamicCommandExceptionType STAND_RESOLVE_SINGLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			player -> Component.translatable("commands.stand.resolve.failed.single", player));
	private static final DynamicCommandExceptionType STAND_RESOLVE_MULTIPLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.stand.resolve.failed.multiple", count));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(standLevelCommand("standlevel"));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID).then(standLevelCommand("stand_level")));
		JojoCommandsCommand.addCommand("standlevel");
		JojoCommandsCommand.addCommand("stand_level");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> standLevelCommand(String literal) {
		return Commands.literal(literal).requires(ctx -> ctx.hasPermission(2))
				.then(Commands.literal("set")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("level", IntegerArgumentType.integer(0))
										.executes(ctx -> setStandLevel(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												IntegerArgumentType.getInteger(ctx, "level"))))))
				.then(Commands.literal("add")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("levels", IntegerArgumentType.integer(0))
										.executes(ctx -> addStandLevel(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												IntegerArgumentType.getInteger(ctx, "levels"))))))
				.then(Commands.literal("query")
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> getStandLevel(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "target")))));
	}

	private static int getStandLevel(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		StandPower stand = getStands(List.of(target)).iterator().next();
		int level = stand.getResolveLevel();
		source.sendSuccess(() -> Component.translatable("commands.standlevel.query.success",
				target.getDisplayName(), level), false);
		return level;
	}

	private static int addStandLevel(CommandSourceStack source, Collection<ServerPlayer> targets, int levels) throws CommandSyntaxException {
		Collection<StandPower> stands = getStands(targets);
		for (StandPower stand : stands) {
			stand.setResolveLevel(stand.getResolveLevel() + levels);
		}

		if (targets.size() == 1) {
			source.sendSuccess(() -> Component.translatable("commands.standlevel.add.success.single",
					levels, targets.iterator().next().getDisplayName()), true);
		}
		else {
			source.sendSuccess(() -> Component.translatable("commands.standlevel.add.success.multiple",
					levels, stands.size()), true);
		}

		return stands.size();
	}

	private static int setStandLevel(CommandSourceStack source, Collection<ServerPlayer> targets, int level) throws CommandSyntaxException {
		Collection<StandPower> stands = getStands(targets);
		for (StandPower stand : stands) {
			stand.setResolveLevel(level);
		}

		if (targets.size() == 1) {
			source.sendSuccess(() -> Component.translatable("commands.standlevel.set.success.single",
					level, targets.iterator().next().getDisplayName()), true);
		}
		else {
			source.sendSuccess(() -> Component.translatable("commands.standlevel.set.success.multiple",
					level, stands.size()), true);
		}

		return stands.size();
	}

	private static Collection<StandPower> getStands(Collection<ServerPlayer> targets) throws CommandSyntaxException {
		List<StandPower> stands = new ArrayList<>();
		boolean noStand = false;
		for (ServerPlayer player : targets) {
			StandPower stand = StandPower.get(player);
			if (stand == null || !stand.hasPower()) {
				noStand = true;
			}
			else if (stand.usesResolve()) {
				stands.add(stand);
			}
		}
		if (!stands.isEmpty()) {
			return stands;
		}
		if (targets.size() == 1) {
			ServerPlayer player = targets.iterator().next();
			if (noStand) {
				throw STAND_SINGLE_FAILED_EXCEPTION.create(player.getDisplayName());
			}
			throw STAND_RESOLVE_SINGLE_FAILED_EXCEPTION.create(player.getDisplayName());
		}
		if (noStand) {
			throw STAND_MULTIPLE_FAILED_EXCEPTION.create(targets.size());
		}
		throw STAND_RESOLVE_MULTIPLE_FAILED_EXCEPTION.create(targets.size());
	}
}
