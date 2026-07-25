package com.github.standobyte.jojo.command.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.standobyte.jojo.command.MultipleTargetsCommandResult;
import com.github.standobyte.jojo.command.argument.StandArgument;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// XXX "/jojo_ripples stand random"
public class StandCommand {
	public static final MultipleTargetsCommandResult GIVE_MSG = new MultipleTargetsCommandResult(
			"rotp.commands.stand.give");
	public static final MultipleTargetsCommandResult RANDOM_MSG = new MultipleTargetsCommandResult(
			"rotp.commands.stand.give.random");
	public static final MultipleTargetsCommandResult QUERY_MSG = new MultipleTargetsCommandResult(
			"rotp.commands.stand.query");
	public static final MultipleTargetsCommandResult REMOVE_MSG = new MultipleTargetsCommandResult(
			"rotp.commands.stand.remove", QUERY_MSG.fail);


	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		dispatcher.register(
		Commands.literal(JojoMod.MOD_ID).then(
			Commands.literal("stand")
				.requires(src -> src.hasPermission(2))
				
				.then(
				Commands.literal("give")
					.then(
					Commands.argument("targets", EntityArgument.entities())
						.then(
						Commands.argument("stand", StandArgument.stand(context))
							.executes(
							src -> setStand(
								src.getSource(),
								EntityArgument.getEntities(src, "targets"),
								StandArgument.getStand(src, "stand"),
								false
								)
							)
							.then(
							Commands.argument("replace", BoolArgumentType.bool())
								.executes(
								src -> setStand(
									src.getSource(),
									EntityArgument.getEntities(src, "targets"),
									StandArgument.getStand(src, "stand"),
									BoolArgumentType.getBool(src, "replace")
									)
								)
							)
						)
					)
				)
				.then(
				Commands.literal("random")
					.then(
					Commands.argument("targets", EntityArgument.players())
						.executes(
						src -> setRandomStand(
							src.getSource(),
							EntityArgument.getPlayers(src, "targets"),
							false
							)
						)
						.then(
						Commands.argument("replace", BoolArgumentType.bool())
							.executes(
							src -> setRandomStand(
								src.getSource(),
								EntityArgument.getPlayers(src, "targets"),
								BoolArgumentType.getBool(src, "replace")
								)
							)
						)
					)
				)
				.then(
				Commands.literal("remove")
					.executes(src -> removeStand(src.getSource(), ImmutableList.of(src.getSource().getEntityOrException())))
					.then(
					Commands.argument("targets", EntityArgument.entities())
						.executes(src -> removeStand(src.getSource(), EntityArgument.getEntities(src, "targets")))
					)
				)
			)
		);
		JojoCommandsCommand.addCommand("stand");
	}
	
	private static int setStand(CommandSourceStack src, Collection<? extends Entity> targets, StandType standType, boolean replace) throws CommandSyntaxException {
		int i = 0;
		for (Entity entity : targets) {
			if (entity instanceof LivingEntity living) {
				PowerClass.STAND.attachPower(living);
				StandPower stand = PowerClass.STAND.get(living);
				if (stand != null && (replace || !stand.hasPower())) {
					stand.setStand(standType);
					i++;
				}
			}
		}
		
		return GIVE_MSG.trySend(src, true, targets, i, standType.name.get());
	}
	
	private static int setRandomStand(CommandSourceStack src, Collection<ServerPlayer> targets, boolean replace) {
		int i = 0;
		for (ServerPlayer player : targets) {
			PowerClass.STAND.attachPower(player);
			StandPower stand = PowerClass.STAND.get(player);
			if (stand == null) {
				continue;
			}
			if (!replace && stand.hasPower()) {
				src.sendFailure(Component.translatable("rotp.commands.stand.give.failed.single", player.getDisplayName()));
				continue;
			}
			
			Either<StandType, Component> standOrError = StandUtil.randomStandOrError(player, player.getRandom());
			if (standOrError.right().isPresent()) {
				src.sendFailure(Component.translatable("commands.list.nameAndId",
						Component.translatable("rotp.commands.stand.give.random.failed.single", player.getDisplayName()),
						standOrError.right().get()));
				continue;
			}
			
			StandType standType = standOrError.left().orElse(null);
			if (standType != null) {
				stand.setStand(standType);
				i++;
			}
		}
		
		if (i > 0) {
			RANDOM_MSG.success.send(src, true, targets, i, new Object[] {}, new Object[] {});
		}
		return i;
	}
	
	private static int removeStand(CommandSourceStack src, Collection<? extends Entity> targets) throws CommandSyntaxException {
		int i = 0;
		StandType singlePrevType = null;
		for (Entity entity : targets) {
			if (entity instanceof LivingEntity living) {
				StandPower stand = StandPower.get(living);
				if (stand != null && stand.hasPower()) {
					singlePrevType = stand.getPowerType();
					stand.setStand(null);
					i++;
				}
			}
		}
		
		return REMOVE_MSG.trySend(src, true, targets, i, 
				singlePrevType != null ? new Object[] { singlePrevType.name.get() } : new Object[] {},
				new Object[] {});
	}


	public static Collection<StandPower> getStands(Collection<? extends Entity> targets) throws CommandSyntaxException {
		List<StandPower> stands = new ArrayList<>();
		for (Entity entity : targets) {
			if (entity instanceof LivingEntity living) {
				StandPower stand = StandPower.get(living);
				if (stand != null && stand.hasPower()) {
					stands.add(stand);
				}
			}
		}
		if (stands.isEmpty()) {
			throw StandCommand.QUERY_MSG.fail.create(targets);
		}
		else {
			return stands;
		}
	}
}
