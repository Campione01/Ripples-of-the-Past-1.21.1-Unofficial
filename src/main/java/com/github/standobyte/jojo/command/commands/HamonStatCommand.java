package com.github.standobyte.jojo.command.commands;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.function.BinaryOperator;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData.HamonStat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public class HamonStatCommand {
	private static final DecimalFormat STAT_FORMAT = new DecimalFormat("#.##");
	private static final DynamicCommandExceptionType SINGLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			player -> Component.translatable("commands.hamon.failed.single", player));
	private static final DynamicCommandExceptionType MULTIPLE_FAILED_EXCEPTION = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.hamon.failed.multiple", count));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(statCommand("hamonstat"));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID)
				.then(Commands.literal("power_hamon")
						.then(statCommand("stat"))));
		JojoCommandsCommand.addCommand("hamonstat");
		JojoCommandsCommand.addCommand("power_hamon");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> statCommand(String literal) {
		return Commands.literal(literal).requires(ctx -> ctx.hasPermission(2))
				.then(Commands.literal("set")
						.then(setOrAddStat("strength", HamonStat.STRENGTH, false))
						.then(setOrAddStat("control", HamonStat.CONTROL, false))
						.then(setOrAddBreathing("breathing", false)))
				.then(Commands.literal("add")
						.then(setOrAddStat("strength", HamonStat.STRENGTH, true))
						.then(setOrAddStat("control", HamonStat.CONTROL, true))
						.then(setOrAddBreathing("breathing", true)))
				.then(Commands.literal("query")
						.then(Commands.literal("strength")
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> getHamonStat(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "target"),
												HamonStat.STRENGTH))))
						.then(Commands.literal("control")
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> getHamonStat(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "target"),
												HamonStat.CONTROL))))
						.then(Commands.literal("breathing")
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> getBreathing(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "target"))))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> setOrAddStat(String literal, HamonStat stat, boolean add) {
		return Commands.literal(literal)
				.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("level", FloatArgumentType.floatArg(0, HamonData.MAX_STAT_LEVEL))
								.executes(ctx -> setHamonStat(ctx.getSource(),
										EntityArgument.getPlayers(ctx, "targets"),
										FloatArgumentType.getFloat(ctx, "level"),
										stat,
										true,
										add))
								.then(Commands.argument("ignoreBreathing", BoolArgumentType.bool())
										.executes(ctx -> setHamonStat(ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												FloatArgumentType.getFloat(ctx, "level"),
												stat,
												BoolArgumentType.getBool(ctx, "ignoreBreathing"),
												add)))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> setOrAddBreathing(String literal, boolean add) {
		return Commands.literal(literal)
				.then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("level", FloatArgumentType.floatArg(0, HamonData.MAX_BREATHING_LEVEL))
								.executes(ctx -> setBreathing(ctx.getSource(),
										EntityArgument.getPlayers(ctx, "targets"),
										FloatArgumentType.getFloat(ctx, "level"),
										add))));
	}

	private static int setHamonStat(CommandSourceStack source, Collection<ServerPlayer> targets,
			float level, HamonStat stat, boolean ignoreBreathing, boolean add) throws CommandSyntaxException {
		BinaryOperator<Float> operation = add ? Float::sum : (current, arg) -> arg;
		String msg = add ? "add." : "";
		int success = 0;
		for (ServerPlayer player : targets) {
			if (PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).map(hamon -> {
				int curPoints = stat == HamonStat.STRENGTH ? hamon.getHamonStrengthPoints() : hamon.getHamonControlPoints();
				float currentLevel = levelFractionFromPoints(curPoints);
				float levelToSet = operation.apply(currentLevel, level);
				int pointsToSet = pointsAtLevelFraction(levelToSet);
				hamon.setHamonStatPoints(stat, pointsToSet, ignoreBreathing, true);
				syncHamon(player, hamon);
				return true;
			}).orElse(false)) {
				success++;
			}
		}
		if (success == 0) {
			throw failed(targets);
		}
		String keyBase = stat == HamonStat.STRENGTH ? "commands.hamon.strength." : "commands.hamon.control.";
		sendSuccess(source, targets, success, keyBase + msg + "success", level);
		return success;
	}

	private static int setBreathing(CommandSourceStack source, Collection<ServerPlayer> targets,
			float level, boolean add) throws CommandSyntaxException {
		BinaryOperator<Float> operation = add ? Float::sum : (current, arg) -> arg;
		String msg = add ? "add." : "";
		int success = 0;
		for (ServerPlayer player : targets) {
			if (PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).map(hamon -> {
				hamon.setBreathingLevel(operation.apply(hamon.getBreathingLevel(), level));
				syncHamon(player, hamon);
				return true;
			}).orElse(false)) {
				success++;
			}
		}
		if (success == 0) {
			throw failed(targets);
		}
		sendSuccess(source, targets, success, "commands.hamon.breathing." + msg + "success", level);
		return success;
	}

	private static int getHamonStat(CommandSourceStack source, ServerPlayer target, HamonStat stat) throws CommandSyntaxException {
		HamonData hamon = PlayerPower.getPowerData(target, ModPlayerPowers.HAMON)
				.orElseThrow(() -> SINGLE_FAILED_EXCEPTION.create(target.getDisplayName()));
		int points = stat == HamonStat.STRENGTH ? hamon.getHamonStrengthPoints() : hamon.getHamonControlPoints();
		float level = levelFractionFromPoints(points);
		source.sendSuccess(() -> Component.translatable(
				stat == HamonStat.STRENGTH ? "commands.hamon.strength.query.success" : "commands.hamon.control.query.success",
				target.getDisplayName(), STAT_FORMAT.format(level)), false);
		return (int) level;
	}

	private static int getBreathing(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		HamonData hamon = PlayerPower.getPowerData(target, ModPlayerPowers.HAMON)
				.orElseThrow(() -> SINGLE_FAILED_EXCEPTION.create(target.getDisplayName()));
		float level = hamon.getBreathingLevel();
		source.sendSuccess(() -> Component.translatable("commands.hamon.breathing.query.success",
				target.getDisplayName(), STAT_FORMAT.format(level)), false);
		return (int) level;
	}

	private static void sendSuccess(CommandSourceStack source, Collection<ServerPlayer> targets,
			int success, String keyBase, float level) {
		if (targets.size() == 1) {
			ServerPlayer target = targets.iterator().next();
			source.sendSuccess(() -> Component.translatable(keyBase + ".single", level, target.getDisplayName()), true);
		}
		else {
			source.sendSuccess(() -> Component.translatable(keyBase + ".multiple", level, success), true);
		}
	}

	private static CommandSyntaxException failed(Collection<ServerPlayer> targets) {
		return targets.size() == 1
				? SINGLE_FAILED_EXCEPTION.create(targets.iterator().next().getDisplayName())
				: MULTIPLE_FAILED_EXCEPTION.create(targets.size());
	}

	private static void syncHamon(ServerPlayer player, HamonData hamon) {
		hamon.syncOnUpdate(player);
		ModCriteriaTriggers.triggerHamonStats(player, hamon);
	}

	private static int pointsAtLevelFraction(float level) {
		float clamped = Mth.clamp(level, 0.0F, HamonData.MAX_STAT_LEVEL);
		int lower = Mth.floor(clamped);
		int upper = Mth.ceil(clamped);
		if (lower == upper) {
			return HamonData.pointsAtLevel(lower);
		}
		int lowerPoints = HamonData.pointsAtLevel(lower);
		int upperPoints = HamonData.pointsAtLevel(upper);
		return Mth.floor(Mth.lerp(clamped - lower, lowerPoints, upperPoints));
	}

	private static float levelFractionFromPoints(int points) {
		int level = HamonData.levelFromPoints(points);
		if (level >= HamonData.MAX_STAT_LEVEL) {
			return HamonData.MAX_STAT_LEVEL;
		}
		int lowerPoints = HamonData.pointsAtLevel(level);
		int upperPoints = HamonData.pointsAtLevel(level + 1);
		if (upperPoints <= lowerPoints) {
			return level;
		}
		return level + Mth.clamp((points - lowerPoints) / (float) (upperPoints - lowerPoints), 0.0F, 1.0F);
	}
}
