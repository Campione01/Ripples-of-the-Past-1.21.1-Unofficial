package rotp.addons.aquanecklace.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.ability.AquaNecklaceAbilities;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity.MaterialState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(AquaNecklaceAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AquaNecklaceGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short GUARD_KEY = 41;
    private static final short HEAVY_KEY = 42;
    private static final short STATE_KEY = 43;
    private static final int LOCK_TICKS = 6;
    // Batch functions are registered for every loaded mod, so the batch name must be unique to this module.
    static final String BATCH = AquaNecklaceAddon.MOD_ID;
    private static final long SETTLE_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(60L);
    private static final long SETTLE_PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(1L);
    // Each waiting tick pauses SETTLE_PAUSE_NANOS, so this is also at least twenty seconds of real time.
    private static final int CHUNK_WAIT_BUDGET_TICKS = 20_000;
    private static final int FIXTURE_TIMEOUT_TICKS = CHUNK_WAIT_BUDGET_TICKS + 40;
    // the summon lock of a Stand a press brings out, then the charge's 20 held ticks
    private static final int AWAY_ENTRY_WAIT_TICKS = 80;
    // Player at +2, its Stand within a block of it, Cow at +3: inside the test's own 6 by 7 lane of the grid.
    private static final int FIXTURE_REACH = 4;
    private static final Map<ChunkPos, Integer> CHUNKS_FORCED_BY_FIXTURES = new HashMap<>();

    private AquaNecklaceGameTests() {}

    /*
     * The game test server stops in the tick in which the last test finishes, and its stop loop gives
     * ChunkMap.processUnloads unlimited time. An unload runnable still queued there re-queues itself for as long as
     * a generation task claims its chunk holder, and a task that waits for the server thread never lets go: the
     * server then spins forever. The fixture players load and release chunks until that last tick, so the batch
     * ends by letting chunk generation finish while the server thread can still run its chunk tasks.
     *
     * Batch functions also run after /test in a development client or server: the players there are real and
     * that server does not stop with the batch, so only the fixtures' forced chunks are given back.
     */
    @AfterBatch(batch = BATCH)
    public static void settleChunkGenerationBeforeShutdown(ServerLevel level) {
        if (!CHUNKS_FORCED_BY_FIXTURES.isEmpty()) {
            LOGGER.warn("Aqua fixtures left {} forced chunks behind when the batch ended: "
                    + "a test ended without its cleanup", CHUNKS_FORCED_BY_FIXTURES.size());
            for (ChunkPos chunk : List.copyOf(CHUNKS_FORCED_BY_FIXTURES.keySet())) {
                level.setChunkForced(chunk.x, chunk.z, false);
            }
            CHUNKS_FORCED_BY_FIXTURES.clear();
        }
        if (!(level.getServer() instanceof GameTestServer)) {
            return;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            LOGGER.error("Aqua fixture player {} was still in the level when the batch ended: "
                    + "its chunk tickets keep chunks loading", player.getGameProfile().getName());
            if (player instanceof FakePlayer) {
                player.discard();
            } else {
                player.connection.disconnect(Component.literal("Aqua fixture player left behind"));
            }
        }
        ServerChunkCache chunks = level.getChunkSource();
        Map<?, ?> holders = chunkHolders(chunks);
        int claimedAtBatchEnd = claimedByGeneration(holders);
        long startedAt = System.nanoTime();
        int chunkTasks = 0;
        while (true) {
            if (chunks.pollTask()) {
                chunkTasks++;
            } else if (claimedByGeneration(holders) == 0 && chunks.getPendingTasksCount() == 0) {
                break;
            } else {
                // Real time for the chunk workers: the game test server does not pace its ticks.
                LockSupport.parkNanos(SETTLE_PAUSE_NANOS);
            }
            if (System.nanoTime() - startedAt > SETTLE_BUDGET_NANOS) {
                throw new IllegalStateException("Chunk generation did not settle within "
                        + TimeUnit.NANOSECONDS.toSeconds(SETTLE_BUDGET_NANOS) + " s of the batch end: "
                        + claimedByGeneration(holders) + " chunk holders are still claimed by generation tasks and "
                        + chunks.getPendingTasksCount() + " server-thread chunk tasks are pending. "
                        + "Stopping the server in this state can spin forever in ChunkMap.processUnloads");
            }
        }
        LOGGER.info("Aqua shutdown guard: chunk generation settled in {} ms after {} server-thread chunk tasks; "
                        + "{} of {} chunk holders were claimed by generation when the batch ended",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), chunkTasks,
                claimedAtBatchEnd, holders.size());
    }

    private static Map<?, ?> chunkHolders(ServerChunkCache chunks) {
        try {
            // The map that generation tasks claim their holders from; ChunkMap offers no public view of it.
            Field updating = ChunkMap.class.getDeclaredField("updatingChunkMap");
            updating.setAccessible(true);
            return (Map<?, ?>) updating.get(chunks.chunkMap);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("Cannot read ChunkMap.updatingChunkMap: the shutdown guard "
                    + "cannot tell whether chunk generation is still in flight", exception);
        }
    }

    private static int claimedByGeneration(Map<?, ?> holders) {
        int claimed = 0;
        for (Object holder : holders.values()) {
            if (((GenerationChunkHolder) holder).getGenerationRefCount() != 0) {
                claimed++;
            }
        }
        return claimed;
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void registeredFluidGuardRespectsBreakLockout(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertFluidGuardRespectsBreakLockout(helper, chunks));
    }

    private static void assertFluidGuardRespectsBreakLockout(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aqua-guard-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        helper.getLevel().addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        boolean scheduled = false;
        try {
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power),
                    "Registered Aqua Necklace summon failed");
            helper.assertTrue(power.getSummonedStandEntity() instanceof AquaNecklaceEntity,
                    "Summon did not create the registered Aqua Necklace entity");
            AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(player);
            LivingComponentAction.getComponent(stand);
            var data = power.getCurTypeData();
            float training = data.getAbilityLearningProgressPoints(AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME);
            int exp = data.getExp();
            boolean gasUnlocked = data.isSkillUnlocked(AquaNecklaceAddon.CHANGE_TO_GASEOUS_NAME);
            boolean skipped = power.wasProgressionSkipped();
            helper.runAfterDelay(2, () -> {
                boolean waitingForExpiry = false;
                try {
                    helper.assertTrue(helper.getLevel().isPositionEntityTicking(stand.blockPosition()),
                            "The Stand's chunk does not tick entities: the lockout below expires by natural Stand "
                                    + "ticks and the Stand left the chunks the fixture holds");
                    Ability guard = power.getAbility(AquaNecklaceAddon.FLUID_GUARD_NAME);
                    helper.assertTrue(guard != null && stand.getMaterialState() == MaterialState.SOLID
                                    && stand.canStartBlocking() && guard.checkSpecificConditions(power).isPositive(),
                            "Normal solid Fluid Guard fixture did not satisfy entry conditions");
                    pressGuard(helper, player, stand, guard);

                    // The same production API is used by block-breaking attacks; no incoming hit is simulated.
                    stand.breakStandBlocking(LOCK_TICKS);
                    int brokenAtTick = stand.tickCount;
                    AbilityInput.keyRelease(GUARD_KEY, player);
                    helper.assertTrue(!stand.isFluidGuarding() && !stand.isStandBlocking()
                                    && LivingComponentAction.getCurEntityAction(stand) == null,
                            "Breaking the active guard did not clear its action and guarding flag");
                    boolean conditionPositive = guard.checkSpecificConditions(power).isPositive();
                    LOGGER.info("Aqua guard fixture break: ticks={}, canStart={}, conditionPositive={}",
                            LOCK_TICKS, stand.canStartBlocking(), conditionPositive);
                    helper.assertTrue(!stand.canStartBlocking() && !conditionPositive,
                            "Fluid Guard bypassed the production blocking lockout");
                    BufferingState blocked = BufferingState.clickCanBuffer();
                    FriendlyByteBuf blockedInput = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(GUARD_KEY, guard, player, blockedInput, InputMethod.HOLD,
                                0.0F, blocked, guard.getAbilityId());
                    } finally {
                        blockedInput.release();
                    }
                    helper.assertTrue(!blocked.isActionSuccess && !stand.isFluidGuarding()
                                    && LivingComponentAction.getCurEntityAction(stand) == null,
                            "The same-tick registered input restarted the broken guard");
                    AbilityInput.keyRelease(GUARD_KEY, player);

                    helper.runAfterDelay(LOCK_TICKS + 2, () -> {
                        try {
                            LOGGER.info("Aqua guard expiry fixture: ticksBefore={}, ticksNow={}, removed={}, alive={}, ownerAlive={}, stamina={}, canStart={}, condition={}",
                                    brokenAtTick, stand.tickCount, stand.isRemoved(), stand.isAlive(), player.isAlive(),
                                    power.getStamina(), stand.canStartBlocking(), guard.checkSpecificConditions(power).isPositive());
                            helper.assertTrue(stand.canStartBlocking()
                                            && guard.checkSpecificConditions(power).isPositive(),
                                    "Natural entity ticks did not restore guard entry after lockout");
                            pressGuard(helper, player, stand, guard);
                            AbilityInput.keyRelease(GUARD_KEY, player);
                            helper.assertTrue(!stand.isFluidGuarding() && !stand.isStandBlocking(),
                                    "Releasing the recovered guard did not clear its guarding state");
                            helper.assertTrue(data.getExp() == exp
                                            && data.getAbilityLearningProgressPoints(
                                                    AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME) == training
                                            && data.isSkillUnlocked(AquaNecklaceAddon.CHANGE_TO_GASEOUS_NAME) == gasUnlocked
                                            && power.wasProgressionSkipped() == skipped,
                                    "Guard lockout changed independent XP, mastery or progression state");
                            LOGGER.info("Aqua guard natural expiry: guard reactivated and released; progression unchanged");
                        } finally {
                            cleanup(player, power, chunks);
                        }
                        helper.succeed();
                    });
                    waitingForExpiry = true;
                } finally {
                    if (!waitingForExpiry) {
                        cleanup(player, power, chunks);
                    }
                }
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup(player, power, chunks);
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void registeredHostCrushUsesHeavyStages(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertRegisteredHeavyStages(helper, chunks, false, false));
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void registeredBrainAttackUsesHeavyStages(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertRegisteredHeavyStages(helper, chunks, true, false));
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void registeredBrainDoesNotHitDepartedHost(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertRegisteredHeavyStages(helper, chunks, true, true));
    }

    private static void assertRegisteredHeavyStages(GameTestHelper helper, FixtureChunks chunks,
            boolean brain, boolean departed) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), brain ? "aqua-brain-test" : "aqua-heavy-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        helper.getLevel().addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow host = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        boolean scheduled = false;
        try {
            host.setNoAi(!brain);
            host.setNoGravity(true);
            host.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            host.setHealth(200.0F);
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            power.skipProgression();
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power),
                    "Heavy-stage Aqua Necklace summon failed");
            helper.assertTrue(power.getSummonedStandEntity() instanceof AquaNecklaceEntity,
                    "Heavy-stage summon did not create the registered Aqua Necklace");
            AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(player);
            LivingComponentAction.getComponent(stand);
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(helper.getLevel().isPositionEntityTicking(host.blockPosition()),
                            "The Cow's chunk does not tick entities: the host left the chunks the fixture holds");
                    helper.assertTrue(helper.getLevel().isPositionEntityTicking(stand.blockPosition()),
                            "The Stand's chunk does not tick entities: the Stand left the chunks the fixture holds");
                    helper.assertTrue(stand.attachTo(host) && stand.getPossessedTarget() == host,
                            "Public possession preparation did not bind the live Cow");
                    stand.setMaterialState(MaterialState.LIQUID);
                    stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(14.0D);
                    stand.setFinisherMeter(0.0F);
                    stand.setHeavyPunchFinisher();
                    stand.setFinisherMeter(0.6F);
                    power.setStamina(power.getMaxStamina());
                    String name = brain ? AquaNecklaceAddon.BRAIN_ATTACK_NAME : AquaNecklaceAddon.HOST_CRUSH_NAME;
                    Ability ability = power.getAbility(name);
                    helper.assertTrue(ability instanceof EntityActionType
                                    && AbilityInput.withConditionCheck(ability, player),
                            "Registered inside-heavy input failed its production conditions");
                    helper.assertTrue(ability instanceof StandEntityAbility standAbility
                                    && standAbility.noFinisherBarDecay,
                            "Inside-heavy action lost the donor finisher-decay exemption");
                    float stamina = power.getStamina();
                    float health = host.getHealth();
                    float meter = stand.getFinisherMeter();
                    float lastHeavy = stand.getLastHeavyFinisherValue();
                    var hand = stand.getCurrentPunchingHand();
                    int windup = StandStatFormulas.getHeavyAttackWindup(stand.getAttackSpeed(), meter);
                    int recovery = StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), meter);
                    float expectedDamage = StandStatFormulas.getHeavyAttackDamage(
                            stand.getAttackDamage() * (brain ? 0.75D : 1.0D)) * (brain ? 2.0F : 1.0F);

                    EntityActionInstance prepared = ((EntityActionType) ability).initActionOnAbilityUse(
                            helper.getLevel(), player, stand, null);
                    helper.assertTrue(prepared != null && stand.getMaterialState() == MaterialState.LIQUID
                                    && power.getStamina() == stamina && stand.getFinisherMeter() == meter
                                    && stand.getLastHeavyFinisherValue() == lastHeavy
                                    && stand.getCurrentPunchingHand() == hand && host.getHealth() == health,
                            "Initializing an uncommitted heavy action converted, charged or mutated its performer");

                    BufferingState pressed = BufferingState.clickCanBuffer();
                    FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        AbilityInput.keyPress(HEAVY_KEY, ability, player, input, InputMethod.CLICK,
                                0.0F, pressed, ability.getAbilityId());
                    } finally {
                        input.release();
                    }
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                    LOGGER.info("Aqua heavy commit: name={}, preparedPhase={}, actualPhase={}, windup={}, recovery={}, material={}, spent={}, meter={}, lastHeavy={}, speed={}",
                            name, prepared.getPhase(), action != null ? action.getPhase() : null,
                            action != null ? action.phasesLength.getFloat(ActionPhase.WINDUP) : -1.0F,
                            action != null ? action.phasesLength.getFloat(ActionPhase.RECOVERY) : -1.0F,
                            stand.getMaterialState(), stamina - power.getStamina(),
                            stand.getFinisherMeter(), stand.getLastHeavyFinisherValue(), stand.getAttackSpeed());
                    helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == ability
                                    && action.getPhase() == ActionPhase.WINDUP
                                    && action.getAnimPhaseTick(0.0F) == 0.0F
                                    && action.getAnimPhaseLength() == windup
                                    && action.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery
                                    && stand.getMaterialState() == MaterialState.SOLID
                                    && Math.abs((stamina - power.getStamina()) - 40.0F) < 0.0001F
                                    && Math.abs(stand.getLastHeavyFinisherValue() - meter) < 0.000001F
                                    && Math.abs(stand.getFinisherMeter() - (meter - 0.51F)) < 0.000001F
                                    && stand.getCurrentPunchingHand() != hand,
                            "Registered inside-heavy commit lost dynamic phases, early conversion or finisher settlement");
                    helper.assertTrue(action.standRotationTarget != null
                                    && action.standRotationTarget.getMainEntity() == host,
                            "The committed heavy action did not bind the prepared host");
                    float committedStamina = power.getStamina();
                    if (departed) {
                        stand.clearPossession();
                        helper.assertTrue(stand.getPossessedTarget() == null,
                                "The departed-host fixture did not clear possession");
                    }
                    for (int tick = 0; tick < windup; tick++) {
                        helper.assertTrue(action.getPhase() == ActionPhase.WINDUP,
                                "Inside-heavy action reached perform before every windup callback");
                        action._tickAction();
                        helper.assertTrue(host.getHealth() == health && !host.hasEffect(ModStatusEffects.STUN)
                                        && power.getStamina() == committedStamina,
                                "Windup applied host damage, stun or the perform-stage stamina payment");
                    }
                    helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                            "Inside-heavy action did not enter perform after the expected callbacks");
                    action._tickAction();
                    helper.assertTrue(Math.abs((health - host.getHealth()) - (departed ? 0.0F : expectedDamage)) < 0.0001F
                                    && Math.abs((committedStamina - power.getStamina()) - 50.0F) < 0.0001F
                                    && action.getPhase() == ActionPhase.RECOVERY
                                    && action.getAnimPhaseLength() == recovery,
                            "First perform callback lost heavy damage, the fifty-stamina payment or snapshot recovery");
                    if (brain && !departed) {
                        var stun = host.getEffect(ModStatusEffects.STUN);
                        helper.assertTrue(host.isAlive() && stun != null && stun.getDuration() == 60,
                                "Brain perform did not apply sixty-tick stun to its surviving host");
                    }
                    if (departed) {
                        helper.assertTrue(!host.hasEffect(ModStatusEffects.STUN),
                                "The departed host received a brain-attack stun");
                    }
                    LOGGER.info("Aqua heavy callbacks: name={}, windupCallbacks={}, damage={}, performSpent={}, recovery={}",
                            name, windup, health - host.getHealth(), committedStamina - power.getStamina(), recovery);
                } finally {
                    AbilityInput.keyRelease(HEAVY_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        host.discard();
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    host.discard();
                }
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void stateChangesRunOnAnEmptyBar(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertStateChangesIgnoreStamina(helper, chunks));
    }

    // 1.16 AquaNecklaceChangeOfState had no stamina condition: StandEntityTask changed the state and then paid the
    // cost with the result ignored, so a short or empty bar still changes the state and ends at 0.
    private static void assertStateChangesIgnoreStamina(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-state-stamina");
        StandPower power = PowerClass.STAND.attachGet(player);
        boolean scheduled = false;
        try {
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                boolean waiting = false;
                try {
                    helper.assertTrue(stand.getMaterialState() == MaterialState.SOLID,
                            "Fixture: the summoned Aqua Necklace is not solid");
                    power.setStamina(0.0F);
                    pressAndPerform(helper, player, stand, power,
                            AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME, STATE_KEY, InputMethod.CLICK,
                            () -> stand.getMaterialState() == MaterialState.LIQUID);
                    helper.assertTrue(power.getStamina() == 0.0F,
                            "The liquid change left an empty bar at " + power.getStamina());
                    AbilityInput.keyRelease(STATE_KEY, player);
                    // 1.16 StandEntityTask read getStaminaCost after standPerform had changed the state, so the half
                    // price AquaNecklaceChangeOfState gave a liquid-gas switch never applied: both switches pay full.
                    helper.runAfterDelay(4, () -> {
                        boolean waitingLiquid = false;
                        try {
                            power.setStamina(100.0F);
                            pressAndPerform(helper, player, stand, power,
                                    AquaNecklaceAddon.CHANGE_TO_GASEOUS_NAME, STATE_KEY, InputMethod.CLICK,
                                    () -> stand.getMaterialState() == MaterialState.VAPOR);
                            helper.assertTrue(power.getStamina() == 20.0F,
                                    "1.16 charged the full 80 for liquid to gas: 100 -> " + power.getStamina());
                            AbilityInput.keyRelease(STATE_KEY, player);
                            helper.runAfterDelay(6, () -> {
                                try {
                                    power.setStamina(100.0F);
                                    pressAndPerform(helper, player, stand, power,
                                            AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME, STATE_KEY, InputMethod.CLICK,
                                            () -> stand.getMaterialState() == MaterialState.LIQUID);
                                    helper.assertTrue(power.getStamina() == 60.0F,
                                            "1.16 charged the full 40 for gas to liquid: 100 -> " + power.getStamina());
                                    LOGGER.info("R769_AQUA_STAMINA_PASS state_changes");
                                } finally {
                                    AbilityInput.keyRelease(STATE_KEY, player);
                                    cleanup(player, power, chunks);
                                }
                                helper.succeed();
                            });
                            waitingLiquid = true;
                        } finally {
                            if (!waitingLiquid) {
                                AbilityInput.keyRelease(STATE_KEY, player);
                                cleanup(player, power, chunks);
                            }
                        }
                    });
                    waiting = true;
                } finally {
                    if (!waiting) {
                        cleanup(player, power, chunks);
                    }
                }
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup(player, power, chunks);
            }
        }
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void insideHostAbilitiesRunOnAShortBar(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertInsideAbilitiesIgnoreStamina(helper, chunks, false));
    }

    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void drownHostHoldsOnAnEmptyBar(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertInsideAbilitiesIgnoreStamina(helper, chunks, true));
    }

    // 1.16 Aqua Necklace had no stamina condition inside a host: AquaNecklaceHeavyPunch, AquaNecklacePunch and
    // AquaNecklaceBlock turned the Stand solid and AquaNecklaceGetIntoTheLungs turned it liquid paying 40 with the
    // result ignored, the heavy attack paid its 50 the same way and the lungs hold ignored its 0.25 a tick.
    private static void assertInsideAbilitiesIgnoreStamina(GameTestHelper helper, FixtureChunks chunks,
            boolean drown) {
        FakePlayer player = staminaPlayer(helper, drown ? "aqua-drown-stamina" : "aqua-inside-stamina");
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow host = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        boolean scheduled = false;
        try {
            host.setNoAi(true);
            host.setNoGravity(true);
            host.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            host.setHealth(200.0F);
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(helper.getLevel().isPositionEntityTicking(host.blockPosition()),
                            "The Cow's chunk does not tick entities: the host left the chunks the fixture holds");
                    helper.assertTrue(stand.attachTo(host) && stand.getPossessedTarget() == host,
                            "Public possession preparation did not bind the live Cow");
                    if (drown) {
                        assertDrownIgnoresStamina(helper, player, stand, power, host);
                    } else {
                        assertInsideHeavyIgnoresStamina(helper, player, stand, power, host);
                    }
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        host.discard();
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    host.discard();
                }
            }
        }
    }

    private static void assertInsideHeavyIgnoresStamina(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, Cow host) {
        stand.setMaterialState(MaterialState.LIQUID);
        power.setStamina(10.0F);
        for (String name : List.of(AquaNecklaceAddon.HOST_CRUSH_NAME, AquaNecklaceAddon.BRAIN_ATTACK_NAME,
                AquaNecklaceAddon.HOST_PRESSURE_NAME, AquaNecklaceAddon.DROWN_HOST_NAME)) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability != null && AbilityInput.withConditionCheck(ability, player),
                    "1.16 had no stamina check: " + name + " must be admitted inside a host on a 10 bar");
        }
        Ability guard = power.getAbility(AquaNecklaceAddon.FLUID_GUARD_NAME);
        helper.assertTrue(guard != null && guard.checkSpecificConditions(power).isPositive(),
                "1.16 AquaNecklaceBlock had no stamina check: the liquid guard must be admitted on a 10 bar");

        float health = host.getHealth();
        Ability crush = power.getAbility(AquaNecklaceAddon.HOST_CRUSH_NAME);
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, crush, player, null, InputMethod.CLICK, 0.0F, pressed, crush.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == crush
                        && action.getPhase() == ActionPhase.WINDUP,
                "The crush did not start its windup on a 10 bar: "
                        + (action == null ? "no action" : action.getPhase()));
        helper.assertTrue(stand.getMaterialState() == MaterialState.SOLID && power.getStamina() == 0.0F,
                "The forced solid state must come with a 40 cost that drains a 10 bar to 0: material="
                        + stand.getMaterialState() + " stamina=" + power.getStamina());
        for (int tick = 0; tick < 40 && action.getPhase() != ActionPhase.RECOVERY && !action.isOver(); tick++) {
            action._tickAction();
        }
        helper.assertTrue(host.getHealth() < health && power.getStamina() == 0.0F,
                "The crush on an empty bar must still hit and pay its 50 into an empty bar: damage="
                        + (health - host.getHealth()) + " stamina=" + power.getStamina());
        LOGGER.info("R769_AQUA_STAMINA_PASS inside_host damage={}", health - host.getHealth());
    }

    private static void assertDrownIgnoresStamina(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, Cow host) {
        helper.assertTrue(stand.getMaterialState() == MaterialState.SOLID && stand.canDrownPossessedTarget(),
                "Fixture: the drowning hold needs a solid Stand inside a breathing host");
        power.setStamina(0.0F);
        Ability drown = power.getAbility(AquaNecklaceAddon.DROWN_HOST_NAME);
        helper.assertTrue(drown != null && AbilityInput.withConditionCheck(drown, player),
                "1.16 AquaNecklaceGetIntoTheLungs had no stamina check: it must be admitted on an empty bar");
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, drown, player, null, InputMethod.HOLD, 0.0F, pressed, drown.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == drown,
                "The drowning hold did not start on an empty bar");
        int air = host.getAirSupply();
        for (int tick = 0; tick < 6; tick++) {
            action._tickAction();
        }
        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && !action.isOver(),
                "1.16 ignored the 0.25 ticking cost: the hold must go on on an empty bar, phase " + action.getPhase());
        helper.assertTrue(stand.getMaterialState() == MaterialState.LIQUID && power.getStamina() == 0.0F,
                "The hold must turn the Stand liquid and leave the bar at 0: material=" + stand.getMaterialState()
                        + " stamina=" + power.getStamina());
        helper.assertTrue(host.getAirSupply() < air, "The held drowning did not take the host's air: "
                + air + " -> " + host.getAirSupply());
        LOGGER.info("R769_AQUA_STAMINA_PASS drown_host air={}->{}", air, host.getAirSupply());
    }

    private static FakePlayer staminaPlayer(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static AquaNecklaceEntity summonForStamina(GameTestHelper helper, FakePlayer player, StandPower power) {
        power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
        power.skipProgression();
        helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power),
                "Stamina fixture Aqua Necklace summon failed");
        helper.assertTrue(power.getSummonedStandEntity() instanceof AquaNecklaceEntity,
                "Stamina fixture summon did not create the registered Aqua Necklace");
        helper.assertTrue(!power.isStaminaInfinite(), "Fixture: the stamina bar is infinite");
        AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        LivingComponentAction.getComponent(player);
        LivingComponentAction.getComponent(stand);
        return stand;
    }

    private static void pressAndPerform(GameTestHelper helper, FakePlayer player, AquaNecklaceEntity stand,
            StandPower power, String name, short key, InputMethod method, BooleanSupplier performed) {
        Ability ability = power.getAbility(name);
        float stamina = power.getStamina();
        helper.assertTrue(ability != null && AbilityInput.withConditionCheck(ability, player),
                "1.16 had no stamina check: " + name + " must be admitted on a " + stamina + " bar");
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(key, ability, player, null, method, 0.0F, pressed, ability.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == ability,
                name + " did not start on a " + stamina + " bar");
        for (int tick = 0; tick < 10 && !performed.getAsBoolean() && !action.isOver(); tick++) {
            action._tickAction();
        }
        helper.assertTrue(performed.getAsBoolean(), name + " did not change the state on a " + stamina
                + " bar: material " + stand.getMaterialState());
    }

    /*
     * Only the chunk of the test origin is known to tick entities when a test starts, and the fixtures stand up to
     * FIXTURE_REACH blocks from it. They stay in the test's own lane of the grid: the chunks that lane reaches are
     * forced, and the fixture is built only once all of them tick entities. Loading them takes real time.
     */
    private static void runWhereEntitiesTick(GameTestHelper helper, Consumer<FixtureChunks> fixture) {
        awaitEntityTicking(helper, new FixtureChunks(helper), 0, System.nanoTime(), fixture);
    }

    private static void awaitEntityTicking(GameTestHelper helper, FixtureChunks chunks, int waitedTicks,
            long startedAt, Consumer<FixtureChunks> fixture) {
        List<ChunkPos> idle = chunks.notTickingEntities();
        if (idle.isEmpty()) {
            LOGGER.info("Aqua fixture chunks: test={}, x%16={}, z%16={}, chunks={}, forcedByFixture={}, "
                            + "waitedTicks={}, waitedMs={}",
                    helper.testInfo.getTestName(), chunks.origin.getX() & 15, chunks.origin.getZ() & 15,
                    chunks.chunks.size(), chunks.forcedHere, waitedTicks,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            boolean built = false;
            try {
                fixture.accept(chunks);
                built = true;
            } finally {
                if (!built) {
                    chunks.release();
                }
            }
            return;
        }
        if (waitedTicks >= CHUNK_WAIT_BUDGET_TICKS) {
            chunks.release();
            throw new GameTestAssertException("The fixture's chunks " + idle + " do not tick entities after "
                    + waitedTicks + " ticks and " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    + " ms of waiting: they were forced, so chunk loading did not finish in that time");
        }
        // Real time for the chunk workers: the game test server does not pace its ticks.
        LockSupport.parkNanos(SETTLE_PAUSE_NANOS);
        helper.runAfterDelay(1, () -> awaitEntityTicking(helper, chunks, waitedTicks + 1, startedAt, fixture));
    }

    private static final class FixtureChunks {
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<ChunkPos> chunks;
        private int forcedHere;
        private boolean held = true;

        private FixtureChunks(GameTestHelper helper) {
            level = helper.getLevel();
            origin = helper.absolutePos(BlockPos.ZERO);
            chunks = ChunkPos.rangeClosed(new ChunkPos(origin),
                    new ChunkPos(helper.absolutePos(new BlockPos(FIXTURE_REACH, 0, FIXTURE_REACH)))).toList();
            // The framework keeps the chunks it forced until the batch ends; only those forced here are counted.
            for (ChunkPos chunk : chunks) {
                Integer users = CHUNKS_FORCED_BY_FIXTURES.get(chunk);
                if (users != null) {
                    CHUNKS_FORCED_BY_FIXTURES.put(chunk, users + 1);
                } else if (level.setChunkForced(chunk.x, chunk.z, true)) {
                    CHUNKS_FORCED_BY_FIXTURES.put(chunk, 1);
                    forcedHere++;
                }
            }
        }

        private List<ChunkPos> notTickingEntities() {
            return chunks.stream().filter(chunk -> !level.isPositionEntityTicking(chunk.getWorldPosition())).toList();
        }

        private void release() {
            if (!held) {
                return;
            }
            held = false;
            for (ChunkPos chunk : chunks) {
                Integer users = CHUNKS_FORCED_BY_FIXTURES.get(chunk);
                if (users == null) {
                    continue;
                }
                if (users > 1) {
                    CHUNKS_FORCED_BY_FIXTURES.put(chunk, users - 1);
                } else {
                    CHUNKS_FORCED_BY_FIXTURES.remove(chunk);
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            }
        }
    }

    private static void pressGuard(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, Ability guard) {
        helper.assertTrue(AbilityInput.withConditionCheck(guard, player),
                "Registered Fluid Guard input was rejected");
        BufferingState pressed = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(GUARD_KEY, guard, player, input, InputMethod.HOLD,
                    0.0F, pressed, guard.getAbilityId());
        } finally {
            input.release();
        }
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == guard,
                "Registered Fluid Guard input did not install its actual action");
        action._tickAction();
        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM
                        && stand.isFluidGuarding() && stand.isStandBlocking(),
                "The registered guard action did not enter active blocking");
        helper.assertTrue(action.userWalkSpeed == 0.5F,
                "The Fluid Guard must halve the user's walk speed (owner rule 2026-10-15): " + action.userWalkSpeed);
        LOGGER.info("Aqua guard registered HOLD: phase={}, fluidFlag={}, blocking={}",
                action.getPhase(), stand.isFluidGuarding(), stand.isStandBlocking());
    }

    /**
     * 1.16 AquaNecklaceEntity: idle withYOffset(-0.75, 0.8, -0.75) and arms-only withYOffset(0, 0.8, 0.15),
     * (left, up, forward) from the user's feet, turned by the view yaw; 0.8 is the default y of task offsets
     * without their own.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH, timeoutTicks = 60)
    public static void idleAquaNecklaceStandsWhereTheDonorPutIt(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "aqua-idle"));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        try {
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power),
                    "Idle fixture Aqua Necklace summon failed");
            helper.assertTrue(power.getSummonedStandEntity() instanceof AquaNecklaceEntity summoned
                            && summoned.isFollowingUser() && !summoned.isArmsOnlyMode()
                            && summoned.offsetFromUser.isIdle(),
                    "Setup: Aqua Necklace is not an idle full Stand following its user");
            AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand.Y_OFFSET == 0.8D,
                    "1.16 Aqua Necklace task offsets without a y took 0.8, got " + stand.Y_OFFSET);
            turnIdleUser(player, 0.0F, 50.0F);
            assertIdleAt(helper, player, stand, -0.75D, 0.8D, -0.75D, "view yaw 0, body yaw 50");
            turnIdleUser(player, 90.0F, 0.0F);
            assertIdleAt(helper, player, stand, 0.75D, 0.8D, -0.75D, "view yaw 90, body yaw 0");
            turnIdleUser(player, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertIdleAt(helper, player, stand, 0.0D, 0.8D, 0.15D, "arms-only, view yaw 0");
            helper.succeed();
        }
        finally {
            AquaNecklaceAddon.AQUA_NECKLACE.get().forceUnsummon(player, power);
            player.discard();
        }
    }

    /**
     * 1.16 AquaNecklaceBlock (a StandEntityBlock) and AquaNecklaceGetIntoTheLungs were holdType Stand actions:
     * PowerBaseImpl let every other action of the power through them and ended the hold once that action went off
     * (onClickAction stopHeldAction(false), setHeldAction). Aqua Necklace has no ability its Stand does not perform,
     * so a pressed Stand action still replaces the held guard at once.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH, timeoutTicks = 60)
    public static void aquaHoldsAreHoldTypeStandActionsAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaPlayer(helper, "aqua-hold-type");
        StandPower power = PowerClass.STAND.attachGet(player);
        try {
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            power.setStamina(power.getMaxStamina());
            Ability drown = power.getAbility(AquaNecklaceAddon.DROWN_HOST_NAME);
            helper.assertTrue(drown instanceof EntityActionType drownType
                            && new AquaNecklaceAbilities.DrownHostAction(drownType).isHoldTypeStandAction(),
                    "1.16 AquaNecklaceGetIntoTheLungs was a holdType Stand action, ended by another action of the"
                            + " power that went off: the drowning hold must be a hold-type Stand action");
            Ability guard = power.getAbility(AquaNecklaceAddon.FLUID_GUARD_NAME);
            pressGuard(helper, player, stand, guard);
            EntityActionInstance hold = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(hold.isHoldTypeStandAction(),
                    "1.16 AquaNecklaceBlock was a holdType Stand action (StandEntityBlock), ended by another action of"
                            + " the power that went off: the Fluid Guard must be a hold-type Stand action");
            Ability liquid = power.getAbility(AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME);
            helper.assertTrue(liquid != null && AbilityInput.withConditionCheck(liquid, player),
                    "Setup: the liquid change is refused during the guard");
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(STATE_KEY, liquid, player, null, InputMethod.CLICK, 0.0F, pressed,
                    liquid.getAbilityId());
            EntityActionInstance now = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != hold
                            && now.ability == liquid && hold.isOver() && !stand.isFluidGuarding(),
                    "1.16 let the liquid change through the held guard at once: started " + pressed.isActionSuccess
                            + ", buffered " + pressed.shouldBuffer + ", the Stand's action " + now + ", the guard over "
                            + hold.isOver());
            LOGGER.info("R770_AQUA_HOLD_TYPE_PASS guard_replaced");
            helper.succeed();
        }
        finally {
            AbilityInput.keyRelease(STATE_KEY, player);
            AbilityInput.keyRelease(GUARD_KEY, player);
            AquaNecklaceAddon.AQUA_NECKLACE.get().forceUnsummon(player, power);
            player.discard();
        }
    }

    /**
     * 1.16 StandEntityAction.canClickDuringTask :495-497 refused the running action's own ability pressed again
     * (clickedAction == this, isChainable false; AquaNecklaceGetIntoTheLungs and AquaNecklaceBlock overrode neither):
     * no message, not queued, the running action left alone.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void holdsPressedAgainAreRefusedSilentlyAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertHoldsPressedAgainRefused(helper, chunks));
    }

    private static void assertHoldsPressedAgainRefused(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-pressed-again");
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow host = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        boolean scheduled = false;
        try {
            host.setNoAi(true);
            host.setNoGravity(true);
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            power.setStamina(power.getMaxStamina());
            helper.runAfterDelay(2, () -> {
                try {
                    assertPressedAgainRefused(helper, player, stand, power,
                            power.getAbility(AquaNecklaceAddon.FLUID_GUARD_NAME));
                    AbilityInput.keyRelease(STATE_KEY, player);
                    AbilityInput.keyRelease((short) (STATE_KEY + 1), player);
                    LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
                    helper.assertTrue(stand.attachTo(host) && stand.getPossessedTarget() == host,
                            "Public possession preparation did not bind the live Cow");
                    assertPressedAgainRefused(helper, player, stand, power,
                            power.getAbility(AquaNecklaceAddon.DROWN_HOST_NAME));
                    LOGGER.info("R770_AQUA_PRESSED_AGAIN_PASS guard_and_drown");
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    AbilityInput.keyRelease((short) (STATE_KEY + 1), player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        host.discard();
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    host.discard();
                }
            }
        }
    }

    private static void assertPressedAgainRefused(GameTestHelper helper, FakePlayer player, AquaNecklaceEntity stand,
            StandPower power, Ability ability) {
        String name = ability == null ? "no ability" : ability.name();
        power._curAvailableMoves.update(power, power.getMoveset());
        helper.assertTrue(ability != null && AbilityInput.withConditionCheck(ability, player),
                "Setup: " + name + " is not admitted");
        BufferingState first = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, ability, player, null, InputMethod.HOLD, 0.0F, first,
                ability.getAbilityId());
        EntityActionInstance running = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(first.isActionSuccess && running != null && running.ability == ability,
                "Setup: " + name + " did not start");
        running._tickAction();
        // The moves are cached once a tick, so this asks again as the next tick's input would.
        power._curAvailableMoves.update(power, power.getMoveset());
        AbilityConditionCheck again = power.updateAvailableMoves().getContextVariationContainer(ability);
        helper.assertTrue(again != null && again.ability == ability && !again.conditionCheck.isPositive()
                        && again.conditionCheck.getWarning() == null,
                "1.16 refused " + name + " pressed again during its hold without a word, the port's check was "
                        + (again == null ? "nothing" : again.conditionCheck.isPositive() ? "positive"
                                : "a refusal with " + again.conditionCheck.getWarning()));
        BufferingState state = BufferingState.clickCanBuffer();
        if (AbilityInput.withConditionCheck(again, player, InputMethod.HOLD)) {
            AbilityInput.keyPress((short) (STATE_KEY + 1), ability, player, null, InputMethod.HOLD, 0.0F, state,
                    ability.getAbilityId());
        }
        EntityActionInstance now = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(!state.isActionSuccess && !state.shouldBuffer && now == running && !running.isOver(),
                "1.16 neither restarted nor queued " + name + " pressed again: started " + state.isActionSuccess
                        + ", buffered " + state.shouldBuffer + ", the Stand's action " + now + " (was " + running
                        + ")");
    }

    /**
     * 1.16 017 InitStands:116-120: aqua_necklace_get_into_the_lungs and aqua_necklace_block (StandEntityBlock) set no
     * standRecoveryTicks (StandEntityAction default 0), so letting go ended them at once (stoppedHolding moved the task
     * to a 0-tick recovery); inside a host the punch was AQUA_NECKLACE_PUNCH, the same light attack class as the punch
     * outside, whose phases came from the light attack's speed formula.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void aquaPhasesAreTheDonorsAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertDonorPhases(helper, chunks));
    }

    private static void assertDonorPhases(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-phases");
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow host = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        boolean scheduled = false;
        try {
            host.setNoAi(true);
            host.setNoGravity(true);
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    power.setStamina(power.getMaxStamina());
                    for (String name : List.of(AquaNecklaceAddon.DROWN_HOST_NAME, AquaNecklaceAddon.FLUID_GUARD_NAME)) {
                        float recovery = initialPhases(helper, power, player, stand, name)
                                .getFloat(ActionPhase.RECOVERY);
                        helper.assertTrue(recovery == 0.0F,
                                "1.16 " + name + " had no recovery (no standRecoveryTicks), the port has " + recovery);
                    }
                    Ability guard = power.getAbility(AquaNecklaceAddon.FLUID_GUARD_NAME);
                    pressGuard(helper, player, stand, guard);
                    EntityActionInstance held = LivingComponentAction.getCurEntityAction(stand);
                    AbilityInput.keyRelease(GUARD_KEY, player);
                    helper.assertTrue(held.isOver() && !stand.isFluidGuarding(),
                            "1.16 ended the released guard at once (a 0-tick recovery): over " + held.isOver()
                                    + " in " + held.getPhase() + ", guarding " + stand.isFluidGuarding());

                    var outside = initialPhases(helper, power, player, stand, AquaNecklaceAddon.HOST_PRESSURE_NAME);
                    helper.assertTrue(stand.attachTo(host) && stand.isPossessing(),
                            "Setup: the Stand did not get into the cow");
                    var inside = initialPhases(helper, power, player, stand, AquaNecklaceAddon.HOST_PRESSURE_NAME);
                    float lightRecovery = StandStatFormulas.getLightAttackRecovery(
                            stand.getAttackSpeed(), stand.getFinisherMeter());
                    helper.assertTrue(inside.getFloat(ActionPhase.WINDUP) == outside.getFloat(ActionPhase.WINDUP)
                                    && inside.getFloat(ActionPhase.WINDUP) > 0.0F
                                    && inside.getFloat(ActionPhase.PERFORM) == lightRecovery
                                    && inside.getFloat(ActionPhase.RECOVERY) == lightRecovery * 4.0F,
                            "1.16 punched inside a host with the light attack's phases (AquaNecklacePunch): windup "
                                    + outside.getFloat(ActionPhase.WINDUP) + ", perform " + lightRecovery
                                    + ", recovery " + lightRecovery * 4.0F + "; the port has " + inside);
                    LOGGER.info("R770_AQUA_PHASES_PASS inside={}", inside);
                } finally {
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        host.discard();
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    host.discard();
                }
            }
        }
    }

    /**
     * 1.16 AquaNecklacePunch.replaceAction (017 :49-52): the punch slot was aqua_necklace_punch only inside a host
     * with a brain; outside, and inside a skeleton, skeleton horse, wither or golem, it was aqua_necklace_punch_out,
     * whose name and icon the HUD showed.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void punchSlotIsThePlainPunchInsideBrainlessHostsAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertPunchSlotByHost(helper, chunks));
    }

    private static void assertPunchSlotByHost(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-punch-slot");
        StandPower power = PowerClass.STAND.attachGet(player);
        List<LivingEntity> hosts = List.of(helper.spawn(EntityType.COW, new BlockPos(3, 2, 2)),
                helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 3)),
                helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4)));
        boolean scheduled = false;
        try {
            for (LivingEntity host : hosts) {
                if (host instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                host.setNoGravity(true);
            }
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    Ability punch = power.getAbility(AquaNecklaceAddon.HOST_PRESSURE_NAME);
                    assertPunchSlot(helper, power, punch, true, "outside a host");
                    for (LivingEntity host : hosts) {
                        stand.clearPossession();
                        helper.assertTrue(stand.attachTo(host) && stand.getPossessedTarget() == host,
                                "Setup: the Stand did not get into the " + host.getType().getDescriptionId());
                        assertPunchSlot(helper, power, punch, !AquaNecklaceEntity.hasBrain(host),
                                "inside a " + host.getType().getDescriptionId());
                    }
                    LOGGER.info("R770_AQUA_PUNCH_SLOT_PASS");
                } finally {
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        hosts.forEach(LivingEntity::discard);
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    hosts.forEach(LivingEntity::discard);
                }
            }
        }
    }

    private static void assertPunchSlot(GameTestHelper helper, StandPower power, Ability punch, boolean plain,
            String where) {
        // getName reads the client's skin loader, so the name is checked through the rule it shares with the icon
        boolean plainName = AquaNecklaceAbilities.HostPressureAbility.showsPlainPunch(power);
        String sprite = punch.getSpriteName(power);
        helper.assertTrue(plainName == plain && sprite.endsWith("_out") == plain,
                "1.16 showed " + (plain ? "aqua_necklace_punch_out" : "aqua_necklace_punch") + " " + where
                        + " (AquaNecklacePunch.replaceAction): the port shows the plain punch's name " + plainName
                        + ", icon " + sprite);
    }

    /**
     * 1.16 017 InitStands :117-118: aqua_necklace_brain_attack had no resolveLevelToUnlock (0) and was the heavy
     * punch's finisher variation (an extra unlockable), so it was unlocked with the Stand and StandUtil
     * .isFinisherMechanicUnlocked opened the finisher meter at Resolve 0; AquaNecklaceHeavyPunch
     * .getFinisherVariationIfPresent gave the heavy's key to it at a 0.5 meter only inside a host with a brain.
     * The port's Host Crush is that heavy punch; the Brain Attack keeps its own H4 wheel slot too.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void brainAttackIsTheHostCrushFinisherFromResolveZeroAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertBrainAttackFinisher(helper, chunks));
    }

    private static void assertBrainAttackFinisher(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-brain-finisher");
        StandPower power = PowerClass.STAND.attachGet(player);
        LivingEntity cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        LivingEntity skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 3));
        List<LivingEntity> hosts = List.of(cow, skeleton);
        boolean scheduled = false;
        try {
            for (LivingEntity host : hosts) {
                if (host instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                host.setNoGravity(true);
            }
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            power.setResolveLevel(0);
            helper.assertTrue(power.getResolveLevel() == 0 && power.getHighestResolveLevel() == 0
                            && !power.wasProgressionSkipped() && !power.isUserCreative(),
                    "Fixture: the Aqua Necklace user is not a Survival user at Resolve 0");
            power.getCurTypeData().unlockOriginalResolveSkills(power);
            Ability brain = power.getAbility(AquaNecklaceAddon.BRAIN_ATTACK_NAME);
            Ability crush = power.getAbility(AquaNecklaceAddon.HOST_CRUSH_NAME);
            helper.assertTrue(brain != null && crush != null, "Aqua Necklace has no Brain Attack or Host Crush");
            helper.assertTrue(brain.getRequiredResolveLevel(power) <= 0
                            && brain.getResolveUnlockConditionCheck(power).isPositive(),
                    "1.16 gave the Brain Attack no Resolve gate; the port asks Resolve "
                            + brain.getRequiredResolveLevel(power));
            helper.assertTrue(power.getCurTypeData().isSkillUnlocked(AquaNecklaceAddon.BRAIN_ATTACK_NAME)
                            && brain.isAbilityUnlocked(power),
                    "1.16 unlocked the Brain Attack with the Stand at Resolve 0; the port keeps it locked");
            helper.assertTrue(brain.isStandFinisherOf != null
                            && AquaNecklaceAddon.HOST_CRUSH_NAME.equals(brain.isStandFinisherOf.baseAbilityName()),
                    "1.16 made the Brain Attack the heavy punch's finisher variation; the port's is not Host Crush's");
            helper.assertTrue(StandEntity.isFinisherVariationUnlocked(power)
                            && StandEntity.isFinisherMechanicUnlocked(power),
                    "1.16 opened the finisher meter at Resolve 0 through the unlocked Brain Attack");
            helper.assertTrue(brain.addToControlSchemeEditing(),
                    "The Brain Attack left the H4 wheel's control scheme editing");
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power),
                    "Brain finisher fixture Aqua Necklace summon failed");
            helper.assertTrue(power.getSummonedStandEntity() instanceof AquaNecklaceEntity,
                    "Brain finisher summon did not create the registered Aqua Necklace");
            AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            LivingComponentAction.getComponent(player);
            LivingComponentAction.getComponent(stand);
            helper.runAfterDelay(2, () -> {
                try {
                    stand.setFinisherMeter(0.6F);
                    helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.6F) < 0.000001F,
                            "The finisher meter reads " + stand.getFinisherMeter() + " at Resolve 0 instead of 0.6");
                    assertHostCrushKey(helper, power, crush, brain, "outside a host at a 0.6 meter");
                    helper.assertTrue(stand.attachTo(cow) && stand.getPossessedTarget() == cow,
                            "Setup: the Stand did not get into the Cow");
                    assertHostCrushKey(helper, power, brain, brain, "inside a Cow at a 0.6 meter");
                    stand.setFinisherMeter(0.4F);
                    assertHostCrushKey(helper, power, crush, brain, "inside a Cow at a 0.4 meter");
                    stand.clearPossession();
                    helper.assertTrue(stand.attachTo(skeleton) && stand.getPossessedTarget() == skeleton,
                            "Setup: the Stand did not get into the Skeleton");
                    stand.setFinisherMeter(0.6F);
                    assertHostCrushKey(helper, power, crush, brain, "inside a Skeleton at a 0.6 meter");
                    LOGGER.info("R770_AQUA_BRAIN_FINISHER_PASS");
                } finally {
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        hosts.forEach(LivingEntity::discard);
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    hosts.forEach(LivingEntity::discard);
                }
            }
        }
    }

    private static void assertHostCrushKey(GameTestHelper helper, StandPower power, Ability expected, Ability brain,
            String where) {
        // updateAvailableMoves caches once per tick; the rows change the state within one tick
        power._curAvailableMoves.update(power, power.getMoveset());
        AbilityConditionCheck key = power._curAvailableMoves.getContextVariationContainer(
                AquaNecklaceAddon.HOST_CRUSH_NAME);
        AbilityConditionCheck own = power._curAvailableMoves.getContextVariationContainer(
                AquaNecklaceAddon.BRAIN_ATTACK_NAME);
        helper.assertTrue(key != null && key.ability == expected,
                "1.16 gave the heavy punch's key to " + expected.name() + " " + where + "; the port's Host Crush key"
                        + " gives " + (key != null ? key.ability.name() : "nothing"));
        helper.assertTrue(own != null && own.ability == brain,
                "The Brain Attack's own wheel slot gives " + (own != null ? own.ability.name() : "nothing") + " "
                        + where);
    }

    private static Object2FloatMap<ActionPhase> initialPhases(GameTestHelper helper,
            StandPower power, FakePlayer player, AquaNecklaceEntity stand, String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType, "Aqua Necklace has no action " + name);
        EntityActionType type = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), player, stand);
        return action.phasesLength;
    }

    /**
     * 1.16 StandEntity.taskOffset: getting_into_entity (TargetRequirement.ENTITY) kept the entity it was pressed at,
     * and while it charged the default offset turned by the yaw from the user's eyes to that entity's eyes, wherever
     * the user looked.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void gettingIntoAnEntityTurnsTheOffsetToIt(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertGettingIntoTurnsTheOffset(helper, chunks));
    }

    // the cow's chunk ticks entities, so the task finds it by its id
    private static void assertGettingIntoTurnsTheOffset(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "aqua-enter-aim"));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
        Vec3 pos = helper.absoluteVec(new Vec3(0.5D, 12.0D, 0.5D));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        helper.getLevel().addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow cow = EntityType.COW.create(helper.getLevel());
        try {
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            power.skipProgression();
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power)
                            && power.getSummonedStandEntity() instanceof AquaNecklaceEntity,
                    "Setup: the Aqua Necklace summon failed");
            AquaNecklaceEntity stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            helper.assertTrue(cow != null, "Setup: could not make a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x + 3.0D, pos.y, pos.z + 2.0D, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Setup: could not add the cow");
            // what the Stand's aim met, as its user's client sent it
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));
            Ability ability = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
            helper.assertTrue(ability instanceof EntityActionType, "Setup: no getting_into_entity ability");
            EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
                    helper.getLevel(), player, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, player, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action && stand.isFollowingUser()
                            && action.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "1.16 kept the entity getting_into_entity was pressed at as its task target, the port holds "
                            + action.getTaskTarget(helper.getLevel()));
            // the user looks west, away from the cow east of it
            turnIdleUser(player, 90.0F, 90.0F);
            Vec3 toCow = cow.getEyePosition().subtract(player.getEyePosition());
            Vec3 expected = new Vec3(-0.75D, 0.8D, -0.75D)
                    .yRot(-MathUtil.yRotDegFromVec(toCow) * MathUtil.DEG_TO_RAD);
            assertIdleAt(helper, player, stand, expected.x, expected.y, expected.z,
                    "getting_into_entity charging at a cow east of its user who looks west (the default offset turned"
                            + " to the cow)");
            LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            AquaNecklaceAddon.AQUA_NECKLACE.get().forceUnsummon(player, power);
            if (cow != null) {
                cow.discard();
            }
            player.discard();
            chunks.release();
        }
    }

    /**
     * 1.16 getting_into_entity (TargetRequirement.ENTITY, holdToFire(20, false)): PowerBaseImpl.onClickAction refused
     * a press without an entity under the crosshair, and tickHeldAction ran checkRequirements on the crosshair on every
     * held tick. No entity stopped the charge silently, one past the Stand's 8 blocks stopped it with target_too_far;
     * a stopped charge entered nothing, paid nothing and set no cooldown.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void gettingIntoAnEntityStopsWhenTheAimLeavesIt(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertEnterRechecksTheAim(helper, chunks));
    }

    private static void assertEnterRechecksTheAim(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-enter-held");
        StandPower power = PowerClass.STAND.attachGet(player);
        Cow host = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        // straight above the host: in the fixture's chunks, 12 blocks from the Stand
        Cow far = helper.spawn(EntityType.COW, new BlockPos(3, 14, 2));
        boolean scheduled = false;
        try {
            for (Cow cow : List.of(host, far)) {
                cow.setNoAi(true);
                cow.setNoGravity(true);
            }
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    assertEnterHeldRecheck(helper, player, stand, power, host, far);
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        host.discard();
                        far.discard();
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                try {
                    cleanup(player, power, chunks);
                } finally {
                    host.discard();
                    far.discard();
                }
            }
        }
    }

    private static void assertEnterHeldRecheck(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, Cow host, Cow far) {
        Ability enter = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
        helper.assertTrue(enter instanceof EntityActionAbility, "Setup: no getting_into_entity ability");
        EntityActionAbility held = (EntityActionAbility) enter;
        power.setStamina(500.0F);

        aimStand(stand, ActionTarget.EMPTY);
        helper.assertTrue(!admitted(enter, player, power),
                "1.16 refused getting_into_entity pressed at nothing: a move that needs a target cannot be charged"
                        + " ahead");
        aimStand(stand, new ActionTarget(far));
        ConditionCheck farPress = enter.checkConditions(power);
        helper.assertTrue(!farPress.isPositive() && isConditionMessage(farPress, "target_too_far"),
                "1.16 Action.checkRangeAndTarget refused a cow 12 blocks above the Stand with target_too_far, got "
                        + describe(farPress));

        EntityActionInstance charge = startEnterCharge(helper, player, stand, power, enter, host);
        aimStand(stand, ActionTarget.EMPTY);
        tickStand(stand);
        assertEnterDropped(helper, stand, power, charge, "the crosshair left the cow");

        charge = startEnterCharge(helper, player, stand, power, enter, host);
        aimStand(stand, new ActionTarget(far));
        ConditionCheck farHeld = held.checkHeldActionConditions(charge, power);
        helper.assertTrue(!farHeld.isPositive() && !farHeld.shouldContinueHold()
                        && isConditionMessage(farHeld, "target_too_far"),
                "1.16 stopped the charge with target_too_far once the crosshair met a cow past the Stand's 8"
                        + " blocks, got " + describe(farHeld));
        tickStand(stand);
        assertEnterDropped(helper, stand, power, charge, "the crosshair went to a cow past the Stand's 8 blocks");

        charge = startEnterCharge(helper, player, stand, power, enter, host);
        for (int tick = 0; tick < 30 && !stand.isPossessing(); tick++) {
            tickStand(stand);
        }
        helper.assertTrue(stand.getPossessedTarget() == host && power.getStamina() < 500.0F
                        && power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME),
                "A charge kept on the cow must enter it, pay and set the cooldown: possessed "
                        + stand.getPossessedTarget() + ", stamina " + power.getStamina());
        LOGGER.info("R770_AQUA_ENTER_HELD_RECHECK_PASS");
    }

    /**
     * 1.16 getting_into_entity: StandEntityAction.startedHolding gave the Stand's task the press's target, and
     * standPerform (017 AquaNecklaceGettingIntoTheEntity :83-93) entered only task.getTarget(). The crosshair the
     * client kept sending during the hold (ClHeldActionTargetPacket) only rechecked the hold (tickHeldAction); the
     * fire from BUTTON_HOLD (StandEntityAction.perform) kept the task's target. StandEntityTask.keepTarget dropped that
     * target on any tick it left 8 blocks of the Stand or was gone; the fire then entered nothing, yet set both
     * cooldowns and paid the stamina.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void gettingIntoAnEntityEntersOnlyItsTaskTargetAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertEnterTakesTheTaskTarget(helper, chunks));
    }

    private static void assertEnterTakesTheTaskTarget(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-enter-task");
        StandPower power = PowerClass.STAND.attachGet(player);
        List<LivingEntity> spawned = new ArrayList<>();
        boolean scheduled = false;
        try {
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    assertEnterTakesTheTaskTargetNow(helper, player, stand, power, spawned);
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        spawned.forEach(LivingEntity::discard);
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup(player, power, chunks);
            }
        }
    }

    private static void assertEnterTakesTheTaskTargetNow(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, List<LivingEntity> spawned) {
        Ability enter = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
        helper.assertTrue(enter instanceof EntityActionAbility, "Setup: no getting_into_entity ability");
        Cow first = stillCow(helper, new BlockPos(3, 2, 2), spawned);
        Cow second = stillCow(helper, new BlockPos(4, 2, 4), spawned);

        // the pressed cow rises 12 blocks above the Stand while the crosshair is on the other one
        resetEnter(power);
        EntityActionInstance charge = startEnterCharge(helper, player, stand, power, enter, first);
        aimStand(stand, new ActionTarget(second));
        Vec3 above = helper.absoluteVec(new Vec3(3.5D, 14.0D, 2.5D));
        first.moveTo(above.x, above.y, above.z, 0.0F, 0.0F);
        fireEnter(stand, charge);
        assertFiredIntoNothing(helper, stand, power, charge, "the pressed cow rose past the Stand's 8 blocks");

        // the pressed cow is gone while the crosshair is on the other one
        resetEnter(power);
        Cow gone = stillCow(helper, new BlockPos(3, 2, 2), spawned);
        charge = startEnterCharge(helper, player, stand, power, enter, gone);
        aimStand(stand, new ActionTarget(second));
        gone.discard();
        fireEnter(stand, charge);
        assertFiredIntoNothing(helper, stand, power, charge, "the pressed cow was gone");

        // the crosshair moves from the pressed cow to the other one, both within the Stand's 8 blocks
        resetEnter(power);
        Vec3 back = helper.absoluteVec(new Vec3(3.5D, 2.0D, 2.5D));
        first.moveTo(back.x, back.y, back.z, 0.0F, 0.0F);
        charge = startEnterCharge(helper, player, stand, power, enter, first);
        aimStand(stand, new ActionTarget(second));
        fireEnter(stand, charge);
        helper.assertTrue(stand.getPossessedTarget() == first && power.getStamina() < 500.0F
                        && power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME),
                "1.16 entered the cow getting_into_entity was pressed at (its task's target), not the one the"
                        + " crosshair moved to during the charge; the port possessed " + stand.getPossessedTarget()
                        + " (pressed " + first + ", crosshair " + second + ")");
        LOGGER.info("R770_AQUA_ENTER_TASK_TARGET_PASS");
    }

    private static Cow stillCow(GameTestHelper helper, BlockPos at, List<LivingEntity> spawned) {
        Cow cow = helper.spawn(EntityType.COW, at);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        spawned.add(cow);
        return cow;
    }

    private static void resetEnter(StandPower power) {
        power.setStamina(500.0F);
        power.setAbilityCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME, 0);
        power.setAbilityCooldown(AquaNecklaceAddon.LEAVE_ENTITY_NAME, 0);
    }

    private static void fireEnter(AquaNecklaceEntity stand, EntityActionInstance charge) {
        for (int tick = 0; tick < 30 && !charge.isOver() && !stand.isPossessing(); tick++) {
            tickStand(stand);
        }
    }

    private static void assertFiredIntoNothing(GameTestHelper helper, AquaNecklaceEntity stand, StandPower power,
            EntityActionInstance charge, String when) {
        helper.assertTrue(!stand.isPossessing() && power.getStamina() < 500.0F
                        && power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME)
                        && power.isAbilityOnCooldown(AquaNecklaceAddon.LEAVE_ENTITY_NAME),
                "1.16's getting_into_entity fired with no task target when " + when + ": it entered nothing, set"
                        + " both cooldowns and paid the stamina; the port possessed " + stand.getPossessedTarget()
                        + ", stamina " + power.getStamina() + ", cooldowns "
                        + power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME) + "/"
                        + power.isAbilityOnCooldown(AquaNecklaceAddon.LEAVE_ENTITY_NAME) + ", phase "
                        + charge.getPhase() + ", over " + charge.isOver());
    }

    /**
     * 1.16 StandEntityAction.overrideVanillaMouseTarget, which PowerBaseImpl.checkTarget ran on the press and on every
     * held tick: getting_into_entity (TargetRequirement.ENTITY) pressed at a torch, which has no collision shape, took
     * what the Stand's collision ray met within its reach. With a cow behind the torch the charge went on and Aqua
     * Necklace entered the cow; with nothing behind it the press was refused silently.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void gettingIntoAnEntityPressedAtATorchEntersTheCowBehindAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertEnterPastATorch(helper, chunks));
    }

    private static void assertEnterPastATorch(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-enter-torch");
        StandPower power = PowerClass.STAND.attachGet(player);
        List<LivingEntity> spawned = new ArrayList<>();
        BlockPos[] torch = new BlockPos[1];
        boolean scheduled = false;
        try {
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    assertEnterPastATorchNow(helper, player, stand, power, spawned, torch);
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        spawned.forEach(LivingEntity::discard);
                        if (torch[0] != null) {
                            helper.getLevel().setBlock(torch[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup(player, power, chunks);
            }
        }
    }

    private static void assertEnterPastATorchNow(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, List<LivingEntity> spawned, BlockPos[] placed) {
        Ability enter = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
        helper.assertTrue(enter instanceof StandEntityAbility, "Setup: no getting_into_entity Stand ability");
        power.setStamina(500.0F);
        // the Stand beside its user, looking south (pitch 0); a wall torch on its look line a block ahead
        Vec3 standPos = helper.absoluteVec(new Vec3(3.5D, 3.0D, 0.5D));
        stand.setNoGravity(true);
        stand.moveTo(standPos.x, standPos.y, standPos.z, 0.0F, 0.0F);
        stand.setYHeadRot(0.0F);
        BlockPos torch = BlockPos.containing(stand.getEyePosition()).south();
        placed[0] = torch;
        helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, Direction.NORTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.assertTrue(helper.getLevel().getBlockState(torch).getCollisionShape(helper.getLevel(), torch).isEmpty(),
                "Setup: the torch has a collision shape");
        ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);

        aimStand(stand, pressedAt);
        helper.assertTrue(!admitted(enter, player, power),
                "1.16 refused getting_into_entity pressed at a torch with nothing behind it in the Stand's reach");

        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Setup: could not make a cow");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(standPos.x, stand.getEyeY() - cow.getBbHeight() / 2.0D, stand.getEyePosition().z + 2.0D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Setup: could not add the cow");
        spawned.add(cow);
        ConditionCheck press = enter.checkConditions(power);
        helper.assertTrue(admitted(enter, player, power) && press.isPositive(),
                "1.16's getting_into_entity pressed at a torch with a cow behind it took the cow"
                        + " (overrideVanillaMouseTarget); the port refused it: " + describe(press));
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, enter, player, null, InputMethod.HOLD, 0.0F, pressed,
                enter.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == enter
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.getTaskTarget(helper.getLevel()).getEntity() == cow,
                "getting_into_entity pressed at a torch did not start charging at the cow behind it: target "
                        + (action != null ? action.getTaskTarget(helper.getLevel()) : null));
        for (int tick = 0; tick < 30 && !stand.isPossessing(); tick++) {
            tickStand(stand);
        }
        helper.assertTrue(stand.getPossessedTarget() == cow,
                "1.16's charge at a torch with a cow behind it entered the cow; the port's possessed "
                        + stand.getPossessedTarget() + " (the torch at " + torch + ")");
        LOGGER.info("R770_AQUA_ENTER_PAST_TORCH_PASS");
    }

    /**
     * 1.16 AquaNecklaceGettingIntoTheEntity.getHoldDurationToFire (:95-100) read the client's crosshair target as it
     * was (InputHandler.mouseTarget), not what overrideVanillaMouseTarget made of it: a skeleton under the crosshair
     * charged 10 ticks; a torch with a skeleton behind it on the Stand's ray charged the builder's 20, though the press
     * took the skeleton.
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS)
    public static void gettingIntoASkeletonChargesTenTicksOnlyWithItUnderTheCrosshairAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertSkeletonChargeByTheRawAim(helper, chunks));
    }

    private static void assertSkeletonChargeByTheRawAim(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-enter-skeleton");
        StandPower power = PowerClass.STAND.attachGet(player);
        List<LivingEntity> spawned = new ArrayList<>();
        BlockPos[] torch = new BlockPos[1];
        boolean scheduled = false;
        try {
            AquaNecklaceEntity stand = summonForStamina(helper, player, power);
            helper.runAfterDelay(2, () -> {
                try {
                    assertSkeletonChargeByTheRawAimNow(helper, player, stand, power, spawned, torch);
                } finally {
                    AbilityInput.keyRelease(STATE_KEY, player);
                    try {
                        cleanup(player, power, chunks);
                    } finally {
                        spawned.forEach(LivingEntity::discard);
                        if (torch[0] != null) {
                            helper.getLevel().setBlock(torch[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                helper.succeed();
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                cleanup(player, power, chunks);
            }
        }
    }

    private static void assertSkeletonChargeByTheRawAimNow(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, List<LivingEntity> spawned, BlockPos[] placed) {
        Ability enter = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
        helper.assertTrue(enter instanceof StandEntityAbility, "Setup: no getting_into_entity Stand ability");
        power.setStamina(500.0F);
        // the Stand beside its user, looking south (pitch 0); a wall torch on its look line a block ahead and a
        // wither skeleton (no daylight burning) 2 blocks behind it
        Vec3 standPos = helper.absoluteVec(new Vec3(3.5D, 3.0D, 0.5D));
        stand.setNoGravity(true);
        stand.moveTo(standPos.x, standPos.y, standPos.z, 0.0F, 0.0F);
        stand.setYHeadRot(0.0F);
        BlockPos torch = BlockPos.containing(stand.getEyePosition()).south();
        placed[0] = torch;
        helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, Direction.NORTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        LivingEntity skeleton = EntityType.WITHER_SKELETON.create(helper.getLevel());
        helper.assertTrue(skeleton instanceof Mob, "Setup: could not make a wither skeleton");
        ((Mob) skeleton).setNoAi(true);
        skeleton.setNoGravity(true);
        skeleton.moveTo(standPos.x, stand.getEyeY() - skeleton.getBbHeight() / 2.0D,
                stand.getEyePosition().z + 2.0D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(skeleton), "Setup: could not add the wither skeleton");
        spawned.add(skeleton);

        float atTorch = enterChargeLength(helper, player, stand, power, enter,
                new ActionTarget(torch, Direction.NORTH), skeleton);
        helper.assertTrue(atTorch == 20.0F,
                "1.16 charged getting_into_entity 20 ticks when the crosshair was at a torch, though the Stand's ray"
                        + " behind it met a skeleton (getHoldDurationToFire read InputHandler.mouseTarget, the torch);"
                        + " the port charges " + atTorch);
        float atSkeleton = enterChargeLength(helper, player, stand, power, enter, new ActionTarget(skeleton),
                skeleton);
        helper.assertTrue(atSkeleton == 10.0F,
                "1.16 charged getting_into_entity 10 ticks with a skeleton under the crosshair; the port charges "
                        + atSkeleton);
        LOGGER.info("R770_AQUA_ENTER_SKELETON_CHARGE_PASS");
    }

    /**
     * 1.16 getting_into_entity (017 InitStands :123) set no standAutoSummonMode: pressed with Aqua Necklace away,
     * StandEntityAction.onClick summoned it in full at its user before PowerBaseImpl.checkTarget judged the vanilla pick
     * the client sent (EntityStandType.clientHitResult with the power inactive) against it: nothing or the user's own
     * wolf (checkStandTarget, canAttack) was refused silently and the Stand taken back, a cow past 8 blocks with
     * target_too_far; a cow beside the user started the charge with the Stand out, its task holding that cow
     * (startedHolding), and the full charge entered it (017 AquaNecklaceGettingIntoTheEntity.standPerform :83-93).
     */
    @GameTest(template = "empty", templateNamespace = AquaNecklaceAddon.MOD_ID, batch = BATCH,
            timeoutTicks = FIXTURE_TIMEOUT_TICKS + AWAY_ENTRY_WAIT_TICKS)
    public static void gettingIntoAnEntityPressedWithAquaAwaySummonsItAsIn116(GameTestHelper helper) {
        runWhereEntitiesTick(helper, chunks -> assertEnterSummonsAqua(helper, chunks));
    }

    private static void assertEnterSummonsAqua(GameTestHelper helper, FixtureChunks chunks) {
        FakePlayer player = staminaPlayer(helper, "aqua-enter-away");
        StandPower power = PowerClass.STAND.attachGet(player);
        List<LivingEntity> spawned = new ArrayList<>();
        boolean scheduled = false;
        try {
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            power.skipProgression();
            power.setStamina(500.0F);
            Ability enter = power.getAbility(AquaNecklaceAddon.ENTER_ENTITY_NAME);
            helper.assertTrue(enter instanceof StandEntityAbility && !power.isSummoned(),
                    "Setup: no getting_into_entity Stand ability, or Aqua Necklace is already out");
            Cow near = stillCow(helper, new BlockPos(2, 2, 4), spawned);
            // straight above the user: in the fixture's chunks, 12 blocks up
            Cow far = stillCow(helper, new BlockPos(2, 14, 2), spawned);
            Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(3, 2, 3));
            spawned.add(wolf);
            wolf.setNoAi(true);
            wolf.tame(player);

            assertAwayPressRefused(helper, player, power, enter, ActionTarget.EMPTY, "at nothing");
            assertAwayPressRefused(helper, player, power, enter, new ActionTarget(wolf),
                    "at the user's own wolf (StandEntity.canAttack)");
            aimUser(player, new ActionTarget(far));
            ConditionCheck tooFar = enter.checkConditions(power);
            helper.assertTrue(!tooFar.isPositive() && isConditionMessage(tooFar, "target_too_far"),
                    "1.16 refused getting_into_entity pressed with Aqua Necklace away at a cow 12 blocks above its user"
                            + " (8 blocks of the Stand summoned there) with target_too_far, got " + describe(tooFar));
            assertAwayPressRefused(helper, player, power, enter, new ActionTarget(far), "at a cow past 8 blocks");

            aimUser(player, new ActionTarget(near));
            helper.assertTrue(admitted(enter, player, power),
                    "1.16 summoned Aqua Necklace for getting_into_entity pressed while it was away at a cow 2 blocks"
                            + " from its user; the port refused the press");
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(STATE_KEY, enter, player, null, InputMethod.HOLD, 0.0F, pressed,
                    enter.getAbilityId());
            AquaNecklaceEntity stand = AquaNecklaceAddon.activeStand(player);
            EntityActionInstance charge = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
            helper.assertTrue(pressed.isActionSuccess && stand != null && stand.isAddedToLevel()
                            && !stand.isArmsOnlyMode() && charge != null && charge.ability == enter
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "1.16 summoned Aqua Necklace in full for the press and started the charge: started "
                            + pressed.isActionSuccess + ", the Stand " + stand + ", its action " + charge);
            // nothing aims the summoned Stand here (no client): what it holds it took from its user at the press
            ActionTarget task = charge.getTaskTarget(helper.getLevel());
            ActionTarget standAim = LivingComponentAction.getComponent(stand).entityAim.getTarget()
                    .resolveEntityId(helper.getLevel());
            helper.assertTrue(task.getType() == ActionTarget.TargetType.ENTITY && task.getEntity() == near
                            && standAim.getType() == ActionTarget.TargetType.ENTITY && standAim.getEntity() == near,
                    "1.16's task held the cow the press summoning Aqua Necklace was at (startedHolding after onClick);"
                            + " the port's charge holds " + task + ", the summoned Stand aims at " + standAim);
            for (int tick = 0; tick < 3; tick++) {
                tickStand(stand);
            }
            helper.assertTrue(!charge.isOver() && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                            && power.getStamina() == 500.0F
                            && !power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME),
                    "1.16 kept charging at the cow with the summoned Aqua Necklace, nothing paid yet: phase "
                            + charge.getPhase() + ", stamina " + power.getStamina());
            // the summon lock and the rest of the charge in the level's own ticks, the key still held
            awaitAwayEntry(helper, player, power, stand, charge, near, spawned, chunks, 0, power.getStamina());
            scheduled = true;
        } finally {
            if (!scheduled) {
                endAwayEntry(player, power, spawned, chunks);
            }
        }
    }

    // each level tick until the charge fires: 1.16's standPerform entered its task's target
    private static void awaitAwayEntry(GameTestHelper helper, FakePlayer player, StandPower power,
            AquaNecklaceEntity stand, EntityActionInstance charge, Cow near, List<LivingEntity> spawned,
            FixtureChunks chunks, int waited, float staminaBefore) {
        helper.runAfterDelay(1, () -> {
            boolean next = false;
            try {
                if (!stand.isPossessing()) {
                    helper.assertTrue(waited < AWAY_ENTRY_WAIT_TICKS && !charge.isOver(),
                            "1.16 kept the charge at the cow its press summoning Aqua Necklace was at and entered it"
                                    + " when it fired; the port's charge " + (charge.isOver() ? "ended" : "still runs")
                                    + " after " + (waited + 1) + " ticks without entering: phase " + charge.getPhase()
                                    + ", task target " + charge.getTaskTarget(helper.getLevel()));
                    awaitAwayEntry(helper, player, power, stand, charge, near, spawned, chunks, waited + 1,
                            power.getStamina());
                    next = true;
                    return;
                }
                helper.assertTrue(stand.getPossessedTarget() == near && power.getStamina() < staminaBefore
                                && power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME)
                                && power.isAbilityOnCooldown(AquaNecklaceAddon.LEAVE_ENTITY_NAME),
                        "1.16's charge entered the pressed cow, paid and set both cooldowns: possessed "
                                + stand.getPossessedTarget() + " (pressed " + near + "), stamina " + staminaBefore
                                + " -> " + power.getStamina());
                LOGGER.info("R770_AQUA_ENTER_AWAY_SUMMON_PASS entered after {} ticks", waited + 1);
            } finally {
                if (!next) {
                    endAwayEntry(player, power, spawned, chunks);
                }
            }
            helper.succeed();
        });
    }

    private static void endAwayEntry(FakePlayer player, StandPower power, List<LivingEntity> spawned,
            FixtureChunks chunks) {
        AbilityInput.keyRelease(STATE_KEY, player);
        try {
            cleanup(player, power, chunks);
        } finally {
            spawned.forEach(LivingEntity::discard);
        }
    }

    // refused with Aqua Necklace away: no Stand comes out, nothing is paid or put on cooldown
    private static void assertAwayPressRefused(GameTestHelper helper, FakePlayer player, StandPower power,
            Ability enter, ActionTarget aim, String row) {
        aimUser(player, aim);
        boolean admitted = admitted(enter, player, power);
        if (admitted) {
            AbilityInput.keyPress(STATE_KEY, enter, player, null, InputMethod.HOLD, 0.0F,
                    BufferingState.clickCanBuffer(), enter.getAbilityId());
        }
        helper.assertTrue(!admitted && !power.isSummoned() && power.getStamina() == 500.0F
                        && !power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME),
                "1.16 refused getting_into_entity pressed " + row + " with Aqua Necklace away and took back the Stand"
                        + " it summoned: admitted " + admitted + ", summoned " + power.isSummoned() + ", stamina "
                        + power.getStamina());
        AbilityInput.keyRelease(STATE_KEY, player);
    }

    // what the user's own vanilla pick met, as its client sent it
    private static void aimUser(FakePlayer player, ActionTarget target) {
        LivingComponentAction.getComponent(player).entityAim.setTarget(target);
    }

    // the charge a press at the aim starts, at the entity it takes, then let go before it fires
    private static float enterChargeLength(GameTestHelper helper, FakePlayer player, AquaNecklaceEntity stand,
            StandPower power, Ability enter, ActionTarget aim, LivingEntity taken) {
        AbilityInput.keyRelease(STATE_KEY, player);
        aimStand(stand, aim);
        helper.assertTrue(admitted(enter, player, power),
                "Setup: getting_into_entity pressed at " + aim + " was refused");
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, enter, player, null, InputMethod.HOLD, 0.0F, pressed,
                enter.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == enter
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.getTaskTarget(helper.getLevel()).getEntity() == taken,
                "Setup: getting_into_entity pressed at " + aim + " did not start charging at the skeleton: "
                        + (action != null ? action.getTaskTarget(helper.getLevel()) : null));
        float length = action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
        AbilityInput.keyRelease(STATE_KEY, player);
        tickStand(stand);
        helper.assertTrue(action.isOver() && LivingComponentAction.getCurEntityAction(stand) == null
                        && !stand.isPossessing(),
                "Setup: the charge let go early went on: phase " + action.getPhase());
        return length;
    }

    private static EntityActionInstance startEnterCharge(GameTestHelper helper, FakePlayer player,
            AquaNecklaceEntity stand, StandPower power, Ability enter, Cow host) {
        AbilityInput.keyRelease(STATE_KEY, player);
        aimStand(stand, new ActionTarget(host));
        helper.assertTrue(admitted(enter, player, power),
                "getting_into_entity pressed at a cow beside the Stand was refused");
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(STATE_KEY, enter, player, null, InputMethod.HOLD, 0.0F, pressed,
                enter.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == enter
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                "getting_into_entity did not start charging at the cow");
        for (int tick = 0; tick < 3; tick++) {
            tickStand(stand);
        }
        helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && !stand.isPossessing(),
                "1.16 kept charging while the crosshair stayed on the cow: phase " + action.getPhase());
        return action;
    }

    private static void assertEnterDropped(GameTestHelper helper, AquaNecklaceEntity stand, StandPower power,
            EntityActionInstance charge, String when) {
        helper.assertTrue(charge.isOver() && LivingComponentAction.getCurEntityAction(stand) == null
                        && !stand.isPossessing() && power.getStamina() == 500.0F
                        && !power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME),
                "1.16 tickHeldAction stopped the charge, unfired and unpaid, when " + when + ": over "
                        + charge.isOver() + ", possessed " + stand.getPossessedTarget() + ", stamina "
                        + power.getStamina() + ", cooldown "
                        + power.isAbilityOnCooldown(AquaNecklaceAddon.ENTER_ENTITY_NAME));
    }

    // what the Stand's aim met, as its user's client sent it
    private static void aimStand(AquaNecklaceEntity stand, ActionTarget target) {
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    private static void tickStand(AquaNecklaceEntity stand) {
        LivingComponentAction.getComponent(stand).tick();
    }

    // the moves and their checks are cached for the tick
    private static boolean admitted(Ability ability, FakePlayer player, StandPower power) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return AbilityInput.withConditionCheck(ability, player);
    }

    private static boolean isConditionMessage(ConditionCheck check, String key) {
        return check.getWarning() != null
                && check.getWarning().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("jojo.message.action_condition." + key);
    }

    private static String describe(ConditionCheck check) {
        return "positive " + check.isPositive() + ", continueHold " + check.shouldContinueHold() + ", message "
                + (check.getWarning() != null ? check.getWarning().getContents() : null);
    }

    private static void turnIdleUser(FakePlayer player, float viewYaw, float bodyYaw) {
        player.setYRot(viewYaw);
        player.yRotO = viewYaw;
        player.setYHeadRot(viewYaw);
        player.yHeadRotO = viewYaw;
        player.yBodyRot = bodyYaw;
        player.yBodyRotO = bodyYaw;
    }

    private static void assertIdleAt(GameTestHelper helper, FakePlayer player, AquaNecklaceEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(player);
        Vec3 actual = stand.position().subtract(player.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put an idle Aqua Necklace at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with "
                        + when + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static void cleanup(FakePlayer player, StandPower power, FixtureChunks chunks) {
        try {
            AbilityInput.keyRelease(GUARD_KEY, player);
            AquaNecklaceAddon.AQUA_NECKLACE.get().forceUnsummon(player, power);
            player.discard();
        } finally {
            chunks.release();
        }
    }
}
