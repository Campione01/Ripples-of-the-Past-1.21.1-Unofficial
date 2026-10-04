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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismSpaceRipperStingyEyesAbility;
import rotp.core.init.ModEntityTypes;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismEyeBeamCollisionGameTests {
    private static final double EPSILON = 1.0E-5D;

    private VampirismEyeBeamCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "srse_unbroken_block", timeoutTicks = 120)
    public static void bothEyeBeamsRetractAndExpireAtTheirFirstUnbrokenBlock(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "srse_empty_flight", timeoutTicks = 120)
    public static void bothEyeBeamsKeepExtendingBeforeNormalDetachWithoutABlock(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "srse_mixed_refused", timeoutTicks = 120)
    public static void bothEyeBeamsSelectOnlyRefusedEntityAcrossTheUnbrokenWall(GameTestHelper helper) {
        start(helper, true, true);
    }

    private static void start(GameTestHelper helper, boolean wall) {
        start(helper, wall, false);
    }

    private static void start(GameTestHelper helper, boolean wall, boolean target) {
        Fixture fixture = new Fixture(helper, wall, target);
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

    private record Frame(long time, int age, Vec3 root, Vec3 tip, Vec3 delta, float length,
            boolean bound, boolean removed, BlockHitResult collider, BlockHitResult outline,
            int candidates, int intersections, boolean targetCandidate, AABB targetBox, Vec3 targetClip, float targetHealth) {}
    private record Impact(HitResult.Type type, BlockPos block, UUID entity, boolean canceled) {}
    private record Attempt(float amount, UUID direct, UUID cause, boolean canceled) {}
    private record Step(Frame before, Frame after, List<Impact> impacts, List<Attempt> attempts) {}

    private static final class Tracked {
        final SpaceRipperStingyEyesEntity beam;
        final boolean right;
        final List<Step> steps = new ArrayList<>();
        final List<Impact> impacts = new ArrayList<>();
        final List<Attempt> attempts = new ArrayList<>();
        Frame pending;
        Step contact;

        Tracked(SpaceRipperStingyEyesEntity beam, boolean right) {
            this.beam = beam;
            this.right = right;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 38;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean withWall;
        private final boolean withTarget;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        private final Map<UUID, Tracked> beams = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private Player user;
        private Cow target;
        private Vec3 targetPosition;
        private float targetHealth;
        private Vec3 userPosition;
        private AABB room;
        private PlayerPower power;
        private VampirismData vampire;
        private Ability ability;
        private EntityActionInstance action;
        private RuntimeException observerFailure;
        private int userTicks;
        private int poweredAt = -1;
        private int pressedAt;
        private boolean pressed;
        private boolean released;
        private boolean closed;
        private float speed;
        private String lastShadeDiagnostic;

        Fixture(GameTestHelper helper, boolean withWall, boolean withTarget) {
            this.helper = helper;
            level = helper.getLevel();
            this.withWall = withWall;
            this.withTarget = withTarget;
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL, "SRSE fixture requires non-Peaceful difficulty");
            speed = 0.5F + level.getDifficulty().getId() * 0.25F;
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 3, y - 1, z + 1);
            BlockPos max = new BlockPos(x + 13, y + 7, z + 13);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight(),
                    "SRSE fixture exceeds build height");
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(level.getEntities((Entity) null, room).isEmpty(), "SRSE fixture contains another entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "SRSE fixture is obstructed");
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 4, y + 3, z + 1), new BlockPos(x + 12, y + 3, z + 12))) {
                ownBlock(pos, Blocks.STONE.defaultBlockState());
            }
            if (withWall) {
                for (int px = x + 7; px <= x + 9; px++) {
                    BlockPos pos = new BlockPos(px, y + 1, z + 5);
                    ownBlock(pos, Blocks.BEDROCK.defaultBlockState());
                    wall.put(pos, level.getBlockState(pos));
                }
            }
            userPosition = new Vec3(x + 8.5D, y, z + 4.5D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(level.addFreshEntity(user), "Could not add SRSE user");
            if (withTarget) {
                target = EntityType.COW.create(level);
                helper.assertTrue(target != null, "Could not create SRSE mixed target");
                target.setNoAi(true);
                target.setNoGravity(true);
                targetPosition = new Vec3(x + 8.5D, y + 0.25D, z + 6.5D);
                target.setPos(targetPosition);
                helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable()
                                && !target.isSpectator() && target.canBeHitByProjectile(), "SRSE target is not projectile eligible");
                for (var entry : wall.entrySet()) {
                    helper.assertTrue(!entry.getValue().getCollisionShape(level, entry.getKey()).bounds().move(entry.getKey())
                                    .intersects(target.getBoundingBox()), "SRSE target physically overlaps the wall");
                }
                targetHealth = target.getHealth();
            }
            registerObservers();
            log("setup owner=" + user.getUUID() + " pos=" + userPosition + " speed=" + speed
                    + " wall=" + wall.keySet() + " ownedBlocks=" + original.size()
                    + " target=" + (target == null ? "none" : target.getUUID() + ":" + target.getBoundingBox()));
        }

        private void ownBlock(BlockPos pos, BlockState state) {
            original.put(pos.immutable(), level.getBlockState(pos));
            helper.assertTrue(level.setBlockAndUpdate(pos, state), "Could not place SRSE fixture block");
        }

        private boolean shadeReady() {
            BlockPos eye = BlockPos.containing(user.getEyePosition());
            BlockPos sun = BlockPos.containing(user.getX(), Math.round(user.getY(1.0D)), user.getZ());
            boolean eyeSeesSky = level.canSeeSky(eye);
            boolean sunSeesSky = level.canSeeSky(sun);
            int sky = level.getBrightness(LightLayer.SKY, eye);
            boolean ticking = level.isPositionEntityTicking(user.blockPosition());
            String diagnostic = "eye=" + eye + " sun=" + sun + " eyeCanSeeSky=" + eyeSeesSky
                    + " sunCanSeeSky=" + sunSeesSky + " eyeSky=" + sky + " entityTicking=" + ticking;
            if (!diagnostic.equals(lastShadeDiagnostic)) {
                lastShadeDiagnostic = diagnostic;
                log("shade-readiness " + diagnostic);
            }
            return !eyeSeesSky && !sunSeesSky && sky < 12;
        }

        private void grantPower() {
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            vampire = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            vampire.setVampireFullPower(true, user);
            var bloodState = VampirismState.get(user).blood();
            bloodState.setCurrent(bloodState.max());
            vampire.setBloodLevel(bloodState.current());
            log("blood-setup existingMax=" + bloodState.max() + " actualCurrent=" + bloodState.current()
                    + " minimumFixtureBudget=160.0 performCostPerTick=20.0 selectedFlightTicks=3");
            helper.assertTrue(bloodState.max() >= 160.0F && bloodState.current() >= 160.0F,
                    "SRSE actor's existing blood capacity cannot fund the early collision/control window");
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility("vampirism_space_ripper_stingy_eyes");
            helper.assertTrue(ability instanceof VampirismSpaceRipperStingyEyesAbility
                            && ability.abilityType == VampirismPowerType.VAMPIRE_SPACE_RIPPER_STINGY_EYES.get(),
                    "Missing registered SRSE ability");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            poweredAt = userTicks;
            log("grant shadeReady=true full=" + vampire.isVampireAtFullPower() + " blood=" + blood());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ItemEntity drop && room.contains(drop.position())) drops.add(drop);
                if (!(event.getEntity() instanceof SpaceRipperStingyEyesEntity beam) || beam.getOwner() != user) return;
                CompoundTag nbt = beam.saveWithoutId(new CompoundTag());
                Tracked tracked = new Tracked(beam, nbt.getBoolean("IsRightEye"));
                Tracked previous = beams.putIfAbsent(beam.getUUID(), tracked);
                observe(() -> {
                    helper.assertTrue(released && previous == null && beams.size() <= 2 && !event.isCanceled()
                                    && action == LivingComponentAction.getCurEntityAction(user)
                                    && action instanceof VampirismSpaceRipperStingyEyesAbility.SpaceRipperInstance
                                    && action.getPhase() == ActionPhase.PERFORM && userTicks - pressedAt >= 20,
                            "Eye beam did not come from the real charged/released SRSE action");
                    helper.assertTrue(beam.getType() == ModEntityTypes.SPACE_RIPPER_STINGY_EYES.get()
                                    && beam.tickCount == 0 && beam.isBoundToOwner() && beam.getLength() == 0
                                    && beam.getSpeedFactor() == 1.0D && near(beam.position(), expectedRoot(tracked))
                                    && near(beam.getDeltaMovement(), Vec3.ZERO), "SRSE eye join geometry/type/age invalid");
                    log("join uuid=" + beam.getUUID() + " right=" + tracked.right + " tip=" + beam.position()
                            + " root=" + beam.getOriginPoint(1) + " naturalChargeTicks=" + (userTicks - pressedAt));
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = beams.get(event.getEntity().getUUID());
                if (tracked == null) return;
                requireFiring();
                helper.assertTrue(!event.isCanceled() && tracked.pending == null && tracked.beam.getOwner() == user
                                && level.isPositionEntityTicking(tracked.beam.blockPosition()), "SRSE natural tick invalid");
                tracked.impacts.clear();
                tracked.attempts.clear();
                tracked.pending = frame(tracked);
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Tracked tracked = beams.get(event.getProjectile().getUUID());
                if (tracked == null) return;
                HitResult hit = event.getRayTraceResult();
                tracked.impacts.add(new Impact(hit.getType(), hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null,
                        hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null, event.isCanceled()));
            });
            Consumer<LivingIncomingDamageEvent> refuse = event -> observe(() -> {
                if (target == null || event.getEntity() != target) return;
                Entity direct = event.getSource().getDirectEntity();
                Tracked tracked = direct == null ? null : beams.get(direct.getUUID());
                helper.assertTrue(tracked != null && tracked.pending != null && event.getSource().getEntity() == user
                                && event.getOriginalAmount() > 0 && !event.isCanceled()
                                && tracked.impacts.stream().anyMatch(hit -> target.getUUID().equals(hit.entity) && !hit.canceled),
                        "SRSE refusal did not follow its owned natural entity impact");
                event.setCanceled(true);
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (target == null || event.getEntity() != target) return;
                Entity direct = event.getSource().getDirectEntity();
                Tracked tracked = direct == null ? null : beams.get(direct.getUUID());
                helper.assertTrue(tracked != null && tracked.pending != null && event.getSource().getEntity() == user
                                && event.getOriginalAmount() > 0 && event.isCanceled()
                                && tracked.impacts.stream().anyMatch(hit -> target.getUUID().equals(hit.entity) && !hit.canceled),
                        "SRSE canceled attempt lost its Pre/ENTITY pairing");
                tracked.attempts.add(new Attempt(event.getOriginalAmount(), direct.getUUID(), user.getUUID(), event.isCanceled()));
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                Tracked tracked = beams.get(event.getEntity().getUUID());
                if (tracked == null) return;
                Frame after = frame(tracked);
                Frame before = tracked.pending;
                helper.assertTrue(before != null && before.age == after.age && before.time == after.time,
                        "Missing paired natural eye-beam tick");
                Step step = new Step(before, after, List.copyOf(tracked.impacts), List.copyOf(tracked.attempts));
                tracked.steps.add(step);
                if (before.age == 1) {
                    helper.assertTrue(step.impacts.isEmpty() && Math.abs(after.length - speed) < EPSILON
                                    && near(after.tip, before.root.add(0, 0, speed)) && after.bound && !after.removed,
                            "First natural eye extension differs from fixed difficulty speed");
                }
                if (before.collider.getType() == HitResult.Type.BLOCK && tracked.contact == null) tracked.contact = step;
                log("step uuid=" + tracked.beam.getUUID() + " right=" + tracked.right + " pre=" + before + " post=" + after
                        + " impacts=" + step.impacts + " attempts=" + step.attempts + " blood=" + blood() + " phaseTick=" + action.getPhaseTick());
                tracked.pending = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            if (withTarget) {
                listeners.add(refuse);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingIncomingDamageEvent.class, refuse);
                listeners.add(damage);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            }
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Vec3 expectedRoot(Tracked tracked) {
            return user.getEyePosition(1).add(tracked.right ? -0.09375D : 0.09375D, 0, 0);
        }

        private Frame frame(Tracked tracked) {
            SpaceRipperStingyEyesEntity beam = tracked.beam;
            Vec3 root = beam.getOriginPoint(1);
            helper.assertTrue(near(root, expectedRoot(tracked)), "SRSE eye root moved from its fixed pitch-zero offset");
            Vec3 end = beam.position().add(beam.getDeltaMovement());
            AABB query = beam.getBoundingBox().expandTowards(root.subtract(end)).inflate(1);
            List<Entity> candidates = level.getEntities(beam, query, entity -> entity != user
                    && !(entity instanceof SpaceRipperStingyEyesEntity sibling && sibling.getOwner() == user));
            int intersections = 0;
            for (Entity entity : candidates) {
                AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + beam.getBbWidth() / 2.0D);
                if (box.contains(root) || box.clip(root, end).isPresent()) intersections++;
            }
            AABB targetBox = target == null ? null : target.getBoundingBox().inflate(target.getPickRadius() + beam.getBbWidth() / 2.0D);
            Vec3 targetClip = targetBox == null ? null : targetBox.contains(root) ? root : targetBox.clip(root, end).orElse(null);
            return new Frame(level.getGameTime(), beam.tickCount, root, beam.position(), beam.getDeltaMovement(), beam.getLength(),
                    beam.isBoundToOwner(), beam.isRemoved(),
                    level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, beam)),
                    level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, beam)),
                    candidates.size(), intersections, target != null && candidates.contains(target), targetBox, targetClip,
                    target == null ? 0 : target.getHealth());
        }

        private float blood() { return VampirismState.get(user).blood().current(); }

        private void requireFiring() {
            helper.assertTrue(action != null && action == LivingComponentAction.getCurEntityAction(user)
                            && action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() < 20 && blood() >= 20,
                    "SRSE discriminator reached action end or blood starvation");
        }

        private void press() {
            helper.assertTrue(shadeReady() && !user.isOnFire() && !user.isCreative() && !user.getAbilities().instabuild
                            && vampire.isVampireAtFullPower() && vampire.getCuringStage(user) <= 1
                            && blood() >= 90 && level.isPositionEntityTicking(user.blockPosition()), "SRSE Survival admission not ready");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "Registered SRSE HOLD failed admission");
            pressed = true;
            pressedAt = userTicks;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD, 0, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof VampirismSpaceRipperStingyEyesAbility.SpaceRipperInstance,
                    "Registered SRSE input installed wrong action");
            action = (EntityActionInstance) input.action;
            helper.assertTrue(action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.WINDUP
                            && action.getCurPhaseLength() == 20, "SRSE charge phase is not natural WINDUP20");
            log("press blood=" + blood() + " shade=true pitch=0 yaw=0 userTicks=" + userTicks);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 100, "SRSE watchdog userTicks=" + userTicks + " powered=" + (power != null)
                        + " beams=" + beams.size() + " sky=" + level.getBrightness(LightLayer.SKY, BlockPos.containing(user.getEyePosition()))
                        + " lastShade=" + lastShadeDiagnostic);
                helper.assertTrue(user.isAlive() && !user.isOnFire() && near(user.position(), userPosition)
                                && user.getYRot() == 0 && user.getXRot() == 0 && user.yBodyRot == 0,
                        "SRSE owner moved, rotated, burned or died");
                helper.assertTrue(target == null || target.isAlive() && !target.isInvulnerable() && target.canBeHitByProjectile()
                                && level.isPositionEntityTicking(target.blockPosition()) && near(target.position(), targetPosition)
                                && target.getHealth() == targetHealth, "SRSE mixed target lost its eligibility/stationary refusal state");
                if (power == null && userTicks >= 2 && shadeReady()) grantPower();
                if (!pressed && poweredAt >= 0 && userTicks >= poweredAt + 2) press();
                if (pressed && !released && action.getPhase() == ActionPhase.WINDUP
                        && action.getPhaseTick() >= 20 && userTicks - pressedAt >= 20) {
                    helper.assertTrue(beams.isEmpty(), "SRSE emitted before charged release");
                    released = true;
                    AbilityInput.keyRelease(KEY, user);
                    log("release chargeTicks=" + (userTicks - pressedAt) + " blood=" + blood());
                }
                boolean ready = beams.size() == 2 && (withWall ? beams.values().stream().allMatch(t -> t.contact != null)
                        : beams.values().stream().allMatch(t -> t.steps.size() >= 3));
                if (ready) {
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

        private void validate() {
            requireFiring();
            helper.assertTrue(beams.values().stream().filter(t -> t.right).count() == 1
                            && beams.values().stream().filter(t -> !t.right).count() == 1 && drops.isEmpty(),
                    "SRSE did not emit exactly one beam per eye without drops");
            for (Tracked tracked : beams.values()) {
                helper.assertTrue(tracked.steps.stream().allMatch(s -> (withTarget || s.before.candidates == 0 && s.before.intersections == 0)
                                && s.impacts.stream().noneMatch(i -> i.canceled || !withTarget && i.type == HitResult.Type.ENTITY)),
                        "SRSE block discriminator acquired entity interference/cancellation");
                if (withWall) {
                    Step hit = tracked.contact;
                    BlockPos pos = hit.before.collider.getBlockPos();
                    helper.assertTrue(hit.before.age == 2 && hit.before.bound && !hit.before.removed
                                    && Math.abs(hit.before.length - speed) < EPSILON && near(hit.before.delta, new Vec3(0, 0, speed))
                                    && wall.containsKey(pos) && hit.before.outline.getType() == HitResult.Type.BLOCK
                                    && pos.equals(hit.before.outline.getBlockPos())
                                    && hit.before.collider.getDirection() == hit.before.outline.getDirection()
                                    && near(hit.before.collider.getLocation(), hit.before.outline.getLocation())
                                    && wall.get(pos).getDestroySpeed(level, pos) < 0
                                    && (withTarget || !hit.impacts.isEmpty() && hit.impacts.stream().allMatch(i -> i.type == HitResult.Type.BLOCK && pos.equals(i.block)))
                                    && wall.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),
                            "SRSE first unbroken-block contact premises failed");
                    if (withTarget) {
                        helper.assertTrue(hit.before.candidates == 1 && hit.before.intersections == 1 && hit.before.targetCandidate
                                        && hit.before.targetClip != null && target.canBeHitByProjectile()
                                        && hit.before.root.distanceToSqr(hit.before.collider.getLocation())
                                                < hit.before.root.distanceToSqr(hit.before.targetClip)
                                        && hit.impacts.stream().anyMatch(i -> target.getUUID().equals(i.entity))
                                        && hit.impacts.stream().filter(i -> i.type == HitResult.Type.ENTITY).count() == 1
                                        && hit.impacts.stream().allMatch(i -> target.getUUID().equals(i.entity)
                                                || i.type == HitResult.Type.BLOCK && pos.equals(i.block))
                                        && hit.attempts.size() == 1 && hit.attempts.stream().allMatch(a -> a.amount > 0 && a.canceled
                                                && a.direct.equals(tracked.beam.getUUID()) && a.cause.equals(user.getUUID()))
                                        && hit.before.targetHealth == targetHealth && hit.after.targetHealth == targetHealth,
                                "SRSE mixed ray lacks paired geometry/eligible canceled damage");
                    }
                }
                else {
                    for (Step step : tracked.steps) {
                        helper.assertTrue(step.impacts.isEmpty() && step.before.collider.getType() == HitResult.Type.MISS
                                        && step.after.bound && !step.after.removed
                                        && Math.abs(step.after.length - step.before.length - speed) < EPSILON
                                        && near(step.after.tip, step.before.root.add(0, 0, step.after.age * (double) speed)),
                                "Empty eye beam stopped growing before normal detach");
                    }
                }
            }
            log("controls valid=true eyes=2 blood=" + blood() + " phaseTick=" + action.getPhaseTick()
                    + " wallUnchanged=" + wall.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())));
            if (withTarget) {
                for (Tracked tracked : beams.values()) {
                    Step hit = tracked.contact;
                    helper.assertTrue(hit.impacts.stream().allMatch(i -> i.type == HitResult.Type.ENTITY && target.getUUID().equals(i.entity))
                                    && hit.after.bound && !hit.after.removed && Math.abs(hit.after.length - hit.before.length - speed) < EPSILON
                                    && near(hit.after.tip, hit.after.root.add(0, 0, 2.0D * speed)),
                            "SRSE mixed refused ray retained block effects or stopped extending; age=" + hit.after.age);
                }
            }
            else if (withWall) {
                for (Tracked tracked : beams.values()) {
                    Step hit = tracked.contact;
                    // At first contact the donor retracts one speed step from a one-step extension to zero and discards.
                    helper.assertTrue(hit.after.removed && tracked.beam.getRemovalReason() == Entity.RemovalReason.DISCARDED
                                    && hit.after.bound && hit.after.age < 20,
                            "Eye beam did not expire from first blocked retraction; age=" + hit.after.age + " length="
                                    + hit.before.length + "->" + hit.after.length + " removed=" + hit.after.removed);
                }
            }
            log("result scope=" + (withTarget ? "mixed-refused-entity-only" : withWall ? "first-block-retraction-removal" : "empty-three-step-extension"));
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("SRSE block fixture observer failure", error);
            }
        }

        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private void log(String message) { JojoMod.LOGGER.info("SRSE-BLOCK {} {}", withTarget ? "mixed-refused" : withWall ? "wall" : "empty", message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
                for (Tracked tracked : beams.values()) if (!tracked.beam.isRemoved()) tracked.beam.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (target != null) target.discard();
                if (user != null) user.discard();
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = original.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                log("cleanup listeners=0 restored=" + restored + " blocks=" + original.size() + " beams=" + beams.size());
                if (!restored) throw new IllegalStateException("SRSE fixture blocks not exactly restored");
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
