package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.StandInitStarPlatinum;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

final class PhaseEntryGameTestSupport {
    static final short ATTACK = 191, FOLLOW = 192, GRAB = 193;
    private static final Map<String, Suite> ACTIVE = new LinkedHashMap<>();

    private PhaseEntryGameTestSupport() {}

    static void afterBatch(String subject) {
        Suite suite = ACTIVE.get(subject);
        if (suite != null) suite.close();
    }

    static void log(String message) { JojoMod.getLogger().info("[PHASE-ENTRY] {}", message); }
    static boolean near(double a, double b) { return Math.abs(a - b) < 0.0001; }

    static abstract class Suite implements GameTestListener {
        final GameTestHelper helper;
        final String subject;
        final List<String> cases;
        final List<String> failures = new ArrayList<>();
        final List<Object> listeners = new ArrayList<>();
        Scene scene;
        Throwable eventFailure;
        int index, checks;
        boolean closed;

        Suite(GameTestHelper helper, String subject, List<String> cases) {
            this.helper = helper; this.subject = subject; this.cases = cases;
            helper.assertTrue(!ACTIVE.containsKey(subject), "Overlapping phase-entry subject " + subject);
            ACTIVE.put(subject, this);
            helper.testInfo.addListener(this);
        }

        abstract Scene create(String id);

        void start(Class<?> fixture) {
            try {
                for (Class<?> type : new Class<?>[] {fixture, PhaseEntryGameTestSupport.class,
                        StandInitStarPlatinum.class, StandEntityPunchAbility.class,
                        StandEntityPunchAbility.StandEntityPunch.class, StandEntityHeavyPunchAbility.class,
                        StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class, EntityActionInstance.class,
                        StandEntity.class, AbilityInput.class, GameTestPlayers.class}) {
                    var source = type.getProtectionDomain().getCodeSource();
                    helper.assertTrue(source != null, "Missing class origin " + type.getName());
                    String digest = sha(type);
                    String expected = System.getProperty("rotp.phaseEntry.expected." + type.getName());
                    helper.assertTrue(expected == null || expected.equalsIgnoreCase(digest), "Class receipt mismatch " + type.getName());
                    log("LOADED subject=" + subject + " class=" + type.getName() + " sha256=" + digest
                            + " source=" + source.getLocation().toURI().toASCIIString());
                }
                Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> scene.pre(event.getEntity()));
                Consumer<EntityTickEvent.Post> post = event -> observe(() -> scene.post(event.getEntity()));
                listeners.add(pre); listeners.add(post);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
                helper.onEachTick(this::tick);
            }
            catch (Exception | Error error) { close(); throw new IllegalStateException("Phase-entry startup", error); }
        }

        void observe(Runnable observation) {
            if (closed || scene == null || eventFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) { eventFailure = error; }
        }

        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw new IllegalStateException("PHASE-ENTRY invalid event premise", eventFailure);
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == cases.size()) {
                        log("SUMMARY subject=" + subject + " scenes=" + index + " checks=" + checks
                                + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "Phase-entry failures; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = create(cases.get(index++));
                    scene.prepare();
                }
                scene.tick();
                scene.premise(++scene.ticks < 180, "Natural scene timed out; no manual tick or stat refresh used");
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + subject + "/" + scene.id + " " + detail);
            if (!passed) failures.add(scene.id + " " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            listeners.forEach(NeoForge.EVENT_BUS::unregister); listeners.clear();
            if (scene != null) scene.close();
            ACTIVE.remove(subject, this);
            log("CLEANUP subject=" + subject + " listeners=0");
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    static abstract class Scene implements AutoCloseable {
        final Suite suite;
        final String id;
        final ServerLevel level;
        final List<Entity> entities = new ArrayList<>();
        final Map<ChunkPos, Boolean> chunks = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        Player user;
        StandPower power;
        StandType type;
        StandEntity stand;
        Cow target;
        Vec3 origin;
        int ticks, userTicks, standTicks;
        boolean done, closed;

        Scene(Suite suite, String id) { this.suite = suite; this.id = id; level = suite.helper.getLevel(); }
        abstract void prepare();
        abstract void tick();
        void pre(Entity entity) {}
        void post(Entity entity) {
            if (entity == user) userTicks++;
            if (entity == stand) standTicks++;
        }

        void actor(String standName, int resolve, boolean arms) {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5,
                    suite.helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            forceChunk(chunk);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) {
                BlockPos pos = BlockPos.containing(origin).offset(x, -1, z);
                floor.put(pos, level.getBlockState(pos)); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            user.getAbilities().invulnerable = false;
            add(user, origin);
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "Registered Stand grant " + standName);
            power.setResolveLevel(resolve);
            premise(type.summon(user, power), "Native Stand summon");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "Owner/body binding");
            entities.add(stand);
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setNoGravity(true);
            if (arms) stand.setArmsOnlyMode();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            premise(power.usesStamina() && !power.isStaminaInfinite(), "Finite Survival stamina");
        }

        void cow() {
            target = EntityType.COW.create(level);
            premise(target != null, "Native cow created");
            target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            add(target, origin.add(0, 0, 1.5));
        }

        void aim() {
            ActionTarget aim = target != null ? new ActionTarget(target) : ActionTarget.EMPTY;
            LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
            Vec3 point = target != null ? target.getBoundingBox().getCenter() : origin.add(0, 1, 8);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, point); user.setYHeadRot(user.getYRot());
            stand.lookAt(EntityAnchorArgument.Anchor.EYES, point); stand.setYHeadRot(stand.getYRot());
        }

        Press press(String name, short key, boolean canBuffer, float skip) {
            Ability base = power.getAbility(name);
            premise(base != null, "Registered ability " + name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "Real registered condition/context admission " + name);
            BufferingState buffer = canBuffer ? BufferingState.clickCanBuffer() : BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, user, null, InputMethod.CLICK, skip, buffer, base.getAbilityId());
            EntityActionInstance action = held != null && held.action instanceof EntityActionInstance a
                    ? a : stand.getCurStandAction();
            log("INPUT " + suite.subject + "/" + id + " base=" + name + " selected=" + selected.ability.name()
                    + " success=" + buffer.isActionSuccess + " buffered=" + buffer.shouldBuffer
                    + " stamina=" + power.getStamina() + " speedGetter=" + stand.getAttackSpeed());
            return new Press(action, selected.ability, buffer.isActionSuccess, buffer.shouldBuffer);
        }

        void add(Entity entity, Vec3 position) {
            entities.add(entity); entity.setNoGravity(true); entity.noPhysics = true;
            entity.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(entity), "Actual level entity added"); forceChunk(entity.chunkPosition());
        }
        void forceChunk(ChunkPos chunk) {
            chunks.computeIfAbsent(chunk, key -> {
                boolean already = level.getForcedChunks().contains(key.toLong());
                if (!already) level.setChunkForced(key.x, key.z, true);
                level.getChunk(key.x, key.z); return already;
            });
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "PHASE-ENTRY PREMISE " + suite.subject + "/" + id + " " + detail); }
        void check(boolean passed, String detail) { suite.check(passed, detail); }
        @Override public void close() {
            if (closed) return; closed = true;
            if (user != null) {
                AbilityInput.keyRelease(ATTACK, user); AbilityInput.keyRelease(FOLLOW, user); AbilityInput.keyRelease(GRAB, user);
                if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            }
            entities.forEach(Entity::discard);
            floor.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
            chunks.forEach((chunk, already) -> { if (!already) level.setChunkForced(chunk.x, chunk.z, false); });
            log("SCENE-CLEANUP " + suite.subject + "/" + id + " removed=" + entities.stream().allMatch(Entity::isRemoved));
        }
    }

    record Press(EntityActionInstance action, Ability selected, boolean success, boolean buffered) {}

    private static String sha(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing class bytes " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
}
