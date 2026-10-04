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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleBarrierAbility;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleLauncherAbility;
import rotp.core.impl.powers.hamon.abilities.HamonZoomPunchAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismFreezeAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonProjectileSourceGameTests {
    private HamonProjectileSourceGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_launcher_freeze", timeoutTicks = 160)
    public static void bubbleLauncherDoesNotRetaliateAgainstItsRangedOwner(GameTestHelper helper) {
        start(helper, Route.LAUNCHER, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_launcher_control", timeoutTicks = 160)
    public static void bubbleLauncherDamagesVampireWithoutFreeze(GameTestHelper helper) {
        start(helper, Route.LAUNCHER, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_barrier_freeze", timeoutTicks = 160)
    public static void initialBubbleBarrierDoesNotRetaliateAgainstItsRangedOwner(GameTestHelper helper) {
        start(helper, Route.BARRIER, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_barrier_control", timeoutTicks = 160)
    public static void initialBubbleBarrierDamagesVampireWithoutFreeze(GameTestHelper helper) {
        start(helper, Route.BARRIER, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_zoom_freeze", timeoutTicks = 160)
    public static void zoomPunchHamonDoesNotRetaliateAgainstItsRangedOwner(GameTestHelper helper) {
        start(helper, Route.ZOOM, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_source_zoom_control", timeoutTicks = 160)
    public static void zoomPunchPreservesPhysicalAndBaseTrainingWithoutFreeze(GameTestHelper helper) {
        start(helper, Route.ZOOM, false);
    }

    private enum Route {
        LAUNCHER("bubble_launcher", InputMethod.HOLD),
        BARRIER("bubble_barrier", InputMethod.HOLD),
        ZOOM("zoom_punch", InputMethod.CLICK);

        final String name;
        final InputMethod input;

        Route(String name, InputMethod input) {
            this.name = name;
            this.input = input;
        }

        EntityType<?> projectileType() {
            return switch (this) {
                case LAUNCHER -> ModEntityTypes.HAMON_BUBBLE.get();
                case BARRIER -> ModEntityTypes.HAMON_BUBBLE_BARRIER.get();
                case ZOOM -> ModEntityTypes.HAMON_ZOOM_PUNCH.get();
            };
        }
    }

    private static void start(GameTestHelper helper, Route route, boolean freezing) {
        Fixture fixture = new Fixture(helper, route, freezing);
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

    private record Attempt(String channel, float amount, UUID direct, UUID cause, boolean canceled) {}
    private record Contact(UUID projectile, long time, int age, float healthBefore, float healthAfter,
            double pointsBefore, double pointsAfter, float basePoints, int shooterFreezeDuration,
            int shooterFreezeAmplifier, List<Attempt> attempts, boolean defenderMounted) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short ATTACK_KEY = 26;
        private static final short FREEZE_KEY = 27;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Route route;
        private final boolean freezing;
        private final List<Object> listeners = new ArrayList<>();
        private final List<DamagingEntity> shots = new ArrayList<>();
        private final List<LivingIncomingDamageEvent> incoming = new ArrayList<>();
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private Player shooter;
        private Player defender;
        private PlayerPower shooterPower;
        private PlayerPower defenderPower;
        private HamonData hamon;
        private VampirismData vampire;
        private Ability attackAbility;
        private Ability freezeAbility;
        private EntityActionInstance freezeAction;
        private DamagingEntity active;
        private Contact contact;
        private RuntimeException observerFailure;
        private float healthBefore;
        private double pointsBefore;
        private long preTime;
        private int preAge;
        private int shooterTicks;
        private int defenderTicks;
        private int freezeStartedTicks;
        private boolean freezePressed;
        private boolean attackPressed;
        private boolean attackReleased;
        private boolean realHit;
        private boolean closed;

        Fixture(GameTestHelper helper, Route route, boolean freezing) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.route = route;
            this.freezing = freezing;
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL && !level.dimensionType().ultraWarm(),
                    "Source fixture needs non-Peaceful/non-ultrawarm conditions without changing global settings");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            AABB room = new AABB(x + 4, y - 1, z + 2, x + 13, y + 6, z + 14);
            helper.assertTrue(room.maxY < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, room).isEmpty(), "Source fixture lacks a clear sky room");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Source fixture room is obstructed");
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 4, y + 5, z + 2), new BlockPos(x + 12, y + 5, z + 13))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            defender = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            shooter.setNoGravity(true);
            defender.setNoGravity(true);
            shooter.moveTo(x + 8.5D, y, z + 4.0D, 0, 0);
            defender.moveTo(x + 8.5D, y, z + 7.5D, 180, 0);
            shooter.setYHeadRot(0);
            shooter.yBodyRot = 0;
            defender.setYHeadRot(180);
            defender.yBodyRot = 180;
            helper.assertTrue(level.addFreshEntity(shooter) && level.addFreshEntity(defender), "Could not add source-fixture players");
            shooterPower = PowerClass.PLAYER_POWER.attachGet(shooter);
            shooterPower.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(shooter, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(switch (route) {
                case LAUNCHER -> ModHamonSkills.BUBBLE_LAUNCHER.get();
                case BARRIER -> ModHamonSkills.BUBBLE_BARRIER.get();
                case ZOOM -> ModHamonSkills.ZOOM_PUNCH.get();
            });
            // Reuse the Barrier passenger fixture's legitimate capacity headroom for twenty paid charge ticks.
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            shooter.setItemInHand(InteractionHand.MAIN_HAND,
                    route == Route.ZOOM ? ItemStack.EMPTY : new ItemStack(ModItems.SOAP.get()));
            defenderPower = PowerClass.PLAYER_POWER.attachGet(defender);
            defenderPower.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            vampire = PlayerPower.getPowerData(defender, ModPlayerPowers.VAMPIRISM).orElseThrow();
            vampire.setVampireFullPower(true, defender);
            VampirismState.get(defender).blood().setCurrent(100);
            vampire.setBloodLevel(100);
            shooter.setHealth(shooter.getMaxHealth());
            defender.setHealth(defender.getMaxHealth());
            LivingComponentAction.getComponent(shooter).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(defender).entityAim.setTarget(ActionTarget.EMPTY);
            attackAbility = shooterPower.getAbility(route.name);
            freezeAbility = defenderPower.getAbility("vampirism_freeze");
            boolean registered = switch (route) {
                case LAUNCHER -> attackAbility instanceof HamonBubbleLauncherAbility
                        && attackAbility.abilityType == HamonPowerType.HAMON_BUBBLE_LAUNCHER.get();
                case BARRIER -> attackAbility instanceof HamonBubbleBarrierAbility
                        && attackAbility.abilityType == HamonPowerType.HAMON_BUBBLE_BARRIER.get();
                case ZOOM -> attackAbility instanceof HamonZoomPunchAbility
                        && attackAbility.abilityType == HamonPowerType.HAMON_ZOOM_PUNCH.get();
            };
            helper.assertTrue(registered && freezeAbility instanceof VampirismFreezeAbility
                            && freezeAbility.abilityType == VampirismPowerType.VAMPIRE_FREEZE.get()
                            && hamon.getEnergy() > 1500 && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                            && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "Source fixture registry, energy or training setup is invalid");
            registerObservers();
            log("setup shooter=" + shooter.getUUID() + " defender=" + defender.getUUID() + " roofCells=" + roof.size()
                    + " survival=true fixtureNoGravity=true separation=" + shooter.distanceTo(defender));
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof DamagingEntity shot
                        && shot.getOwner() == shooter && shot.getType() == route.projectileType()) {
                    if (!attackPressed || event.isCanceled() || shots.contains(shot)) throw new IllegalStateException("Unexpected projectile join");
                    shots.add(shot);
                    CompoundTag nbt = shot.saveWithoutId(new CompoundTag());
                    log("spawn uuid=" + shot.getUUID() + " pos=" + shot.position() + " velocity=" + shot.getDeltaMovement()
                            + " charging=" + nbt.getBoolean("Charging") + " basePoints=" + nbt.getFloat("Points"));
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (contact == null && shots.contains(event.getEntity())) {
                    if (active != null || event.isCanceled()) throw new IllegalStateException("Projectile tick is not isolated");
                    active = (DamagingEntity) event.getEntity();
                    if (!level.isPositionEntityTicking(active.blockPosition())) throw new IllegalStateException("Projectile left ticking chunks");
                    preTime = level.getGameTime();
                    preAge = active.tickCount;
                    healthBefore = defender.getHealth();
                    pointsBefore = points();
                    incoming.clear();
                    realHit = false;
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (contact == null && shots.contains(event.getProjectile())
                        && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == defender) {
                    if (active != event.getProjectile() || realHit || event.isCanceled()) throw new IllegalStateException("Invalid first owned target impact");
                    requireEligibilityAtContact();
                    if (route == Route.BARRIER && active.saveWithoutId(new CompoundTag()).getBoolean("Charging")) {
                        throw new IllegalStateException("Barrier contacted the target before its natural charge fired");
                    }
                    realHit = true;
                    log("impact uuid=" + active.getUUID() + " age=" + active.tickCount + " heldFreeze=" + freezing
                            + " phase=" + (freezeAction == null ? "none" : freezeAction.getPhase())
                            + " blood=" + VampirismState.get(defender).blood().current() + " shooterColdImmune=false shooterFreezeBefore=false");
                }
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (contact == null && event.getEntity() == defender) {
                    if (active == null || !realHit) throw new IllegalStateException("Defender damage is outside its owned projectile contact");
                    // Observe the production HIGHEST Freeze handler; never cancel or rewrite the event here.
                    incoming.add(event);
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == shooter) shooterTicks++;
                if (event.getEntity() == defender) defenderTicks++;
                if (contact == null && shots.contains(event.getEntity())) {
                    if (active != event.getEntity() || preTime != level.getGameTime() || preAge != active.tickCount) {
                        throw new IllegalStateException("Projectile post tick lacks its matching natural pre tick");
                    }
                    if (realHit) {
                        MobEffectInstance freeze = shooter.getEffect(ModStatusEffects.FREEZE);
                        contact = new Contact(active.getUUID(), level.getGameTime(), active.tickCount, healthBefore,
                                defender.getHealth(), pointsBefore, points(), active.saveWithoutId(new CompoundTag()).getFloat("Points"),
                                freeze == null ? 0 : freeze.getDuration(), freeze == null ? -1 : freeze.getAmplifier(),
                                incoming.stream().map(Fixture::attempt).toList(), defender.getVehicle() == active);
                        log("first-contact " + contact);
                        // Stop at this completed initial contact; later volleys/trickle are not another sample.
                        releaseAttack();
                        for (DamagingEntity shot : shots) if (!shot.isRemoved()) shot.discard();
                    }
                    active = null;
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void requireEligibilityAtContact() {
            if (!shooter.isAlive() || !defender.isAlive() || shooter.isCreative() || defender.isCreative()
                    || shooter.isOnFire() || defender.isOnFire() || DamageUtil.isImmuneToCold(shooter)
                    || shooter.hasEffect(ModStatusEffects.FREEZE) || !shooter.canHarmPlayer(defender)
                    || level.canSeeSky(shooter.blockPosition()) || level.canSeeSky(defender.blockPosition())
                    || !vampire.isVampireAtFullPower() || vampire.getCuringStage(defender) > 1
                    || !defender.getMainHandItem().isEmpty() || EntityHamonChargeState.get(defender).hasHamonCharge()
                    || HamonAbilityHelpers.hamonDamageAmount(defender, 0.1F) <= 0
                    || HamonAbilityHelpers.configHamonDamageMultiplier() <= 0
                    || LivingComponentAction.getAim(defender).getTarget().getType() != ActionTarget.TargetType.EMPTY) {
                throw new IllegalStateException("Source-retaliation eligibility changed before first contact");
            }
            EntityActionInstance current = LivingComponentAction.getCurEntityAction(defender);
            if (freezing) {
                if (current != freezeAction || current == null || current.getPhase() != ActionPhase.PERFORM
                        || !freezeAbility.checkMainModLogicConditions(defenderPower).isPositive()
                        || !freezeAbility.checkSpecificConditions(defenderPower).isPositive()
                        || VampirismState.get(defender).blood().current() < 0.45F) {
                    throw new IllegalStateException("Defender is not holding eligible registered Freeze at impact");
                }
            }
            else if (current != null) throw new IllegalStateException("Freeze-OFF control acquired an action");
        }

        private void pressFreeze() {
            freezeAction = press(defender, defenderPower, freezeAbility, InputMethod.HOLD, FREEZE_KEY);
            helper.assertTrue(freezeAction instanceof VampirismFreezeAbility.FreezeInstance, "Wrong defensive Freeze action");
            freezePressed = true;
            freezeStartedTicks = defenderTicks;
        }

        private void pressAttack() {
            Vec3 launchOrigin = shooter.getEyePosition().add(0, -0.3D, 0);
            Vec3 aim = defender.getBoundingBox().getCenter().subtract(launchOrigin);
            shooter.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
            shooter.setYHeadRot(shooter.getYRot());
            shooter.yBodyRot = shooter.getYRot();
            shooter.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance())));
            attackPressed = true;
            EntityActionInstance action = press(shooter, shooterPower, attackAbility, route.input, ATTACK_KEY);
            boolean correct = switch (route) {
                case LAUNCHER -> action instanceof HamonBubbleLauncherAbility.BubbleLauncherInstance;
                case BARRIER -> action instanceof HamonBubbleBarrierAbility.BubbleBarrierInstance
                        && action.getPhase() == ActionPhase.WINDUP && action.phasesLength.getFloat(ActionPhase.WINDUP) == 20F;
                case ZOOM -> action instanceof HamonZoomPunchAbility.ZoomPunchInstance;
            };
            helper.assertTrue(correct, "Wrong registered attack action or Barrier charge duration");
            log("attack-input ability=" + attackAbility.getAbilityId() + " input=" + route.input
                    + " naturalTicks=" + shooterTicks + "/" + defenderTicks + " energy=" + hamon.getEnergy());
        }

        private EntityActionInstance press(Player player, PlayerPower power, Ability ability, InputMethod input, short key) {
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition()), "Input actor is not entity-ticking");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, input),
                    "Registered input was not admitted: " + ability.getAbilityId());
            var held = AbilityInput.keyPress(key, ability, player, null, input, 0, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(held != null && held.action instanceof EntityActionInstance action
                            && action.ability == ability && action == LivingComponentAction.getCurEntityAction(player),
                    "Registered input did not install the expected actual action");
            return (EntityActionInstance) held.action;
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 140, "Hamon source watchdog: route=" + route + " shots=" + shots.size());
                if (shooterTicks >= 2 && defenderTicks >= 2) {
                    if (freezing && !freezePressed) pressFreeze();
                    if (!attackPressed && (!freezing || defenderTicks > freezeStartedTicks)) pressAttack();
                }
                // Keep Launcher bounded, but allow multiple genuine volleys for its random spread.
                if (route == Route.LAUNCHER && shots.size() >= 16) releaseAttack();
                if (contact != null) {
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
            List<Attempt> hamonHits = contact.attempts.stream().filter(a -> a.channel.equals("hamon")).toList();
            List<Attempt> physicalHits = contact.attempts.stream().filter(a -> a.channel.equals("physical")).toList();
            helper.assertTrue(!hamonHits.isEmpty() && contact.attempts.stream().allMatch(a -> a.amount > 0
                            && shooter.getUUID().equals(a.cause) && !a.channel.equals("unexpected")),
                    "First contact lacks eligible owned damage dispatch");
            Attempt initialHamon = hamonHits.get(0);
            if (route == Route.ZOOM) {
                helper.assertTrue(physicalHits.size() == 1 && !physicalHits.get(0).canceled
                                && contact.projectile.equals(physicalHits.get(0).direct) && contact.healthAfter < contact.healthBefore,
                        "Zoom's existing physical projectile channel no longer succeeds");
                double baseAward = contact.basePoints * JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() / 750.0D;
                helper.assertTrue(baseAward > 0 && contact.pointsAfter - contact.pointsBefore + 1.0E-5 >= baseAward,
                        "Zoom lost its independent base-use training");
            }
            else helper.assertTrue(physicalHits.isEmpty(), "Bubble route unexpectedly dispatched physical damage");
            log("result initialHamon=" + initialHamon + " extraHamonEvents=" + (hamonHits.size() - 1)
                    + " health=" + contact.healthBefore + "->" + contact.healthAfter
                    + " shooterFreeze=" + contact.shooterFreezeDuration + "/" + contact.shooterFreezeAmplifier
                    + " training=" + (contact.pointsAfter - contact.pointsBefore) + " mounted=" + contact.defenderMounted);
            helper.assertTrue(!initialHamon.canceled && contact.healthAfter < contact.healthBefore
                            && contact.shooterFreezeDuration == 0,
                    "Ranged Hamon was countered as living melee: canceled=" + initialHamon.canceled
                            + ", shooterFreeze=" + contact.shooterFreezeDuration + ", health=" + contact.healthBefore + "->" + contact.healthAfter);
            // The OFF cases are behavioral eligibility controls even against the original attribution bug.
            if (freezing) helper.assertTrue(contact.projectile.equals(initialHamon.direct), "Initial Hamon direct source is not its projectile");
        }

        private static Attempt attempt(LivingIncomingDamageEvent event) {
            DamageSource source = event.getSource();
            String channel = source.is(ModDamageTypes.HAMON) ? "hamon"
                    : source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) ? "physical" : "unexpected";
            return new Attempt(channel, event.getOriginalAmount(), id(source.getDirectEntity()), id(source.getEntity()), event.isCanceled());
        }

        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }

        private double points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }

        private void releaseAttack() {
            if (attackPressed && !attackReleased) {
                AbilityInput.keyRelease(ATTACK_KEY, shooter);
                attackReleased = true;
            }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Hamon projectile source observer failure", error);
            }
        }

        private void log(String message) { JojoMod.LOGGER.info("HAMON-SOURCE {} freeze={} {}", route, freezing, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            active = null;
            try { releaseAttack(); }
            finally {
                try { if (defender != null) AbilityInput.keyRelease(FREEZE_KEY, defender); }
                finally {
                    try {
                        for (DamagingEntity shot : shots) if (!shot.isRemoved()) shot.discard();
                        if (defender != null) defender.discard();
                        if (shooter != null) shooter.discard();
                    }
                    finally {
                        for (var entry : roof.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                        boolean restored = roof.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                        log("cleanup listeners=0 shots=" + shots.size() + " roofRestored=" + restored + " roofCells=" + roof.size());
                        if (!restored) throw new IllegalStateException("Hamon source fixture roof was not restored");
                    }
                }
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}
        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
