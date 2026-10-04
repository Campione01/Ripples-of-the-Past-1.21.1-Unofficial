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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonScarfCollisionGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final Vec3 FIRST_TIP = sweepTip(1.5D, -54.0D);
    private static final Vec3 SECOND_TIP = sweepTip(3.0D, -40.5D);

    private HamonScarfCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_scarf_mixed_refusal", timeoutTicks = 80)
    public static void refusedMixedScarfRayKeepsExtendingWithoutBlockSideEffect(GameTestHelper helper) {
        start(helper, Scenario.MIXED_REFUSED);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_scarf_block_only", timeoutTicks = 80)
    public static void blockOnlyScarfRayStillRetractsNormally(GameTestHelper helper) {
        start(helper, Scenario.BLOCK_ONLY);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_scarf_accepted_hits", timeoutTicks = 80)
    public static void entityOnlyScarfDamagesTwoTargetsAndTrainsOnce(GameTestHelper helper) {
        start(helper, Scenario.ENTITY_ACCEPTED);
    }

    private enum Scenario { MIXED_REFUSED, BLOCK_ONLY, ENTITY_ACCEPTED }

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

    private static Vec3 sweepTip(double distance, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw) * distance, 0.0D, Math.cos(yaw) * distance);
    }

    private record BlockRay(HitResult.Type type, BlockPos block, Direction face, Vec3 point) {
        static BlockRay of(BlockHitResult hit) {
            return new BlockRay(hit.getType(), hit.getBlockPos().immutable(), hit.getDirection(), hit.getLocation());
        }
    }

    private record Frame(long time, int age, Vec3 root, Vec3 tip, Vec3 delta, double distance,
            boolean forward, boolean retracting, boolean pointsGiven, double points, Map<UUID, Float> health,
            AABB query, List<UUID> queried, Map<UUID, AABB> inflatedTargets, Map<UUID, Vec3> intersections,
            BlockRay collider, BlockRay outline, int priorBlockImpacts) {}

    private record Impact(String type, UUID target, BlockPos block, Vec3 point, boolean canceled) {}

    private record Attempt(UUID target, float amount, UUID direct, UUID cause, LivingIncomingDamageEvent event) {
        String summary() {
            return target + ":hamon=" + amount + "/direct=" + direct + "/cause=" + cause + "/canceled=" + event.isCanceled();
        }
    }

    private record Contact(Frame before, Vec3 tip, Vec3 delta, double distance, boolean retracting,
            boolean removed, boolean pointsGiven, double points, Map<UUID, Float> health,
            List<Impact> impacts, List<Attempt> attempts) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Scenario scenario;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<Cow> targets = new ArrayList<>();
        private final List<SatiporojaScarfEntity> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Impact> impacts = new ArrayList<>();
        private final List<Attempt> attempts = new ArrayList<>();
        private final List<Contact> contacts = new ArrayList<>();
        private Player user;
        private HamonData hamon;
        private SatiporojaScarfEntity scarf;
        private Vec3 userPosition;
        private AABB room;
        private Frame before;
        private Contact firstContact;
        private RuntimeException observerFailure;
        private int userTicks;
        private int scarfTicks;
        private int blockImpacts;
        private double initialPoints;
        private boolean usingItem;
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
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX(), y - 2, chunk.getMinBlockZ());
            BlockPos max = new BlockPos(chunk.getMaxBlockX(), y + 5, chunk.getMaxBlockZ());
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(max.getY() < level.getMaxBuildHeight(), "Scarf fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) helper.assertTrue(level.isEmptyBlock(pos), "Scarf sky fixture is obstructed");
            helper.assertTrue(level.getEntities((Entity) null, room).isEmpty(), "Scarf fixture contains another entity");
            if (scenario != Scenario.ENTITY_ACCEPTED) {
                BlockState pane = Blocks.GLASS_PANE.defaultBlockState()
                        .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
                for (int x = 6; x <= 8; x++) {
                    BlockPos pos = new BlockPos(chunk.getMinBlockX() + x, y + 1, chunk.getMinBlockZ() + 6);
                    original.put(pos, level.getBlockState(pos));
                    helper.assertTrue(level.setBlockAndUpdate(pos, pane), "Could not place owned scarf pane");
                }
            }
            userPosition = new Vec3(chunk.getMinBlockX() + 6.5D, y, chunk.getMinBlockZ() + 5.5D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            helper.assertTrue(level.addFreshEntity(user), "Could not add scarf user");
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.SATIPOROJA_SCARF.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())
                            && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                            && HamonAbilityHelpers.configHamonDamageMultiplier() > 0
                            && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "Scarf skill, damage or training configuration is invalid");
            if (scenario != Scenario.BLOCK_ONLY) {
                addTarget(userPosition.add(sweepTip(2.7D, -54.0D)));
                if (scenario == Scenario.ENTITY_ACCEPTED) addTarget(userPosition.add(SECOND_TIP.scale(2.0D).subtract(FIRST_TIP).scale(0.8D)));
            }
            registerObservers();
            log("setup user=" + user.getUUID() + " pos=" + userPosition + " panes=" + original.keySet()
                    + " targets=" + targets.stream().map(t -> t.getUUID() + ":" + t.getBoundingBox()).toList()
                    + " noGravity=true targetAI=true speed=0");
        }

        private void addTarget(Vec3 position) {
            Cow target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create scarf target");
            target.setNoGravity(true);
            target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            target.setPos(position);
            targets.add(target);
            helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isNoAi() && !target.isInvulnerable()
                            && !EntityHamonChargeState.get(target).hasHamonCharge()
                            && HamonAbilityHelpers.hamonDamageAmount(target, 0.6F) > 0,
                    "Scarf target is not an ordinary vulnerable Cow");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (event.getLevel() != level) return;
                if (!closed && event.getEntity() instanceof ItemEntity item && room.contains(item.position())) drops.add(item);
                if (!(event.getEntity() instanceof SatiporojaScarfEntity created) || created.getOwner() != user) return;
                if (!closed) spawned.add(created);
                observe(() -> {
                    helper.assertTrue(usingItem && scarf == null && spawned.size() == 1 && !event.isCanceled(), "Unexpected scarf spawn");
                    scarf = created;
                    CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
                    helper.assertTrue(scarf.getType() == ModEntityTypes.SATIPOROJA_SCARF.get() && scarf.ticksLifespan() == 10
                                    && scarf.getSpeedFactor() == 1.0D && !nbt.getBoolean("LeftArm") && !nbt.getBoolean("PointsGiven"),
                            "Registered scarf has wrong owner, side, lifetime or training state");
                    log("spawn uuid=" + scarf.getUUID() + " root=" + scarf.getOriginPoint(1.0F) + " tip=" + scarf.position()
                            + " life=" + scarf.ticksLifespan() + " width=" + scarf.getBbWidth());
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != scarf) return;
                helper.assertTrue(!event.isCanceled() && before == null && scarf.getOwner() == user
                                && level.isPositionEntityTicking(scarf.blockPosition()), "Scarf natural tick/owner is invalid");
                before = frame();
                impacts.clear();
                attempts.clear();
                helper.assertTrue(near(before.root, user.getEyePosition(1.0F).add(0, -0.3D, 0)), "Scarf root is not the authored eye offset");
                if (before.age == 2) validateFirstRay(before);
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() != scarf) return;
                helper.assertTrue(before != null && !event.isCanceled(), "Scarf impact lacks a natural ray or was canceled");
                HitResult hit = event.getRayTraceResult();
                UUID target = hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null;
                BlockPos block = hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos().immutable() : null;
                helper.assertTrue(target != null ? before.queried.contains(target) && before.intersections.containsKey(target)
                                : block != null && original.containsKey(block), "Scarf impact escaped its independently checked fixture");
                impacts.add(new Impact(hit.getType().name(), target, block, hit.getLocation(), event.isCanceled()));
                if (block != null) blockImpacts++;
            });
            Consumer<LivingIncomingDamageEvent> damage = event -> observe(() -> {
                if (!targets.contains(event.getEntity())) return;
                UUID target = event.getEntity().getUUID();
                helper.assertTrue(before != null && impacts.stream().anyMatch(hit -> target.equals(hit.target))
                                && event.getSource().is(ModDamageTypes.HAMON) && event.getOriginalAmount() > 0
                                && event.getSource().getEntity() == user && !event.isCanceled(), "Damage was not an eligible owned scarf Hamon attempt");
                if (scenario == Scenario.MIXED_REFUSED) event.setCanceled(true);
                attempts.add(new Attempt(target, event.getOriginalAmount(), id(event.getSource().getDirectEntity()),
                        id(event.getSource().getEntity()), event));
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                if (event.getEntity() != scarf) return;
                helper.assertTrue(before != null && before.age == scarf.tickCount && before.time == level.getGameTime(), "Missing scarf Pre/Post pair");
                scarfTicks++;
                CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
                Contact contact = new Contact(before, scarf.position(), scarf.getDeltaMovement(), nbt.getDouble("Distance"),
                        nbt.getBoolean("IsRetracting"), scarf.isRemoved(), nbt.getBoolean("PointsGiven"), points(), health(),
                        List.copyOf(impacts), List.copyOf(attempts));
                contacts.add(contact);
                if (before.age == 1) {
                    helper.assertTrue(impacts.isEmpty() && before.delta.lengthSqr() < EPSILON && near(before.tip, before.root)
                                    && contact.tip.distanceTo(before.root.add(FIRST_TIP)) < 2.0E-4D
                                    && Math.abs(contact.distance - 1.5D) < EPSILON && !contact.retracting,
                            "Scarf first natural step differs from the fixed right-arm sweep");
                }
                if (before.age == 2) firstContact = contact;
                if (!impacts.isEmpty() || !attempts.isEmpty() || before.age == 2 || contact.removed) logContact(contact);
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
            Vec3 root = scarf.getOriginPoint(1.0F);
            Vec3 tip = scarf.position();
            Vec3 delta = scarf.getDeltaMovement();
            Vec3 end = tip.add(delta);
            AABB query = scarf.getBoundingBox().expandTowards(root.subtract(end)).inflate(1.0D);
            List<UUID> queried = level.getEntities(scarf, query, targets::contains).stream().map(Entity::getUUID).toList();
            Map<UUID, AABB> boxes = new LinkedHashMap<>();
            Map<UUID, Vec3> intersections = new LinkedHashMap<>();
            for (Cow target : targets) {
                AABB box = target.getBoundingBox().inflate(target.getPickRadius() + scarf.getBbWidth() / 2.0D);
                boxes.put(target.getUUID(), box);
                if (box.contains(root)) intersections.put(target.getUUID(), root);
                else box.clip(root, end).ifPresent(point -> intersections.put(target.getUUID(), point));
            }
            CompoundTag nbt = scarf.saveWithoutId(new CompoundTag());
            return new Frame(level.getGameTime(), scarf.tickCount, root, tip, delta, nbt.getDouble("Distance"),
                    nbt.getBoolean("IsMovingForward"), nbt.getBoolean("IsRetracting"), nbt.getBoolean("PointsGiven"), points(), health(),
                    query, queried, Map.copyOf(boxes), Map.copyOf(intersections),
                    BlockRay.of(level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, scarf))),
                    BlockRay.of(level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, scarf))), blockImpacts);
        }

        private void validateFirstRay(Frame frame) {
            helper.assertTrue(frame.age == 2 && frame.age < 5 && frame.forward && !frame.retracting && frame.priorBlockImpacts == 0
                            && !frame.pointsGiven && Math.abs(frame.distance - 1.5D) < EPSILON
                            && frame.tip.add(frame.delta).distanceTo(frame.root.add(FIRST_TIP.scale(2.0D))) < 4.0E-4D,
                    "Scarf missed the first nonzero pre-turn discriminator ray");
            if (scenario != Scenario.ENTITY_ACCEPTED) {
                helper.assertTrue(frame.collider.type == HitResult.Type.BLOCK && frame.outline.type == HitResult.Type.BLOCK
                                && original.containsKey(frame.collider.block) && frame.collider.block.equals(frame.outline.block)
                                && frame.collider.face == frame.outline.face
                                && frame.collider.point.distanceTo(frame.outline.point) < EPSILON
                                && level.getBlockState(frame.collider.block).getDestroySpeed(level, frame.collider.block) > 0,
                        "COLLIDER/OUTLINE did not select the same owned nonzero-hardness pane");
            }
            else helper.assertTrue(frame.collider.type == HitResult.Type.MISS && frame.outline.type == HitResult.Type.MISS,
                    "Entity-only scarf ray found a block");
            if (scenario == Scenario.BLOCK_ONLY) {
                helper.assertTrue(frame.queried.isEmpty() && frame.intersections.isEmpty(), "Block-only scarf fixture contains a target");
            }
            else {
                UUID primary = targets.get(0).getUUID();
                helper.assertTrue(frame.queried.contains(primary) && frame.intersections.containsKey(primary),
                        "Scarf target is not in both the real query volume and ray");
                if (scenario == Scenario.MIXED_REFUSED) helper.assertTrue(
                        frame.collider.point.distanceToSqr(frame.root) < frame.intersections.get(primary).distanceToSqr(frame.root),
                        "Mixed scarf pane is not before the target");
                if (scenario == Scenario.ENTITY_ACCEPTED) helper.assertTrue(!frame.intersections.containsKey(targets.get(1).getUUID()),
                        "Second accepted target entered the first ray too soon");
            }
        }

        private void useItem() {
            helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()) && user.getMainArm() == HumanoidArm.RIGHT
                            && !user.isCreative() && !user.getAbilities().instabuild && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && user.getMainHandItem().is(ModItems.SATIPOROJA_SCARF.get()) && user.getMainHandItem().getCount() == 1
                            && !user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()) && hamon.getEnergy() >= 600.0F,
                    "Scarf use lacks its fixed-hand Survival/resource preconditions");
            for (Cow target : targets) {
                helper.assertTrue(level.isPositionEntityTicking(target.blockPosition()) && target.isAlive() && !target.isInvulnerable()
                                && HamonAbilityHelpers.hamonDamageAmount(target, 0.6F) > 0, "Scarf target is not naturally ticking/eligible");
                for (BlockPos pos : original.keySet()) helper.assertTrue(
                        !level.getBlockState(pos).getCollisionShape(level, pos).bounds().move(pos).intersects(target.getBoundingBox()),
                        "Scarf target overlaps an owned pane");
            }
            initialPoints = points();
            float energyBefore = hamon.getEnergy();
            usingItem = true;
            var result = user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND);
            usingItem = false;
            helper.assertTrue(result.getResult().consumesAction() && scarf != null
                            && Math.abs(energyBefore - hamon.getEnergy() - 600.0F) < 0.001F
                            && user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()), "Registered scarf use did not pay/spawn/cool down");
            used = true;
            log("use item=" + ModItems.SATIPOROJA_SCARF.getId() + " hand=MAIN_HAND arm=RIGHT energy=" + energyBefore + "->" + hamon.getEnergy()
                    + " points=" + initialPoints + " naturalUserTicks=" + userTicks);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 60, "Scarf watchdog userTicks=" + userTicks + " scarfTicks=" + scarfTicks);
                helper.assertTrue(user.isAlive() && near(user.position(), userPosition) && user.getYRot() == 0.0F && user.getXRot() == 0.0F,
                        "Scarf owner moved, turned or died");
                if (!used && userTicks >= 2) useItem();
                if (firstContact != null && (scenario != Scenario.ENTITY_ACCEPTED || scarf.isRemoved())) {
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
            Contact first = firstContact;
            helper.assertTrue(first.before.age == 2 && first.impacts.stream().noneMatch(Impact::canceled)
                            && original.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.GLASS_PANE))
                            && drops.isEmpty(), "Scarf contact or owned pane state is invalid");
            if (scenario == Scenario.BLOCK_ONLY) {
                helper.assertTrue(first.impacts.stream().anyMatch(hit -> "BLOCK".equals(hit.type)) && first.attempts.isEmpty()
                                && first.retracting && (first.removed || first.distance < first.before.distance)
                                && Math.abs(first.points - initialPoints) < EPSILON && !first.pointsGiven,
                        "Block-only scarf lost ordinary retraction or trained without a target");
            }
            else if (scenario == Scenario.MIXED_REFUSED) {
                UUID target = targets.get(0).getUUID();
                helper.assertTrue(first.impacts.stream().anyMatch(hit -> target.equals(hit.target))
                                && first.attempts.stream().anyMatch(a -> target.equals(a.target) && a.event.isCanceled())
                                && first.health.equals(first.before.health) && Math.abs(first.points - first.before.points) < EPSILON
                                && Math.abs(first.points - initialPoints) < EPSILON && !first.pointsGiven,
                        "Mixed scarf refusal did not preserve actual health/training");
                helper.assertTrue(first.impacts.stream().noneMatch(hit -> "BLOCK".equals(hit.type))
                                && !first.retracting && !first.removed && first.distance > first.before.distance,
                        "Mixed entity ray applied a block side effect: retract=" + first.retracting + " removed=" + first.removed
                                + " distance=" + first.before.distance + "->" + first.distance);
            }
            else {
                helper.assertTrue(scarf.getRemovalReason() == Entity.RemovalReason.DISCARDED && scarfTicks > 3 && blockImpacts == 0,
                        "Entity-only scarf did not complete its natural sweep");
                for (Cow target : targets) {
                    UUID id = target.getUUID();
                    helper.assertTrue(contacts.stream().anyMatch(c -> c.health.get(id) < c.before.health.get(id)
                                    && c.attempts.stream().anyMatch(a -> id.equals(a.target) && !a.event.isCanceled())),
                            "Accepted scarf control did not damage both natural targets");
                }
                List<Contact> awards = contacts.stream().filter(c -> c.points - c.before.points > EPSILON).toList();
                helper.assertTrue(awards.size() == 1 && awards.get(0).before.age == 2 && !awards.get(0).before.pointsGiven
                                && Math.abs(awards.get(0).before.points - initialPoints) < EPSILON
                                && awards.get(0).pointsGiven && Math.abs(points() - awards.get(0).points) < EPSILON,
                        "Accepted scarf did not award training exactly once");
            }
            log("result firstAge=" + first.before.age + " firstDistance=" + first.before.distance + "->" + first.distance
                    + " firstRetract=" + first.retracting + " firstRemoved=" + first.removed + " totalTraining=" + (points() - initialPoints));
        }

        private double points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }

        private Map<UUID, Float> health() {
            Map<UUID, Float> health = new LinkedHashMap<>();
            for (Cow target : targets) health.put(target.getUUID(), target.getHealth());
            return Map.copyOf(health);
        }

        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }

        private void logContact(Contact contact) {
            log("contact scarf=" + scarf.getUUID() + " pre=" + contact.before + " postTip=" + contact.tip + " postDelta=" + contact.delta
                    + " postDistance=" + contact.distance + " postRetract=" + contact.retracting + " removed=" + contact.removed
                    + " pointsGiven=" + contact.pointsGiven + " healthAfter=" + contact.health + " pointsAfter=" + contact.points
                    + " impacts=" + contact.impacts + " attempts=" + contact.attempts.stream().map(Attempt::summary).toList());
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Hamon scarf collision observer failure: " + scenario, error);
            }
        }

        private void log(String message) { JojoMod.LOGGER.info("HAMON-SCARF-COLLISION {} {}", scenario, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            before = null;
            try {
                if (user != null) user.stopUsingItem();
                for (SatiporojaScarfEntity created : spawned) if (!created.isRemoved()) created.discard();
                for (Cow target : targets) target.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (user != null) user.discard();
            }
            finally {
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = original.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                log("cleanup listeners=0 exactRestore=" + restored + " cells=" + original.size() + " itemDrops=" + drops.size());
                if (!restored) throw new IllegalStateException("Scarf fixture blocks were not exactly restored");
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
