package rotp.core.command.commands;

import java.util.Collection;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PillarmanModeCommand {
	private static final DynamicCommandExceptionType SINGLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			player -> Component.translatable("commands.pillarman.failed.single", player));
	private static final DynamicCommandExceptionType MULTIPLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.pillarman.failed.multiple", count));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pillarman").requires(ctx -> ctx.hasPermission(2))
				.then(setCommand()));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID)
				.then(Commands.literal("power_pillarman").requires(ctx -> ctx.hasPermission(2))
						.then(setCommand())));
		JojoCommandsCommand.addCommand("pillarman");
		JojoCommandsCommand.addCommand("power_pillarman");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> setCommand() {
		return Commands.literal("set")
				.then(Commands.literal("stage")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("stage", IntegerArgumentType.integer(1, PillarmanData.MAX_STAGE_LEVEL))
										.executes(ctx -> setStage(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
												IntegerArgumentType.getInteger(ctx, "stage"))))))
				.then(Commands.literal("mode")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.literal("light")
										.executes(ctx -> setMode(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), PillarmanMode.LIGHT)))
								.then(Commands.literal("wind")
										.executes(ctx -> setMode(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), PillarmanMode.WIND)))
								.then(Commands.literal("heat")
										.executes(ctx -> setMode(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), PillarmanMode.HEAT)))
								.then(Commands.literal("none")
										.executes(ctx -> setMode(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), PillarmanMode.NONE)))));
	}

	private static int setStage(CommandSourceStack source, Collection<ServerPlayer> targets, int stage) throws CommandSyntaxException {
		final int stageLevel = stage;
		int success = 0;
		for (ServerPlayer player : targets) {
			if (PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).map(data -> {
				data.setEvolutionStage(stageLevel, player);
				return true;
			}).orElse(false)) {
				success++;
			}
		}
		if (success == 0) {
			throw failed(targets);
		}
		final int successCount = success;
		if (targets.size() == 1) {
			Component targetName = targets.iterator().next().getDisplayName();
			source.sendSuccess(() -> Component.translatable("commands.pillarman.stage.success.single",
					stageLevel, targetName), true);
		}
		else {
			source.sendSuccess(() -> Component.translatable("commands.pillarman.stage.success.multiple",
					stageLevel, successCount), true);
		}
		return success;
	}

	private static int setMode(CommandSourceStack source, Collection<ServerPlayer> targets, PillarmanMode mode) throws CommandSyntaxException {
		final PillarmanMode selectedMode = mode;
		int success = 0;
		for (ServerPlayer player : targets) {
			if (PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).map(data -> {
				data.setMode(selectedMode, player);
				return true;
			}).orElse(false)) {
				success++;
			}
		}
		if (success == 0) {
			throw failed(targets);
		}
		final int successCount = success;
		if (targets.size() == 1) {
			Component targetName = targets.iterator().next().getDisplayName();
			source.sendSuccess(() -> modeSuccessSingle(selectedMode, targetName), true);
		}
		else {
			source.sendSuccess(() -> modeSuccessMultiple(selectedMode, successCount), true);
		}
		return success;
	}

	static Component modeSuccessSingle(PillarmanMode mode, Component targetName) {
		return Component.translatable("commands.pillarman.mode.success.single", modeArg(mode), targetName);
	}

	static Component modeSuccessMultiple(PillarmanMode mode, int count) {
		return Component.translatable("commands.pillarman.mode.success.multiple", modeArg(mode), count);
	}

	// 1.16 printed the Mode enum by its name. A 1.21 translation argument must be a Component, Number, Boolean
	// or String: the enum itself threw IllegalArgumentException after the mode was already set.
	static String modeArg(PillarmanMode mode) {
		return mode.name();
	}

	private static CommandSyntaxException failed(Collection<ServerPlayer> targets) {
		return targets.size() == 1
				? SINGLE_FAILED_EXCEPTION.create(targets.iterator().next().getDisplayName())
				: MULTIPLE_FAILED_EXCEPTION.create(targets.size());
	}
}
