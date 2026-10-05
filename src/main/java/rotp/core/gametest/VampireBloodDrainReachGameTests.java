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
import net.minecraft.world.effect.MobEffects;
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
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypeTags;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireBloodDrainReachGameTests {
    private VampireBloodDrainReachGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_drain_surface_reach", timeoutTicks = 60)
    public static void visibleCowWithinDonorSurfaceReachDrains(GameTestHelper helper) {
        start(helper, Scenario.SURFACE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_drain_near_control", timeoutTicks = 60)
    public static void nearbyCowStillDrainsThroughRegisteredHold(GameTestHelper helper) {
        start(helper, Scenario.NEAR);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_drain_retained_aim", timeoutTicks = 60)
    public static void retainedCowAimBeyondOccludedSurfaceReachDoesNotDrain(GameTestHelper helper) {
        start(helper, Scenario.OCCLUDED_RETAINED);
    }

    private enum Scenario {
        SURFACE(2.4D, false, true), NEAR(1.5D, false, true), OCCLUDED_RETAINED(1.9D, true, false);

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
            fixture.closeAfterFailure(error);
            throw error;
        }
    }

    private record Geometry(double originDistance, double donorDistance, Vec3 eye, AABB targetBox,
            Vec3 aimHit, boolean lineOfSight, boolean clearAimRay) {}

    private record Before(Geometry geometry, float health, float blood) {}

    private record Attempt(float amount, UUID direct, UUID cause, LivingIncomingDamageEvent event) {}

    private record EffectSample(int duration, int amplifier) {}

    private record Sample(Before before, float health, float blood, List<EffectSample> effects,
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
        private VampirismBloodDrainAbility ability;
        private EntityActionInstance action;
        private Before before;
        private RuntimeException observerFailure;
        private int userTicks;
        private int targetTicks;
        private boolean pressed;
        private boolean closed;

        private Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.scenario = scenario;
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL && !level.dimensionType().ultraWarm(),
                    "Drain reach requires the normal non-ultrawarm GameTest world");
            helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).bloodDrainMultiplier.get()
                            .equals(List.of(0.0D, 1.0D, 1.75D, 2.5D)),
                    "Drain reach requires the unchanged default difficulty multipliers");
            helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).bloodTickDown.get()
                            .equals(List.of(0.13889D, 0.00278D, 0.00278D, 0.0D)),
                    "Drain reach requires the unchanged default passive blood drain");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 6.5D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2,
                    origin.x + 2, origin.y + 4, origin.z + 5);
            helper.assertTrue(space.maxY < level.getMaxBuildHeight(), "Drain fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Drain fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "Drain fixture contains another entity");
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
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(level.addFreshEntity(user), "Could not add Drain user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, user);
            user.setHealth(user.getMaxHealth());
            VampirismState.get(user).blood().setCurrent(100.0F);
            data.setBloodLevel(100.0F);
            var found = power.getAbility("vampirism_blood_drain");
            helper.assertTrue(found instanceof VampirismBloodDrainAbility
                            && found.abilityType == VampirismPowerType.VAMPIRE_BLOOD_DRAIN.get(),
                    "Missing vampire_blood_drain type at vampirism_blood_drain moveset entry");
            ability = (VampirismBloodDrainAbility) found;

            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create Drain target");
            target.setNoGravity(true);
            target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            target.setPos(origin.x, origin.y, origin.z + scenario.separation);
            helper.assertTrue(level.addFreshEntity(target) && !target.isNoAi() && !target.isInvulnerable()
                            && target.isAlive() && target.getHealth() == target.getMaxHealth(),
                    "Invalid vulnerable default-health AI-enabled Cow");
            helper.assertTrue(effects().stream().allMatch(effect -> effect.duration == 0),
                    "Cow already has a Blood Drain effect");
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " separation=" + scenario.separation
                    + " noGravity=true cowAI=true speed=0 roofAndFloor=" + blocks.size() + " difficulty=" + level.getDifficulty());
        }

        private void putBlock(BlockPos pos, BlockState state) {
            BlockPos owned = pos.immutable();
            helper.assertTrue(!blocks.containsKey(owned), "Drain fixture tried to overwrite its own saved block");
            blocks.put(owned, level.getBlockState(owned));
            helper.assertTrue(level.setBlock(owned, state, 3), "Could not build Drain fixture block");
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != user || !pressed || samples.size() >= 3) return;
                helper.assertTrue(!event.isCanceled() && before == null
                                && level.isPositionEntityTicking(user.blockPosition())
                                && level.isPositionEntityTicking(target.blockPosition()),
                        "Drain entities are not naturally ticking");
                requireStatePremises();
                Geometry geometry = geometry();
                if (samples.stream().noneMatch(sample -> sample.health < sample.before.health)) {
                    requireReachOracle(geometry, scenario.occluded, scenario.hit);
                }
                before = new Before(geometry, target.getHealth(), blood());
                attempts.clear();
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() != target || samples.size() >= 3) return;
                helper.assertTrue(before != null && event.getSource().is(ModDamageTypes.BLOOD_DRAIN),
                        "Cow damage was not from the observed natural Drain tick");
                attempts.add(new Attempt(event.getOriginalAmount(), id(event.getSource().getDirectEntity()),
                        id(event.getSource().getEntity()), event));
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == target) targetTicks++;
                if (event.getEntity() != user) return;
                userTicks++;
                if (before == null) return;
                helper.assertTrue(LivingComponentAction.getCurEntityAction(user) == action && !action.isOver()
                                && action.getPhase() == ActionPhase.PERFORM,
                        "Registered Drain hold stopped or left PERFORM");
                helper.assertTrue(Math.abs(data.getBloodLevel() - blood()) < 1.0E-4F,
                        "Drain blood state and VampirismData diverged");
                Sample sample = new Sample(before, target.getHealth(), blood(), effects(),
                        user.tickCount, target.tickCount, action.getPhaseTick(), List.copyOf(attempts));
                samples.add(sample);
                log("tick=" + samples.size() + " ages=" + sample.userAge + "/" + sample.targetAge + " phaseTick=" + sample.phaseTick
                        + " originDistance=" + before.geometry.originDistance + " donorDistance=" + before.geometry.donorDistance
                        + " visible=" + before.geometry.lineOfSight + " health=" + before.health + "->" + sample.health
                        + " blood=" + before.blood + "->" + sample.blood + " effects=" + sample.effects
                        + " damageAttempts=" + sample.attempts.size());
                before = null;
            });
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void requireStatePremises() {
            boolean drainable = !target.getType().is(ModEntityTypeTags.VAMPIRE_CANNOT_DRAIN)
                    && (target.getType().is(ModEntityTypeTags.VAMPIRE_CAN_DRAIN)
                            || JojoDefinitions.canBleed(target) && !JojoDefinitions.isUndeadOrVampiric(target));
            // Do not use checkSpecificConditions as a premise: it contains the disputed reach gate.
            helper.assertTrue(power.canUsePower() && data.isVampireAtFullPower() && !data.isBeingCured() && data.getCuringStage(user) == 0
                            && !data.isAbilityOnCooldown(ability.name()) && blood() > 1.0F && blood() < 200.0F
                            && Math.abs(data.getBloodLevel() - blood()) < 1.0E-4F
                            && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                            && !user.isCreative() && !user.getAbilities().instabuild && user.isAlive()
                            && user.getHealth() == user.getMaxHealth() && !user.isOnFire() && !target.isOnFire()
                            && target.isAlive() && !target.isInvulnerable() && drainable
                            && level.getDifficulty() == Difficulty.NORMAL
                            && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                            && ability.checkMainModLogicConditions(power).isPositive(),
                    "Drain state premise failed: full=" + data.isVampireAtFullPower() + " curing=" + data.getCuringStage(user)
                            + " blood=" + blood() + "/" + data.getBloodLevel() + " drainable=" + drainable
                            + " emptyMain=" + user.getMainHandItem().isEmpty() + " creative=" + user.isCreative()
                            + " userFire=" + user.isOnFire() + " targetFire=" + target.isOnFire()
                            + " targetAlive=" + target.isAlive() + " sky=" + level.canSeeSky(BlockPos.containing(user.getEyePosition()))
                            + " main=" + ability.checkMainModLogicConditions(power) + " warmTicks=" + userTicks + "/" + targetTicks);
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
            // Independent donor002 JojoModUtil.getDistance oracle, not a production reach helper.
            double donorDistance = box.contains(eye) ? 0.0D : box.clip(eye, donorAimPoint())
                    .map(hit -> eye.distanceTo(hit) - (double) (user.getBbWidth() / 2.0F)).orElse(-1.0D);
            Vec3 hit = box.clip(eye, eye.add(user.getLookAngle().scale(4.0D)))
                    .orElseThrow(() -> new IllegalStateException("Drain look ray missed Cow"));
            boolean clearRay = level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user))
                    .getType() == HitResult.Type.MISS;
            return new Geometry(user.position().distanceTo(target.position()), donorDistance, eye, box, hit,
                    user.hasLineOfSight(target), clearRay);
        }

        private void requireReachOracle(Geometry geometry, boolean occluded, boolean expectedHit) {
            helper.assertTrue(geometry.lineOfSight == !occluded && geometry.clearAimRay == !occluded
                            && geometry.donorDistance >= 0.0D
                            && LivingComponentAction.getAim(user).getTarget().getEntity() == target,
                    "Cow aim or line-of-sight discriminator changed");
            double rangeSquared = geometry.lineOfSight ? 4.0D : 1.0D;
            helper.assertTrue((geometry.donorDistance * geometry.donorDistance <= rangeSquared) == expectedHit,
                    "Cow does not separate the expected donor reach result");
            helper.assertTrue(scenario.separation > 2.0D ? geometry.originDistance > 2.0D : geometry.originDistance < 2.0D,
                    "Cow crossed the origin-distance discriminator");
            if (occluded) {
                helper.assertTrue(geometry.donorDistance > 1.0D && geometry.donorDistance < 2.0D,
                        "Retained Cow does not separate reduced reach1 from visible reach2");
            }
        }

        private void press() {
            Vec3 direction = donorAimPoint().subtract(user.getEyePosition());
            user.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            user.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            user.setYHeadRot(user.getYRot());
            user.yBodyRot = user.getYRot();
            Geometry initial = geometry();
            helper.assertTrue(initial.lineOfSight && initial.clearAimRay, "Initial Cow selection is obstructed");
            LivingComponentAction.getComponent(user).entityAim.setTarget(
                    ActionTarget.fromVanilla(new EntityHitResult(target, initial.aimHit)));
            requireReachOracle(initial, false, true);
            requireStatePremises();
            helper.assertTrue(Math.abs(initial.originDistance - scenario.separation) < 1.0E-4D
                            && effects().stream().allMatch(effect -> effect.duration == 0),
                    "Drain fixture moved or acquired an effect before input");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            boolean admitted = AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD);
            log("admission allowed=" + admitted + " specific=" + ability.checkSpecificConditions(power) + " initial=" + initial);
            helper.assertTrue(admitted,
                    "Registered vampire_blood_drain HOLD rejected donor-visible reach after valid state premises: origin="
                            + initial.originDistance + " donorSurface=" + initial.donorDistance);
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof VampirismBloodDrainAbility.BloodDrainInstance drain
                            && drain.ability == ability && drain == LivingComponentAction.getCurEntityAction(user),
                    "Registered Drain input did not install its player action");
            action = (EntityActionInstance) input.action;
            helper.assertTrue(target.getHealth() == target.getMaxHealth() && samples.isEmpty(),
                    "Drain action damaged the target before a natural observation tick");
            if (scenario.occluded) {
                // Obstruct an already admitted visible HOLD, retaining its real public aim without a new packet.
                BlockPos wall = user.blockPosition().south();
                putBlock(wall, Blocks.GLASS_PANE.defaultBlockState());
                putBlock(wall.above(), Blocks.GLASS_PANE.defaultBlockState());
                for (BlockPos pos : List.of(wall, wall.above())) {
                    AABB pane = level.getBlockState(pos).getCollisionShape(level, pos).bounds().move(pos);
                    helper.assertTrue(!pane.intersects(user.getBoundingBox()) && !pane.intersects(target.getBoundingBox()),
                            "Drain occluder intersects a fixture entity");
                }
                requireReachOracle(geometry(), true, false);
            }
            log("press ability=" + ability.getAbilityId() + " type=" + ability.abilityType.registryKey
                    + " warmTicks=" + userTicks + "/" + targetTicks + " geometry=" + geometry()
                    + " userWidth=" + user.getBbWidth() + " eyeHeight=" + user.getEyeHeight());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 45,
                        "Drain watchdog ticks=" + userTicks + "/" + targetTicks + " samples=" + samples.size());
                if (!pressed && userTicks >= 2 && targetTicks >= 2) {
                    helper.assertTrue(blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                            "Owned Drain shade or floor changed before input");
                    BlockPos eye = BlockPos.containing(user.getEyePosition());
                    boolean shaded = !level.canSeeSky(eye);
                    log("shade-readiness ticks=" + userTicks + "/" + targetTicks + " eye=" + eye
                            + " shaded=" + shaded + " roof=" + level.getBlockState(user.blockPosition().above(3)));
                    if (shaded) press();
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
                closeAfterFailure(error);
                throw error;
            }
        }

        private void validate() {
            Sample first = samples.get(0);
            Sample last = samples.get(2);
            helper.assertTrue(last.userAge >= first.userAge + 2 && last.targetAge >= first.targetAge + 2
                            && last.phaseTick > first.phaseTick,
                    "Drain observation lacks three natural action ticks");
            List<Attempt> damage = samples.stream().flatMap(sample -> sample.attempts.stream()).toList();
            helper.assertTrue(damage.stream().allMatch(attempt -> Math.abs(attempt.amount - 2.0F) < 1.0E-5F
                            && !attempt.event.isCanceled() && user.getUUID().equals(attempt.direct)
                            && user.getUUID().equals(attempt.cause)),
                    "Drain damage was canceled, unexpected or misattributed");
            log("result samples=" + samples.size() + " damageAttempts=" + damage.size() + " health=" + first.before.health
                    + "->" + last.health + " blood=" + first.before.blood + "->" + last.blood + " effects=" + last.effects);
            if (scenario.hit) {
                helper.assertTrue(!damage.isEmpty() && Math.abs(first.before.health - last.health - 2.0F) < 1.0E-5F
                                && samples.stream().anyMatch(sample -> sample.health < sample.before.health
                                        && sample.blood > sample.before.blood + 1.0F
                                        && sample.effects.stream().allMatch(effect -> effect.duration > 0 && effect.amplifier == 1)),
                        "Donor-visible Cow was not actually drained through natural HOLD: origin=" + first.before.geometry.originDistance
                                + " donorSurface=" + first.before.geometry.donorDistance + " health=" + first.before.health + "->" + last.health);
            }
            else {
                helper.assertTrue(damage.isEmpty() && samples.stream().allMatch(sample -> sample.health == sample.before.health
                                && sample.blood <= sample.before.blood + 0.01F
                                && sample.effects.stream().allMatch(effect -> effect.duration == 0)),
                        "Retained Cow beyond reduced donor surface reach was drained: origin=" + first.before.geometry.originDistance
                                + " donorSurface=" + first.before.geometry.donorDistance);
            }
        }

        private List<EffectSample> effects() {
            return List.of(effect(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN)),
                    effect(target.getEffect(MobEffects.DIG_SLOWDOWN)), effect(target.getEffect(MobEffects.WEAKNESS)),
                    effect(target.getEffect(MobEffects.CONFUSION)));
        }

        private static EffectSample effect(MobEffectInstance effect) {
            return effect == null ? new EffectSample(0, -1) : new EffectSample(effect.getDuration(), effect.getAmplifier());
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
                JojoMod.LOGGER.error("Vampire Blood Drain reach observer failure", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("VAMPIRE-BLOOD-DRAIN-REACH {} {}", scenario, message);
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
                    boolean removed = (target == null || target.isRemoved()) && (user == null || user.isRemoved());
                    log("cleanup listeners=0 restoredBlocks=" + blocks.size() + " exactRestore=" + restored + " entitiesRemoved=" + removed);
                    if (!restored || !removed) throw new IllegalStateException("Drain fixture cleanup did not restore owned state exactly");
                    blocks.clear();
                }
            }
        }

        private void closeAfterFailure(Throwable primary) {
            try {
                close();
            }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Drain cleanup failed; original failure retained", cleanup);
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
            if (test.getError() != null) closeAfterFailure(test.getError());
            else close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError());
            else close();
        }
    }
}
