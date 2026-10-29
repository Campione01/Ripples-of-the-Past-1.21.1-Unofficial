package rotp.addons.stand_bearing_creatures.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import rotp.addons.stand_bearing_creatures.entity.goal.ManualHeldInput;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

public final class MobPowerCommands {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                event.getDispatcher();
        dispatcher.register(
                Commands.literal("rotp_mwp")
                        .requires(source -> source.hasPermission(2))
                        .then(targetCommand(
                                "random_stand",
                                MobPowerCommands::randomStand))
                        .then(targetCommand(
                                "remove_stand",
                                MobPowerCommands::removeStand))
                        .then(targetCommand(
                                "random_power",
                                MobPowerCommands::randomPower))
                        .then(targetCommand(
                                "remove_power",
                                MobPowerCommands::removePower))
                        .then(targetCommand(
                                "summon",
                                MobPowerCommands::toggleSummon))
                        .then(targetCommand(
                                "use_random",
                                MobPowerCommands::useRandomAbility))
                        .then(targetCommand(
                                "no_ai",
                                MobPowerCommands::toggleNoAi))
                        .then(targetCommand(
                                "query",
                                mob -> true))
                        .then(Commands.literal("aggro")
                                .then(Commands.argument(
                                                "source",
                                                EntityArgument.entity())
                                        .then(Commands.argument(
                                                        "target",
                                                        EntityArgument.entity())
                                                .executes(
                                                        MobPowerCommands
                                                                ::aggro)))));
    }

    private static com.mojang.brigadier.builder
            .LiteralArgumentBuilder<CommandSourceStack> targetCommand(
                    String name,
                    Predicate<Mob> action) {
        return Commands.literal(name)
                .then(Commands.argument(
                                "targets",
                                EntityArgument.entities())
                        .executes(context ->
                                runOnTargets(
                                        context,
                                        action,
                                        name)));
    }

    private static int runOnTargets(
            CommandContext<CommandSourceStack> context,
            Predicate<Mob> action,
            String operation)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        Collection<? extends Entity> entities =
                EntityArgument.getEntities(context, "targets");
        int changed = 0;
        for (Entity entity : entities) {
            if (entity instanceof Mob mob
                    && MobPowerUtil.isEligibleMob(mob)
                    && action.test(mob)) {
                changed++;
                if (operation.equals("query")) {
                    context.getSource().sendSuccess(
                            () -> describe(mob),
                            false);
                }
            }
        }
        int result = changed;
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "rotp_mwp "
                                + operation
                                + ": "
                                + result
                                + " eligible mob(s)"),
                true);
        return changed;
    }

    private static boolean randomStand(Mob mob) {
        MobPowerUtil.attachPowers(mob);
        StandType stand = MobPowerUtil.randomStand(mob);
        return MobPowerUtil.grantStand(mob, stand, true);
    }

    private static boolean removeStand(Mob mob) {
        return MobPowerUtil.removeStand(mob);
    }

    private static boolean randomPower(Mob mob) {
        MobPowerUtil.attachPowers(mob);
        PlayerPowerType<?> selected =
                MobPowerUtil.randomPlayerPower(mob);
        if (selected == null) {
            return false;
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(mob);
        power.setPowerType(selected);
        return power.getPowerType() == selected;
    }

    private static boolean removePower(Mob mob) {
        PlayerPower power = PlayerPower.get(mob);
        if (power == null || !power.hasPower()) {
            return false;
        }
        // The mob's Stand is not this power's; it stays out.
        MobPowerUtil.releaseInputsAndGrabs(mob);
        power.setPowerType(null);
        return !power.hasPower();
    }

    private static boolean toggleSummon(Mob mob) {
        StandPower power = StandPower.get(mob);
        if (power == null || !power.hasPower()) {
            return false;
        }
        // The Debug Stand's Toggle Summon (1.16 IStandPower.toggleSummon):
        // arms become the full Stand, a full Stand retracts and unsummons
        // once its current action is over instead of vanishing at once.
        boolean wasSummoned = power.isSummoned();
        power.getPowerType().onUserSummonCommand(mob, power);
        return wasSummoned || power.isSummoned();
    }

    private static boolean useRandomAbility(Mob mob) {
        Power<?> power = preferredPower(mob);
        if (power == null) {
            return false;
        }
        if (power instanceof StandPower stand) {
            MobPowerUtil.randomizeTimeStopTraining(
                    stand, mob.getRandom());
        }
        // The whole pool, as 1.16 UseRandomAction drew from: a held pick
        // stays down until the goal's release rule lets it go.
        List<Ability> abilities =
                MobPowerUtil.availableMobAbilities(power)
                        .stream()
                        .filter(ability ->
                                MobPowerUtil.grabStateMatches(
                                        mob,
                                        ability))
                        .toList();
        if (abilities.isEmpty()) {
            return false;
        }
        Ability chosen = abilities.get(
                mob.getRandom().nextInt(abilities.size()));
        // Held like the Debug Stand's Use Random Action (1.16
        // setHeldAction); a click let go at once dropped every charge.
        // The press aims where the mob looks, as 1.16 UseRandomAction did.
        return ManualHeldInput.press(chosen, mob);
    }

    private static Power<?> preferredPower(Mob mob) {
        StandPower stand = StandPower.get(mob);
        if (stand != null && stand.hasPower()) {
            if (!stand.isSummoned()) {
                stand.getPowerType().summon(mob, stand);
            }
            return stand;
        }
        PlayerPower playerPower = PlayerPower.get(mob);
        return playerPower != null && playerPower.hasPower()
                ? playerPower
                : null;
    }

    private static boolean toggleNoAi(Mob mob) {
        mob.setNoAi(!mob.isNoAi());
        return true;
    }

    private static int aggro(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions
                    .CommandSyntaxException {
        Entity source = EntityArgument.getEntity(context, "source");
        Entity target = EntityArgument.getEntity(context, "target");
        if (!(source instanceof Mob mob)
                || !(target instanceof LivingEntity living)
                || !MobPowerUtil.isEligibleMob(mob)) {
            return 0;
        }
        mob.setTarget(living);
        return 1;
    }

    private static Component describe(Mob mob) {
        StandPower stand = StandPower.get(mob);
        PlayerPower playerPower = PlayerPower.get(mob);
        String standId = stand != null && stand.hasPower()
                ? stand.getPowerType().getId().toString()
                : "none";
        String playerPowerId =
                playerPower != null && playerPower.hasPower()
                        ? playerPower.getPowerType().getId().toString()
                        : "none";
        return Component.literal(
                mob.getName().getString()
                        + " stand="
                        + standId
                        + " player_power="
                        + playerPowerId);
    }

    private MobPowerCommands() {}
}
