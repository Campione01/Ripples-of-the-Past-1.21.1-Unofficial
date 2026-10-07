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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodGiftAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModDamageTypes;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireBloodGiftParityGameTests {
    private VampireBloodGiftParityGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_surface_reach", timeoutTicks = 200)
    public static void fundedVisibleSurfaceRecipientAcceptsRegisteredGift(GameTestHelper helper) {
        start(helper, Scenario.SURFACE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_near_payment_ledger", timeoutTicks = 200)
    public static void fundedNearGiftConvertsAndMeasuresNaturalPayments(GameTestHelper helper) {
        start(helper, Scenario.NEAR);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_underfunded_admission", timeoutTicks = 200)
    public static void freshUnderfundedGiftHoldIsRejectedBeforePayment(GameTestHelper helper) {
        start(helper, Scenario.UNDERFUNDED);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_retained_aim_occlusion", timeoutTicks = 200)
    public static void retainedRecipientBeyondOccludedReachStopsWithoutFurtherPayment(GameTestHelper helper) {
        start(helper, Scenario.OCCLUDED);
    }

    private enum Scenario {
        SURFACE(2.4D, 350.0F), NEAR(1.5D, 350.0F), UNDERFUNDED(1.5D, 50.0F), OCCLUDED(1.8D, 350.0F),
        RELEASE_59(1.5D, 350.0F), COMMIT_60(1.5D, 350.0F);
        final double separation;
        final float initialBlood;
        Scenario(double separation, float initialBlood) {
            this.separation = separation;
            this.initialBlood = initialBlood;
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_release59", timeoutTicks = 200)
    public static void releaseAfter59NaturalWindupTicksDoesNotConvert(GameTestHelper helper) {
        start(helper, Scenario.RELEASE_59);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_gift_commit60", timeoutTicks = 200)
    public static void giftCommitsOn60thNaturalWindupTickBeforeRelease(GameTestHelper helper) {
        start(helper, Scenario.COMMIT_60);
    }

    private enum Stage { HUMAN_READY, GRANT_SETTLE, ACTIVE, REJECTED, BOUNDARY_SETTLE }

    private record Geometry(double originDistance, double donorDistance, Vec3 eye, AABB recipientBox,
            Vec3 lookHit, boolean visible, boolean clearLookRay) {}

    private record Snapshot(int giverAge, int recipientAge, float blood, float dataBlood,
            float giverHealth, float giverMaxHealth, float recipientHealth, float recipientMaxHealth,
            ResourceLocation recipientType, boolean recipientWeak, ActionPhase phase, float phaseTick,
            boolean actionInstalled, Geometry geometry) {}

    private record TickSample(Stage stage, boolean blocked, Snapshot before, Snapshot after,
            double passiveEstimate, double residualDebit, int measuredPayments) {}

    private static final class Donation {
        final LivingIncomingDamageEvent incoming;
        final float original;
        final float beforeHealth;
        final UUID direct;
        final UUID cause;
        Float applied;
        Float afterHealth;
        Donation(LivingIncomingDamageEvent event) {
            incoming = event;
            original = event.getOriginalAmount();
            beforeHealth = event.getEntity().getHealth();
            direct = id(event.getSource().getDirectEntity());
            cause = id(event.getSource().getEntity());
        }
    }

    private static void start(GameTestHelper helper, Scenario scenario) {
        Fixture fixture = new Fixture(helper, scenario);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error primary) {
            fixture.closeAfterFailure(primary);
            throw primary;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 30;
        private static final double EPS = 0.02D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Scenario scenario;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<TickSample> ledger = new ArrayList<>();
        private final List<Double> controlDebits = new ArrayList<>();
        private final List<Donation> donations = new ArrayList<>();
        private final List<String> giverKnockbacks = new ArrayList<>();
        private Player giver;
        private Player recipient;
        private PlayerPower power;
        private PlayerPower recipientPower;
        private VampirismData data;
        private VampirismBloodGiftAbility ability;
        private EntityActionInstance action;
        private Stage stage = Stage.HUMAN_READY;
        private Snapshot before;
        private Stage beforeStage;
        private boolean beforeBlocked;
        private Throwable observerFailure;
        private int giverTicks;
        private int recipientTicks;
        private int grantedAtTick;
        private int lastObservedGiverAge = -1;
        private int terminalSamples;
        private int naturalPaymentCount;
        private int entryPayments;
        private double passive;
        private double entryDebit;
        private boolean granted;
        private boolean pressed;
        private boolean wallPlaced;
        private boolean closed;
        private int boundaryWindupPosts;
        private int boundarySettlePosts;
        private long boundaryInputGeneration;

        Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.scenario = scenario;
        }

        private void premise(boolean condition, String message) {
            helper.assertTrue(condition, "GIFT-PREMISE: " + message);
        }

        private void oracle(boolean condition, String message) {
            helper.assertTrue(condition, "GIFT-ORACLE: " + message);
        }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL && !level.dimensionType().ultraWarm(),
                    "requires normal non-ultrawarm world");
            premise(JojoModConfig.getCommonConfigInstance(false).bloodTickDown.get()
                            .equals(List.of(0.13889D, 0.00278D, 0.00278D, 0.0D)),
                    "passive blood profile is not the unchanged default");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 6.625D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2,
                    origin.x + 2, origin.y + 4, origin.z + 5);
            premise(space.maxY < level.getMaxBuildHeight(), "scene exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                premise(level.isEmptyBlock(pos), "scene is not empty at " + pos);
            }
            premise(level.getEntities((Entity) null, space).isEmpty(), "scene contains a foreign entity");
            BlockPos center = BlockPos.containing(origin);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 4; z++) {
                    putBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    putBlock(center.offset(x, 3, z), Blocks.STONE.defaultBlockState());
                }
            }

            giver = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            recipient = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            premise(!giver.getUUID().equals(recipient.getUUID()), "actor UUIDs are not distinct");
            giver.setNoGravity(true);
            recipient.setNoGravity(true);
            giver.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            recipient.moveTo(origin.x, origin.y, origin.z + scenario.separation, 180.0F, 0.0F);
            giver.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            giver.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            recipient.setHealth(6.0F);
            recipient.getFoodData().setFoodLevel(17);
            premise(level.addFreshEntity(giver) && level.addFreshEntity(recipient), "could not add owned actors");
            power = PowerClass.PLAYER_POWER.attachGet(giver);
            recipientPower = PowerClass.PLAYER_POWER.attachGet(recipient);
            premise(!power.hasPower() && !recipientPower.hasPower() && giver.getHealth() == 20.0F
                            && giver.getMaxHealth() == 20.0F && recipient.getHealth() == 6.0F,
                    "fresh human profile differs; recipient6HP is explicit fixture bootstrap, not client injury evidence");
            registerObservers();
            log("setup giver=" + giver.getUUID() + " recipient=" + recipient.getUUID() + " separation=" + scenario.separation
                    + " bloodBootstrap=" + scenario.initialBlood + " ownedCells=" + blocks.size()
                    + " recipientHPBootstrap=6 food17 noGravity=true grantDelayedUntilActualShade=true");
        }

        private void putBlock(BlockPos pos, BlockState state) {
            BlockPos owned = pos.immutable();
            premise(!blocks.containsKey(owned), "duplicate saved cell " + owned);
            blocks.put(owned, level.getBlockState(owned));
            premise(level.setBlock(owned, state, 3), "could not place owned scene block");
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != giver && event.getEntity() != recipient) return;
                premise(!event.isCanceled() && level.isPositionEntityTicking(giver.blockPosition())
                                && level.isPositionEntityTicking(recipient.blockPosition()),
                        "actors are not naturally ticking in their owned chunk");
                if (event.getEntity() != giver) return;
                if (!granted) {
                    premise(giver.isAlive() && recipient.isAlive() && giver.getHealth() == 20.0F
                                    && giver.getMaxHealth() == 20.0F && recipient.getHealth() == 6.0F
                                    && recipient.getFoodData().getFoodLevel() < 18
                                    && !power.hasPower() && !recipientPower.hasPower(),
                            "human shade-warmup eligibility changed: giverHP=" + giver.getHealth()
                                    + " recipientHP=" + recipient.getHealth() + " recipientFood=" + recipient.getFoodData().getFoodLevel());
                    return;
                }
                premise(before == null && giver.tickCount > lastObservedGiverAge, "duplicate or unpaired giver Pre");
                requireOrdinaryState();
                before = snapshot();
                beforeStage = stage;
                beforeBlocked = wallPlaced;
            });
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() == recipient) {
                    premise(false, "unexpected recipient damage source=" + event.getSource());
                }
                if (event.getEntity() != giver) return;
                premise(before != null && pressed && event.getSource().is(ModDamageTypes.BLOOD_GIFT),
                        "giver damage is outside the natural Gift observation or is not donation");
                donations.add(new Donation(event));
            });
            Consumer<LivingDamageEvent.Post> damagePost = event -> observe(() -> {
                if (event.getEntity() != giver) return;
                Donation donation = donations.stream().filter(d -> d.incoming.getSource() == event.getSource() && d.applied == null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("GIFT-PREMISE: unpaired donation damage Post"));
                donation.applied = event.getNewDamage();
                donation.afterHealth = giver.getHealth();
                log("donation original=" + donation.original + " applied=" + donation.applied
                        + " HP=" + donation.beforeHealth + "->" + donation.afterHealth
                        + " direct=" + donation.direct + " cause=" + donation.cause);
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == recipient) recipientTicks++;
                if (event.getEntity() != giver) return;
                giverTicks++;
                if (before == null) return;
                Snapshot after = snapshot();
                premise(after.giverAge >= before.giverAge && before.giverAge > lastObservedGiverAge,
                        "Pre/Post does not identify exactly one natural giver tick");
                premise(Math.abs(after.blood - after.dataBlood) < 1.0E-4F, "blood state/data mirror diverged");
                double rawDebit = before.blood - after.blood;
                double residual = Double.NaN;
                int payments = 0;
                if (beforeStage == Stage.GRANT_SETTLE) {
                    premise(before.phase == null && after.phase == null && before.giverHealth == after.giverHealth
                                    && after.recipientType == null && after.recipientHealth == 6.0F,
                            "grant settling includes an action, healing or recipient conversion");
                    premise(rawDebit >= 0.0D && rawDebit < 0.05D, "control debit is not small passive-only drain=" + rawDebit);
                    controlDebits.add(rawDebit);
                }
                else {
                    residual = rawDebit - passive;
                    payments = classifyPayment(residual, "natural age=" + after.giverAge);
                    naturalPaymentCount += payments;
                }
                TickSample sample = new TickSample(beforeStage, beforeBlocked, before, after, passive, residual, payments);
                ledger.add(sample);
                lastObservedGiverAge = after.giverAge;
                log("ledger stage=" + sample.stage + " blocked=" + sample.blocked + " ages=" + after.giverAge + "/" + after.recipientAge
                        + " phase=" + before.phase + "/" + before.phaseTick + "->" + after.phase + "/" + after.phaseTick
                        + " blood=" + before.blood + "->" + after.blood + " passive=" + passive
                        + " residual=" + residual + " payments=" + payments + " HP=" + before.giverHealth + "->" + after.giverHealth
                        + " recipient=" + before.recipientType + "/" + before.recipientHealth + "->" + after.recipientType + "/" + after.recipientHealth);
                before = null;
                if (isBoundaryCase()) {
                    observeBoundary(sample);
                    return;
                }
                if (sample.stage == Stage.ACTIVE && sample.blocked) {
                    oracle(sample.after.recipientType == null && sample.measuredPayments == 0,
                            "occluded recipient caused continued Gift payment/conversion; surface=" + sample.before.geometry.donorDistance);
                    oracle(LivingComponentAction.getCurEntityAction(giver) == null && action.isOver(),
                            "first natural blocked tick did not stop/clear Gift WINDUP");
                    terminalSamples++;
                }
                else if (sample.stage == Stage.ACTIVE && sample.after.recipientType != null) {
                    terminalSamples++;
                }
                else if (sample.stage == Stage.REJECTED) {
                    oracle(sample.after.recipientType == null && sample.after.phase == null && sample.measuredPayments == 0,
                            "rejected input created an action, paid blood or converted recipient");
                    terminalSamples++;
                }
            });
            Consumer<LivingKnockBackEvent> knockback = event -> observe(() -> {
                if (event.getEntity() == giver && !event.isCanceled() && event.getStrength() > 0.0F) {
                    giverKnockbacks.add("[strength=" + event.getOriginalStrength() + "->" + event.getStrength() + " ratio="
                            + event.getRatioX() + "," + event.getRatioZ() + "]");
                }
            });
            addListener(knockback, LivingKnockBackEvent.class, true);
            addListener(pre, EntityTickEvent.Pre.class, true);
            addListener(incoming, LivingIncomingDamageEvent.class, true);
            listeners.add(damagePost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damagePost);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private <T extends net.neoforged.bus.api.Event> void addListener(Consumer<T> listener, Class<T> eventType, boolean receiveCanceled) {
            listeners.add(listener);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, receiveCanceled, eventType, listener);
        }

        private void requireOrdinaryState() {
            premise(giver.isAlive() && recipient.isAlive() && !giver.isCreative() && !giver.getAbilities().instabuild
                            && !giver.isSpectator() && !recipient.isCreative() && !recipient.isSpectator()
                            && !giver.isInvulnerable() && !recipient.isInvulnerable()
                            && !giver.isOnFire() && !recipient.isOnFire() && giver.getMainHandItem().isEmpty()
                            && !level.canSeeSky(BlockPos.containing(giver.getEyePosition()))
                            && !level.canSeeSky(BlockPos.containing(recipient.getEyePosition()))
                            && power.getPowerType() == ModPlayerPowers.VAMPIRISM.get()
                            && PlayerPower.getPowerData(giver, ModPlayerPowers.VAMPIRISM).orElse(null) == data
                            && !data.isBeingCured() && data.getCuringStage(giver) == 0 && !data.isVampireAtFullPower()
                            && !data.isAbilityOnCooldown(ability.name()) && giver.getEffect(MobEffects.REGENERATION) == null
                            && Math.abs(data.getBloodLevel() - blood()) < 1.0E-4F
                            && Math.abs(VampirismState.get(giver).blood().max() - 1000.0F) < 0.01F
                            && ability.checkMainModLogicConditions(power).isPositive(),
                    "ordinary weak-vampire state changed: HP=" + giver.getHealth() + "/" + giver.getMaxHealth()
                            + " blood=" + blood() + " weak=" + !data.isVampireAtFullPower() + " cure=" + data.getCuringStage(giver)
                            + " sky=" + level.canSeeSky(BlockPos.containing(giver.getEyePosition()))
                            + " main=" + ability.checkMainModLogicConditions(power));
            if (recipientPower.getPowerType() == null) {
                premise(giver.getHealth() == 20.0F && recipient.getHealth() == 6.0F && recipient.getFoodData().getFoodLevel() < 18,
                        "ordinary health bootstrap changed before actual conversion");
                requireGeometry(wallPlaced);
            }
        }

        private void grantAfterShade() {
            premise(!granted && before == null && giver.getHealth() == 20.0F && !power.hasPower() && !recipientPower.hasPower(),
                    "grant profile is not fresh human/20HP");
            premise(power.trySetPowerType(ModPlayerPowers.VAMPIRISM.get()), "real Vampirism grant failed");
            data = PlayerPower.getPowerData(giver, ModPlayerPowers.VAMPIRISM).orElseThrow();
            VampirismState.get(giver).blood().setCurrent(scenario.initialBlood);
            data.setBloodLevel(scenario.initialBlood);
            var found = power.getAbility("vampirism_blood_gift");
            premise(found instanceof VampirismBloodGiftAbility
                            && found.abilityType == VampirismPowerType.VAMPIRE_BLOOD_GIFT.get(), "registered Gift moveset/type absent");
            ability = (VampirismBloodGiftAbility) found;
            granted = true;
            grantedAtTick = giverTicks;
            stage = Stage.GRANT_SETTLE;
            log("grant actualHP=" + giver.getHealth() + "/" + giver.getMaxHealth() + " weak=" + !data.isVampireAtFullPower()
                    + " bloodBootstrapOnce=" + blood() + " passives=" + passiveSummary()
                    + " naturallyObservedTicks=" + giverTicks + "/" + recipientTicks);
        }

        private Vec3 donorAimPoint() {
            AABB box = recipient.getBoundingBox();
            double ratio = giver.getBbHeight() == 0.0F ? 0.0D : (double) (giver.getEyeHeight() / giver.getBbHeight());
            return new Vec3(Mth.lerp(0.5D, box.minX, box.maxX), Mth.lerp(ratio, box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
        }

        private Geometry geometry() {
            Vec3 eye = giver.getEyePosition(1.0F);
            AABB box = recipient.getBoundingBox();
            double donor = box.contains(eye) ? 0.0D : box.clip(eye, donorAimPoint())
                    .map(hit -> eye.distanceTo(hit) - (double) (giver.getBbWidth() / 2.0F)).orElse(-1.0D);
            Vec3 hit = box.clip(eye, eye.add(giver.getLookAngle().scale(4.0D)))
                    .orElse(null);
            boolean clear = hit != null && level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, giver))
                    .getType() == HitResult.Type.MISS;
            return new Geometry(giver.position().distanceTo(recipient.position()), donor, eye, box, hit,
                    giver.hasLineOfSight(recipient), clear);
        }

        private void requireGeometry(boolean blocked) {
            Geometry geometry = geometry();
            premise(geometry.lookHit != null && geometry.visible == !blocked && geometry.clearLookRay == !blocked && geometry.donorDistance >= 0.0D
                            && Math.abs(geometry.originDistance - scenario.separation) < 1.0E-4D,
                    "geometry/LOS differs from declared scene: " + geometry);
            if (blocked) {
                premise(geometry.donorDistance > 1.0D && geometry.donorDistance < 2.0D,
                        "stone scene does not discriminate obstructed1 versus visible2");
            }
            else {
                premise(geometry.donorDistance <= 2.0D, "recipient is outside independent donor visible reach");
                if (scenario == Scenario.SURFACE) premise(geometry.originDistance > 2.0D, "surface case lost origin discriminator");
            }
        }

        private void prepareAim() {
            Vec3 direction = donorAimPoint().subtract(giver.getEyePosition());
            giver.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            giver.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            giver.setYHeadRot(giver.getYRot());
            giver.yBodyRot = giver.getYRot();
            Geometry initial = geometry();
            premise(initial.visible && initial.clearLookRay, "initial server aim is not genuinely visible");
            LivingComponentAction.getComponent(giver).entityAim.setTarget(
                    ActionTarget.fromVanilla(new EntityHitResult(recipient, initial.lookHit)));
            requireGeometry(false);
        }

        private void pressOrReject() {
            premise(before == null && controlDebits.size() >= 3, "no independent natural passive control interval");
            passive = controlDebits.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            premise(controlDebits.stream().allMatch(value -> Math.abs(value - passive) < 0.001D),
                    "passive control is not stable: " + controlDebits);
            prepareAim();
            requireOrdinaryState();
            if (scenario == Scenario.UNDERFUNDED) premise(blood() > 5.0F && blood() < 300.0F, "underfunded discriminator changed");
            else premise(blood() > 300.0F, "fully funded discriminator changed");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            boolean admitted = AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), giver, InputMethod.HOLD);
            log("admission allowed=" + admitted + " sourceSpecific=" + ability.checkSpecificConditions(power)
                    + " blood=" + blood() + " donorFreshRemaining=300 passiveMeasured=" + passive + " geometry=" + geometry());
            if (scenario == Scenario.UNDERFUNDED) {
                oracle(!admitted, "fresh Survival Gift HOLD admitted without donor remaining300 funding; actualBlood=" + blood());
                stage = Stage.REJECTED;
                return;
            }
            oracle(admitted, "funded donor-visible recipient rejected by registered Gift HOLD; geometry=" + geometry());
            Snapshot entryBefore = snapshot();
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, giver, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            oracle(input != null && input.action instanceof VampirismBloodGiftAbility.BloodGiftInstance gift
                            && gift.ability == ability && gift == LivingComponentAction.getCurEntityAction(giver),
                    "registered Gift HOLD did not install its concrete action");
            action = (EntityActionInstance) input.action;
            if (isBoundaryCase()) {
                boundaryInputGeneration = input.generation;
                premise(boundaryInputGeneration > 0 && action.getPhaseTick() == 0.0F
                                && AbilityInput.isHeldByKey(giver, action),
                        "boundary entry lacks actual zero-skip held generation");
            }
            Snapshot entryAfter = snapshot();
            premise(entryAfter.phase == ActionPhase.WINDUP && action.getCurPhaseLength() == 60.0F
                            && entryAfter.recipientType == null && entryAfter.giverHealth == 20.0F,
                    "entry is not an ordinary uncompleted60-tick WINDUP");
            entryDebit = entryBefore.blood - entryAfter.blood;
            entryPayments = classifyPayment(entryDebit, "synchronous registered entry interval");
            stage = Stage.ACTIVE;
            log("entry zeroSkipArgument=0 actualPhase=" + entryAfter.phase + " actualCounter=" + entryAfter.phaseTick
                    + " blood=" + entryBefore.blood + "->" + entryAfter.blood + " entryPayments=" + entryPayments
                    + " originalHP20 maxAfterRealGrant=" + entryAfter.giverMaxHealth + " grantWarmTicks=" + (giverTicks - grantedAtTick));
        }

        private int classifyPayment(double residual, String interval) {
            if (Math.abs(residual) <= EPS) return 0;
            if (Math.abs(residual - 5.0D) <= EPS) return 1;
            premise(false, "unclassified resource measurement " + interval + " residual=" + residual
                    + "; do not infer a charge-count regression from unknown debit");
            return -1;
        }

        private boolean isBoundaryCase() {
            return scenario == Scenario.RELEASE_59 || scenario == Scenario.COMMIT_60;
        }

        private void observeBoundary(TickSample sample) {
            if (sample.stage == Stage.ACTIVE) {
                int counter = boundaryWindupPosts++;
                int limit = scenario == Scenario.RELEASE_59 ? 59 : 60;
                oracle(counter < limit && sample.before.actionInstalled && sample.before.phase == ActionPhase.WINDUP
                                && sample.before.phaseTick == counter && !sample.blocked,
                        "boundary lost ordered owned WINDUP counter=" + counter + " sample=" + sample);
                int expectedPayment = counter < 59 ? 1 : 0;
                oracle(sample.measuredPayments == expectedPayment
                                && Math.abs(sample.residualDebit - expectedPayment * 5.0D) <= EPS,
                        "boundary payment placement differs at counter=" + counter);
                if (boundaryWindupPosts < limit || scenario == Scenario.RELEASE_59) {
                    oracle(sample.after.recipientType == null && sample.after.recipientHealth == 6.0F
                                    && sample.after.giverHealth == 20.0F && donations.isEmpty()
                                    && sample.after.phase == ActionPhase.WINDUP
                                    && sample.after.phaseTick == boundaryWindupPosts && sample.after.actionInstalled,
                            "Gift converted, donated or ended before completion boundary=" + boundaryWindupPosts);
                }
                if (boundaryWindupPosts == limit) {
                    log("boundary reached=" + limit + " generation=" + boundaryInputGeneration
                            + " donations=" + donations.size() + " snapshot=" + sample.after);
                    if (scenario == Scenario.COMMIT_60) {
                        oracle(recipientPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()
                                        && sample.after.recipientWeak && sample.after.recipientHealth > 6.0F
                                        && sample.after.giverHealth == 10.0F && donations.size() == 1
                                        && donations.get(0).applied != null && donations.get(0).applied == 10.0F,
                                "60th qualified WINDUP Post has no same-tick conversion/donation; " + sample.after);
                    }
                    float bloodBeforeRelease = blood();
                    int donationsBeforeRelease = donations.size();
                    long releasedGeneration = AbilityInput.keyReleaseAndGetGeneration(KEY, giver);
                    oracle(releasedGeneration == boundaryInputGeneration && !AbilityInput.isHeldByKey(giver, action)
                                    && action.isOver() && blood() == bloodBeforeRelease
                                    && donations.size() == donationsBeforeRelease
                                    && giver.getHealth() == sample.after.giverHealth
                                    && recipient.getHealth() == sample.after.recipientHealth
                                    && recipientPower.getPowerType() == (scenario == Scenario.COMMIT_60
                                            ? ModPlayerPowers.VAMPIRISM.get() : null),
                            "boundary release changed result/resources or released the wrong generation");
                    stage = Stage.BOUNDARY_SETTLE;
                    log("boundary released=" + releasedGeneration + " settleRequired=2");
                }
            }
            else if (sample.stage == Stage.BOUNDARY_SETTLE) {
                oracle(sample.measuredPayments == 0 && Math.abs(sample.residualDebit) <= EPS
                                && action.isOver() && !sample.after.actionInstalled && sample.after.phase == null
                                && !AbilityInput.isHeldByKey(giver, action),
                        "released boundary produced another Gift debit/action during settling");
                if (scenario == Scenario.RELEASE_59) {
                    oracle(sample.after.recipientType == null && sample.after.recipientHealth == 6.0F
                                    && sample.after.giverHealth == 20.0F && donations.isEmpty(),
                            "release59 converted or donated during settling");
                }
                else oracle(recipientPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()
                                && sample.after.recipientWeak && sample.after.giverHealth == 10.0F
                                && donations.size() == 1,
                        "commit60 release lost or repeated the result during settling");
                boundarySettlePosts++;
            }
        }

        private void insertOwnedStone() {
            premise(before == null && scenario == Scenario.OCCLUDED && !wallPlaced && naturalPaymentCount > 0,
                    "stone insertion is not after an observed qualified natural payment");
            premise(recipientPower.getPowerType() == null && action != null && !action.isOver()
                            && action.getPhase() == ActionPhase.WINDUP, "charge completed before selected occlusion point");
            BlockPos wall = giver.blockPosition().south();
            for (BlockPos pos : List.of(wall, wall.above())) {
                AABB cube = new AABB(pos);
                premise(!cube.intersects(giver.getBoundingBox()) && !cube.intersects(recipient.getBoundingBox()),
                        "stone cube intersects actor/recipient; no box or position adjustment is allowed");
                putBlock(pos, Blocks.STONE.defaultBlockState());
            }
            wallPlaced = true;
            premise(LivingComponentAction.getAim(giver).getTarget().getEntity() == recipient,
                    "real public server aim was replaced before occlusion");
            requireGeometry(true);
            log("occlusion realStone=true retainedServerAim=true paidNaturalTicks=" + naturalPaymentCount
                    + " geometry=" + geometry() + " blood=" + blood() + " ownedCells=" + blocks.size());
        }

        private Snapshot snapshot() {
            EntityActionInstance current = LivingComponentAction.getCurEntityAction(giver);
            var type = recipientPower.getPowerType();
            VampirismData recipientData = type == ModPlayerPowers.VAMPIRISM.get()
                    ? PlayerPower.getPowerData(recipient, ModPlayerPowers.VAMPIRISM).orElse(null) : null;
            return new Snapshot(giver.tickCount, recipient.tickCount, blood(), data.getBloodLevel(),
                    giver.getHealth(), giver.getMaxHealth(), recipient.getHealth(), recipient.getMaxHealth(),
                    type == null ? null : type.getId(), recipientData != null && !recipientData.isVampireAtFullPower(),
                    current == null ? null : current.getPhase(), current == null ? Float.NaN : current.getPhaseTick(),
                    current != null && current == action, geometry());
        }

        private float blood() {
            return VampirismState.get(giver).blood().current();
        }

        private String passiveSummary() {
            var boost = giver.getEffect(MobEffects.HEALTH_BOOST);
            var regeneration = giver.getEffect(MobEffects.REGENERATION);
            return "healthBoost=" + (boost == null ? -1 : boost.getAmplifier())
                    + ",regeneration=" + (regeneration == null ? -1 : regeneration.getAmplifier());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 180, "watchdog stage=" + stage + " naturalTicks=" + giverTicks + "/" + recipientTicks);
                if (!granted && giverTicks >= 2 && recipientTicks >= 2) {
                    premise(blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                            "human-ready roof/floor changed");
                    boolean shade = !level.canSeeSky(BlockPos.containing(giver.getEyePosition()))
                            && !level.canSeeSky(BlockPos.containing(recipient.getEyePosition()));
                    log("human-shade-readiness ticks=" + giverTicks + "/" + recipientTicks + " shaded=" + shade
                            + " giverHP=" + giver.getHealth() + " recipientHP=" + recipient.getHealth()
                            + " recipientFood=" + recipient.getFoodData().getFoodLevel()
                            + " roof=" + level.getBlockState(giver.blockPosition().above(3)));
                    if (shade) grantAfterShade();
                }
                else if (stage == Stage.GRANT_SETTLE && controlDebits.size() >= 3) {
                    pressOrReject();
                }
                else if (stage == Stage.ACTIVE) {
                    if (scenario == Scenario.OCCLUDED && !wallPlaced && naturalPaymentCount > 0) insertOwnedStone();
                    if (scenario != Scenario.OCCLUDED && action != null && action.isOver() && recipientPower.getPowerType() == null) {
                        oracle(false, "qualified funded Gift ended without conversion; lastSample=" + lastSample());
                    }
                }
                if (isBoundaryCase() ? boundarySettlePosts >= 2 : terminalSamples >= 3) {
                    validateOutcome();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error primary) {
                closeAfterFailure(primary);
                throw primary;
            }
        }

        private TickSample lastSample() {
            return ledger.isEmpty() ? null : ledger.get(ledger.size() - 1);
        }

        private void validateOutcome() {
            premise(!ledger.isEmpty() && lastObservedGiverAge >= 3, "no actual natural ledger");
            Snapshot initial = ledger.get(0).before;
            Snapshot last = lastSample().after;
            premise(last.giverAge > initial.giverAge && last.recipientAge > initial.recipientAge,
                    "ledger lacks natural age progress for both owned actors");
            if (scenario == Scenario.UNDERFUNDED || scenario == Scenario.OCCLUDED || scenario == Scenario.RELEASE_59) {
                oracle(recipientPower.getPowerType() == null && recipient.getHealth() == 6.0F
                                && giver.getHealth() == 20.0F && donations.isEmpty()
                                && LivingComponentAction.getCurEntityAction(giver) == null,
                        "negative outcome changed recipient, donation or active action");
            }
            else {
                Snapshot result = snapshot();
                oracle(recipientPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get() && result.recipientWeak
                                && result.recipientHealth > 6.0F
                                && Math.abs(result.recipientHealth - result.recipientMaxHealth) < 1.0E-4F,
                        "near/surface completion did not actually heal and grant weak recipient Vampirism: " + result);
                oracle(donations.size() == 1, "completion did not contain exactly one ordinary donation event");
                Donation donation = donations.get(0);
                oracle(!donation.incoming.isCanceled() && Math.abs(donation.original - 10.0F) < 1.0E-4F
                                && donation.applied != null && Math.abs(donation.applied - 10.0F) < 1.0E-4F
                                && donation.afterHealth != null
                                && Math.abs(donation.beforeHealth - donation.afterHealth - 10.0F) < 1.0E-4F
                                && donation.direct == null && donation.cause == null && giver.getHealth() == 10.0F,
                        "actual10HP unattributed donation is absent or differs from the ordinary profile");
                oracle(giverKnockbacks.isEmpty(),
                        "the donation pushed the giver, 1.16 blood_gift has no attacker and no knockback: " + giverKnockbacks);
                oracle(action.isOver() && LivingComponentAction.getCurEntityAction(giver) == null,
                        "completed Gift action did not naturally end/clear");
                oracle(naturalPaymentCount + entryPayments > 0, "Survival conversion had no measured paid interval");
            }
            double corrected = ledger.stream().filter(sample -> sample.stage != Stage.GRANT_SETTLE)
                    .mapToDouble(TickSample::residualDebit).sum();
            if (scenario == Scenario.NEAR) validateNearPaymentTimeline(corrected + entryDebit);
            if (isBoundaryCase()) {
                oracle(boundaryWindupPosts == (scenario == Scenario.RELEASE_59 ? 59 : 60)
                                && boundarySettlePosts == 2 && entryPayments == 0 && entryDebit == 0.0D
                                && naturalPaymentCount == 59 && Math.abs(corrected + entryDebit - 295.0D) <= EPS,
                        "boundary ledger lacks exact observed posts, zero entry or nominal295");
                if (scenario == Scenario.COMMIT_60) validateNearPaymentTimeline(corrected + entryDebit);
                log("BOUNDARY_VERIFIED windupPosts=" + boundaryWindupPosts + " settlePosts=" + boundarySettlePosts
                        + " generation=" + boundaryInputGeneration + " donations=" + donations.size());
            }
            log("RESULT qualifiedOutcome=true measuredNaturalPayments=" + naturalPaymentCount + " measuredEntryPayments=" + entryPayments
                    + " measuredNominalGiftDebit=" + (corrected + entryDebit) + " passiveControl=" + controlDebits
                    + " charge59vs60=" + (scenario == Scenario.NEAR ? "NEAR_TIMELINE_VERIFIED" : "MEASUREMENT_ONLY") + " actualFirstCounter="
                    + ledger.stream().filter(sample -> sample.stage == Stage.ACTIVE).map(sample -> sample.before.phaseTick).findFirst().orElse(Float.NaN)
                    + " giverHP=" + giver.getHealth() + " recipientHP=" + recipient.getHealth() + "/" + recipient.getMaxHealth());
        }

        private void validateNearPaymentTimeline(double nominalDebit) {
            oracle(entryPayments == 0 && entryDebit == 0.0D,
                    "NEAR Gift paid on registered entry; payments=" + entryPayments + " debit=" + entryDebit);
            List<TickSample> windup = ledger.stream()
                    .filter(sample -> sample.stage == Stage.ACTIVE && sample.before.phase == ActionPhase.WINDUP).toList();
            oracle(windup.size() == 60, "NEAR Gift lacks exactly60 natural ACTIVE/WINDUP samples; actual=" + windup.size());
            for (int counter = 0; counter < 60; counter++) {
                TickSample sample = windup.get(counter);
                int expectedPayment = counter < 59 ? 1 : 0;
                oracle(sample.before.phaseTick == counter,
                        "NEAR Gift counter sequence differs at index=" + counter + " actual=" + sample.before.phaseTick);
                oracle(sample.measuredPayments == expectedPayment
                                && Math.abs(sample.residualDebit - expectedPayment * 5.0D) <= EPS,
                        "NEAR Gift payment placement differs at WINDUP=" + counter + " expected=" + expectedPayment
                                + " actual=" + sample.measuredPayments + " residual=" + sample.residualDebit);
            }
            oracle(naturalPaymentCount == 59 && windup.stream().mapToInt(TickSample::measuredPayments).sum() == 59
                            && Math.abs(nominalDebit - 295.0D) <= EPS,
                    "NEAR Gift total differs after verified placement; naturalPayments=" + naturalPaymentCount
                            + " nominalDebit=" + nominalDebit);
        }

        private void observe(Runnable operation) {
            if (closed || observerFailure != null) return;
            try { operation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Gift fixture observer failed", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("VAMPIRE-BLOOD-GIFT {} {}", scenario, message);
        }

        private void cleanupStep(Runnable operation, List<Throwable> failures) {
            try { operation.run(); }
            catch (RuntimeException | Error failure) { failures.add(failure); }
        }

        private void assertNoHeldInputs(Player owner, String role) {
            if (owner != null && owner.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(state -> !state.heldKeys.isEmpty()).orElse(false)) {
                throw new IllegalStateException(role + " held-input entries remain after specific release");
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            before = null;
            cleanupStep(() -> { if (pressed && giver != null) AbilityInput.keyRelease(KEY, giver); }, failures);
            cleanupStep(() -> {
                if (recipient != null && recipient.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                        .map(state -> state.heldKeys.containsKey(KEY)).orElse(false)) {
                    AbilityInput.keyRelease(KEY, recipient);
                }
            }, failures);
            cleanupStep(() -> assertNoHeldInputs(giver, "giver"), failures);
            cleanupStep(() -> assertNoHeldInputs(recipient, "recipient"), failures);
            cleanupStep(() -> {
                if (giver != null) {
                    LivingComponentAction.getComponent(giver).entityAim.setTarget(ActionTarget.EMPTY);
                    LivingComponentAction.getComponent(giver).setAction(null, giver, SyncType.NO_SYNC);
                }
            }, failures);
            cleanupStep(() -> { if (recipient != null) recipient.discard(); }, failures);
            cleanupStep(() -> { if (giver != null) giver.discard(); }, failures);
            for (var entry : blocks.entrySet()) cleanupStep(() -> level.setBlock(entry.getKey(), entry.getValue(), 3), failures);
            boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                    && level.getBlockEntity(entry.getKey()) == null);
            boolean removed = (giver == null || giver.isRemoved() && level.getEntity(giver.getUUID()) == null)
                    && (recipient == null || recipient.isRemoved() && level.getEntity(recipient.getUUID()) == null);
            log("cleanup listeners=0 savedCells=" + blocks.size() + " exactRestore=" + restored + " entitiesRemoved=" + removed
                    + " failures=" + failures.size());
            if (!restored || !removed) failures.add(new IllegalStateException("owned Gift cleanup was not exact"));
            blocks.clear();
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException("Gift fixture cleanup failed");
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        }

        private void closeAfterFailure(Throwable primary) {
            try { close(); }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Gift cleanup failed; original failure retained", cleanup);
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
