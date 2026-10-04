package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HGStringEntity;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HierophantStringSpatialGameTests {
    private static final double EPSILON = 1.0E-4D;

    private HierophantStringSpatialGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "r650_hg_unobstructed", timeoutTicks = 240)
    public static void hgStringAttackUsesDonorTurnaround(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "r650_hg_binding", timeoutTicks = 240)
    public static void hgStringBindTracksLivingMidHeight(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean binding) {
        Fixture fixture = new Fixture(helper, binding);
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

    private record Frame(long time, int age, UUID owner, Vec3 ownerPos, float yaw, float pitch,
            float bodyYaw, Vec3 origin, Vec3 tip, Vec3 motion, double radius, double distance,
            int lifespan, double speedFactor, boolean binding, boolean retracting, boolean dealtDamage,
            UUID attached, boolean removed, Entity.RemovalReason removal, boolean entityTicking) {}

    private record Step(Frame before, Frame after) {}

    private record Impact(UUID string, long time, int age, String type, Vec3 location,
            UUID target, boolean canceled) {}

    private record BindSample(long time, int stringAge, int targetAge, UUID target, Vec3 feet,
            double height, Vec3 tip, UUID attached, boolean targetAlive, boolean stringAlive,
            boolean immobilized, boolean dealtDamage) {
        double midHeightError() {
            return tip.y - (feet.y + height * 0.5D);
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 21;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean binding;
        private final List<HGStringEntity> strings = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private final List<Impact> impacts = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private FakePlayer player;
        private StandPower power;
        private StandType standType;
        private StandEntity stand;
        private Ability ability;
        private Cow target;
        private HGStringEntity central;
        private Frame spawned;
        private Frame before;
        private Frame terminal;
        private BindSample firstBind;
        private BindSample movedBind;
        private long movedAt = -1;
        private int targetAgeAtMove;
        private int playerTicks;
        private int standTicks;
        private int canceledTicks;
        private int canceledJoins;
        private Vec3 previousStandPos;
        private RuntimeException observerFailure;
        private boolean pressed;
        private boolean closed;

        private Fixture(GameTestHelper helper, boolean binding) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.binding = binding;
        }

        private void setUp() {
            // The empty template guarantees only its own chunk is entity-ticking.
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 pad = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 8.5D);
            AABB column = new AABB(pad.x - 4, pad.y - 2, pad.z - 4,
                    pad.x + 4, pad.y + 40, pad.z + 4);
            helper.assertTrue(column.maxY < level.getMaxBuildHeight(), "HG sky column exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(column.minX, column.minY, column.minZ),
                    BlockPos.containing(column.maxX, column.maxY, column.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "HG sky column is obstructed at " + pos);
            }
            helper.assertTrue(level.getEntities((Entity) null, column).isEmpty(), "HG sky column contains another fixture");
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), binding ? "HgStringBind" : "HgStringFlight"));
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(pad.x, pad.y, pad.z, 0.0F, -90.0F);
            player.setYHeadRot(0.0F);
            player.yBodyRot = 0.0F;
            level.addNewPlayer(player);
            standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
            helper.assertTrue(standType != null, "Missing registered Hierophant Green");
            power = PowerClass.STAND.attachGet(player);
            var result = StandPowerTransitions.insert(power, new StandInstance(standType));
            helper.assertTrue(result.status() == StandPowerTransitions.Status.APPLIED, "HG grant failed: " + result.status());
            helper.assertTrue(standType.summon(player, power), "HG summon failed");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "HG summon did not create a Stand");
            stand.setNoGravity(true);
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            power.setStamina(power.getMaxStamina());
            ability = power.getAbility(binding ? "string_bind" : "string_attack");
            helper.assertTrue(ability instanceof HierophantStringAttackAbility
                            && ability.abilityType == (binding ? ModStandAbilities.HG_STRING_BIND.get() : ModStandAbilities.HG_STRING_ATTACK.get()),
                    "HG fixture did not resolve the registered string ability");
            registerObservers();
            log("setup chunk=" + chunk + " player=" + player.getUUID() + " pad=" + pad + " stand=" + stand.getUUID());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof HGStringEntity string
                        && string.getOwner() == stand) {
                    strings.add(string);
                    if (event.isCanceled()) canceledJoins++;
                    CompoundTag nbt = string.saveWithoutId(new CompoundTag());
                    Frame frame = sample(string);
                    log("spawn uuid=" + string.getUUID() + " xOffset=" + nbt.getFloat("XRotOffset")
                            + " yOffset=" + nbt.getFloat("YRotOffset") + " canceled=" + event.isCanceled() + " " + frame);
                    if (nbt.getFloat("XRotOffset") == 0.0F && nbt.getFloat("YRotOffset") == 0.0F) {
                        if (central != null) throw new IllegalStateException("More than one central HG string");
                        central = string;
                        spawned = frame;
                    }
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == central) {
                    if (event.isCanceled()) canceledTicks++;
                    before = sample(central);
                    log("pre uuid=" + central.getUUID() + " canceled=" + event.isCanceled() + " " + before);
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == player) playerTicks++;
                if (event.getEntity() == stand) standTicks++;
                if (event.getEntity() == central) {
                    Frame after = sample(central);
                    log("post uuid=" + central.getUUID() + " " + after);
                    if (before == null || before.age != after.age || before.time != after.time) {
                        throw new IllegalStateException("Missing same-tick HG pre/post pair");
                    }
                    steps.add(new Step(before, after));
                    before = null;
                    if (binding && central.getEntityAttachedTo() == target && !central.isRemoved()) {
                        BindSample attached = new BindSample(after.time, after.age, target.tickCount, target.getUUID(),
                                target.position(), target.getBbHeight(), central.position(), after.attached,
                                target.isAlive(), central.isAlive(), target.hasEffect(ModStatusEffects.IMMOBILIZE), after.dealtDamage);
                        if (firstBind == null) {
                            firstBind = attached;
                            log("bind-initial " + attached + " midHeightError=" + attached.midHeightError());
                        }
                        else if (movedAt >= 0 && movedBind == null && after.time > movedAt && target.tickCount > targetAgeAtMove) {
                            movedBind = attached;
                            log("bind-moved " + attached + " midHeightError=" + attached.midHeightError());
                        }
                    }
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (strings.contains(event.getProjectile())) {
                    var hit = event.getRayTraceResult();
                    Impact observed = new Impact(event.getProjectile().getUUID(), level.getGameTime(), event.getProjectile().tickCount,
                            hit.getType().name(), hit.getLocation(), hit instanceof EntityHitResult entityHit
                                    ? entityHit.getEntity().getUUID() : null, event.isCanceled());
                    impacts.add(observed);
                    log("impact " + observed);
                }
            });
            Consumer<EntityLeaveLevelEvent> leave = event -> observe(() -> {
                if (event.getLevel() == level && strings.contains(event.getEntity())) {
                    Frame frame = sample((HGStringEntity) event.getEntity());
                    log("natural-leave uuid=" + event.getEntity().getUUID() + " " + frame);
                    if (event.getEntity() == central) terminal = frame;
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(leave);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityLeaveLevelEvent.class, leave);
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                // Fail from the GameTest scheduler, never from the world's entity tick loop.
                observerFailure = error;
                JojoMod.LOGGER.error("R650-HG observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 220, "HG observation watchdog: spawned=" + strings.size()
                        + " central=" + central + " terminal=" + terminal + " firstBind=" + firstBind + " movedBind=" + movedBind);
                if (!pressed) {
                    boolean settled = previousStandPos != null && previousStandPos.distanceToSqr(stand.position()) < EPSILON * EPSILON;
                    previousStandPos = stand.position();
                    if (playerTicks >= 2 && standTicks >= 2 && settled && stand.summonLockTicks == 0) {
                        press();
                    }
                    else {
                        helper.assertTrue(helper.getTick() < 60, "HG fixture never warmed: playerTicks=" + playerTicks
                                + " standTicks=" + standTicks + " summonLock=" + stand.summonLockTicks);
                    }
                }
                if (binding && firstBind != null && movedAt < 0 && target.isAlive() && !central.isRemoved()) {
                    Vec3 destination = target.position().add(0.75D, 1.0D, 0.5D);
                    target.setPos(destination);
                    movedAt = level.getGameTime();
                    targetAgeAtMove = target.tickCount;
                    log("target-translation time=" + movedAt + " targetAge=" + targetAgeAtMove + " destination=" + destination);
                }
                if (terminal != null && strings.stream().allMatch(Entity::isRemoved)) {
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

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition()) && level.isPositionEntityTicking(stand.blockPosition()),
                    "HG user or Stand is outside an entity-ticking chunk");
            helper.assertTrue(Math.abs(stand.getXRot() + 90.0F) < EPSILON
                            && Math.abs(Mth.wrapDegrees(stand.getYRot() - stand.yBodyRot)) < EPSILON,
                    "HG fixture did not settle into the upward, matching head/body frame");
            if (binding) {
                target = EntityType.COW.create(level);
                helper.assertTrue(target != null, "Could not create HG binding target");
                target.setNoAi(true);
                target.setNoGravity(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
                target.setHealth(100.0F);
                Vec3 center = stand.getEyePosition().add(stand.getLookAngle().scale(3.0D));
                target.setPos(center.x, center.y - target.getBbHeight() * 0.5D, center.z);
                helper.assertTrue(level.addFreshEntity(target), "Could not add HG binding target");
                helper.assertTrue(level.isPositionEntityTicking(target.blockPosition()) && target.isAlive()
                                && !target.isInvulnerable() && !JojoModUtil.isTargetBlocking(target),
                        "HG binding target is not an ordinary living, ticking, nonblocking target");
                LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(target));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
                log("target uuid=" + target.getUUID() + " feet=" + target.position() + " height=" + target.getBbHeight());
            }
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.CLICK),
                    "Registered HG string input failed its normal admission check");
            log("press ability=" + ability.getAbilityId() + " attackSpeed=" + stand.getAttackSpeed()
                    + " playerPostTicks=" + playerTicks + " standPostTicks=" + standTicks
                    + " player=" + player.position() + " stand=" + stand.position() + " entityTicking=true");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.CLICK,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof HierophantStringAttackAbility.StringAttackShot shot
                            && shot == stand.getCurStandAction() && shot.ability == ability,
                    "Public HG press did not install the registered shot on this Stand");
        }

        private Frame sample(HGStringEntity string) {
            CompoundTag nbt = string.saveWithoutId(new CompoundTag());
            var owner = string.getOwner();
            if (owner == null) throw new IllegalStateException("Observed HG string lost its living owner");
            Vec3 origin = string.getOriginPoint(1.0F);
            return new Frame(level.getGameTime(), string.tickCount, owner.getUUID(), owner.position(), owner.getYRot(), owner.getXRot(),
                    owner.yBodyRot, origin, string.position(), string.getDeltaMovement(), string.position().distanceTo(origin),
                    nbt.getDouble("Distance"), nbt.getInt("Lifespan"), string.getSpeedFactor(), nbt.getBoolean("Binding"),
                    nbt.getBoolean("Retracting"), nbt.getBoolean("DealtDamage"),
                    nbt.hasUUID("AttachedEntity") ? nbt.getUUID("AttachedEntity") : null,
                    string.isRemoved(), string.getRemovalReason(), level.isPositionEntityTicking(string.blockPosition()));
        }

        private void validate() {
            Frame peak = steps.stream().map(Step::after).max(java.util.Comparator.comparingDouble(Frame::radius)).orElse(null);
            log("complete L=" + spawned.lifespan + " F=" + spawned.speedFactor + " steps=" + steps.size()
                    + " peak=" + peak + " terminal=" + terminal + " impactCount=" + impacts.size()
                    + " firstBind=" + firstBind + " movedBind=" + movedBind);
            helper.assertTrue(strings.size() == (binding ? 4 : 8) && canceledJoins == 0,
                    "Registered HG input did not naturally spawn its full fan and central string: " + strings.size());
            helper.assertTrue(strings.stream().allMatch(string -> string.getType() == ModEntityTypes.HG_STRING.get()
                            && string.getOwner() == stand && string.isBinding() == binding), "Unexpected HG spawn type, owner or mode");
            helper.assertTrue(spawned.lifespan > 0 && Double.isFinite(spawned.speedFactor) && spawned.speedFactor > 0,
                    "Registered HG shot has invalid observed L/F");
            helper.assertTrue(terminal.removed && terminal.removal == Entity.RemovalReason.DISCARDED
                            && stand.isAlive() && player.isAlive(), "HG lifetime ended by unload or owner loss");
            helper.assertTrue(!steps.isEmpty() && canceledTicks == 0, "HG trace lacks uncanceled natural ticks");
            if (binding) validateBinding();
            else validateTurnaround();
        }

        private void validateTurnaround() {
            helper.assertTrue(impacts.isEmpty(), "Unobstructed HG fixture had a real impact: " + impacts);
            Frame start = steps.get(0).before;
            Step transition = null;
            for (Step step : steps) {
                Frame after = step.after;
                helper.assertTrue(after.owner.equals(stand.getUUID()) && after.entityTicking && !after.binding
                                && after.attached == null && !after.dealtDamage, "HG free-flight trace changed ownership, flags or ticking chunk");
                helper.assertTrue(after.ownerPos.distanceToSqr(start.ownerPos) < EPSILON * EPSILON
                                && after.origin.distanceToSqr(start.origin) < EPSILON * EPSILON
                                && Math.abs(Mth.wrapDegrees(after.yaw - start.yaw)) < EPSILON
                                && Math.abs(after.pitch - start.pitch) < EPSILON
                                && Math.abs(Mth.wrapDegrees(after.bodyYaw - after.yaw)) < EPSILON,
                        "HG free-flight owner frame moved during the trace");
                helper.assertTrue(after.lifespan == spawned.lifespan && after.speedFactor == spawned.speedFactor,
                        "HG free-flight L/F changed after production spawn");
                if (!after.removed) {
                    helper.assertTrue(Math.abs(after.radius - after.distance) < EPSILON,
                            "HG physical tip radius does not match its observed Distance: " + after);
                }
                if (!step.before.retracting && after.retracting) {
                    helper.assertTrue(transition == null, "HG free flight has multiple retraction transitions");
                    transition = step;
                }
            }
            helper.assertTrue(transition != null, "HG full lifetime never produced a retraction transition");
            log("turnaround " + transition);
            // The donor advances distance before setting its retraction flag, even on the transition tick.
            helper.assertTrue(transition.after.radius > transition.before.radius + EPSILON
                            && transition.after.distance > transition.before.distance + EPSILON,
                    "HG donor turnaround must finish outward: age=" + transition.after.age
                            + ", radius=" + transition.before.radius + " -> " + transition.after.radius);
        }

        private void validateBinding() {
            helper.assertTrue(firstBind != null && movedBind != null,
                    "HG did not supply two natural post-collision attachment samples: first=" + firstBind + ", moved=" + movedBind);
            helper.assertTrue(impacts.stream().anyMatch(impact -> impact.string.equals(central.getUUID())
                            && target.getUUID().equals(impact.target) && !impact.canceled),
                    "HG central binding was not preceded by a real, uncanceled target impact");
            helper.assertTrue(firstBind.feet.distanceToSqr(movedBind.feet) > 0.5D
                            && firstBind.tip.distanceToSqr(movedBind.tip) > 0.5D && movedBind.time > firstBind.time,
                    "HG binding samples did not observe a moved target and endpoint on separate world ticks");
            for (BindSample sample : List.of(firstBind, movedBind)) {
                helper.assertTrue(sample.target.equals(target.getUUID()) && sample.target.equals(sample.attached)
                                && sample.targetAlive && sample.stringAlive && sample.immobilized && sample.dealtDamage && sample.height > 0,
                        "HG attachment was not the living target bound by the real hit: " + sample);
                helper.assertTrue(Math.abs(sample.tip.x - sample.feet.x) < EPSILON
                                && Math.abs(sample.tip.z - sample.feet.z) < EPSILON,
                        "HG bound endpoint did not track target X/Z: " + sample);
            }
            helper.assertTrue(Math.abs(firstBind.midHeightError()) < EPSILON && Math.abs(movedBind.midHeightError()) < EPSILON,
                    "HG donor binding endpoint is target mid-height; errors initial=" + firstBind.midHeightError()
                            + ", moved=" + movedBind.midHeightError());
        }

        private void log(String message) {
            JojoMod.LOGGER.info("R650-HG {} {}", binding ? "bind" : "flight", message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (player != null) AbilityInput.keyRelease(KEY, player);
            }
            finally {
                try {
                    if (power != null && standType != null && power.isSummoned()) standType.forceUnsummon(player, power);
                }
                finally {
                    for (HGStringEntity string : strings) if (!string.isRemoved()) string.discard();
                    if (target != null) target.discard();
                    if (stand != null && !stand.isRemoved()) stand.discard();
                    if (player != null) player.discard();
                    log("cleanup listeners=0 spawned=" + strings.size());
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
