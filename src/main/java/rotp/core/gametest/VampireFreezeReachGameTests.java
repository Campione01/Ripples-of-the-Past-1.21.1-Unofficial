package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismFreezeAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireFreezeReachGameTests {
    private VampireFreezeReachGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_surface_reach", timeoutTicks = 60)
    public static void visibleCowWithinDonorSurfaceReachFreezes(GameTestHelper helper) {
        start(helper, Scenario.SURFACE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_near_control", timeoutTicks = 60)
    public static void nearbyCowStillFreezesThroughRegisteredHold(GameTestHelper helper) {
        start(helper, Scenario.NEAR);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_outside_reach", timeoutTicks = 60)
    public static void visibleCowBeyondDonorSurfaceReachDoesNotFreeze(GameTestHelper helper) {
        start(helper, Scenario.OUTSIDE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_occluded_reach", timeoutTicks = 60)
    public static void occludedCowBeyondReducedSurfaceReachDoesNotFreeze(GameTestHelper helper) {
        start(helper, Scenario.OCCLUDED);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_occluded_near", timeoutTicks = 60)
    public static void nearbyOccludedCowWithinReducedSurfaceReachStillFreezes(GameTestHelper helper) {
        start(helper, Scenario.OCCLUDED_NEAR);
    }

    private enum Scenario {
        SURFACE(2.4D, false, true), NEAR(1.5D, false, true),
        OUTSIDE(3.4D, false, false), OCCLUDED(1.9D, true, false), OCCLUDED_NEAR(1.6D, true, true);

        final double separation;
        final boolean occluded;
        final boolean hit;

        Scenario(double separation, boolean occluded, boolean hit) {
            this.separation = separation;
            this.occluded = occluded;
            this.hit = hit;
        }
    }

    private static void start(GameTestHelper helper, Scenario scenario) {
        Fixture fixture = new Fixture(helper, scenario);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error error) {
            fixture.close();
            throw error;
        }
    }

    private record Geometry(double originDistance, double donorDistance, Vec3 eye, AABB targetBox,
            Vec3 aimHit, boolean lineOfSight, boolean clearAimRay) {}

    private record Before(Geometry geometry, float health, float blood) {}

    private record Attempt(float amount, UUID direct, UUID cause, LivingIncomingDamageEvent event) {}

    private record Sample(Before before, float health, float blood, int freezeDuration, int freezeAmplifier,
            int userAge, int targetAge, float phaseTick, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 29;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Scenario scenario;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Sample> samples = new ArrayList<>();
        private final List<Attempt> attempts = new ArrayList<>();
        private Player user;
        private Cow target;
        private PlayerPower power;
        private VampirismData data;
        private VampirismFreezeAbility ability;
        private EntityActionInstance action;
        private Before before;
        private RuntimeException observerFailure;
        private int userTicks;
        private int targetTicks;
        private BlockPos occluderBase;
        private boolean prepared;
        private boolean pressed;
        private boolean closed;

        private Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.scenario = scenario;
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL && !level.dimensionType().ultraWarm(),
                    "Freeze reach requires the normal non-ultrawarm GameTest world");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 6.5D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2,
                    origin.x + 2, origin.y + 4, origin.z + 5);
            helper.assertTrue(space.maxY < level.getMaxBuildHeight(), "Freeze fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Freeze fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "Freeze fixture contains another entity");
            BlockPos center = BlockPos.containing(origin);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 4; z++) {
                    putBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    putBlock(center.offset(x, 3, z), Blocks.STONE.defaultBlockState());
                }
            }

            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(level.addFreshEntity(user), "Could not add Freeze user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, user);
            user.setHealth(user.getMaxHealth());
            VampirismState.get(user).blood().setCurrent(100.0F);
            data.setBloodLevel(100.0F);
            var found = power.getAbility("vampirism_freeze");
            helper.assertTrue(found instanceof VampirismFreezeAbility
                            && found.abilityType == VampirismPowerType.VAMPIRE_FREEZE.get(),
                    "Missing vampire_freeze type at vampirism_freeze moveset entry");
            ability = (VampirismFreezeAbility) found;

            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create Freeze target");
            target.setNoGravity(true);
            target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            target.setPos(origin.x, origin.y, origin.z + scenario.separation);
            helper.assertTrue(level.addFreshEntity(target) && !target.isNoAi() && !target.isInvulnerable()
                            && target.isAlive() && !DamageUtil.isImmuneToCold(target), "Invalid vulnerable AI-enabled Cow");
            helper.assertTrue(!target.hasEffect(ModStatusEffects.FREEZE), "Cow already has FREEZE");
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " separation=" + scenario.separation
                    + " noGravity=true cowAI=true speed=0 roofAndFloor=" + blocks.size() + " difficulty=" + level.getDifficulty());
        }

        private void putBlock(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), level.getBlockState(pos));
            helper.assertTrue(level.setBlock(pos, state, 3), "Could not build Freeze fixture block");
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != user || !pressed || samples.size() >= 3) return;
                helper.assertTrue(!event.isCanceled() && before == null
                                && level.isPositionEntityTicking(user.blockPosition())
                                && level.isPositionEntityTicking(target.blockPosition()), "Freeze entities are not naturally ticking");
                requireEligible();
                Geometry geometry = geometry();
                if (samples.isEmpty() || samples.stream().noneMatch(s -> s.health < s.before.health)) {
                    requireReach(geometry);
                }
                before = new Before(geometry, target.getHealth(), blood());
                attempts.clear();
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != target || samples.size() >= 3) return;
                helper.assertTrue(before != null && event.getSource().is(ModDamageTypes.VAMPIRE_FREEZE),
                        "Cow damage was not from the observed Freeze tick");
                attempts.add(new Attempt(event.getOriginalAmount(), id(event.getSource().getDirectEntity()),
                        id(event.getSource().getEntity()), event));
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == target) targetTicks++;
                if (event.getEntity() != user) return;
                userTicks++;
                if (before == null) return;
                helper.assertTrue(LivingComponentAction.getCurEntityAction(user) == action && !action.isOver()
                                && action.getPhase() == ActionPhase.PERFORM, "Registered Freeze hold stopped or left PERFORM");
                MobEffectInstance freeze = target.getEffect(ModStatusEffects.FREEZE);
                Sample sample = new Sample(before, target.getHealth(), blood(), freeze == null ? 0 : freeze.getDuration(),
                        freeze == null ? -1 : freeze.getAmplifier(), user.tickCount, target.tickCount, action.getPhaseTick(),
                        List.copyOf(attempts));
                samples.add(sample);
                log("tick=" + samples.size() + " ages=" + sample.userAge + "/" + sample.targetAge + " phaseTick=" + sample.phaseTick
                        + " originDistance=" + before.geometry.originDistance + " donorDistance=" + before.geometry.donorDistance
                        + " health=" + before.health + "->" + sample.health + " blood=" + before.blood + "->" + sample.blood
                        + " freeze=" + sample.freezeDuration + "/" + sample.freezeAmplifier + " damageAttempts=" + sample.attempts.size());
                before = null;
            });
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void requireEligible() {
            helper.assertTrue(data.isVampireAtFullPower() && data.getCuringStage(user) == 0 && blood() > 1.0F
                            && user.getMainHandItem().isEmpty() && !user.isCreative() && !user.getAbilities().instabuild
                            && !user.isOnFire() && !target.isOnFire() && target.isAlive() && !DamageUtil.isImmuneToCold(target)
                            && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                            && ability.checkMainModLogicConditions(power).isPositive()
                            && ability.checkSpecificConditions(power).isPositive(),
                    "Freeze eligibility changed during the fixture: full=" + data.isVampireAtFullPower()
                            + " curing=" + data.getCuringStage(user) + " blood=" + blood()
                            + " emptyHand=" + user.getMainHandItem().isEmpty() + " creative=" + user.isCreative()
                            + " instabuild=" + user.getAbilities().instabuild + " userFire=" + user.isOnFire()
                            + " targetFire=" + target.isOnFire() + " targetAlive=" + target.isAlive()
                            + " coldImmune=" + DamageUtil.isImmuneToCold(target)
                            + " sky=" + level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                            + " main=" + ability.checkMainModLogicConditions(power)
                            + " specific=" + ability.checkSpecificConditions(power)
                            + " warmTicks=" + userTicks + "/" + targetTicks);
        }

        private Vec3 donorAimPoint() {
            AABB box = target.getBoundingBox();
            double eyeFraction = user.getBbHeight() == 0.0F ? 0.0D : (double) (user.getEyeHeight() / user.getBbHeight());
            return new Vec3(Mth.lerp(0.5D, box.minX, box.maxX), Mth.lerp(eyeFraction, box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
        }

        private Geometry geometry() {
            Vec3 eye = user.getEyePosition(1.0F);
            AABB box = target.getBoundingBox();
            // Independent donor002 JojoModUtil.getDistance oracle, not the current Freeze range helper.
            double donorDistance = box.contains(eye) ? 0.0D : box.clip(eye, donorAimPoint())
                    .map(hit -> eye.distanceTo(hit) - (double) (user.getBbWidth() / 2.0F)).orElse(-1.0D);
            Vec3 end = eye.add(user.getLookAngle().scale(4.0D));
            Vec3 hit = box.clip(eye, end).orElseThrow(() -> new IllegalStateException("Freeze look ray missed Cow"));
            boolean clearRay = level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user))
                    .getType() == HitResult.Type.MISS;
            return new Geometry(user.position().distanceTo(target.position()), donorDistance, eye, box, hit,
                    user.hasLineOfSight(target), clearRay);
        }

        private void requireReach(Geometry geometry) {
            helper.assertTrue(geometry.lineOfSight == !scenario.occluded && geometry.clearAimRay == !scenario.occluded
                            && geometry.donorDistance >= 0.0D
                            && LivingComponentAction.getAim(user).getTarget().getEntity() == target,
                    "Cow aim or line-of-sight discriminator changed");
            double rangeSquared = geometry.lineOfSight ? 4.0D : 1.0D;
            helper.assertTrue((geometry.donorDistance * geometry.donorDistance <= rangeSquared) == scenario.hit,
                    "Cow does not separate the expected donor reach result");
            helper.assertTrue(scenario.separation > 2.0D ? geometry.originDistance > 2.0D : geometry.originDistance < 2.0D,
                    "Cow crossed the origin-distance discriminator");
            if (scenario.occluded) {
                helper.assertTrue(geometry.donorDistance < 2.0D && (geometry.donorDistance <= 1.0D) == scenario.hit,
                        "Occluded Cow does not separate the reduced-reach boundary");
            }
        }

        private void prepareSelection() {
            helper.assertTrue(!prepared, "Freeze selection was already prepared");
            Vec3 direction = donorAimPoint().subtract(user.getEyePosition());
            user.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            user.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            user.setYHeadRot(user.getYRot());
            user.yBodyRot = user.getYRot();
            Geometry geometry = geometry();
            helper.assertTrue(geometry.lineOfSight && geometry.clearAimRay, "Initial Cow selection is obstructed");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.fromVanilla(new EntityHitResult(target, geometry.aimHit)));
            if (scenario.occluded) {
                // Retain a real visible selection, then obstruct it before the server validates held reach.
                occluderBase = user.blockPosition().south();
                BlockPos wall = occluderBase;
                putBlock(wall, Blocks.GLASS_PANE.defaultBlockState());
                putBlock(wall.above(), Blocks.GLASS_PANE.defaultBlockState());
                for (BlockPos pos : List.of(wall, wall.above())) {
                    AABB pane = level.getBlockState(pos).getCollisionShape(level, pos).bounds().move(pos);
                    helper.assertTrue(!pane.intersects(user.getBoundingBox()) && !pane.intersects(target.getBoundingBox()),
                            "Freeze occluder intersects a fixture entity");
                }
                geometry = geometry();
            }
            requireReach(geometry);
            prepared = true;
        }

        private boolean shadeReady() {
            helper.assertTrue(blocks.size() == (scenario.occluded ? 38 : 36)
                            && blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).equals(
                                    occluderBase != null && (pos.equals(occluderBase) || pos.equals(occluderBase.above()))
                                            ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.STONE.defaultBlockState())),
                    "Owned Freeze floor/roof/occluder cells changed before input: " + blocks.size());
            return !level.canSeeSky(BlockPos.containing(user.getEyePosition()));
        }

        private void press() {
            helper.assertTrue(prepared, "Freeze selection is not prepared");
            Geometry geometry = geometry();
            requireReach(geometry);
            requireEligible();
            helper.assertTrue(Math.abs(geometry.originDistance - scenario.separation) < 1.0E-4D && !target.hasEffect(ModStatusEffects.FREEZE),
                    "Freeze fixture moved or acquired an effect before input");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "Registered vampire_freeze HOLD was not admitted");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof VampirismFreezeAbility.FreezeInstance freeze
                            && freeze.ability == ability && freeze == LivingComponentAction.getCurEntityAction(user),
                    "Registered Freeze input did not install its player action");
            action = (EntityActionInstance) input.action;
            log("press ability=" + ability.getAbilityId() + " type=" + ability.abilityType.registryKey
                    + " warmTicks=" + userTicks + "/" + targetTicks + " geometry=" + geometry
                    + " userWidth=" + user.getBbWidth() + " eyeHeight=" + user.getEyeHeight());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 45,
                        (prepared && !pressed ? "Freeze shade readiness deadline" : "Freeze watchdog")
                                + " ticks=" + userTicks + "/" + targetTicks + " samples=" + samples.size()
                                + " sky=" + level.canSeeSky(BlockPos.containing(user.getEyePosition())));
                if (!pressed && userTicks >= 2 && targetTicks >= 2) {
                    if (!prepared) prepareSelection();
                    if (shadeReady()) press();
                    else log("shade-wait ticks=" + userTicks + "/" + targetTicks + " eye=" + user.getEyePosition()
                            + " sky=" + level.canSeeSky(BlockPos.containing(user.getEyePosition())) + " ownedBlocks=" + blocks.size()
                            + " blood=" + blood() + " userFire=" + user.isOnFire() + " targetFire=" + target.isOnFire());
                }
                if (samples.size() == 3) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            Sample first = samples.get(0);
            Sample last = samples.get(2);
            helper.assertTrue(last.userAge >= first.userAge + 2 && last.targetAge >= first.targetAge + 2
                            && last.phaseTick > first.phaseTick, "Freeze observation lacks three natural action ticks");
            helper.assertTrue(samples.stream().allMatch(s -> s.before.blood - s.blood >= 0.44F),
                    "Natural Survival Freeze ticks did not spend blood");
            List<Attempt> damage = samples.stream().flatMap(s -> s.attempts.stream()).toList();
            helper.assertTrue(damage.stream().allMatch(a -> a.amount > 0.0F && !a.event.isCanceled()
                            && user.getUUID().equals(a.direct) && user.getUUID().equals(a.cause)), "Freeze damage was refused or misattributed");
            log("result samples=" + samples.size() + " damageAttempts=" + damage.size() + " health=" + first.before.health
                    + "->" + last.health + " freeze=" + last.freezeDuration + "/" + last.freezeAmplifier);
            if (scenario.hit) {
                helper.assertTrue(samples.stream().anyMatch(s -> s.health < s.before.health && s.freezeDuration > 0),
                        "Visible donor-range Cow was not damaged and frozen: origin=" + first.before.geometry.originDistance
                                + " surface=" + first.before.geometry.donorDistance + " hp=" + first.before.health + "->" + last.health);
            }
            else {
                helper.assertTrue(damage.isEmpty() && samples.stream().allMatch(s -> s.health == s.before.health && s.freezeDuration == 0),
                        "Outside donor reach Cow was hit: origin=" + first.before.geometry.originDistance
                                + " surface=" + first.before.geometry.donorDistance + " visible=" + first.before.geometry.lineOfSight);
            }
        }

        private float blood() {
            return VampirismState.get(user).blood().current();
        }

        private static UUID id(Entity entity) {
            return entity == null ? null : entity.getUUID();
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Vampire Freeze reach observer failure", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("VAMPIRE-FREEZE-REACH {} {}", scenario, message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            before = null;
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
            }
            finally {
                try {
                    if (target != null) target.discard();
                    if (user != null) {
                        try {
                            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
                            LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC);
                        }
                        finally {
                            user.discard();
                        }
                    }
                }
                finally {
                    for (var entry : blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
                    boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                    log("cleanup listeners=0 restoredBlocks=" + blocks.size() + " exactRestore=" + restored);
                    if (!restored) throw new IllegalStateException("Freeze reach fixture blocks were not exactly restored");
                    blocks.clear();
                }
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
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
