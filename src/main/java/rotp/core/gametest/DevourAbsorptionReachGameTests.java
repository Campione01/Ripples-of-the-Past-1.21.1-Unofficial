package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.impl.powers.zombie.ZombiePowerType;
import rotp.core.impl.powers.zombie.abilities.ZombieDevourAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 ZombieDevour and PillarmanAbsorption have getMaxRangeSqEntityTarget() == 4 under Action.checkRangeAndTarget:
 * eye to target box surface less half the user's width, the squared range quartered without a line of sight.
 * Neither drain knocked its victim back: bloodDrain had knockback factor 0 (DamageUtil.knockbackReduction) and
 * PillarmanAbsorption.absorb dealt its damage without an attacker.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DevourAbsorptionReachGameTests {
    private static final String BATCH = "devour_absorption_reach";

    private DevourAbsorptionReachGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void devourReachesVisibleCowAtDonorSurfaceRange(GameTestHelper helper) {
        run(helper, Kind.DEVOUR, Scene.SURFACE);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void devourStillReachesNearbyCow(GameTestHelper helper) {
        run(helper, Kind.DEVOUR, Scene.NEAR);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void devourDoesNotReachVisibleCowBeyondDonorSurfaceRange(GameTestHelper helper) {
        run(helper, Kind.DEVOUR, Scene.OUTSIDE);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void devourDoesNotReachOccludedCowBeyondQuarteredRange(GameTestHelper helper) {
        run(helper, Kind.DEVOUR, Scene.OCCLUDED);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void devourStillReachesOccludedCowWithinQuarteredRange(GameTestHelper helper) {
        run(helper, Kind.DEVOUR, Scene.OCCLUDED_NEAR);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void absorptionReachesVisibleCowAtDonorSurfaceRange(GameTestHelper helper) {
        run(helper, Kind.ABSORPTION, Scene.SURFACE);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void absorptionStillReachesNearbyCow(GameTestHelper helper) {
        run(helper, Kind.ABSORPTION, Scene.NEAR);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void absorptionDoesNotReachVisibleCowBeyondDonorSurfaceRange(GameTestHelper helper) {
        run(helper, Kind.ABSORPTION, Scene.OUTSIDE);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void absorptionDoesNotReachOccludedCowBeyondQuarteredRange(GameTestHelper helper) {
        run(helper, Kind.ABSORPTION, Scene.OCCLUDED);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void absorptionStillReachesOccludedCowWithinQuarteredRange(GameTestHelper helper) {
        run(helper, Kind.ABSORPTION, Scene.OCCLUDED_NEAR);
    }

    private enum Kind {
        DEVOUR("zombie_devour", ModDamageTypes.BLOOD_DRAIN),
        ABSORPTION("pillarman_absorption", ModDamageTypes.PILLAR_MAN_ABSORPTION);

        final String abilityName;
        final ResourceKey<DamageType> damageType;

        Kind(String abilityName, ResourceKey<DamageType> damageType) {
            this.abilityName = abilityName;
            this.damageType = damageType;
        }
    }

    // Centre separations from a 0.6 wide user to a 0.9 wide cow.
    private enum Scene {
        SURFACE(2.6D, false, true), NEAR(1.5D, false, true), OUTSIDE(2.9D, false, false),
        OCCLUDED(1.9D, true, false), OCCLUDED_NEAR(1.6D, true, true);

        final double separation;
        final boolean occluded;
        final boolean reached;

        Scene(double separation, boolean occluded, boolean reached) {
            this.separation = separation;
            this.occluded = occluded;
            this.reached = reached;
        }
    }

    private static void run(GameTestHelper helper, Kind kind, Scene scene) {
        Fixture fixture = new Fixture(helper, kind, scene);
        try {
            fixture.setUp();
            fixture.exercise();
        }
        finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static final class Fixture {
        private static final short KEY = 71;
        private static final double NEAR_SEPARATION = 1.5D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Kind kind;
        private final Scene scene;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<String> knockbacks = new ArrayList<>();
        private Consumer<LivingKnockBackEvent> knockbackListener;
        private Vec3 origin;
        private Player user;
        private Cow target;
        private PlayerPower power;
        private Ability ability;
        private LivingComponentAction component;
        private HeldInputEntry held;

        Fixture(GameTestHelper helper, Kind kind, Scene scene) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.kind = kind;
            this.scene = scene;
        }

        private void premise(boolean condition, String message) {
            helper.assertTrue(condition, "REACH-PREMISE " + kind + " " + scene + ": " + message);
        }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL, "requires the Normal difficulty GameTest world");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
            AABB space = new AABB(origin.x - 2, origin.y - 1, origin.z - 2, origin.x + 2, origin.y + 4, origin.z + 6);
            premise(space.maxY < level.getMaxBuildHeight(), "scene exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                premise(level.isEmptyBlock(pos), "scene is not empty at " + pos);
            }
            premise(level.getEntities((Entity) null, space).isEmpty(), "scene contains a foreign entity");

            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            premise(level.addFreshEntity(user) && user.getMainHandItem().isEmpty()
                            && user.getBbWidth() == 0.6F && user.getBbHeight() == 1.8F && user.getEyeHeight() == 1.62F,
                    "user is not an empty-handed standing player");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            if (kind == Kind.DEVOUR) {
                power.setPowerType(ModPlayerPowers.ZOMBIE.get());
                ZombieData data = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElseThrow();
                premise(!data.isDisguiseEnabled(), "zombie disguise is on");
                ability = power.getAbility(kind.abilityName);
                premise(ability instanceof ZombieDevourAbility && ability.abilityType == ZombiePowerType.ZOMBIE_DEVOUR.get(),
                        "registered zombie_devour is missing");
            }
            else {
                power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
                PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
                data.setEvolutionStage(2, user);
                data.setMode(PillarmanMode.NONE, user);
                data.setEnergy(user, 100.0F);
                premise(!data.isStoneFormEnabled(), "Pillar Man stone form is on");
                ability = power.getAbility(kind.abilityName);
                premise(ability instanceof PillarmanAbsorptionAbility
                                && ability.abilityType == PillarmanPowerType.PILLAR_MAN_ABSORPTION.get(),
                        "registered pillarman_absorption is missing");
            }
            component = LivingComponentAction.getComponent(user);

            target = EntityType.COW.create(level);
            premise(target != null, "could not create the cow");
            target.setNoGravity(true);
            target.setNoAi(true);
            target.setPos(origin.x, origin.y, origin.z + NEAR_SEPARATION);
            premise(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable()
                            && target.getBbWidth() == 0.9F && target.getHealth() == target.getMaxHealth(),
                    "cow is not a fresh vulnerable adult");
            knockbackListener = event -> {
                if (event.getEntity() == target && !event.isCanceled() && event.getStrength() > 0.0F) {
                    knockbacks.add("[strength=" + event.getOriginalStrength() + "->" + event.getStrength() + " ratio="
                            + event.getRatioX() + "," + event.getRatioZ() + "]");
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockbackListener);
        }

        private void exercise() {
            // The hold starts on a near visible cow; the scene's geometry is applied before its first tick.
            component.entityAim.setTarget(new ActionTarget(target));
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "registered HOLD on a near visible cow was not admitted");
            held = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            EntityActionInstance action = component.getAction();
            premise(held != null && action != null && held.action == action && action.getPhase() == ActionPhase.PERFORM
                            && target.getHealth() == target.getMaxHealth(),
                    "registered HOLD did not install an undamaging PERFORM hold");

            target.setPos(origin.x, origin.y, origin.z + scene.separation);
            if (scene.occluded) {
                BlockPos wall = BlockPos.containing(origin).south();
                for (BlockPos pos : new BlockPos[] { wall, wall.above() }) {
                    blocks.put(pos.immutable(), level.getBlockState(pos));
                    premise(level.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState(), 3), "could not place the pane");
                    AABB pane = level.getBlockState(pos).getCollisionShape(level, pos).bounds().move(pos);
                    premise(!pane.intersects(user.getBoundingBox()) && !pane.intersects(target.getBoundingBox()),
                            "pane intersects a fixture entity");
                }
            }

            // Independent 1.16 JojoModUtil.getDistance oracle.
            Vec3 eye = user.getEyePosition(1.0F);
            AABB box = target.getBoundingBox();
            double eyeFraction = user.getEyeHeight() / user.getBbHeight();
            Vec3 aimPoint = new Vec3(Mth.lerp(0.5D, box.minX, box.maxX), Mth.lerp(eyeFraction, box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
            double halfWidth = user.getBbWidth() / 2.0F;
            double surface = box.contains(eye) ? 0.0D
                    : box.clip(eye, aimPoint).map(hit -> eye.distanceTo(hit) - halfWidth).orElse(-1.0D);
            double centre = user.position().distanceTo(target.position());
            boolean visible = user.hasLineOfSight(target);
            double donorRange = visible ? 2.0D : 1.0D;
            String geometry = "centreDistance=" + centre + " donorSurfaceDistance=" + surface + " visible=" + visible;
            premise(visible == !scene.occluded && surface >= 0.0D && (surface <= donorRange) == scene.reached
                            && Math.abs(centre - scene.separation) < 1.0E-6D
                            && component.entityAim.getTarget().getEntity() == target,
                    "scene does not separate the donor reach result: " + geometry);
            if (scene.reached) {
                premise(surface + halfWidth > donorRange || scene == Scene.NEAR,
                        "reached scene does not depend on the half width: " + geometry);
            }
            if (scene == Scene.SURFACE) premise(centre > 2.0D, "surface scene is inside the centre-distance rule");
            if (scene == Scene.OCCLUDED) premise(centre < 2.0D && surface < 2.0D, "occluded scene is outside the visible range");

            boolean pressGate = ability.checkConditions(power).isPositive();
            float healthBefore = target.getHealth();
            component.tick();
            float healthAfter = target.getHealth();
            boolean drained = healthAfter < healthBefore;
            JojoMod.LOGGER.info("DEVOUR-ABSORPTION-REACH {} {} pressGate={} drained={} health={}->{} {}",
                    kind, scene, pressGate, drained, healthBefore, healthAfter, geometry);
            helper.assertTrue(pressGate == scene.reached && drained == scene.reached,
                    kind + " " + scene + " reach differs from 1.16: pressGate=" + pressGate + " drained=" + drained
                            + " expected=" + scene.reached + " " + geometry);
            if (drained) {
                DamageSource source = target.getLastDamageSource();
                helper.assertTrue(Math.abs(healthBefore - healthAfter - 2.0F) < 1.0E-5F && source != null
                                && source.is(kind.damageType) && source.getEntity() == user,
                        kind + " " + scene + " drained with the wrong hit: health=" + healthBefore + "->" + healthAfter
                                + " source=" + source);
                Vec3 speed = target.getDeltaMovement();
                helper.assertTrue(knockbacks.isEmpty() && speed.x == 0.0D && speed.z == 0.0D,
                        kind + " " + scene + " pushed its victim, 1.16 drained without knockback: knockback=" + knockbacks
                                + " speed=" + speed);
            }
            EntityActionInstance after = component.getAction();
            helper.assertTrue(after == action && !after.isOver() && after.getPhase() == ActionPhase.PERFORM
                            && AbilityInput.isHeldByKey(user, action),
                    kind + " " + scene + " hold ended instead of draining or pausing");
        }

        private void close() {
            if (knockbackListener != null) NeoForge.EVENT_BUS.unregister(knockbackListener);
            try {
                if (user != null) {
                    if (held != null) AbilityInput.keyRelease(KEY, user);
                    LivingComponentAction owned = LivingComponentAction.getComponent(user);
                    owned.entityAim.setTarget(ActionTarget.EMPTY);
                    owned.setAction(null, user, SyncType.NO_SYNC);
                }
            }
            finally {
                try {
                    if (target != null) target.discard();
                    if (user != null) user.discard();
                }
                finally {
                    for (var entry : blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
                    blocks.clear();
                }
            }
        }
    }
}
