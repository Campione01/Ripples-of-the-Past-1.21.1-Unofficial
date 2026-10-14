package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarFingerAbility.StarFingerInstance;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionWithOBBSyncPacket;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SPStarFingerImpactGameTests {
    private static final short KEY = 118;
    private static Suite active;

    private SPStarFingerImpactGameTests() {}

    private enum SceneKind { EMPTY, ENTITY, BROKEN_BLOCK, BEDROCK, RULE_DENIED, EVENT_DENIED }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "star_finger_impact")
    public static void registeredStarFingerKeepsDonorImpactLifecycle(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY production=" + sha(SPStarFingerEntity.class) + " action=" + sha(StarFingerInstance.class)
                    + " fixture=" + sha(SPStarFingerImpactGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "star_finger_impact")
    public static void releaseImpactBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final boolean oldRule;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException observerFailure;
        int index;
        int checks;
        int defaultExpiry;
        int defaultActionTicks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping Star Finger impact suites");
            this.helper = helper;
            level = helper.getLevel();
            oldRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
            helper.testInfo.addListener(this);
            active = this;
        }

        void listen() {
            Consumer<EntityJoinLevelEvent> spawn = event -> {
                if (scene == null || closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof SPStarFingerEntity finger && finger.getOwner() == scene.stand) {
                    scene.emissions.add(finger);
                    scene.finger = finger;
                    scene.uid = finger.getUUID();
                    scene.emittedStandSpeed = scene.stand.getAttackSpeed();
                    log("SPAWN " + scene.kind + " projectile=" + scene.uid + " speedFactor=" + finger.getSpeedFactor()
                            + " standSpeed=" + scene.emittedStandSpeed + " life=" + finger.ticksLifespan());
                }
                if (event.getEntity() instanceof ItemEntity item && scene.block != null
                        && item.position().distanceToSqr(Vec3.atCenterOf(scene.block)) < 4) scene.drops.add(item);
            };
            Consumer<ProjectileImpactEvent> impact = event -> {
                if (scene == null || closed || event.getProjectile() != scene.finger) return;
                scene.impacts++;
                if (event.getRayTraceResult() instanceof BlockHitResult hit && hit.getBlockPos().equals(scene.block)
                        && scene.blockAge == 0) scene.blockAge = scene.finger.tickCount;
            };
            Consumer<LivingDestroyBlockEvent> destroy = event -> {
                if (scene == null || closed || event.getEntity() != scene.stand || !event.getPos().equals(scene.block)) return;
                scene.destroyRequests++;
                if (scene.kind == SceneKind.EVENT_DENIED) event.setCanceled(true);
            };
            Consumer<LivingDamageEvent.Post> damage = event -> {
                if (scene == null || closed || event.getSource().getDirectEntity() != scene.finger) return;
                if (event.getEntity() == scene.first && scene.firstAge == 0) scene.firstAge = scene.finger.tickCount;
                if (event.getEntity() == scene.later && scene.laterAge == 0) scene.laterAge = scene.finger.tickCount;
                log("DAMAGE " + scene.kind + " victim=" + event.getEntity().getUUID() + " age="
                        + scene.finger.tickCount + " projectile=" + scene.finger.getUUID() + " amount=" + event.getNewDamage());
            };
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && event.getEntity() == scene.finger) scene.preDistance = scene.distance();
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene == null || closed || observerFailure != null) return;
                try {
                    if (event.getEntity() == scene.finger) scene.post();
                    if (event.getEntity() == scene.stand) scene.standTick();
                    if (event.getEntity() == scene.stand && scene.observed && scene.blockAge > 0
                            && scene.finger.isAlive() && scene.finger.tickCount <= scene.blockAge + 2
                            && scene.action.extendableOBB().getLength() < scene.impactLength) scene.obbShrank = true;
                }
                catch (RuntimeException error) { observerFailure = error; }
            };
            listeners.add(spawn); listeners.add(impact); listeners.add(destroy); listeners.add(damage);
            listeners.add(pre); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ProjectileImpactEvent.class, impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDestroyBlockEvent.class, destroy);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        void tick() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                if (scene != null) {
                    if (!scene.collisionReady) {
                        if (scene.finger != null) scene.prepareCollision();
                        else helper.assertTrue(++scene.spawnWait < 10, "Registered Star Finger did not emit on native Stand ticks");
                    }
                    else if (++scene.worldTicks >= 25) {
                        scene.finish();
                        scene.close();
                        scene = null;
                    }
                }
                if (scene == null) {
                    if (index == SceneKind.values().length) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Star Finger impact lifecycle failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, SceneKind.values()[index++]);
                    scene.prepare();
                }
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.kind + " " + detail);
            if (!passed) failures.add(scene.kind + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            if (scene != null) scene.close();
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldRule, level.getServer());
            if (active == this) active = null;
            log("CLEANUP listeners=0 restoredRule=" + level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS));
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final SceneKind kind;
        final ServerLevel level;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<SPStarFingerEntity> emissions = new ArrayList<>();
        final List<ItemEntity> drops = new ArrayList<>();
        FakePlayer user;
        RecordingConnection connection;
        StandPower power;
        StandType type;
        StandEntity stand;
        StarFingerInstance action;
        SPStarFingerEntity finger;
        UUID uid;
        Cow first;
        Cow later;
        Vec3 root;
        BlockPos block;
        BlockPos neighbor;
        int worldTicks;
        int spawnWait;
        int firstAge;
        int blockAge;
        int laterAge;
        int introducedAge;
        int removedAge;
        int impacts;
        int destroyRequests;
        int actionTicks;
        double preDistance;
        double emittedStandSpeed;
        float impactLength;
        boolean observed;
        boolean collisionReady;
        boolean obbShrank;
        boolean walkHeld = true;
        boolean closed;

        Scene(Suite suite, SceneKind kind) { this.suite = suite; this.kind = kind; level = suite.level; }

        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMinBlockZ() + 4.5);
            user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StarFingerImpact"));
            user.setGameMode(GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            connection = new RecordingConnection(user);
            user.connection = connection;
            premise(level.addFreshEntity(user), "user added");
            connection.chunkSender.dropChunk(user, user.chunkPosition());
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Star Platinum granted");
            power.setResolveLevel(3);
            premise(type.summon(user, power), "registered Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user);
            stand.updateStrengthMultipliers();
            power.setStamina(power.getMaxStamina());
            premise(connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                    && add.getId() == stand.getId()), "owner tracks real Stand spawn");
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(kind != SceneKind.RULE_DENIED, level.getServer());
            var ability = power.getAbility("star_finger");
            var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "registered CLICK admission");
            BufferingState input = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, input, ability.getAbilityId());
            premise(input.isActionSuccess && stand.getCurStandAction() instanceof StarFingerInstance,
                    "production registered Star Finger action");
            action = (StarFingerInstance) stand.getCurStandAction();
            premise(action.getPhase() == ActionPhase.PERFORM && action.networkGeneration() > 0, "native PERFORM action identity");
            connection.packets.clear();
            log("INPUT " + kind + " action=" + action.id + " generation=" + action.networkGeneration());
        }

        void prepareCollision() {
            premise(action.getPhase() == ActionPhase.PERFORM && emissions.size() == 1
                    && finger.getOwner() == stand && finger.ticksLifespan() == 20
                    && Math.abs(finger.getSpeedFactor() - emittedStandSpeed / 8) < 0.000001,
                    "real PERFORM emission and production stat-derived speed phase=" + action.getPhase()
                            + " generation=" + action.networkGeneration() + " emissions=" + emissions.size()
                            + " life=" + (finger == null ? -1 : finger.ticksLifespan())
                            + " speedFactor=" + (finger == null ? -1 : finger.getSpeedFactor())
                            + " standSpeed=" + emittedStandSpeed);
            root = finger.getOriginPoint(1);
            connection.packets.clear();
            if (kind == SceneKind.ENTITY) first = addCow(1.25);
            if (kind != SceneKind.ENTITY && kind != SceneKind.EMPTY) {
                block = BlockPos.containing(root.add(0, 0, 2.3));
                neighbor = block.east();
                premise(level.getBlockState(block).isAir() && level.getBlockState(neighbor).isAir(), "clear block scene");
                place(block, (kind == SceneKind.BEDROCK ? Blocks.BEDROCK : Blocks.STONE).defaultBlockState());
                place(neighbor, Blocks.GOLD_BLOCK.defaultBlockState());
            }
            log("START " + kind + " action=" + action.id + " generation=" + action.networkGeneration()
                    + " projectile=" + uid + " root=" + root + " block=" + block);
            collisionReady = true;
        }

        void post() {
            if (!collisionReady) return;
            if (finger.isRemoved() && removedAge == 0) removedAge = finger.tickCount;
            int impactAge = kind == SceneKind.ENTITY ? firstAge : blockAge;
            if (!observed && impactAge > 0) {
                observed = true;
                impactLength = action.extendableOBB().getLength();
                if (kind == SceneKind.ENTITY || kind == SceneKind.BROKEN_BLOCK) {
                    suite.check(finger.isAlive() && level.getEntity(finger.getId()) == finger && uid.equals(finger.getUUID()),
                            "ordinary impact keeps same live UID at age=" + impactAge);
                    suite.check(!action.extendableOBB().isRetracting() && connection.obbPackets().isEmpty(),
                            "ordinary impact leaves action extension and OBB sync untouched");
                    if (kind == SceneKind.BROKEN_BLOCK) suite.check(level.getBlockState(block).isAir(), "successful block destruction retained");
                    introducedAge = finger.tickCount;
                    later = addCow(3.1);
                    log("LATER-INTRODUCED " + kind + " age=" + introducedAge + " victim=" + later.getUUID());
                }
                else {
                    List<TrEntityActionWithOBBSyncPacket> packets = connection.obbPackets();
                    suite.check(action.getPhase() == ActionPhase.PERFORM && action.extendableOBB().isRetracting()
                            && !action.extendableOBB().isMovingForward(), "refused block starts early action retraction at age=" + impactAge);
                    suite.check(packets.size() == 1 && packets.getFirst().performerId() == stand.getId()
                            && packets.getFirst().performerUuid().equals(stand.getUUID())
                            && packets.getFirst().actionId() == action.id
                            && packets.getFirst().actionGeneration() == action.networkGeneration(),
                            "real outbound OBB sync matches performer/action/generation packets=" + packets);
                    suite.check(distance() < preDistance, "collision tick retracts real tip before=" + preDistance + " after=" + distance());
                }
            }
            log("TICK " + kind + " age=" + finger.tickCount + " distance=" + distance() + " removed=" + finger.isRemoved()
                    + " obbRetracting=" + action.extendableOBB().isRetracting() + " obbLength=" + action.extendableOBB().getLength());
        }

        // 1.16 Stand task: the Star Finger pose and its 0.5 walk factor last the whole perform, whatever the finger hit
        void standTick() {
            if (action == null || stand.getCurStandAction() != action || action.getPhase() != ActionPhase.PERFORM) return;
            actionTicks++;
            if (action.userWalkSpeed != 0.5F) walkHeld = false;
        }

        void finish() {
            if (finger.isRemoved() && removedAge == 0) removedAge = finger.tickCount;
            if (kind == SceneKind.EMPTY) {
                suite.defaultActionTicks = actionTicks;
                suite.check(actionTicks >= 19 && actionTicks <= 21 && stand.getCurStandAction() != action,
                        "no-impact Star Finger ends after its 20-tick perform ticks=" + actionTicks);
            }
            else {
                suite.check(actionTicks == suite.defaultActionTicks && stand.getCurStandAction() != action,
                        "the action keeps its full perform ticks=" + actionTicks + " control=" + suite.defaultActionTicks);
            }
            suite.check(walkHeld, "the 0.5 walk factor holds through the perform");
            suite.check(emissions.size() == 1 && uid.equals(finger.getUUID()) && finger.isRemoved()
                    && level.getEntity(finger.getId()) == null, "single original emission cleaned up");
            if (kind == SceneKind.ENTITY || kind == SceneKind.BROKEN_BLOCK) {
                suite.check(observed && later != null && laterAge > introducedAge && later.getHealth() < later.getMaxHealth(),
                        "later victim hit on later native tick introduced=" + introducedAge + " hit=" + laterAge);
                suite.check(removedAge == suite.defaultExpiry, "ordinary impact preserves measured no-impact expiry age="
                        + removedAge + " control=" + suite.defaultExpiry);
            }
            else if (kind == SceneKind.EMPTY) {
                suite.defaultExpiry = removedAge;
                suite.check(impacts == 0 && connection.obbPackets().isEmpty() && removedAge > 0
                        && removedAge <= finger.ticksLifespan() + 1,
                        "no-impact natural cleanup within configured lifetime age=" + removedAge);
            }
            else {
                suite.check(observed && removedAge > 0 && removedAge < suite.defaultExpiry && obbShrank,
                        "refused-block tip and action retract early removedAge=" + removedAge + " obbShrank=" + obbShrank);
                suite.check(level.getBlockState(block).is(kind == SceneKind.BEDROCK ? Blocks.BEDROCK : Blocks.STONE),
                        "unbreakable/protected block remains");
                suite.check(connection.obbPackets().size() == 1, "early retraction sends once, not per collision");
                if (kind == SceneKind.EVENT_DENIED) suite.check(destroyRequests > 0, "real destruction event was denied");
            }
            if (neighbor != null) suite.check(level.getBlockState(neighbor).is(Blocks.GOLD_BLOCK), "neighbor block untouched");
            log("END " + kind + " impacts=" + impacts + " firstAge=" + firstAge + " blockAge=" + blockAge
                    + " laterAge=" + laterAge + " removedAge=" + removedAge + " destroyRequests=" + destroyRequests);
        }

        Cow addCow(double distance) {
            Cow cow = new Cow(EntityType.COW, level);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            Vec3 pos = root.add(0, -0.7, distance);
            cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
            premise(level.addFreshEntity(cow), "real later/first victim added");
            return cow;
        }

        double distance() { return finger.position().distanceTo(finger.getOriginPoint(1)); }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, kind + " premise: " + detail); }
        void place(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (user != null) AbilityInput.keyRelease(KEY, user);
            for (SPStarFingerEntity projectile : emissions) projectile.discard();
            if (first != null) first.discard();
            if (later != null) later.discard();
            for (ItemEntity item : drops) item.discard();
            for (var entry : blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            if (power != null && type != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (user != null) user.discard();
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(suite.oldRule, level.getServer());
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        final List<Packet<?>> packets = new ArrayList<>();

        RecordingConnection(FakePlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        List<TrEntityActionWithOBBSyncPacket> obbPackets() {
            return packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                    .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                    .filter(payload -> payload instanceof TrEntityActionWithOBBSyncPacket)
                    .map(payload -> (TrEntityActionWithOBBSyncPacket) payload).toList();
        }

        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(this::send);
            else packets.add(packet);
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
    }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }

    private static void log(String message) { JojoMod.getLogger().info("D-SP-SPECIAL-01 " + message); }
}
