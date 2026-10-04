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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfEntity;
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
public final class HamonScarfSourceGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final Vec3 FIRST_TIP = sweepTip(1.5D, -54.0D);

    private HamonScarfSourceGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_scarf_source_freeze", timeoutTicks = 100)
    public static void scarfSwingDoesNotRetaliateAgainstItsRangedOwner(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_scarf_source_control", timeoutTicks = 100)
    public static void scarfSwingDamagesVampireAndTrainsWithoutFreeze(GameTestHelper helper) {
        start(helper, false);
    }

    private static void start(GameTestHelper helper, boolean freezing) {
        Fixture fixture = new Fixture(helper, freezing);
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

    private static Vec3 sweepTip(double distance, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
    }

    private record Frame(long time, int age, Vec3 root, Vec3 tip, Vec3 delta, double distance,
            boolean forward, boolean retracting, boolean pointsGiven, double points, float health,
            float expectedDamage, AABB query, List<UUID> candidates, AABB targetBox, Vec3 intersection,
            HitResult.Type collider, HitResult.Type outline, int userTicks, int defenderTicks) {}

    private record Attempt(float amount, UUID direct, UUID cause, boolean hamon, boolean canceled) {}

    private record Shade(BlockPos feet, BlockPos eye, boolean feetSky, boolean eyeSky,
            int feetLight, int eyeLight, boolean ticking) {
        boolean ready() { return !feetSky && !eyeSky && ticking; }
    }

    private record Contact(UUID projectile, Frame before, Vec3 tip, double distance, boolean retracting,
            boolean removed, boolean pointsGiven, double points, float health, int freezeDuration,
            int freezeAmplifier, int impacts, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short FREEZE_KEY = 27;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean freezing;
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<SatiporojaScarfEntity> spawned = new ArrayList<>();
        private final List<LivingIncomingDamageEvent> incoming = new ArrayList<>();
        private Player user;
        private Player defender;
        private HamonData hamon;
        private PlayerPower defenderPower;
        private VampirismData vampire;
        private Ability freezeAbility;
        private EntityActionInstance freezeAction;
        private SatiporojaScarfEntity scarf;
        private Vec3 userPosition;
        private Vec3 defenderPosition;
        private Frame before;
        private Contact contact;
        private RuntimeException observerFailure;
        private double initialPoints;
        private int userTicks;
        private int defenderTicks;
        private int userTicksAtGrant;
        private int defenderTicksAtGrant;
        private int scarfTicks;
        private int freezeStartedTicks;
        private int impacts;
        private boolean freezePressed;
        private boolean powersGranted;
        private boolean usingItem;
        private boolean used;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean freezing) {
            this.helper = helper;
            level = helper.getLevel();
            this.freezing = freezing;
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL && !level.dimensionType().ultraWarm(),
                    "Scarf source needs non-Peaceful, non-ultrawarm conditions");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 4, y - 1, z + 2);
            BlockPos max = new BlockPos(x + 13, y + 6, z + 15);
            AABB room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, room).isEmpty(), "Scarf source room is unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Scarf source room is obstructed");
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 4, y + 5, z + 2), new BlockPos(x + 12, y + 5, z + 13))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "Could not place owned scarf shade");
            }
            userPosition = new Vec3(x + 6.5D, y, z + 5.5D);
            defenderPosition = userPosition.add(sweepTip(2.7D, -54.0D));
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            defender = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            defender.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            defender.moveTo(defenderPosition.x, defenderPosition.y, defenderPosition.z, 0, 0);
            defender.setYHeadRot(0);
            defender.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(user) && level.addFreshEntity(defender), "Could not add scarf source players");
            registerObservers();
            log("setup ordinaryUser=" + user.getUUID() + " at=" + userPosition + " ordinaryDefender=" + defender.getUUID()
                    + " at=" + defenderPosition + " roofCells=" + roof.size() + " powersGranted=false"
                    + " survival=true noGravity=true separation=" + user.distanceTo(defender));
        }

        private Shade shade(Player actor) {
            BlockPos feet = actor.blockPosition();
            BlockPos eye = BlockPos.containing(actor.getEyePosition());
            return new Shade(feet, eye, level.canSeeSky(feet), level.canSeeSky(eye),
                    level.getBrightness(LightLayer.SKY, feet), level.getBrightness(LightLayer.SKY, eye),
                    level.isPositionEntityTicking(feet));
        }

        private boolean ownedRoofReady() {
            return roof.size() == 108 && roof.keySet().stream()
                    .allMatch(pos -> level.getBlockState(pos).equals(Blocks.STONE.defaultBlockState()));
        }

        private boolean shadeAndTickingReady() {
            Shade userShade = shade(user);
            Shade defenderShade = shade(defender);
            long roofStates = roof.keySet().stream()
                    .filter(pos -> level.getBlockState(pos).equals(Blocks.STONE.defaultBlockState())).count();
            boolean ready = roof.size() == 108 && roofStates == 108 && userShade.ready() && defenderShade.ready();
            log("readiness tick=" + helper.getTick() + " ready=" + ready + " roofStates=" + roofStates + "/" + roof.size()
                    + " user=" + userShade + " defender=" + defenderShade + " naturalTicks=" + userTicks + "/" + defenderTicks
                    + " powersGranted=" + powersGranted);
            return ready;
        }

        private void grantPowers() {
            helper.assertTrue(!powersGranted && ownedRoofReady() && shade(user).ready() && shade(defender).ready(),
                    "Owned shade and ticking must precede vampire grant");
            requireActors();
            helper.assertTrue(!user.isOnFire() && !defender.isOnFire(), "Ordinary scarf source actors burned before power grant");
            PlayerPower userPower = PowerClass.PLAYER_POWER.attachGet(user);
            userPower.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.SATIPOROJA_SCARF.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
            defenderPower = PowerClass.PLAYER_POWER.attachGet(defender);
            defenderPower.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            vampire = PlayerPower.getPowerData(defender, ModPlayerPowers.VAMPIRISM).orElseThrow();
            vampire.setVampireFullPower(true, defender);
            VampirismState.get(defender).blood().setCurrent(100);
            vampire.setBloodLevel(100);
            user.setHealth(user.getMaxHealth());
            defender.setHealth(defender.getMaxHealth());
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(defender).entityAim.setTarget(ActionTarget.EMPTY);
            freezeAbility = defenderPower.getAbility("vampirism_freeze");
            helper.assertTrue(freezeAbility instanceof VampirismFreezeAbility
                            && freezeAbility.abilityType == VampirismPowerType.VAMPIRE_FREEZE.get()
                            && hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())
                            && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                            && !hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())
                            && HamonAbilityHelpers.configHamonDamageMultiplier() > 0
                            && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "Scarf source power or configuration is invalid");
            userTicksAtGrant = userTicks;
            defenderTicksAtGrant = defenderTicks;
            powersGranted = true;
            log("powers-granted tick=" + helper.getTick() + " shadeBeforeGrant=true user=" + shade(user)
                    + " defender=" + shade(defender) + " naturalTicksAtGrant=" + userTicksAtGrant + "/" + defenderTicksAtGrant);
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level
                        || !(joinEvent.getEntity() instanceof SatiporojaScarfEntity created) || created.getOwner() != user) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(usingItem && scarf == null && spawned.size() == 1 && !joinEvent.isCanceled(),
                            "Unexpected scarf source spawn");
                    scarf = created;
                    CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
                    helper.assertTrue(scarf.getType() == ModEntityTypes.SATIPOROJA_SCARF.get() && scarf.ticksLifespan() == 10
                                    && scarf.getSpeedFactor() == 1.0D && !nbt.getBoolean("LeftArm") && !nbt.getBoolean("PointsGiven"),
                            "Scarf source registry or startup state is wrong");
                    log("spawn scarf=" + scarf.getUUID() + " root=" + scarf.getOriginPoint(1)
                            + " tip=" + scarf.position() + " width=" + scarf.getBbWidth() + " life=" + scarf.ticksLifespan());
                });
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() != scarf || contact != null) return;
                helper.assertTrue(!preEvent.isCanceled() && before == null && scarf.getOwner() == user
                                && level.isPositionEntityTicking(scarf.blockPosition()), "Scarf source natural tick is invalid");
                requireActors();
                before = frame();
                impacts = 0;
                incoming.clear();
                log("pre " + before);
                helper.assertTrue(near(before.root, user.getEyePosition(1).add(0, -0.3D, 0)), "Scarf root offset changed");
                if (before.age == 2) validateFirstRay(before);
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                if (impactEvent.getProjectile() != scarf || contact != null) return;
                helper.assertTrue(before != null && before.age == 2 && impacts == 0 && !impactEvent.isCanceled()
                                && impactEvent.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == defender,
                        "Scarf source has an unexpected or early impact");
                validateFirstRay(before);
                requireEligibilityAtContact();
                impacts++;
                log("impact scarf=" + scarf.getUUID() + " target=" + defender.getUUID() + " point=" + impactEvent.getRayTraceResult().getLocation()
                        + " phase=" + (freezeAction == null ? "none" : freezeAction.getPhase())
                        + " blood=" + VampirismState.get(defender).blood().current());
            });
            Consumer<LivingIncomingDamageEvent> damage = damageEvent -> observe(() -> {
                if (damageEvent.getEntity() != defender || contact != null) return;
                helper.assertTrue(before != null && impacts == 1, "Defender damage escaped its owned scarf contact");
                // Observe the real Freeze handler, including its cancellation; never rewrite damage here.
                incoming.add(damageEvent);
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == user) userTicks++;
                if (postEvent.getEntity() == defender) defenderTicks++;
                if (postEvent.getEntity() != scarf || contact != null) return;
                helper.assertTrue(before != null && before.time == level.getGameTime() && before.age == scarf.tickCount,
                        "Scarf source lacks its natural Pre/Post pair");
                scarfTicks++;
                CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
                MobEffectInstance freeze = user.getEffect(ModStatusEffects.FREEZE);
                Contact sample = new Contact(scarf.getUUID(), before, scarf.position(), nbt.getDouble("Distance"),
                        nbt.getBoolean("IsRetracting"), scarf.isRemoved(), nbt.getBoolean("PointsGiven"), points(), defender.getHealth(),
                        freeze == null ? 0 : freeze.getDuration(), freeze == null ? -1 : freeze.getAmplifier(), impacts,
                        incoming.stream().map(Fixture::attempt).toList());
                log("post " + sample);
                if (before.age == 1) {
                    helper.assertTrue(impacts == 0 && incoming.isEmpty() && before.delta.lengthSqr() < EPSILON
                                    && near(before.tip, before.root) && sample.tip.distanceTo(before.root.add(FIRST_TIP)) < 2.0E-4D
                                    && Math.abs(sample.distance - 1.5D) < EPSILON && !sample.retracting
                                    && sample.freezeDuration == 0 && sample.health == before.health
                                    && !sample.pointsGiven && Math.abs(sample.points - initialPoints) < EPSILON,
                            "Scarf source first natural sweep step changed");
                }
                if (before.age == 2) {
                    contact = sample;
                    // Keep the completed first-contact sample isolated from later sweep hits.
                    if (!scarf.isRemoved()) scarf.discard();
                }
                before = null;
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

        private Frame frame() {
            Vec3 root = scarf.getOriginPoint(1);
            Vec3 tip = scarf.position();
            Vec3 delta = scarf.getDeltaMovement();
            Vec3 end = tip.add(delta);
            AABB query = scarf.getBoundingBox().expandTowards(root.subtract(end)).inflate(1);
            List<UUID> candidates = level.getEntities(scarf, query, entity -> entity != user).stream().map(Entity::getUUID).toList();
            AABB box = defender.getBoundingBox().inflate(defender.getPickRadius() + scarf.getBbWidth() / 2.0D);
            Vec3 intersection = box.contains(root) ? root : box.clip(root, end).orElse(null);
            CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
            float expectedDamage = HamonAbilityHelpers.hamonDamageAmount(defender, 0.6F)
                    * (hamon.getHamonDamageMultiplier() * HamonAbilityHelpers.configHamonDamageMultiplier());
            return new Frame(level.getGameTime(), scarf.tickCount, root, tip, delta, nbt.getDouble("Distance"),
                    nbt.getBoolean("IsMovingForward"), nbt.getBoolean("IsRetracting"), nbt.getBoolean("PointsGiven"), points(),
                    defender.getHealth(), expectedDamage, query, candidates, box, intersection,
                    level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, scarf)).getType(),
                    level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, scarf)).getType(),
                    userTicks, defenderTicks);
        }

        private void validateFirstRay(Frame frame) {
            helper.assertTrue(frame.age == 2 && frame.forward && !frame.retracting && !frame.pointsGiven
                            && Math.abs(frame.distance - 1.5D) < EPSILON && Math.abs(frame.points - initialPoints) < EPSILON
                            && frame.tip.add(frame.delta).distanceTo(frame.root.add(FIRST_TIP.scale(2))) < 4.0E-4D,
                    "Scarf source missed its first nonzero sweep ray");
            helper.assertTrue(frame.collider == HitResult.Type.MISS && frame.outline == HitResult.Type.MISS
                            && frame.candidates.equals(List.of(defender.getUUID())) && frame.intersection != null
                            && frame.expectedDamage > 0 && frame.health > frame.expectedDamage,
                    "Scarf source needs an unobstructed real target ray and query");
        }

        private void requireActors() {
            helper.assertTrue(user.isAlive() && defender.isAlive() && near(user.position(), userPosition)
                            && near(defender.position(), defenderPosition) && user.getYRot() == 0 && user.getXRot() == 0
                            && defender.getYRot() == 0 && defender.getXRot() == 0
                            && level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(defender.blockPosition()),
                    "Scarf source actors moved or stopped naturally ticking");
        }

        private void requireEligibilityAtContact() {
            requireActors();
            helper.assertTrue(!user.isCreative() && !defender.isCreative() && !defender.isInvulnerable()
                            && !defender.getAbilities().invulnerable && !user.isOnFire() && !defender.isOnFire()
                            && !DamageUtil.isImmuneToCold(user) && !user.hasEffect(ModStatusEffects.FREEZE)
                            && user.canHarmPlayer(defender) && !level.canSeeSky(user.blockPosition())
                            && !level.canSeeSky(defender.blockPosition()) && vampire.isVampireAtFullPower()
                            && ownedRoofReady() && shade(user).ready() && shade(defender).ready()
                            && vampire.getCuringStage(defender) <= 1 && defender.getMainHandItem().isEmpty()
                            && defender.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && !EntityHamonChargeState.get(defender).hasHamonCharge()
                            && HamonAbilityHelpers.hamonDamageAmount(defender, 0.6F) > 0
                            && LivingComponentAction.getAim(defender).getTarget().getType() == ActionTarget.TargetType.EMPTY,
                    "Scarf source target or retaliation eligibility changed");
            EntityActionInstance current = LivingComponentAction.getCurEntityAction(defender);
            if (freezing) helper.assertTrue(current == freezeAction && current != null && current.getPhase() == ActionPhase.PERFORM
                            && freezeAbility.checkMainModLogicConditions(defenderPower).isPositive()
                            && freezeAbility.checkSpecificConditions(defenderPower).isPositive()
                            && VampirismState.get(defender).blood().current() >= 0.45F,
                    "Defender lacks an eligible registered Freeze HOLD");
            else helper.assertTrue(current == null, "Scarf Freeze-OFF control acquired an action");
        }

        private void pressFreeze() {
            AvailableAbilities available = new AvailableAbilities();
            available.update(defenderPower, defenderPower.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(freezeAbility), defender, InputMethod.HOLD),
                    "Registered Freeze HOLD was not admitted");
            var held = AbilityInput.keyPress(FREEZE_KEY, freezeAbility, defender, null, InputMethod.HOLD, 0,
                    BufferingState.clickOnly(), freezeAbility.getAbilityId());
            helper.assertTrue(held != null && held.action instanceof VampirismFreezeAbility.FreezeInstance action
                            && action.ability == freezeAbility && action == LivingComponentAction.getCurEntityAction(defender),
                    "Registered Freeze HOLD did not install its actual action");
            freezeAction = (EntityActionInstance) held.action;
            freezePressed = true;
            freezeStartedTicks = defenderTicks;
            log("freeze-input ability=" + freezeAbility.getAbilityId() + " defenderTicks=" + defenderTicks
                    + " phase=" + freezeAction.getPhase());
        }

        private void useItem() {
            requireActors();
            requireEligibilityAtContact();
            helper.assertTrue(user.getMainArm() == HumanoidArm.RIGHT && !user.getAbilities().instabuild
                            && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && user.getMainHandItem().is(ModItems.SATIPOROJA_SCARF.get()) && user.getMainHandItem().getCount() == 1
                            && !user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()) && hamon.getEnergy() >= 600,
                    "Scarf item use lacks Survival/right-arm/resource prerequisites");
            initialPoints = points();
            float energyBefore = hamon.getEnergy();
            usingItem = true;
            try {
                var result = user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.getResult().consumesAction() && scarf != null && spawned.size() == 1
                                && Math.abs(energyBefore - hamon.getEnergy() - 600) < 0.001F
                                && user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()),
                        "Registered scarf use did not pay, spawn or cool down");
            }
            finally { usingItem = false; }
            used = true;
            log("item-use energy=" + energyBefore + "->" + hamon.getEnergy() + " points=" + initialPoints
                    + " naturalActorTicks=" + userTicks + "/" + defenderTicks
                    + " powerWarmTicks=" + (userTicks - userTicksAtGrant) + "/" + (defenderTicks - defenderTicksAtGrant)
                    + " damageMultiplier=" + hamon.getHamonDamageMultiplier());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Scarf source watchdog expired");
                if (!powersGranted) {
                    if (shadeAndTickingReady()) grantPowers();
                    helper.runAfterDelay(1, this::poll);
                    return;
                }
                if (contact != null) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                requireActors();
                if (!used && userTicks - userTicksAtGrant >= 2 && defenderTicks - defenderTicksAtGrant >= 2) {
                    if (freezing && !freezePressed) pressFreeze();
                    if (!freezing || defenderTicks > freezeStartedTicks) useItem();
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            Contact hit = contact;
            log("result " + hit);
            helper.assertTrue(spawned.size() == 1 && scarfTicks == 2 && hit.before.age == 2 && hit.impacts == 1
                            && hit.attempts.size() == 1 && !hit.retracting && !hit.removed && hit.distance > hit.before.distance,
                    "Scarf source lacks one genuine first-contact sample");
            Attempt damage = hit.attempts.get(0);
            helper.assertTrue(damage.hamon && damage.amount > 0 && user.getUUID().equals(damage.cause)
                            && Math.abs(damage.amount - hit.before.expectedDamage) < 1.0E-4F,
                    "Scarf Hamon cause or damage scaling changed");
            double training = hit.points - hit.before.points;
            if (damage.canceled) helper.assertTrue(hit.health == hit.before.health && Math.abs(training) < EPSILON && !hit.pointsGiven,
                    "Refused scarf damage changed health or awarded training");
            else {
                double award = 600.0D * JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() / 750.0D;
                helper.assertTrue(hit.health < hit.before.health && hit.pointsGiven && Math.abs(training - award) < 1.0E-4D,
                        "Accepted scarf damage lost its one successful-hit training award");
            }
            helper.assertTrue(!damage.canceled && hit.health < hit.before.health && hit.freezeDuration == 0,
                    "Scarf Hamon was countered as living melee");
            // OFF remains a behavioral control against the original owner-as-direct producer.
            if (freezing) helper.assertTrue(hit.projectile.equals(damage.direct), "Scarf Hamon direct source is not its projectile");
        }

        private static Attempt attempt(LivingIncomingDamageEvent event) {
            return new Attempt(event.getOriginalAmount(), id(event.getSource().getDirectEntity()), id(event.getSource().getEntity()),
                    event.getSource().is(ModDamageTypes.HAMON), event.isCanceled());
        }

        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
        private static boolean near(Vec3 first, Vec3 second) { return first.distanceToSqr(second) < EPSILON * EPSILON; }

        private double points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Hamon scarf source observer failure: freeze=" + freezing, error);
            }
        }

        private void log(String message) { JojoMod.LOGGER.info("HAMON-SCARF-SOURCE freeze={} {}", freezing, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            before = null;
            try { if (defender != null) AbilityInput.keyRelease(FREEZE_KEY, defender); }
            finally {
                try {
                    if (user != null) user.stopUsingItem();
                    for (SatiporojaScarfEntity created : spawned) if (!created.isRemoved()) created.discard();
                    if (defender != null) defender.discard();
                    if (user != null) user.discard();
                }
                finally {
                    for (var entry : roof.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = roof.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                    log("cleanup listeners=0 scarves=" + spawned.size() + " roofRestored=" + restored + " roofCells=" + roof.size());
                    if (!restored) throw new IllegalStateException("Scarf source roof was not exactly restored");
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
