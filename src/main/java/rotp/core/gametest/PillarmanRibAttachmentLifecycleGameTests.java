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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
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
public final class PillarmanRibAttachmentLifecycleGameTests {
    private PillarmanRibAttachmentLifecycleGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "rib_removed_attachment", timeoutTicks = 120)
    public static void lethalRibKeepsTargetEndpointAfterNaturalCorpseRemoval(GameTestHelper helper) {
        start(helper, true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "rib_living_attachment", timeoutTicks = 120)
    public static void survivingRibTargetKeepsItsNaturalAttachmentEndpoint(GameTestHelper helper) {
        start(helper, false);
    }

    private record Frame(long time, int age, Vec3 tip, Vec3 root, Vec3 end, Vec3 projection,
            Vec3 targetCenter, boolean targetLookup, boolean attached, int attachedId, int life,
            boolean removed, boolean resolvedTarget, AABB query, boolean candidate, boolean clip,
            boolean clear, float health) {}

    private static final class Attack {
        final LivingIncomingDamageEvent incoming;
        final float healthBefore;
        Float applied, healthAfter;
        Attack(LivingIncomingDamageEvent incoming, float healthBefore) {
            this.incoming = incoming; this.healthBefore = healthBefore;
        }
    }

    private static final class Tracked {
        final PillarmanRibEntity rib;
        final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        final List<Attack> attacks = new ArrayList<>();
        Frame before;
        Tracked(PillarmanRibEntity rib) { this.rib = rib; }
    }

    private static void start(GameTestHelper helper, boolean lethal) {
        Fixture fixture = new Fixture(helper, lethal);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 38;
        private static final double EPS = 1.0E-4D;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean lethal;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final Map<UUID, Tracked> ribs = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<LivingDeathEvent> deaths = new ArrayList<>();
        private final List<LivingExperienceDropEvent> experiences = new ArrayList<>();
        private Player user;
        private IronGolem target;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action, spawnAction;
        private Tracked selected;
        private Attack selectedAttack;
        private DamageSource lethalSource;
        private ChunkPos chunk;
        private Vec3 targetPosition, removalCenter;
        private int userPosts, targetPosts, grantPost, selectedAge, attachedPosts, selectedLastAge;
        private int absentFrames, targetPreAge, removalDeathTime, drops;
        private long generation, targetPreTime;
        private boolean granted, pressed, targetTickOpen, naturalRemoval, lastSelectedLookup, done, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, boolean lethal) {
            this.helper = helper; this.level = helper.getLevel(); this.lethal = lethal;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "RIB-ATTACH-PREMISE " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, "RIB-ATTACH-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("RIB-ATTACH {} {}", lethal ? "LETHAL" : "SURVIVING", message); }
        private Vec3 center() { return new Vec3(target.getX(), target.getY(0.5D), target.getZ()); }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO); chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 4, y - 3, z + 2), max = new BlockPos(x + 12, y + 4, z + 10);
            premise(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight(), "basin build bounds");
            premise(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(), "foreign entity in basin");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "basin is not air: " + pos);
                original.put(pos.immutable(), level.getBlockState(pos));
            }
            for (BlockPos pos : original.keySet()) {
                if (pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getY() == min.getY() || pos.getY() == max.getY()
                        || pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
                    premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "basin wall placement failed");
                }
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(user);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true); user.moveTo(x + 8.5D, y, z + 4.10D, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            target = EntityType.IRON_GOLEM.create(level);
            premise(target != null, "ordinary golem factory failed"); owned.add(target);
            target.setNoAi(true); target.setNoGravity(true);
            targetPosition = new Vec3(x + 8.5D, y, z + 8.35D); target.setPos(targetPosition);
            if (lethal) target.setHealth(0.25F);
            premise(target.getHealth() == (lethal ? 0.25F : 100F) && !target.isInvulnerable() && !target.isBlocking(),
                    "declared low/default HP ordinary target profile changed");
            premise(level.addFreshEntity(user) && level.addFreshEntity(target), "owned actors failed to join");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(!power.hasPower() && target.getExperienceReward(level, user) == 0, "fresh owner/default zero-XP golem premise");
            observeEvents();
            log("setup owner=" + user.getUUID() + " target=" + target.getUUID() + " lowHpBootstrap=" + lethal
                    + " targetHP=" + target.getHealth() + " NoAI=true NoGravity=true stunAcceptanceNotClaimed=true"
                    + " ownerPos=" + user.position() + " targetPos=" + target.position() + " savedCells=" + original.size());
        }

        private void observeEvents() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() != level || !(event.getEntity() instanceof PillarmanRibEntity rib) || rib.getOwner() != user) return;
                owned.add(rib);
                Tracked tracked = new Tracked(rib);
                premise(ribs.put(rib.getUUID(), tracked) == null && ribs.size() <= 8, "duplicate/excess owned rib");
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                premise(pressed && !event.isCanceled() && current != null && current.ability == ability
                        && current.getPowerUser() == user && current.getPhase() == ActionPhase.PERFORM
                        && (spawnAction == null || spawnAction == current), "join lacks actual registered CLICK performer");
                spawnAction = current;
                premise(rib.getType() == ModEntityTypes.PILLAR_MAN_RIBS.get() && rib.tickCount == 0 && rib.ticksLifespan() == 21
                        && !rib.isAttachedToAnEntity(), "registered rib initial lifetime/type/attachment differs");
                log("join rib=" + rib.getUUID() + " type=" + rib.getType() + " pos=" + rib.position() + " root=" + rib.getOriginPoint(1));
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user || event.getEntity() == target) {
                    premise(!event.isCanceled(), "owned actor natural tick canceled"); ready(event.getEntity().getBoundingBox());
                }
                if (event.getEntity() == target) {
                    targetTickOpen = true; targetPreAge = target.tickCount; targetPreTime = level.getGameTime();
                }
                Tracked tracked = ribs.get(event.getEntity().getUUID());
                if (tracked == null || tracked.rib != event.getEntity()) return;
                premise(!event.isCanceled() && tracked.before == null, "unpaired/canceled natural rib Pre");
                tracked.before = frame(tracked.rib);
                ready(tracked.before.query);
                if (tracked == selected) {
                    premise(!tracked.before.removed && user.isAlive() && tracked.rib.getOwner() == user
                            && tracked.before.age == selectedLastAge + 1, "selected source/owner or consecutive natural age lost");
                    if (!tracked.before.targetLookup) {
                        absentFrames++;
                        log("absent-lookup observation selected=" + selected.rib.getUUID() + " naturalRemoval=" + naturalRemoval
                                + " removalCenter=" + removalCenter + " deathTime=" + removalDeathTime + " before=" + tracked.before);
                        premise(lethal && absentFrames == 1 && lastSelectedLookup && naturalRemoval && target.isRemoved()
                                && target.getRemovalReason() == Entity.RemovalReason.KILLED && removalDeathTime >= 20
                                && target.getHealth() == 0 && !deaths.isEmpty() && deaths.stream().noneMatch(LivingDeathEvent::isCanceled)
                                && tracked.before.attached && tracked.before.attachedId == target.getId() && tracked.before.life == 41
                                && tracked.before.age < tracked.before.life && removalCenter != null
                                && removalCenter.distanceTo(tracked.before.projection) > 0.5D,
                                "first absent lookup lacks natural removal, live attachment or separated endpoints");
                        log("FIRST_ABSENT qualified=true selected=" + selected.rib.getUUID() + " removalDeathTime=" + removalDeathTime
                                + " targetCenter=" + removalCenter + " before=" + tracked.before);
                    }
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Tracked tracked = ribs.get(event.getProjectile().getUUID());
                if (tracked == null || tracked.rib != event.getProjectile()) return;
                tracked.impacts.add(event);
                premise(tracked.before != null && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == target,
                        "rib hit unrelated entity/block outside owned target contact");
                premise(tracked.before.candidate && tracked.before.clip && tracked.before.clear, "natural contact lacks independent clip/query/clear ray");
            });
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() != user && event.getEntity() != target) return;
                premise(event.getEntity() == target && event.getSource().getDirectEntity() instanceof PillarmanRibEntity,
                        "unexpected owned actor damage");
                Tracked tracked = ribs.get(event.getSource().getDirectEntity().getUUID());
                premise(tracked != null && tracked.before != null && event.getSource().getEntity() == user
                        && event.getSource().is(ModDamageTypes.MOD_PROJECTILE) && !JojoModUtil.isTargetBlocking(target),
                        "Incoming is not the unblocked owned natural rib source");
                tracked.attacks.add(new Attack(event, target.getHealth()));
            });
            Consumer<LivingDamageEvent.Post> damagePost = event -> observe(() -> {
                if (event.getEntity() != target) return;
                premise(event.getSource().getDirectEntity() instanceof PillarmanRibEntity, "non-rib damage Post");
                Tracked tracked = ribs.get(event.getSource().getDirectEntity().getUUID());
                premise(tracked != null && tracked.before != null, "damage Post outside natural owned rib tick");
                Attack attack = tracked.attacks.stream().filter(a -> a.incoming.getSource() == event.getSource() && a.applied == null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("RIB-ATTACH-PREMISE unpaired damage Post"));
                attack.applied = event.getNewDamage(); attack.healthAfter = target.getHealth();
                log("damage rib=" + tracked.rib.getUUID() + " original=" + attack.incoming.getOriginalAmount()
                        + " applied=" + attack.applied + " HP=" + attack.healthBefore + "->" + attack.healthAfter);
            });
            Consumer<LivingDeathEvent> death = event -> observe(() -> {
                if (event.getEntity() != target) return;
                premise(lethal && event.getSource().getDirectEntity() instanceof PillarmanRibEntity, "unexpected target death");
                Tracked tracked = ribs.get(event.getSource().getDirectEntity().getUUID());
                premise(tracked != null && tracked.before != null && target.getHealth() == 0
                        && (lethalSource == null || lethalSource == event.getSource()), "death is not the owned natural lethal contact");
                lethalSource = event.getSource(); deaths.add(event);
                log("death rib=" + tracked.rib.getUUID() + " canceled=" + event.isCanceled() + " targetAge=" + target.tickCount);
            });
            Consumer<LivingDropsEvent> drop = event -> observe(() -> {
                if (event.getEntity() != target) return;
                owned.addAll(event.getDrops()); drops += event.getDrops().size();
                premise(lethal && event.getSource() == lethalSource, "target loot lacks the witnessed lethal source");
            });
            Consumer<LivingExperienceDropEvent> experience = event -> observe(() -> {
                if (event.getEntity() != target) return;
                experiences.add(event);
                premise(event.getDroppedExperience() == 0, "ordinary golem unexpectedly produces XP; no orb provenance is claimed");
            });
            Consumer<EntityLeaveLevelEvent> leave = event -> observe(() -> {
                if (event.getEntity() != target) return;
                premise(lethal && selected != null && selectedAttack != null && targetTickOpen && targetPreAge == target.tickCount
                        && targetPreTime == level.getGameTime() && target.deathTime >= 20 && target.getHealth() == 0
                        && target.getRemovalReason() == Entity.RemovalReason.KILLED && !deaths.isEmpty()
                        && deaths.stream().noneMatch(LivingDeathEvent::isCanceled), "target leave lacks actual natural death-tick removal");
                removalCenter = center(); removalDeathTime = target.deathTime; naturalRemoval = true;
                log("natural-remove target=" + target.getUUID() + " deathTime=" + removalDeathTime + " center=" + removalCenter
                        + " selectedAge=" + selected.rib.tickCount + " selectedRemoved=" + selected.rib.isRemoved());
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userPosts++;
                if (event.getEntity() == target) { targetPosts++; targetTickOpen = false; }
                Tracked tracked = ribs.get(event.getEntity().getUUID());
                if (tracked == null || tracked.rib != event.getEntity()) return;
                premise(tracked.before != null && tracked.before.time == level.getGameTime(), "rib Post lacks natural Pre");
                Frame after = frame(tracked.rib);
                if (selected == null && after.attached && after.resolvedTarget) select(tracked, after);
                if (tracked == selected) finishFrame(tracked.before, after);
                tracked.before = null;
            });
            add(join, EntityJoinLevelEvent.class, true); add(pre, EntityTickEvent.Pre.class, true);
            add(impact, ProjectileImpactEvent.class, true); add(incoming, LivingIncomingDamageEvent.class, true);
            add(damagePost, LivingDamageEvent.Post.class, false); add(death, LivingDeathEvent.class, true);
            add(drop, LivingDropsEvent.class, true); add(experience, LivingExperienceDropEvent.class, true);
            add(leave, EntityLeaveLevelEvent.class, false); add(post, EntityTickEvent.Post.class, false);
        }

        private void select(Tracked tracked, Frame after) {
            Attack attack = tracked.attacks.stream().filter(a -> a.applied != null && a.applied > 0 && a.healthAfter != null
                    && a.healthAfter < a.healthBefore && !a.incoming.isCanceled()).findFirst().orElse(null);
            premise(attack != null && !tracked.impacts.isEmpty() && tracked.impacts.stream().noneMatch(ProjectileImpactEvent::isCanceled)
                    && after.attachedId == target.getId() && after.life == 41 && !after.removed
                    && tracked.before.candidate && tracked.before.clip && tracked.before.clear,
                    "attachment lacks actual accepted positive unblocked contact/source proof");
            if (lethal) premise(attack.healthBefore == 0.25F && attack.healthAfter == 0F && lethalSource == attack.incoming.getSource()
                    && !target.isAlive() && !deaths.isEmpty() && deaths.stream().noneMatch(LivingDeathEvent::isCanceled), "contact was not naturally lethal");
            else premise(target.isAlive() && target.getHealth() > 0 && deaths.isEmpty(), "surviving attachment control died");
            selected = tracked; selectedAttack = attack; selectedAge = after.age; selectedLastAge = after.age;
            lastSelectedLookup = after.targetLookup;
            log("selected rib=" + tracked.rib.getUUID() + " source=" + attack.incoming.getSource()
                    + " health=" + attack.healthBefore + "->" + attack.healthAfter + " attached=" + after);
        }

        private void finishFrame(Frame before, Frame after) {
            premise(after.age == before.age && after.attached && after.attachedId == target.getId() && after.life == 41
                    && !after.removed && user.isAlive() && selected.rib.getOwner() == user, "selected attached source/lifetime became unavailable");
            log("attached-frame rib=" + selected.rib.getUUID() + " before=" + before + " after=" + after
                    + " ownerLook=" + user.getYRot() + "," + user.getXRot() + " bodyYaw=" + user.yBodyRot);
            if (before.targetLookup) {
                oracle(after.tip.distanceTo(after.targetCenter) < EPS, "live lookup attachment did not retain actual target center");
                attachedPosts++;
                if (!lethal && after.age >= selectedAge + 22) {
                    premise(target.isAlive() && after.targetLookup && after.resolvedTarget && deaths.isEmpty() && !naturalRemoval,
                            "surviving long-window control lost target");
                    done = true;
                }
            }
            else {
                premise(lethal && absentFrames == 1 && naturalRemoval && after.age < after.life
                        && !after.targetLookup && after.attached && removalCenter != null, "absent lookup branch not qualified");
                oracle(after.tip.distanceTo(removalCenter) < EPS,
                        "first natural absent-target tick left the cached target center");
                done = true;
            }
            selectedLastAge = after.age; lastSelectedLookup = after.targetLookup;
        }

        private Frame frame(PillarmanRibEntity rib) {
            CompoundTag tag = new CompoundTag(); rib.saveWithoutId(tag);
            Vec3 root = rib.getOriginPoint(1), end = rib.position().add(rib.getDeltaMovement());
            AABB query = rib.getBoundingBox().expandTowards(root.subtract(end)).inflate(1);
            AABB targetBox = target.getBoundingBox().inflate(target.getPickRadius() + rib.getBbWidth() / 2D);
            boolean candidate = level.getEntities(rib, query, entity -> entity == target).contains(target);
            boolean clip = targetBox.contains(root) || targetBox.clip(root, end).isPresent();
            boolean clear = level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rib)).getType() == HitResult.Type.MISS;
            float distance = (float) tag.getDouble("Distance"), speed = 8F / rib.ticksLifespan();
            if (tag.getBoolean("IsRetracting")) distance = (float) (distance - speed * rib.getSpeedFactor());
            else if (tag.getBoolean("IsMovingForward")) distance = (float) (distance + speed * rib.getSpeedFactor());
            Vec3 rootByLook = user.getEyePosition(1).add(new Vec3(tag.getDouble("XOriginOffset"), tag.getDouble("YOriginOffset"), 0)
                    .yRot((float) Math.toRadians(-user.getYRot())));
            Vec3 projection = rootByLook.add(Vec3.directionFromRotation(user.getXRot() + tag.getFloat("XRotOffset"),
                    user.getYRot() + tag.getFloat("YRotOffset")).scale(distance));
            return new Frame(level.getGameTime(), rib.tickCount, rib.position(), root, end, projection, center(),
                    level.getEntity(target.getId()) == target, rib.isAttachedToAnEntity(),
                    tag.hasUUID("AttachedEntity") && tag.getUUID("AttachedEntity").equals(target.getUUID()) ? target.getId() : -1,
                    rib.ticksLifespan(),
                    rib.isRemoved(), rib.getEntityAttachedTo() == target, query, candidate, clip, clear, target.getHealth());
        }

        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, canceled, type, listener);
        }
        private void ready(AABB box) {
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "actor/rib query is outside ready chunk");
        }
        private void grant() {
            premise(power.trySetPowerType(ModPlayerPowers.PILLAR_MAN.get()), "Pillar grant failed");
            data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, user); data.setMode(PillarmanMode.NONE, user); data.setEnergy(user, 300F);
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility("pillarman_ribs_blades");
            premise(ability instanceof PillarmanRibsBladesAbility && ability.abilityType == PillarmanPowerType.PILLAR_MAN_RIBS_BLADES.get(), "registered ribs absent");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            grantPost = userPosts; granted = true;
        }
        private void press() {
            premise(user.isAlive() && !user.isOnFire() && target.isAlive() && !target.isInvulnerable()
                    && target.position().distanceTo(targetPosition) < EPS && target.getHealth() == (lethal ? 0.25F : 100F)
                    && !level.canSeeSky(BlockPos.containing(user.getEyePosition())) && !data.isStoneFormEnabled(), "ready actor profile changed");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK), "registered CLICK admission failed");
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK, 0, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof PillarmanRibsBladesAbility.RibsBladesInstance, "CLICK did not install concrete ribs action");
            action = (EntityActionInstance) input.action; generation = input.generation;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.ability == ability && generation > 0
                    && (spawnAction == null || spawnAction == action), "registered emitter identity differs");
            log("input generation=" + generation + " stage=" + data.getEvolutionStage() + " mode=" + data.getMode() + " energy=" + data.getEnergy());
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 100, "finite attachment/removal watchdog; ribs=" + ribs.size() + " selected=" + (selected != null));
                if (!granted && userPosts >= 2 && targetPosts >= 2 && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))) grant();
                else if (granted && !pressed && userPosts >= grantPost + 3) press();
                if (selected != null) premise(!selected.rib.isRemoved() && user.isAlive(), "natural source expired before required lifecycle window");
                if (done) {
                    premise(ribs.size() == 8 && attachedPosts >= 1 && experiences.stream().allMatch(e -> e.getDroppedExperience() == 0), "volley/attachment/XP provenance differs");
                    long released = AbilityInput.keyReleaseAndGetGeneration(KEY, user);
                    premise(released == generation && !AbilityInput.isHeldByKey(user, action), "owned input generation release failed");
                    log("RESULT qualified=true selected=" + selected.rib.getUUID() + " attachedPosts=" + attachedPosts
                            + " firstAbsentFrames=" + absentFrames + " sourceAge=" + selected.rib.tickCount + " naturalRemoval=" + naturalRemoval
                            + " ownedDropItems=" + drops + " native=false");
                    close(); helper.succeed(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }
        private void observe(Runnable operation) {
            if (closed || done || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (user != null && user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned held key remains"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : original.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup cells=" + original.size() + " exactRestore=" + restored + " ownedRemoved=" + removed + " listeners=0");
                if (!restored || !removed) throw new IllegalStateException("rib attachment cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("rib attachment cleanup failed");
                failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
