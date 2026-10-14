package rotp.core.gametest;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchInput;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.silverchariot.SilverChariotState;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.UserStandEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One serial family: the server multiplier is global, so these scenes must not run concurrently. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandDamageMultiplierGameTests {
    private static final short ATTACK_KEY = 111;
    private static final short SPECIAL_KEY = 112;
    private static final float EPSILON = 0.0001F;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(StandDamageMultiplierGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static final Field DEAD = deadField();
    private static Suite activeSuite;

    private StandDamageMultiplierGameTests() {}

    private enum Kind { HEAVY, LIGHT, BARRAGE, CHARGED, GRAB, KICK, SPLASH,
        ANGELO, ARMORED, CHEAT, PROJECTILE, CALLER, BOAT, REFUSAL, TRANSPORT, INVUL, SHIELD, LINK, FIXED, FIXED_BOAT }
    private record Spec(String id, Kind kind, String stand, double multiplier, boolean cheat, boolean fiery) {}

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "stand_damage_multiplier")
    public static void sharedMultiplierNativeAndCallerMatrix(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            requireIdentity(helper);
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) {
            suite.close();
            throw error;
        }
    }

    @AfterBatch(batch = "stand_damage_multiplier")
    public static void releaseMultiplierBatch(ServerLevel level) {
        // The runner advances batches before later GameTestInfo listeners receive failure/timeout.
        boolean active = activeSuite != null;
        if (activeSuite != null) activeSuite.close();
        log("BATCH-CLEANUP active=" + active + " released=" + (activeSuite == null)
                + " M=" + config().standDamageMultiplier.get());
    }

    private static List<Spec> cases() {
        List<Spec> cases = new ArrayList<>();
        for (double m : new double[] {1, 0, 2}) {
            cases.add(new Spec("native-heavy-M" + m, Kind.HEAVY, "star_platinum", m, false, false));
            cases.add(new Spec("native-angelo-SC-M" + m, Kind.ANGELO, "crazy_diamond", m, false, false));
            cases.add(new Spec("native-emerald-M" + m, Kind.PROJECTILE, "hierophant_green", m, false, false));
        }
        // Below 1, 1.16 scaled the whole raw hit and capped the non-lethal Angelo hit only at death.
        cases.add(new Spec("native-angelo-SC-M0.5", Kind.ANGELO, "crazy_diamond", 0.5, false, false));
        for (Kind kind : new Kind[] {Kind.LIGHT, Kind.BARRAGE, Kind.CHARGED, Kind.GRAB, Kind.KICK, Kind.SPLASH}) {
            for (double m : new double[] {1, 2}) {
                cases.add(new Spec("native-" + kind + "-M" + m, kind, kind == Kind.KICK ? "the_world" : "star_platinum", m, false, false));
            }
        }
        cases.add(new Spec("native-angelo-keeps-cheat", Kind.ANGELO, "crazy_diamond", 2, true, false));
        for (double m : new double[] {1, 2}) {
            cases.add(new Spec("native-armored-angelo-M" + m, Kind.ARMORED, "crazy_diamond", m, false, false));
            cases.add(new Spec("native-lethal-cheat-M" + m, Kind.CHEAT, "star_platinum", m, true, false));
            cases.add(new Spec("caller-raw-invul-M" + m, Kind.INVUL, "star_platinum", m, false, false));
            cases.add(new Spec("caller-native-shield-M" + m, Kind.SHIELD, "star_platinum", m, false, false));
            cases.add(new Spec("native-reduced-link-M" + m, Kind.LINK, "star_platinum", m, false, false));
        }
        for (String stand : new String[] {"star_platinum", "magicians_red", "silver_chariot"}) {
            boolean fiery = stand.equals("silver_chariot");
            for (double m : new double[] {1, 0, 2}) {
                cases.add(new Spec("caller-" + stand + "-M" + m, Kind.CALLER, stand, m, false, fiery));
                cases.add(new Spec("caller-boat-" + stand + "-M" + m, Kind.BOAT, stand, m, false, fiery));
                cases.add(new Spec("caller-refusal-" + stand + "-M" + m, Kind.REFUSAL, stand, m, false, fiery));
            }
            cases.add(new Spec("native-source-transport-" + stand, Kind.TRANSPORT, stand, 2, false, fiery));
        }
        // Add-on hits whose 1.16 originals hurt directly carry the fixed-damage flag: M does not scale them.
        for (double m : new double[] {0, 2}) {
            cases.add(new Spec("caller-fixed-star_platinum-M" + m, Kind.FIXED, "star_platinum", m, false, false));
            cases.add(new Spec("caller-fixed-magicians_red-M" + m, Kind.FIXED, "magicians_red", m, false, false));
            cases.add(new Spec("caller-fixed-boat-M" + m, Kind.FIXED_BOAT, "star_platinum", m, false, false));
        }
        return cases;
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "standPowerMultiplier"));
        final double oldMultiplier;
        final List<Spec> cases = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        int index;
        int assertions;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(activeSuite == null, "SHARED-M overlapping multiplier suites");
            this.helper = helper;
            level = helper.getLevel();
            oldMultiplier = multiplier.get();
            helper.testInfo.addListener(this);
            activeSuite = this;
        }

        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (scene != null && !closed) scene.incoming(event);
            };
            Consumer<LivingDamageEvent.Post> damage = event -> {
                if (scene != null && !closed) scene.damage(event);
            };
            Consumer<LivingDeathEvent> death = event -> {
                if (scene != null && !closed) scene.death(event);
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed) scene.post(event.getEntity());
            };
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (scene != null && !closed && event.getLevel() == level) scene.spawned(event.getEntity());
            };
            listeners.add(incoming); listeners.add(damage); listeners.add(death); listeners.add(post); listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDeathEvent.class, death);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, join);
        }

        void tick() {
            if (closed) return;
            try {
                if (scene == null) {
                    if (index == cases.size()) {
                        log("SUMMARY scenes=" + index + " assertions=" + assertions + " failures=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "SHARED-M " + failures.size() + " behavioral failures; see SUMMARY log");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, cases.get(index++));
                    scene.prepare();
                }
                scene.tick();
                if (scene.done) {
                    scene.close();
                    scene = null;
                    multiplier.set(oldMultiplier);
                }
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        void check(boolean result, String detail) {
            assertions++;
            log((result ? "PASS " : "FAIL ") + scene.spec.id + " " + detail);
            if (!result) failures.add(scene.spec.id + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            try {
                for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
                listeners.clear();
                if (scene != null) scene.close();
            }
            finally {
                multiplier.set(oldMultiplier);
                if (activeSuite == this) activeSuite = null;
                log("CLEANUP closed=true listeners=" + listeners.size() + " restoredM=" + config().standDamageMultiplier.get());
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private record Hit(LivingEntity target, DamageSource source, float amount, float health, int age) {}
    private record Life(float bodyHp, boolean bodyDead, int bodyAge, float userHp, boolean userDead, boolean cheat) {}

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        final List<Entity> owned = new ArrayList<>();
        final List<Actor> actors = new ArrayList<>();
        final List<Hit> hits = new ArrayList<>();
        final List<String> deaths = new ArrayList<>();
        Actor caster;
        Actor victimActor;
        Entity target;
        LivingEntity secondary;
        Entity projectile;
        EntityActionInstance action;
        CrazyDAngeloRockPunchEffect angelo;
        DamageSource captured;
        Life immediate;
        Life afterSync;
        Vec3 origin;
        BlockPos block;
        BlockState oldBlock;
        float initialHealth;
        float raw;
        float bodyApplied = Float.NaN;
        float linkedAmount = Float.NaN;
        int age;
        int hitBodyAge = -1;
        int firstInvulAge;
        Vec3 dropCenter;
        long dropTime = -1;
        boolean started;
        boolean released;
        boolean grabStarted;
        boolean pendingAtHit;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }

        void prepare() {
            double intendedMultiplier = spec.kind == Kind.TRANSPORT ? 1D : spec.multiplier;
            suite.multiplier.set(intendedMultiplier);
            premise(config().standDamageMultiplier.get() == intendedMultiplier, "live server multiplier did not change");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            caster = actor(spec.kind == Kind.TRANSPORT ? "crazy_diamond" : spec.stand, origin);
            caster.stand.setCanFollowUser(false);
            caster.stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            if (spec.kind == Kind.ANGELO || spec.kind == Kind.TRANSPORT || spec.kind == Kind.LINK) {
                victimActor = actor(spec.kind == Kind.LINK ? "star_platinum" : "silver_chariot", origin.add(0, 0, 1.7));
                victimActor.stand.setCanFollowUser(false);
                victimActor.stand.moveTo(origin.x, origin.y, origin.z + 1.7, 0, 0);
                victimActor.stand.setYHeadRot(0);
                if (spec.kind != Kind.LINK) {
                    SilverChariotState.get(victimActor.user).setHasArmor(false);
                    victimActor.stand.refreshSilverChariotStateAfterMutation(victimActor.user);
                    victimActor.stand.setHealth(4);
                    premise(victimActor.stand.getHealth() == 4 && victimActor.user.getHealth() == 4
                            && victimActor.stand.getArmorValue() == 0, "SC body/user/armor premise");
                }
                target = victimActor.stand;
                premise(!victimActor.stand.canBlockFromAngle(caster.stand.position())
                        && victimActor.stand.getCurStandAction() == null, "victim must be idle and back-facing");
            }
            else if (spec.kind == Kind.CHEAT || spec.kind == Kind.ARMORED || spec.kind == Kind.SHIELD) {
                Player player = player(origin.add(0, 0, 1.7), spec.kind == Kind.SHIELD ? 180 : 0);
                target = player;
                if (spec.kind == Kind.SHIELD) {
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    player.startUsingItem(InteractionHand.OFF_HAND);
                }
                else player.setHealth(4);
                if (spec.kind == Kind.ARMORED) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    player.setAbsorptionAmount(2);
                }
            }
            else if (spec.kind == Kind.BOAT || spec.kind == Kind.FIXED_BOAT) {
                Boat boat = EntityType.BOAT.create(level);
                premise(boat != null, "boat creation");
                own(boat); boat.setNoGravity(true); boat.moveTo(origin.x, origin.y, origin.z + 1.7, 0, 0);
                premise(level.addFreshEntity(boat), "boat add"); target = boat;
            }
            else target = cow(origin.add(0, 0, 1.7), 200);
            if (spec.kind == Kind.REFUSAL) {
                target.setInvulnerable(true);
                target.setRemainingFireTicks(31);
            }
            if (spec.cheat) userTarget().addEffect(new MobEffectInstance(ModStatusEffects.CHEAT_DEATH, 1000));
            initialHealth = target instanceof LivingEntity living ? living.getHealth() : 0;
            if (spec.kind == Kind.KICK) secondary = cow(origin.add(0.55, 0, 1.7), 200);
            if (spec.kind == Kind.SPLASH) {
                block = BlockPos.containing(origin.add(0, 0, 1.7));
                oldBlock = level.getBlockState(block);
                level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                target.moveTo(origin.x + 0.8, origin.y, origin.z + 2.1, 0, 0);
            }
            if (spec.fiery && spec.kind != Kind.TRANSPORT) igniteRapier(caster);
            aim(caster, spec.kind == Kind.SPLASH ? new ActionTarget(block, Direction.NORTH) : new ActionTarget(target));
            log("START " + spec.id + " M=" + config().standDamageMultiplier.get() + " caster=" + caster.stand.getId()
                    + " target=" + target.getId() + " sourcePower=" + caster.power.getPowerType().getId());
        }

        void tick() {
            age++;
            premise(age < 130, "native scene timed out, kind=" + spec.kind + " phase=" + (action != null ? action.getPhase() : null));
            if (!started) {
                if (spec.kind == Kind.SHIELD && age < 7) return;
                started = true;
                switch (spec.kind) {
                    case CALLER, BOAT -> { caller(caster, target, source(caster, 0), 1); finishCaller(spec.multiplier); return; }
                    case FIXED, FIXED_BOAT -> {
                        DamageSource fixed = source(caster, 0);
                        ((DamageSourceModified) fixed).jojo_ripples$setFixedDamage(true);
                        caller(caster, target, fixed, 1); finishCaller(1); return;
                    }
                    case REFUSAL -> {
                        boolean hurt = caster.stand.hurtWithStandAttack(target, source(caster, 0), 1);
                        check(!hurt && hits.isEmpty() && ((LivingEntity) target).getHealth() == initialHealth,
                                "native invulnerable target refuses hit");
                        check(target.getRemainingFireTicks() == 31, "refused hit restores prior fire duration");
                        done = true; return;
                    }
                    case INVUL -> { startInvul(); return; }
                    case SHIELD -> { shield(); return; }
                    case PROJECTILE -> { shoot(); return; }
                    default -> startNative();
                }
            }
            if (spec.kind == Kind.INVUL) {
                if (target.tickCount >= firstInvulAge + 10) {
                    float hp = ((LivingEntity) target).getHealth();
                    caller(caster, target, source(caster, 10), 6);
                    check(near(hp - ((LivingEntity) target).getHealth(), 6 * spec.multiplier), "post-expiry amount=" + (hp - ((LivingEntity) target).getHealth()));
                    done = true;
                }
                return;
            }
            if (isAngelo() && angelo == null && age > 1) attachAngelo();
            if (spec.kind == Kind.GRAB && !grabStarted && LivingComponentGrab.getEntityGrabbedBy(caster.stand) == target) {
                grabStarted = true;
                action = press(caster, "punch", InputMethod.CLICK, ATTACK_KEY);
                premise(action != null && ((Ability) action.ability).name().equals("grab_punch"), "native grabbed punch replacement");
            }
            if (spec.kind == Kind.CHARGED && !released && action.getPhase() == ActionPhase.WINDUP) {
                released = true; AbilityInput.keyRelease(ATTACK_KEY, caster.user);
            }
            boolean zeroStandAttempt = isAngelo() && victimActor != null && spec.multiplier == 0
                    && action != null && action.punchedTarget != null && action.punchedTarget.getMainEntity() == target;
            if (!zeroStandAttempt && hits.stream().noneMatch(hit -> hit.target == target)) return;
            if (isAngelo() && victimActor != null) {
                if (immediate == null || afterSync == null) return;
                verifyAngelo();
                if (spec.kind == Kind.TRANSPORT) transport();
            }
            else verifyNative();
            done = true;
        }

        void startNative() {
            if (isAngelo()) {
                UNLOCKS.forceUnlock(caster.power, "finisher"); UNLOCKS.forceUnlock(caster.power, "restore_terrain");
                caster.stand.setFinisherMeter(1);
            }
            else if (spec.kind == Kind.KICK) {
                UNLOCKS.forceUnlock(caster.power, "kick"); caster.stand.setFinisherMeter(1);
            }
            String ability = switch (spec.kind) {
                case LIGHT -> "punch";
                case BARRAGE -> "barrage";
                case CHARGED -> "heavy_charged";
                case GRAB -> "grab";
                default -> "heavy_punch";
            };
            if (spec.kind == Kind.GRAB) UNLOCKS.forceUnlock(caster.power, "grab_throw");
            action = press(caster, ability, spec.kind == Kind.BARRAGE || spec.kind == Kind.CHARGED ? InputMethod.HOLD : InputMethod.CLICK, ATTACK_KEY);
            premise(action != null, "native action was not installed");
        }

        void attachAngelo() {
                premise(((Ability) action.ability).name().equals("finisher") && action.getPhase() == ActionPhase.WINDUP
                        && caster.stand.getLastHeavyFinisherValue() >= 1 && action.getPunchModifiers().isEmpty(), "native full finisher windup");
                float stamina = caster.power.getStamina();
                Ability base = caster.power.getAbility("restore_terrain");
                var selected = caster.power.updateAvailableMoves().getContextVariationContainer(base);
                Ability fuse = caster.power.getAbility("fuse_with_rock");
                log("ANGELO-ADMISSION selected=" + (selected == null ? null : selected.ability.name())
                        + " check=" + (selected == null ? null : selected.conditionCheck)
                        + " fuseUnlocked=" + fuse.isAbilityUnlocked(caster.power)
                        + " fuseCheck=" + fuse.checkConditions(caster.power) + " meter=" + caster.stand.getLastHeavyFinisherValue()
                        + " resolve=" + caster.power.getResolveLevel() + " stamina=" + caster.power.getStamina());
                premise(selected != null && selected.ability instanceof CrazyDAngeloRockPunchInput
                        && AbilityInput.withConditionCheck(selected, caster.user, InputMethod.HOLD), "native Angelo replacement/admission");
                BufferingState buffering = BufferingState.clickOnly();
                AbilityInput.keyPress(SPECIAL_KEY, selected.ability, caster.user, null, InputMethod.HOLD, 0, buffering, base.getAbilityId());
                premise(buffering.isActionSuccess && action.getPunchModifiers().size() == 1
                        && action.getPunchModifiers().get(0) instanceof CrazyDAngeloRockPunchEffect
                        && near(stamina - caster.power.getStamina(), 50), "real Angelo input did not install one modifier/spend50");
                angelo = (CrazyDAngeloRockPunchEffect) action.getPunchModifiers().get(0);
        }

        boolean isAngelo() { return spec.kind == Kind.ANGELO || spec.kind == Kind.ARMORED || spec.kind == Kind.TRANSPORT; }

        void incoming(LivingIncomingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (victimActor != null && entity == victimActor.user && event.getSource() instanceof StandLinkDamageSource) {
                linkedAmount = event.getOriginalAmount(); return;
            }
            if (entity != target && entity != secondary) return;
            DamageSource source = event.getSource();
            if (!source.is(ModDamageTypes.STAND_ATTACK) && source.getDirectEntity() != projectile) return;
            Hit hit = new Hit(entity, source, event.getOriginalAmount(), entity.getHealth(), entity.tickCount);
            hits.add(hit);
            if (entity == target && captured == null) {
                captured = source;
                hitBodyAge = entity.tickCount;
                raw = switch (spec.kind) {
                    case LIGHT, GRAB -> (float) caster.stand.getAttackDamage() * 0.25F;
                    case BARRAGE -> {
                        float p = (float) caster.stand.getPrecision() / 16;
                        yield (0.04F + (float) caster.stand.getAttackDamage() * 0.01F)
                                * (1 + p * 0.5F * Math.min(p, 0.5F)) * ((StandEntityBarrage) action).hitsThisTick;
                    }
                    case CHARGED -> Math.max((float) caster.stand.getAttackDamage(), 1) * 1.5F;
                    case SPLASH -> (float) caster.stand.getAttackDamage() * 0.4F;
                    case ANGELO, ARMORED, TRANSPORT -> Math.max((float) caster.stand.getAttackDamage(), 1);
                    case PROJECTILE, CALLER, BOAT -> 1;
                    case SHIELD -> 2;
                    default -> Math.max((float) caster.stand.getAttackDamage(), 1);
                };
                if (angelo != null) {
                    pendingAtHit = angelo.getTarget() == userTarget() && angelo.preventTargetDeath()
                            && UserStandEffects.getEffectsTargetedBy(userTarget(), ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get()).anyMatch(effect -> effect == angelo);
                }
            }
            log("INCOMING " + spec.id + " target=" + entity.getId() + " amount=" + hit.amount + " health=" + hit.health
                    + " age=" + hit.age + " source=" + source.getMsgId() + " direct=" + id(source.getDirectEntity())
                    + " cause=" + id(source.getEntity()) + " canceled=" + event.isCanceled() + " pending=" + pendingAtHit);
        }

        void damage(LivingDamageEvent.Post event) {
            if (event.getEntity() == target) bodyApplied = event.getNewDamage();
        }

        void death(LivingDeathEvent event) {
            if (owned.contains(event.getEntity())) {
                dropCenter = event.getEntity().position();
                dropTime = level.getGameTime();
            }
            if (event.getEntity() != target && event.getEntity() != userTarget()) return;
            String value = "entity=" + event.getEntity().getId() + " canceled=" + event.isCanceled() + " hp=" + event.getEntity().getHealth();
            deaths.add(value); log("DEATH " + spec.id + " " + value);
        }

        void post(Entity entity) {
            if (victimActor == null || !isAngelo() || action == null || action.punchedTarget == null) return;
            if (entity == caster.stand && immediate == null) {
                if (captured == null) {
                    raw = Math.max((float) caster.stand.getAttackDamage(), 1);
                    pendingAtHit = angelo != null && angelo.getTarget() == userTarget() && angelo.preventTargetDeath()
                            && UserStandEffects.getEffectsTargetedBy(userTarget(), ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get()).anyMatch(effect -> effect == angelo);
                    hitBodyAge = victimActor.stand.tickCount;
                }
                immediate = life();
                log("IMMEDIATE " + spec.id + " " + immediate + " hitAge=" + hitBodyAge);
            }
            if (entity == victimActor.stand && immediate != null && victimActor.stand.tickCount > immediate.bodyAge) {
                afterSync = life(); log("POST-SYNC " + spec.id + " " + afterSync);
            }
        }

        Life life() {
            return new Life(victimActor.stand.getHealth(), dead(victimActor.stand), victimActor.stand.tickCount,
                    victimActor.user.getHealth(), dead(victimActor.user), victimActor.user.hasEffect(ModStatusEffects.CHEAT_DEATH));
        }

        void verifyAngelo() {
            double m = spec.kind == Kind.TRANSPORT ? 1 : spec.multiplier;
            Hit hit = hits.stream().filter(h -> h.target == target).findFirst().orElse(null);
            float amount = hit != null ? hit.amount : 0;
            check((hit != null || m == 0) && near(amount, Math.min(raw * m, initialHealth - EPSILON)), "Angelo actual=" + amount + " raw=" + raw + " M=" + m);
            check(pendingAtHit, "real pending effect registered on user");
            check(deaths.isEmpty() && !immediate.bodyDead && !afterSync.bodyDead && !immediate.userDead && !afterSync.userDead
                    && immediate.bodyHp > 0 && afterSync.bodyHp > 0 && immediate.userHp > 0 && afterSync.userHp > 0,
                    "Angelo life immediate=" + immediate + " after=" + afterSync + " deaths=" + deaths);
            // 1.16 used Cheat Death up here (cheatDeath ran before the non-lethal cancel); the port keeps its older
            // pre-armor non-lethal cap, which never lets the hit reach death: an accepted difference, not parity.
            if (spec.cheat) check(immediate.cheat && afterSync.cheat, "the port's kept pre-armor non-lethal behaviour leaves Cheat Death unused");
            if (m == 0) check(near(immediate.bodyHp, 4) && near(immediate.userHp, 4), "M0 keeps body/user HP4");
        }

        void verifyNative() {
            Hit first = hits.stream().filter(h -> h.target == target).findFirst().orElseThrow();
            if (spec.kind == Kind.ARMORED) {
                check(near(first.amount, initialHealth - EPSILON), "pre-armor Angelo cap actual=" + first.amount);
                check(pendingAtHit && target.isAlive() && deaths.isEmpty(), "armored Angelo remains alive and pending at impact");
                check(((LivingEntity) target).getHealth() > EPSILON, "mitigation remains after the conservative cap");
            }
            else {
                check(near(first.amount, raw * spec.multiplier), "actual=" + first.amount + " raw=" + raw + " M=" + spec.multiplier);
                if (spec.kind == Kind.CHEAT) {
                    LivingEntity living = (LivingEntity) target;
                    check(near(living.getHealth(), living.getMaxHealth() / 2) && !living.hasEffect(ModStatusEffects.CHEAT_DEATH)
                            && living.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "unrelated lethal hit keeps native half-health Cheat Death");
                }
                else if (spec.kind != Kind.BARRAGE && spec.kind != Kind.KICK && spec.kind != Kind.SPLASH && spec.kind != Kind.LINK) {
                    check(near(initialHealth - ((LivingEntity) target).getHealth(), raw * spec.multiplier), "native health loss=" + (initialHealth - ((LivingEntity) target).getHealth()));
                }
                if (spec.kind == Kind.KICK) check(hits.stream().anyMatch(h -> h.target == secondary && near(h.amount, raw * 0.5 * spec.multiplier)), "native kick secondary scales once");
                if (spec.kind == Kind.LINK) check(!Float.isNaN(bodyApplied) && near(linkedAmount, bodyApplied), "linked=" + linkedAmount + " reducedBody=" + bodyApplied);
            }
            if (first.source instanceof DamageSourceModified modified) {
                check(modified.jojo_ripples$standPower() == caster.power || spec.kind == Kind.PROJECTILE, "source retains owning Stand power");
            }
        }

        void transport() {
            Actor fire = actor(spec.stand, origin.add(3, 0, 0));
            if (spec.fiery) igniteRapier(fire);
            Cow other = cow(origin.add(3, 0, 1.5), 4);
            suite.multiplier.set(2D);
            boolean hurt = fire.stand.hurtWithStandAttack(other, captured, raw);
            check(hurt && other.getHealth() > 0 && !dead(other), "native-source transport " + spec.stand + " hp=" + other.getHealth());
            if (!spec.stand.equals("star_platinum")) check(other.getRemainingFireTicks() == (spec.fiery ? 80 : 200), "transport fire wrapper retains duration");
        }

        void caller(Actor actor, Entity hit, DamageSource source, float amount) {
            boolean result = actor.stand.hurtWithStandAttack(hit, source, amount);
            log("CALLER " + spec.id + " result=" + result + " amount=" + amount + " target=" + hit.getId());
        }

        void finishCaller(double factor) {
            if (target instanceof Boat boat) check(near(boat.getDamage(), 10 * factor), "boat damage=" + boat.getDamage());
            else {
                check(!hits.isEmpty() && near(hits.get(0).amount, factor), "caller actual=" + (hits.isEmpty() ? "missing" : hits.get(0).amount));
                check(near(initialHealth - ((LivingEntity) target).getHealth(), factor), "caller health loss=" + (initialHealth - ((LivingEntity) target).getHealth()));
            }
            if (!spec.stand.equals("star_platinum")) check(target.getRemainingFireTicks() == (spec.fiery ? 80 : 200), "fire path retains duration at M=" + spec.multiplier);
            done = true;
        }

        void startInvul() {
            LivingEntity living = (LivingEntity) target;
            living.invulnerableTime = 7; living.lastHurt = 3;
            firstInvulAge = living.tickCount;
            caller(caster, target, source(caster, 10), 4);
            check(near(initialHealth - living.getHealth(), 4 * spec.multiplier), "first invul hit amount");
            check(living.invulnerableTime == 7 && living.lastHurt == 3, "vanilla invul/lastHurt restored");
            float hp = living.getHealth();
            caller(caster, target, source(caster, 10), 8);
            check(living.getHealth() == hp, "donor-shared assign-before-subtract quirk unchanged");
        }

        void shield() {
            Player player = (Player) target;
            premise(player.isBlocking(), "native raised shield");
            boolean hurt = EntityActionInstance.standEntityAttack(caster.stand, player, source(caster, 0), 2);
            int wear = player.getOffhandItem().getDamageValue();
            check(!hurt && wear == (spec.multiplier == 1 ? 1 : 6), "raw2 shield result=" + hurt + " wear=" + wear);
            done = true;
        }

        void shoot() {
            HGEmeraldEntity emerald = new HGEmeraldEntity(caster.user, level);
            own(emerald); projectile = emerald;
            emerald.setPos(origin.x, ((LivingEntity) target).getY(0.5), origin.z + 0.5);
            emerald.shoot(0, 0, 1, 0.5F, 0);
            premise(level.addFreshEntity(emerald), "native emerald spawn");
        }

        EntityActionInstance press(Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power.getAbility(name);
            premise(base != null, "missing registered ability " + name);
            var selected = actor.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, actor.user, method), "real admission " + name);
            BufferingState buffering = BufferingState.clickOnly();
            var input = AbilityInput.keyPress(key, selected.ability, actor.user, null, method, 0, buffering, base.getAbilityId());
            premise(buffering.isActionSuccess, "native press failed " + name);
            log("PRESS " + spec.id + " base=" + name + " selected=" + selected.ability.name());
            return input != null && input.action instanceof EntityActionInstance a ? a : actor.stand.getCurStandAction();
        }

        Actor actor(String name, Vec3 position) {
            Player user = player(position, 0);
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(name));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "grant registered " + name);
            power.setResolveLevel(2);
            premise(type.summon(user, power), "summon registered " + name);
            StandEntity stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "real Stand/user binding");
            own(stand); stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            power.setStamina(power.getMaxStamina());
            Actor result = new Actor(user, power, type, stand); actors.add(result);
            return result;
        }

        Player player(Vec3 position, float yaw) {
            Player player = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            own(player); player.setNoGravity(true); player.noPhysics = true;
            player.getAbilities().invulnerable = false;
            player.moveTo(position.x, position.y, position.z, yaw, 0); player.setYHeadRot(yaw);
            premise(level.addFreshEntity(player), "damageable player add"); return player;
        }

        Cow cow(Vec3 position, float health) {
            Cow cow = EntityType.COW.create(level); premise(cow != null, "cow creation"); own(cow);
            cow.setNoAi(true); cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health); cow.setHealth(health);
            cow.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(cow), "cow add"); return cow;
        }

        void igniteRapier(Actor actor) {
            actor.power.setResolveLevel(4);
            Cow shooter = cow(origin.add(5, 0, 0), 20);
            MRCrossfireHurricaneEntity flame = new MRCrossfireHurricaneEntity(shooter, level);
            own(flame); flame.setPos(actor.stand.getX(), actor.stand.getY(), actor.stand.getZ() + 1);
            flame.setDeltaMovement(0, 0, -0.5);
            premise(actor.stand.deflectSilverChariotTargetProjectile(flame) && actor.stand.isSilverChariotRapierOnFire(), "native fiery deflection");
            flame.discard();
        }

        void aim(Actor actor, ActionTarget target) {
            LivingComponentAction.getComponent(actor.user).entityAim.setTarget(target);
            LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(target);
            actor.user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getCenterPos());
            actor.user.setYHeadRot(actor.user.getYRot());
            actor.stand.lookAt(EntityAnchorArgument.Anchor.EYES, target.getCenterPos());
            actor.stand.setYHeadRot(actor.stand.getYRot());
        }

        DamageSource source(Actor actor, int invul) {
            DamageSource source = DamageUtil.make(level, ModDamageTypes.STAND_ATTACK, actor.stand);
            ((DamageSourceModified) source).jojo_ripples$setStandPower(actor.power);
            ((DamageSourceModified) source).jojo_ripples$setStandInvulTicks(invul);
            return source;
        }

        LivingEntity userTarget() { return victimActor != null ? victimActor.user : target instanceof LivingEntity living ? living : null; }
        void own(Entity entity) { if (!owned.contains(entity)) owned.add(entity); }

        void spawned(Entity entity) {
            if (closed) return;
            if (entity instanceof DamagingEntity projectile && owned.contains(projectile.getOwner())) own(entity);
            if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                boolean deathDrop = dropCenter != null && dropTime == level.getGameTime()
                        && entity.position().distanceToSqr(dropCenter) < 4;
                boolean brokenBlockDrop = block != null && action != null && action.getPhase() == ActionPhase.PERFORM
                        && entity.position().distanceToSqr(Vec3.atCenterOf(block)) < 4;
                if (deathDrop || brokenBlockDrop) own(entity);
            }
        }
        void premise(boolean ok, String message) { suite.helper.assertTrue(ok, "SHARED-M PREMISE " + spec.id + ": " + message); }
        void check(boolean ok, String message) { suite.check(ok, message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                for (Actor actor : actors) {
                    AbilityInput.keyRelease(ATTACK_KEY, actor.user); AbilityInput.keyRelease(SPECIAL_KEY, actor.user);
                    LivingComponentAction.getComponent(actor.stand).setAction(null, SyncType.NO_SYNC);
                    actor.user.removeAllEffects();
                    if (actor.power.isSummoned()) actor.type.forceUnsummon(actor.user, actor.power);
                }
            }
            finally {
                if (block != null) level.setBlockAndUpdate(block, oldBlock);
                for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
                log("SCENE-CLEANUP " + spec.id + " actorsRemoved=" + owned.stream().allMatch(Entity::isRemoved));
            }
        }
    }

    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}
    private static JojoModConfig.Common config() { return JojoModConfig.getCommonConfigInstance(false); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.002; }
    private static int id(Entity entity) { return entity != null ? entity.getId() : -1; }
    private static void log(String text) { JojoMod.getLogger().info("SHARED-M " + text); }
    private static Field deadField() {
        try { Field field = LivingEntity.class.getDeclaredField("dead"); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static boolean dead(LivingEntity entity) {
        try { return DEAD.getBoolean(entity); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    private static void requireIdentity(GameTestHelper helper) {
        for (String name : new String[] {
                "rotp.core.customobjects.DamageSourceModified", "rotp.core.mixin.damage.DamageSourceMixin",
                StandEntity.class.getName(), StandEntityHeavyPunchAbility.class.getName(),
                StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class.getName(), StandDamageMultiplierGameTests.class.getName()}) {
            String path = name.replace('.', '/') + ".class";
            try (var input = StandEntity.class.getClassLoader().getResourceAsStream(path)) {
                helper.assertTrue(input != null, "SHARED-M missing class resource " + path);
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                String expected = System.getProperty("rotp.sharedM.expected." + name);
                boolean focused = System.getProperty("neoforge.enabledGameTestNamespaces", "").contains("rotp_shared_m");
                helper.assertTrue(!focused || expected != null, "SHARED-M focused launch lacks its class receipt " + name);
                log("LOADED name=" + name + " resource=" + StandEntity.class.getClassLoader().getResource(path)
                        + " sha256=" + sha + " standCodeSource=" + StandEntity.class.getProtectionDomain().getCodeSource());
                helper.assertTrue(expected == null || sha.equalsIgnoreCase(expected), "SHARED-M loaded class receipt mismatch " + name);
            }
            catch (Exception error) { throw new IllegalStateException("SHARED-M class identity", error); }
        }
    }
}
