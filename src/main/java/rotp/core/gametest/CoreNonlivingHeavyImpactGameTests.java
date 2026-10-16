package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.StandInitStarPlatinum;
import rotp.core.impl.stands.StandInitTheWorld;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModGamerules;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.CollisionHelper;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreNonlivingHeavyImpactGameTests {
    private static final short ATTACK = 151;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(CoreNonlivingHeavyImpactGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;

    private enum Kind {
        BOAT_WALL, BOAT_M0, BOAT_REFUSED, LIVING_WALL, LIVING_CANCELED,
        BOAT_REMOVED, BOAT_NO_WALL, BOAT_RULE_OFF, BOAT_EXPLOSION_CANCELED, KICK_SECONDARY,
        // Crazy Diamond's heavy keeps ordinary (non-detached) knockback: 1.16 knockbackTarget still pushed the boat
        CD_BOAT_WALL;
        boolean living() { return this == LIVING_WALL || this == LIVING_CANCELED; }
        boolean refused() { return this == BOAT_REFUSED || this == LIVING_CANCELED; }
        boolean wall() { return this != BOAT_NO_WALL && this != BOAT_REMOVED; }
        boolean breaks() { return this != BOAT_RULE_OFF; }
        double multiplier() { return this == BOAT_M0 ? 0 : this == BOAT_REMOVED ? 1 : 0.1; }
    }
    private record Hit(Entity target, DamageSource source, float raw, double multiplier,
            boolean result, float boatBefore, float boatAfter, float healthBefore, float healthAfter,
            int before, int after, Entity standBookBefore, Entity userBookBefore) {}
    private record Impact(boolean active, Vec3 direction, double power, float radius) {}

    private CoreNonlivingHeavyImpactGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2000, batch = "core_nonliving_heavy_impact")
    public static void registeredPrimaryHurtArmsNaturalBoatWallAftermath(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            identity();
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { throwCleanup(suite.closeInto(error)); }
    }

    @AfterBatch(batch = "core_nonliving_heavy_impact")
    public static void releaseNonlivingImpactBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "core_nonliving_heavy_impact_cleanup")
    public static void nonlivingImpactFixtureCleanupPreservesCauseAndOwnedResources(GameTestHelper helper) {
        identity();
        CleanupProbe probe = new CleanupProbe(helper);
        helper.testInfo.addListener(probe); helper.onEachTick(probe::tick);
    }
    @AfterBatch(batch = "core_nonliving_heavy_impact_cleanup")
    public static void releaseNonlivingImpactCleanupBatch(ServerLevel level) {
        if (active != null) active.close();
        cleanupLog("BATCH-CLEANUP active=" + (active != null));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final ModConfigSpec.DoubleValue multiplier = (ModConfigSpec.DoubleValue) JojoModConfig.COMMON_SPEC
                .getValues().get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        final boolean oldBreaks;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException callbackFailure;
        int index;
        int checks;
        int premises;
        boolean closed;
        boolean summaryComplete;
        boolean cleanupComplete;
        boolean multiplierRestored;
        boolean breaksRestored;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping nonliving impact suites");
            this.helper = helper; level = helper.getLevel(); oldMultiplier = multiplier.get();
            oldBreaks = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
            active = this; helper.testInfo.addListener(this);
        }
        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> callback(() -> scene.pre(event.getEntity()));
            Consumer<EntityTickEvent.Post> post = event -> callback(() -> scene.post(event.getEntity()));
            Consumer<LivingIncomingDamageEvent> incoming = event -> callback(() -> scene.incoming(event));
            Consumer<EntityInvulnerabilityCheckEvent> invulnerability = event -> callback(() -> scene.invulnerability(event));
            Consumer<ExplosionEvent.Start> start = event -> callback(() -> scene.explosionStart(event));
            Consumer<ExplosionEvent.Detonate> detonate = event -> callback(() -> scene.explosionDetonate(event));
            Consumer<EntityJoinLevelEvent> join = event -> callback(() -> scene.join(event));
            listeners.add(pre); listeners.add(post); listeners.add(incoming); listeners.add(invulnerability);
            listeners.add(start); listeners.add(detonate); listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityInvulnerabilityCheckEvent.class, invulnerability);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Start.class, start);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Detonate.class, detonate);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, join);
        }
        void callback(Runnable action) {
            if (scene == null || closed || callbackFailure != null) return;
            try { action.run(); } catch (RuntimeException error) { callbackFailure = error; }
        }
        void tick() {
            if (closed) return;
            try {
                if (callbackFailure != null) throw callbackFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Kind.values().length) {
                        log("SUMMARY scenes=" + index + " premises=" + premises + " checks=" + checks
                                + " failures=" + failures.size() + " details=" + failures);
                        summaryComplete = true;
                        close(); helper.assertTrue(failures.isEmpty(), "Nonliving aftermath failed; see SUMMARY");
                        helper.succeed(); return;
                    }
                    scene = new Scene(this, Kind.values()[index++]); scene.prepare();
                }
                scene.tick();
                premise(++scene.ticks < 64, "native scene timeout ages=" + scene.stand.tickCount + "/" + scene.primary.tickCount);
            }
            catch (RuntimeException | Error error) {
                Throwable failure = attemptCleanup(error, () -> log((summaryComplete ? "ASSERTION-RED " : "INVALID-PREMISE ") + error));
                throwCleanup(closeInto(failure));
            }
        }
        void premise(boolean value, String detail) {
            premises++;
            if (!value) log("PREMISE-FAIL " + (scene != null ? scene.kind : "setup") + " " + detail);
            helper.assertTrue(value, (scene != null ? scene.kind : "setup") + " premise: " + detail);
        }
        void check(boolean value, String detail) {
            checks++; log((value ? "PASS " : "FAIL ") + scene.kind + " " + detail);
            if (!value) failures.add(scene.kind + ": " + detail);
        }
        void close() {
            throwCleanup(closeInto(null));
        }







        Throwable closeInto(Throwable failure) {
            if (closed && cleanupComplete) return failure;
            closed = true;
            boolean[] failed = {false};
            for (Object listener : new ArrayList<>(listeners)) failure = attemptCleanup(failure, failed, () -> {
                NeoForge.EVENT_BUS.unregister(listener); listeners.remove(listener);
            });
            if (scene != null) failure = scene.closeInto(failure);
            failure = attemptCleanup(failure, failed, () -> {
                multiplier.set(oldMultiplier);
                multiplierRestored = Double.compare(multiplier.get(), oldMultiplier) == 0
                        && Double.compare(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get(), oldMultiplier) == 0;
                if (!multiplierRestored) throw new IllegalStateException("Owned stand multiplier was not restored");
            });
            failure = attemptCleanup(failure, failed, () -> {
                level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreaks, level.getServer());
                breaksRestored = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS) == oldBreaks;
                if (!breaksRestored) throw new IllegalStateException("Owned BREAK_BLOCKS was not restored");
            });
            failure = attemptCleanup(failure, failed, () -> log("CLEANUP listeners=" + listeners.size()
                    + " sceneComplete=" + (scene == null || scene.cleanupComplete)
                    + " M=" + JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()
                    + " breakBlocks=" + level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS)
                    + " activeOwned=" + (active == this)));
            cleanupComplete = !failed[0] && listeners.isEmpty() && (scene == null || scene.cleanupComplete)
                    && multiplierRestored && breaksRestored;
            if (cleanupComplete && active == this) active = null;
            return failure;
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final ServerLevel level;
        final List<Entity> loose = new ArrayList<>();
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final List<BlockPos> wall = new ArrayList<>();
        final Map<Entity, Hit> hits = new IdentityHashMap<>();
        final Map<Entity, Float> incomingAmounts = new IdentityHashMap<>();
        TestPlayer user;
        StandPower power;
        StandType type;
        ObservedAttackerStand stand;
        Entity primary;
        LivingEntity secondary;
        EntityActionInstance action;
        Impact captured;
        Vec3 origin;
        Vec3 motionAtHit;
        Vec3 positionAtHit;
        int ageAtHit;
        int hitWorldTick;
        int primaryTicksAfterHit;
        int order;
        int hitOrder;
        int explosionStartOrder;
        int explosionStarts;
        int detonations;
        int wallChecks;
        int primaryInvulnerabilityChecks;
        float wallDamageBaseline;
        float wallDamageObserved;
        int ticks;
        boolean hitCaptured;
        boolean hitVerified;
        boolean hadBlockImpact;
        boolean done;
        boolean closed;
        boolean cleanupComplete;
        RuntimeException invalid;

        Scene(Suite suite, Kind kind) { this.suite = suite; this.kind = kind; level = suite.level; }
        void prepare() {
            suite.multiplier.set(kind.multiplier());
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(kind.breaks(), level.getServer());
            suite.premise(near(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get(), kind.multiplier()), "actual server M");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            for (int x = -5; x <= 5; x++) for (int z = -3; z <= 7; z++) {
                for (int y = kind == Kind.BOAT_NO_WALL ? -5 : 0; y <= 4; y++) set(origin.add(x, y, z), Blocks.AIR.defaultBlockState());
                if (kind != Kind.BOAT_NO_WALL) set(origin.add(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            if (kind.wall()) for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) {
                BlockPos pos = set(origin.add(x, y, 2.5), Blocks.GLASS.defaultBlockState()); wall.add(pos);
            }
            actor(kind == Kind.KICK_SECONDARY ? "the_world" : kind == Kind.CD_BOAT_WALL ? "crazy_diamond" : "star_platinum");
            if (kind.living()) {
                Cow cow = EntityType.COW.create(level); suite.premise(cow != null, "native cow created");
                cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
                cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); primary = cow;
            }
            else {
                Boat boat = EntityType.BOAT.create(level); suite.premise(boat != null, "native boat factory");
                if (kind == Kind.BOAT_REFUSED) boat.setInvulnerable(true); primary = boat;
            }
            primary.moveTo(origin.x, origin.y, origin.z + 1.5, 0, 0);
            suite.premise(!primary.noPhysics && !primary.isNoGravity(), "native primary physics/gravity enabled");
            suite.premise(level.addFreshEntity(primary), "native primary added"); loose.add(primary);
            if (kind == Kind.KICK_SECONDARY) {
                Cow cow = EntityType.COW.create(level); suite.premise(cow != null, "vanilla sweep cow factory");
                cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                secondary = cow;
                secondary.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); secondary.setHealth(100);
                secondary.moveTo(origin.x + 1.4, origin.y, origin.z + 1.5, 0, 0);
                suite.premise(level.addFreshEntity(secondary), "native living secondary added"); loose.add(secondary);
            }
            log("START " + kind + " user=" + user.getId() + " stand=" + stand.getId() + " primary=" + primary.getId()
                    + " M=" + kind.multiplier() + " wall=" + wall + " physics=" + primary.noPhysics);
        }
        BlockPos set(Vec3 point, BlockState state) {
            BlockPos pos = BlockPos.containing(point); restored.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state); return pos;
        }
        void actor(String name) {
            user = new TestPlayer(level, "impact-caster"); user.noPhysics = true; user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            suite.premise(level.addFreshEntity(user), "world-ticked synthetic user added"); loose.add(user);
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(name));
            suite.premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED, "public registry Stand grant");
            power.setResolveLevel(2); suite.premise(type.summon(user, power), "actual native summon");
            StandEntity original = power.getSummonedStandEntity(); suite.premise(original != null, "native summon result"); original.discard();
            stand = new ObservedAttackerStand(this, name); stand.withStandType(type);
            power.setSummonedStand(stand); stand.copySharedEffectsFromUser(user);
            ((EntityStandType) type).finalizeStandSummonFromAction(user, power, stand, true);
            suite.premise(stand.getUser() == user && power.getSummonedStandEntity() == stand && stand.isAddedToLevel(), "public summon/finalize installs passive helper observer");
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0; stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0); stand.setYHeadRot(0);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(28);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user); power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
        }
        void tick() {
            if (invalid != null) throw invalid;
            if (action == null && ticks >= 3) press();
            if (!hitCaptured) return;
            if (!hitVerified) { verifyHit(); hitVerified = true; }
            if (kind == Kind.BOAT_REMOVED || primaryTicksAfterHit >= 5) { verifyAftermath(); done = true; }
        }
        void press() {
            suite.premise(stand.tickCount >= 3 && user.tickCount >= 3 && primary.tickCount >= 3, "only normal world tick drivers advanced all actors");
            suite.premise(primary.isAlive() && primary.isPickable(), "live pickable primary before input");
            if (primary instanceof Boat boat) suite.premise(boat.getPassengers().isEmpty() && boat.getControllingPassenger() == null && boat.getDamage() == 0, "surviving unoccupied undamaged vanilla boat");
            if (kind == Kind.KICK_SECONDARY) {
                var result = UNLOCKS.forceUnlock(power, "kick");
                suite.premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public Kick unlock");
                suite.premise(secondary.isAlive() && secondary.isPickable() && stand.canAttackEntity(secondary)
                        && primary.getBoundingBox().inflate(0.5, 0, 0.5).intersects(secondary.getBoundingBox())
                        && !primary.getBoundingBox().inflate(0.2, -0.01, 0.2).intersects(secondary.getBoundingBox()),
                        "native secondary inside sweep, outside boarding bounds");
                log("SECONDARY-PREMISE position=" + secondary.position() + " box=" + secondary.getBoundingBox()
                        + " primaryBox=" + primary.getBoundingBox() + " pickable=" + secondary.isPickable()
                        + " physics=" + secondary.noPhysics + " riding=" + secondary.isPassenger());
                stand.setFinisherMeter(0.75F);
            } else stand.setFinisherMeter(0);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(primary));
            // the crosshair a client sends: 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget),
            // and the strike aimed again through the user's aim, so only that aim brings the heavy punch to the boat
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(primary));
            Ability base = power.getAbility("heavy_punch"); suite.premise(base != null, "registered heavy ability");
            var binding = power.getMoveset().controlScheme.defaultGroup.separateBinds.get("heavy_punch");
            suite.premise(binding != null && binding.getFirst() == InputMethod.CLICK && binding.getSecond() instanceof InputKey key
                    && key.device == InputKey.RMB.device && key.keyCode == InputKey.RMB.keyCode && key.modifier == null, "registered plain RMB CLICK binding");
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            suite.premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "actual native input admission");
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(ATTACK, selected.ability, user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
            suite.premise(buffer.isActionSuccess, "checked native keyPress success");
            action = held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
            suite.premise(action instanceof StandEntityHeavyPunch && (action instanceof TheWorldKick) == (kind == Kind.KICK_SECONDARY), "actual ordinary/Kick registry action factory");
            suite.premise(stand.getAttackDamage() == 15 && stand.getAttackSpeed() == 28, "actual effective attack getters");
            log("PRESS " + kind + " ability=" + ((Ability) action.ability).name() + " phases=" + action.phasesLength
                    + " ages=" + user.tickCount + "/" + stand.tickCount + "/" + primary.tickCount + " world=" + level.getGameTime());
        }
        void pre(Entity entity) {
            if (kind != Kind.BOAT_NO_WALL || entity != primary || closed || done) return;
            Vec3 nativeMotion = primary.getDeltaMovement();
            AABB corridor = primary.getBoundingBox().inflate(0.25).expandTowards(nativeMotion.add(0, -0.08, 0));
            boolean allAir = true;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(corridor.minX, corridor.minY, corridor.minZ),
                    BlockPos.containing(corridor.maxX, corridor.maxY, corridor.maxZ))) {
                if (!level.getBlockState(pos).isAir()) { allAir = false; break; }
            }
            var blockers = CollisionHelper.getEntityCollisions(level, primary, corridor,
                    EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(e -> JojoModUtil.canHarm(user, e)))
                    .map(pair -> pair.getLeft().getId()).toList();
            suite.premise(allAir && level.noCollision(primary, corridor), "actual inflated swept all-air/native-shape corridor");
            suite.premise(blockers.isEmpty(), "actual impact-eligible entity blockers=" + blockers);
            suite.premise(!primary.noPhysics && !primary.isNoGravity() && !primary.onGround(), "airborne native collision/gravity/no ground contact");
            log("NO-CONTACT-PRE world=" + level.getGameTime() + " age=" + primary.tickCount + " afterHit=" + primaryTicksAfterHit
                    + " motion=" + nativeMotion + " inflatedSwept=" + corridor + " allAir=" + allAir + " blockers=" + blockers
                    + " actorIds=" + user.getId() + "/" + stand.getId() + " ownUserCanHarm=" + JojoModUtil.canHarm(user, user));
        }
        void post(Entity entity) {
            if (closed || done || action == null) return;
            if (entity == stand && !hitCaptured && action.punchedTarget != null) {
                hitCaptured = true; hitOrder = ++order; motionAtHit = primary.getDeltaMovement(); positionAtHit = primary.position();
                ageAtHit = primary.tickCount; hitWorldTick = (int) level.getGameTime(); captured = capture(primary);
                if (primary instanceof Boat boat) wallDamageBaseline = boat.getDamage();
                log("HIT-CAPTURE " + kind + " order=" + hitOrder + " phase=" + action.getPhase() + " world=" + hitWorldTick
                        + " ages=" + user.tickCount + "/" + stand.tickCount + "/" + primary.tickCount + " removed=" + primary.isRemoved()
                        + " velocity=" + motionAtHit + " position=" + positionAtHit + " impact=" + captured);
            }
            if (entity == primary && hitCaptured && primary.tickCount > ageAtHit) {
                primaryTicksAfterHit++; var impact = KnockbackCollisionImpact.getExistingHandler(primary);
                hadBlockImpact |= impact != null && impact.getHadImpactWithBlock();
                if (primary instanceof Boat boat) {
                    if (primaryTicksAfterHit == 1 && !boat.getPassengers().isEmpty())
                        invalid = new IllegalStateException("Boat acquired a passenger before first physical collision");
                    float expectedDecay = Math.max(wallDamageBaseline - 1, 0);
                    wallDamageObserved += Math.max(boat.getDamage() - expectedDecay, 0); wallDamageBaseline = boat.getDamage();
                }
                log("WORLD-PHYSICS " + kind + " tick=" + primaryTicksAfterHit + " age=" + primary.tickCount + " world=" + level.getGameTime()
                        + " position=" + primary.position() + " velocity=" + primary.getDeltaMovement() + " ground=" + primary.onGround()
                        + " impact=" + capture(primary) + " hadBlock=" + hadBlockImpact + " wallDamage10=" + wallDamageObserved
                        + " passengers=" + primary.getPassengers().stream().map(Entity::getId).toList());
            }
        }
        void incoming(LivingIncomingDamageEvent event) {
            if (event.getSource().getDirectEntity() != stand || event.getEntity() != primary && event.getEntity() != secondary) return;
            incomingAmounts.put(event.getEntity(), event.getOriginalAmount());
            if (kind == Kind.LIVING_CANCELED && event.getEntity() == primary) event.setCanceled(true);
            log("INCOMING " + kind + " target=" + event.getEntity().getId() + " actual=" + event.getOriginalAmount() + " canceled=" + event.isCanceled());
        }
        void invulnerability(EntityInvulnerabilityCheckEvent event) {
            if (event.getEntity() != primary) return;
            if (event.getSource().getDirectEntity() == stand) primaryInvulnerabilityChecks++;
            if (event.getSource().is(DamageTypes.FLY_INTO_WALL)) wallChecks++;
            log("NATIVE-INVULNERABILITY " + kind + " source=" + event.getSource().getMsgId() + " invulnerable=" + event.isInvulnerable());
        }
        boolean owned(ExplosionEvent event) {
            return event.getLevel() == level && event.getExplosion() instanceof StandEntityHeavyPunchAbility.HeavyPunchExplosion
                    && event.getExplosion().getDirectSourceEntity() == stand;
        }
        void explosionStart(ExplosionEvent.Start event) {
            if (!owned(event)) return; explosionStarts++; explosionStartOrder = ++order;
            if (kind == Kind.BOAT_EXPLOSION_CANCELED) event.setCanceled(true);
            log("NATIVE-EXPLOSION-START " + kind + " order=" + explosionStartOrder + " hitOrder=" + hitOrder + " world=" + level.getGameTime()
                    + " position=" + event.getExplosion().center() + " canceled=" + event.isCanceled());
        }
        void explosionDetonate(ExplosionEvent.Detonate event) {
            if (!owned(event)) return; detonations++;
            log("NATIVE-EXPLOSION-DETONATE " + kind + " order=" + (++order) + " blocks=" + event.getExplosion().getToBlow()
                    + " entities=" + event.getAffectedEntities().stream().map(Entity::getId).toList());
        }
        void join(EntityJoinLevelEvent event) {
            if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item
                    && origin != null && new AABB(origin.add(-8, -3, -6), origin.add(8, 8, 10)).contains(item.position())) loose.add(item);
        }
        Impact capture(Entity target) {
            var impact = KnockbackCollisionImpact.getExistingHandler(target);
            if (impact == null || !impact.isActive()) return new Impact(false, Vec3.ZERO, 0, 0);
            var saved = impact.serializeNBT(level.registryAccess());
            Vec3 direction = Vec3.CODEC.parse(NbtOps.INSTANCE, saved.get("Vec")).result().orElse(Vec3.ZERO);
            return new Impact(true, direction, saved.getDouble("Power"), saved.getFloat("ExplosionRadius"));
        }
        void verifyHit() {
            Hit hit = hits.get(primary); suite.premise(hit != null, "one real virtual primary hurt dispatch observed");
            suite.premise(action.punchedTarget.getMainEntity() == primary && near(hit.raw, 15) && near(hit.multiplier, kind.multiplier()), "primary target/RAW/actual-M operands");
            suite.premise(hit.result == !kind.refused(), "actual delegated hurt return=" + hit.result);
            suite.premise(!primary.noPhysics, "native target collision still enabled");
            float actualAmount = primary instanceof Boat ? (hit.boatAfter - hit.boatBefore) / 10 : incomingAmounts.getOrDefault(primary, Float.NaN);
            suite.premise(kind.refused() && primary instanceof Boat ? actualAmount == 0 : near(actualAmount, 15 * kind.multiplier()), "native actual amount/damage delta=" + actualAmount);
            suite.premise(primary.isRemoved() == (kind == Kind.BOAT_REMOVED), "actual removed/surviving target lifecycle");
            if (primary instanceof Boat boat) suite.premise(boat.getPassengers().isEmpty(), "boat remains unoccupied after normal input");
            suite.check(captured.active == hit.result, "primary handler state follows actual hurt result active=" + captured.active + " hurt=" + hit.result);
            if (hit.result) {
                suite.check(near(captured.direction, motionAtHit.normalize()) && near(captured.power, motionAtHit.length()), "capture equals final post-tail vector/power=" + captured);
                suite.check(near(captured.radius, 2.125), "donor effective strength15 explosion radius=" + captured.radius);
            }
            suite.check(hits.size() == (secondary == null ? 1 : 2), "single primary and only expected secondary virtual dispatches count=" + hits.size());
            if (secondary != null) {
                Hit secondaryHit = hits.get(secondary); suite.premise(secondaryHit != null && secondaryHit.result && near(secondaryHit.raw, 7.5)
                        && near(incomingAmounts.getOrDefault(secondary, Float.NaN), 0.75), "real successful half-RAW secondary amount/return");
                suite.check(!capture(secondary).active, "moving successful Kick secondary has no primary registration");
                suite.check(stand.getLastHurtMob() == null && user.getLastHurtMob() == null, "nonliving primary bookkeeping remains null; secondary does not replace it");
            }
            suite.check(motionAtHit.lengthSqr() > 0, "real native hurt/tail left nonzero movement");
            log("QUALIFIED-HIT " + kind + " return=" + hit.result + " actualAmount=" + actualAmount + " raw=" + hit.raw
                    + " M=" + hit.multiplier + " removed=" + primary.isRemoved() + " helperOrders=" + hit.before + "/" + hit.after);
        }
        void verifyAftermath() {
            long changedWall = wall.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.GLASS)).count();
            boolean physical = kind.wall() && !kind.refused();
            boolean explodes = physical && kind.breaks() && kind != Kind.BOAT_EXPLOSION_CANCELED;
            if (kind == Kind.BOAT_REMOVED) {
                suite.check(primaryTicksAfterHit == 0 && explosionStarts == 0, "removed target provides state only, never later collision proof"); return;
            }
            suite.premise(primaryTicksAfterHit >= 5 && primary.tickCount >= ageAtHit + 5, "normal target world ticks completed without manual driver");
            suite.check(!near(primary.position(), positionAtHit), "native world movement actually occurred");
            if (kind == Kind.BOAT_NO_WALL) suite.check(!hadBlockImpact && wallChecks == 0, "qualified air corridor has no block impact or wall hurt");
            else suite.check(hadBlockImpact == physical, "native sideways wall impact flag=" + hadBlockImpact);
            suite.check(explosionStarts == (physical && kind.breaks() ? 1 : 0), "native explosion-start count=" + explosionStarts);
            suite.check(detonations == (explodes ? 1 : 0), "accepted native explosion-detonate count=" + detonations);
            suite.check(explodes ? changedWall > 0 : changedWall == 0, "real wall block delta=" + changedWall);
            suite.check(explosionStarts == 0 || explosionStartOrder > hitOrder, "native explosion follows captured successful primary");
            if (primary instanceof Boat) suite.check(physical ? wallChecks > 0 && wallDamageObserved > 0 : wallDamageObserved == 0,
                    "native wall-damage hook/count=" + wallChecks + " actualDamage10=" + wallDamageObserved);
            if (kind == Kind.BOAT_NO_WALL) suite.check(capture(primary).active && !hadBlockImpact && !primary.onGround(),
                    "moving no-contact primary retains armed impact through five natural ticks");
            else suite.check(!capture(primary).active, "impact ended naturally after wall/floor collision; no manual reset");
        }
        @Override public void close() {
            throwCleanup(closeInto(null));
        }



        Throwable closeInto(Throwable failure) {
            if (closed && cleanupComplete) return failure;
            closed = true;
            boolean[] failed = {false};
            if (user != null) failure = attemptCleanup(failure, failed, () -> AbilityInput.keyRelease(ATTACK, user));
            failure = attemptCleanup(failure, failed, () -> {
                if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            });
            failure = attemptCleanup(failure, failed, () -> {
                if (stand != null && !stand.isRemoved()) stand.discard();
            });
            for (Entity entity : new ArrayList<>(loose)) failure = attemptCleanup(failure, failed, () -> {
                if (!entity.isRemoved()) entity.discard();
            });
            for (var block : restored.entrySet()) failure = attemptCleanup(failure, failed, () -> {
                level.setBlockAndUpdate(block.getKey(), block.getValue());
                if (!level.getBlockState(block.getKey()).equals(block.getValue()))
                    throw new IllegalStateException("Owned terrain was not restored at " + block.getKey());
            });
            failure = attemptCleanup(failure, failed, () -> {
                cleanupComplete = !failed[0] && remainingEntities() == 0 && (power == null || !power.isSummoned())
                        && restored.entrySet().stream().allMatch(block -> level.getBlockState(block.getKey()).equals(block.getValue()));
                log("SCENE-CLEANUP " + kind + " remainingEntities=" + remainingEntities() + " complete=" + cleanupComplete);
                if (!cleanupComplete && !failed[0]) throw new IllegalStateException("Owned scene cleanup residue remains");
            });
            if (failed[0]) cleanupComplete = false;
            return failure;
        }
        int remainingEntities() {
            return (int) loose.stream().filter(entity -> !entity.isRemoved() || level.getEntity(entity.getUUID()) != null).count()
                    + (stand != null && (!stand.isRemoved() || level.getEntity(stand.getUUID()) != null) ? 1 : 0);
        }
    }

    private enum CleanupKind { UNREGISTER, ENTITY_REMOVE }
    private static final class CleanupFailure extends RuntimeException {
        CleanupFailure(String message) { super(message); }
    }
    private static final class CleanupProbe implements GameTestListener {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        Suite suite;
        Scene scene;
        Cow sentinel;
        UnregisterProbe first;
        Consumer<EntityTickEvent.Pre> later;
        CleanupKind kind;
        CleanupFailure primary;
        CleanupFailure cleanup;
        RuntimeException caught;
        int index, phase, waitTicks, checks, firstCalls, laterCalls, sentinelAgeAtClose;
        long nativeSignalTime = -1;
        boolean complete;
        CleanupProbe(GameTestHelper helper) { this.helper = helper; }
        void setup() {
            kind = CleanupKind.values()[index++];
            primary = new CleanupFailure(kind + " initial caught native-event failure");
            cleanup = new CleanupFailure(kind + " actual owned cleanup failure");
            suite = new Suite(helper); scene = new Scene(suite, Kind.BOAT_RULE_OFF); suite.scene = scene; scene.prepare();
            if (kind == CleanupKind.ENTITY_REMOVE) {
                Player owned = new RemovalProbePlayer(scene.level, cleanup);
                owned.moveTo(scene.origin.x - 1, scene.origin.y, scene.origin.z, 0, 0);
                helper.assertTrue(scene.level.addFreshEntity(owned), "Actual owned removal-probe Player added");
                scene.loose.addFirst(owned);
            }
            suite.multiplier.set(Double.compare(suite.oldMultiplier, 0.1) == 0 ? 0.2 : 0.1);
            scene.level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(!suite.oldBreaks, scene.level.getServer());
            helper.assertTrue(Double.compare(actualMultiplier(), suite.oldMultiplier) != 0
                    && actualBreaks() != suite.oldBreaks, "Both actual owned config/rule values changed before cleanup");
            sentinel = new Cow(EntityType.COW, scene.level); sentinel.setNoAi(true); sentinel.setNoGravity(true);
            sentinel.moveTo(scene.origin.x + 7, scene.origin.y + 1, scene.origin.z, 0, 0);
            helper.assertTrue(scene.level.addFreshEntity(sentinel) && !sentinel.isPassenger(), "Natural tick sentinel added, not a passenger");
            suite.listen();
            first = new UnregisterProbe(this);
            later = event -> { if (event.getEntity() == sentinel && phase >= 2) laterCalls++; };
            suite.listeners.addFirst(first); NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, EntityTickEvent.Pre.class, first);
            suite.listeners.add(later); NeoForge.EVENT_BUS.addListener(EventPriority.LOW, EntityTickEvent.Pre.class, later);
            helper.assertTrue(active == suite && scene.remainingEntities() >= 3 && scene.power.isSummoned()
                    && !scene.stand.isRemoved() && scene.restored.entrySet().stream()
                    .anyMatch(block -> !scene.level.getBlockState(block.getKey()).equals(block.getValue())),
                    "Actual owned actors/linked Stand/changed terrain/listeners exist before cleanup; no combat input");
            phase = 0; waitTicks = 0; nativeSignalTime = -1; firstCalls = 0; laterCalls = 0; caught = null;
            cleanupLog("BEFORE " + state() + " ownedUUIDs=" + ownedUuids()
                    + " primary=" + primary.getMessage() + " cleanup=" + cleanup.getMessage());
        }
        void tick() {
            if (complete) return;
            try {
                if (scene == null) {
                    if (index == CleanupKind.values().length) {
                        complete = true;
                        cleanupLog("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        helper.assertTrue(failures.isEmpty(), "Nonliving impact fixture exceptional cleanup failed; see CLEANUP SUMMARY");
                        helper.succeed(); return;
                    }
                    setup(); return;
                }
                helper.assertTrue(++waitTicks < 70, "Natural cleanup probe timeout");
                if (phase == 0 && nativeSignalTime >= 0) {
                    try { suite.tick(); } catch (RuntimeException error) { caught = error; }
                    phase = 2; waitTicks = 0; sentinelAgeAtClose = sentinel.tickCount;
                    check(caught == primary, "actual caught native-event primary identity preserved");
                    check(caught != null && caught.getSuppressed().length == 1 && caught.getSuppressed()[0] == cleanup,
                            "exact distinct owned cleanup error suppressed on primary");
                    check(suite.closed && scene.closed, "both owners stop normal callbacks despite first cleanup exception");
                    check(scene.remainingEntities() == 0, "every owned Player/Stand/primary UUID removed from native world");
                    check(!scene.power.isSummoned(), "actual summoned ownership unlinked despite earlier cleanup exception");
                    check(terrainRestored(), "every original owned block state restored despite earlier exception");
                    check(Double.compare(actualMultiplier(), suite.oldMultiplier) == 0, "actual server multiplier restored");
                    check(actualBreaks() == suite.oldBreaks, "actual BREAK_BLOCKS restored");
                    check(suite.listeners.size() == (kind == CleanupKind.UNREGISTER ? 1 : 0),
                            "pending listener inventory equals actual failed primitive, not unconditional zero");
                    check(!suite.cleanupComplete && active == suite, "failed primitive retains incomplete Suite ownership for retry");
                    check(scene.cleanupComplete == (kind == CleanupKind.UNREGISTER),
                            "Scene completion reflects actual failed delegated removal, not merely visible residue");
                    cleanupLog("FIRST " + state() + " caught=" + describe(caught)); return;
                }
                if (phase == 2 && waitTicks >= 3) {
                    helper.assertTrue(sentinel.tickCount >= sentinelAgeAtClose + 2 && !sentinel.isPassenger(),
                            "Further real sentinel world ticks qualify listener presence/absence");
                    check(laterCalls == 0, "later actual native listener removed despite earlier failure");
                    check(kind == CleanupKind.UNREGISTER ? firstCalls > 0 : firstCalls == 0,
                            "actual failed unregister remains live, otherwise first listener is absent");
                    RuntimeException retry = null;
                    try { suite.close(); } catch (RuntimeException error) { retry = error; }
                    check(retry == null, "idempotent own retry has no repeated one-shot cleanup error");
                    check(settled(), "retry settles all actual own actors/terrain/config/listeners and releases own active");
                    check(suite.cleanupComplete && scene.cleanupComplete, "owners complete only after successful retry");
                    phase = 3; waitTicks = 0; firstCalls = 0; laterCalls = 0; sentinelAgeAtClose = sentinel.tickCount;
                    cleanupLog("RETRY " + state() + " failure=" + describe(retry)); return;
                }
                if (phase == 3 && waitTicks >= 3) {
                    helper.assertTrue(sentinel.tickCount >= sentinelAgeAtClose + 2, "Post-retry real sentinel ticks occurred");
                    check(firstCalls == 0 && laterCalls == 0, "both actual native listeners remain absent after retry");
                    cleanupLog("PRE-RECOVERY " + state() + " ownedUUIDs=" + ownedUuids());
                    Throwable recovery = recover(null);
                    helper.assertTrue(recovery == null && settled() && sentinel.isRemoved()
                            && scene.level.getEntity(sentinel.getUUID()) == null,
                            "Only after orphan measurement, exact-owned recovery removes actual residue");
                    scene = null; suite = null; sentinel = null; return;
                }
            }
            catch (RuntimeException | Error error) {
                Throwable failure = recover(error); complete = true; throwCleanup(failure);
            }
        }
        void check(boolean value, String detail) {
            checks++; cleanupLog((value ? "PASS " : "FAIL ") + kind + " " + detail);
            if (!value) failures.add(kind + ": " + detail);
        }
        double actualMultiplier() { return JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get(); }
        boolean actualBreaks() { return suite.level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS); }
        boolean terrainRestored() {
            return scene.restored.entrySet().stream().allMatch(block -> scene.level.getBlockState(block.getKey()).equals(block.getValue()));
        }
        boolean settled() {
            return scene.remainingEntities() == 0 && !scene.power.isSummoned() && terrainRestored()
                    && Double.compare(actualMultiplier(), suite.oldMultiplier) == 0 && actualBreaks() == suite.oldBreaks
                    && suite.listeners.isEmpty() && active != suite;
        }
        String ownedUuids() {
            return scene.loose.stream().map(entity -> entity.getUUID() + ":removed=" + entity.isRemoved()
                    + ":lookup=" + (scene.level.getEntity(entity.getUUID()) != null)).toList()
                    + " stand=" + scene.stand.getUUID() + ":lookup=" + (scene.level.getEntity(scene.stand.getUUID()) != null);
        }
        String state() {
            if (suite == null) return "kind=" + kind + " suite=null";
            return "kind=" + kind + " world=" + suite.level.getGameTime() + " remainingEntities="
                    + (scene == null ? -1 : scene.remainingEntities()) + " blocksRestored="
                    + (scene != null && terrainRestored()) + " M=" + actualMultiplier() + "/" + suite.oldMultiplier
                    + " BREAK_BLOCKS=" + actualBreaks() + "/" + suite.oldBreaks + " listenersPending=" + suite.listeners.size()
                    + " firstCalls=" + firstCalls + " laterCalls=" + laterCalls + " activeOwned=" + (active == suite)
                    + " suiteComplete=" + suite.cleanupComplete + " sceneComplete=" + (scene != null && scene.cleanupComplete);
        }
        Throwable recover(Throwable failure) {
            if (suite == null) return failure;
            suite.closed = true; if (scene != null) scene.closed = true;
            for (Object listener : new ArrayList<>(suite.listeners)) failure = attemptCleanup(failure, () -> {
                NeoForge.EVENT_BUS.unregister(listener); suite.listeners.remove(listener);
            });
            if (scene != null) {
                if (scene.user != null) failure = attemptCleanup(failure, () -> AbilityInput.keyRelease(ATTACK, scene.user));
                failure = attemptCleanup(failure, () -> {
                    if (scene.power != null && scene.power.isSummoned()) scene.type.forceUnsummon(scene.user, scene.power);
                });
                failure = attemptCleanup(failure, () -> { if (scene.stand != null && !scene.stand.isRemoved()) scene.stand.discard(); });
                for (Entity entity : new ArrayList<>(scene.loose)) failure = attemptCleanup(failure, () -> { if (!entity.isRemoved()) entity.discard(); });
                for (var block : scene.restored.entrySet()) failure = attemptCleanup(failure, () -> scene.level.setBlockAndUpdate(block.getKey(), block.getValue()));
            }
            if (sentinel != null) failure = attemptCleanup(failure, () -> { if (!sentinel.isRemoved()) sentinel.discard(); });
            failure = attemptCleanup(failure, () -> suite.multiplier.set(suite.oldMultiplier));
            failure = attemptCleanup(failure, () -> suite.level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(suite.oldBreaks, suite.level.getServer()));
            failure = attemptCleanup(failure, () -> {
                boolean clean = suite.listeners.isEmpty() && Double.compare(actualMultiplier(), suite.oldMultiplier) == 0
                        && actualBreaks() == suite.oldBreaks && (scene == null || scene.remainingEntities() == 0
                        && (scene.power == null || !scene.power.isSummoned()) && terrainRestored())
                        && (sentinel == null || sentinel.isRemoved() && suite.level.getEntity(sentinel.getUUID()) == null);
                if (clean && active == suite) active = null;
                if (!clean) throw new IllegalStateException("Owned recovery residue remains");
            });
            Throwable result = failure;
            return attemptCleanup(result, () -> cleanupLog("RECOVERY " + state() + " failure=" + describe(result)));
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { throwCleanup(recover(null)); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { throwCleanup(recover(null)); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { throwCleanup(recover(null)); }
    }
    private static final class UnregisterProbe implements Consumer<EntityTickEvent.Pre> {
        final CleanupProbe owner;
        boolean throwNextHash;
        UnregisterProbe(CleanupProbe owner) { this.owner = owner; }
        @Override public void accept(EntityTickEvent.Pre event) {
            if (event.getEntity() != owner.sentinel) return;
            if (owner.phase == 0 && owner.nativeSignalTime < 0) {
                owner.nativeSignalTime = owner.scene.level.getGameTime();
                owner.suite.callback(() -> { throw owner.primary; });
                throwNextHash = owner.kind == CleanupKind.UNREGISTER;
                cleanupLog("NATIVE-CALLBACK kind=" + owner.kind + " sentinelUUID=" + event.getEntity().getUUID()
                        + " world=" + owner.nativeSignalTime + " primaryCaught=" + (owner.suite.callbackFailure == owner.primary));
            }
            else if (owner.phase >= 2) owner.firstCalls++;
        }
        @Override public int hashCode() {
            if (throwNextHash) {
                throwNextHash = false; cleanupLog("NATIVE-UNREGISTER-HASH kind=" + owner.kind + " failure=" + owner.cleanup.getMessage());
                throw owner.cleanup;
            }
            return System.identityHashCode(this);
        }
    }
    private static final class RemovalProbePlayer extends Player {
        final RuntimeException failure;
        boolean throwOnce = true;
        RemovalProbePlayer(ServerLevel level, RuntimeException failure) {
            super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "impact-cleanup-resource"));
            this.failure = failure; setNoGravity(true); noPhysics = true;
        }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public void remove(RemovalReason reason) {
            super.remove(reason);
            if (throwOnce && reason == RemovalReason.DISCARDED) {
                throwOnce = false; cleanupLog("NATIVE-REMOVE playerUUID=" + getUUID() + " delegatedRemoved=" + isRemoved());
                throw failure;
            }
        }
    }
    private static Throwable attemptCleanup(Throwable primary, Runnable action) {
        try { action.run(); } catch (RuntimeException | Error later) {
            if (primary == null) return later;
            if (primary != later && java.util.Arrays.stream(primary.getSuppressed()).noneMatch(error -> error == later))
                primary.addSuppressed(later);
        }
        return primary;
    }
    private static Throwable attemptCleanup(Throwable primary, boolean[] failed, Runnable action) {
        return attemptCleanup(primary, () -> {
            try { action.run(); } catch (RuntimeException | Error error) { failed[0] = true; throw error; }
        });
    }
    private static void throwCleanup(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
    private static String describe(Throwable failure) {
        return failure == null ? "none" : failure.getClass().getName() + ":" + failure.getMessage()
                + " suppressed=" + java.util.Arrays.stream(failure.getSuppressed()).map(Throwable::getMessage).toList();
    }
    private static void cleanupLog(String value) { JojoMod.getLogger().info("CORE-NONLIVING-CLEANUP " + value); }

    private static final class ObservedAttackerStand extends StandEntity {
        final Scene scene;
        ObservedAttackerStand(Scene scene, String name) {
            super(switch (name) {
                case "the_world" -> ModEntityTypes.THE_WORLD_STAND.get();
                case "crazy_diamond" -> ModEntityTypes.CRAZY_DIAMOND_STAND.get();
                default -> ModEntityTypes.STAR_PLATINUM_STAND.get();
            }, scene.level); this.scene = scene;
        }
        @Override public boolean hurtWithStandAttack(Entity target, DamageSource source, float raw) {
            float boatBefore = target instanceof Boat boat ? boat.getDamage() : 0;
            float healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0;
            double multiplier = JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get();
            int before = ++scene.order; Entity standBook = getLastHurtMob(); Entity userBook = scene.user.getLastHurtMob();
            boolean result = super.hurtWithStandAttack(target, source, raw);
            int after = ++scene.order;
            Hit hit = new Hit(target, source, raw, multiplier, result, boatBefore,
                    target instanceof Boat boat ? boat.getDamage() : 0, healthBefore,
                    target instanceof LivingEntity living ? living.getHealth() : 0, before, after, standBook, userBook);
            if (!scene.hitCaptured && (target == scene.primary || target == scene.secondary)
                    && scene.hits.put(target, hit) != null) scene.invalid = new IllegalStateException("Duplicate tracked virtual hurt dispatch");
            log("REAL-HELPER-RETURN " + scene.kind + " target=" + target.getId() + " source=" + source.getMsgId()
                    + " rawOperand=" + raw + " serverM=" + multiplier + " scaledDelegateOperand=" + raw * multiplier
                    + " actionPhase=" + (scene.action != null ? scene.action.getPhase() : null)
                    + " result=" + result + " boatDamage=" + hit.boatBefore + "/" + hit.boatAfter
                    + " health=" + hit.healthBefore + "/" + hit.healthAfter + " orders=" + before + "/" + after);
            return result;
        }
    }
    private static final class TestPlayer extends Player {
        TestPlayer(ServerLevel level, String name) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), name)); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }
    private static void identity() {
        Class<?>[] types = {CoreNonlivingHeavyImpactGameTests.class, ObservedAttackerStand.class,
                Suite.class, Scene.class, CleanupProbe.class, CleanupKind.class, CleanupFailure.class,
                UnregisterProbe.class, RemovalProbePlayer.class, TestPlayer.class,
                StandEntityHeavyPunchAbility.class, StandEntityHeavyPunch.class, TheWorldKick.class,
                StandInitStarPlatinum.class, StandInitTheWorld.class, EntityActionInstance.class,
                StandEntity.class, KnockbackCollisionImpact.class, DamageUtil.class, AbilityInput.class};
        boolean bound = Boolean.getBoolean("rotp.nonliving.bound") || System.getProperties().stringPropertyNames().stream()
                .anyMatch(name -> name.startsWith("rotp.nonliving.expected."));
        for (Class<?> type : types) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                String expected = System.getProperty("rotp.nonliving.expected." + type.getName());
                if (bound && (expected == null || !expected.equalsIgnoreCase(actual))) throw new IllegalStateException("Bound class mismatch " + type.getName());
                log("IDENTITY " + type.getName() + " sha256=" + actual + " source="
                        + type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString() + " resource="
                        + type.getResource("/" + type.getName().replace('.', '/') + ".class").toURI().toASCIIString() + " bound=" + bound);
            }
            catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
        }
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 0.0002; }
    private static void log(String value) { JojoMod.getLogger().info("CORE-NONLIVING-IMPACT " + value); }
}
