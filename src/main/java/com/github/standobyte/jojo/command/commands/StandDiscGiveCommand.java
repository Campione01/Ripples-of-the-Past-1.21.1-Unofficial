package com.github.standobyte.jojo.command.commands;

import java.util.Collection;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.command.argument.StandArgument;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.mechanics.standdisc.StandDiscItem;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class StandDiscGiveCommand {
	
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		dispatcher.register(standDiscCommand("standdisc", context));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID).then(standDiscCommand("stand_disc", context)));
		JojoCommandsCommand.addCommand("standdisc");
		JojoCommandsCommand.addCommand("stand_disc");
	}
	
	private static LiteralArgumentBuilder<CommandSourceStack> standDiscCommand(String literal, CommandBuildContext context) {
		return Commands.literal(literal).requires(src -> src.hasPermission(2))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("stand", StandArgument.stand(context))
										.executes(src -> giveStandDiscItem(src.getSource(),
												StandArgument.getStand(src, "stand"),
												EntityArgument.getPlayers(src, "targets"))))))
				.then(Commands.literal("random")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(src -> giveStandDiscItem(src.getSource(),
										null,
										EntityArgument.getPlayers(src, "targets")))));
	}
	
	private static int giveStandDiscItem(CommandSourceStack source, @Nullable StandType standType,
			Collection<ServerPlayer> targets) throws CommandSyntaxException {
		int successful = 0;
		boolean random = standType == null;
		for (ServerPlayer player : targets) {
			StandType standForPlayer = standType;
			if (random) {
				Either<StandType, Component> randomStandOrError = StandUtil.randomStandOrError(player, player.getRandom());
				if (randomStandOrError.right().isPresent()) {
					source.sendFailure(randomStandOrError.right().get());
					continue;
				}
				standForPlayer = randomStandOrError.left().orElse(null);
			}
			if (standForPlayer == null) {
				continue;
			}
			
			giveDiscToPlayer(player, StandDiscItem.withStand(new StandInstance(standForPlayer)));
			successful++;
		}
		
		if (successful > 0) {
			sendSuccess(source, targets, successful);
		}
		return successful;
	}
	
	private static void giveDiscToPlayer(ServerPlayer player, ItemStack discItem) {
		boolean added = player.getInventory().add(discItem);
		if (added && discItem.isEmpty()) {
			discItem.setCount(1);
			ItemEntity itemEntity = player.drop(discItem, false);
			if (itemEntity != null) {
				itemEntity.makeFakeItem();
			}
			player.level().playSound(null,
					player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
					((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
			player.containerMenu.broadcastChanges();
		}
		else {
			ItemEntity itemEntity = player.drop(discItem, false);
			if (itemEntity != null) {
				itemEntity.setNoPickUpDelay();
				itemEntity.setTarget(player.getUUID());
			}
		}
	}
	
	private static void sendSuccess(CommandSourceStack source, Collection<ServerPlayer> targets, int successful) {
		Component itemName = Component.translatable(ModItems.STAND_DISC.get().getDescriptionId());
		if (targets.size() == 1) {
			ServerPlayer player = targets.iterator().next();
			source.sendSuccess(() -> Component.translatable("commands.give.success.single",
					1, itemName, player.getDisplayName()), true);
		}
		else {
			source.sendSuccess(() -> Component.translatable("commands.give.success.multiple",
					1, itemName, successful), true);
		}
	}
}
