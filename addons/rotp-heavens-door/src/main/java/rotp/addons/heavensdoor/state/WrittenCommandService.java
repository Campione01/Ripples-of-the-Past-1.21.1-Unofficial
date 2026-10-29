package rotp.addons.heavensdoor.state;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.network.BookCommand;

public final class WrittenCommandService {
    private static final EnumSet<BookCommand> EFFECT_COMMANDS = EnumSet.of(
            BookCommand.FREEZE, BookCommand.BLINDNESS_TOGGLE, BookCommand.SELF_HARM_TOGGLE);
    private static final ThreadLocal<Projection> PROJECTION = new ThreadLocal<>();

    private WrittenCommandService() {}

    public static boolean apply(LivingEntity issuer, LivingEntity target, BookCommand command) {
        if (!WrittenCommandState.effectCommand(command) || target.level().isClientSide()
                || RestrictedCommandOwners.released(issuer) || !target.isAlive()
                || issuer.level() != target.level()) {
            return false;
        }
        tick(target);
        WrittenCommandState state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
        Holder<MobEffect> effect = effect(command);
        MobEffectInstance instance = new MobEffectInstance(effect, duration(command), 0, false, false,
                command == BookCommand.SELF_HARM_TOGGLE);
        // Untagged legacy or externally applied effects have no revocable source identity.
        if (!CommonHooks.canMobEffectBeApplied(target, instance, issuer)
                || target.hasEffect(effect) && !state.has(command) && !state.hasExternal(command)) {
            return false;
        }
        long epoch = RestrictedCommandOwners.get(target.getServer()).begin(issuer.getUUID());
        state.put(target.getUUID(), command, issuer.getUUID(), epoch, duration(command));
        project(target, state, command, issuer);
        return true;
    }

    public static boolean toggle(LivingEntity issuer, LivingEntity target, BookCommand command) {
        if (RestrictedCommandOwners.released(issuer) || !WrittenCommandState.effectCommand(command)) {
            return false;
        }
        tick(target);
        if (target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)) {
            WrittenCommandState state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            if (state.remove(command, issuer.getUUID())) {
                project(target, state, command, issuer);
                return true;
            }
        }
        return apply(issuer, target, command);
    }

    public static void tick(LivingEntity target) {
        if (target.level().isClientSide() || !target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)) {
            return;
        }
        WrittenCommandState state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
        EnumSet<BookCommand> before = EnumSet.noneOf(BookCommand.class);
        for (BookCommand command : EFFECT_COMMANDS) {
            if (state.has(command) || state.hasExternal(command)) {
                before.add(command);
            }
        }
        state.migrateLegacyDeadlines(target.level().getGameTime(), command -> survivingDuration(target, command));
        RestrictedCommandOwners owners = RestrictedCommandOwners.get(target.getServer());
        state.prune(entry -> {
            LivingEntity issuer = BookStateService.findLoadedEntity(target.getServer(), entry.issuer());
            if (issuer != null) {
                owners.observe(issuer.getUUID(), RestrictedCommandOwners.released(issuer));
            }
            return !target.isAlive() || !state.belongsTo(target.getUUID())
                    || !owners.matches(entry.issuer(), entry.epoch())
                    || entry.command() == BookCommand.SELF_HARM_TOGGLE
                            && (issuer == null || issuer.level() != target.level())
                    || entry.remainingTicks() == 0
                    || !target.hasEffect(effect(entry.command()));
        });
        for (BookCommand command : before) {
            if (!target.hasEffect(effect(command))) {
                state.clearExternal(command);
            }
            project(target, state, command, null);
        }
    }

    public static void captureExternalAdd(LivingEntity target, MobEffectInstance incoming) {
        if (target.level().isClientSide() || !target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)) {
            return;
        }
        Projection projection = PROJECTION.get();
        if (projection != null && projection.target() == target && projection.effect() == incoming) {
            return;
        }
        BookCommand command = commandFor(incoming.getEffect());
        WrittenCommandState state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
        if (command != BookCommand.INVALID && (state.has(command) || state.hasExternal(command))) {
            state.recordExternal(command, incoming);
        }
    }

    public static void onEffectTick(LivingEntity target) {
        if (!target.level().isClientSide() && target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)) {
            target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).tickDurations();
            tick(target);
        }
    }

    private static int survivingDuration(LivingEntity target, BookCommand command) {
        int remaining = 0;
        for (MobEffectInstance layer = target.getEffect(effect(command)); layer != null; layer = layer.hiddenEffect) {
            if (layer.isInfiniteDuration()) {
                return Integer.MAX_VALUE;
            }
            remaining = Math.max(remaining, layer.getDuration());
        }
        return remaining;
    }

    public static void observeOwner(LivingEntity issuer) {
        if (issuer.level().isClientSide() || issuer.getServer() == null) {
            return;
        }
        boolean released = RestrictedCommandOwners.released(issuer);
        RestrictedCommandOwners owners = RestrictedCommandOwners.get(issuer.getServer());
        if (owners.tracks(issuer.getUUID()) && owners.observe(issuer.getUUID(), released) && released) {
            releaseOwnedBy(issuer.getServer(), issuer.getUUID());
        }
    }

    public static void releaseOwnedBy(MinecraftServer server, UUID issuer) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity target && target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)) {
                    WrittenCommandState state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
                    // A target loaded from an R768 save may not have ticked yet; its other writers' deadlines must count.
                    state.migrateLegacyDeadlines(target.level().getGameTime(), command -> survivingDuration(target, command));
                    for (BookCommand command : EFFECT_COMMANDS) {
                        if (state.remove(command, issuer)) {
                            project(target, state, command, null);
                        }
                    }
                }
            }
        }
    }

    private static void project(LivingEntity target, WrittenCommandState state, BookCommand command, LivingEntity issuer) {
        Holder<MobEffect> effect = effect(command);
        int remaining = state.remaining(command);
        MobEffectInstance external = state.externalEffect(command);
        MobEffectInstance desired = remaining != 0 ? new MobEffectInstance(effect, remaining, 0, false, false,
                command == BookCommand.SELF_HARM_TOGGLE) : null;
        if (external != null) {
            if (desired == null) {
                desired = external;
            }
            else {
                desired.update(external);
            }
        }
        MobEffectInstance current = target.getEffect(effect);
        if (desired == null && current == null
                || desired != null && current != null && desired.save().equals(current.save())) {
            return;
        }
        Projection previous = PROJECTION.get();
        PROJECTION.set(new Projection(target, desired));
        try {
            target.removeEffect(effect);
            if (desired != null) {
                target.addEffect(desired, issuer);
            }
        }
        finally {
            if (previous == null) {
                PROJECTION.remove();
            }
            else {
                PROJECTION.set(previous);
            }
        }
    }

    public static int duration(BookCommand command) {
        return switch (command) {
            case FREEZE -> BookStateRules.CANNOT_MOVE_DURATION_TICKS;
            case BLINDNESS_TOGGLE -> BookStateRules.VANISH_NEARBY_DURATION_TICKS;
            case SELF_HARM_TOGGLE -> BookStateRules.UNTIMED_COMMAND_DURATION;
            default -> throw new IllegalArgumentException("Not an effect command: " + command);
        };
    }

    private static Holder<MobEffect> effect(BookCommand command) {
        return switch (command) {
            case FREEZE -> HeavensDoorAddon.CANNOT_MOVE;
            case BLINDNESS_TOGGLE -> HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT;
            case SELF_HARM_TOGGLE -> HeavensDoorAddon.KYS_EFFECT;
            default -> throw new IllegalArgumentException("Not an effect command: " + command);
        };
    }

    public static BookCommand commandFor(Holder<MobEffect> effect) {
        for (BookCommand command : EFFECT_COMMANDS) {
            if (effect.value() == effect(command).value()) {
                return command;
            }
        }
        return BookCommand.INVALID;
    }

    private record Projection(LivingEntity target, MobEffectInstance effect) {}
}
