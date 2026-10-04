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
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.abilities.PillarmanHornAttackAbility;
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
import rotp.core.util.functions.JojoModUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanHornCollisionGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final double SPEED = (double) 0.4F;

    private PillarmanHornCollisionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "horn_mixed_collision", timeoutTicks = 100)
    public static void hornMixedRayKeepsDonorEntityOnlySelection(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "horn_block_control", timeoutTicks = 100)
    public static void hornBlockOnlyRayRetainsOrdinaryBlockResponse(GameTestHelper helper) {
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
            boolean retracting, BlockHitResult collider, BlockHitResult outline, boolean pane,
            AABB candidates, AABB targetBox, Vec3 targetClip, boolean targetCandidate,
            float targetHealth, BlockState paneBefore, boolean policyAllowsBreak, int priorImpacts) {}

    private record Impact(String type, Vec3 position, BlockPos block, UUID target, boolean canceled) {}
    private record DestroyAttempt(BlockPos block, boolean canceled) {}
    private record Contact(Ray before, Vec3 tipAfter, double distanceAfter, boolean retractingAfter,
            float targetHealthAfter, BlockState paneAfter, List<BlockPos> changedPanes,
            List<Impact> impacts, List<DestroyAttempt> destruction) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 34;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean withTarget;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> panes = new LinkedHashMap<>();
        private final List<PillarmanHornEntity> horns = new ArrayList<>();
        private final List<ItemEntity> blockDrops = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<ProjectileImpactEvent> currentImpacts = new ArrayList<>();
        private final List<LivingDestroyBlockEvent> currentDestruction = new ArrayList<>();
        private Player player;
        private IronGolem target;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action;
        private EntityActionInstance spawnAction;
        private Vec3 playerPosition;
        private Ray before;
        private Contact contact;
        private RuntimeException observerFailure;
        private int playerTicks;
        private int freeFlightSamples;
        private int priorImpacts;
        private float energyBeforePress;
        private float energyBeforePlayerTick;
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
            BlockPos min = new BlockPos(x + 4, y - 1, z + 2);
            BlockPos max = new BlockPos(x + 12, y + 4, z + 10);
            helper.assertTrue(y + 5 < level.getMaxBuildHeight(), "Horn basin exceeds build height");
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Horn basin contains an unrelated entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Horn sky basin is obstructed at " + pos);
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
            playerPosition = new Vec3(x + 8.5D, y, z + 4.0D);
            player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0);
            player.setYHeadRot(0);
            player.yBodyRot = 0;
            helper.assertTrue(level.addFreshEntity(player), "Could not add the horn-test player");
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, player);
            data.setMode(PillarmanMode.NONE, player);
            data.setEnergy(player, 300.0F);
            ability = power.getAbility("pillarman_horn_attack");
            helper.assertTrue(ability instanceof PillarmanHornAttackAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_HORN_ATTACK.get(),
                    "Fixture did not resolve the registered Horn Attack");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            if (withTarget) {
                target = EntityType.IRON_GOLEM.create(level);
                helper.assertTrue(target != null, "Could not create the living horn target");
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setPos(x + 8.5D, y, z + 8.30D);
                helper.assertTrue(level.addFreshEntity(target) && target.isAlive() && !target.isInvulnerable()
                                && !target.isSpectator() && target.isPickable() && player.canAttack(target),
                        "Horn target is not an eligible ordinary living entity");
            }
            registerObservers();
            log("setup chunk=" + chunk + " owner=" + player.getUUID() + " pos=" + playerPosition
                    + " target=" + (target == null ? "none" : target.getUUID() + " " + target.getBoundingBox())
                    + " paneZ=" + (z + 7) + " savedBlocks=" + original.size()
                    + " breakBlocksRule=" + JojoModUtil.breakingBlocksEnabled(level));
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof PillarmanHornEntity horn && horn.getOwner() == player) {
                    horns.add(horn);
                    observe(() -> {
                        EntityActionInstance live = LivingComponentAction.getCurEntityAction(player);
                        helper.assertTrue(!event.isCanceled() && pressed && horns.size() == 1
                                        && live instanceof PillarmanHornAttackAbility.HornAttackInstance
                                        && live.ability == ability && live.getPhase() == ActionPhase.PERFORM,
                                "Horn was not emitted once by its live registered perform action");
                        spawnAction = live;
                        helper.assertTrue(horn.getType() == ModEntityTypes.PILLAR_MAN_HORN.get()
                                        && horn.tickCount == 0 && horn.ticksLifespan() == 40 && horn.getSpeedFactor() == 1.0D,
                                "Horn type, natural spawn age, lifespan or speed factor changed");
                        float energyBefore = pressInProgress ? energyBeforePress : energyBeforePlayerTick;
                        float drainAllowance = pressInProgress ? 0.0F
                                : Math.max(0.0F, VampirismUtil.bloodTickDown(player) * data.getEvolutionStage());
                        float debit = energyBefore - data.getEnergy();
                        float rounding = Math.ulp(energyBefore) * 2.0F;
                        helper.assertTrue(debit >= 15.0F - rounding && debit <= 15.0F + drainAllowance + rounding,
                                "Real Survival Horn input did not debit 15 energy: " + debit);
                        log("spawn uuid=" + horn.getUUID() + " age=" + horn.tickCount + " L=" + horn.ticksLifespan()
                                + " tip=" + horn.position() + " root=" + horn.getOriginPoint(1)
                                + " synchronous=" + pressInProgress + " observedEnergyDebit=" + debit
                                + " passiveDrainAllowance=" + drainAllowance);
                    });
                }
                else if (event.getEntity() instanceof ItemEntity item && before != null
                        && currentImpacts.stream().anyMatch(impact -> impact.getRayTraceResult() instanceof BlockHitResult hit
                                && panes.containsKey(hit.getBlockPos()) && hit.getBlockPos().equals(item.blockPosition()))) {
                    blockDrops.add(item);
                }
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == player) energyBeforePlayerTick = data.getEnergy();
                if (!horns.contains(event.getEntity())) return;
                helper.assertTrue(!event.isCanceled(), "Natural horn tick was canceled");
                currentImpacts.clear();
                currentDestruction.clear();
                before = ray((PillarmanHornEntity) event.getEntity());
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (horns.contains(event.getProjectile())) currentImpacts.add(event);
            });
            Consumer<LivingDestroyBlockEvent> destroy = event -> observe(() -> {
                if (event.getEntity() == player && before != null && panes.containsKey(event.getPos())) {
                    currentDestruction.add(event);
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == player) playerTicks++;
                if (!(event.getEntity() instanceof PillarmanHornEntity horn) || !horns.contains(horn)) return;
                helper.assertTrue(before != null && before.age == horn.tickCount && before.time == level.getGameTime(),
                        "Missing same-tick natural horn pre/post observations");
                CompoundTag nbt = horn.saveWithoutId(new CompoundTag());
                if (before.pane && contact == null) {
                    List<Impact> impacts = currentImpacts.stream().map(impactEvent -> {
                        HitResult hit = impactEvent.getRayTraceResult();
                        return new Impact(hit.getType().name(), hit.getLocation(),
                                hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null,
                                hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null, impactEvent.isCanceled());
                    }).toList();
                    contact = new Contact(before, horn.position(), nbt.getDouble("Distance"), nbt.getBoolean("IsRetracting"),
                            target == null ? 0.0F : target.getHealth(), level.getBlockState(before.collider.getBlockPos()),
                            panes.entrySet().stream().filter(entry -> !level.getBlockState(entry.getKey()).equals(entry.getValue()))
                                    .map(Map.Entry::getKey).toList(),
                            impacts, currentDestruction.stream().map(destroyEvent -> new DestroyAttempt(destroyEvent.getPos().immutable(), destroyEvent.isCanceled())).toList());
                    log("contact " + contact);
                }
                else if (contact == null) {
                    helper.assertTrue(currentImpacts.isEmpty() && !before.retracting && !nbt.getBoolean("IsRetracting"),
                            "Horn impacted or retracted before reaching the owned pane");
                    helper.assertTrue(Math.abs(nbt.getDouble("Distance") - before.distance - SPEED) < EPSILON
                                    && near(horn.getDeltaMovement(), new Vec3(0, 0, SPEED)),
                            "Natural horn did not extend at speed 0.4 before collision");
                    freeFlightSamples++;
                }
                priorImpacts += currentImpacts.size();
                before = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(destroy);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDestroyBlockEvent.class, destroy);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Ray ray(PillarmanHornEntity horn) {
            helper.assertTrue(horn.getOwner() == player && level.isPositionEntityTicking(horn.blockPosition()),
                    "Horn changed owner or left the entity-ticking basin");
            Vec3 root = player.getEyePosition(1.0F).add(0, (double) 0.15F, 0);
            helper.assertTrue(near(root, horn.getOriginPoint(1.0F)), "Horn's actual root differs from the independent authored eye offset");
            Vec3 tip = horn.position();
            Vec3 delta = horn.getDeltaMovement();
            Vec3 end = tip.add(delta);
            BlockHitResult collider = level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, horn));
            BlockHitResult outline = level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, horn));
            boolean hitsPane = collider.getType() == HitResult.Type.BLOCK && panes.containsKey(collider.getBlockPos());
            AABB candidates = horn.getBoundingBox().expandTowards(root.subtract(end)).inflate(1.0D);
            AABB targetBox = target == null ? null : target.getBoundingBox().inflate(target.getPickRadius() + horn.getBbWidth() / 2.0D);
            Vec3 targetClip = targetBox == null ? null : targetBox.contains(root) ? root : targetBox.clip(root, end).orElse(null);
            boolean candidate = target != null && level.getEntities(horn, candidates, entity -> entity == target).contains(target);
            BlockState paneBefore = hitsPane ? level.getBlockState(collider.getBlockPos()) : Blocks.AIR.defaultBlockState();
            boolean policyAllowsBreak = hitsPane && JojoModUtil.breakingBlocksEnabled(level)
                    && level.mayInteract(player, collider.getBlockPos())
                    && paneBefore.canEntityDestroy(level, collider.getBlockPos(), player);
            if (hitsPane) {
                float hardness = paneBefore.getDestroySpeed(level, collider.getBlockPos());
                helper.assertTrue(hardness >= 0.0F && hardness <= 5.0F, "Owned glass pane is not within the donor horn's hardness limit");
            }
            CompoundTag nbt = horn.saveWithoutId(new CompoundTag());
            return new Ray(level.getGameTime(), horn.tickCount, root, tip, delta, nbt.getDouble("Distance"),
                    nbt.getBoolean("IsRetracting"), collider, outline, hitsPane, candidates, targetBox, targetClip,
                    candidate, target == null ? 0.0F : target.getHealth(), paneBefore, policyAllowsBreak, priorImpacts);
        }

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition())
                            && (target == null || level.isPositionEntityTicking(target.blockPosition()))
                            && !player.getAbilities().instabuild && data.getEvolutionStage() == 2
                            && data.getMode() == PillarmanMode.NONE && !data.isStoneFormEnabled() && data.getEnergy() > 15.0F,
                    "Horn fixture is not a ready Survival stage-2 NONE player");
            player.setHealth(player.getMaxHealth());
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.CLICK),
                    "Registered Horn CLICK failed normal admission");
            pressed = true;
            energyBeforePress = data.getEnergy();
            pressInProgress = true;
            try {
                var input = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.CLICK,
                        0.0F, BufferingState.clickOnly(), ability.getAbilityId());
                helper.assertTrue(input != null && input.action instanceof PillarmanHornAttackAbility.HornAttackInstance
                                && input.action == LivingComponentAction.getCurEntityAction(player),
                        "Registered Horn CLICK did not install the real action");
                action = (EntityActionInstance) input.action;
                helper.assertTrue(action.ability == ability && (spawnAction == null || spawnAction == action),
                        "Returned Horn action differs from the actual emitter");
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
                helper.assertTrue(helper.getTick() < 80, "Horn watchdog: playerTicks=" + playerTicks
                        + " horns=" + horns.size() + " freeFlight=" + freeFlightSamples);
                helper.assertTrue(player.isAlive() && near(player.position(), playerPosition) && !player.isOnFire()
                                && player.getYRot() == 0.0F && player.getXRot() == 0.0F && player.yBodyRot == 0.0F,
                        "Horn owner moved, rotated, died or burned before observation");
                if (!pressed && playerTicks >= 2) press();
                if (!horns.isEmpty() && !released) {
                    AbilityInput.keyRelease(KEY, player);
                    released = true;
                }
                if (contact != null) {
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
            Ray ray = contact.before;
            helper.assertTrue(horns.size() == 1 && spawnAction == action && freeFlightSamples >= 3
                            && ray.age >= 3 && ray.age < 40 && ray.priorImpacts == 0 && !ray.retracting
                            && near(ray.delta, new Vec3(0, 0, SPEED)),
                    "Horn contact was not the single naturally extending production projectile");
            helper.assertTrue(ray.paneBefore.is(Blocks.GLASS_PANE) && ray.outline.getType() == HitResult.Type.BLOCK
                            && ray.outline.getBlockPos().equals(ray.collider.getBlockPos())
                            && ray.outline.getDirection() == ray.collider.getDirection()
                            && near(ray.outline.getLocation(), ray.collider.getLocation()),
                    "Independent COLLIDER/OUTLINE clips do not agree on the owned pane");
            helper.assertTrue(contact.impacts.stream().noneMatch(Impact::canceled), "Actual horn impact was canceled");
            if (withTarget) {
                helper.assertTrue(ray.targetClip != null && ray.targetCandidate && ray.targetHealth > 0
                                && target.isAlive() && !target.isInvulnerable()
                                && ray.root.distanceToSqr(ray.collider.getLocation()) < ray.root.distanceToSqr(ray.targetClip),
                        "Mixed ray did not independently select both the earlier pane and candidate-volume target");
                helper.assertTrue(contact.impacts.stream().anyMatch(hit -> target.getUUID().equals(hit.target))
                                && contact.targetHealthAfter < ray.targetHealth,
                        "Geometrically selected golem had no accepted real horn hit");
                helper.assertTrue(contact.impacts.stream().allMatch(hit -> target.getUUID().equals(hit.target))
                                && contact.changedPanes.isEmpty() && contact.paneAfter.equals(ray.paneBefore)
                                && !contact.retractingAfter,
                        "Donor mixed horn ray must remain entity-only without block destruction/retraction: age=" + ray.age);
                log("result scope=mixed-entity-only paneUnchanged=true damage=" + (ray.targetHealth - contact.targetHealthAfter));
            }
            else {
                helper.assertTrue(contact.impacts.stream().noneMatch(hit -> hit.target != null)
                                && contact.impacts.stream().anyMatch(hit -> ray.collider.getBlockPos().equals(hit.block)),
                        "Block-only control did not deliver an actual ordinary pane impact");
                boolean policyRefused = !ray.policyAllowsBreak
                        || contact.destruction.stream().anyMatch(attempt -> attempt.block.equals(ray.collider.getBlockPos()) && attempt.canceled);
                if (policyRefused) {
                    helper.assertTrue(contact.paneAfter.equals(ray.paneBefore) && contact.changedPanes.isEmpty() && contact.retractingAfter,
                            "Policy-refused block control did not preserve its pane and trigger ordinary retraction");
                    log("result scope=policy-refused-block-retraction breakClaim=false attempts=" + contact.destruction);
                }
                else {
                    helper.assertTrue(contact.destruction.stream().anyMatch(attempt -> attempt.block.equals(ray.collider.getBlockPos()) && !attempt.canceled)
                                    && contact.paneAfter.isAir() && contact.changedPanes.contains(ray.collider.getBlockPos())
                                    && !contact.retractingAfter,
                            "Permitted block-only control did not actually break its pane without premature retraction");
                    log("result scope=ordinary-pane-destruction broken=" + ray.collider.getBlockPos() + " ownedDrops=" + blockDrops.size());
                }
            }
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Horn collision fixture observer failed", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("HORN-COLLISION {} {}", withTarget ? "mixed" : "control", message);
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
                    for (PillarmanHornEntity horn : horns) if (!horn.isRemoved()) horn.discard();
                    for (ItemEntity drop : blockDrops) if (!drop.isRemoved()) drop.discard();
                    if (target != null) target.discard();
                    if (player != null) player.discard();
                }
                finally {
                    for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                    log("cleanup listeners=0 restored=" + restored + " savedBlocks=" + original.size()
                            + " ownedHorns=" + horns.size() + " ownedDrops=" + blockDrops.size());
                    if (!restored) throw new IllegalStateException("Horn fixture blocks were not exactly restored");
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
