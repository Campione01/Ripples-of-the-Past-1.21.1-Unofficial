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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import rotp.core.impl.powers.vampirism.VampirismUtil;
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
public final class PillarmanRibCollisionGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final double SPEED = (double) (8.0F / 21.0F);
    private static final int NATURAL_TURN_TICK = 10;
    private static final List<Vec3> ORIGINS = List.of(
            new Vec3(-0.18D, -0.50D, 0), new Vec3(-0.22D, -0.60D, 0),
            new Vec3(-0.22D, -0.70D, 0), new Vec3(-0.18D, -0.80D, 0),
            new Vec3(0.18D, -0.50D, 0), new Vec3(0.22D, -0.65D, 0),
            new Vec3(0.22D, -0.85D, 0), new Vec3(0.18D, -0.95D, 0));

    private PillarmanRibCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "rib_refused_mixed_collision", timeoutTicks = 100)
    public static void ribsRefusedTargetKeepsAllEightMixedRaysEntityOnly(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "rib_block_collision_control", timeoutTicks = 100)
    public static void ribsBlockOnlyRaysRetractAtAllEightUnbrokenPaneContacts(GameTestHelper helper) {
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
            boolean retracting, boolean attached, BlockHitResult collider, BlockHitResult outline,
            boolean pane, AABB candidates, AABB targetBox, Vec3 targetClip, boolean targetCandidate,
            float targetHealth, BlockState paneBefore, float paneHardness, int priorImpacts) {}

    private record Impact(String type, Vec3 position, BlockPos block, UUID target, boolean canceled) {}
    private record Contact(Ray before, Vec3 tipAfter, double distanceAfter, boolean retractingAfter,
            boolean attachedAfter, int lifespanAfter, float targetHealthAfter, BlockState paneAfter,
            List<BlockPos> changedPanes, List<Impact> impacts) {}

    private static final class Tracked {
        final PillarmanRibEntity rib;
        final Vec3 origin;
        final float pitch;
        final float yaw;
        final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        Ray before;
        Contact firstPane;
        int priorImpacts;
        int freeFlightSamples;

        Tracked(PillarmanRibEntity rib) {
            this.rib = rib;
            CompoundTag nbt = rib.saveWithoutId(new CompoundTag());
            origin = new Vec3(nbt.getDouble("XOriginOffset"), nbt.getDouble("YOriginOffset"), 0);
            pitch = nbt.getFloat("XRotOffset");
            yaw = nbt.getFloat("YRotOffset");
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 35;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean withTarget;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> panes = new LinkedHashMap<>();
        private final Map<UUID, Tracked> ribs = new LinkedHashMap<>();
        private final List<ItemEntity> blockDrops = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player player;
        private IronGolem target;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action;
        private EntityActionInstance spawnAction;
        private Vec3 playerPosition;
        private Vec3 targetPosition;
        private RuntimeException observerFailure;
        private int playerTicks;
        private float targetHealth;
        private float energyBeforePress;
        private float energyBeforePlayerTick;
        private float volleyEnergy;
        private boolean pressed;
        private boolean pressInProgress;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean withTarget) {
            this.helper = helper;
            level = helper.getLevel();
            this.withTarget = withTarget;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            // The restored constructor-eye delta must not hit the fixture floor on tick two.
            BlockPos min = new BlockPos(x + 4, y - 3, z + 2);
            BlockPos max = new BlockPos(x + 12, y + 4, z + 10);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && y + 5 < level.getMaxBuildHeight(),
                    "Rib basin exceeds build height");
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Rib basin contains an unrelated entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Rib sky basin is obstructed at " + pos);
                original.put(pos.immutable(), level.getBlockState(pos));
            }
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
                    BlockPos pos = new BlockPos(px, py, z + 7);
                    panes.put(pos, pane);
                    level.setBlockAndUpdate(pos, pane);
                }
            }
            panes.replaceAll((pos, ignored) -> level.getBlockState(pos));
            playerPosition = new Vec3(x + 8.5D, y, z + 4.10D);
            player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0);
            player.setYHeadRot(0);
            player.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(player), "Could not add the rib-test player");
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, player);
            data.setMode(PillarmanMode.NONE, player);
            data.setEnergy(player, 300.0F);
            ability = power.getAbility("pillarman_ribs_blades");
            helper.assertTrue(ability instanceof PillarmanRibsBladesAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_RIBS_BLADES.get(),
                    "Fixture did not resolve the registered Ribs Blades");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            if (withTarget) {
                target = EntityType.IRON_GOLEM.create(level);
                helper.assertTrue(target != null, "Could not create the refused-hit rib target");
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setInvulnerable(true);
                targetPosition = new Vec3(x + 8.5D, y, z + 8.35D);
                target.setPos(targetPosition);
                helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && target.isInvulnerable()
                                && !target.isSpectator() && target.isPickable(),
                        "Rib refused-hit target is not an ordinary invulnerable living entity");
                // Actual impact events prove eligibility; vanilla canAttack is not the legacy projectile filter.
                targetHealth = target.getHealth();
            }
            registerObservers();
            log("setup chunk=" + chunk + " owner=" + player.getUUID() + " pos=" + playerPosition
                    + " target=" + (target == null ? "none" : target.getUUID() + " " + target.getBoundingBox())
                    + " paneZ=" + (z + 7) + " floorTop=" + (y - 2) + " savedBlocks=" + original.size());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level) return;
                if (joinEvent.getEntity() instanceof PillarmanRibEntity rib && rib.getOwner() == player) {
                    Tracked tracked = new Tracked(rib);
                    Tracked previous = ribs.putIfAbsent(rib.getUUID(), tracked);
                    observe(() -> {
                        EntityActionInstance live = LivingComponentAction.getCurEntityAction(player);
                        helper.assertTrue(!joinEvent.isCanceled() && pressed && previous == null && ribs.size() <= 8
                                        && live instanceof PillarmanRibsBladesAbility.RibsBladesInstance
                                        && live.ability == ability && live.getPhase() == ActionPhase.PERFORM,
                                "Rib was not emitted by the single live registered perform action");
                        helper.assertTrue(spawnAction == null || spawnAction == live, "Rib volley changed emitter action");
                        spawnAction = live;
                        helper.assertTrue(rib.getType() == ModEntityTypes.PILLAR_MAN_RIBS.get()
                                        && rib.tickCount == 0 && rib.ticksLifespan() == 21
                                        && rib.getSpeedFactor() == 1.0D && !rib.isAttachedToAnEntity()
                                        && ORIGINS.stream().anyMatch(origin -> near(origin, tracked.origin))
                                        && Math.abs(tracked.pitch - Math.toDegrees(Math.atan(0.1D))) < EPSILON
                                        && Math.abs(tracked.yaw) < EPSILON,
                                "Rib type, age, lifespan, speed, authored origin or angle changed");
                        if (ribs.size() == 1) {
                            float energyBefore = pressInProgress ? energyBeforePress : energyBeforePlayerTick;
                            float drainAllowance = pressInProgress ? 0.0F
                                    : Math.max(0.0F, VampirismUtil.bloodTickDown(player) * data.getEvolutionStage());
                            float debit = energyBefore - data.getEnergy();
                            float rounding = Math.ulp(energyBefore) * 2.0F;
                            helper.assertTrue(debit >= 60.0F - rounding && debit <= 60.0F + drainAllowance + rounding,
                                    "Real Survival Ribs input did not debit 60 energy: " + debit);
                            volleyEnergy = data.getEnergy();
                            log("debit observed=" + debit + " passiveDrainAllowance=" + drainAllowance
                                    + " synchronous=" + pressInProgress);
                        }
                        helper.assertTrue(data.getEnergy() == volleyEnergy, "Rib volley acquired a per-projectile energy debit");
                        log("spawn uuid=" + rib.getUUID() + " age=" + rib.tickCount + " L=" + rib.ticksLifespan()
                                + " origin=" + tracked.origin + " angles=" + tracked.pitch + "," + tracked.yaw
                                + " tip=" + rib.position() + " root=" + rib.getOriginPoint(1));
                    });
                }
                else if (joinEvent.getEntity() instanceof ItemEntity item && ribs.values().stream().anyMatch(tracked ->
                        tracked.before != null && tracked.impacts.stream().anyMatch(impact ->
                                impact.getRayTraceResult() instanceof BlockHitResult hit && panes.containsKey(hit.getBlockPos())
                                        && hit.getBlockPos().equals(item.blockPosition())))) {
                    blockDrops.add(item);
                }
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() == player) energyBeforePlayerTick = data.getEnergy();
                Tracked tracked = ribs.get(preEvent.getEntity().getUUID());
                if (tracked == null) return;
                helper.assertTrue(!preEvent.isCanceled() && tracked.before == null, "Natural rib tick was canceled or unmatched");
                tracked.impacts.clear();
                tracked.before = ray(tracked);
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                Tracked tracked = ribs.get(impactEvent.getProjectile().getUUID());
                if (tracked != null) tracked.impacts.add(impactEvent);
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == player) playerTicks++;
                Tracked tracked = ribs.get(postEvent.getEntity().getUUID());
                if (tracked == null) return;
                PillarmanRibEntity rib = tracked.rib;
                Ray before = tracked.before;
                helper.assertTrue(before != null && before.age == rib.tickCount && before.time == level.getGameTime(),
                        "Missing same-tick natural rib pre/post pair");
                CompoundTag nbt = rib.saveWithoutId(new CompoundTag());
                if (before.pane && tracked.firstPane == null) {
                    List<Impact> impacts = tracked.impacts.stream().map(hitEvent -> {
                        HitResult hit = hitEvent.getRayTraceResult();
                        return new Impact(hit.getType().name(), hit.getLocation(),
                                hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null,
                                hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null, hitEvent.isCanceled());
                    }).toList();
                    tracked.firstPane = new Contact(before, rib.position(), nbt.getDouble("Distance"), nbt.getBoolean("IsRetracting"),
                            rib.isAttachedToAnEntity(), rib.ticksLifespan(), target == null ? 0.0F : target.getHealth(),
                            level.getBlockState(before.collider.getBlockPos()),
                            panes.entrySet().stream().filter(entry -> !level.getBlockState(entry.getKey()).equals(entry.getValue()))
                                    .map(Map.Entry::getKey).toList(), impacts);
                    log("contact uuid=" + rib.getUUID() + " origin=" + tracked.origin + " " + tracked.firstPane);
                }
                else if (tracked.firstPane == null) {
                    helper.assertTrue(tracked.impacts.isEmpty() && !before.retracting && !before.attached
                                    && !nbt.getBoolean("IsRetracting") && !rib.isAttachedToAnEntity(),
                            "Rib impacted, attached or retracted before the owned pane; age=" + before.age);
                    Vec3 expectedTip = before.root.add(Vec3.directionFromRotation(tracked.pitch, tracked.yaw)
                            .scale(nbt.getDouble("Distance")));
                    helper.assertTrue(Math.abs(nbt.getDouble("Distance") - before.distance - SPEED) < EPSILON
                                    && near(rib.position(), expectedTip) && near(rib.getDeltaMovement(), rib.position().subtract(before.tip)),
                            "Rib's natural free extension or stored delta changed; age=" + before.age);
                    tracked.freeFlightSamples++;
                }
                tracked.priorImpacts += tracked.impacts.size();
                tracked.before = null;
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
            PillarmanRibEntity rib = tracked.rib;
            helper.assertTrue(rib.getOwner() == player && level.isPositionEntityTicking(rib.blockPosition()),
                    "Rib changed owner or left its ticking basin");
            Vec3 root = player.getEyePosition(1.0F).add(tracked.origin);
            helper.assertTrue(near(root, rib.getOriginPoint(1.0F)), "Rib root differs from its independently checked authored origin");
            Vec3 tip = rib.position();
            Vec3 delta = rib.getDeltaMovement();
            Vec3 end = tip.add(delta);
            BlockHitResult collider = level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rib));
            BlockHitResult outline = level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, rib));
            boolean hitsPane = collider.getType() == HitResult.Type.BLOCK && panes.containsKey(collider.getBlockPos());
            AABB candidates = rib.getBoundingBox().expandTowards(root.subtract(end)).inflate(1.0D);
            AABB targetBox = target == null ? null : target.getBoundingBox().inflate(target.getPickRadius() + rib.getBbWidth() / 2.0D);
            Vec3 targetClip = targetBox == null ? null : targetBox.contains(root) ? root : targetBox.clip(root, end).orElse(null);
            boolean candidate = target != null && level.getEntities(rib, candidates, entity -> entity == target).contains(target);
            BlockState state = hitsPane ? level.getBlockState(collider.getBlockPos()) : Blocks.AIR.defaultBlockState();
            float hardness = hitsPane ? state.getDestroySpeed(level, collider.getBlockPos()) : -1.0F;
            CompoundTag nbt = rib.saveWithoutId(new CompoundTag());
            return new Ray(level.getGameTime(), rib.tickCount, root, tip, delta, nbt.getDouble("Distance"),
                    nbt.getBoolean("IsRetracting"), rib.isAttachedToAnEntity(), collider, outline, hitsPane,
                    candidates, targetBox, targetClip, candidate, target == null ? 0.0F : target.getHealth(),
                    state, hardness, tracked.priorImpacts);
        }

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition())
                            && (target == null || level.isPositionEntityTicking(target.blockPosition()))
                            && !player.getAbilities().instabuild && data.getEvolutionStage() == 2
                            && data.getMode() == PillarmanMode.NONE && !data.isStoneFormEnabled() && data.getEnergy() > 60.0F,
                    "Ribs fixture is not a ready Survival stage-2 NONE player");
            player.setHealth(player.getMaxHealth());
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.CLICK),
                    "Registered Ribs CLICK failed normal admission");
            pressed = true;
            energyBeforePress = data.getEnergy();
            pressInProgress = true;
            try {
                var input = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.CLICK,
                        0.0F, BufferingState.clickOnly(), ability.getAbilityId());
                helper.assertTrue(input != null && input.action instanceof PillarmanRibsBladesAbility.RibsBladesInstance
                                && input.action == LivingComponentAction.getCurEntityAction(player),
                        "Registered Ribs CLICK did not install its real action");
                action = (EntityActionInstance) input.action;
                helper.assertTrue(action.ability == ability && (spawnAction == null || spawnAction == action),
                        "Returned Ribs action differs from the actual volley emitter");
            }
            finally {
                pressInProgress = false;
            }
            log("press playerTicks=" + playerTicks + " ability=" + ability.getAbilityId() + " energy=" + energyBeforePress);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Rib watchdog: spawned=" + ribs.size()
                        + " contacts=" + ribs.values().stream().filter(tracked -> tracked.firstPane != null).count());
                helper.assertTrue(player.isAlive() && near(player.position(), playerPosition) && !player.isOnFire()
                                && player.getYRot() == 0.0F && player.getXRot() == 0.0F && player.yBodyRot == 0.0F,
                        "Rib owner moved, rotated, died or burned");
                helper.assertTrue(target == null || target.isAlive() && target.isInvulnerable()
                                && near(target.position(), targetPosition) && target.getHealth() == targetHealth,
                        "Refused-hit target moved, lost invulnerability or took damage");
                if (!pressed && playerTicks >= 2) press();
                if (ribs.size() == 8 && !released) {
                    AbilityInput.keyRelease(KEY, player);
                    released = true;
                }
                if (ribs.size() == 8 && ribs.values().stream().allMatch(tracked -> tracked.firstPane != null)) {
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
            helper.assertTrue(ribs.size() == 8 && spawnAction == action, "Missing or foreign Ribs volley");
            for (Vec3 origin : ORIGINS) {
                helper.assertTrue(ribs.values().stream().filter(tracked -> near(tracked.origin, origin)).count() == 1,
                        "Rib volley lost or duplicated an authored origin");
            }
            for (Tracked tracked : ribs.values()) {
                Contact contact = tracked.firstPane;
                Ray before = contact.before;
                helper.assertTrue(tracked.freeFlightSamples >= 3 && before.age >= 3 && before.age < NATURAL_TURN_TICK
                                && before.priorImpacts == 0 && !before.retracting && !before.attached,
                        "Rib first contact did not precede its natural turn; age=" + before.age);
                helper.assertTrue(before.paneBefore.is(Blocks.GLASS_PANE) && Math.abs(before.paneHardness - 0.3F) < EPSILON
                                && before.outline.getType() == HitResult.Type.BLOCK
                                && before.outline.getBlockPos().equals(before.collider.getBlockPos())
                                && before.outline.getDirection() == before.collider.getDirection()
                                && near(before.outline.getLocation(), before.collider.getLocation()),
                        "Rib COLLIDER/OUTLINE clips or pane hardness disagree; age=" + before.age);
                helper.assertTrue(contact.impacts.stream().noneMatch(Impact::canceled)
                                && contact.changedPanes.isEmpty() && contact.paneAfter.equals(before.paneBefore)
                                && !contact.attachedAfter && contact.lifespanAfter == 21,
                        "Rib impact was canceled, altered the pane or gained attachment; age=" + before.age);
                if (withTarget) {
                    helper.assertTrue(before.targetClip != null && before.targetCandidate && before.targetHealth == targetHealth
                                    && before.root.distanceToSqr(before.collider.getLocation()) < before.root.distanceToSqr(before.targetClip),
                            "Mixed rib clip or candidate volume missed the refused target; age=" + before.age);
                    helper.assertTrue(!contact.impacts.isEmpty()
                                    && contact.impacts.stream().allMatch(hit -> target.getUUID().equals(hit.target))
                                    && contact.targetHealthAfter == targetHealth && !contact.retractingAfter,
                            "Refused mixed rib hit was not entity-only without retraction; age=" + before.age);
                }
                else {
                    helper.assertTrue(!contact.impacts.isEmpty()
                                    && contact.impacts.stream().allMatch(hit -> before.collider.getBlockPos().equals(hit.block))
                                    && contact.retractingAfter,
                            "Block-only rib did not deliver an ordinary unbroken-pane impact and retract; age=" + before.age);
                }
            }
            log("result contacts=8/8 scope=" + (withTarget ? "refused-entity-only-no-attachment" : "unbroken-pane-retraction")
                    + " ages=" + ribs.values().stream().map(tracked -> tracked.firstPane.before.age).toList()
                    + " changedPanes=0 ownedDrops=" + blockDrops.size());
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Rib collision fixture observer failed", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("RIB-COLLISION {} {}", withTarget ? "mixed-refused" : "block-control", message);
        }

        private static boolean near(Vec3 a, Vec3 b) {
            return a.distanceToSqr(b) < EPSILON * EPSILON;
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
                    for (Tracked tracked : ribs.values()) if (!tracked.rib.isRemoved()) tracked.rib.discard();
                    for (ItemEntity drop : blockDrops) if (!drop.isRemoved()) drop.discard();
                    if (target != null) target.discard();
                    if (player != null) player.discard();
                }
                finally {
                    for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                    log("cleanup listeners=0 restored=" + restored + " savedBlocks=" + original.size()
                            + " ownedRibs=" + ribs.size() + " ownedDrops=" + blockDrops.size());
                    if (!restored) throw new IllegalStateException("Rib fixture blocks were not exactly restored");
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
