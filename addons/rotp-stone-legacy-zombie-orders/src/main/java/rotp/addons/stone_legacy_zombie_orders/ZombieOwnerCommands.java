package rotp.addons.stone_legacy_zombie_orders;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ZombieOwnerCommands {
    private static final SimpleCommandExceptionType NO_TARGETS =
            new SimpleCommandExceptionType(
                    Component.translatable(
                            "commands.jojozombie.entity.failed"));

    private ZombieOwnerCommands() {}

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("jojozombie")
                        .requires(source ->
                                source.hasPermission(2))
                        .then(Commands.argument(
                                        "targets",
                                        EntityArgument.entities())
                                .then(Commands.literal("setowner")
                                        .then(Commands.argument(
                                                        "owner",
                                                        EntityArgument
                                                                .player())
                                                .executes(
                                                        ZombieOwnerCommands
                                                                ::setOwner)))));
    }

    private static int setOwner(
            CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets =
                EntityArgument.getEntities(context, "targets");
        ServerPlayer owner =
                EntityArgument.getPlayer(context, "owner");
        int changed = 0;
        Entity single = null;
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living
                    && ZombieOwnership.setOwner(
                            living, owner)) {
                changed++;
                single = entity;
            }
        }
        if (changed == 0) {
            throw NO_TARGETS.create();
        }
        int result = changed;
        Entity singleTarget = single;
        context.getSource().sendSuccess(
                () -> result == 1
                        ? Component.translatable(
                                "commands.jojozombie.setowner.success.single",
                                owner.getDisplayName(),
                                singleTarget.getDisplayName())
                        : Component.translatable(
                                "commands.jojozombie.setowner.success.multiple",
                                owner.getDisplayName(),
                                result),
                true);
        return changed;
    }
}
