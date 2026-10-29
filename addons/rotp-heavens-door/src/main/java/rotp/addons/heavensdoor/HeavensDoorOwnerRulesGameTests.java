package rotp.addons.heavensdoor;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.state.BookCommandLeaseService;
import rotp.addons.heavensdoor.state.BookCommandSessionService;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.addons.heavensdoor.state.CannotMoveRules;
import rotp.addons.heavensdoor.state.WrittenCommandService;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorOwnerRulesGameTests {
    private HeavensDoorOwnerRulesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void resolveWeakensTheActualBookOnceIncludingReload(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "HdResolve");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            owner.setPos(target.position().add(-2, 0, 0));
            helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(owner), target), "Book mark failed");
            BookState state = HeavensDoorAddon.bookState(target);
            long originalEnd = state.expiresAt();
            helper.assertTrue(state.bookAmplifier() == 2
                            && originalEnd - target.level().getGameTime() == BookStateRules.MAX_UNPOWERED_DURATION_TICKS,
                    "Max Resolve unpowered production book did not use final row");
            var session = BookCommandSessionService.issue(owner, target);
            helper.assertTrue(session != null, "Command session not issued");
            target.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            long now = target.level().getGameTime();
            BookStateService.tick(target);
            long weakenedEnd = now + (originalEnd - now) / 2;
            helper.assertTrue(state.expiresAt() == weakenedEnd && state.bookAmplifier() == 0
                            && target.getEffect(HeavensDoorAddon.BOOK_EFFECT).getAmplifier() == 0,
                    "Gaining Resolve did not weaken book state and actual effect");
            state.deserializeNBT(target.registryAccess(), state.serializeNBT(target.registryAccess()));
            BookStateService.tick(target);
            helper.assertTrue(state.expiresAt() == weakenedEnd, "Reload repeated the counter or extended book");
            target.removeEffect(ModStatusEffects.RESOLVE);
            target.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            BookStateService.tick(target);
            helper.assertTrue(state.expiresAt() == weakenedEnd, "Resolve refresh weakened the same cast twice");
            helper.assertTrue(BookCommandSessionService.consume(owner, session.nonce(), BookCommand.FREEZE, -1),
                    "Retimed book session lost its valid nonce binding");
            helper.assertTrue(target.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration() == 120,
                    "Registered Cannot Move did not last 120 ticks");
            helper.succeed();
        }
        finally {
            target.discard();
            close(owner);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void casterSpecificReleaseSurvivesTargetUnloadAndPreservesForeignStun(GameTestHelper helper) {
        ServerPlayer first = owner(helper, "HdSource1");
        ServerPlayer second = owner(helper, "HdSource2");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        Cow absentIssuer = helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 2);
        Cow absentSubject = helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 3);
        try {
            first.setPos(target.position().add(-2, 0, 0));
            second.setPos(target.position().add(2, 0, 0));
            helper.assertTrue(WrittenCommandService.apply(absentIssuer, absentSubject, BookCommand.BLINDNESS_TOGGLE),
                    "Timed command with a living issuer did not apply");
            absentIssuer.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            WrittenCommandService.tick(absentSubject);
            helper.assertTrue(absentSubject.hasEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT)
                            && absentSubject.getEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT).getDuration() == 600,
                    "Timed command lost donor lifetime merely because its issuer unloaded");
            helper.assertTrue(WrittenCommandService.apply(first, target, BookCommand.FREEZE)
                            && WrittenCommandService.apply(second, target, BookCommand.FREEZE)
                            && WrittenCommandService.apply(first, target, BookCommand.BLINDNESS_TOGGLE)
                            && WrittenCommandService.apply(second, target, BookCommand.SELF_HARM_TOGGLE),
                    "Production source-bound command apply failed");
            helper.assertTrue(target.getEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT).getDuration() == 600
                            && target.getEffect(HeavensDoorAddon.KYS_EFFECT).isInfiniteDuration(), "Command durations drifted");
            target.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 400));
            CompoundTag saved = new CompoundTag();
            helper.assertTrue(target.save(saved), "Written-command target could not save");
            target.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            first.setHealth(first.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            BookCommandLeaseService.tickIssuer(first);
            first.setHealth(first.getMaxHealth());
            WrittenCommandService.observeOwner(first);
            Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
            helper.assertTrue(loaded instanceof Cow && helper.getLevel().addFreshEntity(loaded), "Target reload failed");
            target = (Cow) loaded;
            WrittenCommandService.tick(target);
            var state = target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE);
            helper.assertTrue(!state.has(BookCommand.FREEZE, first.getUUID())
                            && state.has(BookCommand.FREEZE, second.getUUID())
                            && !target.hasEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT)
                            && target.hasEffect(HeavensDoorAddon.CANNOT_MOVE)
                            && target.hasEffect(HeavensDoorAddon.KYS_EFFECT)
                            && target.hasEffect(ModStatusEffects.STUN),
                    "Unloaded first-caster revocation erased another caster or foreign STUN");
            second.hurt(second.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!target.hasEffect(HeavensDoorAddon.CANNOT_MOVE)
                            && !target.hasEffect(HeavensDoorAddon.KYS_EFFECT)
                            && target.hasEffect(ModStatusEffects.STUN), "Caster death cleanup touched foreign STUN");
            BookCommandSessionService.applyFlyBack(first, target);
            helper.assertTrue(!target.hasEffect(ModStatusEffects.STUN), "Fly Back left another source's STUN on its target");
            helper.succeed();
        }
        finally {
            target.discard();
            absentIssuer.discard();
            absentSubject.discard();
            close(first);
            close(second);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lowHealthHidesOnlyFourCommandsAndLeavesBooksPagesAndBehavior(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "HdHealth");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            owner.setPos(target.position().add(-2, 0, 0));
            helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(owner), target), "Book mark failed");
            var staleSession = BookCommandSessionService.issue(owner, target);
            helper.assertTrue(staleSession != null, "Session not issued above threshold");
            owner.setHealth(owner.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            BookCommandLeaseService.tickIssuer(owner);
            int mask = BookCommandSessionService.availableCommandMask(owner, target, true);
            for (BookCommand command : BookCommand.values()) {
                if (command.healthLimited()) {
                    helper.assertTrue(!command.permittedBy(mask), "Low-health screen offered " + command);
                }
            }
            helper.assertTrue(BookCommand.FLY_BACK.permittedBy(mask) && BookCommand.MOB_BEHAVIOR_TOGGLE.permittedBy(mask),
                    "Low health hid unrestricted commands");
            helper.assertTrue(!BookCommandSessionService.consume(owner, staleSession.nonce(), BookCommand.FREEZE, -1),
                    "Token issued before damage bypassed the current health counter");
            helper.assertTrue(BookStateService.tearRefusal(owner, target) == null && BookStateService.tearPage(owner, target),
                    "Low health prevented reading/tearing a live book");
            BookStateService.tickPageOwner(owner);
            helper.assertTrue(HeavensDoorAddon.bookState(target).active() && HeavensDoorAddon.bookState(target).hasPages(),
                    "Low health closed a book or revoked pages");
            helper.assertTrue(BookCommandLeaseService.toggleBehavior(target, owner), "Low health refused mob behavior");
            BookCommandLeaseService.tickIssuer(owner);
            BookCommandLeaseService.tickSubject(target);
            helper.assertTrue(HeavensDoorAddon.bookCommandState(target).behaviorActive(), "Health counter released mob behavior");
            owner.setHealth(owner.getMaxHealth());
            helper.assertTrue(BookCommand.FREEZE.permittedBy(BookCommandSessionService.availableCommandMask(owner, target, true)),
                    "Recovery did not restore command availability");
            owner.hurt(owner.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!HeavensDoorAddon.bookState(target).active() && !HeavensDoorAddon.bookState(target).hasPages()
                            && !HeavensDoorAddon.bookCommandState(target).behaviorActive(),
                    "Death failed to release open book, pages or behavior");
            helper.succeed();
        }
        finally {
            target.discard();
            close(owner);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotMoveBlocksAccelerationJumpAndPathingButKeepsExternalMotionAndAttack(GameTestHelper helper) {
        ServerPlayer issuer = owner(helper, "HdMoveIssuer");
        ServerPlayer target = owner(helper, "HdMoveTarget");
        Cow mob = helper.spawn(EntityType.COW, 3, 4, 2);
        Cow freeMob = helper.spawn(EntityType.COW, 3, 4, 5);
        try {
            issuer.setPos(helper.absoluteVec(new Vec3(1, 2, 2)));
            target.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
            var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
            var foreign = HeavensDoorAddon.id("test_foreign_speed");
            speed.addTransientModifier(new AttributeModifier(foreign, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            double boosted = speed.getValue();
            helper.assertTrue(WrittenCommandService.apply(issuer, target, BookCommand.FREEZE), "Cannot Move apply failed");
            helper.assertTrue(speed.getValue() == 0 && !target.hasEffect(ModStatusEffects.STUN), "Cannot Move is not independent of STUN");
            Vec3 external = new Vec3(0.2, -0.1, 0.3);
            target.setDeltaMovement(external);
            target.moveRelative(1.0F, new Vec3(1, 1, 1));
            target.jumpFromGround();
            EmbeddedChannel sent = (EmbeddedChannel) target.connection.getConnection().channel();
            target.displayClientMessage(Component.literal("probe"), true);
            helper.assertTrue(sent.outboundMessages().stream().anyMatch(HeavensDoorOwnerRulesGameTests::isActionBarLine),
                    "Fixture cannot see the action-bar lines sent to the held player");
            sent.outboundMessages().clear();
            CannotMoveRules.enforce(target);
            helper.assertTrue(sent.outboundMessages().stream().noneMatch(HeavensDoorOwnerRulesGameTests::isActionBarLine),
                    "The server told a held player that it cannot move although it did not try to");
            helper.assertTrue(target.getDeltaMovement().equals(external), "Input suppression erased external velocity");
            target.setYRot(80);
            target.setXRot(25);
            helper.assertTrue(target.getYRot() == 80 && target.getXRot() == 25
                            && StandPower.get(target).isSummoned(), "Cannot Move suppressed view or Stand");
            mob.setPos(target.position().add(1, 0, 0));
            float health = mob.getHealth();
            target.attack(mob);
            helper.assertTrue(mob.getHealth() < health, "Cannot Move blocked an attack in place");
            helper.assertTrue(WrittenCommandService.apply(issuer, mob, BookCommand.FREEZE), "Mob Cannot Move apply failed");
            mob.getNavigation().moveTo(mob.getX() + 8, mob.getY(), mob.getZ(), 1);
            CannotMoveRules.enforce(mob);
            helper.assertTrue(mob.getNavigation().isDone(), "Cannot Move left mob pathing active");
            mob.setPos(helper.absoluteVec(new Vec3(3, 4, 2)));
            freeMob.setPos(helper.absoluteVec(new Vec3(3, 4, 5)));
            double x = mob.getX();
            double z = mob.getZ();
            double freeZ = freeMob.getZ();
            for (Cow cow : new Cow[]{mob, freeMob}) {
                cow.setOnGround(false);
                cow.setDeltaMovement(new Vec3(0.25, 0, 0));
                cow.travel(new Vec3(1, 1, 1));
            }
            helper.assertTrue(freeMob.getZ() > freeZ, "Fixture: the travel input does not move a free cow sideways");
            helper.assertTrue(mob.getX() > x && mob.getDeltaMovement().y < 0,
                    "Cannot Move blocked outside motion or gravity");
            helper.assertTrue(mob.getZ() == z, "Cannot Move let the travel input accelerate a mob: moved "
                    + (mob.getZ() - z) + " sideways");
            issuer.setHealth(issuer.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            BookCommandLeaseService.tickIssuer(issuer);
            helper.assertTrue(Math.abs(speed.getValue() - boosted) < 1.0E-6 && speed.getModifier(foreign) != null,
                    "Cannot Move cleanup erased a foreign modifier or left zero speed behind");
            helper.succeed();
        }
        finally {
            mob.discard();
            freeMob.discard();
            close(issuer);
            close(target);
        }
    }

    private static boolean isActionBarLine(Object packet) {
        return packet instanceof ClientboundSystemChatPacket line && line.overlay();
    }

    static ServerPlayer owner(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "Owner Stand capability missing");
        power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
        power.setResolveLevel(power.getMaxResolveLevel());
        helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(player, power), "Owner summon failed");
        power.getSummonedStandEntity().summonLockTicks = 0;
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedOpenBookCannotReturnAfterCasterDeathAndSameUuidRespawn(GameTestHelper helper) {
        ServerPlayer original = owner(helper, "HdBookDeath");
        ServerPlayer respawned = null;
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            Vec3 position = target.position();
            original.setPos(position.add(-2, 0, 0));
            helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(original), target), "Book mark failed");
            long deadline = HeavensDoorAddon.bookState(target).expiresAt();
            CompoundTag saved = new CompoundTag();
            helper.assertTrue(target.save(saved), "Open-book subject could not save");
            target.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(helper.getLevel().getEntity(target.getUUID()) == null, "Subject was not unloaded before death");
            original.hurt(original.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!original.isAlive(), "Caster did not actually die");
            respawned = helper.getLevel().getServer().getPlayerList().respawn(original, false, Entity.RemovalReason.KILLED);
            respawned.setPos(position.add(-2, 0, 0));
            helper.assertTrue(respawned.isAlive() && respawned.getUUID().equals(original.getUUID())
                            && respawned.level() == helper.getLevel()
                            && helper.getLevel().getGameTime() < deadline, "Fixture did not recreate an alive same-UUID caster before timeout");
            Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
            helper.assertTrue(loaded instanceof Cow, "Subject NBT reload failed");
            target = (Cow) loaded;
            target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
            helper.assertTrue(HeavensDoorAddon.bookState(target).active(), "Fixture lost the serialized open-book state");
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Subject could not rejoin level");
            BookStateService.tick(target);
            helper.assertTrue(!HeavensDoorAddon.bookState(target).active() && !target.hasEffect(HeavensDoorAddon.BOOK_EFFECT),
                    "Unloaded open book survived caster death and regained BOOK_EFFECT after same-UUID respawn");
            helper.succeed();
        }
        finally {
            target.discard();
            close(respawned != null ? respawned : original);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacySelfHarmSnapshotAndToggleShareExactCasterOwnership(GameTestHelper helper) {
        ServerPlayer issuer = owner(helper, "HdLegacyUi");
        Cow own = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        Cow foreign = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        Cow untagged = helper.spawnWithNoFreeWill(EntityType.COW, 4, 2, 2);
        String tag = "rotp_hd.SelfHarmOwner";
        UUID foreignOwner = UUID.randomUUID();
        try {
            issuer.setPos(own.position().add(-1, 0, 0));
            own.getPersistentData().putUUID(tag, issuer.getUUID());
            foreign.getPersistentData().putUUID(tag, foreignOwner);
            for (Cow subject : new Cow[]{own, foreign, untagged}) {
                subject.addEffect(new MobEffectInstance(HeavensDoorAddon.KYS_EFFECT, 200));
                helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(issuer), subject), "Legacy book mark failed");
                var session = BookCommandSessionService.issue(issuer, subject);
                helper.assertTrue(session != null, "Legacy book session not issued");
                var snapshot = OpenBookSnapshotPayload.from(subject, HeavensDoorAddon.bookState(subject), session);
                boolean owned = subject == own;
                helper.assertTrue(snapshot.selfHarmActive() == owned,
                        "Legacy own Self Harm snapshot must advertise active/undo, but foreign and untagged must not");
                helper.assertTrue(BookCommandSessionService.consume(issuer, session.nonce(), BookCommand.SELF_HARM_TOGGLE, -1) == owned,
                        "Legacy execute did not follow the snapshot's exact-owner predicate");
                helper.assertTrue(subject.hasEffect(HeavensDoorAddon.KYS_EFFECT) != owned,
                        "Legacy toggle removed a foreign/untagged effect or retained its own");
            }
            helper.assertTrue(!own.getPersistentData().contains(tag)
                            && foreign.getPersistentData().getUUID(tag).equals(foreignOwner), "Legacy source tag cleanup crossed ownership");
            helper.succeed();
        }
        finally {
            own.discard();
            foreign.discard();
            untagged.discard();
            close(issuer);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotAttackCounterReleasesOnlyItsCasterAndKeepsBehavior(GameTestHelper helper) {
        ServerPlayer first = owner(helper, "HdCombat1");
        ServerPlayer second = owner(helper, "HdCombat2");
        Cow firstSubject = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        Cow secondSubject = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            first.setPos(firstSubject.position().add(-1, 0, 0));
            second.setPos(secondSubject.position().add(1, 0, 0));
            helper.assertTrue(BookCommandLeaseService.toggleCombat(firstSubject, second, first)
                            && BookCommandLeaseService.toggleCombat(secondSubject, first, second)
                            && BookCommandLeaseService.toggleBehavior(firstSubject, first), "Command lease setup failed");
            first.setHealth(first.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            BookCommandLeaseService.tickIssuer(first);
            BookCommandLeaseService.tickSubject(firstSubject);
            BookCommandLeaseService.tickSubject(secondSubject);
            helper.assertTrue(!HeavensDoorAddon.bookCommandState(firstSubject).combatActive()
                            && HeavensDoorAddon.bookCommandState(firstSubject).behaviorActive()
                            && HeavensDoorAddon.bookCommandState(secondSubject).combatActive()
                            && HeavensDoorAddon.bookCommandState(secondSubject).combatIssuerUuid().equals(second.getUUID()),
                    "Cannot attack release erased behavior or another caster's combat command");
            first.setGameMode(GameType.CREATIVE);
            helper.assertTrue(BookCommandLeaseService.toggleCombat(firstSubject, second, first),
                    "Creative low-health issuer was refused");
            first.hurt(first.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!HeavensDoorAddon.bookCommandState(firstSubject).active(),
                    "Creative death retained combat or behavior");
            helper.succeed();
        }
        finally {
            firstSubject.discard();
            secondSubject.discard();
            close(first);
            close(second);
        }
    }

    static void close(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.connection.disconnect(Component.literal("Heaven's Door owner rules GameTest complete"));
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void laterExternalEffectsKeepTheirOwnTimersAfterWrittenSourceRelease(GameTestHelper helper) {
        ServerPlayer issuer = owner(helper, "HdMixed");
        Cow longer = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 4.5D)));
        Cow shorter = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 5.5D)));
        Cow layered = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 6.5D)));
        try {
            issuer.setPos(longer.position().add(-1, 0, 0));
            for (Cow subject : new Cow[]{longer, shorter, layered}) {
                helper.assertTrue(WrittenCommandService.apply(issuer, subject, BookCommand.FREEZE), "Owned effect setup failed");
            }
            int shortStart = shorter.tickCount;
            int layeredStart = layered.tickCount;
            longer.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 600, 0, true, true, true));
            shorter.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 20, 0, true, true, true));
            layered.addEffect(new MobEffectInstance(HeavensDoorAddon.CANNOT_MOVE, 20, 2, true, true, true));
            WrittenCommandService.tick(longer);
            helper.assertTrue(longer.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration() == 600,
                    "A later normal external addEffect was shortened to the written command's tracked timer");
            helper.runAfterDelay(2, () -> {
                int longRemaining = longer.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration();
                int shortRemaining = 20 - (shorter.tickCount - shortStart);
                int layeredRemaining = 20 - (layered.tickCount - layeredStart);
                issuer.setHealth(issuer.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
                BookCommandLeaseService.tickIssuer(issuer);
                helper.assertTrue(longer.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration() == longRemaining
                                && longer.getEffect(HeavensDoorAddon.CANNOT_MOVE).isAmbient()
                                && longer.getEffect(HeavensDoorAddon.CANNOT_MOVE).isVisible(), "Release reset the external long timer or metadata");
                helper.assertTrue(shorter.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration() == shortRemaining,
                        "Release lost the shorter external contribution");
                helper.assertTrue(layered.getEffect(HeavensDoorAddon.CANNOT_MOVE).getDuration() == layeredRemaining
                                && layered.getEffect(HeavensDoorAddon.CANNOT_MOVE).getAmplifier() == 2,
                        "Release lost the stronger external contribution");
                helper.runAfterDelay(25, () -> {
                    try {
                        helper.assertTrue(longer.hasEffect(HeavensDoorAddon.CANNOT_MOVE)
                                        && !shorter.hasEffect(HeavensDoorAddon.CANNOT_MOVE)
                                        && !layered.hasEffect(HeavensDoorAddon.CANNOT_MOVE),
                                "An expired external layer revived a released written command");
                        helper.succeed();
                    }
                    finally {
                        longer.discard();
                        shorter.discard();
                        layered.discard();
                        close(issuer);
                    }
                });
            });
        }
        catch (Throwable failure) {
            longer.discard();
            shorter.discard();
            layered.discard();
            close(issuer);
            throw failure;
        }
    }

    // The tests below move mobs through the world, so each runs in a batch of its own.
    @GameTest(template = "empty", timeoutTicks = 60, batch = "hd_fly_back")
    public static void flyBackFreesAStunnedTargetAndThrowsItThroughCannotMove(GameTestHelper helper) {
        ServerPlayer caster = owner(helper, "HdFlyBack");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 5.5D)));
        Runnable cleanup = () -> {
            target.discard();
            close(caster);
        };
        try {
            caster.setPos(spot(helper, 0, 3.5D));
            helper.assertTrue(WrittenCommandService.apply(caster, target, BookCommand.FREEZE), "Cannot Move apply failed");
            target.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 400));
            helper.assertTrue(target.hasEffect(ModStatusEffects.STUN) && target.isNoAi(),
                    "Fixture: the core STUN does not hold the cow");
            Vec3 start = target.position();
            helper.assertTrue(BookCommandSessionService.applyFlyBack(caster, target), "Fly Back was refused");
            helper.assertTrue(!target.hasEffect(ModStatusEffects.STUN), "Fly Back left another source's STUN on its target");
            helper.assertTrue(target.hasEffect(HeavensDoorAddon.CANNOT_MOVE), "Fly Back removed the module's own Cannot Move");
            later(helper, 3, cleanup, () -> {
                helper.assertTrue(target.getX() - start.x > 2.0D,
                        "Fly Back did not throw a target that was stunned and under Cannot Move: moved "
                                + (target.getX() - start.x));
                helper.assertTrue(target.hasEffect(HeavensDoorAddon.CANNOT_MOVE), "The throw ended the module's own Cannot Move");
                cleanup.run();
                helper.succeed();
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "hd_finisher")
    public static void finisherRollsRefusedAtLowHealthFallBackToFlyBack(GameTestHelper helper) {
        ServerPlayer user = owner(helper, "HdFinisher");
        Cow held = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 6.5D)));
        Cow blinded = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 1, 6.5D)));
        Runnable cleanup = () -> {
            held.discard();
            blinded.discard();
            close(user);
        };
        try {
            user.setPos(spot(helper, 0, 3.5D));
            user.setHealth(user.getMaxHealth() * BookStateRules.COMMAND_RELEASE_HEALTH_FRACTION);
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(user);
            Vec3 heldStart = held.position();
            Vec3 blindedStart = blinded.position();
            HeavensDoorEvents.applyFinisherCommand(stand, held, 1);
            HeavensDoorEvents.applyFinisherCommand(stand, blinded, 2);
            helper.assertTrue(!held.hasEffect(HeavensDoorAddon.CANNOT_MOVE)
                            && !blinded.hasEffect(HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT),
                    "Fixture: a user at the health threshold still wrote a restricted command");
            // The finisher's Fly Back is queued behind the tick of the hit.
            later(helper, 8, cleanup, () -> {
                helper.assertTrue(horizontalDistance(held, heldStart) > 2.0D && horizontalDistance(blinded, blindedStart) > 2.0D,
                        "A finisher roll refused at low health did nothing instead of Fly Back: moved "
                                + horizontalDistance(held, heldStart) + " and " + horizontalDistance(blinded, blindedStart));
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
    public static void writerKeepsBookPagesAndBehaviorThroughATotemOfUndying(GameTestHelper helper) {
        writerSurvivesALethalHit(helper, "HdTotem", "a Totem of Undying",
                writer -> writer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING)),
                writer -> writer.getOffhandItem().isEmpty());
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void writerKeepsBookPagesAndBehaviorThroughCheatDeath(GameTestHelper helper) {
        writerSurvivesALethalHit(helper, "HdCheatDeath", "Cheat Death",
                writer -> writer.addEffect(new MobEffectInstance(ModStatusEffects.CHEAT_DEATH, 200)),
                writer -> !writer.hasEffect(ModStatusEffects.CHEAT_DEATH));
    }

    // The core's Angelo Rock punch saves its target this way, with next to no health left.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void writerKeepsBookPagesAndBehaviorThroughACancelledDeath(GameTestHelper helper) {
        UUID[] saved = new UUID[1];
        Consumer<LivingDeathEvent> cancelOnce = event -> {
            if (event.getEntity().getUUID().equals(saved[0])) {
                saved[0] = null;
                event.setCanceled(true);
                event.getEntity().setHealth(0.0001F);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, cancelOnce);
        try {
            writerSurvivesALethalHit(helper, "HdCancelledDeath", "a cancelled death",
                    writer -> saved[0] = writer.getUUID(),
                    writer -> saved[0] == null && writer.getHealth() < 0.01F);
        }
        finally {
            NeoForge.EVENT_BUS.unregister(cancelOnce);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void mobWriterKeepsTornPagesThroughATotemOfUndying(GameTestHelper helper) {
        Zombie writer = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 2);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        StandPower power = PowerClass.STAND.attachGet(writer);
        try {
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(writer, power)
                            && BookStateService.markFromHit(HeavensDoorAddon.controller(writer), target)
                            && BookStateService.tearPage(writer, target),
                    "Fixture: a mob could not summon Heaven's Door, open a book or tear a page");
            writer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            writer.hurt(writer.damageSources().generic(), 1000.0F);
            helper.assertTrue(writer.isAlive() && writer.getOffhandItem().isEmpty(),
                    "Fixture: the lethal hit was not stopped by a Totem of Undying");
            helper.assertTrue(HeavensDoorAddon.bookState(target).hasPages(),
                    "A mob writer saved by a Totem of Undying lost its torn page");
            writer.hurt(writer.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!writer.isAlive() && !HeavensDoorAddon.bookState(target).hasPages(),
                    "A mob writer's death no longer releases its pages");
            helper.succeed();
        }
        finally {
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(writer, power);
            }
            // A mob drops the page it tears.
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(6.0D))
                    .forEach(Entity::discard);
            target.discard();
            writer.discard();
        }
    }

    private static void writerSurvivesALethalHit(GameTestHelper helper, String name, String protection,
            Consumer<ServerPlayer> protect, Predicate<ServerPlayer> spent) {
        ServerPlayer writer = owner(helper, name);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
        try {
            Vec3 post = target.position().add(-2, 0, 0);
            writer.setPos(post);
            helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(writer), target)
                            && BookStateService.tearPage(writer, target)
                            && BookCommandLeaseService.toggleBehavior(target, writer),
                    "Fixture: the book, the page or the mob behavior was refused");
            // A player that just joined ignores damage for 60 ticks.
            for (int tick = 0; tick < 60; tick++) {
                writer.tick();
            }
            protect.accept(writer);
            writer.hurt(writer.damageSources().generic(), 1000.0F);
            helper.assertTrue(writer.isAlive() && spent.test(writer),
                    "Fixture: the lethal hit was not stopped by " + protection);
            // Cheat Death moves its user.
            writer.setPos(post);
            BookState book = HeavensDoorAddon.bookState(target);
            helper.assertTrue(book.active() && book.hasPages()
                            && HeavensDoorAddon.bookCommandState(target).behaviorActive(),
                    "A writer saved by " + protection + " lost the open book, the torn page or the mob behavior at the hit");
            BookStateService.tick(target);
            BookCommandLeaseService.tickIssuer(writer);
            BookCommandLeaseService.tickSubject(target);
            helper.assertTrue(book.active() && book.hasPages()
                            && HeavensDoorAddon.bookCommandState(target).behaviorActive(),
                    "A writer saved by " + protection + " lost the open book, the torn page or the mob behavior on the next tick");
            writer.hurt(writer.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!writer.isAlive() && !book.active() && !book.hasPages()
                            && !HeavensDoorAddon.bookCommandState(target).behaviorActive(),
                    "The writer's death no longer releases the open book, the pages and the mob behavior");
            helper.succeed();
        }
        finally {
            target.discard();
            close(writer);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "hd_cannot_move_leap")
    public static void cannotMoveStopsAMobLeapingAtItsTarget(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 12.5D)));
        Spider held = leaper(helper, 0);
        Spider free = leaper(helper, 1);
        Cow heldPrey = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 7.5D)));
        Cow freePrey = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 1, 7.5D)));
        Runnable cleanup = () -> List.of(writer, held, free, heldPrey, freePrey).forEach(Entity::discard);
        // A spider leaps from the ground only: let them land first.
        later(helper, 5, cleanup, () -> {
            held.setTarget(heldPrey);
            free.setTarget(freePrey);
            helper.assertTrue(WrittenCommandService.apply(writer, held, BookCommand.FREEZE), "Cannot Move apply failed");
            Vec3 heldStart = held.position();
            Vec3 freeStart = free.position();
            int heldTicks = held.tickCount;
            later(helper, 40, cleanup, () -> {
                helper.assertTrue(horizontalDistance(free, freeStart) > 0.5D && held.tickCount >= heldTicks + 40,
                        "Fixture: the free spider never leapt at its target, or the held one did not tick");
                helper.assertTrue(horizontalDistance(held, heldStart) < 0.05D,
                        "A spider under Cannot Move leapt at its target: moved " + horizontalDistance(held, heldStart));
                cleanup.run();
                helper.succeed();
            });
        });
    }

    // Only the leap is left, so that a spider that changes its place has leapt.
    private static Spider leaper(GameTestHelper helper, int lane) {
        Spider spider = helper.spawn(EntityType.SPIDER, helper.relativeVec(spot(helper, lane, 4.5D)));
        spider.targetSelector.removeAllGoals(goal -> true);
        spider.goalSelector.removeAllGoals(goal -> !(goal instanceof LeapAtTargetGoal));
        return spider;
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "hd_cannot_move_flight")
    public static void cannotMoveStopsAMobThatSteersItsOwnVelocity(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 12.5D)));
        Vex held = helper.spawnWithNoFreeWill(EntityType.VEX, helper.relativeVec(spot(helper, 0, 4.5D).add(0, 2, 0)));
        Vex free = helper.spawnWithNoFreeWill(EntityType.VEX, helper.relativeVec(spot(helper, 1, 4.5D).add(0, 2, 0)));
        Runnable cleanup = () -> List.of(writer, held, free).forEach(Entity::discard);
        try {
            helper.assertTrue(WrittenCommandService.apply(writer, held, BookCommand.FREEZE), "Cannot Move apply failed");
            Vec3 heldStart = held.position();
            Vec3 freeStart = free.position();
            int heldTicks = held.tickCount;
            for (Vex vex : new Vex[]{held, free}) {
                vex.getMoveControl().setWantedPosition(vex.getX() + 8.0D, vex.getY(), vex.getZ(), 1.0D);
            }
            later(helper, 20, cleanup, () -> {
                helper.assertTrue(horizontalDistance(free, freeStart) > 1.0D && held.tickCount >= heldTicks + 20,
                        "Fixture: the free vex did not fly towards its wanted position, or the held one did not tick");
                helper.assertTrue(horizontalDistance(held, heldStart) < 0.05D,
                        "A vex under Cannot Move kept flying: moved " + horizontalDistance(held, heldStart));
                cleanup.run();
                helper.succeed();
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "hd_cannot_move_attack")
    public static void cannotMoveLeavesAnAttackInPlaceAndKnockback(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 1, 12.5D)));
        // A husk: a zombie that daylight does not set on fire.
        Husk held = helper.spawn(EntityType.HUSK, helper.relativeVec(spot(helper, 0, 6.5D)));
        held.targetSelector.removeAllGoals(goal -> true);
        Cow prey = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 7.7D)));
        Runnable cleanup = () -> List.of(writer, held, prey).forEach(Entity::discard);
        later(helper, 5, cleanup, () -> {
            held.setTarget(prey);
            helper.assertTrue(WrittenCommandService.apply(writer, held, BookCommand.FREEZE), "Cannot Move apply failed");
            float health = prey.getHealth();
            Vec3 start = held.position();
            later(helper, 45, cleanup, () -> {
                helper.assertTrue(prey.getHealth() < health,
                        "A zombie under Cannot Move no longer hits a target within its reach");
                helper.assertTrue(horizontalDistance(held, start) < 0.05D,
                        "A zombie under Cannot Move walked: moved " + horizontalDistance(held, start));
                held.knockback(1.0D, 1.0D, 0.0D);
                later(helper, 5, cleanup, () -> {
                    helper.assertTrue(held.hasEffect(HeavensDoorAddon.CANNOT_MOVE) && start.x - held.getX() > 0.5D,
                            "Knockback no longer moves a mob under Cannot Move: moved " + (start.x - held.getX()));
                    cleanup.run();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cannotMoveCancelsEnderTeleports(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        EnderMan held = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, 3, 2, 2);
        try {
            helper.assertTrue(!EventHooks.onEnderTeleport(held, held.getX() + 4.0D, held.getY(), held.getZ()).isCanceled(),
                    "Fixture: an enderman's teleport is cancelled without Cannot Move");
            helper.assertTrue(WrittenCommandService.apply(writer, held, BookCommand.FREEZE), "Cannot Move apply failed");
            helper.assertTrue(EventHooks.onEnderTeleport(held, held.getX() + 4.0D, held.getY(), held.getZ()).isCanceled(),
                    "An enderman under Cannot Move may still teleport");
            helper.succeed();
        }
        finally {
            writer.discard();
            held.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotMoveIsRefusedForCreativeAndSpectatorPlayers(GameTestHelper helper) {
        ServerPlayer target = owner(helper, "HdMoveRefused");
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        try {
            for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                target.setGameMode(mode);
                helper.assertTrue(!WrittenCommandService.apply(writer, target, BookCommand.FREEZE)
                                && !target.hasEffect(HeavensDoorAddon.CANNOT_MOVE),
                        "Cannot Move was written on a " + mode.getName() + " player");
            }
            target.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(WrittenCommandService.apply(writer, target, BookCommand.FREEZE),
                    "Fixture: Cannot Move is refused for a survival player too");
            helper.succeed();
        }
        finally {
            writer.discard();
            close(target);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotMoveCoversAiStepJumpsMountsAndGliding(GameTestHelper helper) {
        ServerPlayer target = owner(helper, "HdMoveNets");
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        Slime heldSlime = helper.spawnWithNoFreeWill(EntityType.SLIME, 5, 2, 2);
        Slime freeSlime = helper.spawnWithNoFreeWill(EntityType.SLIME, 5, 2, 5);
        JumpProbe horse = new JumpProbe(helper.getLevel());
        try {
            target.setPos(helper.absoluteVec(new Vec3(2, 4, 2)));
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            target.setOnGround(false);
            helper.assertTrue(target.tryToStartFallFlying(), "Fixture: a free player with an elytra cannot start gliding");
            target.stopFallFlying();
            // A flying player keeps 0.6 of its fall each tick and takes no gravity.
            Vec3 falling = new Vec3(0.0D, -0.5D, 0.0D);
            target.getAbilities().flying = true;
            target.setDeltaMovement(falling);
            target.travel(Vec3.ZERO);
            helper.assertTrue(target.getDeltaMovement().y > -0.4D, "Fixture: a free flying player takes gravity");
            helper.assertTrue(WrittenCommandService.apply(writer, target, BookCommand.FREEZE)
                            && WrittenCommandService.apply(writer, heldSlime, BookCommand.FREEZE), "Cannot Move apply failed");
            helper.assertTrue(!target.tryToStartFallFlying() && !target.isFallFlying(),
                    "A player under Cannot Move started gliding");
            target.setDeltaMovement(falling);
            target.travel(Vec3.ZERO);
            helper.assertTrue(target.getDeltaMovement().y < -0.5D,
                    "A flying player under Cannot Move stayed in the air instead of falling: " + target.getDeltaMovement().y);
            target.getAbilities().flying = false;

            for (Slime slime : new Slime[]{heldSlime, freeSlime}) {
                slime.setOnGround(true);
                slime.hasImpulse = false;
                slime.getJumpControl().jump();
                slime.aiStep();
            }
            helper.assertTrue(freeSlime.hasImpulse && freeSlime.getDeltaMovement().y > 0.0D,
                    "Fixture: a free slime does not jump through its AI step");
            helper.assertTrue(!heldSlime.hasImpulse && heldSlime.getDeltaMovement().y <= 0.0D,
                    "A slime under Cannot Move started a jump through its AI step");

            horse.setPos(helper.absoluteVec(new Vec3(2, 2, 5)));
            helper.assertTrue(helper.getLevel().addFreshEntity(horse), "Fixture: the horse could not join the level");
            horse.setTamed(true);
            horse.equipSaddle(new ItemStack(Items.SADDLE), null);
            horse.onPlayerJump(90);
            horse.aiStep();
            helper.assertTrue(horse.pendingJump() > 0.0F, "Fixture: a free horse does not keep its rider's pending jump");
            helper.assertTrue(target.startRiding(horse, true) && horse.getControllingPassenger() == target,
                    "Fixture: the held player does not control the horse");
            horse.setDeltaMovement(new Vec3(0.3D, 0.0D, 0.0D));
            horse.aiStep();
            helper.assertTrue(horse.pendingJump() == 0.0F, "The horse of a held rider kept the pending jump for later");
            // The server is not the side that moves a ridden horse: it must still run its own ridden tick.
            helper.assertTrue(horse.getDeltaMovement().lengthSqr() == 0.0D,
                    "The server skipped the ridden tick of a held rider's horse");
            target.stopRiding();
            horse.onPlayerJump(90);
            helper.assertTrue(WrittenCommandService.apply(writer, horse, BookCommand.FREEZE), "Cannot Move apply failed on the horse");
            horse.aiStep();
            helper.assertTrue(horse.pendingJump() == 0.0F, "A horse under Cannot Move kept its rider's pending jump");
            helper.succeed();
        }
        finally {
            target.stopRiding();
            horse.discard();
            heldSlime.discard();
            freeSlime.discard();
            writer.discard();
            close(target);
        }
    }

    // One tick after a command the effect is one tick ahead of its record.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotMoveIsNotWrittenAgainOnTheTickAfterTheCommand(GameTestHelper helper) {
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 1, 12.5D)));
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, helper.relativeVec(spot(helper, 0, 4.5D)));
        Runnable cleanup = () -> List.of(writer, target).forEach(Entity::discard);
        try {
            helper.assertTrue(WrittenCommandService.apply(writer, target, BookCommand.FREEZE), "Cannot Move apply failed");
            MobEffectInstance written = target.getEffect(HeavensDoorAddon.CANNOT_MOVE);
            int ticks = target.tickCount;
            later(helper, 2, cleanup, () -> {
                helper.assertTrue(target.tickCount >= ticks + 2, "Fixture: the held cow did not tick");
                helper.assertTrue(target.getEffect(HeavensDoorAddon.CANNOT_MOVE) == written,
                        "The service removed and re-added Cannot Move on the tick after the command");
                cleanup.run();
                helper.succeed();
            });
        }
        catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    // The grid sets a test of this one-block template every 7 blocks along z.
    private static final int GRID_ROW_SPACING = 7;

    /**
     * A place on the ground for a mob that has to tick. The framework keeps only the chunk of the test's structure
     * block ticking, so the place lies in that chunk, in one of two rows along x that hold no structure block of
     * the grid: x is counted from the chunk's west edge.
     */
    private static Vec3 spot(GameTestHelper helper, int row, double x) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        int z = (origin.getZ() & 15) % GRID_ROW_SPACING + (row == 0 ? 2 : 5);
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

    private static double horizontalDistance(Entity entity, Vec3 from) {
        return Math.hypot(entity.getX() - from.x, entity.getZ() - from.z);
    }

    // A horse does not show the jump its rider has asked for.
    private static final class JumpProbe extends Horse {
        private JumpProbe(Level level) {
            super(EntityType.HORSE, level);
        }

        private float pendingJump() {
            return playerJumpPendingScale;
        }
    }
}
