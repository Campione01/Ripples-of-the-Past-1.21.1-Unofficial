package com.github.standobyte.jojo.command.commands;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.vampirism.BloodEconomy;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismState;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class JojoEnergyCommand {
	private static final SimpleCommandExceptionType ERROR_SET_RATIO_INVALID = new SimpleCommandExceptionType(
			Component.translatable("commands.jojoenergy.set.ratio.invalid"));
	private static final DynamicCommandExceptionType SET_SINGLE_EXCEPTION_NOT_LIVING = new DynamicCommandExceptionType(
			entity -> Component.translatable("commands.jojoenergy.set.failed.single.not_living", entity));
	private static final DynamicCommandExceptionType SET_MULTIPLE_EXCEPTION_NOT_LIVING = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.jojoenergy.set.failed.multiple.not_living", count));
	private static final DynamicCommandExceptionType SET_SINGLE_EXCEPTION_NO_POWER = new DynamicCommandExceptionType(
			entity -> Component.translatable("commands.jojoenergy.set.failed.single.no_power", entity));
	private static final DynamicCommandExceptionType SET_MULTIPLE_EXCEPTION_NO_POWER = new DynamicCommandExceptionType(
			count -> Component.translatable("commands.jojoenergy.set.failed.multiple.no_power", count));
	private static final DynamicCommandExceptionType GET_SINGLE_EXCEPTION_NOT_LIVING = new DynamicCommandExceptionType(
			entity -> Component.translatable("commands.jojoenergy.get.failed.not_living", entity));
	private static final DynamicCommandExceptionType GET_SINGLE_EXCEPTION_NO_POWER = new DynamicCommandExceptionType(
			entity -> Component.translatable("commands.jojoenergy.get.failed.no_power", entity));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(energyCommand("jojoenergy", EnergyKind.ANY));
		dispatcher.register(Commands.literal(JojoMod.MOD_ID)
				.then(energyCommand("power_energy", EnergyKind.ANY))
				.then(powerEnergyCommand("power_hamon", EnergyKind.HAMON))
				.then(powerEnergyCommand("power_pillarman", EnergyKind.PILLAR_MAN))
				.then(powerEnergyCommand("power_vampire", EnergyKind.VAMPIRISM))
				.then(powerEnergyCommand("power_zombie", EnergyKind.ZOMBIE)));
		JojoCommandsCommand.addCommand("jojoenergy");
		JojoCommandsCommand.addCommand("power_energy");
		JojoCommandsCommand.addCommand("power_pillarman");
		JojoCommandsCommand.addCommand("power_vampire");
		JojoCommandsCommand.addCommand("power_zombie");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> powerEnergyCommand(String literal, EnergyKind kind) {
		return Commands.literal(literal).then(energyCommand("energy", kind));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> energyCommand(String literal, EnergyKind kind) {
		return Commands.literal(literal).requires(ctx -> ctx.hasPermission(2))
				.then(Commands.literal("set")
						.then(Commands.argument("targets", EntityArgument.entities())
								.then(Commands.argument("amount", FloatArgumentType.floatArg(0))
										.executes(ctx -> setEnergy(ctx.getSource(),
												EntityArgument.getEntities(ctx, "targets"),
												FloatArgumentType.getFloat(ctx, "amount"),
												NumType.VALUE,
												kind))
										.then(Commands.literal("points")
												.executes(ctx -> setEnergy(ctx.getSource(),
														EntityArgument.getEntities(ctx, "targets"),
														FloatArgumentType.getFloat(ctx, "amount"),
														NumType.VALUE,
														kind)))
										.then(Commands.literal("ratio")
												.executes(ctx -> setEnergy(ctx.getSource(),
														EntityArgument.getEntities(ctx, "targets"),
														FloatArgumentType.getFloat(ctx, "amount"),
														NumType.RATIO,
														kind))))))
				.then(Commands.literal("get")
						.then(Commands.argument("target", EntityArgument.entity())
								.executes(ctx -> getEnergy(ctx.getSource(),
										EntityArgument.getEntity(ctx, "target"),
										kind))));
	}

	private static int setEnergy(CommandSourceStack source, Collection<? extends Entity> targets,
			float value, NumType numType, EnergyKind kind) throws CommandSyntaxException {
		if (numType == NumType.RATIO && value > 1.0F) {
			throw ERROR_SET_RATIO_INVALID.create();
		}

		int success = 0;
		boolean hasLiving = false;
		for (Entity entity : targets) {
			if (entity instanceof LivingEntity living) {
				hasLiving = true;
				Optional<EnergyAccess> access = energyAccess(living, kind);
				if (access.isPresent()) {
					EnergyAccess energy = access.get();
					float amount = numType == NumType.RATIO ? value * energy.max() : value;
					energy.set(amount);
					success++;
				}
				else if (targets.size() == 1) {
					throw SET_SINGLE_EXCEPTION_NO_POWER.create(entity.getDisplayName());
				}
			}
		}

		if (success == 0) {
			if (targets.size() == 1) {
				Entity target = targets.iterator().next();
				if (!hasLiving) {
					throw SET_SINGLE_EXCEPTION_NOT_LIVING.create(target.getDisplayName());
				}
				throw SET_SINGLE_EXCEPTION_NO_POWER.create(target.getDisplayName());
			}
			if (!hasLiving) {
				throw SET_MULTIPLE_EXCEPTION_NOT_LIVING.create(targets.size());
			}
			throw SET_MULTIPLE_EXCEPTION_NO_POWER.create(targets.size());
		}

		String key = "commands.jojoenergy.set.success."
				+ (targets.size() == 1 ? "single." : "multiple.")
				+ numType.name().toLowerCase(Locale.ROOT);
		if (targets.size() == 1) {
			Entity target = targets.iterator().next();
			source.sendSuccess(() -> Component.translatable(key, value, target.getDisplayName()), true);
		}
		else {
			int successCount = success;
			source.sendSuccess(() -> Component.translatable(key, value, successCount), true);
		}
		return success;
	}

	private static int getEnergy(CommandSourceStack source, Entity target, EnergyKind kind) throws CommandSyntaxException {
		if (!(target instanceof LivingEntity living)) {
			throw GET_SINGLE_EXCEPTION_NOT_LIVING.create(target.getDisplayName());
		}
		EnergyAccess energy = energyAccess(living, kind)
				.orElseThrow(() -> GET_SINGLE_EXCEPTION_NO_POWER.create(target.getDisplayName()));
		float current = energy.current();
		float max = energy.max();
		String ratio = String.format(Locale.ROOT, "%.4f", max > 0.0F ? current / max : 0.0F);
		source.sendSuccess(() -> Component.translatable("commands.jojoenergy.get.success",
				target.getDisplayName(), current, max, ratio), false);
		return (int) current;
	}

	private static Optional<EnergyAccess> energyAccess(LivingEntity user, EnergyKind kind) {
		PlayerPower power = PlayerPower.get(user);
		if (power == null || !power.hasPower()) {
			return Optional.empty();
		}
		if (kind.matches(EnergyKind.HAMON)) {
			Optional<HamonData> hamon = power.getCurTypeData(ModPlayerPowers.HAMON);
			if (hamon.isPresent()) {
				HamonData data = hamon.get();
				return Optional.of(new EnergyAccess(data.getEnergy(), data.getMaxEnergy(), amount -> {
					data.setEnergy(amount);
					data.syncOnUpdate(user);
				}));
			}
		}
		if (kind.matches(EnergyKind.PILLAR_MAN)) {
			Optional<PillarmanData> pillarman = power.getCurTypeData(ModPlayerPowers.PILLAR_MAN);
			if (pillarman.isPresent()) {
				PillarmanData data = pillarman.get();
				return Optional.of(new EnergyAccess(data.getEnergy(), data.getMaxEnergy(user), amount -> {
					data.setEnergy(user, amount);
					data.syncOnUpdate(user);
				}));
			}
		}
		if (kind.matches(EnergyKind.VAMPIRISM)) {
			Optional<VampirismData> vampirism = power.getCurTypeData(ModPlayerPowers.VAMPIRISM);
			if (vampirism.isPresent()) {
				VampirismData data = vampirism.get();
				BloodEconomy blood = VampirismState.get(user).blood();
				return Optional.of(new EnergyAccess(blood.current(), blood.max(), amount -> {
					blood.setCurrent(amount);
					data.setBloodLevel(blood.current());
					data.syncOnUpdate(user);
				}));
			}
		}
		if (kind.matches(EnergyKind.ZOMBIE)) {
			Optional<ZombieData> zombie = power.getCurTypeData(ModPlayerPowers.ZOMBIE);
			if (zombie.isPresent()) {
				ZombieData data = zombie.get();
				return Optional.of(new EnergyAccess(data.getEnergy(), data.getMaxEnergy(user), amount -> {
					data.setEnergy(user, amount);
					data.syncOnUpdate(user);
				}));
			}
		}
		return Optional.empty();
	}

	private enum NumType {
		RATIO,
		VALUE
	}

	private enum EnergyKind {
		ANY,
		HAMON,
		PILLAR_MAN,
		VAMPIRISM,
		ZOMBIE;

		private boolean matches(EnergyKind type) {
			return this == ANY || this == type;
		}
	}

	private record EnergyAccess(float current, float max, Consumer<Float> setter) {
		private void set(float value) {
			setter.accept(value);
		}
	}
}
