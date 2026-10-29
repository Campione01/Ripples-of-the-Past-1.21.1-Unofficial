package rotp.addons.hermitpurple.ability;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitGrappleGameTests {
    private static final double EPS = 1.0E-4D;
    private static final int ATTACHED_TICKS = 10;
    private static final int SPAWN_TICKS = 2;
    private static final int BOUND_TICKS = 3;

    private HermitGrappleGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void blockGrappleKeepsItsFirstAnchorAndCatchesOnce(GameTestHelper helper) {
        begin(helper, Case.FLOOR_ANCHOR);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void barrierBlockRemovesTheGrapple(GameTestHelper helper) {
        begin(helper, Case.BARRIER);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void blockGrapplePassesThroughAMobInItsPath(GameTestHelper helper) {
        begin(helper, Case.ENTITY_PASS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void grappleFliesFromBelowTheEyes(GameTestHelper helper) {
        begin(helper, Case.GRAPPLE_ORIGIN);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void grabVineFliesFromBelowTheEyes(GameTestHelper helper) {
        begin(helper, Case.GRAB_ORIGIN);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void blockGrappleBreaksGrassAndCatchesTheBlockBehindIt(GameTestHelper helper) {
        begin(helper, Case.GRASS_CATCH);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void attachedGrappleSurvivesGrassOnItsLine(GameTestHelper helper) {
        begin(helper, Case.ATTACHED_GRASS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void entityGrappleFliesOnThroughGrass(GameTestHelper helper) {
        begin(helper, Case.ENTITY_GRAPPLE_GRASS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void grabVineFliesOnThroughGrass(GameTestHelper helper) {
        begin(helper, Case.GRAB_GRASS);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void entityGrappleIsRemovedByABlockItCannotBreak(GameTestHelper helper) {
        begin(helper, Case.ENTITY_GRAPPLE_BLOCK);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void entityGrappleBindsWithoutImmobilize(GameTestHelper helper) {
        begin(helper, Case.ENTITY_BIND);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void grabVineStillImmobilizesItsCatch(GameTestHelper helper) {
        begin(helper, Case.GRAB_BIND);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void entityGrappleDragsAMobWithAiAndLetsGoWithinTwoBlocks(GameTestHelper helper) {
        begin(helper, Case.ENTITY_DRAG);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void entityGrappleLeavesAPlayerTargetsPullAndStopToItsClient(GameTestHelper helper) {
        begin(helper, Case.ENTITY_DRAG_PLAYER);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200)
    public static void blockGrapplePullsAPlayerUserWithoutAMotionPacket(GameTestHelper helper) {
        begin(helper, Case.BLOCK_PULL_PLAYER);
    }

    private enum Case {
        FLOOR_ANCHOR("floor_anchor", 82.0D, HermitPurpleAddon.HP_VINE_NAME, 10.0F),
        BARRIER("barrier", 92.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F),
        ENTITY_PASS("entity_pass", 102.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F),
        GRAPPLE_ORIGIN("grapple_origin", 112.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F),
        GRAB_ORIGIN("grab_origin", 122.0D, HermitPurpleAddon.HP_GRAB_NAME, 0.0F),
        GRASS_CATCH("grass_catch", 212.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F),
        ATTACHED_GRASS("attached_grass", 222.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F),
        ENTITY_GRAPPLE_GRASS("entity_grapple_grass", 232.0D, HermitPurpleAddon.HP_VINE_ENTITY_NAME, 0.0F),
        GRAB_GRASS("grab_grass", 242.0D, HermitPurpleAddon.HP_GRAB_NAME, 0.0F),
        ENTITY_GRAPPLE_BLOCK("entity_grapple_block", 252.0D, HermitPurpleAddon.HP_VINE_ENTITY_NAME, 0.0F),
        ENTITY_BIND("entity_bind", 282.0D, HermitPurpleAddon.HP_VINE_ENTITY_NAME, 0.0F),
        GRAB_BIND("grab_bind", 292.0D, HermitPurpleAddon.HP_GRAB_NAME, 0.0F),
        ENTITY_DRAG("entity_drag", 262.0D, HermitPurpleAddon.HP_VINE_ENTITY_NAME, 0.0F),
        ENTITY_DRAG_PLAYER("entity_drag_player", 272.0D, HermitPurpleAddon.HP_VINE_ENTITY_NAME, 0.0F),
        BLOCK_PULL_PLAYER("block_pull_player", 302.0D, HermitPurpleAddon.HP_VINE_NAME, 0.0F);

        final String label;
        final double height;
        final String ability;
        final float pitch;

        Case(String label, double height, String ability, float pitch) {
            this.label = label;
            this.height = height;
            this.ability = ability;
            this.pitch = pitch;
        }

        boolean breaksGrass() {
            return this == GRASS_CATCH || this == ATTACHED_GRASS || this == ENTITY_GRAPPLE_GRASS || this == GRAB_GRASS;
        }
    }

    private static void begin(GameTestHelper helper, Case kind) {
        Fixture fixture = new Fixture(helper, kind);
        helper.testInfo.addListener(fixture);
        fixture.guarded(fixture::prepare);
    }

    private record Sample(int age, Vec3 tip, boolean attached, Vec3 userMotion, boolean userMarked) {}

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Case kind;
        final List<Sample> samples = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> joinListener = this::joined;
        final Consumer<EntityTickEvent.Post> tickListener = this::ticked;
        final Consumer<EntityLeaveLevelEvent> leaveListener = this::left;
        final Consumer<PlayLevelSoundEvent.AtPosition> soundListener = this::heard;
        FakePlayer player;
        StandPower power;
        HermitPurpleStandEntity stand;
        HermitGrappleVineEntity vine;
        IronGolem golem;
        float golemHealth;
        FakePlayer victim;
        final List<Double> dragDistances = new ArrayList<>();
        final List<String> pullErrors = new ArrayList<>();
        double golemDistanceAtRemoval = -1.0D;
        Vec3 golemMotionAtRemoval;
        Boolean victimMarkedAtRemoval;
        Vec3 victimMotionAtRemoval;
        int victimBoundTicks;
        boolean victimMoved;
        AABB corridor;
        BlockPos column;
        BlockPos obstacle;
        BlockPos grass;
        Boolean breakRuleBefore;
        int brokenAge = -1;
        int plantedAge = -1;
        Vec3 spawnTip;
        Vec3 spawnEye;
        int vinesJoined;
        long joinedAt = -1;
        int attachedTicks;
        int immobilizedTicks;
        int removalAge = -1;
        int catchSounds;
        boolean watching;
        boolean closed;
        long firedAt;

        Fixture(GameTestHelper helper, Case kind) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.kind = kind;
        }

        void prepare() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int chunkX = origin.getX() & ~15;
            int chunkZ = origin.getZ() & ~15;
            int y = origin.getY() + (int) kind.height;
            corridor = new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 8, chunkZ + 15);
            helper.assertTrue(level.getBlockStates(corridor).allMatch(state ->
                            state.isAir() && state.getFluidState().isEmpty()),
                    "Grapple fixture needs an existing empty, dry corridor");
            helper.assertTrue(level.getEntities((Entity) null, corridor,
                            entity -> !entity.isSpectator()).isEmpty(),
                    "Grapple fixture corridor contains another actor");
            column = new BlockPos(chunkX + 7, y, chunkZ + 3);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPGrapple" + kind.ordinal()));
            player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            player.setXRot(kind.pitch);
            level.addNewPlayer(player);
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition()),
                    "Grapple owner is outside an entity-ticking chunk");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "Normal summon produced no Hermit controller");
            later(stand.summonLockTicks + 2, this::press);
        }

        void press() {
            helper.assertTrue(stand.isAlive() && stand.getUser() == player && stand.getHeldVine() == null,
                    "Grapple fixture lost its controller or already holds a vine");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            if (kind.breaksGrass()) {
                GameRules.BooleanValue rule = level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS);
                breakRuleBefore = rule.get();
                rule.set(true, level.getServer());
            }
            placeObstacles();
            var ability = power.getAbility(kind.ability);
            helper.assertTrue(ability instanceof HermitGrappleAbility
                            && ability.checkConditions(power).isPositive(),
                    "Registered grapple refused the fixture");
            watching = true;
            NeoForge.EVENT_BUS.addListener(joinListener);
            NeoForge.EVENT_BUS.addListener(tickListener);
            NeoForge.EVENT_BUS.addListener(leaveListener);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    PlayLevelSoundEvent.AtPosition.class, soundListener);
            firedAt = level.getGameTime();
            BufferingState buffering = BufferingState.clickOnly();
            ability.onKeyPress(level, player, null, InputMethod.HOLD, 0.0F, buffering);
            helper.assertTrue(buffering.isActionSuccess, "Registered server HOLD was not accepted");
            LogUtils.getLogger().info("R764_HP_GRAPPLE_INPUT case={} world={} player={}",
                    kind.label, firedAt, player.getUUID());
            later(1, this::monitor);
        }

        void placeObstacles() {
            switch (kind) {
                case FLOOR_ANCHOR -> {
                    for (int dz = -1; dz <= 11; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            place(column.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                        }
                    }
                }
                case BARRIER -> {
                    obstacle = column.offset(0, 1, 6);
                    place(obstacle, Blocks.BARRIER.defaultBlockState());
                }
                case ENTITY_PASS -> {
                    obstacle = column.offset(0, 1, 10);
                    place(obstacle, Blocks.STONE.defaultBlockState());
                    spawnGolem(3.0D);
                }
                case ENTITY_BIND, GRAB_BIND -> spawnGolem(6.0D);
                case ENTITY_DRAG -> {
                    for (int dz = -1; dz <= 11; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            place(column.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                        }
                    }
                    spawnGolem(6.0D);
                    golem.setNoAi(false);
                    golem.setNoGravity(false);
                }
                case ENTITY_DRAG_PLAYER -> spawnVictim(4.0D);
                case GRASS_CATCH -> {
                    plantGrass();
                    obstacle = column.offset(0, 1, 8);
                    place(obstacle, Blocks.STONE.defaultBlockState());
                }
                case ATTACHED_GRASS, BLOCK_PULL_PLAYER -> {
                    obstacle = column.offset(0, 1, 8);
                    place(obstacle, Blocks.STONE.defaultBlockState());
                }
                case ENTITY_GRAPPLE_GRASS, GRAB_GRASS -> plantGrass();
                case ENTITY_GRAPPLE_BLOCK -> {
                    obstacle = column.offset(0, 1, 6);
                    place(obstacle, Blocks.STONE.defaultBlockState());
                }
                default -> { }
            }
        }

        void spawnGolem(double ahead) {
            golem = EntityType.IRON_GOLEM.create(level);
            helper.assertTrue(golem != null, "Grapple obstacle mob could not be created");
            golem.moveTo(player.getX(), player.getY(), player.getZ() + ahead, 180.0F, 0.0F);
            golem.setNoAi(true);
            golem.setNoGravity(true);
            golem.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(golem), "Grapple obstacle mob could not be added");
            golemHealth = golem.getHealth();
        }

        void spawnVictim(double ahead) {
            victim = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPVictim" + kind.ordinal()));
            victim.setPos(player.getX(), player.getY(), player.getZ() + ahead);
            victim.setNoGravity(true);
            level.addNewPlayer(victim);
            helper.assertTrue(level.isPositionEntityTicking(victim.blockPosition()),
                    "Grapple target is outside an entity-ticking chunk");
        }

        // Short grass on a dirt block, two blocks ahead at the height the vine leaves the user.
        void plantGrass() {
            BlockPos soil = column.offset(0, 0, 2);
            place(soil, Blocks.DIRT.defaultBlockState());
            grass = soil.above();
            place(grass, Blocks.SHORT_GRASS.defaultBlockState());
            helper.assertTrue(level.getBlockState(grass).getDestroySpeed(level, grass) == 0.0F,
                    "Fixture: short grass is not an instant-break block");
        }

        void noteBrokenGrass() {
            if (grass != null && brokenAge < 0 && vine != null && level.getBlockState(grass).isAir()) {
                brokenAge = vine.tickCount;
                LogUtils.getLogger().info("R764_HP_GRAPPLE_TRACE case={} event=grass_broken age={}", kind.label, brokenAge);
            }
        }

        void place(BlockPos pos, BlockState state) {
            level.setBlockAndUpdate(pos, state);
            placed.add(pos);
            helper.assertTrue(level.getBlockState(pos).is(state.getBlock()), "Grapple obstacle was not placed");
        }

        void joined(EntityJoinLevelEvent event) {
            // a broken plant's drop would fall through the corridors of the cases below this one
            if (watching && !closed && event.getLevel() == level && event.getEntity() instanceof ItemEntity item
                    && corridor.inflate(1.0D).contains(item.position())) {
                event.setCanceled(true);
                return;
            }
            if (!watching || closed || event.getLevel() != level
                    || !(event.getEntity() instanceof HermitGrappleVineEntity joinedVine)
                    || joinedVine.getOwner() != stand) return;
            vinesJoined++;
            if (vine == null) {
                vine = joinedVine;
                spawnTip = joinedVine.position();
                joinedAt = level.getGameTime();
                spawnEye = player.getEyePosition();
                LogUtils.getLogger().info("R764_HP_GRAPPLE_TRACE case={} event=spawn tip={} eye={} delay={}",
                        kind.label, spawnTip, spawnEye, joinedAt - firedAt);
            }
        }

        void ticked(EntityTickEvent.Post event) {
            if (!watching || closed || vine == null || event.getEntity() != vine || vine.isRemoved()) return;
            Sample sample = new Sample(vine.tickCount, vine.position(), vine.isAttachedToBlock(),
                    player.getDeltaMovement(), player.hurtMarked);
            samples.add(sample);
            LivingEntity bound = vine.getEntityAttachedTo();
            if (golem != null && bound == golem) {
                attachedTicks++;
                if (golem.hasEffect(ModStatusEffects.IMMOBILIZE)) immobilizedTicks++;
                if (kind == Case.ENTITY_DRAG) dragDistances.add(horizontalDistance(golem));
            }
            if (victim != null && bound == victim) {
                victimBoundTicks++;
                if (!victimMoved) {
                    Vec3 pull = player.position().subtract(victim.position()).normalize()
                            .scale(HermitPurpleSpec.BIND_PULL_SPEED);
                    if (victim.getDeltaMovement().distanceTo(pull) > EPS || victim.hurtMarked) {
                        pullErrors.add("age=" + sample.age() + " motion=" + victim.getDeltaMovement()
                                + " expected=" + pull + " marked=" + victim.hurtMarked);
                    }
                }
            }
            noteBrokenGrass();
            LogUtils.getLogger().info("R764_HP_GRAPPLE_TRACE case={} event=post_tick age={} tip={} attached={}",
                    kind.label, sample.age(), sample.tip(), sample.attached());
        }

        void left(EntityLeaveLevelEvent event) {
            if (!watching || closed || vine == null || event.getEntity() != vine || removalAge >= 0) return;
            noteBrokenGrass();
            removalAge = vine.tickCount;
            if (kind == Case.ENTITY_DRAG) {
                golemDistanceAtRemoval = horizontalDistance(golem);
                golemMotionAtRemoval = golem.getDeltaMovement();
            }
            if (victim != null) {
                victimMarkedAtRemoval = victim.hurtMarked;
                victimMotionAtRemoval = victim.getDeltaMovement();
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_TRACE case={} event=removed age={}", kind.label, removalAge);
        }

        // DamagingEntity.playSound reaches every listener through this server event.
        void heard(PlayLevelSoundEvent.AtPosition event) {
            if (!watching || closed || event.getLevel() != level || event.getSound() == null
                    || event.getSound().value() != HermitPurpleAddon.HP_GRAPPLE.get()
                    || !corridor.inflate(1.0D).contains(event.getPosition())) return;
            catchSounds++;
        }

        void monitor() {
            // Donor hp_vine, hp_vine_entity and hp_grab are hold-only: the vine leaves on the first PERFORM tick.
            helper.assertTrue(vine != null || level.getGameTime() - firedAt <= SPAWN_TICKS,
                    "No held vine within " + SPAWN_TICKS + " ticks of the press; the donor fires it on the press");
            helper.assertTrue(vinesJoined <= 1, "One HOLD spawned more than one held vine");
            helper.assertTrue(player.isAlive() && !player.isRemoved() && stand.isAlive(),
                    "Owner or controller lifecycle invalidated the grapple fixture");
            boolean done = switch (kind) {
                case FLOOR_ANCHOR -> floorAnchor();
                case BARRIER -> barrier();
                case ENTITY_PASS -> entityPass();
                case GRAPPLE_ORIGIN -> origin(-0.2D, HermitPurpleSpec.GRAPPLE_VINE_SPEED);
                case GRAB_ORIGIN -> origin(-0.25D, HermitPurpleSpec.GRAB_VINE_SPEED);
                case GRASS_CATCH -> grassCatch();
                case ATTACHED_GRASS -> attachedGrass();
                case ENTITY_GRAPPLE_GRASS -> flightThroughGrass(-0.2D, HermitPurpleSpec.GRAPPLE_VINE_SPEED);
                case GRAB_GRASS -> flightThroughGrass(-0.25D, HermitPurpleSpec.GRAB_VINE_SPEED);
                case ENTITY_GRAPPLE_BLOCK -> unbrokenBlockRemovesEntityGrapple();
                case ENTITY_BIND -> boundTarget(false);
                case GRAB_BIND -> boundTarget(true);
                case ENTITY_DRAG -> draggedMob();
                case ENTITY_DRAG_PLAYER -> releasedPlayer();
                case BLOCK_PULL_PLAYER -> pulledPlayerUser();
            };
            if (done) {
                LogUtils.getLogger().info("R764_HP_GRAPPLE_PASS {}", kind.label);
                close();
                helper.succeed();
                return;
            }
            helper.assertTrue(level.getGameTime() - firedAt <= 80,
                    "Held vine produced no verdict within the bounded observation window; samples="
                            + samples.size() + "; removalAge=" + removalAge);
            later(1, this::monitor);
        }

        // Donor HPGrapplingVineEntity.afterBlockHit: the catch sound and attachToBlockPos run only while unattached.
        boolean floorAnchor() {
            int first = firstAttached();
            if (first < 0) {
                helper.assertTrue(removalAge < 0, "Grapple ended at age " + removalAge + " without reaching the floor");
                return false;
            }
            List<Sample> attached = samples.subList(first, samples.size());
            if (attached.size() < ATTACHED_TICKS && removalAge < 0) return false;
            Set<BlockPos> anchors = new LinkedHashSet<>();
            for (Sample sample : attached) {
                if (sample.attached()) anchors.add(BlockPos.containing(sample.tip()));
            }
            BlockPos firstAnchor = anchors.iterator().next();
            helper.assertTrue(level.getBlockState(firstAnchor).is(Blocks.STONE)
                            && firstAnchor.getY() == column.getY() - 1
                            && firstAnchor.getZ() - column.getZ() >= 6,
                    "Grapple did not catch the far floor: " + firstAnchor);
            List<String> errors = new ArrayList<>();
            if (attached.size() < ATTACHED_TICKS) {
                errors.add("attached grapple ended at age " + removalAge + " after " + attached.size() + " ticks");
            }
            if (anchors.size() != 1) {
                errors.add("anchor moved over " + attached.size() + " attached ticks: " + anchors);
            }
            if (catchSounds != 1) {
                errors.add("hp_grap played " + catchSounds + " times for one catch");
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} anchors={} catchSounds={} errors={}",
                    kind.label, anchors, catchSounds, errors);
            helper.assertTrue(errors.isEmpty(), "Attached grapple differs from the donor: "
                    + String.join(" | ", errors));
            return true;
        }

        // Donor HPGrapplingVineEntity.afterBlockHit: a barrier block removes the vine before any attach.
        boolean barrier() {
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.BARRIER), "Barrier obstacle disappeared");
            int first = firstAttached();
            helper.assertTrue(first < 0, "Grapple attached to a barrier block"
                    + (first < 0 ? "" : " at age " + samples.get(first).age()
                            + ", anchor " + BlockPos.containing(samples.get(first).tip())));
            if (removalAge < 0) return false;
            helper.assertTrue(removalAge <= 3, "Grapple flew on past the barrier block until age " + removalAge);
            return true;
        }

        // Donor OwnerBoundProjectileEntity.breakProjectile is empty: a mob in the path does not end the grapple.
        boolean entityPass() {
            int first = firstAttached();
            if (first >= 0) {
                BlockPos anchor = BlockPos.containing(samples.get(first).tip());
                helper.assertTrue(golem.isRemoved(), "Grapple caught a block through the mob in its path");
                helper.assertTrue(anchor.equals(obstacle), "Grapple caught " + anchor + " instead of " + obstacle);
                return true;
            }
            helper.assertTrue(removalAge < 0, golem.isRemoved()
                    ? "Block grapple ended at age " + removalAge + " without catching the block behind the mob"
                    : "Block grapple was removed at age " + removalAge + " by a mob in its path");
            if (!golem.isRemoved() && !samples.isEmpty() && samples.getLast().age() >= 2) {
                helper.assertTrue(golem.isAlive() && golem.getHealth() == golemHealth
                                && golem.getActiveEffects().isEmpty(),
                        "Block grapple hurt or bound the mob in its path");
                golem.discard();
            }
            return false;
        }

        // Donor getOwnerRelativeOffset: the vine leaves the user's eyes lowered by the given offset.
        boolean origin(double offsetY, double speed) {
            if (samples.size() < 3) {
                helper.assertTrue(removalAge < 0, "Held vine ended at age " + removalAge + " in an empty corridor");
                return false;
            }
            Vec3 base = spawnEye.add(0.0D, offsetY, 0.0D);
            Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            double step = speed * vine.getSpeedFactor();
            List<String> errors = new ArrayList<>();
            if (spawnTip.distanceTo(base) > EPS) {
                errors.add("spawn=" + spawnTip + " expected=" + base);
            }
            for (int index = 0; index < 3; index++) {
                Sample sample = samples.get(index);
                Vec3 expected = base.add(direction.scale(step * sample.age()));
                if (sample.attached() || sample.tip().distanceTo(expected) > EPS) {
                    errors.add("age=" + sample.age() + " tip=" + sample.tip() + " expected=" + expected);
                }
            }
            helper.assertTrue(errors.isEmpty(), "Held vine does not fly from the donor origin (eye "
                    + offsetY + "): " + String.join(" | ", errors));
            return true;
        }

        // Premise of the grass cases: the vine really broke the plant. A refusal to break it (gamerule,
        // canEntityDestroy, mayInteract) fails here instead of passing.
        boolean grassBroken() {
            if (brokenAge >= 0) return true;
            helper.assertTrue(removalAge < 0 && firstAttached() < 0 && samples.size() < 4,
                    "Fixture: the vine did not break the short grass in its path; block=" + level.getBlockState(grass)
                            + " removalAge=" + removalAge + " samples=" + samples.size());
            return false;
        }

        // Donor HPGrapplingVineEntity.afterBlockHit: a block the vine broke neither catches nor removes it.
        boolean grassCatch() {
            if (!grassBroken()) return false;
            int first = firstAttached();
            if (first < 0) {
                helper.assertTrue(removalAge < 0, "Grapple was removed at age " + removalAge
                        + " after breaking short grass at age " + brokenAge
                        + "; the donor vine flies on to the block behind it");
                helper.assertTrue(samples.isEmpty() || samples.getLast().age() < brokenAge + 6,
                        "Grapple broke the short grass but never caught the stone block behind it");
                return false;
            }
            BlockPos anchor = BlockPos.containing(samples.get(first).tip());
            List<String> errors = new ArrayList<>();
            if (!anchor.equals(obstacle) || !level.getBlockState(obstacle).is(Blocks.STONE)) {
                errors.add("caught " + anchor + " instead of the stone block " + obstacle + " behind the grass");
            }
            if (catchSounds != 1) errors.add("hp_grap played " + catchSounds + " times for one catch");
            if (removalAge >= 0) errors.add("removed at age " + removalAge + " on catching the block");
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} grassBrokenAge={} attachedAge={} anchor={}"
                    + " catchSounds={} errors={}", kind.label, brokenAge, samples.get(first).age(), anchor, catchSounds, errors);
            helper.assertTrue(errors.isEmpty(), "Grapple behind broken grass differs from the donor: "
                    + String.join(" | ", errors));
            return true;
        }

        // Donor: the per-tick hit of an attached vine on a block it breaks changes nothing; the pull goes on.
        boolean attachedGrass() {
            int first = firstAttached();
            if (first < 0) {
                helper.assertTrue(removalAge < 0, "Grapple ended at age " + removalAge + " before reaching the stone block");
                return false;
            }
            if (plantedAge < 0) {
                helper.assertTrue(removalAge < 0, "Attached grapple ended at age " + removalAge + " before the grass was planted");
                if (samples.size() - first < 3) return false;
                helper.assertTrue(BlockPos.containing(samples.getLast().tip()).equals(obstacle),
                        "Fixture: the grapple is not attached to the stone block");
                plantGrass();
                player.setDeltaMovement(Vec3.ZERO);
                plantedAge = samples.getLast().age();
                return false;
            }
            long ticksAfter = samples.stream().filter(sample -> sample.age() > plantedAge).count();
            if (ticksAfter < 3 && removalAge < 0) return false;
            helper.assertTrue(brokenAge > plantedAge && level.getBlockState(grass).isAir(),
                    "Fixture: the attached vine did not break the short grass on its line; block="
                            + level.getBlockState(grass) + " removalAge=" + removalAge);
            List<String> errors = new ArrayList<>();
            if (removalAge >= 0) {
                errors.add("attached grapple was dropped at age " + removalAge + " when its line broke short grass");
            }
            Vec3 centre = Vec3.atCenterOf(obstacle);
            Vec3 pull = centre.subtract(player.position()).normalize().scale(HermitPurpleSpec.GRAPPLE_PULL_SPEED);
            for (Sample sample : samples) {
                if (sample.age() <= plantedAge) continue;
                if (!sample.attached() || sample.tip().distanceTo(centre) > EPS) {
                    errors.add("age=" + sample.age() + " attached=" + sample.attached() + " tip=" + sample.tip());
                }
                if (sample.userMotion().distanceTo(pull) > EPS) {
                    errors.add("age=" + sample.age() + " pull on the user=" + sample.userMotion() + ", donor keeps " + pull);
                }
            }
            if (ticksAfter < 3) errors.add("only " + ticksAfter + " attached ticks after the grass broke");
            if (catchSounds != 1) errors.add("hp_grap played " + catchSounds + " times for one catch");
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} plantedAge={} grassBrokenAge={} removalAge={}"
                    + " catchSounds={} errors={}", kind.label, plantedAge, brokenAge, removalAge, catchSounds, errors);
            helper.assertTrue(errors.isEmpty(), "Attached grapple over broken grass differs from the donor: "
                    + String.join(" | ", errors));
            return true;
        }

        // Donor HPGrapplingVineEntity.afterBlockHit (hp_vine_entity) and 1.16 OwnerBoundProjectileEntity.afterBlockHit
        // (hp_grab): a vine that broke the block it hit flies on.
        boolean flightThroughGrass(double offsetY, double speed) {
            if (!grassBroken()) return false;
            long ticksAfter = samples.stream().filter(sample -> sample.age() > brokenAge).count();
            if (ticksAfter < 2 && removalAge < 0) return false;
            List<String> errors = new ArrayList<>();
            if (removalAge >= 0) {
                errors.add("removed at age " + removalAge + " after breaking short grass at age " + brokenAge
                        + "; the donor vine flies on");
            }
            Vec3 base = spawnEye.add(0.0D, offsetY, 0.0D);
            Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            double step = speed * vine.getSpeedFactor();
            for (Sample sample : samples) {
                Vec3 expected = base.add(direction.scale(step * sample.age()));
                if (sample.attached() || sample.tip().distanceTo(expected) > EPS) {
                    errors.add("age=" + sample.age() + " tip=" + sample.tip() + " expected=" + expected);
                }
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} grassBrokenAge={} removalAge={} samples={} errors={}",
                    kind.label, brokenAge, removalAge, samples.size(), errors);
            helper.assertTrue(errors.isEmpty(), "Held vine through broken grass differs from the donor: "
                    + String.join(" | ", errors));
            return true;
        }

        // Donor HPGrapplingVineEntity.afterBlockHit: a block it did not break removes the entity grapple.
        boolean unbrokenBlockRemovesEntityGrapple() {
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE), "Stone obstacle disappeared");
            int first = firstAttached();
            helper.assertTrue(first < 0, "Entity grapple attached to a block"
                    + (first < 0 ? "" : " at age " + samples.get(first).age()));
            if (removalAge < 0) {
                helper.assertTrue(samples.size() < 6, "Entity grapple flew on past a block it cannot break");
                return false;
            }
            helper.assertTrue(removalAge <= 3, "Entity grapple flew on past the stone block until age " + removalAge);
            return true;
        }

        // Donor HPGrapplingVineEntity applies no effect to the bound entity; only HPVineGrabEntity immobilizes it.
        boolean boundTarget(boolean immobilizes) {
            helper.assertTrue(golem.isAlive(), "The bound mob died");
            if (attachedTicks == 0) {
                helper.assertTrue(removalAge < 0, "Vine ended at age " + removalAge + " without catching the mob");
                return false;
            }
            if (attachedTicks < BOUND_TICKS && removalAge < 0) return false;
            List<String> errors = new ArrayList<>();
            if (immobilizes && immobilizedTicks != attachedTicks) {
                errors.add("grab vine immobilized the catch on " + immobilizedTicks + " of " + attachedTicks + " ticks");
            }
            if (!immobilizes && immobilizedTicks != 0) {
                errors.add("entity grapple immobilized the bound mob on " + immobilizedTicks + " of " + attachedTicks + " ticks");
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} attachedTicks={} immobilizedTicks={} errors={}",
                    kind.label, attachedTicks, immobilizedTicks, errors);
            helper.assertTrue(errors.isEmpty(), "Bound target differs from the donor: " + String.join(" | ", errors));
            return true;
        }

        double horizontalDistance(Entity other) {
            return Math.hypot(other.getX() - player.getX(), other.getZ() - player.getZ());
        }

        // Donor HPGrapplingVineEntity: a bound mob is dragged toward the user at 1 block per tick and let go within 2 blocks.
        boolean draggedMob() {
            helper.assertTrue(golem.isAlive(), "The dragged mob died");
            helper.assertTrue(!golem.isNoAi(), "Fixture: the dragged mob has no AI");
            if (removalAge < 0) {
                helper.assertTrue(dragDistances.size() < 12, "Mob held for " + dragDistances.size()
                        + " ticks and still not within 2 blocks of the user; distances=" + dragDistances);
                return false;
            }
            List<String> errors = new ArrayList<>();
            if (dragDistances.size() < 3) {
                errors.add("vine caught the mob for only " + dragDistances.size() + " ticks: " + dragDistances);
            }
            else {
                double firstDrop = dragDistances.get(0) - dragDistances.get(1);
                if (firstDrop < -EPS || firstDrop > 1.1D) {
                    errors.add("first drag step " + firstDrop + " blocks");
                }
                for (int index = 2; index < dragDistances.size(); index++) {
                    double drop = dragDistances.get(index - 1) - dragDistances.get(index);
                    if (Math.abs(drop - 1.0D) > 0.1D) {
                        errors.add("tick " + index + " moved the mob " + drop + " blocks, donor drags 1");
                    }
                }
                if (dragDistances.getLast() < 2.0D - EPS) {
                    errors.add("vine was still held at " + dragDistances.getLast() + " blocks, then removed");
                }
                if (dragDistances.getFirst() - golemDistanceAtRemoval < 3.0D) {
                    errors.add("mob travelled only " + (dragDistances.getFirst() - golemDistanceAtRemoval) + " blocks");
                }
            }
            if (golemDistanceAtRemoval >= 2.0D) {
                errors.add("vine was removed with the mob " + golemDistanceAtRemoval + " blocks away");
            }
            if (golemMotionAtRemoval == null || golemMotionAtRemoval.horizontalDistanceSqr() > EPS * EPS) {
                errors.add("mob keeps motion " + golemMotionAtRemoval + " after the release");
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} distances={} removalDistance={} errors={}",
                    kind.label, dragDistances, golemDistanceAtRemoval, errors);
            helper.assertTrue(errors.isEmpty(), "Dragged mob differs from the donor: " + String.join(" | ", errors));
            return true;
        }

        // Donor OwnerBoundProjectileEntity.doDragEntity and onRemovedFromWorld: the server sets and clears the
        // player's motion without a motion packet; the vine on the player's own client pulls and stops it. A packet
        // here would reach that client a tick late, carrying the server's friction-reduced pull (0.546 on a floor).
        boolean releasedPlayer() {
            helper.assertTrue(victim.isAlive(), "The pulled player died");
            if (removalAge < 0) {
                if (victimBoundTicks >= BOUND_TICKS && !victimMoved) {
                    // Stand in for the client: it has walked the player to 1.5 blocks and the last motion packet is gone.
                    victim.hurtMarked = false;
                    victim.setPos(victim.getX(), victim.getY(), player.getZ() + 1.5D);
                    victimMoved = true;
                }
                return false;
            }
            List<String> errors = new ArrayList<>();
            if (!victimMoved) {
                errors.add("vine ended at age " + removalAge + " before the player was pulled for " + BOUND_TICKS + " ticks");
            }
            errors.addAll(pullErrors);
            if (!Boolean.FALSE.equals(victimMarkedAtRemoval)) {
                errors.add("the release marked the player for a motion packet; its client stops itself when the vine goes");
            }
            if (victimMotionAtRemoval == null || victimMotionAtRemoval.lengthSqr() > EPS * EPS) {
                errors.add("the player keeps motion " + victimMotionAtRemoval + " after the release");
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} boundTicks={} markedAtRemoval={} motionAtRemoval={} errors={}",
                    kind.label, victimBoundTicks, victimMarkedAtRemoval, victimMotionAtRemoval, errors);
            helper.assertTrue(errors.isEmpty(), "Pulled player differs from the donor: " + String.join(" | ", errors));
            return true;
        }

        // Donor HPGrapplingVineEntity.moveToBlockAttached runs on both sides: the server and the user's own client
        // each set the 2.0 pull, and no motion packet goes out. A packet would reach that client a tick late,
        // carrying the pull the server's friction had already cut.
        boolean pulledPlayerUser() {
            int first = firstAttached();
            if (first < 0) {
                helper.assertTrue(removalAge < 0, "Grapple ended at age " + removalAge + " before reaching the stone block");
                return false;
            }
            List<Sample> attached = samples.subList(first, samples.size());
            if (attached.size() < BOUND_TICKS && removalAge < 0) return false;
            Vec3 centre = Vec3.atCenterOf(obstacle);
            Vec3 pull = centre.subtract(player.position()).normalize().scale(HermitPurpleSpec.GRAPPLE_PULL_SPEED);
            List<String> errors = new ArrayList<>();
            if (attached.size() < BOUND_TICKS) {
                errors.add("attached grapple ended at age " + removalAge + " after " + attached.size() + " ticks");
            }
            for (Sample sample : attached) {
                if (!sample.attached() || sample.tip().distanceTo(centre) > EPS) {
                    errors.add("age=" + sample.age() + " attached=" + sample.attached() + " tip=" + sample.tip());
                }
                if (sample.userMotion().distanceTo(pull) > EPS || sample.userMarked()) {
                    errors.add("age=" + sample.age() + " motion=" + sample.userMotion() + " expected=" + pull
                            + " marked=" + sample.userMarked());
                }
            }
            LogUtils.getLogger().info("R764_HP_GRAPPLE_COMPLETE case={} attachedTicks={} removalAge={} errors={}",
                    kind.label, attached.size(), removalAge, errors);
            helper.assertTrue(errors.isEmpty(), "Block grapple pull on a player user differs from the donor: "
                    + String.join(" | ", errors));
            return true;
        }

        int firstAttached() {
            for (int index = 0; index < samples.size(); index++) {
                if (samples.get(index).attached()) return index;
            }
            return -1;
        }

        void later(int ticks, Runnable task) {
            helper.runAfterDelay(ticks, () -> guarded(task));
        }

        void guarded(Runnable task) {
            if (closed || helper.testInfo.isDone()) return;
            try {
                task.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            watching = false;
            NeoForge.EVENT_BUS.unregister(joinListener);
            NeoForge.EVENT_BUS.unregister(tickListener);
            NeoForge.EVENT_BUS.unregister(leaveListener);
            NeoForge.EVENT_BUS.unregister(soundListener);
            try {
                if (power != null && power.hasPower()) power.getPowerType().forceUnsummon(player, power);
            }
            finally {
                if (breakRuleBefore != null) {
                    level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakRuleBefore, level.getServer());
                }
                if (vine != null && !vine.isRemoved()) vine.discard();
                if (golem != null && !golem.isRemoved()) golem.discard();
                if (victim != null && !victim.isRemoved()) {
                    level.removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
                }
                for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                if (player != null && !player.isRemoved()) {
                    level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
                LogUtils.getLogger().info("R764_HP_GRAPPLE_CLEANUP case={} listenersReleased=true", kind.label);
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
