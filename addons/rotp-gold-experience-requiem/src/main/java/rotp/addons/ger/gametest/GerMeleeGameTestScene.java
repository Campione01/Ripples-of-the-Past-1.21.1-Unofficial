package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

/**
 * GER Stand users and observed targets driven by real inputs and natural
 * server ticks. Everything a scene adds is removed when the test ends.
 */
final class GerMeleeGameTestScene implements GameTestListener, AutoCloseable {
    static final short LMB = 401;
    static final short RMB = 402;
    static final short GUARD = 403;
    static final short SPECIAL = 404;
    static final double EPS = 1.0E-4;
    private static final double FORCE_MARGIN = 3.0D;
    /** Every fixture stands within 4 blocks of the origin; its own margin comes on top. */
    private static final double PREPARED_REACH = 8.0D;
    /**
     * A newly forced chunk ticks entities only after its neighbour ring has
     * generated, which takes real time under load. Each waiting tick lasts at
     * least a millisecond, so the budget is in nanoseconds and the tick
     * ceiling follows from it.
     */
    private static final long CHUNK_WAIT_NANOS = 30_000_000_000L;
    static final int WAIT_TEST_TICKS = 30_100;
    /*
     * The tests of a batch stand 6 blocks apart, so their scenes hold the same chunks. A scene holds them with tickets
     * of its own (a value of its own, not the level's forced chunks): one that closes first, or late, as a listener
     * does once the next batch has started, takes no chunk from another scene, and the end of a batch takes none
     * from a live scene.
     */
    // Level 31 is the entity-ticking level, the one a forced chunk gets.
    static final int TICKET_RADIUS = 2;
    static final TicketType<UUID> TICKET =
            TicketType.create("rotp_ger_gametest", Comparator.<UUID>naturalOrder());

    final GameTestHelper helper;
    final String id;
    final ServerLevel level;
    final Vec3 origin;
    final List<Actor> actors = new ArrayList<>();
    final List<Client> clients = new ArrayList<>();
    final List<Entity> owned = new ArrayList<>();
    final List<Holder<SoundEvent>> positionedSounds = new ArrayList<>();
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private final List<Object> listeners = new ArrayList<>();
    private final List<Runnable> closers = new ArrayList<>();
    /** Every chunk the scene relies on, which it holds with its own ticket. */
    private final Set<ChunkPos> chunks = new LinkedHashSet<>();
    private final UUID ticketId = UUID.randomUUID();
    private boolean prepared;
    private boolean closed;

    record Actor(
            ServerPlayer user,
            StandPower power,
            StandType type,
            StandEntity stand,
            List<Holder<SoundEvent>> standSounds,
            AtomicInteger chunkBatches,
            List<Object> received) {}

    /** A connected player and the custom payloads, death screens and messages it was sent. */
    record Client(ServerPlayer player, List<Object> received) {}

    /**
     * A scene's connected player, which stands for its own client's copy
     * (LocalPlayer.isLocalPlayer) while ownClient is set, for checks that
     * the user's client runs too.
     */
    static final class ScenePlayer extends ServerPlayer {
        boolean ownClient;

        ScenePlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile,
                    ClientInformation.createDefault());
        }

        @Override
        public boolean isLocalPlayer() {
            return ownClient;
        }
    }

    record Knock(double strength, boolean insideHurt) {}

    record Hurt(float amount, float addedKnockback, boolean result) {}

    GerMeleeGameTestScene(GameTestHelper helper, String id) {
        this.helper = helper;
        this.id = id;
        this.level = helper.getLevel();
        // High above the structure, away from its cleanup bounds.
        this.origin = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 24, 1)));
        helper.testInfo.addListener(this);
        force(origin, PREPARED_REACH);
        listen(PlayLevelSoundEvent.AtPosition.class, event -> {
            if (event.getLevel() == level && event.getSound() != null
                    && event.getPosition().distanceTo(origin) < 16) {
                positionedSounds.add(event.getSound());
            }
        });
    }

    Actor actor(Vec3 position, float yaw, @Nullable Consumer<ServerPlayer> beforeSummon) {
        return actor(position, yaw, beforeSummon, true);
    }

    private ServerPlayer connectedPlayer(Vec3 position, float yaw, String name,
            List<Holder<SoundEvent>> standSounds, AtomicInteger chunkBatches, List<Object> received) {
        force(position);
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer user = new ScenePlayer(level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        channels.add(channel);
        new ServerGamePacketListenerImpl(
                level.getServer(), connection, user,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
                record(packet, standSounds, chunkBatches, received);
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
                record(packet, standSounds, chunkBatches, received);
            }
        };
        user.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        user.setNoGravity(true);
        user.noPhysics = true;
        user.moveTo(position.x, position.y, position.z, yaw, 0);
        user.setYHeadRot(yaw);
        owned.add(user);
        level.addNewPlayer(user);
        return user;
    }

    /** A player without a Stand whose received packets are kept. */
    Client client(Vec3 position) {
        List<Object> received = new ArrayList<>();
        ServerPlayer player = connectedPlayer(position, 180, "ger-undie-victim",
                new ArrayList<>(), new AtomicInteger(), received);
        Client client = new Client(player, received);
        clients.add(client);
        return client;
    }

    Actor actor(Vec3 position, float yaw, @Nullable Consumer<ServerPlayer> beforeSummon,
            boolean skipProgression) {
        List<Holder<SoundEvent>> standSounds = new ArrayList<>();
        AtomicInteger chunkBatches = new AtomicInteger();
        List<Object> received = new ArrayList<>();
        ServerPlayer user = connectedPlayer(position, yaw, "ger-melee",
                standSounds, chunkBatches, received);
        user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        user.setHealth(200);
        user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standAttackTargetLock = true;
        if (beforeSummon != null) {
            beforeSummon.accept(user);
        }

        StandPower power = PowerClass.STAND.attachGet(user);
        StandType type = GoldExperienceRequiemAddon.GER_STAND.get();
        premise(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                        == StandPowerTransitions.Status.APPLIED,
                "GER Stand granted");
        if (skipProgression) {
            power.skipProgression();
        }
        premise(type.summon(user, power), "GER Stand summoned");
        StandEntity stand = power.getSummonedStandEntity();
        premise(stand != null && stand.getUser() == user, "summoned GER body is owned");
        owned.add(stand);
        stand.setNoGravity(true);
        stand.noPhysics = true;
        stand.setCanFollowUser(false);
        stand.moveTo(position.x, position.y, position.z, yaw, 0);
        stand.setYHeadRot(yaw);
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        power.setStamina(power.getMaxStamina());
        stand.updateStrengthMultipliers();
        Actor actor = new Actor(user, power, type, stand, standSounds, chunkBatches, received);
        actors.add(actor);
        return actor;
    }

    Actor actor() {
        return actor(origin, 0, null);
    }

    /** A connected user of another Stand, its progression skipped, summoned or not (then without a body). */
    Actor standUser(Vec3 position, float yaw, StandType type, boolean summon) {
        List<Holder<SoundEvent>> standSounds = new ArrayList<>();
        AtomicInteger chunkBatches = new AtomicInteger();
        List<Object> received = new ArrayList<>();
        ServerPlayer user = connectedPlayer(position, yaw, "ger-other-stand",
                standSounds, chunkBatches, received);
        StandPower power = PowerClass.STAND.attachGet(user);
        premise(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                        == StandPowerTransitions.Status.APPLIED,
                type.getId() + " granted");
        power.skipProgression();
        StandEntity stand = null;
        if (summon) {
            premise(type.summon(user, power), type.getId() + " summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "summoned body is owned");
            owned.add(stand);
            stand.setNoGravity(true);
            stand.noPhysics = true;
            stand.moveTo(position.x, position.y, position.z, yaw, 0);
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
        }
        power.setStamina(power.getMaxStamina());
        Actor actor = new Actor(user, power, type, stand, standSounds, chunkBatches, received);
        actors.add(actor);
        return actor;
    }

    /**
     * What PlayerList.respawn does for a scene's player, which the player list does not hold: a new player with the old
     * one's connection and id, restored from it (the Clone event; a death keeps less than an End return), in its place,
     * then the respawn event.
     */
    private ScenePlayer respawn(ServerPlayer old, boolean death) {
        level.removePlayerImmediately(old,
                death ? Entity.RemovalReason.KILLED : Entity.RemovalReason.CHANGED_DIMENSION);
        ScenePlayer player = new ScenePlayer(level, old.getGameProfile());
        player.connection = old.connection;
        player.restoreFrom(old, !death);
        player.setId(old.getId());
        old.connection.player = player;
        player.setNoGravity(true);
        player.noPhysics = true;
        player.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), 0);
        player.setYHeadRot(old.getYRot());
        owned.add(player);
        level.addRespawnedPlayer(player);
        player.setHealth(player.getHealth());
        EventHooks.firePlayerRespawnEvent(player, !death);
        return player;
    }

    /** The actor's player respawned, in the actor's place in the scene. */
    Actor respawn(Actor old, boolean death) {
        ScenePlayer player = respawn(old.user(), death);
        StandPower power = StandPower.get(player);
        premise(power != null && power.getPowerType() == old.type(), old.type().getId() + " kept by the new player");
        StandEntity stand = power.getSummonedStandEntity();
        if (stand != null) {
            owned.add(stand);
        }
        Actor actor = new Actor(player, power, old.type(), stand, old.standSounds(), old.chunkBatches(),
                old.received());
        actors.set(actors.indexOf(old), actor);
        return actor;
    }

    /** The client's player respawned, in the client's place in the scene. */
    Client respawn(Client old, boolean death) {
        Client client = new Client(respawn(old.player(), death), old.received());
        clients.set(clients.indexOf(old), client);
        return client;
    }

    private static void record(Packet<?> packet, List<Holder<SoundEvent>> standSounds,
            AtomicInteger chunkBatches, List<Object> received) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            if (custom.payload() instanceof StandEntitySoundPacket sound) {
                standSounds.add(sound.sound());
            }
            received.add(custom.payload());
        }
        else if (packet instanceof ClientboundChunkBatchStartPacket) {
            chunkBatches.incrementAndGet();
        }
        else if (packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundSystemChatPacket) {
            received.add(packet);
        }
    }

    /**
     * Does what a connected client's packets would: takes its chunk batches,
     * acknowledges them and reports a move, so the server starts sending it
     * the entities in those chunks.
     */
    void tickClients() {
        for (Actor actor : actors) {
            ServerPlayer user = actor.user();
            user.connection.chunkSender.sendNextChunks(user);
            for (int batches = actor.chunkBatches().getAndSet(0); batches > 0; batches--) {
                user.connection.chunkSender.onChunkBatchReceivedByClient(64);
            }
            level.getChunkSource().move(user);
        }
        // A connected player's living tick (and its dying) runs in its connection tick.
        for (Client client : clients) {
            client.player().doTick();
        }
    }

    ObservedCow cow(Vec3 position) {
        ObservedCow cow = new ObservedCow(level);
        cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        cow.setHealth(200);
        return add(cow, position);
    }

    <T extends Entity> T add(T entity, Vec3 position) {
        force(position);
        owned.add(entity);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.moveTo(position.x, position.y, position.z, 180, 0);
        premise(level.addFreshEntity(entity), "target added");
        return entity;
    }

    /** Aims both the user and the body at the target, as a locked-on player would. */
    void aim(Actor actor, @Nullable Entity target) {
        if (target != null) {
            LivingComponentAction.getComponent(actor.user()).entityAim
                    .setTarget(new ActionTarget(target));
            LivingComponentAction.getComponent(actor.stand()).entityAim
                    .setTarget(new ActionTarget(target));
        }
    }

    /** The ability the moveset resolves for a base name right now. */
    AbilityConditionCheck resolve(Actor actor, String baseName) {
        Ability base = actor.power().getAbility(baseName);
        premise(base != null, "registered base " + baseName);
        return actor.power().updateAvailableMoves()
                .getContextVariationContainer(base);
    }

    /** Presses a bound input the way the server handles a client key press. */
    void press(Actor actor, @Nullable Entity target, String baseName,
            String expectedName, InputMethod method, short key) {
        aim(actor, target);
        AbilityConditionCheck selected = resolve(actor, baseName);
        helper.assertTrue(selected != null && selected.ability.name().equals(expectedName),
                id + ": " + baseName + " resolves to "
                        + (selected != null ? selected.ability.name() : null)
                        + ", expected " + expectedName);
        premise(AbilityInput.withConditionCheck(selected, actor.user(), method),
                "conditions of " + expectedName + " " + selected.conditionCheck);
        BufferingState buffer = BufferingState.clickOnly();
        AbilityInput.keyPress(key, selected.ability, actor.user(), null,
                method, 0, buffer, actor.power().getAbility(baseName).getAbilityId());
        premise(buffer.isActionSuccess, "input of " + expectedName + " succeeded");
    }

    EntityActionInstance pressAction(Actor actor, @Nullable Entity target,
            String baseName, String expectedName, InputMethod method, short key) {
        press(actor, target, baseName, expectedName, method, key);
        EntityActionInstance action = actor.stand().getCurStandAction();
        premise(action != null && action.ability == actor.power().getAbility(expectedName),
                expectedName + " action started");
        return action;
    }

    boolean heard(Holder<SoundEvent> sound) {
        return positionedSounds.stream().anyMatch(heard -> heard.value() == sound.value());
    }

    /** Runs when the scene closes, before its entities are removed. */
    void onClose(Runnable closer) {
        closers.add(closer);
    }

    <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        listeners.add(listener);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, type, listener);
    }

    /**
     * Holds every chunk within FORCE_MARGIN blocks of a position: a body
     * moves beside its user, and only a chunk held at the entity-ticking level
     * ticks its entities here. Once the sequence has waited for its chunks, a
     * chunk it has not held is a fixture outside the prepared reach and fails
     * the test.
     */
    void force(Vec3 position) {
        force(position, FORCE_MARGIN);
    }

    private void force(Vec3 position, double margin) {
        int minX = SectionPos.blockToSectionCoord(Mth.floor(position.x - margin));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(position.x + margin));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(position.z - margin));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(position.z + margin));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos key = new ChunkPos(x, z);
                premise(!prepared || chunks.contains(key),
                        "chunk " + x + "," + z + " is held after the sequence waited for its chunks");
                if (chunks.add(key)) {
                    level.getChunkSource().addRegionTicket(TICKET, key, TICKET_RADIUS, ticketId);
                    level.getChunk(key.x, key.z);
                }
            }
        }
    }

    /** Every chunk the scene relies on. */
    List<ChunkPos> heldChunks() {
        return List.copyOf(chunks);
    }

    /** The chunks the scene holds that do not tick entities (yet, or any more). */
    List<ChunkPos> chunksNotTickingEntities() {
        List<ChunkPos> idle = new ArrayList<>();
        for (ChunkPos chunk : chunks) {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition())) {
                idle.add(chunk);
            }
        }
        return idle;
    }

    void prepared() {
        prepared = true;
    }

    void premise(boolean value, String message) {
        helper.assertTrue(value, id + " premise: " + message);
    }

    void log(String message) {
        System.out.println("GER-MELEE " + id + " " + message);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Runnable> cleanup = new ArrayList<>();
        listeners.forEach(listener -> cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener)));
        cleanup.addAll(closers);
        for (Actor actor : actors) {
            for (short key : new short[] {LMB, RMB, GUARD, SPECIAL}) {
                cleanup.add(() -> AbilityInput.keyRelease(key, actor.user()));
            }
            if (actor.stand() != null) {
                cleanup.add(() -> LivingComponentAction.getComponent(actor.stand())
                        .setAction(null, SyncType.NO_SYNC));
            }
            cleanup.add(() -> {
                if (actor.user().hasData(ModDataAttachmentTypes.CONTROLLER)) {
                    actor.user().getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                }
            });
            cleanup.add(() -> {
                if (actor.power().isSummoned()) {
                    actor.type().forceUnsummon(actor.user(), actor.power());
                }
            });
        }
        owned.forEach(entity -> cleanup.add(entity::discard));
        channels.forEach(channel -> cleanup.add(channel::finishAndReleaseAll));
        chunks.forEach(chunk -> cleanup.add(
                () -> level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_RADIUS, ticketId)));
        RuntimeException failure = null;
        for (Runnable step : cleanup) {
            try {
                step.run();
            }
            catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                }
                else {
                    failure.addSuppressed(error);
                }
            }
        }
        listeners.clear();
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        close();
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        close();
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest,
            GameTestRunner runner) {
        close();
    }

    /** A cow that records its hurts and every knockback, inside a hurt or not. */
    static final class ObservedCow extends Cow {
        final List<Knock> knocks = new ArrayList<>();
        final List<Hurt> hurts = new ArrayList<>();
        final List<DamageSource> sources = new ArrayList<>();
        private int hurtDepth;

        ObservedCow(Level level) {
            super(EntityType.COW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            float added = ((DamageSourceModified) source).jojo_ripples$addKnockback();
            hurtDepth++;
            try {
                boolean result = super.hurt(source, amount);
                hurts.add(new Hurt(amount, added, result));
                sources.add(source);
                return result;
            }
            finally {
                hurtDepth--;
            }
        }

        @Override
        public void knockback(double strength, double x, double z) {
            knocks.add(new Knock(strength, hurtDepth > 0));
            super.knockback(strength, x, z);
        }

        List<Knock> detached() {
            return knocks.stream().filter(knock -> !knock.insideHurt()).toList();
        }
    }

    /** Runs a test as a natural-tick sequence and succeeds when it reports done. */
    abstract static class Sequence implements Runnable {
        final GameTestHelper helper;
        final GerMeleeGameTestScene scene;
        final int deadline;
        int tick;
        private final long waitStart = System.nanoTime();
        private int waitedTicks;
        private boolean chunksReady;
        private boolean finished;

        Sequence(GameTestHelper helper, String id, int deadline) {
            this.helper = helper;
            this.scene = new GerMeleeGameTestScene(helper, id);
            this.deadline = deadline;
        }

        void start() {
            helper.onEachTick(this);
        }

        @Override
        public final void run() {
            if (finished) {
                return;
            }
            boolean done;
            try {
                if (!chunksReady && !chunksTickEntities()) {
                    return;
                }
                tick++;
                helper.assertTrue(tick < deadline, scene.id + " did not finish in " + deadline + " ticks");
                List<ChunkPos> idle = scene.chunksNotTickingEntities();
                helper.assertTrue(idle.isEmpty(), scene.id + ": chunks " + idle
                        + " stopped ticking entities while it ran, so what stands in them stops too");
                scene.tickClients();
                done = step();
            }
            catch (RuntimeException | Error error) {
                finished = true;
                scene.close();
                throw error;
            }
            if (done) {
                finished = true;
                scene.close();
                helper.succeed();
            }
        }

        /**
         * Holds the sequence back until every chunk the scene forced ticks
         * entities, so the timed part never runs on a chunk that is still
         * loading.
         */
        private boolean chunksTickEntities() {
            List<ChunkPos> idle = scene.chunksNotTickingEntities();
            long waited = System.nanoTime() - waitStart;
            if (idle.isEmpty()) {
                chunksReady = true;
                scene.prepared();
                scene.log("chunks tick entities after " + waitedTicks + " ticks, "
                        + waited / 1_000_000L + " ms");
                return true;
            }
            helper.assertTrue(waited < CHUNK_WAIT_NANOS,
                    scene.id + ": chunks " + idle + " do not tick entities after " + waitedTicks
                            + " ticks and " + waited / 1_000_000L + " ms although they are held");
            waitedTicks++;
            pause();
            return false;
        }

        private static void pause() {
            try {
                Thread.sleep(1L);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /** @return true once every check has passed */
        abstract boolean step();
    }
}
