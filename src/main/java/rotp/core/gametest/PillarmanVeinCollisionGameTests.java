package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanVeinEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanErraticBlazeKingAbility;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModEntityTypes;
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
public final class PillarmanVeinCollisionGameTests {
    private PillarmanVeinCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vein_mixed_collision", timeoutTicks = 100)
    public static void erraticMixedRayKeepsDonorEntityOnlySelection(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "vein_block_control", timeoutTicks = 100)
    public static void erraticBlockOnlyRayStillPlacesBoilingBlood(GameTestHelper helper) {
        start(helper, false);
    }

    private static void start(GameTestHelper helper, boolean withTarget) {
        Fixture fixture = new Fixture(helper, withTarget);
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

    private record Ray(long time, int age, Vec3 root, Vec3 tip, Vec3 delta, double distance,
            boolean retracting, BlockHitResult collider, BlockHitResult outline,
            boolean pane, boolean targetHit, boolean targetAlive, AABB targetBox,
            BlockPos placement, BlockState stateBefore, int priorBlockImpacts) {}

    private record Impact(String type, Vec3 position, BlockPos block, UUID target, boolean canceled) {}

    private record Contact(UUID vein, Ray before, Vec3 tipAfter, double distanceAfter,
            boolean retractingAfter, BlockState stateAfter, List<Impact> impacts) {
        boolean placedBlood() {
            return before.stateBefore.isAir() && stateAfter.is(ModBlocks.BOILING_BLOOD.get());
        }
    }

    private static final class Tracked {
        final PillarmanVeinEntity vein;
        final List<Impact> currentImpacts = new ArrayList<>();
        Ray before;
        Contact firstPane;
        int blockImpacts;

        Tracked(PillarmanVeinEntity vein) {
            this.vein = vein;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 22;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean withTarget;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Set<BlockPos> panes = new LinkedHashSet<>();
        private final Map<UUID, Tracked> veins = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player player;
        private IronGolem target;
        private PlayerPower power;
        private Ability ability;
        private EntityActionInstance action;
        private Vec3 playerPosition;
        private RuntimeException observerFailure;
        private int playerTicks;
        private boolean pressed;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean withTarget) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.withTarget = withTarget;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 4, y - 1, z + 2);
            BlockPos max = new BlockPos(x + 12, y + 4, z + 10);
            helper.assertTrue(y + 5 < level.getMaxBuildHeight(), "Vein fixture exceeds build height");
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Vein fixture contains an unrelated entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Vein sky fixture is obstructed at " + pos);
                original.put(pos.immutable(), level.getBlockState(pos));
            }
            // Contain fluid even on timeout, and keep the ordinary Pillarman out of direct sunlight.
            for (BlockPos pos : original.keySet()) {
                if (pos.getX() == min.getX() || pos.getX() == max.getX()
                        || pos.getY() == min.getY() || pos.getY() == max.getY()
                        || pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                }
            }
            BlockState pane = Blocks.GLASS_PANE.defaultBlockState()
                    .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
            for (int px = x + 6; px <= x + 10; px++) {
                for (int py = y; py <= y + 2; py++) {
                    BlockPos pos = new BlockPos(px, py, z + 6);
                    panes.add(pos);
                    level.setBlockAndUpdate(pos, pane);
                }
            }
            playerPosition = new Vec3(x + 8.5D, y, z + 4.0D);
            player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0);
            player.setYHeadRot(0);
            player.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(player), "Could not add the ordinary vein-test player");
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            PillarmanData data = PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, player);
            data.setMode(PillarmanMode.HEAT, player);
            data.setEnergy(player, 200.0F);
            ability = power.getAbility("pillarman_erratic_blaze_king");
            helper.assertTrue(ability instanceof PillarmanErraticBlazeKingAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_ERRATIC_BLAZE_KING.get(),
                    "Fixture did not resolve the registered Erratic Blaze King");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            if (withTarget) {
                target = EntityType.IRON_GOLEM.create(level);
                helper.assertTrue(target != null, "Could not create the living vein target");
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setPos(x + 8.5D, y, z + 7.55D);
                helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable(),
                        "Vein target is not an ordinary living entity");
            }
            helper.assertTrue(EventHooks.canEntityGrief(level, player), "Vein block side effects are vetoed by this world");
            registerObservers();
            log("setup chunk=" + chunk + " player=" + player.getUUID() + " pos=" + playerPosition
                    + " target=" + (target == null ? "none" : target.getUUID() + " " + target.getBoundingBox())
                    + " paneZ=" + (z + 6) + " savedBlocks=" + original.size());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof PillarmanVeinEntity vein && vein.getOwner() == player) {
                    if (event.isCanceled()) throw new IllegalStateException("Production vein join was canceled");
                    veins.put(vein.getUUID(), new Tracked(vein));
                    CompoundTag nbt = vein.saveWithoutId(new CompoundTag());
                    log("spawn uuid=" + vein.getUUID() + " L=" + vein.ticksLifespan() + " F=" + vein.getSpeedFactor()
                            + " originOffsets=" + new Vec3(nbt.getDouble("XOriginOffset"), nbt.getDouble("YOriginOffset"), nbt.getDouble("ZOriginOffset"))
                            + " angles=" + nbt.getFloat("XRotOffset") + "," + nbt.getFloat("YRotOffset"));
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = veins.get(event.getEntity().getUUID());
                if (tracked != null) {
                    if (event.isCanceled()) throw new IllegalStateException("Natural vein tick was canceled");
                    tracked.currentImpacts.clear();
                    tracked.before = ray(tracked);
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Tracked tracked = veins.get(event.getProjectile().getUUID());
                if (tracked != null) {
                    HitResult hit = event.getRayTraceResult();
                    if (hit.getType() == HitResult.Type.BLOCK) tracked.blockImpacts++;
                    tracked.currentImpacts.add(new Impact(hit.getType().name(), hit.getLocation(),
                            hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null,
                            hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null, event.isCanceled()));
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == player) playerTicks++;
                Tracked tracked = veins.get(event.getEntity().getUUID());
                if (tracked != null) {
                    Ray before = tracked.before;
                    if (before == null || before.age != tracked.vein.tickCount || before.time != level.getGameTime()) {
                        throw new IllegalStateException("Missing same-tick vein pre/post pair");
                    }
                    if (before.pane && tracked.firstPane == null) {
                        CompoundTag after = tracked.vein.saveWithoutId(new CompoundTag());
                        tracked.firstPane = new Contact(tracked.vein.getUUID(), before, tracked.vein.position(),
                                after.getDouble("Distance"), after.getBoolean("IsRetracting"),
                                level.getBlockState(before.placement), List.copyOf(tracked.currentImpacts));
                        logContact(tracked.firstPane);
                    }
                    else if (!tracked.currentImpacts.isEmpty() && tracked.firstPane == null) {
                        log("unexpected-early-impact uuid=" + tracked.vein.getUUID() + " age=" + before.age
                                + " root=" + before.root + " end=" + before.tip.add(before.delta) + " impacts=" + tracked.currentImpacts);
                    }
                    tracked.before = null;
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Ray ray(Tracked tracked) {
            PillarmanVeinEntity vein = tracked.vein;
            if (!level.isPositionEntityTicking(vein.blockPosition()) || vein.getOwner() != player) {
                throw new IllegalStateException("Vein left its ticking fixture or changed owner");
            }
            Vec3 root = vein.getOriginPoint(1.0F);
            Vec3 tip = vein.position();
            Vec3 delta = vein.getDeltaMovement();
            Vec3 end = tip.add(delta);
            BlockHitResult collider = level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vein));
            BlockHitResult outline = level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, vein));
            boolean hitsPane = collider.getType() == HitResult.Type.BLOCK && panes.contains(collider.getBlockPos());
            AABB targetBox = target == null ? null : target.getBoundingBox().inflate(target.getPickRadius() + vein.getBbWidth() / 2.0D);
            boolean targetHit = targetBox != null && (targetBox.contains(root) || targetBox.clip(root, end).isPresent());
            BlockPos placement = hitsPane ? collider.getBlockPos().relative(collider.getDirection()).immutable() : null;
            CompoundTag nbt = vein.saveWithoutId(new CompoundTag());
            return new Ray(level.getGameTime(), vein.tickCount, root, tip, delta, nbt.getDouble("Distance"),
                    nbt.getBoolean("IsRetracting"), collider, outline, hitsPane, targetHit,
                    target != null && target.isAlive(), targetBox, placement,
                    placement == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(placement), tracked.blockImpacts);
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Vein fixture observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Vein fixture watchdog: playerTicks=" + playerTicks
                        + " actionPhase=" + (action == null ? "none" : action.getPhase() + "/" + action.getPhaseTick())
                        + " spawned=" + veins.size() + " contacts=" + veins.values().stream().filter(v -> v.firstPane != null).count());
                helper.assertTrue(player.isAlive() && player.position().distanceToSqr(playerPosition) < 1.0E-6D,
                        "Vein fixture player died or moved before collision observation");
                if (!pressed && playerTicks >= 2) press();
                if (veins.size() == 10 && veins.values().stream().allMatch(v -> v.firstPane != null)) {
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
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition())
                            && (target == null || level.isPositionEntityTicking(target.blockPosition())),
                    "Vein player or target is not in an entity-ticking chunk");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.CLICK),
                    "Registered Erratic CLICK failed its normal admission check");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.CLICK,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof PillarmanErraticBlazeKingAbility.ErraticBlazeKingInstance shot
                            && shot == LivingComponentAction.getComponent(player).getAction() && shot.ability == ability,
                    "Registered Erratic CLICK did not install its real player action");
            action = (EntityActionInstance) input.action;
            log("press ability=" + ability.getAbilityId() + " playerPostTicks=" + playerTicks + " entityTicking=true");
        }

        private void validate() {
            List<Contact> contacts = veins.values().stream().map(v -> v.firstPane).toList();
            List<Contact> placements = contacts.stream().filter(Contact::placedBlood).toList();
            log("result spawned=" + veins.size() + " geometricPaneContacts=" + contacts.size()
                    + " geometricMixed=" + contacts.stream().filter(c -> c.before.targetHit).count()
                    + " attributedPlacements=" + placements.size() + " playerPostTicks=" + playerTicks);
            helper.assertTrue(veins.values().stream().allMatch(v -> v.vein.getType() == ModEntityTypes.PILLAR_MAN_VEINS.get()
                            && v.vein.getOwner() == player), "Unexpected vein type or owner");
            for (Contact contact : contacts) {
                Ray ray = contact.before;
                helper.assertTrue(ray.age >= 2 && ray.priorBlockImpacts == 0 && !ray.retracting,
                        "Vein reached the pane after an earlier block hit or retraction: " + contact.vein);
                helper.assertTrue(ray.outline.getType() == HitResult.Type.BLOCK
                                && ray.outline.getBlockPos().equals(ray.collider.getBlockPos())
                                && ray.outline.getDirection() == ray.collider.getDirection()
                                && original.containsKey(ray.placement),
                        "Vein collider/outline do not independently select the same owned pane");
                helper.assertTrue(contact.impacts.stream().noneMatch(Impact::canceled), "Vein impact was canceled");
                if (withTarget) {
                    helper.assertTrue(ray.targetHit && ray.targetAlive,
                            "Mixed fixture ray did not independently intersect the living target: " + contact.vein);
                    helper.assertTrue(contact.impacts.stream().anyMatch(hit -> target.getUUID().equals(hit.target)),
                            "Geometrically intersected target had no real vein impact: " + contact.vein);
                }
                else {
                    helper.assertTrue(contact.impacts.stream().noneMatch(hit -> hit.target != null)
                                    && contact.impacts.stream().anyMatch(hit -> ray.collider.getBlockPos().equals(hit.block)),
                            "No-target control did not deliver its ordinary pane-only impact");
                }
            }
            if (withTarget) {
                // Geometry above remains required when donor entity-only selection removes the block event.
                helper.assertTrue(placements.isEmpty(), "Donor mixed-ray entity selection must not place blood; first attributed vein="
                        + (placements.isEmpty() ? "none" : placements.get(0).vein + " age=" + placements.get(0).before.age
                                + " cell=" + placements.get(0).before.placement + " before=" + placements.get(0).before.stateBefore
                                + " after=" + placements.get(0).stateAfter));
                helper.assertTrue(contacts.stream().allMatch(contact -> contact.impacts.stream().noneMatch(hit -> "BLOCK".equals(hit.type))),
                        "Donor mixed-ray selection retained a block impact alongside an entity impact");
            }
            else {
                helper.assertTrue(!placements.isEmpty(), "No-target control never placed boiling blood from an initially empty cell");
            }
        }

        private void logContact(Contact contact) {
            Ray ray = contact.before;
            log("contact uuid=" + contact.vein + " time=" + ray.time + " age=" + ray.age
                    + " preRoot=" + ray.root + " preTip=" + ray.tip + " preDelta=" + ray.delta
                    + " preDistance=" + ray.distance + " preRetracting=" + ray.retracting
                    + " pane=" + ray.collider.getBlockPos() + " face=" + ray.collider.getDirection()
                    + " targetBox=" + ray.targetBox + " targetHit=" + ray.targetHit + " priorBlockImpacts=" + ray.priorBlockImpacts
                    + " cell=" + ray.placement + " before=" + ray.stateBefore + " after=" + contact.stateAfter
                    + " postTip=" + contact.tipAfter + " postDistance=" + contact.distanceAfter
                    + " postRetracting=" + contact.retractingAfter + " impacts=" + contact.impacts);
        }

        private void log(String message) {
            JojoMod.LOGGER.info("VEIN-COLLISION {} {}", withTarget ? "mixed" : "control", message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (player != null) AbilityInput.keyRelease(KEY, player);
            }
            finally {
                try {
                    for (Tracked tracked : veins.values()) if (!tracked.vein.isRemoved()) tracked.vein.discard();
                    if (target != null) target.discard();
                    if (player != null) player.discard();
                }
                finally {
                    for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                    log("cleanup listeners=0 restored=" + restored + " savedBlocks=" + original.size());
                    if (!restored) throw new IllegalStateException("Vein fixture blocks were not exactly restored");
                }
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
