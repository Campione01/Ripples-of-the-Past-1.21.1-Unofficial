package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.impl.powers.zombie.ZombiePowerType;
import rotp.core.impl.powers.zombie.abilities.ZombieDisguiseAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieDisguiseCompletionGameTests {
    private ZombieDisguiseCompletionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "zombie_disguise_complete60", timeoutTicks = 160)
    public static void registeredDisguiseCompletesOn60thNaturalHoldTick(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "zombie_disguise_release59", timeoutTicks = 160)
    public static void releaseAfter59NaturalDisguiseTicksDoesNotToggle(GameTestHelper helper) {
        start(helper, true);
    }

    private record Frame(int age, long time, ActionPhase phase, float counter, boolean ownAction,
            boolean over, boolean keyHeld, long generation, boolean disguise, float energy,
            float health, Vec3 position, boolean onGround) {}
    private record Row(int number, Frame before, Frame after) {}

    private static void start(GameTestHelper helper, boolean release59) {
        Fixture fixture = new Fixture(helper, release59);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 41;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean release59;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private ZombieData data;
        private ZombieDisguiseAbility ability;
        private EntityActionInstance action;
        private HeldInputEntry input;
        private Frame before, releasedBefore, releasedAfter;
        private AABB room;
        private ChunkPos chunk;
        private int posts, grantPosts, lastAge, preAge;
        private long preTime, releasedGeneration;
        private boolean granted, pressed, released, componentOpen, done, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, boolean release59) {
            this.helper = helper; this.level = helper.getLevel(); this.release59 = release59;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "ZOMBIE-DISGUISE-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "ZOMBIE-DISGUISE-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("ZOMBIE-DISGUISE {} {}", release59 ? "RELEASE59" : "COMPLETE60", message); }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL, "ordinary Normal difficulty required");
            BlockPos origin = helper.absolutePos(BlockPos.ZERO); chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 32;
            room = new AABB(x + 5, y - 1, z + 5, x + 12, y + 4, z + 12);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "owned room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "owned room not air");
            }
            for (int dx = 6; dx <= 10; dx++) for (int dz = 6; dz <= 10; dz++) {
                put(new BlockPos(x + dx, y - 1, z + dz)); put(new BlockPos(x + dx, y + 3, z + dz));
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.moveTo(x + 8.5D, y + 0.2D, z + 8.5D, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            premise(user.getHealth() == 20F && user.getMaxHealth() == 20F
                    && user.getAttributeBaseValue(Attributes.MAX_HEALTH) == 20D && level.addFreshEntity(user), "fresh20HP player admission");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(!power.hasPower() && !PowerClass.STAND.attachGet(user).hasPower(), "fresh actor has an existing power");
            lastAge = user.tickCount; observeEvents();
            log("setup user=" + user.getUUID() + " position=" + user.position() + " gravity=ordinary cells=" + blocks.size());
        }
        private void put(BlockPos pos) {
            blocks.put(pos.immutable(), level.getBlockState(pos));
            premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned floor/roof placement failed");
        }

        private void observeEvents() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(!event.isCanceled() && user.tickCount == lastAge + 1, "missing consecutive natural actor Pre");
                preAge = user.tickCount; preTime = level.getGameTime(); requireActor();
            });
            Consumer<EntityTickEvent.Post> opening = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(!componentOpen && preAge == user.tickCount && preTime == level.getGameTime(), "unpaired natural component bracket");
                componentOpen = true;
                if (pressed && !released && action != null && !action.isOver())
                    premise(heldEntry() == input, "owned HOLD generation disappeared before completion or selected release");
                if (pressed) before = frame();
            });
            Consumer<EntityTickEvent.Post> closing = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(componentOpen && preAge == user.tickCount && preTime == level.getGameTime(), "component Post lost its natural bracket");
                if (pressed) {
                    premise(before != null && before.age == user.tickCount && before.time == level.getGameTime(), "hold frame has no real component Pre");
                    Row row = new Row(rows.size() + 1, before, frame()); rows.add(row);
                    log("row=" + row.number + " before=" + row.before + " after=" + row.after);
                    if (release59 && row.number == 59) {
                        releasedBefore = frame();
                        releasedGeneration = AbilityInput.keyReleaseAndGetGeneration(KEY, user);
                        releasedAfter = frame(); released = true;
                        log("actual-release afterPost=59 generation=" + releasedGeneration
                                + " before=" + releasedBefore + " after=" + releasedAfter);
                    }
                    if (row.number == 61) done = true;
                    premise(row.number <= 61, "observation exceeded bounded61 Posts");
                    before = null;
                }
                lastAge = user.tickCount; posts++; componentOpen = false;
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() == user) premise(false, "unexpected actor damage in the completion-only scene");
            });
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(opening, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(closing, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add(damage, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
        }

        private void requireActor() {
            AABB box = user.getBoundingBox();
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(box.minX >= room.minX && box.maxX <= room.maxX && box.minY >= room.minY && box.maxY <= room.maxY
                    && box.minZ >= room.minZ && box.maxZ <= room.maxZ && new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "actor left the owned ready room");
            premise(user.isAlive() && !user.isRemoved() && user.getHealth() == 20F
                    && user.getAttributeBaseValue(Attributes.MAX_HEALTH) == 20D && !user.isCreative() && !user.isSpectator()
                    && !user.isInvulnerable() && !user.isNoGravity() && !user.isOnFire() && !ModStatusEffects.isStunned(user)
                    && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                    && !PowerClass.STAND.attachGet(user).hasPower(), "ordinary living actor state changed");
            if (granted) premise(power.getPowerType() == ZombiePowerType.ZOMBIE.get()
                    && PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElse(null) == data
                    && data.getEnergy() >= 0F && data.getEnergy() <= data.getMaxEnergy(user)
                    && user.onGround() && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                    && blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)), "Zombie/resource/support state changed");
        }
        private Frame frame() {
            EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
            HeldInputEntry held = heldEntry();
            premise(current == null || current == action, "another action replaced the owned charge");
            premise(held == null || held == input, "another input generation replaced the owned key");
            return new Frame(user.tickCount, level.getGameTime(), current == null ? null : current.getPhase(),
                    current == null ? Float.NaN : current.getPhaseTick(), current != null && current == action,
                    action != null && action.isOver(), held != null && held == input, held == null ? 0 : held.generation,
                    data.isDisguiseEnabled(), data.getEnergy(), user.getHealth(), user.position(), user.onGround());
        }
        private HeldInputEntry heldEntry() {
            return user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).map(state -> state.heldKeys.get(KEY)).orElse(null);
        }

        private void grant() {
            premise(power.trySetPowerType(ModPlayerPowers.ZOMBIE.get()), "real Zombie grant failed");
            data = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElseThrow();
            premise(!data.isDisguiseEnabled() && data.getEnergy() > 0F && user.getHealth() == 20F, "default Zombie bootstrap changed");
            var found = power.getAbility("zombie_disguise");
            premise(found instanceof ZombieDisguiseAbility && found.abilityType == ZombiePowerType.ZOMBIE_DISGUISE.get(), "registered Disguise absent");
            ability = (ZombieDisguiseAbility) found;
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            granted = true; grantPosts = posts;
            log("grant type=" + power.getPowerType().getId() + " energy=" + data.getEnergy()
                    + " HP=" + user.getHealth() + "/" + user.getMaxHealth() + " disguise=" + data.isDisguiseEnabled());
        }
        private void press() {
            requireActor();
            premise(!data.isDisguiseEnabled() && LivingComponentAction.getCurEntityAction(user) == null && heldEntry() == null,
                    "fresh input already has disguise/action/key state");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "registered HOLD rejected ordinary admission");
            float energy = data.getEnergy(); pressed = true;
            input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD, 0F, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof ZombieDisguiseAbility.DisguiseInstance, "HOLD did not install the concrete registered producer");
            action = (EntityActionInstance) input.action;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.ability == ability && action.getPowerUser() == user
                    && input.generation > 0 && heldEntry() == input && action.getPhase() == ActionPhase.WINDUP
                    && action.getCurPhaseLength() == 60F && !data.isDisguiseEnabled(), "actual input/action admission is not a fresh60-tick charge");
            log("entry skipArgument=0 frame=" + frame() + " phaseLength=" + action.getCurPhaseLength()
                    + " publicKeyHeld=" + AbilityInput.isHeldByKey(user, action) + " classifiedHeld=" + ability.isActionHeld(action)
                    + " energy=" + energy + "->" + data.getEnergy());
        }

        private void validate() {
            premise(rows.size() == 61 && rows.get(58).number == 59 && rows.get(59).number == 60 && rows.get(60).number == 61,
                    "missing natural boundary observations");
            int transitions = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                premise(row.before.age == row.after.age && row.before.time == row.after.time
                        && (i == 0 || row.before.age == rows.get(i - 1).after.age + 1), "nonconsecutive natural boundary rows");
                if (row.before.disguise != row.after.disguise) transitions++;
            }
            log("BOUNDARY rows59/60/61=" + rows.subList(58, 61) + " transitions=" + transitions + " released=" + released);
            for (int i = 0; i < 59; i++) oracle(!rows.get(i).after.disguise, "Disguise toggled before60 natural hold ticks");
            if (release59) {
                oracle(released && releasedGeneration == input.generation && releasedBefore != null && releasedAfter != null
                        && releasedBefore.ownAction && !releasedBefore.over && releasedBefore.keyHeld
                        && releasedBefore.generation == input.generation && releasedBefore.phase == ActionPhase.WINDUP
                        && !releasedBefore.disguise && !releasedAfter.disguise && !releasedAfter.keyHeld && releasedAfter.over
                        && releasedBefore.health == releasedAfter.health && releasedBefore.energy == releasedAfter.energy,
                        "real release59 did not cancel the uncompleted charge without a toggle/resource change");
                oracle(transitions == 0 && !rows.get(59).after.disguise && !rows.get(60).after.disguise
                        && !rows.get(59).after.ownAction && !rows.get(60).after.ownAction
                        && !rows.get(59).after.keyHeld && !rows.get(60).after.keyHeld,
                        "release59 produced disguise or retained/replaced its charge during two natural settle ticks");
            }
            else {
                oracle(rows.get(59).after.disguise, "60th natural hold Post did not complete the donor disguise toggle");
                oracle(rows.get(60).after.disguise && transitions == 1
                        && rows.get(60).after.over && !rows.get(60).after.ownAction && action.isOver()
                        && LivingComponentAction.getCurEntityAction(user) == null,
                        "completion did not retain its single observed toggle and naturally clear by Post61");
            }
            log("RESULT qualified=true naturalRows=61 observedTransitions=" + transitions + " native=false");
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 140, "finite readiness/charge watchdog");
                if (!granted && posts >= 2 && user.onGround() && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                        && blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE))) grant();
                else if (granted && !pressed && posts >= grantPosts + 3) press();
                if (done) { validate(); close(); helper.succeed(); return; }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable operation) {
            if (closed || done || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (user != null && user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(state -> !state.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned input remains held"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (user != null) user.getInventory().clearContent(); }, failures);
            cleanup(() -> { if (user != null && !user.isRemoved()) user.discard(); }, failures);
            for (var entry : blocks.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean gone = user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null;
                log("cleanup cells=" + blocks.size() + " exactRestore=" + restored + " actorGone=" + gone + " listeners=0");
                if (!restored || !gone) throw new IllegalStateException("Zombie disguise cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Zombie disguise cleanup failed");
                failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
