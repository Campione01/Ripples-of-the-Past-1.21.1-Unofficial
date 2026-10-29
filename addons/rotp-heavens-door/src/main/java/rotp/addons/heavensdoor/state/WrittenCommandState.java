package rotp.addons.heavensdoor.state;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.util.INBTSerializable;
import rotp.addons.heavensdoor.network.BookCommand;

/** Independent source records; releasing one caster cannot erase another caster's remaining timer. */
public final class WrittenCommandState implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 3;
    private UUID subject;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<BookCommand, MobEffectInstance> externalEffects = new EnumMap<>(BookCommand.class);

    public boolean belongsTo(UUID uuid) {
        return uuid != null && uuid.equals(subject);
    }

    public boolean has(BookCommand command) {
        return entries.stream().anyMatch(entry -> entry.command() == command);
    }

    public boolean has(BookCommand command, UUID issuer) {
        return entries.stream().anyMatch(entry -> entry.command() == command && entry.issuer().equals(issuer));
    }

    public void put(UUID subject, BookCommand command, UUID issuer, long epoch, int remainingTicks) {
        put(subject, new Entry(command, issuer, epoch, remainingTicks, 0L));
    }

    private void put(UUID subject, Entry entry) {
        if (!belongsTo(subject)) {
            entries.clear();
            externalEffects.clear();
        }
        this.subject = subject;
        remove(entry.command(), entry.issuer());
        entries.add(entry);
    }

    public boolean remove(BookCommand command, UUID issuer) {
        return entries.removeIf(entry -> entry.command() == command && entry.issuer().equals(issuer));
    }

    public boolean prune(Predicate<Entry> invalid) {
        return entries.removeIf(invalid);
    }

    public int remaining(BookCommand command) {
        int remaining = 0;
        for (Entry entry : entries) {
            if (entry.command() == command) {
                if (entry.remainingTicks() == BookStateRules.UNTIMED_COMMAND_DURATION) {
                    return BookStateRules.UNTIMED_COMMAND_DURATION;
                }
                remaining = Math.max(remaining, entry.remainingTicks());
            }
        }
        return remaining;
    }

    public void migrateLegacyDeadlines(long now, ToIntFunction<BookCommand> survivingDuration) {
        entries.replaceAll(entry -> {
            if (entry.legacyExpiresAt() == 0L) {
                return entry;
            }
            int bound = survivingDuration.applyAsInt(entry.command());
            long left = entry.legacyExpiresAt() > now ? entry.legacyExpiresAt() - now : 0L;
            int remaining = (int) Math.clamp(left, 0L, Math.max(0, bound));
            return new Entry(entry.command(), entry.issuer(), entry.epoch(), remaining, 0L);
        });
    }

    public void tickDurations() {
        entries.replaceAll(entry -> entry.legacyExpiresAt() == 0L && entry.remainingTicks() > 0
                ? new Entry(entry.command(), entry.issuer(), entry.epoch(), entry.remainingTicks() - 1, 0L) : entry);
        tickExternalEffects();
    }

    public void clear() {
        entries.clear();
        externalEffects.clear();
        subject = null;
    }

    public boolean hasExternal(BookCommand command) {
        return externalEffects.containsKey(command);
    }

    public MobEffectInstance externalEffect(BookCommand command) {
        MobEffectInstance current = externalEffects.get(command);
        return current != null ? copyEffect(current) : null;
    }

    public void recordExternal(BookCommand command, MobEffectInstance incoming) {
        MobEffectInstance current = externalEffects.get(command);
        if (current == null) {
            externalEffects.put(command, copyEffect(incoming));
        }
        else {
            current.update(incoming);
        }
    }

    public void clearExternal(BookCommand command) {
        externalEffects.remove(command);
    }

    private void tickExternalEffects() {
        for (BookCommand command : List.copyOf(externalEffects.keySet())) {
            MobEffectInstance current = externalEffects.get(command);
            tickDetachedDurations(current);
            if (current.duration == 0 && current.hiddenEffect != null) {
                current = current.hiddenEffect;
            }
            if (current.isInfiniteDuration() || current.duration > 0) {
                externalEffects.put(command, current);
            }
            else {
                externalEffects.remove(command);
            }
        }
    }

    // Advance only the detached timer/hidden layers, never apply their gameplay tick a second time.
    private static void tickDetachedDurations(MobEffectInstance instance) {
        if (instance.hiddenEffect != null) {
            tickDetachedDurations(instance.hiddenEffect);
        }
        instance.duration = instance.mapDuration(duration -> duration - 1);
    }

    private static MobEffectInstance copyEffect(MobEffectInstance instance) {
        return MobEffectInstance.load((CompoundTag) instance.save());
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SERIALIZATION_VERSION);
        if (subject == null || entries.isEmpty() && externalEffects.isEmpty()) {
            return tag;
        }
        tag.putUUID("Subject", subject);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag item = new CompoundTag();
            item.putInt("Command", entry.command().networkId());
            item.putUUID("Issuer", entry.issuer());
            item.putLong("Epoch", entry.epoch());
            if (entry.legacyExpiresAt() > 0L) {
                item.putLong("LegacyExpiresAt", entry.legacyExpiresAt());
            }
            else {
                item.putInt("RemainingTicks", entry.remainingTicks());
            }
            list.add(item);
        }
        tag.put("Commands", list);
        ListTag external = new ListTag();
        externalEffects.forEach((command, effect) -> {
            CompoundTag item = new CompoundTag();
            item.putInt("Command", command.networkId());
            item.put("Effect", effect.save());
            external.add(item);
        });
        tag.put("ExternalEffects", external);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt("Version") < 1 || tag.getInt("Version") > SERIALIZATION_VERSION || !tag.hasUUID("Subject")) {
            return;
        }
        subject = tag.getUUID("Subject");
        ListTag list = tag.getList("Commands", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            BookCommand command = BookCommand.fromNetworkId(item.getInt("Command")).orElse(BookCommand.INVALID);
            boolean legacy = tag.getInt("Version") < 3 || item.contains("LegacyExpiresAt", Tag.TAG_LONG);
            long end = legacy ? item.getLong(tag.getInt("Version") < 3 ? "ExpiresAt" : "LegacyExpiresAt") : 0L;
            int remaining = legacy ? 0 : item.getInt("RemainingTicks");
            if (legacy && command == BookCommand.SELF_HARM_TOGGLE && end == BookStateRules.UNTIMED_COMMAND_DURATION) {
                remaining = BookStateRules.UNTIMED_COMMAND_DURATION;
                end = 0L;
            }
            if (effectCommand(command) && item.hasUUID("Issuer") && item.getLong("Epoch") >= 0L
                    && (end > 0L || remaining > 0 || command == BookCommand.SELF_HARM_TOGGLE
                            && remaining == BookStateRules.UNTIMED_COMMAND_DURATION)) {
                put(subject, new Entry(command, item.getUUID("Issuer"), item.getLong("Epoch"), remaining, end));
            }
        }
        ListTag external = tag.getList("ExternalEffects", Tag.TAG_COMPOUND);
        for (int i = 0; i < external.size(); i++) {
            CompoundTag item = external.getCompound(i);
            BookCommand command = BookCommand.fromNetworkId(item.getInt("Command")).orElse(BookCommand.INVALID);
            MobEffectInstance effect = MobEffectInstance.load(item.getCompound("Effect"));
            if (effect != null && effectCommand(command) && WrittenCommandService.commandFor(effect.getEffect()) == command
                    && (effect.isInfiniteDuration() || effect.getDuration() > 0)) {
                externalEffects.put(command, effect);
            }
        }
    }

    public static boolean effectCommand(BookCommand command) {
        return command == BookCommand.FREEZE || command == BookCommand.BLINDNESS_TOGGLE
                || command == BookCommand.SELF_HARM_TOGGLE;
    }

    public record Entry(BookCommand command, UUID issuer, long epoch, int remainingTicks, long legacyExpiresAt) {}
}
