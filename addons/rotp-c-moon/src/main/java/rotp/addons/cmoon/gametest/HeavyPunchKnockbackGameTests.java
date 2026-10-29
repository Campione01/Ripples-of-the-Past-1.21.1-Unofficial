package rotp.addons.cmoon.gametest;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CMoonHeavyPunchAbility;
import rotp.addons.cmoon.ability.HeartInversionAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

/** Donor StandEntityPunch.doAttack: two post-hurt living pulses, one after a refused hurt, none before hurt. */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyPunchKnockbackGameTests {
    private static final String BATCH = "cmoon_heavy_knockback";
    private static final short KEY = 7693;
    private static final double EPSILON = 1.0E-4D;
    private static final int CHUNK_WAIT_TICKS = 60;
    private static final TicketType<UUID> TICKET = TicketType.create(
            "cmoon_heavy_knockback", Comparator.<UUID>naturalOrder());
    private static boolean bindingPrinted;

    private HeavyPunchKnockbackGameTests() {}

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void successfulLivingHeavyHasTwoDetachedRadialPulses(GameTestHelper helper) {
        run(helper, Mode.SUCCESS);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void cancelledNativeHurtKeepsOneDetachedHeavyPulse(GameTestHelper helper) {
        run(helper, Mode.CANCELLED);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void survivingNonLivingHeavyReplacesItsVelocity(GameTestHelper helper) {
        run(helper, Mode.MINECART);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void refusedNonLivingHeavyStillReplacesItsVelocity(GameTestHelper helper) {
        run(helper, Mode.REFUSED_MINECART);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void targetStandForwardsDetachedPulsesToItsOwnUser(GameTestHelper helper) {
        run(helper, Mode.STAND);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void swallowedStandDamageDoesNotCreateAHeavyPulse(GameTestHelper helper) {
        run(helper, Mode.STAND_INVUL);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void heavyMissDoesNotPushNearbyEntities(GameTestHelper helper) {
        run(helper, Mode.MISS);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void outOfReachHeavyDoesNotPushItsOldTarget(GameTestHelper helper) {
        run(helper, Mode.RANGE);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void friendlyTargetRefusalDoesNotCreateAHeavyPulse(GameTestHelper helper) {
        run(helper, Mode.FRIENDLY);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void refusedFinisherInputDoesNotStartOrPush(GameTestHelper helper) {
        run(helper, Mode.ADMISSION);
    }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void admittedHeartHeavyHasTwoDetachedPulses(GameTestHelper helper) { run(helper, Mode.HEART_SUCCESS); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void cancelledHeartHurtKeepsItsTailPulse(GameTestHelper helper) { run(helper, Mode.HEART_CANCELLED); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void nativeInvulnerableHeartTargetKeepsItsTailPulse(GameTestHelper helper) { run(helper, Mode.HEART_NATIVE_REFUSED); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void admittedHeartReplacesSurvivingMinecartVelocity(GameTestHelper helper) { run(helper, Mode.HEART_MINECART); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void admittedHeartReplacesRefusedMinecartVelocity(GameTestHelper helper) { run(helper, Mode.HEART_REFUSED_MINECART); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void admittedHeartForwardsPulsesToTheTargetStandsUser(GameTestHelper helper) { run(helper, Mode.HEART_STAND); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void swallowedHeartDamageCreatesNoPulse(GameTestHelper helper) { run(helper, Mode.HEART_STAND_INVUL); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void heartMissLeavesNearbyEntitiesAlone(GameTestHelper helper) { run(helper, Mode.HEART_MISS); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void heartRefusesAnOutOfReachTarget(GameTestHelper helper) { run(helper, Mode.HEART_RANGE); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void heartRefusesItsInReachUser(GameTestHelper helper) { run(helper, Mode.HEART_FRIENDLY); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void heartDisablesShieldBetweenSuccessAndTailPulses(GameTestHelper helper) { run(helper, Mode.HEART_SHIELD); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void admittedHeartBreaksGuardBeforeNativeHurt(GameTestHelper helper) { run(helper, Mode.HEART_GUARD); }

    @GameTest(template = "inversion_fixture", batch = BATCH, timeoutTicks = 160)
    public static void swallowedHeartGuardHitDrawsNoGuardBreakRoll(GameTestHelper helper) { run(helper, Mode.HEART_GUARD_INVUL); }

    @GameTest(template = "inversion_fixture", batch = "cmoon_pulse_m0", timeoutTicks = 160)
    public static void zeroMultiplierDoesNotRejectPositiveRawHeavyDamage(GameTestHelper helper) { run(helper, Mode.ZERO_MULTIPLIER); }

    private static void run(GameTestHelper helper, Mode mode) {
        Fixture fixture = new Fixture(helper, mode);
        try {
            fixture.holdChunks();
            helper.onEachTick(fixture::step);
        }
        catch (RuntimeException error) {
            fixture.close();
            throw error;
        }
    }

    private enum Mode {
        SUCCESS, CANCELLED, MINECART, REFUSED_MINECART, STAND, STAND_INVUL,
        MISS, RANGE, FRIENDLY, ADMISSION, ZERO_MULTIPLIER,
        HEART_SUCCESS, HEART_CANCELLED, HEART_NATIVE_REFUSED, HEART_MINECART, HEART_REFUSED_MINECART,
        HEART_STAND, HEART_STAND_INVUL, HEART_MISS, HEART_RANGE, HEART_FRIENDLY, HEART_SHIELD,
        HEART_GUARD, HEART_GUARD_INVUL;

        boolean heart() { return name().startsWith("HEART_"); }
        boolean is(Mode ordinary) { return this == ordinary || name().equals("HEART_" + ordinary.name()); }
        boolean standTarget() { return is(STAND) || this == HEART_GUARD || this == HEART_GUARD_INVUL; }
        boolean swallowed() { return is(STAND_INVUL) || this == HEART_GUARD_INVUL; }
        boolean refused() { return is(CANCELLED) || this == HEART_NATIVE_REFUSED; }
        double pulseStrength() { return heart() ? 2.5D : 0.25D; }
        double finalStrength() { return heart() ? 3.85D : 0.475D; }
        int windup() { return heart() ? 13 : 18; }
        int recovery() { return heart() ? 13 : 19; }
    }

    private record Pulse(Entity target, long gameTick, ActionPhase phase, float phaseTick,
            float strength, Vec3 ratio, Vec3 radial, Vec3 before, boolean inHurt,
            boolean shieldUsing, boolean shieldCooldown) {}

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final Mode mode;
        final UUID ticketId = UUID.randomUUID();
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final List<Player> players = new ArrayList<>();
        final List<StandPower> powers = new ArrayList<>();
        final List<Pulse> pulses = new ArrayList<>();
        final Consumer<LivingKnockBackEvent> knockback = this::knockback;
        final Consumer<LivingIncomingDamageEvent> incoming = this::incoming;
        final Consumer<EntityTickEvent.Post> beforeAction = this::beforeAction;
        final Consumer<EntityTickEvent.Post> afterAction = this::afterAction;
        Player user;
        StandPower power;
        CMoonStandEntity stand;
        StandEntityHeavyPunch action;
        Entity target;
        Mob nearby;
        Player defender;
        Vec3 standPos;
        Vec3 targetPos;
        Vec3 nearbyPos;
        Vec3 finalMotion;
        Vec3 ownerFinalMotion;
        Vec3 targetAtHit;
        Vec3 standAtHit;
        float healthBefore;
        float healthAfter;
        float staminaBefore;
        float staminaAfter;
        float minecartDamageBefore;
        float minecartDamageAfter;
        double impactStrength = -1;
        Double previousMultiplier;
        ModConfigSpec.ConfigValue<Double> multiplier;
        int inversionAtHit;
        boolean guardAtIncoming;
        float undrawnGuardRoll;
        int incomingCount;
        int atPerformIncoming;
        long inputTick;
        long performTick = -1;
        double performFullTicks;
        boolean prepared;
        boolean captured;
        boolean tracking;
        boolean closed;
        boolean listenersRegistered;
        boolean opened;
        int chunkWaitTicks;
        String setupError;

        Fixture(GameTestHelper helper, Mode mode) {
            this.helper = helper;
            this.mode = mode;
            helper.testInfo.addListener(this);
        }

        // The scene lies outside the test structure and may cross a chunk border, and under load the chunks around it
        // become entity-ticking only some ticks after their tickets are added: the scene is built once they all are.
        void holdChunks() {
            // Just past the barrier ring around the 4x4x4 test area: under manual control the Stand aims from its own
            // eyes (1.16 StandEntity.aimWithThisOrUser), and from inside a barrier that aim is the barrier.
            standPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 3, 5)));
            ChunkPos centre = new ChunkPos(BlockPos.containing(standPos));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkPos chunk = new ChunkPos(centre.x + x, centre.z + z);
                    helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketId);
                    chunks.add(chunk);
                }
            }
        }

        // Where the scene puts an entity, the only chunks whose ticking it needs.
        List<BlockPos> sceneSpots() {
            Vec3 origin = standPos;
            return List.of(BlockPos.containing(origin), BlockPos.containing(origin.add(-4.0D, 0, 2.0D)),
                    BlockPos.containing(origin.add(3.0D, 0, 3.0D)), BlockPos.containing(origin.add(-1.5D, 0, 1.0D)),
                    BlockPos.containing(origin.add(mode.is(Mode.RANGE) ? 6.0D : 1.2D, 0, 1.6D)));
        }

        List<BlockPos> idleSceneSpots() {
            return sceneSpots().stream().filter(spot -> !helper.getLevel().isPositionEntityTicking(spot)).toList();
        }

        void step() {
            if (closed) return;
            if (!opened) {
                List<BlockPos> idle = idleSceneSpots();
                if (!idle.isEmpty()) {
                    helper.assertTrue(++chunkWaitTicks <= CHUNK_WAIT_TICKS,
                            "CM-PULSE setup: the scene chunks never became entity-ticking: " + idle);
                    return;
                }
                opened = true;
                open();
                return;
            }
            check();
        }

        void open() {
            printBinding();
            if (mode == Mode.ZERO_MULTIPLIER) {
                multiplier = JojoModConfig.COMMON_SPEC.getValues()
                        .get(List.of("Stand settings", "standPowerMultiplier"));
                previousMultiplier = multiplier.get();
                multiplier.set(0.0D);
                helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get() == 0.0D,
                        "CM-PULSE premise: live server multiplier did not become zero");
            }
            Vec3 origin = standPos;
            targetPos = origin.add(mode.is(Mode.RANGE) ? 6.0D : mode == Mode.HEART_SHIELD
                    || mode == Mode.HEART_GUARD || mode == Mode.HEART_GUARD_INVUL ? 0.24D : 1.2D,
                    0, mode == Mode.HEART_SHIELD || mode == Mode.HEART_GUARD
                            || mode == Mode.HEART_GUARD_INVUL ? 0.32D : 1.6D);
            nearbyPos = origin.add(-1.5D, 0, 1.0D);
            user = player(origin.add(-4.0D, 0, 2.0D));
            power = summon(user);
            stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.setManuallyControlled(true);
            stand.setNoGravity(true);
            stand.moveTo(standPos.x, standPos.y, standPos.z, 75, -35);
            stand.setYHeadRot(-110);
            stand.yBodyRot = 145;
            LivingComponentAction component = LivingComponentAction.getComponent(stand);
            component.setAction(null, user, SyncType.NO_SYNC);
            nearby = mob(nearbyPos);
            if (mode.is(Mode.MINECART) || mode.is(Mode.REFUSED_MINECART)) {
                target = EntityType.MINECART.create(helper.getLevel());
                helper.assertTrue(target instanceof Minecart, "CM-PULSE premise: native minecart missing");
                target.setInvulnerable(mode.is(Mode.REFUSED_MINECART));
                add(target, targetPos);
            }
            else if (mode.standTarget()) {
                defender = player(origin.add(3.0D, 0, 3.0D));
                StandPower defendingPower = summon(defender);
                target = defendingPower.getSummonedStandEntity();
                ((StandEntity) target).setManuallyControlled(true);
                ((StandEntity) target).summonLockTicks = 0;
                target.setNoGravity(true);
                target.moveTo(targetPos.x, targetPos.y, targetPos.z, -90, 20);
                if (mode == Mode.HEART_GUARD || mode == Mode.HEART_GUARD_INVUL) {
                    defendingPower.setStamina(defendingPower.getMaxStamina());
                    Ability guard = defendingPower.getAbility(CMoonAddon.GUARD);
                    AvailableAbilities guards = new AvailableAbilities();
                    guards.update(defendingPower, defendingPower.getMoveset());
                    var admittedGuard = guards.getContextVariationContainer(guard);
                    helper.assertTrue(AbilityInput.withConditionCheck(admittedGuard, defender, InputMethod.HOLD),
                            "CM-PULSE premise: defender guard input refused");
                    AbilityInput.keyPress((short) (KEY + 1), admittedGuard.ability, defender, null, InputMethod.HOLD,
                            0, BufferingState.clickOnly(), guard.getAbilityId());
                    helper.assertTrue(((StandEntity) target).isStandBlocking(), "CM-PULSE premise: native guard did not start");
                }
            }
            else if (mode == Mode.HEART_SHIELD) {
                target = player(targetPos);
            }
            else {
                target = mode.is(Mode.FRIENDLY) ? user : mob(targetPos);
                if (mode.is(Mode.FRIENDLY)) user.setPos(targetPos);
                if (mode == Mode.HEART_NATIVE_REFUSED) target.setInvulnerable(true);
            }
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTickEvent.Post.class, beforeAction);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityTickEvent.Post.class, afterAction);
            listenersRegistered = true;
            stand.setFinisherMeter(mode.heart() || mode == Mode.ADMISSION ? 0.6F : 0.0F);
            power.setStamina(mode == Mode.ADMISSION ? 10.0F : power.getMaxStamina());
            component.entityAim.setTarget(mode.is(Mode.MISS) ? ActionTarget.EMPTY : new ActionTarget(target));
            Ability heavy = power.getAbility(CMoonAddon.HEAVY_PUNCH);
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            var resolved = available.getContextVariationContainer(heavy);
            boolean admitted = AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK);
            inputTick = helper.getTick();
            if (mode == Mode.ADMISSION) {
                helper.assertTrue(!admitted && component.getAction() == null,
                        "CM-PULSE admission: an under-stamina finisher input started an action");
                tracking = true;
                return;
            }
            helper.assertTrue(admitted && power.getStamina() >= 50 && !stand.hasAutoAttackTarget(),
                    "CM-PULSE premise: heavy input lacked its raw admission");
            Ability expected = power.getAbility(mode.heart() ? CMoonAddon.HEART_INVERSION : CMoonAddon.HEAVY_PUNCH);
            helper.assertTrue(resolved.ability == expected, "CM-PULSE premise: heavy input resolved to the wrong variation");
            AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F,
                    BufferingState.clickOnly(), heavy.getAbilityId());
            helper.assertTrue(component.getAction() instanceof StandEntityHeavyPunch
                    && component.getAction().ability == expected, "CM-PULSE premise: registered heavy variation did not start");
            action = (StandEntityHeavyPunch) component.getAction();
            bind(action.getClass());
            // 1.16 cm_heavy_punch and cm_heart_inversion set no standPerformDuration: the builder's 1
            helper.assertTrue(action.phasesLength.get(ActionPhase.WINDUP) == (float) mode.windup()
                    && action.phasesLength.get(ActionPhase.PERFORM) == 1.0F
                    && action.phasesLength.get(ActionPhase.RECOVERY) == (float) mode.recovery(),
                    "CM-PULSE premise: baseline phase lengths changed: " + action.phasesLength);
            staminaBefore = power.getStamina();
            tracking = true;
        }

        Player player(Vec3 pos) {
            Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0,
                    new GameProfile(UUID.randomUUID(), "CMoonPulse")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return false; }
            };
            players.add(player);
            player.setNoGravity(true);
            player.moveTo(pos.x, pos.y, pos.z, 160, 40);
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "CM-PULSE premise: native Player spawn failed");
            return player;
        }

        StandPower summon(Player player) {
            StandPower standPower = PowerClass.STAND.attachGet(player);
            powers.add(standPower);
            helper.assertTrue(StandPowerTransitions.insert(standPower,
                    new StandInstance(CMoonAddon.C_MOON.get())).status() == StandPowerTransitions.Status.APPLIED
                    && CMoonAddon.C_MOON.get().summon(player, standPower),
                    "CM-PULSE premise: could not grant native C-Moon");
            return standPower;
        }

        Mob mob(Vec3 pos) {
            Mob mob = EntityType.HUSK.create(helper.getLevel());
            helper.assertTrue(mob != null, "CM-PULSE premise: native husk missing");
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            mob.setHealth(500);
            add(mob, pos);
            return mob;
        }

        void add(Entity entity, Vec3 pos) {
            entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
            helper.assertTrue(helper.getLevel().addFreshEntity(entity), "CM-PULSE premise: native entity spawn failed");
            entities.add(entity);
        }

        void beforeAction(EntityTickEvent.Post event) {
            if (closed || event.getEntity() != stand || action == null || prepared
                    || action.getPhase() != ActionPhase.PERFORM || action.getPhaseTick() != 0) return;
            try {
                // Only transport setup is pinned here; the registered action is still ticked once by the core.
                stand.moveTo(standPos.x, standPos.y, standPos.z, 75, -35);
                stand.setYHeadRot(-110);
                stand.yBodyRot = 145;
                stand.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetPos);
                // 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget) and the strike
                // aimed again with the precision ray from the manually controlled Stand's eyes: it looks at the cart
                if (mode.is(Mode.MINECART) || mode.is(Mode.REFUSED_MINECART)) {
                    stand.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
                }
                if (mode.is(Mode.FRIENDLY)) {
                    if (!HitResultUtil.isTargetWithinRange(new ActionTarget(target), stand, stand.level(),
                            stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
                            stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)) || stand.canAttackEntity(target)) {
                        setupError = "friendly control is not independently in-range and permission-refused";
                    }
                }
                if (mode == Mode.HEART_SHIELD) {
                    Player shield = (Player) target;
                    shield.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    shield.startUsingItem(InteractionHand.OFF_HAND);
                    // Freshly raised: it is in use, before the native five-tick blocking window.
                    if (!shield.isUsingItem() || shield.isBlocking()) setupError = "fresh shield premise failed";
                }
                if (mode == Mode.HEART_GUARD || mode == Mode.HEART_GUARD_INVUL) {
                    target.setYRot(180);
                    ((LivingEntity) target).setYHeadRot(180);
                    ((LivingEntity) target).yBodyRot = 180;
                }
                if (mode.swallowed()) {
                    tracking = false;
                    DamageSource seed = action.makePunchDamageSource();
                    ((DamageSourceModified) seed).jojo_ripples$setStandInvulTicks(10);
                    if (!EntityActionInstance.standEntityAttack(stand, target, seed, 1.0F)) {
                        setupError = "native transport seed did not start Stand invulnerability";
                    }
                    tracking = true;
                }
                if (mode == Mode.HEART_GUARD || mode == Mode.HEART_GUARD_INVUL) {
                    stand.getRandom().setSeed(4096L);
                    undrawnGuardRoll = RandomSource.create(4096L).nextFloat();
                    if (!(undrawnGuardRoll < HeartInversionAbility.disableBlockingChance(stand, target))) {
                        setupError = "deterministic guard-break seed is not a successful roll";
                    }
                }
                target.setDeltaMovement(mode.is(Mode.MINECART) || mode.is(Mode.REFUSED_MINECART)
                        ? new Vec3(-0.7D, 0.3D, 0.4D) : Vec3.ZERO);
                if (defender != null) defender.setDeltaMovement(Vec3.ZERO);
                healthBefore = target instanceof LivingEntity living ? living.getHealth() : 0;
                minecartDamageBefore = target instanceof Minecart minecart ? minecart.getDamage() : 0;
                atPerformIncoming = incomingCount;
                prepared = true;
                performFullTicks = action.getFullTicksPassed();
            }
            catch (RuntimeException error) {
                setupError = error.toString();
            }
        }

        void afterAction(EntityTickEvent.Post event) {
            if (closed || event.getEntity() != stand || action == null || captured || action.punchedTarget == null) return;
            captured = true;
            performTick = helper.getLevel().getGameTime();
            standAtHit = stand.position();
            targetAtHit = target.position();
            finalMotion = target.getDeltaMovement();
            ownerFinalMotion = defender != null ? defender.getDeltaMovement() : null;
            healthAfter = target instanceof LivingEntity living ? living.getHealth() : 0;
            minecartDamageAfter = target instanceof Minecart minecart ? minecart.getDamage() : 0;
            staminaAfter = power.getStamina();
            inversionAtHit = target instanceof LivingEntity living && living.hasEffect(CMoonAddon.CM_INVERSION)
                    ? living.getEffect(CMoonAddon.CM_INVERSION).getDuration() : 0;
            Entity impacted = defender != null ? defender : target;
            KnockbackCollisionImpact impact = KnockbackCollisionImpact.getExistingHandler(impacted);
            impactStrength = impact != null && impact.isActive() ? impact.getKnockbackImpactStrength() : -1;
            if (mode == Mode.HEART_GUARD_INVUL && stand.getRandom().nextFloat() != undrawnGuardRoll) {
                setupError = "swallowed native Heart hit consumed the guard-break roll";
            }
            // Freeze only after the native motion/impact snapshot so large pulses cannot cross test scenes.
            target.setDeltaMovement(Vec3.ZERO);
            if (defender != null) defender.setDeltaMovement(Vec3.ZERO);
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (!closed && tracking && event.getEntity() == target && event.getSource().getEntity() == stand) {
                incomingCount++;
                if (mode.is(Mode.CANCELLED)) event.setCanceled(true);
                if (mode == Mode.HEART_GUARD) guardAtIncoming = ((StandEntity) target).isStandBlocking();
            }
        }

        void knockback(LivingKnockBackEvent event) {
            if (closed || !tracking || (event.getEntity() != target && event.getEntity() != defender
                    && event.getEntity() != nearby && event.getEntity() != user)) return;
            Vec3 delta = target.position().subtract(stand.position());
            pulses.add(new Pulse(event.getEntity(), helper.getLevel().getGameTime(),
                    action != null ? action.getPhase() : null, action != null ? action.getPhaseTick() : -1,
                    event.getStrength(), new Vec3(event.getRatioX(), 0, event.getRatioZ()),
                    new Vec3(delta.x, 0, delta.z).normalize(), event.getEntity().getDeltaMovement(),
                    !event.getEntity().damageContainers.isEmpty(),
                    event.getEntity() instanceof Player player && player.isUsingItem(),
                    event.getEntity() instanceof Player player && player.getCooldowns().isOnCooldown(Items.SHIELD)));
            if (mode == Mode.HEART_SHIELD && event.getEntity() == target
                    && event.getEntity().damageContainers.isEmpty()
                    && pulses.stream().filter(p -> p.target() == target && !p.inHurt()).count() == 1) {
                ((Player) target).getRandom().setSeed(4096L);
                if (!(RandomSource.create(4096L).nextFloat() < HeartInversionAbility.disableBlockingChance(stand, target))) {
                    setupError = "deterministic shield seed is not a successful roll";
                }
            }
        }

        void check() {
            if (closed) return;
            helper.assertTrue(setupError == null, "CM-PULSE setup: " + setupError);
            if (mode == Mode.ADMISSION) {
                if (helper.getTick() - inputTick < 45) return;
                helper.assertTrue(pulses.isEmpty(), "CM-PULSE admission created knockback: " + pulses);
                helper.assertTrue(LivingComponentAction.getComponent(stand).getAction() == null,
                        "CM-PULSE admission later started an action");
            }
            else {
                if (LivingComponentAction.getComponent(stand).getAction() == action) return;
                helper.assertTrue(captured && prepared, "CM-PULSE setup: normal native action never performed");
                helper.assertTrue(Math.abs(performFullTicks - mode.windup()) < EPSILON,
                        "CM-PULSE timing: hit at full tick " + performFullTicks + " instead of " + mode.windup());
                helper.assertTrue(Math.abs((staminaBefore - staminaAfter) - 50.0F) < EPSILON,
                        "CM-PULSE stamina changed: " + staminaBefore + " -> " + staminaAfter);
                helper.assertTrue(nearby.getDeltaMovement().lengthSqr() < EPSILON
                        && nearby.getHealth() == 500.0F && !nearby.hasEffect(CMoonAddon.CM_INVERSION),
                        "CM-PULSE heavy changed a nearby untargeted entity");
                helper.assertTrue(pulses.stream().noneMatch(p -> p.target() == nearby || p.target() == user),
                        "CM-PULSE changed the bystander or attacker's owner: " + pulses);
                verify();
            }
            System.out.println("CM-PULSE PASS " + mode + " native input/action completion; events=" + pulses);
            helper.succeed();
        }

        void verify() {
            System.out.println("CM-PULSE OBSERVE " + mode + " tick=" + performTick + " fullTicks=" + performFullTicks
                    + " stand=" + standAtHit + " target=" + targetAtHit + " movement=" + finalMotion
                    + " ownerMovement=" + ownerFinalMotion + " health=" + healthBefore + "->" + healthAfter
                    + " minecartDamage=" + minecartDamageBefore + "->" + minecartDamageAfter
                    + " inversion=" + inversionAtHit + " impact=" + impactStrength
                    + " liveM=" + JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()
                    + " incoming=" + (incomingCount - atPerformIncoming) + " events=" + pulses);
            if (mode.is(Mode.MISS) || mode.is(Mode.RANGE) || mode.is(Mode.FRIENDLY)) {
                helper.assertTrue(action.punchedTarget.getType() == ActionTarget.TargetType.EMPTY,
                        "CM-PULSE control selected a refused target: " + action.punchedTarget);
                helper.assertTrue(pulses.isEmpty() && finalMotion.lengthSqr() < EPSILON,
                        "CM-PULSE refused target/miss created motion: " + pulses + " / " + finalMotion);
                return;
            }
            helper.assertTrue(action.punchedTarget.getMainEntity() == target,
                    "CM-PULSE premise: registered action did not select its intended target");
            if (mode.swallowed()) {
                helper.assertTrue(incomingCount == atPerformIncoming && healthAfter == healthBefore && pulses.isEmpty(),
                        "CM-PULSE a hit swallowed before native hurt created damage/knockback: " + pulses);
                return;
            }
            Vec3 radial = new Vec3(targetAtHit.x - standAtHit.x, 0, targetAtHit.z - standAtHit.z).normalize();
            if (mode.is(Mode.MINECART) || mode.is(Mode.REFUSED_MINECART)) {
                helper.assertTrue(target.isAlive(), "CM-PULSE premise: minecart did not survive");
                helper.assertTrue(mode.is(Mode.MINECART) ? minecartDamageAfter > minecartDamageBefore
                        : minecartDamageAfter == minecartDamageBefore,
                        "CM-PULSE premise: native minecart hurt/refusal changed: "
                                + minecartDamageBefore + " -> " + minecartDamageAfter);
                helper.assertTrue(finalMotion.distanceTo(radial.scale(mode.pulseStrength())) < EPSILON * mode.pulseStrength(),
                        "CM-PULSE donor replaces non-living velocity with radial " + mode.pulseStrength() + ", got " + finalMotion);
                // 1.16 HeavyPunchInstance.afterAttack: a landed hit arms the wall impact with the pushed velocity
                helper.assertTrue(mode.is(Mode.MINECART) ? Math.abs(impactStrength - finalMotion.length()) < EPSILON
                                : impactStrength < 0,
                        "CM-PULSE minecart wall impact must be armed after the push, and only on a landed hit: "
                                + impactStrength + " vs pushed " + finalMotion.length());
                return;
            }
            if (mode.is(Mode.SUCCESS) || mode == Mode.HEART_SHIELD || mode == Mode.ZERO_MULTIPLIER) {
                helper.assertTrue((mode == Mode.ZERO_MULTIPLIER ? healthAfter == healthBefore : healthAfter < healthBefore)
                        && incomingCount - atPerformIncoming == 1,
                        "CM-PULSE premise: native living hurt did not succeed once");
                var inversion = ((LivingEntity) target).getEffect(CMoonAddon.CM_INVERSION);
                helper.assertTrue(mode == Mode.ZERO_MULTIPLIER ? inversionAtHit == 0
                        : inversion != null && inversionAtHit == (mode.heart() ? 150 : 75),
                        "CM-PULSE retained standard inversion was lost or changed");
            }
            else if (mode.refused()) {
                helper.assertTrue(incomingCount - atPerformIncoming == (mode == Mode.HEART_NATIVE_REFUSED ? 0 : 1)
                        && healthAfter == healthBefore,
                        "CM-PULSE premise: scoped cancellation did not refuse the one native hurt");
            }
            if (mode == Mode.HEART_GUARD) helper.assertTrue(!guardAtIncoming,
                    "CM-PULSE Heart guard break did not precede native hurt");
            int expected = mode.refused() ? 1 : 2;
            assertDetached(target, expected);
            if (mode.standTarget()) assertDetached(defender, 2);
            helper.assertTrue(pulses.size() == (mode.standTarget() ? 6 : mode.refused() ? 1 : 3),
                    "CM-PULSE additional unexpected native/detached events: " + pulses);
            for (Pulse pulse : pulses) {
                helper.assertTrue(pulse.gameTick() == performTick && pulse.phase() == ActionPhase.PERFORM
                        && pulse.phaseTick() == 0, "CM-PULSE knockback escaped perform tick 0: " + pulse);
                helper.assertTrue(pulse.ratio().normalize().distanceTo(pulse.radial().reverse()) < EPSILON,
                        "CM-PULSE knockback escaped Stand-to-target direction: " + pulse);
            }
            if (!mode.refused()) {
                List<Pulse> nativeEvents = pulses.stream().filter(p -> p.target() == target && p.inHurt()).toList();
                helper.assertTrue(nativeEvents.size() == 1 && Math.abs(nativeEvents.getFirst().strength() - 0.4F) < EPSILON,
                        "CM-PULSE donor native event stays 0.4 before separate pulses: " + nativeEvents);
                helper.assertTrue(finalMotion.distanceTo(radial.scale(mode.finalStrength())) < EPSILON * Math.max(1, mode.finalStrength()),
                        "CM-PULSE donor native/separate pulse sequence changed: " + finalMotion);
                helper.assertTrue(Math.abs(impactStrength - (defender != null ? ownerFinalMotion : finalMotion).length()) < EPSILON,
                        "CM-PULSE heavy impact captured velocity before both pulses: " + impactStrength);
            }
            if (mode == Mode.HEART_SHIELD) {
                List<Pulse> detached = pulses.stream().filter(p -> p.target() == target && !p.inHurt()).toList();
                helper.assertTrue(detached.get(0).shieldUsing() && !detached.get(0).shieldCooldown()
                        && !detached.get(1).shieldUsing() && detached.get(1).shieldCooldown(),
                        "CM-PULSE shield disable is not between success and tail pulses: " + detached);
            }
        }

        void assertDetached(Entity entity, int expected) {
            List<Pulse> detached = pulses.stream().filter(p -> p.target() == entity && !p.inHurt()
                    && Math.abs(p.strength() - mode.pulseStrength()) < EPSILON).toList();
            helper.assertTrue(detached.size() == expected,
                    "CM-PULSE donor expected " + expected + " detached " + mode.pulseStrength() + " pulses for " + entity.getType()
                            + ", got " + detached.size() + "; all=" + pulses);
            for (Pulse pulse : detached) {
                helper.assertTrue(pulse.gameTick() == performTick && pulse.phase() == ActionPhase.PERFORM
                        && pulse.phaseTick() == 0, "CM-PULSE detached push escaped perform tick 0: " + pulse);
                helper.assertTrue(pulse.ratio().normalize().distanceTo(pulse.radial().reverse()) < EPSILON,
                        "CM-PULSE direction used caster/look instead of performing Stand-to-target: " + pulse);
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            if (previousMultiplier != null) {
                multiplier.set(previousMultiplier);
                helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()
                        .equals(previousMultiplier), "CM-PULSE cleanup: live server multiplier was not restored");
            }
            if (listenersRegistered) {
                NeoForge.EVENT_BUS.unregister(knockback);
                NeoForge.EVENT_BUS.unregister(incoming);
                NeoForge.EVENT_BUS.unregister(beforeAction);
                NeoForge.EVENT_BUS.unregister(afterAction);
            }
            for (StandPower standPower : powers) {
                if (standPower.isSummoned()) {
                    CMoonAddon.C_MOON.get().forceUnsummon(standPower.getUser(), standPower);
                }
            }
            entities.forEach(Entity::discard);
            for (Player player : players) {
                player.discard();
            }
            for (ChunkPos chunk : chunks) {
                helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
            }
            System.out.println("CM-PULSE CLEANUP " + mode + " listeners/players/entities/tickets released");
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static void printBinding() {
        if (bindingPrinted) return;
        bindingPrinted = true;
        for (Class<?> type : List.of(HeavyPunchKnockbackGameTests.class, CMoonHeavyPunchAbility.CMoonHeavyPunch.class,
                HeartInversionAbility.HeartInversion.class, StandEntityHeavyPunch.class,
                StandEntity.class, EntityActionInstance.class, DamageSourceModified.class)) {
            bind(type);
        }
        try {
            System.out.println("CM-PULSE METHOD " + StandEntity.class.getDeclaredMethod("isStandAttackInvulnerable", LivingEntity.class));
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void bind(Class<?> type) {
            try (InputStream bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                System.out.println("CM-PULSE BIND " + type.getName() + " sha256=" + hash
                        + " codeSource=" + type.getProtectionDomain().getCodeSource().getLocation());
            }
            catch (Exception error) {
                throw new IllegalStateException("CM-PULSE loaded-class binding failed: " + type.getName(), error);
            }
    }
}
