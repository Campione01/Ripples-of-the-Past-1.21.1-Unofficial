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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
import rotp.core.impl.powers.hamon.abilities.HamonTurquoiseBlueOverdriveAbility;
import rotp.core.impl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.network.s2c.TrHamonParticlesPacket;
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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTurquoiseSourceGameTests {
    private HamonTurquoiseSourceGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "turquoise_source_accepted", timeoutTicks = 120)
    public static void registeredTurquoiseHitKeepsItsWaveDirectSource(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "turquoise_source_canceled", timeoutTicks = 120)
    public static void canceledTurquoiseHitAwardsNoTrainingOrHitSparks(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean cancelDamage) {
        Fixture fixture = new Fixture(helper, cancelDamage);
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

    private record Before(int age, long time, Vec3 position, Vec3 motion, AABB waveBox,
            AABB targetBox, boolean overlap, float health, double points, boolean pointsGiven) {}
    private record Attempt(float amount, UUID direct, UUID cause, Vec3 sourcePosition,
            Vec3 wavePosition, Vec3 ownerPosition, boolean canceledBefore) {}
    private record Contact(Before before, Attempt attempt, boolean canceled, float health,
            double points, boolean pointsGiven, TrHamonParticlesPacket emitter,
            Vec3 targetMotion, Vec3 wavePostPosition, boolean waveAlive, boolean waveWet) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short INPUT_KEY = 26;
        private static final double EPS = 2.0E-5D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean cancelDamage;
        private final List<Object> listeners = new ArrayList<>();
        private final Map<UUID, HamonTurquoiseBlueOverdriveEntity> owned = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<BlockPos> waterCells = new ArrayList<>();
        private Player user;
        private Cow target;
        private PlayerPower power;
        private StandPower stand;
        private HamonData hamon;
        private Ability ability;
        private HamonTurquoiseBlueOverdriveEntity wave;
        private AABB waterVolume;
        private BlockPos guardMin;
        private BlockPos guardMax;
        private ChunkPos chunk;
        private Before pre;
        private Attempt attempt;
        private LivingIncomingDamageEvent incoming;
        private Contact contact;
        private Throwable observerFailure;
        private int userTicks;
        private int targetTicks;
        private int inputUserTicks;
        private int completedWaveTicks;
        private boolean sawWindup;
        private boolean waveJoinedOnFirstActionTick;
        private float radius;
        private float baseDamage;
        private float basePoints;
        private int duration;
        private float initialHealth;
        private double inputPoints;
        private boolean inputPressed;
        private boolean inputReleased;
        private boolean sawSeparatedTick;
        private boolean guardVerified;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean cancelDamage) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.cancelDamage = cancelDamage;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            guardMin = new BlockPos(x + 3, y - 2, z + 1);
            guardMax = new BlockPos(x + 12, y + 6, z + 14);
            AABB guard = AABB.encapsulatingFullBlocks(guardMin, guardMax);
            premise(guardMin.getY() >= level.getMinBuildHeight() && guardMax.getY() < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, guard).isEmpty(), "tank region unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(guardMin, guardMax)) {
                premise(level.isEmptyBlock(pos), "tank or guard region is not empty");
            }
            guardVerified = true;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 4, y - 1, z + 2),
                    new BlockPos(x + 11, y + 5, z + 13))) {
                original.put(pos.immutable(), level.getBlockState(pos));
            }
            for (BlockPos pos : original.keySet()) {
                boolean shell = pos.getX() == x + 4 || pos.getX() == x + 11
                        || pos.getY() == y - 1 || pos.getY() == y + 5
                        || pos.getZ() == z + 2 || pos.getZ() == z + 13;
                if (shell) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                else waterCells.add(pos);
            }
            for (BlockPos pos : waterCells) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            waterVolume = new AABB(x + 5, y, z + 3, x + 11, y + 5, z + 13);

            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(x + 8D, y + 1D, z + 5D, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            premise(level.addFreshEntity(user), "could not add user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            stand = PowerClass.STAND.attachGet(user);
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
            hamon.learnSkill(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get());
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            ability = power.getAbility("turquoise_blue_overdrive");
            premise(ability instanceof HamonTurquoiseBlueOverdriveAbility
                    && ability.abilityType == HamonPowerType.HAMON_TURQUOISE_BLUE_OVERDRIVE.get(), "wrong registered ability");

            target = EntityType.COW.create(level);
            premise(target != null, "could not create target");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            target.setHealth(100F);
            target.moveTo(x + 8D, y + 1D, z + 9D, 180, 0);
            premise(level.addFreshEntity(target), "could not add target");
            initialHealth = target.getHealth();
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " tank=" + waterVolume
                    + " ownedCells=" + original.size() + " waterCells=" + waterCells.size()
                    + " fixtureNoGravity=true targetNoAI=true control=10");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof HamonTurquoiseBlueOverdriveEntity created)
                        || created.getOwner() != user) return;
                // Capture ownership before any assertion can fail inside the synchronous join.
                owned.put(created.getUUID(), created);
                observe(() -> {
                    premise(inputPressed && wave == null && owned.size() == 1 && !event.isCanceled()
                            && created.getType() == ModEntityTypes.TURQUOISE_BLUE_OVERDRIVE.get(), "unexpected wave emission");
                    wave = created;
                    EntityActionInstance action = LivingComponentAction.getCurEntityAction(user);
                    premise(action instanceof HamonTurquoiseBlueOverdriveAbility.TurquoiseBlueOverdriveInstance
                            && action.ability == ability && action.getPhase() == ActionPhase.PERFORM
                            && userTicks == inputUserTicks && !sawWindup,
                            "1.16 spawned the wave on the click: it must join on the user's first tick after the press"
                                    + " (userPostsSinceInput=" + (userTicks - inputUserTicks) + ", sawWindup=" + sawWindup + ")");
                    waveJoinedOnFirstActionTick = true;
                    CompoundTag nbt = wave.saveWithoutId(new CompoundTag());
                    radius = nbt.getFloat("Radius");
                    baseDamage = nbt.getFloat("Damage");
                    basePoints = nbt.getFloat("Points");
                    duration = nbt.getInt("Duration");
                    Vec3 expectedCenter = user.getEyePosition(1F).add(0, -0.3D, 0);
                    Vec3 expectedPosition = expectedCenter.add(0, -radius, 0);
                    log("join uuid=" + wave.getUUID() + " owner=" + user.getUUID() + " userPostsSinceInput="
                            + (userTicks - inputUserTicks) + " sawWindup=" + sawWindup
                            + " radius=" + radius + " baseDamage=" + baseDamage + " points=" + basePoints
                            + " duration=" + duration + " position=" + wave.position() + " motion=" + wave.getDeltaMovement()
                            + " box=" + wave.getBoundingBox() + " targetBox=" + target.getBoundingBox());
                    premise(wave.tickCount == 0 && Float.isFinite(radius) && radius > 0 && Float.isFinite(baseDamage)
                            && baseDamage > 0 && basePoints > 0 && duration == 41 && !nbt.getBoolean("PointsGiven")
                            && Math.abs(radius - (1F + 2.5F * (10F / HamonData.MAX_STAT_LEVEL) * baseDamage)) < EPS
                            && wave.position().distanceTo(expectedPosition) < EPS
                            && wave.getBoundingBox().getCenter().distanceTo(expectedCenter) < EPS
                            && wave.getDeltaMovement().distanceTo(new Vec3(0, 0, 1.5D)) < EPS
                            && wetBox(wave.getBoundingBox()) && !wave.getBoundingBox().intersects(target.getBoundingBox()),
                            "wave launch or initial separation is invalid");
                });
            };
            Consumer<EntityTickEvent.Pre> before = event -> {
                if (event.getEntity() != wave || contact != null) return;
                observe(() -> {
                    requireActors();
                    premise(pre == null && !event.isCanceled() && !wave.isRemoved() && wave.getOwner() == user
                            && !wave.canHitOwner() && wave.tickCount == completedWaveTicks + 1,
                            "wave lacks a consecutive natural Pre");
                    CompoundTag nbt = wave.saveWithoutId(new CompoundTag());
                    AABB box = wave.getBoundingBox();
                    boolean overlap = box.intersects(target.getBoundingBox());
                    List<UUID> candidates = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != user)
                            .stream().map(Entity::getUUID).toList();
                    boolean candidateProof = overlap ? candidates.equals(List.of(target.getUUID())) : candidates.isEmpty();
                    pre = new Before(wave.tickCount, level.getGameTime(), wave.position(), wave.getDeltaMovement(), box,
                            target.getBoundingBox(), overlap, target.getHealth(), training(), nbt.getBoolean("PointsGiven"));
                    attempt = null;
                    incoming = null;
                    log("pre " + pre + " waveWetFlag=" + wave.isInWaterOrBubble() + " targetWet=" + target.isInWaterOrBubble()
                            + " candidates=" + candidates + " ticking=" + ticking(box));
                    premise(wetBox(box) && wetBox(target.getBoundingBox()) && ticking(box) && candidateProof
                            && (wave.tickCount == 1 || wave.isInWaterOrBubble())
                            && !pre.pointsGiven && Math.abs(pre.health - initialHealth) < EPS,
                            "water, candidate or untouched-target premise changed");
                    if (!overlap) sawSeparatedTick = true;
                });
            };
            Consumer<LivingIncomingDamageEvent> damage = event -> {
                if (event.getEntity() != target || contact != null) return;
                observe(() -> {
                    DamageSource source = event.getSource();
                    premise(pre != null && wave != null && wave.tickCount == pre.age && level.getGameTime() == pre.time
                            && pre.overlap && completedWaveTicks >= 1 && sawSeparatedTick && incoming == null
                            && source.is(ModDamageTypes.HAMON) && source.getEntity() == user
                            && event.getOriginalAmount() > 0 && !event.isCanceled()
                            && wave.isInWaterOrBubble() && target.isInWaterOrBubble()
                            && wave.position().distanceTo(pre.position) < EPS
                            && wave.getBoundingBox().intersects(target.getBoundingBox()),
                            "incoming damage is not the first owned natural water contact");
                    incoming = event;
                    attempt = new Attempt(event.getOriginalAmount(), id(source.getDirectEntity()), id(source.getEntity()),
                            source.getSourcePosition(), wave.position(), user.position(), event.isCanceled());
                    if (cancelDamage) event.setCanceled(true);
                    log("incoming wave=" + wave.getUUID() + " age=" + wave.tickCount + " attempt=" + attempt
                            + " canceledByFixture=" + cancelDamage);
                });
            };
            Consumer<EntityTickEvent.Post> after = event -> observe(() -> {
                if (event.getEntity() == user) {
                    userTicks++;
                    if (inputPressed && wave == null) {
                        EntityActionInstance action = LivingComponentAction.getCurEntityAction(user);
                        if (action != null && action.ability == ability && action.getPhase() == ActionPhase.WINDUP) {
                            sawWindup = true;
                            log("windup userTicks=" + userTicks + " phaseTick=" + action.getPhaseTick());
                        }
                    }
                }
                if (event.getEntity() == target) targetTicks++;
                if (event.getEntity() != wave || contact != null) return;
                premise(pre != null && wave.tickCount == pre.age && level.getGameTime() == pre.time,
                        "wave Post lacks its matching natural Pre");
                completedWaveTicks++;
                CompoundTag nbt = wave.saveWithoutId(new CompoundTag());
                TrHamonParticlesPacket emitter = HamonAbilityHelpers.takeLastSparkEmitter();
                premise(!wave.isRemoved() && wave.isInWaterOrBubble() && wetBox(wave.getBoundingBox())
                        && wave.position().distanceTo(pre.position.add(pre.motion)) < EPS,
                        "wave did not complete normal wet constant-motion tick");
                if (incoming == null) {
                    premise(!pre.overlap && emitter == null && Math.abs(target.getHealth() - pre.health) < EPS
                            && !nbt.getBoolean("PointsGiven"), "overlap or side effect lacks an incoming attempt");
                    log("post age=" + pre.age + " noContact=true position=" + wave.position());
                }
                else {
                    contact = new Contact(pre, attempt, incoming.isCanceled(), target.getHealth(), training(),
                            nbt.getBoolean("PointsGiven"), emitter, target.getDeltaMovement(), wave.position(),
                            !wave.isRemoved(), wave.isInWaterOrBubble());
                    log("contact " + contact);
                    release();
                    // Observation ends after this real Post, before another volume-overlap hit.
                    for (HamonTurquoiseBlueOverdriveEntity shot : owned.values()) if (!shot.isRemoved()) shot.discard();
                }
                pre = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(before);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, before);
            listeners.add(damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            listeners.add(after);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, after);
        }

        private void requireActors() {
            premise(user.isAlive() && !user.isCreative() && !user.isSpectator()
                    && !user.getAbilities().instabuild && !user.getAbilities().invulnerable
                    && user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
                    && !user.isUsingItem() && !user.isShiftKeyDown() && user.getYRot() == 0F && user.getXRot() == 0F
                    && power.getPowerType() == ModPlayerPowers.HAMON.get() && stand.getPowerType() == null
                    && hamon.getHamonControlLevel() == 10 && hamon.isSkillLearned(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get())
                    && !hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())
                    && target.isAlive() && !target.isInvulnerable() && target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && !EntityHamonChargeState.get(target).hasHamonCharge()
                    && user.isInWaterOrBubble() && target.isInWaterOrBubble()
                    && wetBox(user.getBoundingBox()) && wetBox(target.getBoundingBox())
                    && ticking(user.getBoundingBox()) && ticking(target.getBoundingBox()), "actors lost normal wet eligibility");
        }

        private void press() {
            requireActors();
            premise(user.getAttackStrengthScale(1F) == 1F && hamon.getEnergy() > 1500F
                    && HamonAbilityHelpers.configHamonDamageMultiplier() > 0F
                    && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0D,
                    "recharge, energy or configured effects unavailable");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "registered Turquoise CLICK not admitted");
            inputPoints = training();
            HamonAbilityHelpers.takeLastSparkEmitter();
            inputUserTicks = userTicks;
            inputPressed = true;
            var held = AbilityInput.keyPress(INPUT_KEY, ability, user, null, InputMethod.CLICK, 0,
                    BufferingState.clickOnly(), ability.getAbilityId());
            premise(held != null && held.action instanceof HamonTurquoiseBlueOverdriveAbility.TurquoiseBlueOverdriveInstance shot
                    && shot.ability == ability && shot == LivingComponentAction.getCurEntityAction(user),
                    "CLICK did not start the registered Turquoise action");
            EntityActionInstance clicked = LivingComponentAction.getCurEntityAction(user);
            // 1.16 ModHamonActions (:60-62) gave it no hold and no windup: the click performs at once.
            helper.assertTrue(clicked.getPhase() == ActionPhase.PERFORM && clicked.getPhaseTick() == 0F
                    && clicked.phasesLength.getFloat(ActionPhase.WINDUP) == 0F,
                    "1.16 Turquoise Blue Overdrive performs on the click, with no windup: phase=" + clicked.getPhase()
                            + ", windup=" + clicked.phasesLength.getFloat(ActionPhase.WINDUP));
            log("input ability=" + ability.getAbilityId() + " userTicks=" + userTicks + " targetTicks=" + targetTicks
                    + " energy=" + hamon.getEnergy() + " points=" + inputPoints + " userWet=" + user.isInWaterOrBubble()
                    + " targetWet=" + target.isInWaterOrBubble());
        }

        private void validate() {
            premise(contact != null && pre == null && owned.size() == 1 && !contact.attempt.canceledBefore
                    && contact.attempt.amount > 0 && contact.attempt.cause.equals(user.getUUID())
                    && contact.waveAlive && contact.waveWet, "first-contact sample is incomplete");
            double trainingDelta = contact.points - contact.before.points;
            double healthLoss = contact.before.health - contact.health;
            boolean blueEmitter = contact.emitter != null && contact.emitter.entityId() == target.getId()
                    && contact.emitter.particle() == ModParticles.HAMON_SPARK_BLUE.get()
                    && contact.emitter.intensity() > 0F && contact.emitter.soundVolume() == 1F;
            double sourceError = contact.attempt.sourcePosition == null ? Double.POSITIVE_INFINITY
                    : contact.attempt.sourcePosition.distanceTo(contact.attempt.wavePosition);
            log("result wave=" + wave.getUUID() + " direct=" + contact.attempt.direct + " cause=" + contact.attempt.cause
                    + " sourceError=" + sourceError + " healthLoss=" + healthLoss + " trainingDelta=" + trainingDelta
                    + " trainingSinceInput=" + (contact.points - inputPoints)
                    + " pointsGiven=" + contact.pointsGiven + " blueEmitter=" + blueEmitter
                    + " canceled=" + contact.canceled + " premises=valid");
            helper.assertTrue(waveJoinedOnFirstActionTick && !sawWindup,
                    "1.16 Turquoise Blue Overdrive spawned its wave on the click, not after a windup");
            if (cancelDamage) {
                helper.assertTrue(contact.canceled && Math.abs(healthLoss) < EPS && Math.abs(trainingDelta) < EPS
                        && !contact.pointsGiven && contact.emitter == null,
                        "Turquoise refusal oracle: health, training or hit sparks changed");
            }
            else {
                helper.assertTrue(!contact.canceled && healthLoss > 0 && trainingDelta > 0 && contact.pointsGiven && blueEmitter,
                        "Turquoise accepted-result oracle failed");
                helper.assertTrue(wave.getUUID().equals(contact.attempt.direct) && sourceError < EPS,
                        "Turquoise source oracle: direct entity or position is not the wave");
            }
        }

        private boolean wetBox(AABB box) {
            if (waterVolume == null || box.minX < waterVolume.minX || box.maxX > waterVolume.maxX
                    || box.minY < waterVolume.minY || box.maxY > waterVolume.maxY
                    || box.minZ < waterVolume.minZ || box.maxZ > waterVolume.maxZ) return false;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX + 1.0E-6D, box.minY + 1.0E-6D, box.minZ + 1.0E-6D),
                    BlockPos.containing(box.maxX - 1.0E-6D, box.maxY - 1.0E-6D, box.maxZ - 1.0E-6D))) {
                if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;
            }
            return true;
        }

        private boolean ticking(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            return new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max);
        }

        private double training() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }

        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 90, "watchdog expired before first contact");
                if (!inputPressed && userTicks >= 20 && targetTicks >= 20) press();
                if (contact != null) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                if (wave != null) premise(!wave.isRemoved(), "wave ended before a valid first contact");
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                closeAfterFailure(error);
                throw error;
            }
        }

        private void release() {
            if (inputPressed && !inputReleased) {
                AbilityInput.keyRelease(INPUT_KEY, user);
                inputReleased = true;
            }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Turquoise source premise failed: cancel=" + cancelDamage, error);
            }
        }

        private void premise(boolean valid, String message) { helper.assertTrue(valid, "Turquoise premise: " + message); }
        private void log(String message) { JojoMod.LOGGER.info("TURQUOISE-SOURCE cancel={} {}", cancelDamage, message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            int registered = listeners.size();
            try {
                for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
                listeners.clear();
            }
            finally {
                try { release(); }
                finally {
                    try {
                        for (HamonTurquoiseBlueOverdriveEntity shot : owned.values()) if (!shot.isRemoved()) shot.discard();
                    }
                    finally {
                        try { if (target != null) target.discard(); }
                        finally {
                            try {
                                if (user != null) {
                                    try { user.stopUsingItem(); user.getInventory().clearContent(); }
                                    finally { user.discard(); }
                                }
                            }
                            finally {
                                // Drain while the shell is intact, then restore every captured block.
                                for (BlockPos pos : waterCells) level.setBlockAndUpdate(pos, original.get(pos));
                                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                                boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                                boolean guardEmpty = true;
                                if (guardVerified) {
                                    for (BlockPos pos : BlockPos.betweenClosed(guardMin, guardMax)) guardEmpty &= level.isEmptyBlock(pos);
                                }
                                boolean entitiesGone = owned.entrySet().stream().allMatch(entry -> entry.getValue().isRemoved()
                                        && level.getEntity(entry.getKey()) == null)
                                        && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null)
                                        && (target == null || target.isRemoved() && level.getEntity(target.getUUID()) == null);
                                log("cleanup unregistered=" + registered + " listeners=" + listeners.size() + " owned=" + owned.keySet()
                                        + " restored=" + restored + " cells=" + original.size() + " guardEmpty=" + guardEmpty
                                        + " entitiesGone=" + entitiesGone);
                                if (!listeners.isEmpty() || !restored || !guardEmpty || !entitiesGone) {
                                    throw new IllegalStateException("Turquoise fixture cleanup verification failed");
                                }
                            }
                        }
                    }
                }
            }
        }

        private void closeAfterFailure(Throwable primary) {
            try { close(); }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("Turquoise cleanup failed; original failure retained", cleanup);
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
}
