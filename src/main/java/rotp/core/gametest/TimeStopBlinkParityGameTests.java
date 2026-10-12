package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopTicksModifiers;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopBlinkParityGameTests {
    private static final String BATCH = "time_stop_blink_parity";
    private static final short INPUT = 171;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc("time_stop_blink_parity_gametest"));
    private static final Map<UUID, IntUnaryOperator> REACH_MODIFIERS = new ConcurrentHashMap<>();

    static {
        TimeStopTicksModifiers.register(JojoMod.resLoc("time_stop_blink_parity_gametest"),
                (user, ticks) -> {
                    IntUnaryOperator modifier = REACH_MODIFIERS.get(user.getUUID());
                    return modifier == null ? ticks : modifier.applyAsInt(ticks);
                });
    }

    private TimeStopBlinkParityGameTests() {}

    private enum Kind {
        EMPTY, CREATIVE, RESOLVE, ZERO_MODIFIER, ZERO_AFTER_PAYMENT, RESOURCE_MODIFIER, BASE_COOLDOWN, BLINK_COOLDOWN,
        LIVING, NONLIVING, BLOCK, STALE, FAR, SELF, OWN_STAND, STAND_IN_RANGE,
        STAND_OUT_OF_RANGE, EMPTY_BLOCK, FAR_BLOCK, UNPICKABLE, SPECTATOR,
        RAY_LIVING, RAY_NONLIVING, PITCH
    }

    private record Spec(String stand, Kind kind, float stamina) {
        String id() { return stand + "/" + kind + "/" + stamina; }
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
    public static void registeredBlinkUsesDonorPaymentAndClickedTarget(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        suite.identity();
        helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standStamina.get(), "Finite stamina config required");
        for (String stand : new String[] {"star_platinum", "the_world"}) {
            for (float stamina : new float[] {0, 179, 180, 200, 720, 730})
                suite.run(new Spec(stand, Kind.EMPTY, stamina));
            for (Kind kind : Kind.values())
                if (kind != Kind.EMPTY) suite.run(new Spec(stand, kind,
                        kind == Kind.BASE_COOLDOWN ? 730 : kind == Kind.ZERO_AFTER_PAYMENT || kind == Kind.RESOURCE_MODIFIER ? 300 : 200));
        }
        log("SUMMARY scenes=" + suite.scenes + " checks=" + suite.checks + " failures=" + suite.failures.size()
                + " details=" + suite.failures);
        helper.assertTrue(suite.failures.isEmpty(), "Blink parity failures; see SUMMARY");
        helper.succeed();
    }

    private static final class Suite {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        int scenes;
        int checks;

        Suite(GameTestHelper helper) { this.helper = helper; }

        void identity() {
            for (Class<?> type : new Class<?>[] {TimeStopBlinkAbility.class, AbilityInput.class,
                    ActionTargetRange.class, TimeStopLearning.class, TimeStopBlinkParityGameTests.class}) {
                String actual = sha(type);
                var origin = type.getProtectionDomain().getCodeSource();
                String expected = System.getProperty("rotp.blink.expected." + type.getName());
                helper.assertTrue(origin != null && (expected == null || expected.equalsIgnoreCase(actual)), "Blink loaded class " + type.getName());
                log("LOADED class=" + type.getName() + " source=" + origin.getLocation() + " sha256=" + actual);
            }
        }

        void run(Spec spec) {
            try (Scene scene = new Scene(this, spec)) {
                scene.prepare();
                scene.execute();
                scenes++;
            }
        }

        void check(Spec spec, boolean passed, String detail) {
            checks++;
            if (!passed) {
                String failure = spec.id() + " " + detail;
                failures.add(failure);
                log("VIOLATION " + failure);
            }
        }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<Entity> actors = new ArrayList<>();
        Player user;
        StandPower power;
        StandType type;
        StandEntity stand;
        TimeStopBlinkAbility blink;
        ActionTarget aim = ActionTarget.EMPTY;
        Vec3 start;
        Vec3 expectedTargetPos;
        float beforeStamina;
        float baseCost;
        float tickCost;
        int budget;
        boolean denied;

        Scene(Suite suite, Spec spec) {
            this.suite = suite;
            this.spec = spec;
            level = suite.helper.getLevel();
        }

        void prepare() {
            start = Vec3.atBottomCenterOf(suite.helper.absolutePos(new BlockPos(2, 3, 2)));
            user = GameTestPlayers.makeServerMockPlayer(suite.helper,
                    spec.kind == Kind.CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL);
            actors.add(user);
            user.getAbilities().instabuild = spec.kind == Kind.CREATIVE;
            user.setNoGravity(true);
            user.noPhysics = true;
            user.moveTo(start.x, start.y, start.z, 0, spec.kind == Kind.PITCH ? -30 : 0);
            user.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1);
            premise(level.addFreshEntity(user), "Actual server player added");
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(spec.stand));
            power = PowerClass.STAND.attachGet(user);
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "Registered Stand transition");
            power.setResolveLevel(spec.stand.equals("star_platinum") ? 4 : 3);
            var unlocked = UNLOCKS.forceUnlock(power, "time_stop");
            premise(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "Public time-stop unlock");
            power.getCurTypeData().setAbilityLearningProgressPoints("time_stop", 95, TimeStopLearning.getMaxTrainingPoints(power), power);
            premise(TimeStopLearning.getTimeStopTicks(power, "time_stop") == 100, "Trained 100-tick time stop");
            premise(power.getAbility("time_stop_blink") instanceof TimeStopBlinkAbility, "Registered common blink");
            blink = (TimeStopBlinkAbility) power.getAbility("time_stop_blink");
            if (spec.kind == Kind.RESOLVE) user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 4));
            for (int z = -2; z <= 27; z++) for (int x = -1; x <= 3; x++)
                block(BlockPos.containing(start).offset(x, -1, z), Blocks.STONE.defaultBlockState());
            configureTarget();
            if (spec.kind == Kind.ZERO_MODIFIER) REACH_MODIFIERS.put(user.getUUID(), ticks -> 0);
            if (spec.kind == Kind.ZERO_AFTER_PAYMENT) REACH_MODIFIERS.put(user.getUUID(), ticks -> ticks - 10);
            if (spec.kind == Kind.RESOURCE_MODIFIER) REACH_MODIFIERS.put(user.getUUID(),
                    ticks -> PowerClass.STAND.get(user).getStamina() < 180 ? 0 : ticks);
            if (spec.kind == Kind.BASE_COOLDOWN) power.setAbilityCooldown("time_stop", 321);
            if (spec.kind == Kind.BLINK_COOLDOWN) power.setAbilityCooldown("time_stop_blink", 321);
            power.setStamina(spec.stamina);
            beforeStamina = power.getStamina();
            premise(power.isStaminaInfinite() || near(beforeStamina, spec.stamina), "Exact finite resource before input");
            baseCost = blink.getBlinkStaminaCost(power);
            tickCost = blink.getBlinkStaminaCostTicking(power, "time_stop");
            premise(near(baseCost, 180) && near(tickCost, 7.2), "Canonical base/tick cost");
            float postBase = power.isStaminaInfinite() ? beforeStamina : Math.max(0, beforeStamina - baseCost);
            budget = StandUtil.standIgnoresStaminaDebuff(power) ? 100 : Mth.clamp(Mth.floor((postBase - baseCost) / tickCost), 5, 100);
            denied = spec.kind == Kind.ZERO_MODIFIER || spec.kind == Kind.BLINK_COOLDOWN;
            if (spec.kind == Kind.ZERO_MODIFIER) budget = 0;
            if (spec.kind == Kind.ZERO_AFTER_PAYMENT) budget = Math.max(0, budget - 10);
            if (spec.kind == Kind.RESOURCE_MODIFIER) budget = 0;
            int getterBudget = spec.kind == Kind.ZERO_MODIFIER ? 0 : StandUtil.standIgnoresStaminaDebuff(power) ? 100
                    : Mth.clamp(Mth.floor((beforeStamina - baseCost) / tickCost), 5, 100);
            if (spec.kind == Kind.ZERO_AFTER_PAYMENT) getterBudget = Math.max(0, getterBudget - 10);
            check(blink.getMaxImpliedTicks(power) == getterBudget && near(power.getStamina(), beforeStamina),
                    "Shared getter keeps one reservation and performs no debit");
            LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
            if (stand != null) LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
        }

        void configureTarget() {
            switch (spec.kind) {
                case LIVING, STALE, FAR, STAND_IN_RANGE, STAND_OUT_OF_RANGE, RAY_LIVING -> {
                    double distance = switch (spec.kind) { case FAR -> 14; case STAND_IN_RANGE -> 12; case RAY_LIVING -> 0.7; default -> 3; };
                    Cow cow = EntityType.COW.create(level);
                    premise(cow != null, "Real cow created");
                    cow.setNoAi(true);
                    entity(cow, start.add(0, spec.kind == Kind.RAY_LIVING ? 0.5 : 0, distance));
                    if (spec.kind == Kind.RAY_LIVING) premise(clip(true).getEntity() == cow, "Cow actually intersects fallback ray");
                    if (spec.kind == Kind.STALE) { aim = new ActionTarget(cow); cow.discard(); }
                    else if (spec.kind != Kind.RAY_LIVING) aim = new ActionTarget(cow);
                    if (spec.kind == Kind.STAND_IN_RANGE || spec.kind == Kind.STAND_OUT_OF_RANGE)
                        summon(start.add(0, 0, spec.kind == Kind.STAND_IN_RANGE ? 11 : 22));
                    boolean accepted = spec.kind == Kind.LIVING || spec.kind == Kind.STAND_IN_RANGE || spec.kind == Kind.RAY_LIVING;
                    if (accepted) expectedTargetPos = entityDestination(cow);
                    if (spec.kind == Kind.STAND_IN_RANGE) premise(user.distanceTo(cow) > 8 && ActionTargetRange.isTargetWithinRange(stand, aim, level), "Active performer, not user, admits target");
                    if (spec.kind == Kind.STAND_OUT_OF_RANGE) premise(user.distanceTo(cow) < 8 && !ActionTargetRange.isTargetWithinRange(stand, aim, level), "Active performer, not user, refuses target");
                }
                case NONLIVING, RAY_NONLIVING -> {
                    Entity cart = EntityType.MINECART.create(level);
                    premise(cart != null && cart.isPickable(), "Native pickable nonliving cart");
                    entity(cart, start.add(0, spec.kind == Kind.RAY_NONLIVING ? 1 : 0, spec.kind == Kind.RAY_NONLIVING ? 0.7 : 3));
                    if (spec.kind == Kind.RAY_NONLIVING) premise(clip(false).getEntity() == cart && clip(true).isEmpty(level),
                            "Nonliving intersects ray, but fallback living filter excludes it");
                    if (spec.kind == Kind.NONLIVING) { aim = new ActionTarget(cart); expectedTargetPos = entityDestination(cart); }
                }
                case BLOCK, EMPTY_BLOCK, FAR_BLOCK -> {
                    BlockPos pos = BlockPos.containing(start).offset(0, 0, spec.kind == Kind.FAR_BLOCK ? 14 : 3);
                    if (spec.kind != Kind.EMPTY_BLOCK) block(pos, Blocks.STONE.defaultBlockState());
                    aim = new ActionTarget(pos, Direction.NORTH);
                    if (spec.kind == Kind.BLOCK) expectedTargetPos = Vec3.atBottomCenterOf(pos.above());
                }
                case SELF -> aim = new ActionTarget(user);
                case OWN_STAND -> { summon(start.add(0, 0, 3)); aim = new ActionTarget(stand); }
                case UNPICKABLE -> {
                    Entity item = EntityType.ITEM.create(level);
                    premise(item != null && !item.isPickable(), "Native unpickable entity");
                    entity(item, start.add(0, 0, 3));
                    aim = new ActionTarget(item);
                }
                case SPECTATOR -> {
                    Player spectator = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SPECTATOR);
                    entity(spectator, start.add(0, 0, 3));
                    premise(spectator.isSpectator(), "Native spectator target");
                    aim = new ActionTarget(spectator);
                }
                default -> {}
            }
        }

        void execute() {
            int userAge = user.tickCount;
            int standAge = stand != null ? stand.tickCount : 0;
            float overlay = stand != null ? stand.overlayTickCount : 0;
            Vec3 look = user.getLookAngle();
            float pitch = user.getXRot();
            var selected = power.updateAvailableMoves().getContextVariationContainer(blink);
            premise(selected != null && selected.ability == blink, "Actual registered blink variation");
            boolean admitted = AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK);
            BufferingState buffer = BufferingState.clickOnly();
            if (admitted) AbilityInput.keyPress(INPUT, selected.ability, user, null, InputMethod.CLICK, 0, buffer, blink.getAbilityId());
            boolean refusedPerform = denied || spec.kind == Kind.ZERO_AFTER_PAYMENT || spec.kind == Kind.RESOURCE_MODIFIER;
            check(admitted == !denied && buffer.isActionSuccess == !refusedPerform, "Registered CLICK admission/success=" + admitted + "/" + buffer.isActionSuccess);
            double speed = user.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
            Vec3 expected = refusedPerform ? start : expectedTargetPos != null ? expectedTargetPos
                    : new Vec3(start.x + look.x * speed * budget, start.y, start.z + look.z * speed * budget);
            int implied = refusedPerform ? 0 : Mth.clamp(Mth.ceil(expected.distanceTo(start) / speed), 0, budget);
            float expectedStamina = power.isStaminaInfinite() || refusedPerform ? beforeStamina
                    : Math.max(0, Math.max(0, beforeStamina - baseCost) - implied * tickCost);
            check(user.position().distanceTo(expected) < 0.0001, "Destination expected=" + expected + " actual=" + user.position());
            check(near(power.getStamina(), expectedStamina), "One base debit then actual-distance ticks expected=" + expectedStamina + " actual=" + power.getStamina());
            check(user.tickCount - userAge == implied, "Actual user age skip expected=" + implied + " actual=" + (user.tickCount - userAge));
            if (stand != null) {
                check(stand.tickCount - standAge == implied && near(stand.overlayTickCount - overlay, implied), "Stand and overlay skip follow user");
                check(LivingComponentAction.getCurEntityAction(stand) == null, "Instant blink installs no Stand task");
            }
            int cooldown = spec.kind == Kind.CREATIVE ? 0 : (int) (3F * implied / (spec.kind == Kind.RESOLVE ? 3F : 1F) * (1F / 6F));
            check(power.getAbilityCooldown("time_stop_blink") == (spec.kind == Kind.BLINK_COOLDOWN ? 321 : cooldown), "Native blink cooldown=" + power.getAbilityCooldown("time_stop_blink"));
            check(power.getAbilityCooldown("time_stop") == (spec.kind == Kind.BASE_COOLDOWN ? 321 : cooldown), "Base cooldown not overwritten if already active");
            check(near(user.getXRot(), pitch), "Own pitch preserved");
            check(level.getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId()).isEmpty(), "Blink installs no actual time-stop instance");
            if (expectedTargetPos != null && aim.getType() == ActionTarget.TargetType.ENTITY) {
                Float facing = TimeStopBlinkAbility.getFacingYaw(aim, expected);
                check(facing == null || near(Mth.wrapDegrees(user.getYRot() - facing), 0), "Native absolute facing toward clicked target");
            }
            log("SCENE " + spec.id() + " admitted=" + admitted + " success=" + buffer.isActionSuccess + " budget=" + budget
                    + " before=" + beforeStamina + " after=" + power.getStamina() + " start=" + start + " destination=" + user.position()
                    + " skipped=" + (user.tickCount - userAge) + " cooldown=" + power.getAbilityCooldown("time_stop_blink"));
        }

        void summon(Vec3 position) {
            premise(type.summon(user, power), "Native full-body summon");
            stand = power.getSummonedStandEntity();
            premise(stand != null && !stand.isArmsOnlyMode(), "Actual active Stand performer");
            actors.add(stand);
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, 0, 0);
        }

        Vec3 entityDestination(Entity target) {
            double distance = target.getBbWidth() + user.getBbWidth();
            return spec.stand.equals("the_world") ? target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(distance))
                    : user.distanceToSqr(target) > distance * distance ? target.position().subtract(user.getLookAngle().scale(distance)) : user.position();
        }

        void entity(Entity entity, Vec3 position) {
            actors.add(entity);
            entity.setNoGravity(true);
            entity.moveTo(position.x, position.y, position.z, 90, 0);
            premise(level.addFreshEntity(entity), "Native target added");
        }

        ActionTarget clip(boolean livingOnly) {
            double distance = 0.1 * 2.1585 * 5;
            return HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(), distance, distance, level,
                    entity -> entity != user && (!livingOnly || entity instanceof net.minecraft.world.entity.LivingEntity), user, 0);
        }

        void block(BlockPos pos, BlockState state) {
            blocks.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, state, 3);
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "BLINK PREMISE " + spec.id() + " " + detail); }
        void check(boolean passed, String detail) { suite.check(spec, passed, detail); }

        @Override public void close() {
            if (user != null) {
                AbilityInput.keyRelease(INPUT, user);
                REACH_MODIFIERS.remove(user.getUUID());
                if (power != null && type != null && power.isSummoned()) type.forceUnsummon(user, power);
            }
            actors.forEach(Entity::discard);
            blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
            premise(actors.stream().allMatch(Entity::isRemoved)
                    && blocks.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()))
                    && (user == null || !REACH_MODIFIERS.containsKey(user.getUUID())), "Actual actor/block/modifier cleanup");
            log("SCENE-CLEANUP " + spec.id() + " actorsRemoved=" + actors.stream().allMatch(Entity::isRemoved)
                    + " blocksRestored=" + blocks.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()))
                    + " modifierReleased=" + (user == null || !REACH_MODIFIERS.containsKey(user.getUUID())));
        }
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.001; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Blink loaded-class proof", error); }
    }
    private static void log(String value) { JojoMod.getLogger().info("BLINK-PARITY {}", value); }
}
