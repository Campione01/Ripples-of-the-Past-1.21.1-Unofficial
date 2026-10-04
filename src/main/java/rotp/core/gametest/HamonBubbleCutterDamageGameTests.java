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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
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
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleCutterAbility;
import rotp.core.impl.powers.hamon.entity.HamonBubbleCutterEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBubbleCutterDamageGameTests {
    private static final float EPSILON = 1.0E-5F;

    private HamonBubbleCutterDamageGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "bubble_cutter_refused_damage", timeoutTicks = 100)
    public static void refusedCutterDamageDoesNotTrainStrength(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "bubble_cutter_damage_channels", timeoutTicks = 100)
    public static void ordinaryCutterKeepsPhysicalAndHamonChannels(GameTestHelper helper) {
        start(helper, false);
    }

    private static void start(GameTestHelper helper, boolean refuseDamage) {
        Fixture fixture = new Fixture(helper, refuseDamage);
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

    private record Points(int strength, float fraction) {
        double total() {
            return strength + (double) fraction;
        }
    }

    private record Attempt(String channel, float amount, float expectedHamon, UUID direct,
            UUID cause, LivingIncomingDamageEvent event) {
        String summary() {
            return channel + "=" + amount + "/expectedHamon=" + expectedHamon
                    + "/direct=" + direct + "/canceled=" + event.isCanceled();
        }
    }

    private record Contact(UUID cutter, int age, float healthBefore, float healthAfter,
            Points pointsBefore, Points pointsAfter, List<Attempt> attempts, boolean removed) {}

    private static final class Tracked {
        final HamonBubbleCutterEntity cutter;
        final List<Attempt> attempts = new ArrayList<>();
        float healthBefore;
        Points pointsBefore;
        boolean hitTarget;
        Contact contact;

        Tracked(HamonBubbleCutterEntity cutter) {
            this.cutter = cutter;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 23;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean refuseDamage;
        private final Map<UUID, Tracked> cutters = new LinkedHashMap<>();
        private final List<Contact> contacts = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private Cow target;
        private PlayerPower power;
        private HamonData hamon;
        private Ability ability;
        private EntityActionInstance action;
        private Tracked active;
        private RuntimeException observerFailure;
        private int userTicks;
        private boolean pressed;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean refuseDamage) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.refuseDamage = refuseDamage;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 7.5D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2,
                    origin.x + 2, origin.y + 4, origin.z + 4);
            helper.assertTrue(space.maxY < level.getMaxBuildHeight(), "Cutter fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Cutter fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "Cutter fixture contains another entity");
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
            helper.assertTrue(level.addFreshEntity(user), "Could not add cutter user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.BUBBLE_CUTTER.get());
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.BUBBLE_CUTTER.get())
                            && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get()), "Unexpected cutter skill setup");
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            ability = power.getAbility("bubble_cutter");
            helper.assertTrue(ability instanceof HamonBubbleCutterAbility cutterAbility && !cutterAbility.isGliding()
                            && ability.abilityType == HamonPowerType.HAMON_BUBBLE_CUTTER.get(), "Missing registered ordinary cutter");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create cutter target");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            target.setPos(origin.x, origin.y, origin.z + 1.25D);
            helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable(), "Invalid cutter target");
            helper.assertTrue(!EntityHamonChargeState.get(target).hasHamonCharge()
                            && PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isEmpty()
                            && Math.abs(HamonAbilityHelpers.hamonDamageMultiplier(target) - 0.2F) < EPSILON,
                    "Fresh healthy target must have the real reduced Hamon multiplier");
            helper.assertTrue(HamonAbilityHelpers.configHamonDamageMultiplier() > 0
                            && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "World disables Hamon damage or training");
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " targetMultiplier="
                    + HamonAbilityHelpers.hamonDamageMultiplier(target) + " sourceMultiplier=" + hamon.getHamonDamageMultiplier()
                    + " configMultiplier=" + HamonAbilityHelpers.configHamonDamageMultiplier() + " points=" + points());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof HamonBubbleCutterEntity cutter && cutter.getOwner() == user) {
                    if (event.isCanceled()) throw new IllegalStateException("Cutter spawn canceled");
                    CompoundTag nbt = cutter.saveWithoutId(new CompoundTag());
                    if (nbt.getBoolean("Gliding") || !(nbt.getFloat("HamonStatPoints") > 0)) {
                        throw new IllegalStateException("Production cutter has wrong variant or no training budget");
                    }
                    cutters.put(cutter.getUUID(), new Tracked(cutter));
                    log("spawn uuid=" + cutter.getUUID() + " pointsBudget=" + nbt.getFloat("HamonStatPoints")
                            + " pos=" + cutter.position() + " velocity=" + cutter.getDeltaMovement());
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = cutters.get(event.getEntity().getUUID());
                if (tracked != null) {
                    if (event.isCanceled() || active != null || !level.isPositionEntityTicking(tracked.cutter.blockPosition())) {
                        throw new IllegalStateException("Cutter natural tick is not isolated and ticking");
                    }
                    active = tracked;
                    tracked.healthBefore = target.getHealth();
                    tracked.pointsBefore = points();
                    tracked.attempts.clear();
                    tracked.hitTarget = false;
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Tracked tracked = cutters.get(event.getProjectile().getUUID());
                if (tracked != null) {
                    if (tracked != active || event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                            || hit.getEntity() != target) throw new IllegalStateException("Cutter missed its intended living target");
                    tracked.hitTarget = true;
                }
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (event.getEntity() == target) {
                    if (active == null || !active.hitTarget) throw new IllegalStateException("Target damage occurred outside its cutter impact");
                    DamageSource source = event.getSource();
                    if (event.isCanceled()) throw new IllegalStateException("Another listener already refused target damage");
                    if (refuseDamage) event.setCanceled(true);
                    String channel = source.is(ModDamageTypes.HAMON) ? "hamon"
                            : source.is(ModDamageTypes.MOD_PROJECTILE) ? "physical" : "unexpected";
                    float expected = HamonAbilityHelpers.hamonDamageAmount(target, 0.3F)
                            * hamon.getHamonDamageMultiplier() * HamonAbilityHelpers.configHamonDamageMultiplier();
                    active.attempts.add(new Attempt(channel, event.getOriginalAmount(), expected,
                            id(source.getDirectEntity()), id(source.getEntity()), event));
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                Tracked tracked = cutters.get(event.getEntity().getUUID());
                if (tracked != null) {
                    if (active != tracked) throw new IllegalStateException("Cutter post tick lacks its pre tick");
                    if (tracked.hitTarget) {
                        tracked.contact = new Contact(tracked.cutter.getUUID(), tracked.cutter.tickCount,
                                tracked.healthBefore, target.getHealth(), tracked.pointsBefore, points(),
                                List.copyOf(tracked.attempts), tracked.cutter.isRemoved());
                        contacts.add(tracked.contact);
                        Contact contact = tracked.contact;
                        log("hit uuid=" + contact.cutter + " age=" + contact.age + " health=" + contact.healthBefore + "->" + contact.healthAfter
                                + " points=" + contact.pointsBefore + "->" + contact.pointsAfter + " removed=" + contact.removed
                                + " attempts=" + contact.attempts.stream().map(Attempt::summary).toList());
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

        private Points points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return new Points(nbt.getInt("StrengthPoints"), nbt.getFloat("PointsIncFrac"));
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Bubble cutter damage observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Cutter watchdog: userTicks=" + userTicks + " shots=" + cutters.size()
                        + " hits=" + contacts.size() + " phase=" + (action == null ? "none" : action.getPhase() + "/" + action.getPhaseTick()));
                if (!pressed && userTicks >= 2) press();
                helper.assertTrue(cutters.size() <= 8 && contacts.size() <= 8, "Unexpected duplicate cutter volley or hit");
                if (cutters.size() == 8 && contacts.size() == 8) {
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
            helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(target.blockPosition()),
                    "Cutter user or target is not entity-ticking");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "Registered Bubble Cutter CLICK was not admitted");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof HamonBubbleCutterAbility.BubbleCutterInstance shot
                            && shot == LivingComponentAction.getComponent(user).getAction() && shot.ability == ability,
                    "Registered cutter input did not install its player action");
            action = (EntityActionInstance) input.action;
            log("press ability=" + ability.getAbilityId() + " naturalUserTicks=" + userTicks + " points=" + points());
        }

        private void validate() {
            helper.assertTrue(target.isAlive() && cutters.values().stream().allMatch(t -> t.contact != null
                            && t.cutter.getType() == ModEntityTypes.HAMON_BUBBLE_CUTTER.get() && t.cutter.getOwner() == user),
                    "Cutter hit coverage or ownership is invalid");
            double awarded = contacts.stream().mapToDouble(c -> c.pointsAfter.total() - c.pointsBefore.total()).sum();
            log("result realHits=" + contacts.size() + " health=" + contacts.get(0).healthBefore + "->" + target.getHealth()
                    + " attributedTraining=" + awarded);
            for (Contact contact : contacts) {
                helper.assertTrue(contact.removed && !contact.attempts.isEmpty(), "Cutter did not reach real damage dispatch");
                helper.assertTrue(contact.attempts.stream().allMatch(a -> a.amount > 0 && a.expectedHamon > 0
                                && user.getUUID().equals(a.cause) && a.event.isCanceled() == refuseDamage),
                        "Cutter damage eligibility, ownership or refusal is invalid");
            }
            if (refuseDamage) {
                helper.assertTrue(contacts.stream().allMatch(c -> c.healthBefore == c.healthAfter), "Refused damage changed target health");
                helper.assertTrue(Math.abs(awarded) < EPSILON, "Refused cutter damage trained Strength: delta=" + awarded);
            }
            else {
                helper.assertTrue(target.getHealth() < contacts.get(0).healthBefore && awarded > EPSILON,
                        "Normal cutter contact did not damage and train");
            }
            Contact first = contacts.get(0);
            helper.assertTrue(first.attempts.stream().anyMatch(a -> a.channel.equals("physical") && Math.abs(a.amount - 1.0F) < EPSILON),
                    "Cutter omitted its physical 1.0 damage channel");
            for (Contact contact : contacts) {
                List<Attempt> hamonAttempts = contact.attempts.stream().filter(a -> a.channel.equals("hamon")).toList();
                helper.assertTrue(!hamonAttempts.isEmpty() && hamonAttempts.stream().allMatch(a -> Math.abs(a.amount - a.expectedHamon) < EPSILON),
                        "Cutter Hamon dose is not donor base 0.3 with actual modifiers");
                helper.assertTrue(contact.attempts.stream().allMatch(a -> !a.channel.equals("unexpected") && contact.cutter.equals(a.direct)),
                        "Cutter damage source lost its direct projectile identity");
            }
        }

        private static UUID id(Entity entity) {
            return entity == null ? null : entity.getUUID();
        }

        private void log(String message) {
            JojoMod.LOGGER.info("BUBBLE-CUTTER-DAMAGE {} {}", refuseDamage ? "refused" : "control", message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            active = null;
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
            }
            finally {
                for (Tracked tracked : cutters.values()) if (!tracked.cutter.isRemoved()) tracked.cutter.discard();
                if (target != null) target.discard();
                if (user != null) user.discard();
                log("cleanup listeners=0 shots=" + cutters.size());
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
