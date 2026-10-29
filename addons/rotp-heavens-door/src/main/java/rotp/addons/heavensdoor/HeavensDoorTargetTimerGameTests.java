package rotp.addons.heavensdoor;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.RestrictedCommandOwners;
import rotp.addons.heavensdoor.state.WrittenCommandService;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.powersystem.PowerClass;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorTargetTimerGameTests {
    private static int nextStopId = -7_690_300;
    private static final PowerSkillUnlocks.Owner TIMER_UNLOCKS =
            PowerSkillUnlocks.register(HeavensDoorAddon.id("target_timer_time_stop_test"));

    private HeavensDoorTargetTimerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 280, batch = "hd_timer_cancel")
    public static void cancelledTargetTicksDoNotConsumeCommandTime(GameTestHelper helper) {
        Cow writer = cow(helper, 1, 12.5D);
        Cow target = cow(helper, 0, 4.5D);
        Consumer<EntityTickEvent.Pre> cancel = event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        };
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(cancel);
            writer.discard();
            target.discard();
        };
        try {
            applyTimed(helper, writer, target);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
            later(helper, 20, cleanup, () -> {
                int held = duration(target, BookCommand.FREEZE);
                int vanished = duration(target, BookCommand.BLINDNESS_TOGGLE);
                int vanilla = target.getEffect(MobEffects.GLOWING).getDuration();
                helper.assertTrue(held >= 99 && held <= 101, "Fixture did not reach about 100 held ticks");
                long now = helper.getLevel().getGameTime();
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTickEvent.Pre.class, cancel);
                later(helper, 200, cleanup, () -> {
                    helper.assertTrue(helper.getLevel().getGameTime() >= now + 200
                                    && target.getEffect(MobEffects.GLOWING).getDuration() == vanilla,
                            "Fixture did not pause the target's actual effect ticks for 200 world ticks");
                    for (int refresh = 0; refresh < 5; refresh++) {
                        WrittenCommandService.tick(target);
                    }
                    helper.assertTrue(duration(target, BookCommand.FREEZE) == held
                                    && duration(target, BookCommand.BLINDNESS_TOGGLE) == vanished,
                            "Cancelled target ticks or repeated reconciliation consumed the command's remaining time");
                    NeoForge.EVENT_BUS.unregister(cancel);
                    later(helper, 5, cleanup, () -> {
                        int spent = vanilla - target.getEffect(MobEffects.GLOWING).getDuration();
                        helper.assertTrue(spent > 0 && duration(target, BookCommand.FREEZE) == held - spent
                                        && duration(target, BookCommand.BLINDNESS_TOGGLE) == vanished - spent,
                                "Resumed command timers did not follow the target's actual vanilla effect clock");
                        cleanup.run();
                        helper.succeed();
                    });
                });
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 280, batch = "hd_timer_unload")
    public static void savedUnloadedTargetKeepsItsRemainingCommandTime(GameTestHelper helper) {
        Cow writer = cow(helper, 1, 12.5D);
        Cow[] target = {cow(helper, 0, 4.5D)};
        Runnable cleanup = () -> {
            writer.discard();
            target[0].discard();
        };
        try {
            applyTimed(helper, writer, target[0]);
            later(helper, 20, cleanup, () -> {
                int held = duration(target[0], BookCommand.FREEZE);
                int vanished = duration(target[0], BookCommand.BLINDNESS_TOGGLE);
                helper.assertTrue(held >= 99 && held <= 101, "Fixture did not reach about 100 held ticks");
                CompoundTag saved = new CompoundTag();
                helper.assertTrue(target[0].save(saved), "Timed target could not save");
                target[0].setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                long now = helper.getLevel().getGameTime();
                later(helper, 200, cleanup, () -> {
                    helper.assertTrue(helper.getLevel().getGameTime() >= now + 200
                                    && helper.getLevel().getEntity(target[0].getUUID()) == null, "Target did not remain unloaded for 200 world ticks");
                    Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
                    helper.assertTrue(loaded instanceof Cow && helper.getLevel().addFreshEntity(loaded), "Timed target could not reload");
                    target[0] = (Cow) loaded;
                    WrittenCommandService.tick(target[0]);
                    helper.assertTrue(duration(target[0], BookCommand.FREEZE) == held
                                    && duration(target[0], BookCommand.BLINDNESS_TOGGLE) == vanished,
                            "A saved unloaded target lost remaining command time to the world's clock");
                    cleanup.run();
                    helper.succeed();
                });
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 280, batch = "hd_timer_time_stop")
    public static void timeStopPausesMobPassengerAndConnectedPlayerTimers(GameTestHelper helper) {
        Cow writer = cow(helper, 1, 12.5D);
        Cow mob = cow(helper, 0, 4.5D);
        Cow passenger = cow(helper, 0, 6.5D);
        Minecart vehicle = helper.spawn(EntityType.MINECART, helper.relativeVec(spot(helper, 0, 6.5D)));
        ServerPlayer player = HeavensDoorOwnerRulesGameTests.owner(helper, "HdClockPlayer");
        player.setPos(spot(helper, 1, 9.5D));
        TimeStopState stop = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
        int id = nextStopId--;
        List<LivingEntity> subjects = List.of(mob, passenger, player);
        boolean[] driveConnection = {true};
        Runnable cleanup = () -> {
            driveConnection[0] = false;
            stop.removeInstance(id);
            passenger.stopRiding();
            List.of(writer, mob, passenger, vehicle).forEach(Entity::discard);
            HeavensDoorOwnerRulesGameTests.close(player);
        };
        try {
            var power = PowerClass.STAND.attachGet(writer);
            power.setStand(ModStands.THE_WORLD.get());
            TIMER_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
            power.setResolveLevel(power.getMaxResolveLevel());
            writer.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 500));
            helper.assertTrue(TimeStopState.getDefaultTimeStopAbility(power) != null
                            && TimeStopState.getDefaultTimeStopAbility(power).isAbilityUnlocked(power),
                    "Time stop fixture lacks its actual living, unlocked The World writer");
            helper.assertTrue(passenger.startRiding(vehicle, true), "Passenger fixture did not mount");
            for (LivingEntity subject : subjects) {
                applyTimed(helper, writer, subject);
                subject.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
            }
            // Embedded connections are not in the socket listener's list; run the real listener/doTick path.
            helper.onEachTick(() -> {
                if (driveConnection[0]) {
                    player.connection.tick();
                }
            });
            later(helper, 20, cleanup, () -> {
                int[] held = subjects.stream().mapToInt(subject -> duration(subject, BookCommand.FREEZE)).toArray();
                int[] vanished = subjects.stream().mapToInt(subject -> duration(subject, BookCommand.BLINDNESS_TOGGLE)).toArray();
                int[] vanilla = subjects.stream().mapToInt(subject -> subject.getEffect(MobEffects.GLOWING).getDuration()).toArray();
                helper.assertTrue(held[0] >= 99 && held[0] <= 101 && held[1] >= 99 && held[1] <= 101 && held[2] >= 99 && held[2] <= 101,
                        "Mob/passenger/connected-player fixture did not tick about 20 times: " + java.util.Arrays.toString(held));
                helper.assertTrue(stop.tryPutInstance(new TimeStopState.Instance(id, 400, 400,
                                new ChunkPos(mob.blockPosition()), 1, writer.getId(), "heavens_door_timer_test")), "Actual core time stop was refused");
                helper.assertTrue(subjects.stream().allMatch(TimeStopState::shouldFreezeOnServer), "Core time stop did not freeze every target");
                long now = helper.getLevel().getGameTime();
                later(helper, 200, cleanup, () -> {
                    helper.assertTrue(helper.getLevel().getGameTime() >= now + 200, "World did not advance 200 ticks in time stop");
                    for (int index = 0; index < subjects.size(); index++) {
                        LivingEntity subject = subjects.get(index);
                        helper.assertTrue(subject.getEffect(MobEffects.GLOWING).getDuration() == vanilla[index],
                                "Core cancellation let potion clock advance: subject=" + index + " expected=" + vanilla[index]
                                        + " actual=" + subject.getEffect(MobEffects.GLOWING).getDuration()
                                        + " frozen=" + TimeStopState.shouldFreezeOnServer(subject) + " position=" + subject.position()
                                        + " ticksLeft=" + stop.getTimeStopTicks(new ChunkPos(subject.blockPosition())));
                        WrittenCommandService.tick(subject);
                        helper.assertTrue(duration(subject, BookCommand.FREEZE) == held[index]
                                        && duration(subject, BookCommand.BLINDNESS_TOGGLE) == vanished[index],
                                "Time stop consumed a mob/passenger/connected-player written timer");
                    }
                    cleanup.run();
                    helper.succeed();
                });
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "hd_timer_passenger")
    public static void passengerTimersAdvanceOncePerActualEffectTick(GameTestHelper helper) {
        Cow writer = cow(helper, 1, 12.5D);
        Cow target = cow(helper, 0, 4.5D);
        Minecart vehicle = helper.spawn(EntityType.MINECART, helper.relativeVec(spot(helper, 0, 4.5D)));
        Runnable cleanup = () -> List.of(writer, target, vehicle).forEach(Entity::discard);
        try {
            helper.assertTrue(target.startRiding(vehicle, true), "Passenger fixture did not mount");
            applyTimed(helper, writer, target);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 300));
            later(helper, 12, cleanup, () -> {
                int spent = 1000 - target.getEffect(MobEffects.GLOWING).getDuration();
                helper.assertTrue(spent >= 11 && spent <= 13 && target.isPassenger(), "Passenger did not keep ticking on its mount");
                writer.setHealth(writer.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
                WrittenCommandService.observeOwner(writer);
                helper.assertTrue(duration(target, BookCommand.FREEZE) == 300 - spent
                                && !target.hasEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT),
                        "Passenger external timer was double-counted, or issuer release kept its Vanish contribution");
                cleanup.run();
                helper.succeed();
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void playerBookkeepingDoesNotAgeButDoTickAgesEveryTimer(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        ServerPlayer target = HeavensDoorOwnerRulesGameTests.owner(helper, "HdActualClock");
        try {
            applyTimed(helper, writer, target);
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 300));
            for (int tick = 0; tick < 50; tick++) {
                target.tick();
                WrittenCommandService.tick(target);
            }
            helper.assertTrue(duration(target, BookCommand.FREEZE) == 300 && duration(target, BookCommand.BLINDNESS_TOGGLE) == 600,
                    "ServerPlayer bookkeeping or a reconciliation call aged a potion-bearing command timer");
            for (int tick = 0; tick < 5; tick++) {
                target.doTick();
            }
            helper.assertTrue(duration(target, BookCommand.FREEZE) == 295 && duration(target, BookCommand.BLINDNESS_TOGGLE) == 595,
                    "Actual ServerPlayer.doTick did not age owned and external timers exactly once");
            writer.setHealth(writer.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            WrittenCommandService.observeOwner(writer);
            helper.assertTrue(duration(target, BookCommand.FREEZE) == 295, "Issuer release changed the external player's true remaining timer");
            helper.succeed();
        }
        finally {
            writer.discard();
            HeavensDoorOwnerRulesGameTests.close(target);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 90, batch = "hd_timer_sources")
    public static void staggeredSourcesAndExternalHiddenLayersKeepTheirOwnClocks(GameTestHelper helper) {
        Cow first = cow(helper, 1, 11.5D);
        Cow second = cow(helper, 1, 12.5D);
        Cow target = cow(helper, 0, 4.5D);
        Runnable cleanup = () -> List.of(first, second, target).forEach(Entity::discard);
        try {
            applyTimed(helper, first, target);
            later(helper, 20, cleanup, () -> {
                applyTimed(helper, second, target);
                target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 20, 2, true, true, true));
                target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 160, 0, true, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000));
                later(helper, 10, cleanup, () -> {
                    int spent = 1000 - target.getEffect(MobEffects.GLOWING).getDuration();
                    first.setHealth(first.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
                    WrittenCommandService.observeOwner(first);
                    helper.assertTrue(duration(target, BookCommand.BLINDNESS_TOGGLE) == 600 - spent,
                            "Releasing the older caster shortened the later caster's independent TTL");
                    second.setHealth(second.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
                    WrittenCommandService.observeOwner(second);
                    MobEffectInstance external = target.getEffect(HeavensDoorAddon.CANNOT_MOVE);
                    helper.assertTrue(external != null && external.getAmplifier() == 2 && external.getDuration() == 20 - spent
                                    && external.hiddenEffect != null && external.hiddenEffect.getDuration() == 160 - spent
                                    && !target.hasEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT),
                            "Releasing both casters lost or refreshed the normal external effect's active/hidden layers");
                    later(helper, 15, cleanup, () -> {
                        int totalSpent = 1000 - target.getEffect(MobEffects.GLOWING).getDuration();
                        MobEffectInstance promoted = target.getEffect(HeavensDoorAddon.CANNOT_MOVE);
                        helper.assertTrue(promoted != null && promoted.getAmplifier() == 0 && promoted.getDuration() == 160 - totalSpent,
                                "An external hidden layer did not promote on its real target clock");
                        cleanup.run();
                        helper.succeed();
                    });
                });
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyExpiredAndAbsentTimersStayGoneWhileUntimedStaysUntimed(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            long now = helper.getLevel().getGameTime();
            CompoundTag legacy = legacyState(writer, target, now - 1, now + 70, BookStateRules.UNTIMED_COMMAND_DURATION);
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 100));
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, -1, 0, false, false, true));
            var state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            state.deserializeNBT(target.registryAccess(), legacy);
            CompoundTag pending = state.serializeNBT(target.registryAccess());
            state.deserializeNBT(target.registryAccess(), pending);
            WrittenCommandService.tick(target);
            helper.assertTrue(!state.has(BookCommand.FREEZE) && !state.has(BookCommand.BLINDNESS_TOGGLE)
                            && !target.hasEffect(HeavensDoorAddon.CANNOT_MOVE) && state.has(BookCommand.SELF_HARM_TOGGLE)
                            && target.getEffect(HeavensDoorAddon.KYS_EFFECT).isInfiniteDuration(),
                    "Legacy migration resurrected an expired/absent command or timed an untimed Self Harm");
            helper.succeed();
        }
        finally {
            writer.discard();
            target.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyOverlapUsesSurvivingHiddenLayersAndInfiniteExternalStaysExternal(GameTestHelper helper) {
        Cow first = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        Cow second = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        Cow hidden = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        Cow infinite = helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 2);
        try {
            long now = helper.getLevel().getGameTime();
            CompoundTag legacy = legacyState(first, hidden, now + 50, 0, 0);
            legacy.getList("Commands", Tag.TAG_COMPOUND).add(legacyEntry(second, BookCommand.FREEZE, now + 150));
            MobEffectInstance external = new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 20, 2, true, true, true,
                    new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 80, 0, true, true, true));
            hidden.addEffect(external);
            addExternal(legacy, BookCommand.FREEZE, external);
            var hiddenState = hidden.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            hiddenState.deserializeNBT(hidden.registryAccess(), legacy);
            CompoundTag pending = hiddenState.serializeNBT(hidden.registryAccess());
            helper.assertTrue(pending.getList("Commands", Tag.TAG_COMPOUND).getCompound(1).getLong("LegacyExpiresAt") == now + 150,
                    "Pending legacy deadline was changed before effects were reconciled");
            hiddenState.deserializeNBT(hidden.registryAccess(), pending);
            WrittenCommandService.tick(hidden);
            CompoundTag converted = hiddenState.serializeNBT(hidden.registryAccess());
            helper.assertTrue(converted.getList("Commands", Tag.TAG_COMPOUND).getCompound(0).getInt("RemainingTicks") == 50
                            && converted.getList("Commands", Tag.TAG_COMPOUND).getCompound(1).getInt("RemainingTicks") == 80,
                    "Legacy overlap invented full durations or ignored the surviving hidden layer bound");
            CompoundTag infiniteLegacy = legacyState(first, infinite, now + 70, 0, 0);
            MobEffectInstance unbounded = new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, -1, 1, true, true, true);
            infinite.addEffect(unbounded);
            addExternal(infiniteLegacy, BookCommand.FREEZE, unbounded);
            var infiniteState = infinite.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            infiniteState.deserializeNBT(infinite.registryAccess(), infiniteLegacy);
            WrittenCommandService.tick(infinite);
            helper.assertTrue(infiniteState.serializeNBT(infinite.registryAccess()).getList("Commands", Tag.TAG_COMPOUND)
                            .getCompound(0).getInt("RemainingTicks") == 70,
                    "An infinite external layer turned a finite legacy owned deadline into an infinite/reset command");
            first.setHealth(first.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            second.setHealth(second.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            WrittenCommandService.observeOwner(first);
            WrittenCommandService.observeOwner(second);
            helper.assertTrue(hidden.getEffect(HeavensDoorAddon.CANNOT_MOVE).save().equals(external.save())
                            && infinite.getEffect(HeavensDoorAddon.CANNOT_MOVE).isInfiniteDuration(),
                    "Legacy source release changed the external active/hidden/infinite effects");
            helper.succeed();
        }
        finally {
            List.of(first, second, hidden, infinite).forEach(Entity::discard);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void releaseBeforeTheFirstTickKeepsTheOtherWritersLegacyCommands(GameTestHelper helper) {
        Cow first = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        Cow second = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            long now = helper.getLevel().getGameTime();
            CompoundTag legacy = legacyState(first, target, now + 100, now + 500, 0);
            legacy.getList("Commands", Tag.TAG_COMPOUND).add(legacyEntry(second, BookCommand.FREEZE, now + 60));
            legacy.getList("Commands", Tag.TAG_COMPOUND).add(legacyEntry(second, BookCommand.BLINDNESS_TOGGLE, now + 300));
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 100, 0, false, false, false));
            target.addEffect(new MobEffectInstance(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT, 500, 0, false, false, false));
            var state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            state.deserializeNBT(target.registryAccess(), legacy);
            first.setHealth(first.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            WrittenCommandService.observeOwner(first);
            helper.assertTrue(duration(target, BookCommand.FREEZE) == 60 && duration(target, BookCommand.BLINDNESS_TOGGLE) == 300,
                    "Releasing one writer before the target's first tick ended the other writer's legacy commands");
            WrittenCommandService.tick(target);
            helper.assertTrue(state.has(BookCommand.FREEZE, second.getUUID()) && state.has(BookCommand.BLINDNESS_TOGGLE, second.getUUID())
                            && !state.has(BookCommand.FREEZE, first.getUUID()) && !state.has(BookCommand.BLINDNESS_TOGGLE, first.getUUID())
                            && duration(target, BookCommand.FREEZE) == 60 && duration(target, BookCommand.BLINDNESS_TOGGLE) == 300,
                    "The surviving writer's migrated legacy commands were dropped on the target's next tick");
            helper.succeed();
        }
        finally {
            List.of(first, second, target).forEach(Entity::discard);
        }
    }

    private static CompoundTag legacyState(LivingEntity writer, LivingEntity target, long freeze, long vanish, long selfHarm) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putUUID("Subject", target.getUUID());
        ListTag commands = new ListTag();
        if (freeze != 0) commands.add(legacyEntry(writer, BookCommand.FREEZE, freeze));
        if (vanish != 0) commands.add(legacyEntry(writer, BookCommand.BLINDNESS_TOGGLE, vanish));
        if (selfHarm != 0) commands.add(legacyEntry(writer, BookCommand.SELF_HARM_TOGGLE, selfHarm));
        tag.put("Commands", commands);
        return tag;
    }

    private static CompoundTag legacyEntry(LivingEntity writer, BookCommand command, long deadline) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Command", command.networkId());
        entry.putUUID("Issuer", writer.getUUID());
        entry.putLong("Epoch", RestrictedCommandOwners.get(writer.getServer()).begin(writer.getUUID()));
        entry.putLong("ExpiresAt", deadline);
        return entry;
    }

    private static void addExternal(CompoundTag state, BookCommand command, MobEffectInstance effect) {
        CompoundTag item = new CompoundTag();
        item.putInt("Command", command.networkId());
        item.put("Effect", effect.save());
        ListTag external = new ListTag();
        external.add(item);
        state.put("ExternalEffects", external);
    }

    private static void applyTimed(GameTestHelper helper, LivingEntity writer, LivingEntity target) {
        helper.assertTrue(WrittenCommandService.apply(writer, target, BookCommand.FREEZE)
                        && WrittenCommandService.apply(writer, target, BookCommand.BLINDNESS_TOGGLE), "Timed command fixture failed");
    }

    private static int duration(LivingEntity target, BookCommand command) {
        var effect = target.getEffect(command == BookCommand.FREEZE ? HeavensDoorAddon.CANNOT_MOVE : HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT);
        return effect != null ? effect.getDuration() : 0;
    }

    private static Cow cow(GameTestHelper helper, int row, double x) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, row, x)));
        cow.setNoGravity(true);
        return cow;
    }

    private static Vec3 spot(GameTestHelper helper, int row, double x) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        int z = (origin.getZ() & 15) % 7 + (row == 0 ? 2 : 5);
        return new Vec3(chunk.getMinBlockX() + x, origin.getY(), chunk.getMinBlockZ() + z + 0.5D);
    }

    private static void later(GameTestHelper helper, long delay, Runnable cleanup, Runnable task) {
        helper.runAfterDelay(delay, () -> {
            try {
                task.run();
            }
            catch (Throwable failure) {
                cleanup.run();
                throw failure;
            }
        });
    }
}
