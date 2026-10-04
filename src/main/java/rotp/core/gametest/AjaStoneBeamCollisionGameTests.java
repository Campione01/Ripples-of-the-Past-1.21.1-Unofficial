package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import rotp.core.block.WoodenCoffinBlock;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AjaStoneBeamCollisionGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final float DAMAGE = 10.0F;
    private static final float REQUESTED_LENGTH = 21.0F;

    private AjaStoneBeamCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "aja_item_mixed", timeoutTicks = 100)
    public static void chargedAjaKeepsFullRayEntityPriorityBehindWall(GameTestHelper helper) {
        start(helper, Scenario.MIXED);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "aja_item_entity", timeoutTicks = 100)
    public static void chargedAjaHitsEligibleHuskThroughItsRealItemRoute(GameTestHelper helper) {
        start(helper, Scenario.ENTITY);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "aja_item_block", timeoutTicks = 100)
    public static void chargedAjaBlockOnlyHitPlacesSupportedFire(GameTestHelper helper) {
        start(helper, Scenario.BLOCK);
    }

    private enum Scenario { MIXED, ENTITY, BLOCK }

    private static void start(GameTestHelper helper, Scenario scenario) {
        Fixture fixture = new Fixture(helper, scenario);
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

    private record BlockRay(HitResult.Type type, BlockPos block, Direction face, Vec3 point) {
        static BlockRay of(BlockHitResult hit) {
            return new BlockRay(hit.getType(), hit.getBlockPos().immutable(), hit.getDirection(), hit.getLocation());
        }
    }
    private record OwnerTick(long time, int holdTick, int remaining, int brightness) {}
    private record Completion(long time, int userTicks, int holdTicks, int consumedCount, float cooldown, boolean cooldownActive) {}
    private record CooldownStep(int userTicks, float fraction, boolean active) {}
    private record BeamFrame(long time, int age, Vec3 start, Vec3 eyeStart, Vec3 requestedEnd, Vec3 storedEnd, float storedLength, boolean shortenedEndpoint,
            AABB fullQuery, List<UUID> fullCandidates, AABB blockCappedQuery, List<UUID> blockCappedCandidates,
            AABB targetBox, Vec3 fullTargetClip, Vec3 eyeTargetClip, Vec3 cappedTargetClip,
            BlockRay fullCollider, BlockRay fullOutline, BlockRay storedCollider, BlockRay storedOutline,
            float health, int targetFire, int brightness, boolean fireCellEmpty) {}
    private record Impact(HitResult.Type type, UUID target, BlockPos block, Direction face, Vec3 point, boolean canceled) {}
    private record Incoming(LivingIncomingDamageEvent event, int fireAtDispatch, float healthAtDispatch) {}
    private record Attempt(float amount, boolean ultraviolet, UUID direct, UUID cause, boolean canceled,
            int fireAtDispatch, float healthAtDispatch) {}
    private record Contact(UUID beam, BeamFrame before, float health, int targetFire, BlockState fireCell,
            boolean removed, Entity.RemovalReason removalReason, List<Impact> impacts, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Scenario scenario;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> structural = new LinkedHashMap<>();
        private final List<BlockPos> roof = new ArrayList<>();
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<LightBeamEntity> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        private final List<Incoming> incoming = new ArrayList<>();
        private Player user;
        private PlayerPower power;
        private Husk target;
        private LightBeamEntity beam;
        private ItemStack usedStack;
        private Vec3 userPosition;
        private Vec3 targetPosition;
        private Vec3 expectedRoot;
        private AABB room;
        private BlockPos wallCenter;
        private BlockPos firePos;
        private BlockPos lampPos;
        private OwnerTick ownerTick;
        private Completion completion;
        private CooldownStep cooldownStep;
        private BeamFrame before;
        private Contact contact;
        private RuntimeException observerFailure;
        private int userTicks;
        private int targetTicks;
        private int holdTicks;
        private int beamTicks;
        private float targetInitialHealth;
        private boolean actorsReady;
        private boolean used;
        private boolean closed;

        Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            level = helper.getLevel();
            this.scenario = scenario;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 6, y + 2, z + 6);
            BlockPos max = new BlockPos(x + 11, y + 30, z + 11);
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, room).isEmpty(), "Aja fixture lacks its empty in-bounds column");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Aja fixture column is obstructed");
            }
            for (int dx = 7; dx <= 9; dx++) {
                for (int dz = 7; dz <= 9; dz++) {
                    BlockPos shade = new BlockPos(x + dx, y + 29, z + dz);
                    roof.add(shade);
                    place(shade, Blocks.STONE.defaultBlockState());
                    if (scenario != Scenario.ENTITY) {
                        BlockPos obstacle = new BlockPos(x + dx, y + 20, z + dz);
                        wall.add(obstacle);
                        place(obstacle, Blocks.STONE.defaultBlockState());
                    }
                }
            }
            lampPos = new BlockPos(x + 10, y + 24, z + 8);
            place(lampPos, Blocks.SEA_LANTERN.defaultBlockState());
            wallCenter = new BlockPos(x + 8, y + 20, z + 8);
            firePos = wallCenter.above();
            original.put(firePos, level.getBlockState(firePos));
            expectedRoot = new Vec3(x + 8.5D, y + 25.5D, z + 8.5D);
            targetPosition = new Vec3(x + 8.5D, y + 15.0D, z + 8.5D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            // An exact half-block muzzle keeps the existing float-length wall endpoint away from rounding ambiguity.
            userPosition = new Vec3(expectedRoot.x, expectedRoot.y + 0.3D - user.getEyeHeight(), expectedRoot.z);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 90);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(user), "Could not add Aja user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AJA_STONE.get()));
            if (scenario != Scenario.BLOCK) {
                helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL, "Aja Husk control needs existing non-Peaceful difficulty");
                target = EntityType.HUSK.create(level);
                helper.assertTrue(target != null, "Could not create the Aja UV victim");
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setPos(targetPosition);
                targetInitialHealth = target.getHealth();
            }
            registerObservers();
            log("setup user=" + user.getUUID() + " feet=" + userPosition + " expectedRoot=" + expectedRoot
                    + " target=" + id(target) + " targetFeet=" + targetPosition + " room=" + room
                    + " roof=" + roof + " lamp=" + lampPos + " wall=" + wall + " fire=" + firePos);
        }

        private void place(BlockPos pos, BlockState state) {
            original.put(pos, level.getBlockState(pos));
            structural.put(pos, state);
            helper.assertTrue(level.setBlockAndUpdate(pos, state), "Could not place an owned Aja fixture block");
        }

        private boolean lightingReady() {
            BlockPos userEye = BlockPos.containing(user.getEyePosition());
            BlockPos targetFeet = BlockPos.containing(targetPosition);
            BlockPos targetEye = target == null ? targetFeet : BlockPos.containing(target.getEyePosition());
            boolean states = structural.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
            int brightness = level.getMaxLocalRawBrightness(user.blockPosition());
            boolean userShade = !level.canSeeSky(user.blockPosition()) && !level.canSeeSky(userEye);
            boolean targetShade = target == null || !level.canSeeSky(targetFeet) && !level.canSeeSky(targetEye);
            boolean ticking = level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(targetFeet);
            boolean ready = states && brightness > 9 && userShade && targetShade && ticking;
            log("readiness tick=" + helper.getTick() + " ready=" + ready + " states=" + states + " brightness=" + brightness
                    + " blockLight=" + level.getBrightness(LightLayer.BLOCK, user.blockPosition()) + " userShade=" + userShade
                    + " targetShade=" + targetShade + " targetSky=" + level.getBrightness(LightLayer.SKY, targetEye) + " ticking=" + ticking);
            return ready;
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level) return;
                if (ownerTick != null && joinEvent.getEntity() instanceof ItemEntity item && item.distanceToSqr(user) < 4
                        && (item.getItem().is(Items.REDSTONE) || item.getItem().is(ModItems.AJA_STONE.get())
                                || item.getItem().is(ModItems.SUPER_AJA_STONE.get()))) drops.add(item);
                if (!(joinEvent.getEntity() instanceof LightBeamEntity created) || created.getOwner() != user) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(used && ownerTick != null && ownerTick.holdTick == 20 && ownerTick.remaining == 1
                                    && user.getUseItemRemainingTicks() == 0
                                    && ownerTick.time == level.getGameTime() && spawned.size() == 1 && beam == null && !joinEvent.isCanceled(),
                            "Aja beam did not come from its twentieth natural item-use tick");
                    requireInputState();
                    helper.assertTrue(level.getMaxLocalRawBrightness(user.blockPosition()) > 9,
                            "Aja completion lost its real sufficient-light condition");
                    beam = created;
                    helper.assertTrue(beam.getType() == ModEntityTypes.AJA_STONE_BEAM.get() && beam.getBaseDamage() == DAMAGE
                                    && beam.ticksLifespan() == 1 && near(beam.position(), expectedRoot) && beam.position().y == expectedRoot.y
                                    && beam.getYRot() == 0 && beam.getXRot() == 90 && beam.getLength() > 0 && beam.getLength() <= REQUESTED_LENGTH,
                            "Registered Aja beam has the wrong owner, root, direction or scalar");
                    log("spawn beam=" + beam.getUUID() + " owner=" + user.getUUID() + " age=" + beam.tickCount
                            + " root=" + beam.position() + " eye=" + beam.getEyePosition(1) + " yaw=" + beam.getYRot() + " pitch=" + beam.getXRot()
                            + " damage=" + beam.getBaseDamage() + " nominalRequest=" + REQUESTED_LENGTH + " storedLength=" + beam.getLength()
                            + " storedEnd=" + beam.getEndPoint() + " nbt=" + beam.saveWithoutId(new CompoundTag()));
                });
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() == user && used && completion == null) {
                    helper.assertTrue(!preEvent.isCanceled() && ownerTick == null, "Aja user tick was canceled or unpaired");
                    requireActorsBeforeContact();
                    requireInputState();
                    holdTicks++;
                    ownerTick = new OwnerTick(level.getGameTime(), holdTicks, user.getUseItemRemainingTicks(),
                            level.getMaxLocalRawBrightness(user.blockPosition()));
                    log("hold-pre " + ownerTick);
                    helper.assertTrue(holdTicks <= 20 && user.isUsingItem() && user.getUsedItemHand() == InteractionHand.MAIN_HAND
                                    && user.getUseItem().is(ModItems.AJA_STONE.get()) && ownerTick.remaining == 21 - holdTicks
                                    && ownerTick.brightness > 9, "Aja did not retain its natural twenty-tick hold");
                }
                if (preEvent.getEntity() != beam || contact != null) return;
                helper.assertTrue(!preEvent.isCanceled() && before == null && beamTicks == 0 && beam.tickCount == 1
                                && beam.getOwner() == user && level.isPositionEntityTicking(beam.blockPosition()),
                        "Aja beam lacks its first natural owned tick");
                requireActorsBeforeContact();
                impacts.clear();
                incoming.clear();
                before = frame();
                log("beam-pre " + before);
                requireRay(before);
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                if (impactEvent.getProjectile() != beam || contact != null) return;
                helper.assertTrue(before != null && !impactEvent.isCanceled(), "Aja impact was canceled or outside its natural tick");
                HitResult hit = impactEvent.getRayTraceResult();
                helper.assertTrue(hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target
                                || hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(wallCenter) && !wall.isEmpty(),
                        "Aja impact escaped its owned wall and UV target");
                impacts.add(impactEvent);
                log("impact " + impact(impactEvent));
            });
            Consumer<LivingIncomingDamageEvent> damage = damageEvent -> observe(() -> {
                if (damageEvent.getEntity() != target || contact != null) return;
                helper.assertTrue(before != null && impacts.stream().anyMatch(hitEvent ->
                                hitEvent.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == target),
                        "Aja UV victim took damage outside its real beam contact");
                incoming.add(new Incoming(damageEvent, target.getRemainingFireTicks(), target.getHealth()));
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == user) {
                    userTicks++;
                    if (ownerTick != null) {
                        log("hold-post tick=" + holdTicks + " remaining=" + user.getUseItemRemainingTicks()
                                + " using=" + user.isUsingItem() + " originalCount=" + usedStack.getCount()
                                + " brightness=" + level.getMaxLocalRawBrightness(user.blockPosition()) + " cooldown=" + cooldown());
                        if (beam == null) helper.assertTrue(holdTicks < 20 && user.isUsingItem()
                                        && user.getUseItemRemainingTicks() == 20 - holdTicks && usedStack.getCount() == 1,
                                "Aja hold stopped or consumed before natural completion");
                        else {
                            completion = new Completion(level.getGameTime(), userTicks, holdTicks, 1 - usedStack.getCount(), cooldown(),
                                    user.getCooldowns().isOnCooldown(ModItems.AJA_STONE.get()));
                            helper.assertTrue(holdTicks == 20 && !user.isUsingItem() && user.getUseItemRemainingTicks() == 0 && usedStack.isEmpty()
                                            && completion.consumedCount == 1 && completion.cooldownActive && completion.cooldown > 0,
                                    "Aja completion did not consume the stone and install cooldown");
                            log("completion " + completion);
                        }
                        ownerTick = null;
                    }
                    else if (completion != null && cooldownStep == null) {
                        cooldownStep = new CooldownStep(userTicks, cooldown(), user.getCooldowns().isOnCooldown(ModItems.AJA_STONE.get()));
                        log("next-cooldown " + cooldownStep);
                    }
                }
                if (postEvent.getEntity() == target) targetTicks++;
                if (postEvent.getEntity() != beam || contact != null) return;
                helper.assertTrue(before != null && before.time == level.getGameTime() && before.age == beam.tickCount,
                        "Aja beam lacks its matching natural Pre/Post pair");
                beamTicks++;
                contact = new Contact(beam.getUUID(), before, target == null ? 0 : target.getHealth(),
                        target == null ? 0 : target.getRemainingFireTicks(), level.getBlockState(firePos), beam.isRemoved(), beam.getRemovalReason(),
                        impacts.stream().map(Fixture::impact).toList(), incoming.stream().map(Fixture::attempt).toList());
                log("beam-post " + contact);
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

        private BeamFrame frame() {
            Vec3 start = beam.position();
            Vec3 direction = Vec3.directionFromRotation(beam.getXRot(), beam.getYRot());
            Vec3 end = start.add(direction.scale(REQUESTED_LENGTH));
            Vec3 eyeStart = beam.getEyePosition(1);
            Vec3 storedEnd = beam.getEndPoint();
            BlockHitResult fullCollider = clip(start, end, ClipContext.Block.COLLIDER);
            BlockHitResult fullOutline = clip(start, end, ClipContext.Block.OUTLINE);
            BlockHitResult storedCollider = clip(start, storedEnd, ClipContext.Block.COLLIDER);
            BlockHitResult storedOutline = clip(start, storedEnd, ClipContext.Block.OUTLINE);
            Vec3 cappedEnd = storedCollider.getType() == HitResult.Type.BLOCK ? storedCollider.getLocation() : storedEnd;
            AABB fullQuery = beam.getBoundingBox().expandTowards(end.subtract(start)).inflate(1);
            AABB cappedQuery = beam.getBoundingBox().expandTowards(cappedEnd.subtract(start)).inflate(1);
            AABB targetBox = target == null ? null : target.getBoundingBox().inflate(target.getPickRadius());
            boolean shortenedEndpoint = fullCollider.getType() == HitResult.Type.BLOCK && fullOutline.getType() == HitResult.Type.BLOCK
                    && storedCollider.getType() == HitResult.Type.MISS && storedOutline.getType() == HitResult.Type.MISS
                    && storedEnd.equals(fullCollider.getLocation()) && storedEnd.equals(fullOutline.getLocation())
                    && beam.getLength() == 4.5F && start.distanceTo(fullCollider.getLocation()) == (double) beam.getLength();
            return new BeamFrame(level.getGameTime(), beam.tickCount, start, eyeStart, end, storedEnd, beam.getLength(),
                    shortenedEndpoint, fullQuery, candidates(fullQuery), cappedQuery, candidates(cappedQuery), targetBox,
                    targetBox == null ? null : targetBox.clip(start, end).orElse(null),
                    targetBox == null ? null : targetBox.clip(eyeStart, eyeStart.add(direction.scale(REQUESTED_LENGTH))).orElse(null),
                    target == null ? null : target.getBoundingBox().inflate(0.3D).clip(start, cappedEnd).orElse(null),
                    BlockRay.of(fullCollider), BlockRay.of(fullOutline), BlockRay.of(storedCollider), BlockRay.of(storedOutline),
                    target == null ? 0 : target.getHealth(), target == null ? 0 : target.getRemainingFireTicks(),
                    level.getMaxLocalRawBrightness(user.blockPosition()), level.isEmptyBlock(firePos));
        }

        private BlockHitResult clip(Vec3 start, Vec3 end, ClipContext.Block mode) {
            return level.clip(new ClipContext(start, end, mode, ClipContext.Fluid.NONE, beam));
        }

        private List<UUID> candidates(AABB query) {
            return level.getEntities(beam, query, entity -> entity != user && !entity.isSpectator() && entity.canBeHitByProjectile())
                    .stream().map(Entity::getUUID).toList();
        }

        private void requireRay(BeamFrame ray) {
            helper.assertTrue(near(ray.start, expectedRoot) && ray.start.y == expectedRoot.y
                            && near(ray.requestedEnd, expectedRoot.add(0, -REQUESTED_LENGTH, 0)) && ray.brightness > 9
                            && ray.fireCellEmpty && contained(ray.fullQuery) && contained(ray.blockCappedQuery),
                    "Aja requested ray escaped its owned ready column");
            if (scenario == Scenario.ENTITY) helper.assertTrue(ray.fullCollider.type == HitResult.Type.MISS
                            && ray.fullOutline.type == HitResult.Type.MISS && ray.storedCollider.type == HitResult.Type.MISS,
                    "Aja entity-only control has an unexpected collider");
            else {
                helper.assertTrue(ray.fullCollider.type == HitResult.Type.BLOCK && ray.fullOutline.type == HitResult.Type.BLOCK
                                && ray.fullCollider.block.equals(wallCenter) && ray.fullOutline.block.equals(wallCenter)
                                && ray.fullCollider.face == Direction.UP && ray.fullOutline.face == Direction.UP
                                && near(ray.fullCollider.point, ray.fullOutline.point)
                                && Math.abs(ray.start.distanceTo(ray.fullCollider.point) - 4.5D) < EPSILON
                                && level.getBlockState(wallCenter).isFaceSturdy(level, wallCenter, Direction.UP),
                        "Aja nominal wall ray lacks the exact supported UP-face boundary");
                boolean storedWallHit = ray.storedCollider.type == HitResult.Type.BLOCK && ray.storedOutline.type == HitResult.Type.BLOCK
                        && ray.storedCollider.block.equals(wallCenter) && ray.storedOutline.block.equals(wallCenter)
                        && ray.storedCollider.face == Direction.UP && ray.storedOutline.face == Direction.UP
                        && near(ray.fullCollider.point, ray.storedCollider.point) && near(ray.fullCollider.point, ray.storedOutline.point);
                // The current shoot() can end exactly on the wall; observe that strict MISS state before judging real impacts.
                helper.assertTrue(storedWallHit || ray.shortenedEndpoint, "Aja stored ray has neither the owned wall hit nor its exact shortened endpoint");
            }
            if (target == null) helper.assertTrue(ray.fullCandidates.isEmpty() && ray.blockCappedCandidates.isEmpty(),
                    "Aja block-only control has an entity candidate");
            else {
                helper.assertTrue(ray.fullCandidates.equals(List.of(target.getUUID())) && ray.fullTargetClip != null && ray.eyeTargetClip != null
                                && ray.health == targetInitialHealth && ray.targetFire <= 0,
                        "Aja full requested ray lacks its pristine eligible UV target");
                if (scenario == Scenario.MIXED) helper.assertTrue(target.getBoundingBox().maxY < wallCenter.getY()
                                && ray.fullCollider.point.distanceToSqr(ray.start) < ray.fullTargetClip.distanceToSqr(ray.start)
                                && ray.blockCappedCandidates.isEmpty() && ray.cappedTargetClip == null,
                        "Aja mixed target is not wholly beyond the independently capped wall ray");
                else helper.assertTrue(ray.blockCappedCandidates.equals(List.of(target.getUUID())) && ray.cappedTargetClip != null,
                        "Aja unobstructed positive target missed the stored ray");
            }
        }

        private boolean contained(AABB box) {
            return room.contains(new Vec3(box.minX, box.minY, box.minZ)) && room.contains(new Vec3(box.maxX, box.maxY, box.maxZ));
        }

        private void requireInputState() {
            helper.assertTrue(power.getPowerType() == null && PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).isEmpty()
                            && !user.isCreative() && !user.getAbilities().instabuild && !user.isShiftKeyDown()
                            && user.getYRot() == 0 && user.getXRot() == 90,
                    "Aja user lost NONE-power Survival input conditions");
        }

        private void requireActorsBeforeContact() {
            helper.assertTrue(user.isAlive() && near(user.position(), userPosition) && !user.isOnFire()
                            && level.isPositionEntityTicking(user.blockPosition())
                            && structural.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())),
                    "Aja actor or owned fixture structure changed");
            if (target != null) helper.assertTrue(target.isAlive() && near(target.position(), targetPosition) && !target.isInvulnerable()
                            && target.isNoAi() && !target.isOnFire() && target.getHealth() == targetInitialHealth
                            && target.canBeHitByProjectile() && JojoDefinitions.isUndeadOrVampiric(target)
                            && !WoodenCoffinBlock.isSleepingInCoffin(target)
                            && PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isEmpty()
                            && !target.hasEffect(ModStatusEffects.VAMPIRE_SUN_BURN)
                            && !level.canSeeSky(target.blockPosition()) && !level.canSeeSky(BlockPos.containing(target.getEyePosition()))
                            && level.isPositionEntityTicking(target.blockPosition()),
                    "Aja Husk lost actual shade, UV eligibility or pristine health");
        }

        private void beginUse() {
            requireActorsBeforeContact();
            requireInputState();
            helper.assertTrue(level.getMaxLocalRawBrightness(user.blockPosition()) > 9
                            && user.getMainHandItem().is(ModItems.AJA_STONE.get()) && user.getMainHandItem().getCount() == 1
                            && !user.isUsingItem() && !user.getCooldowns().isOnCooldown(ModItems.AJA_STONE.get())
                            && user.getMainHandItem().getUseDuration(user) == 20
                            && user.getEyePosition(1).add(0, -0.3D, 0).y == expectedRoot.y,
                    "Aja ordinary item use lacks its real light/stack/root prerequisites");
            usedStack = user.getMainHandItem();
            var result = usedStack.use(level, user, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction() && user.isUsingItem() && user.getUsedItemHand() == InteractionHand.MAIN_HAND
                            && user.getUseItemRemainingTicks() == 20 && beam == null && usedStack.getCount() == 1,
                    "Registered Aja use did not start an ordinary twenty-tick hold");
            used = true;
            log("use hand=MAIN_HAND power=NONE userTicks=" + userTicks + " targetTicks=" + targetTicks
                    + " duration=" + user.getUseItemRemainingTicks() + " brightness=" + level.getMaxLocalRawBrightness(user.blockPosition()));
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Aja item fixture watchdog expired");
                if (contact != null && completion != null && cooldownStep != null) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                if (!actorsReady) {
                    if (lightingReady()) {
                        if (target != null) helper.assertTrue(level.addFreshEntity(target), "Could not add the shaded Aja Husk");
                        actorsReady = true;
                    }
                }
                else if (!used && userTicks >= 2 && (target == null || targetTicks >= 2)) beginUse();
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            Contact hit = contact;
            log("result " + hit + " completion=" + completion + " nextCooldown=" + cooldownStep);
            helper.assertTrue(spawned.size() == 1 && holdTicks == 20 && completion.holdTicks == 20 && completion.consumedCount == 1
                            && beamTicks == 1 && hit.before.age == 1 && hit.removed && hit.removalReason == Entity.RemovalReason.DISCARDED,
                    "Aja real completion or one-natural-tick expiry changed");
            helper.assertTrue(cooldownStep.userTicks == completion.userTicks + 1 && completion.cooldownActive && cooldownStep.active
                            && Math.abs(completion.cooldown - cooldownStep.fraction - 0.01F) < 1.0E-5F,
                    "Aja did not retain its natural hundred-tick cooldown schedule");
            helper.assertTrue(hit.impacts.size() <= 1 && hit.impacts.stream().noneMatch(Impact::canceled)
                            && structural.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())),
                    "Aja contact was canceled, duplicated or destroyed the owned structure");
            if (scenario == Scenario.BLOCK) {
                helper.assertTrue(hit.impacts.size() == 1 && hit.impacts.get(0).type == HitResult.Type.BLOCK
                                && wallCenter.equals(hit.impacts.get(0).block) && hit.impacts.get(0).face == Direction.UP,
                        "Aja block-only ray did not produce its actual wall impact");
                helper.assertTrue(hit.attempts.isEmpty() && hit.fireCell.is(Blocks.FIRE)
                                && hit.fireCell.canSurvive(level, firePos) && level.getBlockState(wallCenter).is(Blocks.STONE),
                        "Aja block-only control lost real supported fire or changed the wall");
            }
            else {
                helper.assertTrue(hit.impacts.size() == 1 && hit.impacts.get(0).type == HitResult.Type.ENTITY
                                && target.getUUID().equals(hit.impacts.get(0).target),
                        "Aja full requested ray did not hit its eligible UV target");
                helper.assertTrue(hit.attempts.size() == 1, "Aja eligible target lacks its one real UV attempt");
                Attempt damage = hit.attempts.get(0);
                helper.assertTrue(damage.ultraviolet && !damage.canceled && damage.amount == DAMAGE
                                && hit.beam.equals(damage.direct) && user.getUUID().equals(damage.cause)
                                && damage.fireAtDispatch == 100 && damage.healthAtDispatch == hit.before.health
                                && Math.abs(hit.before.health - hit.health - DAMAGE) < EPSILON && hit.targetFire == 100,
                        "Aja lost UV10, ignition order, health change or beam/owner attribution");
                helper.assertTrue(hit.fireCell.isAir(), "Aja entity selection also placed wall fire");
            }
        }

        private float cooldown() { return user.getCooldowns().getCooldownPercent(ModItems.AJA_STONE.get(), 0); }
        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
        private static Impact impact(ProjectileImpactEvent event) {
            HitResult hit = event.getRayTraceResult();
            return new Impact(hit.getType(), hit instanceof EntityHitResult entity ? id(entity.getEntity()) : null,
                    hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null,
                    hit instanceof BlockHitResult block ? block.getDirection() : null, hit.getLocation(), event.isCanceled());
        }
        private static Attempt attempt(Incoming observed) {
            LivingIncomingDamageEvent event = observed.event;
            return new Attempt(event.getOriginalAmount(), event.getSource().is(ModDamageTypes.ULTRAVIOLET_ENTITY),
                    id(event.getSource().getDirectEntity()), id(event.getSource().getEntity()), event.isCanceled(),
                    observed.fireAtDispatch, observed.healthAtDispatch);
        }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Aja item collision observer failure: " + scenario, error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("AJA-ITEM-COLLISION {} {}", scenario, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            ownerTick = null;
            before = null;
            try {
                if (user != null) user.stopUsingItem();
                for (LightBeamEntity created : spawned) if (!created.isRemoved()) created.discard();
                if (target != null) target.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (user != null) {
                    user.getInventory().clearContent();
                    user.discard();
                }
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                log("cleanup listeners=0 beams=" + spawned.size() + " exactRestore=" + restored + " cells=" + original.size() + " drops=" + drops.size());
                if (!restored) throw new IllegalStateException("Aja fixture blocks were not exactly restored");
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
