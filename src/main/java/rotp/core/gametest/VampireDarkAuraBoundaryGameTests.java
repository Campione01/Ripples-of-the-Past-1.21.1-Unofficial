package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.VampirismUtil;
import rotp.core.impl.powers.vampirism.abilities.VampirismDarkAuraAbility;
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
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireDarkAuraBoundaryGameTests {
    private static final double EPS = 1.0E-5;
    private static final List<Holder<MobEffect>> DEBUFFS = List.of(MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN);
    private VampireDarkAuraBoundaryGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_dark_aura_vertical_boundary", timeoutTicks = 60)
    public static void darkAuraUsesTheDonorCenteredVerticalBounds(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private enum Placement {
        INSIDE(-2, 24.5, true, true), EXCESS(0, 25.2, false, true), FAR(2, 26.1, false, false);
        final double x, y;
        final boolean donor, expanded;
        Placement(double x, double y, boolean donor, boolean expanded) {
            this.x = x; this.y = y; this.donor = donor; this.expanded = expanded;
        }
    }

    private record Added(Holder<MobEffect> effect, int duration, int amplifier, UUID source,
            int cowPosts, int userAge, long time) {}

    private static final class Subject {
        final Placement placement;
        final Cow cow;
        final Vec3 position;
        final AABB initialBox;
        final List<Added> additions = new ArrayList<>();
        int posts;
        Subject(Placement placement, Cow cow, Vec3 position) {
            this.placement = placement; this.cow = cow; this.position = position; initialBox = cow.getBoundingBox();
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 30;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final Map<UUID, Subject> subjects = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private StandPower stand;
        private VampirismData data;
        private VampirismDarkAuraAbility ability;
        private EntityActionInstance action, observedPerform;
        private ChunkPos chunk;
        private Vec3 feet;
        private AABB room;
        private BlockPos roomMin, roomMax;
        private Throwable observerFailure;
        private int userPosts, grantPosts, pressPosts;
        private float frameBlood, framePassive;
        private long frameTime;
        private boolean roomVerified, powered, pressed, pressing, released, paid, userFrame, closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL && !level.dimensionType().ultraWarm(), "World is not ordinary Normal");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = template.getY() + 32;
            feet = new Vec3(x + 8.5, y, z + 6.5);
            roomMin = new BlockPos(x + 5, y, z + 4);
            roomMax = new BlockPos(x + 12, y + 29, z + 10);
            room = AABB.encapsulatingFullBlocks(roomMin, roomMax);
            premise(roomMin.getY() >= level.getMinBuildHeight() && roomMax.getY() < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "Owned column unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "Owned column is obstructed");
            }
            roomVerified = true;
            BlockPos center = BlockPos.containing(feet);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 3, -1), center.offset(1, 3, 1))) {
                BlockPos key = pos.immutable();
                premise(level.getBlockEntity(key) == null, "Roof cell has a block entity");
                roof.put(key, level.getBlockState(key));
                premise(level.setBlock(key, Blocks.STONE.defaultBlockState(), 3), "Roof placement failed");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(feet.x, feet.y, feet.z, 0, 0);
            premise(level.addFreshEntity(user), "User join failed");
            for (Placement placement : Placement.values()) {
                Cow cow = EntityType.COW.create(level);
                premise(cow != null, "Cow construction failed");
                cow.setNoGravity(true); cow.setNoAi(true);
                Vec3 position = feet.add(placement.x, placement.y, 2);
                cow.moveTo(position.x, position.y, position.z, 0, 0);
                Subject subject = new Subject(placement, cow, position);
                subjects.put(cow.getUUID(), subject);
                premise(level.addFreshEntity(cow) && cow.getHealth() == 10F && cow.getMaxHealth() == 10F
                        && Math.abs(cow.getBbWidth() - 0.9F) < EPS && Math.abs(cow.getBbHeight() - 1.4F) < EPS
                        && cow.getActiveEffects().isEmpty(), "Default Cow state differs");
            }
            registerObservers();
            log("setup feet=" + feet + " room=" + room + " roof=" + roof.keySet()
                    + " stationaryCasterNoGravity=true cowsNoGravityNoAI=true effectScope=threeVanillaDebuffs");
            for (Subject subject : subjects.values()) log("subject=" + subject.placement + " id=" + subject.cow.getUUID()
                    + " box=" + subject.initialBox + " health=" + subject.cow.getHealth());
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user || subjects.containsKey(event.getEntity().getUUID())) {
                    premise(!event.isCanceled() && ticking(event.getEntity().position()), "Owned natural tick unavailable");
                }
            });
            Consumer<EntityTickEvent.Post> beforeAttachments = event -> observe(() -> {
                if (event.getEntity() != user || !powered) return;
                premise(!userFrame, "Nested actor Post frame");
                userFrame = true; frameBlood = blood(); frameTime = level.getGameTime();
                framePassive = Math.max(0F, VampirismUtil.bloodTickDown(user));
            });
            Consumer<MobEffectEvent.Added> effects = event -> observe(() -> {
                Subject subject = subjects.get(event.getEntity().getUUID());
                if (subject == null) return;
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                premise(pressed && (pressing || userFrame) && current instanceof VampirismDarkAuraAbility.DarkAuraInstance
                        && current.ability == ability && current.getPhase() == ActionPhase.PERFORM
                        && (action == null || current == action) && event.getOldEffectInstance() == null,
                        "Effect is not from the one registered PERFORM window");
                if (observedPerform == null) observedPerform = current;
                premise(observedPerform == current, "A second Aura action produced an effect");
                requireGeometry();
                MobEffectInstance effect = event.getEffectInstance();
                Added sample = new Added(effect.getEffect(), effect.getDuration(), effect.getAmplifier(),
                        id(event.getEffectSource()), subject.posts, user.tickCount, level.getGameTime());
                subject.additions.add(sample);
                log("effect-added target=" + subject.placement + " id=" + subject.cow.getUUID() + " event=" + sample
                        + " donor=" + donorBox().intersects(subject.cow.getBoundingBox())
                        + " expanded=" + expandedBox().intersects(subject.cow.getBoundingBox())
                        + " blood=" + blood() + " phase=" + current.getPhase() + "/" + current.getPhaseTick());
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                Subject subject = subjects.get(event.getEntity().getUUID());
                if (subject != null) subject.posts++;
                if (event.getEntity() != user) return;
                userPosts++;
                if (!powered) return;
                premise(userFrame && frameTime == level.getGameTime(), "Actor Post pair missing");
                float actionDebit = frameBlood - blood() - framePassive;
                log("user-post count=" + userPosts + " blood=" + frameBlood + "->" + blood()
                        + " passive=" + framePassive + " actionDebit=" + actionDebit
                        + " cooldown=" + data.getAbilityCooldown(ability.name()) + " phase=" + phase());
                recordDebit(actionDebit, "natural-post");
                premise(Math.abs(data.getBloodLevel() - blood()) < 1.0E-4, "Blood state/data diverged");
                if (pressed) premise(data.getAbilityCooldown(ability.name()) == 300 - (userPosts - pressPosts),
                        "Aura cooldown did not follow natural actor ticks");
                userFrame = false;
            });
            listeners.add(pre); listeners.add(beforeAttachments); listeners.add(effects); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, beforeAttachments);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Added.class, effects);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void grantVampirism() {
            premise(shadeReady() && ticking(user.position()) && user.getActiveEffects().isEmpty(), "Shade must precede vampire grant");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            stand = PowerClass.STAND.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, user);
            user.setHealth(user.getMaxHealth());
            VampirismState.get(user).blood().setCurrent(100F);
            data.setBloodLevel(100F);
            var found = power.getAbility("vampirism_dark_aura");
            premise(found instanceof VampirismDarkAuraAbility && found.abilityType == VampirismPowerType.VAMPIRE_DARK_AURA.get(),
                    "Wrong registered Aura");
            ability = (VampirismDarkAuraAbility) found;
            powered = true; grantPosts = userPosts;
            log("grant user=" + user.getUUID() + " posts=" + userPosts + " blood=" + blood() + " shaded=true");
        }

        private void requireEligibility() {
            premise(powered && power.getPowerType() == ModPlayerPowers.VAMPIRISM.get() && power.canUsePower()
                    && data.isVampireAtFullPower() && !data.isBeingCured() && data.getCuringStage(user) == 0
                    && stand.getPowerType() == null && !stand.hasPower() && stand.getSummonedStandEntity() == null
                    && user.isAlive() && !user.isCreative() && !user.isSpectator() && !user.getAbilities().instabuild
                    && !user.getAbilities().invulnerable && user.getPose() == Pose.STANDING
                    && user.getHealth() == user.getMaxHealth() && !user.isOnFire() && blood() > 25F
                    && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                    && level.getDifficulty() == Difficulty.NORMAL && shadeReady(), "Caster eligibility changed");
        }

        private boolean shadeReady() {
            premise(roof.size() == 9 && roof.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                    "Owned roof changed");
            BlockPos eye = BlockPos.containing(user.getEyePosition());
            boolean shaded = !level.canSeeSky(eye);
            log("shade-readiness posts=" + userPosts + " eye=" + eye + " shaded=" + shaded + " roofCells=" + roof.size());
            return shaded;
        }

        private AABB donorBox() {
            Vec3 center = user.getBoundingBox().getCenter();
            return new AABB(center.subtract(24, 24, 24), center.add(24, 24, 24));
        }
        private AABB expandedBox() { return user.getBoundingBox().inflate(24); }

        private void requireGeometry() {
            AABB donor = donorBox(), expanded = expandedBox();
            premise(level.getDifficulty() == Difficulty.NORMAL && user.isNoGravity()
                    && user.position().distanceTo(feet) < EPS && ticking(user.position()), "Caster moved or lost the declared Normal scene");
            List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, expanded, entity -> entity != user && entity.isAlive());
            log("query donor=" + donor + " oldExpanded=" + expanded + " candidates=" + candidates.stream().map(Entity::getUUID).toList());
            premise(candidates.stream().allMatch(entity -> subjects.containsKey(entity.getUUID())), "Aura area contains a foreign living entity");
            for (Subject subject : subjects.values()) {
                Cow cow = subject.cow;
                AABB box = cow.getBoundingBox();
                premise(cow.isAlive() && cow.getHealth() == 10F && cow.getMaxHealth() == 10F && !cow.isInvulnerable()
                        && cow.isNoAi() && cow.isNoGravity() && !JojoDefinitions.isUndeadOrVampiric(cow)
                        && cow.position().distanceTo(subject.position) < EPS && box.equals(subject.initialBox)
                        && ticking(cow.position()) && ticking(new Vec3(box.minX, box.minY, box.minZ))
                        && ticking(new Vec3(box.maxX, box.maxY, box.maxZ)), "Cow changed its declared default stationary geometry");
                premise(box.minX > donor.minX && box.maxX < donor.maxX && box.minZ > donor.minZ && box.maxZ < donor.maxZ
                        && donor.intersects(box) == subject.placement.donor
                        && expanded.intersects(box) == subject.placement.expanded
                        && candidates.contains(cow) == subject.placement.expanded,
                        "Measured boxes do not separate the vertical boundary");
                if (subject.placement == Placement.EXCESS) premise(box.minY > donor.maxY && box.minY < expanded.maxY,
                        "Excess target is not vertically between the two upper faces");
                if (subject.placement == Placement.FAR) premise(box.minY > expanded.maxY, "Far control is not outside both boxes");
            }
        }

        private void press() {
            requireEligibility(); requireGeometry();
            premise(!userFrame && !data.isAbilityOnCooldown(ability.name())
                    && subjects.values().stream().allMatch(subject -> subject.cow.getActiveEffects().isEmpty()),
                    "Input or initial effects are not fresh");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "Registered Aura CLICK rejected");
            log("press boxes donor=" + donorBox() + " oldExpanded=" + expandedBox() + " userBox=" + user.getBoundingBox());
            for (Subject subject : subjects.values()) log("geometry target=" + subject.placement + " box=" + subject.cow.getBoundingBox()
                    + " donor=" + subject.placement.donor + " oldExpanded=" + subject.placement.expanded);
            float beforeBlood = blood(); pressPosts = userPosts; pressed = true; pressing = true;
            try {
                var held = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK, 0F,
                        BufferingState.clickOnly(), ability.getAbilityId());
                premise(held != null && held.action instanceof VampirismDarkAuraAbility.DarkAuraInstance aura
                        && aura.ability == ability, "CLICK did not return the registered Aura instance");
                action = (EntityActionInstance) held.action;
                premise(observedPerform == null || observedPerform == action, "Synchronous effects used a different action");
                premise(action.isOver() || LivingComponentAction.getCurEntityAction(user) == action, "Returned action is not the installed action");
            }
            finally { pressing = false; }
            float inputDebit = beforeBlood - blood();
            log("after-input blood=" + beforeBlood + "->" + blood() + " debit=" + inputDebit
                    + " cooldown=" + data.getAbilityCooldown(ability.name()) + " phase=" + phase());
            recordDebit(inputDebit, "input");
            premise(data.getAbilityCooldown(ability.name()) == 300, "Aura did not set its ordinary cooldown300");
        }

        private void recordDebit(float debit, String where) {
            if (Math.abs(debit) < 1.0E-4) return;
            premise(pressed && !paid && Math.abs(debit - 25F) < 1.0E-4, "Unexpected Aura debit in " + where);
            paid = true;
        }

        private void validate() {
            requireEligibility(); requireGeometry();
            premise(paid && userPosts > pressPosts && action != null && action.isOver()
                    && LivingComponentAction.getCurEntityAction(user) == null && !userFrame,
                    "One paid action has not naturally completed after actor Post");
            List<String> mismatches = new ArrayList<>();
            for (Subject subject : subjects.values()) {
                log("result target=" + subject.placement + " box=" + subject.cow.getBoundingBox() + " posts=" + subject.posts
                        + " additions=" + subject.additions + " active=" + subject.cow.getActiveEffects());
                if (subject.placement == Placement.INSIDE) {
                    for (Holder<MobEffect> effect : DEBUFFS) {
                        List<Added> additions = subject.additions.stream().filter(sample -> sample.effect.equals(effect)).toList();
                        MobEffectInstance current = subject.cow.getEffect(effect);
                        if (additions.size() != 1 || current == null) {
                            mismatches.add("inside-missing-or-repeated:" + effect); continue;
                        }
                        Added sample = additions.getFirst();
                        int expectedRemaining = sample.duration - (subject.posts - sample.cowPosts);
                        if (sample.duration != 600 || sample.amplifier != 1 || current.getAmplifier() != 1
                                || current.getDuration() != expectedRemaining || sample.source != null) {
                            mismatches.add("inside-effect-state:" + effect);
                        }
                    }
                }
                else if (!subject.additions.isEmpty() || !subject.cow.getActiveEffects().isEmpty()) {
                    mismatches.add("outside-donor-effects:" + subject.placement);
                }
            }
            if (observedPerform != action) mismatches.add("no-real-perform-effects");
            log("oracle paid25=" + paid + " cooldown=" + data.getAbilityCooldown(ability.name()) + " mismatches=" + mismatches);
            helper.assertTrue(mismatches.isEmpty(), "AURA-ORACLE: effects did not respect the donor centered vertical bounds");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 45, "Watchdog expired before the one-cast boundary observation");
                if (userPosts >= 2 && subjects.values().stream().allMatch(subject -> subject.posts >= 2) && !pressed) {
                    if (shadeReady()) {
                        if (!powered) grantVampirism();
                        else if (userPosts >= grantPosts + 2) press();
                    }
                }
                if (pressed && userPosts > pressPosts && action != null && action.isOver()) {
                    validate(); release(); close(); helper.succeed(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private boolean ticking(Vec3 position) {
            BlockPos pos = BlockPos.containing(position);
            return new ChunkPos(pos).equals(chunk) && level.isPositionEntityTicking(pos);
        }
        private float blood() { return VampirismState.get(user).blood().current(); }
        private String phase() { return action == null ? "unreturned" : action.getPhase() + "/" + action.getPhaseTick(); }
        private void release() { if (pressed && !released) { AbilityInput.keyRelease(KEY, user); released = true; } }
        private void premise(boolean condition, String message) { helper.assertTrue(condition, "AURA-PREMISE: " + message); }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error; JojoMod.LOGGER.error("VAMPIRE-AURA-BOUNDARY observer failure", error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("VAMPIRE-AURA-BOUNDARY {}", message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            try {
                for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
                listeners.clear();
            }
            finally {
                cleanupStep(this::release, failures);
                for (Subject subject : subjects.values()) cleanupStep(subject.cow::discard, failures);
                if (user != null) {
                    cleanupStep(user::stopUsingItem, failures);
                    cleanupStep(() -> user.getInventory().clearContent(), failures);
                    cleanupStep(user::discard, failures);
                }
                for (Map.Entry<BlockPos, BlockState> entry : roof.entrySet()) {
                    cleanupStep(() -> level.setBlock(entry.getKey(), entry.getValue(), 3), failures);
                }
                cleanupStep(() -> {
                    premise(subjects.values().stream().allMatch(subject -> subject.cow.isRemoved()
                            && level.getEntity(subject.cow.getUUID()) == null)
                            && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null), "Owned entity cleanup incomplete");
                    for (Map.Entry<BlockPos, BlockState> entry : roof.entrySet()) {
                        premise(entry.getValue().isAir() && level.getBlockState(entry.getKey()).equals(entry.getValue())
                                && level.getBlockEntity(entry.getKey()) == null, "Owned roof AIR restoration failed");
                    }
                    if (roomVerified) for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                        premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "Owned column changed");
                    }
                    if (user != null && action != null) premise(!AbilityInput.isHeldByKey(user, action), "Owned input remains held");
                }, failures);
            }
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("AURA-CLEANUP: verification failed");
                failures.forEach(error::addSuppressed); throw error;
            }
            log("cleanup-verified listeners=0 subjects=" + subjects.size() + " roofAirRestored=" + roof.size() + " actorRemoved=true");
        }
        private void cleanupStep(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        private void closeAfterFailure(Throwable primary) {
            try { close(); } catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup); JojoMod.LOGGER.error("Aura cleanup failed; original failure retained", cleanup);
            }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
            if (test.getError() != null) closeAfterFailure(test.getError()); else close();
        }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }

    private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
}
