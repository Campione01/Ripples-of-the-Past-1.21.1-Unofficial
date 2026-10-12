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
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandStatFormulas.BlockMiningTier;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerBlockDestroyTracker;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Serial scenes because the destruction gamerule is shared by the server level. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageBlockRestoreGameTests {
    private static final short KEY = 117;
    private static Suite active;

    private BarrageBlockRestoreGameTests() {}

    private record Spec(String id, Block block, double strength, double speed, boolean locked, boolean creative,
            boolean resolve, boolean denyRule, boolean denyEvent, boolean chariot,
            boolean immediate, int ticks, boolean rayControl, boolean manualDecay) {}

    @GameTest(template = "empty", timeoutTicks = 600, batch = "barrage_block_restore")
    public static void registeredHeldBarrageRestoresDonorBlockGate(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY production=" + sha(StandEntityBarrage.class) + " fixture="
                    + sha(BarrageBlockRestoreGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) {
            suite.close();
            throw error;
        }
    }

    @AfterBatch(batch = "barrage_block_restore")
    public static void releaseBlockRestoreBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        return List.of(
                new Spec("directional-stone-first-hit", Blocks.STONE, 15, 14, false, true, false, false, false, false, true, 1, false, false),
                new Spec("locked-stone-first-hit", Blocks.STONE, 15, 14, true, true, false, false, false, false, true, 1, false, false),
                new Spec("strength4-stone", Blocks.STONE, 4, 14, false, true, false, false, false, false, true, 1, false, false),
                new Spec("tool-hardness-cobweb-refused", Blocks.COBWEB, 4, 14, true, true, false, false, false, false, false, 4, false, false),
                new Spec("hand-hardness-reinforced", Blocks.REINFORCED_DEEPSLATE, 15, 14, false, true, false, false, false, false, true, 1, false, false),
                new Spec("strict-harvest-diamond", Blocks.DIAMOND_ORE, 8, 14, false, true, false, false, false, false, false, 40, false, false),
                new Spec("harvest-diamond-allowed", Blocks.DIAMOND_ORE, 12, 14, false, true, false, false, false, false, true, 1, false, false),
                new Spec("resolve-obsidian-refused", Blocks.OBSIDIAN, 15, 14, false, true, true, false, false, false, false, 145, false, false),
                new Spec("bedrock-refused", Blocks.BEDROCK, 15, 14, false, true, false, false, false, false, false, 4, false, false),
                new Spec("gamerule-refused", Blocks.STONE, 15, 14, false, true, false, true, false, false, false, 4, false, false),
                new Spec("event-refused", Blocks.STONE, 15, 14, false, true, false, false, true, false, false, 4, false, false),
                new Spec("creative-no-drops", Blocks.OAK_PLANKS, 15, 14, false, true, false, false, false, false, true, 1, false, false),
                new Spec("survival-drops", Blocks.OAK_PLANKS, 15, 14, false, false, false, false, false, false, true, 1, false, false),
                new Spec("nearest-ray-face", Blocks.STONE, 15, 14, false, true, false, false, false, false, true, 1, true, false),
                new Spec("rapier-stone-refused", Blocks.STONE, 15, 14, false, true, false, false, false, true, false, 4, false, false),
                new Spec("rapier-glass-allowed", Blocks.GLASS, 15, 14, false, true, false, false, false, true, true, 1, false, false),
                new Spec("strength2-netherrack-refused", Blocks.NETHERRACK, 2, 3.5, false, true, false, false, false, false, false, 4, false, false),
                new Spec("rapier-strength2-netherrack", Blocks.NETHERRACK, 2, 3.5, false, true, false, false, false, true, true, 1, false, false),
                new Spec("rapier-strength2-gamerule", Blocks.NETHERRACK, 2, 3.5, false, true, false, true, false, true, false, 4, false, false),
                new Spec("rapier-strength2-event", Blocks.NETHERRACK, 2, 3.5, false, true, false, false, true, true, false, 4, false, false),
                new Spec("rapier-manual-netherrack", Blocks.NETHERRACK, 8, 14, false, true, false, false, false, true, true, 1, false, true));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final boolean oldRule;
        final List<Spec> specs = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        int index;
        int checks;
        RuntimeException postFailure;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping barrage block suites");
            this.helper = helper;
            level = helper.getLevel();
            oldRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
            helper.testInfo.addListener(this);
            active = this;
        }

        void listen() {
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && postFailure == null && event.getEntity() == scene.stand) {
                    try { scene.post(); }
                    catch (RuntimeException error) { postFailure = error; }
                }
            };
            Consumer<LivingDestroyBlockEvent> destruction = event -> {
                if (scene != null && event.getEntity() == scene.stand && event.getPos().equals(scene.block)) {
                    scene.destroyRequests++;
                    if (scene.spec.denyEvent) event.setCanceled(true);
                }
            };
            Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
                if (scene != null && event.getLevel() == level && event.getSource() == SoundSource.BLOCKS
                        && event.getPosition().distanceToSqr(Vec3.atCenterOf(scene.block)) < 0.01) scene.blockSounds++;
            };
            Consumer<EntityJoinLevelEvent> spawn = event -> {
                if (scene != null && event.getLevel() == level && event.getEntity() instanceof ItemEntity item
                        && item.position().distanceToSqr(Vec3.atCenterOf(scene.block)) < 4) scene.drops.add(item);
            };
            listeners.add(post); listeners.add(destruction); listeners.add(sound); listeners.add(spawn);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDestroyBlockEvent.class, destruction);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, sound);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
        }

        void tick() {
            if (closed) return;
            try {
                if (postFailure != null) throw postFailure;
                if (scene != null && scene.done) {
                    scene.close();
                    scene = null;
                }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size()
                                + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Barrage block restore failed " + failures.size() + " checks; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, specs.get(index++));
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < scene.spec.ticks + 15,
                        "Native barrage did not reach its expected tick window: " + scene.spec.id);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.spec.id + " " + detail);
            if (!passed) failures.add(scene.spec.id + ": " + detail);
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
        final Spec spec;
        final ServerLevel level;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<ItemEntity> drops = new ArrayList<>();
        Player user;
        StandPower power;
        StandType type;
        StandEntity stand;
        StandEntityBarrage action;
        BlockPos block;
        BlockPos behind;
        int startedAge;
        int worldTicks;
        int performTicks;
        int positiveTicks;
        int destroyRequests;
        int blockSounds;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }

        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            Vec3 userOrigin = spec.manualDecay ? origin.add(0, 0, -4) : origin;
            Vec3 standOrigin = spec.manualDecay ? origin.add(0, 0, 4) : origin;
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, spec.creative ? GameType.CREATIVE : GameType.SURVIVAL);
            user.getAbilities().instabuild = spec.creative;
            user.setNoGravity(true);
            user.noPhysics = true;
            user.moveTo(userOrigin.x, userOrigin.y, userOrigin.z, 0, 0);
            premise(level.addFreshEntity(user), "user added");
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(spec.chariot ? "silver_chariot" : "star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Stand granted");
            power.setResolveLevel(2);
            if (spec.resolve) user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
            premise(type.summon(user, power), "registered Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.moveTo(standOrigin.x, standOrigin.y, standOrigin.z, 0, 0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(spec.strength);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(spec.speed);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            if (spec.manualDecay) {
                Ability manual = power.getAbility("manual_control");
                var control = power.updateAvailableMoves().getContextVariationContainer(manual);
                premise(control != null && AbilityInput.withConditionCheck(control, user, InputMethod.CLICK),
                        "registered manual-control CLICK admission");
                BufferingState controlInput = BufferingState.clickOnly();
                AbilityInput.keyPress((short) (KEY + 1), control.ability, user, null, InputMethod.CLICK, 0,
                        controlInput, manual.getAbilityId());
                premise(controlInput.isActionSuccess && stand.isManuallyControlled()
                        && EntityComponentController.getControlTarget(user) == stand,
                        "production manual-control ownership");
                stand.updateStrengthMultipliers();
            }
            assertResolvedStats();
            premise(StandStatFormulas.getBarrageHitsPerSecond(stand.getAttackSpeed()) > 0, "positive native barrage cadence");
            if (spec.chariot) premise(JojoMod.resLoc("silver_chariot").equals(stand.getStandType())
                    && stand.isSilverChariotRapierVisible(), "actual Silver Chariot type and visible rapier");
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = spec.locked;
            block = BlockPos.containing(standOrigin.add(0, 1, 2));
            place(block, spec.block.defaultBlockState());
            if (spec.block == Blocks.NETHERRACK) {
                BlockState state = level.getBlockState(block);
                premise(state.requiresCorrectToolForDrops() && Math.abs(state.getDestroySpeed(level, block) - 0.4F) < 0.0001
                        && new BlockMiningTier.VanillaTierWrapper(Tiers.WOOD).canMine(state)
                        && !BlockMiningTier.EMPTY_ARMS.canMine(state), "native tool-required hardness0.4 WOOD/EMPTY_ARMS premises");
            }
            if (spec.block == Blocks.COBWEB) premise(level.getBlockState(block).requiresCorrectToolForDrops()
                    && level.getBlockState(block).getDestroySpeed(level, block) == 4, "tool-required hardness4 premise");
            if (spec.block == Blocks.REINFORCED_DEEPSLATE) premise(!level.getBlockState(block).requiresCorrectToolForDrops()
                    && level.getBlockState(block).getDestroySpeed(level, block) == 55, "hand-mineable hardness55 premise");
            if (spec.rayControl) {
                behind = block.south();
                place(behind, Blocks.OAK_PLANKS.defaultBlockState());
            }
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(!spec.denyRule, level.getServer());
            lookAt(Vec3.atCenterOf(block));
            ActionTarget target = new ActionTarget(block, Direction.NORTH);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
            String name = spec.chariot ? "melee_barrage" : "barrage";
            Ability base = power.getAbility(name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD), "real hold admission");
            BufferingState buffering = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, buffering, base.getAbilityId());
            premise(buffering.isActionSuccess && held != null && held.action instanceof StandEntityBarrage, "native registered hold");
            action = (StandEntityBarrage) held.action;
            premise(action == stand.getCurStandAction() && action.getPhase() == ActionPhase.PERFORM,
                    "native PERFORM identity");
            startedAge = stand.tickCount;
            log("START " + spec.id + " ability=" + selected.ability.getAbilityId() + " action=" + action.id
                    + " strength=" + stand.getAttackDamage() + " speed=" + stand.getAttackSpeed()
                    + " duration=" + action.phasesLength.getFloat(ActionPhase.PERFORM) + " block=" + block);
        }

        void post() {
            if (done || closed) return;
            premise(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
                    "held action changed before the observation window");
            assertResolvedStats();
            if (spec.chariot) premise(JojoMod.resLoc("silver_chariot").equals(stand.getStandType())
                    && stand.isSilverChariotRapierVisible(), "native tick retains actual type and visible rapier");
            performTicks++;
            if (action.hitsThisTick > 0) {
                positiveTicks++;
                if (positiveTicks == 1) {
                    ActionTarget hit = action.punchedTarget;
                    suite.check(hit != null && hit.getType() == ActionTarget.TargetType.BLOCK && hit.getBlockPos().equals(block),
                            "first positive production target=" + hit + " hits=" + action.hitsThisTick + " phase=" + action.getPhaseTick());
                    suite.check(level.getBlockState(block).isAir() == spec.immediate,
                            "first positive immediate=" + spec.immediate + " actualAir=" + level.getBlockState(block).isAir());
                    if (!spec.immediate) suite.check(ServerBlockDestroyTracker.getBlockDestroyProgress(level, block) == 0,
                            "first refused tick has no mining progress");
                    if (spec.block == Blocks.NETHERRACK && spec.immediate) suite.check(
                            ServerBlockDestroyTracker.getBlockDestroyProgress(level, block) == 0,
                            "first rapier hit has no accumulated mining progress");
                    if (spec.rayControl) {
                        suite.check(hit != null && hit.getFace() == Direction.NORTH && level.getBlockState(behind).is(Blocks.OAK_PLANKS),
                                "nearest block NORTH face and untouched block behind");
                    }
                }
            }
            if (performTicks >= spec.ticks && positiveTicks > 0) {
                if (!spec.immediate) {
                    suite.check(level.getBlockState(block).is(spec.block), "refused block remains after nativeTicks=" + performTicks);
                    suite.check(ServerBlockDestroyTracker.getBlockDestroyProgress(level, block) == 0,
                            "refused block has no mining progress");
                }
                if (spec.denyRule || spec.denyEvent) suite.check(blockSounds == 0, "protected block sounds=" + blockSounds);
                if (spec.denyEvent) suite.check(destroyRequests > 0, "canceled real destruction requests=" + destroyRequests);
                if (spec.id.equals("creative-no-drops")) suite.check(drops.isEmpty(), "Creative drops=" + drops.size());
                if (spec.id.equals("survival-drops")) suite.check(drops.stream().anyMatch(item -> item.getItem().is(Blocks.OAK_PLANKS.asItem())),
                        "Survival native drop count=" + drops.size());
                log("END " + spec.id + " nativeTicks=" + performTicks + " positiveTicks=" + positiveTicks
                        + " destroyRequests=" + destroyRequests + " sounds=" + blockSounds + " drops=" + drops.size());
                done = true;
            }
        }

        void lookAt(Vec3 target) {
            Vec3 delta = target.subtract(spec.manualDecay ? stand.getEyePosition() : user.getEyePosition());
            float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            user.setYRot(yaw); user.setYHeadRot(yaw); user.setXRot(pitch);
            user.yRotO = yaw; user.xRotO = pitch;
            stand.setYRot(yaw); stand.setYHeadRot(yaw); stand.setXRot(pitch);
        }

        void place(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, spec.id + " premise: " + detail); }

        void assertResolvedStats() {
            if (spec.manualDecay) {
                double distance = MathUtil.getAABBDistance(stand.getBoundingBox(), user.getBoundingBox());
                double efficiency = stand.getStandEfficiency();
                premise(stand.isManuallyControlled() && EntityComponentController.getControlTarget(user) == stand
                        && distance > stand.getEffectiveRange() && distance < stand.getMaxRange(),
                        "legal native manual distance=" + distance);
                premise(stand.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8 && stand.getAttributeValue(Attributes.ATTACK_SPEED) == 14
                        && efficiency >= 0.25 && efficiency < 0.5
                        && Math.abs(stand.getAttackDamage() - 8 * efficiency) < 0.0001
                        && Math.abs(stand.getAttackSpeed() - 14 * efficiency) < 0.0001
                        && stand.getAttackDamage() >= 2 && stand.getAttackDamage() < 4,
                        "native range-derived getters strength=" + stand.getAttackDamage() + " speed=" + stand.getAttackSpeed());
            }
            else premise(Math.abs(stand.getAttackDamage() - spec.strength) < 0.0001
                    && Math.abs(stand.getAttackSpeed() - spec.speed) < 0.0001,
                    "resolved getters strength=" + stand.getAttackDamage() + " speed=" + stand.getAttackSpeed());
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (user != null) AbilityInput.keyRelease(KEY, user);
            if (user != null) AbilityInput.keyRelease((short) (KEY + 1), user);
            for (ItemEntity item : drops) item.discard();
            for (var entry : blocks.entrySet()) {
                BlockState current = level.getBlockState(entry.getKey());
                if (!current.isAir()) ServerBlockDestroyTracker.addBlockDestroyProgress(level, null, entry.getKey(), current, -1);
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
            if (user != null && user.hasData(ModDataAttachmentTypes.CONTROLLER))
                user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
            if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (user != null) user.discard();
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(suite.oldRule, level.getServer());
        }
    }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }

    private static void log(String message) { JojoMod.getLogger().info("B-S11-06-R1 " + message); }
}
