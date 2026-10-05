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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonCutterAbility;
import rotp.core.impl.powers.hamon.entity.HamonCutterEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
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
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonCutterDamageGameTests {
    private static final double EPS = 1.0E-5;
    private HamonCutterDamageGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_cutter_damage_control", timeoutTicks = 90)
    public static void ordinaryCutterPreservesBothDamageChannelsAndSource(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_cutter_damage_refused", timeoutTicks = 90)
    public static void refusedOrdinaryCutterDoesNotTrainStrength(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean refuse) {
        Fixture fixture = new Fixture(helper, refuse);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Points(int strength, float fraction) {
        double total() { return strength + (double) fraction; }
    }
    private record Snapshot(float health, int invulnerability, Points points, List<String> effects) {}
    private record HurtReturn(DamageSource source, float amount, boolean accepted, boolean reachedIncoming,
            UUID direct, UUID cause, Vec3 sourcePosition, Vec3 cutterPosition) {}
    private record Contact(UUID id, int age, Snapshot before, Snapshot after, List<Attempt> attempts,
            List<HurtReturn> returns, boolean removed, Entity.RemovalReason reason) {}

    private static final class ObservedGolem extends IronGolem {
        private final Fixture fixture;
        ObservedGolem(Fixture fixture) { super(EntityType.IRON_GOLEM, fixture.level); this.fixture = fixture; }
        @Override public boolean hurt(DamageSource source, float amount) {
            boolean accepted = super.hurt(source, amount);
            fixture.observe(() -> fixture.onHurtReturn(source, amount, accepted));
            return accepted;
        }
    }

    private static final class Attempt {
        final DamageSource source;
        final LivingIncomingDamageEvent incoming;
        final String channel;
        final float original, expectedHamon, targetMultiplier, sourceMultiplier, configMultiplier;
        final UUID direct, cause;
        final Vec3 sourcePosition, cutterPosition;
        Float applied;

        Attempt(LivingIncomingDamageEvent event, Tracked tracked, Fixture fixture) {
            source = event.getSource();
            incoming = event;
            channel = source.is(ModDamageTypes.HAMON) ? "hamon"
                    : source.is(ModDamageTypes.MOD_PROJECTILE) ? "physical" : "unexpected";
            original = event.getOriginalAmount();
            targetMultiplier = HamonAbilityHelpers.hamonDamageMultiplier(fixture.target);
            sourceMultiplier = fixture.hamon.getHamonDamageMultiplier();
            configMultiplier = HamonAbilityHelpers.configHamonDamageMultiplier();
            expectedHamon = HamonAbilityHelpers.hamonDamageAmount(fixture.target, 0.075F)
                    * sourceMultiplier * configMultiplier;
            direct = id(source.getDirectEntity());
            cause = id(source.getEntity());
            sourcePosition = source.getSourcePosition();
            cutterPosition = tracked.cutter.position();
        }
        @Override public String toString() {
            return channel + " amount=" + original + " expectedHamon=" + expectedHamon
                    + " multipliers=" + targetMultiplier + "/" + sourceMultiplier + "/" + configMultiplier
                    + " direct=" + direct + " cause=" + cause + " sourcePos=" + sourcePosition
                    + " cutterPos=" + cutterPosition + " canceled=" + incoming.isCanceled() + " applied=" + applied;
        }
    }

    private static final class Tracked {
        final HamonCutterEntity cutter;
        final List<Attempt> attempts = new ArrayList<>();
        final List<HurtReturn> returns = new ArrayList<>();
        Snapshot before;
        AABB query;
        Vec3 clippedTarget;
        long time;
        int age;
        boolean hit;
        Contact contact;
        Tracked(HamonCutterEntity cutter) { this.cutter = cutter; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 27;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean refuse;
        private final Map<UUID, Tracked> cutters = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> supports = new LinkedHashMap<>();
        private final List<Contact> contacts = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private IronGolem target;
        private PlayerPower power;
        private StandPower stand;
        private HamonData hamon;
        private Ability ability;
        private EntityActionInstance action;
        private Tracked active;
        private Throwable observerFailure;
        private ChunkPos chunk;
        private AABB room;
        private BlockPos roomMin, roomMax;
        private Vec3 userPosition, targetPosition;
        private float frameEnergy, frameDecay;
        private int frameShots, userTicks;
        private long frameTime;
        private boolean userPostActive, pressed, released, paidBurst, roomVerified, closed;

        Fixture(GameTestHelper helper, boolean refuse) {
            this.helper = helper; level = helper.getLevel(); this.refuse = refuse;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = template.getY() + 32;
            roomMin = new BlockPos(x + 4, y - 1, z + 3);
            roomMax = new BlockPos(x + 13, y + 5, z + 14);
            room = AABB.encapsulatingFullBlocks(roomMin, roomMax);
            premise(roomMin.getY() >= level.getMinBuildHeight() && roomMax.getY() < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "Room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "Room obstructed");
            }
            roomVerified = true;
            userPosition = new Vec3(x + 8.5, y, z + 7.5);
            targetPosition = userPosition.add(0, 0, 1.75);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 7, y - 1, z + 8),
                    new BlockPos(x + 9, y - 1, z + 9))) {
                BlockPos key = pos.immutable();
                premise(level.getBlockEntity(key) == null, "Support cell has a block entity");
                supports.put(key, level.getBlockState(key));
                premise(level.setBlock(key, Blocks.STONE.defaultBlockState(), 3), "Support placement failed");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
            premise(level.addFreshEntity(user), "User join failed");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            stand = PowerClass.STAND.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.HAMON_CUTTER.get());
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            ability = power.getAbility("hamon_cutter");
            premise(ability instanceof HamonCutterAbility && ability.abilityType == HamonPowerType.HAMON_CUTTER.get()
                    && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                    && !hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get()), "Wrong registered Cutter setup");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            target = new ObservedGolem(this);
            target.moveTo(targetPosition.x, targetPosition.y, targetPosition.z, 180, 0);
            premise(level.addFreshEntity(target) && target.getHealth() == 100F && target.getMaxHealth() == 100F
                    && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1D
                    && target.getArmorValue() == 0 && target.getAbsorptionAmount() == 0F,
                    "Default target attributes differ");
            premise(HamonAbilityHelpers.configHamonDamageMultiplier() > 0
                    && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "World disables damage or training");
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " feet=" + userPosition
                    + " mouth=" + mouth() + " targetBox=" + target.getBoundingBox() + " room=" + room
                    + " stationaryCasterNoGravity=true targetNaturalGravity=true defaultGolem=true supports=" + supports.keySet());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof HamonCutterEntity cutter)
                        || cutter.getOwner() != user) return;
                cutters.put(cutter.getUUID(), new Tracked(cutter));
                observe(() -> {
                    log("join id=" + cutter.getUUID() + " count=" + cutters.size() + " pos=" + cutter.position()
                            + " mouth=" + mouth() + " velocity=" + cutter.getDeltaMovement() + " energy=" + hamon.getEnergy());
                    EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                    CompoundTag nbt = cutter.saveWithoutId(new CompoundTag());
                    Vec3 delta = cutter.getDeltaMovement();
                    premise(pressed && userPostActive && frameTime == level.getGameTime() && !event.isCanceled()
                            && cutters.size() <= 8 && cutter.getType() == ModEntityTypes.HAMON_CUTTER.get()
                            && current instanceof HamonCutterAbility.CutterInstance && current.ability == ability
                            && current == action && current.getPhase() == ActionPhase.PERFORM,
                            "Emission lacks its natural registered perform tick");
                    premise(cutter.tickCount == 0 && cutter.position().distanceTo(mouth()) < EPS
                            && cutter.getBbWidth() == 0.5F && cutter.getBbHeight() == 0.125F
                            && cutter.ticksLifespan() == 100 && !cutter.canHitOwner() && cutter.isNoGravity()
                            && cutter.getColor() == 0x98DAC0 && nbt.getFloat("HamonStatPoints") == 50F
                            && nbt.getDouble("SpeedFactor") == 1D && user.getMainHandItem().is(ModItems.SOAP.get())
                            && user.getMainHandItem().getCount() == 1 && Math.abs(delta.x) <= 0.285
                            && Math.abs(delta.y) <= 0.285 && delta.z >= 1.117 && delta.z <= 1.935,
                            "Burst origin, input, dimensions or realized spread differs");
                });
            };
            Consumer<EntityTickEvent.Post> startUserPost = event -> observe(() -> {
                if (event.getEntity() != user) return;
                premise(!userPostActive, "Nested user Post");
                userPostActive = true; frameTime = level.getGameTime(); frameEnergy = hamon.getEnergy();
                frameShots = cutters.size();
                CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
                frameDecay = nbt.getInt("EnergyTicks") > 0
                        || !JojoModConfig.getCommonConfigInstance(false).hamonEnergyTicksDown.get()
                        ? 0F : HamonData.ENERGY_TICK_DOWN_AMOUNT;
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = cutters.get(event.getEntity().getUUID());
                if (tracked == null) return;
                requireActors();
                premise(!event.isCanceled() && active == null && tracked.contact == null && paidBurst
                        && cutters.size() == 8 && tracked.cutter.getOwner() == user && !tracked.cutter.canHitOwner(),
                        "Cutter tick is canceled, repeated or not a paid owned burst");
                active = tracked; tracked.before = snapshot(); tracked.age = tracked.cutter.tickCount;
                tracked.time = level.getGameTime();
                Vec3 start = tracked.cutter.position(), end = start.add(tracked.cutter.getDeltaMovement());
                tracked.query = tracked.cutter.getBoundingBox().expandTowards(tracked.cutter.getDeltaMovement()).inflate(1);
                List<Entity> candidates = level.getEntities(tracked.cutter, tracked.query,
                        entity -> entity != user && !cutters.containsKey(entity.getUUID()));
                AABB inflatedTarget = target.getBoundingBox().inflate((double) 0.3F);
                tracked.clippedTarget = inflatedTarget.clip(start, end).orElse(null);
                HitResult.Type collider = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, tracked.cutter)).getType();
                HitResult.Type outline = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, tracked.cutter)).getType();
                log("pre id=" + tracked.cutter.getUUID() + " age=" + tracked.age + " start=" + start + " end=" + end
                        + " query=" + tracked.query + " candidates=" + candidates.stream().map(Entity::getUUID).toList()
                        + " targetBox=" + target.getBoundingBox() + " inflated=" + inflatedTarget
                        + " clip=" + tracked.clippedTarget + " blocks=" + collider + "/" + outline
                        + " snapshot=" + tracked.before);
                premise(tracked.age <= 3 && contains(tracked.query) && ticking(start) && ticking(end)
                        && ticking(new Vec3(tracked.query.minX, tracked.query.minY, tracked.query.minZ))
                        && ticking(new Vec3(tracked.query.maxX, tracked.query.maxY, tracked.query.maxZ))
                        && candidates.size() == 1 && candidates.getFirst() == target && tracked.clippedTarget != null
                        && !inflatedTarget.contains(start) && target.canBeHitByProjectile()
                        && collider == HitResult.Type.MISS && outline == HitResult.Type.MISS,
                        "Realized spread has no isolated first target contact");
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                Tracked tracked = cutters.get(event.getProjectile().getUUID());
                if (tracked == null) return;
                premise(tracked == active && !tracked.hit && !event.isCanceled()
                        && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == target,
                        "Unexpected or canceled natural impact");
                tracked.hit = true;
                log("impact id=" + tracked.cutter.getUUID() + " target=" + target.getUUID()
                        + " type=" + event.getRayTraceResult().getType() + " reportedLocation=" + event.getRayTraceResult().getLocation()
                        + " independentClip=" + tracked.clippedTarget + " cutterPos=" + tracked.cutter.position());
            });
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() != target) return;
                DamageSource source = event.getSource();
                premise(active != null && active.hit && source.getEntity() == user
                        && (source.getDirectEntity() == active.cutter || source.getDirectEntity() == user)
                        && !event.isCanceled(), "Foreign or already-refused target damage");
                Attempt attempt = new Attempt(event, active, this);
                premise(attempt.original > 0 && Float.isFinite(attempt.original) && attempt.expectedHamon > 0,
                        "Damage modifiers cannot support the selected comparison");
                active.attempts.add(attempt);
                if (refuse) event.setCanceled(true);
                log("incoming id=" + active.cutter.getUUID() + " " + attempt);
            });
            Consumer<LivingDamageEvent.Post> damagePost = event -> observe(() -> {
                if (event.getEntity() != target) return;
                premise(active != null && active.hit, "Unpaired accepted damage");
                Attempt attempt = active.attempts.stream().filter(a -> a.source == event.getSource() && a.applied == null)
                        .findFirst().orElseThrow(() -> new IllegalStateException("CUTTER-PREMISE: unmatched damage Post"));
                attempt.applied = event.getNewDamage();
                log("damage-post id=" + active.cutter.getUUID() + " hp=" + target.getHealth() + " " + attempt);
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == target && !target.isNoAi() && target.onGround()) {
                    premise(!pressed && target.position().distanceTo(targetPosition) < EPS,
                            "Natural target settling changed the prepared contact position");
                    target.setNoAi(true);
                    log("target-naturally-grounded age=" + target.tickCount + " position=" + target.position()
                            + " delta=" + target.getDeltaMovement() + " ground=" + target.onGround()
                            + " gravityUnchanged=" + !target.isNoGravity());
                }
                if (event.getEntity() == user) {
                    premise(userPostActive && frameTime == level.getGameTime(), "Missing user Post frame");
                    int emitted = cutters.size() - frameShots;
                    float debit = frameEnergy - hamon.getEnergy() - frameDecay;
                    log("user-post age=" + user.tickCount + " energy=" + frameEnergy + "->" + hamon.getEnergy()
                            + " naturalDecay=" + frameDecay + " actionDebit=" + debit + " emitted=" + emitted
                            + " phase=" + phase() + " soap=" + inventory(ModItems.SOAP.get()) + " bottles=" + inventory(Items.GLASS_BOTTLE));
                    premise((emitted == 0 && Math.abs(debit) < EPS)
                            || (emitted == 8 && !paidBurst && Math.abs(debit - 400F) < EPS),
                            "Energy delta does not match natural decay plus the single400 debit");
                    if (emitted == 8) {
                        premise(inventory(ModItems.SOAP.get()) == 0 && inventory(Items.GLASS_BOTTLE) == 1,
                                "Natural soap debit or bottle return missing");
                        paidBurst = true;
                    }
                    userPostActive = false; userTicks++;
                }
                Tracked tracked = cutters.get(event.getEntity().getUUID());
                if (tracked == null) return;
                premise(active == tracked && tracked.time == level.getGameTime() && tracked.age == tracked.cutter.tickCount
                        && tracked.hit && !tracked.attempts.isEmpty(), "Missing natural hit/Post/damage dispatch");
                Contact contact = new Contact(tracked.cutter.getUUID(), tracked.age, tracked.before, snapshot(),
                        List.copyOf(tracked.attempts), List.copyOf(tracked.returns),
                        tracked.cutter.isRemoved(), tracked.cutter.getRemovalReason());
                tracked.contact = contact; contacts.add(contact); active = null;
                log("contact " + contact);
            });
            listeners.add(join); listeners.add(startUserPost); listeners.add(pre); listeners.add(impact);
            listeners.add(incoming); listeners.add(damagePost); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, startUserPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damagePost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void onHurtReturn(DamageSource source, float amount, boolean accepted) {
            premise(active != null && active.hit && source.getEntity() == user
                    && (source.getDirectEntity() == active.cutter || source.getDirectEntity() == user),
                    "Target hurt return is not owned by the active natural collision");
            HurtReturn result = new HurtReturn(source, amount, accepted,
                    active.attempts.stream().anyMatch(attempt -> attempt.source == source),
                    id(source.getDirectEntity()), id(source.getEntity()), source.getSourcePosition(), active.cutter.position());
            active.returns.add(result);
            log("hurt-return id=" + active.cutter.getUUID() + " age=" + active.age + " time=" + level.getGameTime()
                    + " result=" + result + " hp=" + target.getHealth());
        }

        private void requireActors() {
            premise(user.isAlive() && !user.isCreative() && !user.isSpectator() && !user.getAbilities().instabuild
                    && !user.getAbilities().invulnerable && user.position().distanceTo(userPosition) < EPS
                    && user.getYRot() == 0 && user.getXRot() == 0 && user.yBodyRot == 0 && user.getYHeadRot() == 0
                    && !user.isUsingItem() && !user.isShiftKeyDown() && user.getOffhandItem().isEmpty()
                    && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && power.getPowerType() == ModPlayerPowers.HAMON.get()
                    && stand.getPowerType() == null && !stand.hasPower() && stand.getSummonedStandEntity() == null
                    && target.isAlive() && !target.isInvulnerable() && target.position().distanceTo(targetPosition) < EPS
                    && !target.isNoGravity() && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1D
                    && target.getAbsorptionAmount() == 0 && target.getArmorValue() == 0
                    && !EntityHamonChargeState.get(target).hasHamonCharge()
                    && Math.abs(HamonAbilityHelpers.hamonDamageMultiplier(target) - 0.2F) < EPS
                    && ticking(user.position()) && ticking(target.position()), "Actor or target admission changed");
        }

        private void press() {
            requireActors();
            float maximumHamon = HamonAbilityHelpers.hamonDamageAmount(target, 2F)
                    * HamonData.MAX_HAMON_STRENGTH_MULTIPLIER * HamonAbilityHelpers.configHamonDamageMultiplier();
            int limit = Math.min(hamon.getStatLevelLimit(false), HamonData.MAX_STAT_LEVEL);
            int pointsCap = limit == HamonData.MAX_STAT_LEVEL ? HamonData.MAX_HAMON_POINTS
                    : HamonData.pointsAtLevel(limit + 1) - 1;
            // HamonData's action conversion is750 energy per point; exclude cap-masked training.
            double maximumPoints = points().total() + 400D
                    * JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get().floatValue() / 750D;
            premise(Float.isFinite(maximumHamon) && 8D * (2D + maximumHamon) < target.getHealth()
                    && Double.isFinite(maximumPoints) && maximumPoints < pointsCap + 1D,
                    "Configured damage or training lacks nonlethal uncapped headroom");
            log("input-admission recharge=" + user.getAttackStrengthScale(1F) + " energy=" + hamon.getEnergy()
                    + " learned=" + hamon.isSkillLearned(ModHamonSkills.HAMON_CUTTER.get())
                    + " main=" + user.getMainHandItem() + " bottles=" + inventory(Items.GLASS_BOTTLE)
                    + " targetIFrames=" + target.invulnerableTime + " targetHealth=" + target.getHealth()
                    + "/" + target.getMaxHealth() + " targetEffects=" + target.getActiveEffects()
                    + " grounded=" + target.onGround() + " targetDelta=" + target.getDeltaMovement()
                    + " supports=" + supports.size() + " stoneSupports=" + supports.keySet().stream()
                    .allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)));
            premise(user.getAttackStrengthScale(1F) == 1F && hamon.getEnergy() > 800F
                    && hamon.isSkillLearned(ModHamonSkills.HAMON_CUTTER.get())
                    && user.getMainHandItem().is(ModItems.SOAP.get()) && user.getMainHandItem().getCount() == 1
                    && inventory(Items.GLASS_BOTTLE) == 0 && target.invulnerableTime == 0
                    && target.getHealth() == target.getMaxHealth() && target.getActiveEffects().isEmpty()
                    && target.onGround() && supports.size() == 6 && supports.keySet().stream()
                    .allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                    "Input recharge, plain soap or fresh target unavailable");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "Registered Cutter CLICK rejected");
            pressed = true;
            var held = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK, 0,
                    BufferingState.clickOnly(), ability.getAbilityId());
            premise(held != null && held.action instanceof HamonCutterAbility.CutterInstance shot
                    && shot.ability == ability && shot == LivingComponentAction.getCurEntityAction(user), "Wrong Cutter action");
            action = (EntityActionInstance) held.action;
            premise(action.getPhase() == ActionPhase.WINDUP && cutters.isEmpty(), "Cutter skipped natural windup");
            log("press userTicks=" + userTicks + " phase=" + phase() + " energy=" + hamon.getEnergy() + " points=" + points());
        }

        private void validate() {
            requireActors();
            premise(paidBurst && cutters.size() == 8 && contacts.size() == 8 && active == null
                    && cutters.values().stream().allMatch(t -> t.contact != null && t.cutter.getOwner() == user),
                    "Natural burst coverage incomplete");
            List<String> mismatches = new ArrayList<>();
            for (int index = 0; index < contacts.size(); index++) {
                Contact c = contacts.get(index);
                premise(c.removed && c.reason == Entity.RemovalReason.DISCARDED
                        && c.attempts.stream().allMatch(a -> a.incoming.isCanceled() == refuse), "Terminal/refusal state invalid");
                double hpLoss = c.before.health - c.after.health;
                double applied = c.attempts.stream().filter(a -> a.applied != null).mapToDouble(a -> a.applied).sum();
                premise(Math.abs(hpLoss - applied) < 1.0E-4, "Health change is not paired with actual damage Post");
                premise(!c.returns.isEmpty() && c.attempts.stream().allMatch(a -> c.returns.stream()
                        .filter(result -> result.source == a.source && result.amount == a.original).count() == 1),
                        "Incoming damage lacks its natural hurt return");
                double trained = c.after.points.total() - c.before.points.total();
                boolean accepted = c.returns.stream().anyMatch(HurtReturn::accepted);
                if ((accepted && trained <= 0) || (!accepted && !c.before.points.equals(c.after.points))) {
                    mismatches.add("training-result@" + index);
                }
                if (!c.before.effects.equals(c.after.effects)) mismatches.add("soap-effects@" + index);
                List<Attempt> physical = c.attempts.stream().filter(a -> a.channel.equals("physical")).toList();
                List<Attempt> hamonAttempts = c.attempts.stream().filter(a -> a.channel.equals("hamon")).toList();
                if (c.returns.size() != 2) {
                    mismatches.add("literal-call-count@" + index);
                }
                else {
                    HurtReturn physicalReturn = c.returns.getFirst();
                    HurtReturn hamonReturn = c.returns.get(1);
                    if (!physicalReturn.source.is(ModDamageTypes.MOD_PROJECTILE) || !Float.isFinite(physicalReturn.amount)
                            || Math.abs(physicalReturn.amount - 2F) >= EPS) {
                        mismatches.add("literal-physical2-first@" + index);
                    }
                    if (!hamonReturn.source.is(ModDamageTypes.HAMON) || !Float.isFinite(hamonReturn.amount)
                            || hamonAttempts.size() != 1
                            || Math.abs(hamonReturn.amount - hamonAttempts.getFirst().expectedHamon) >= EPS) {
                        mismatches.add("literal-hamon0.075-second@" + index);
                    }
                }
                for (HurtReturn result : c.returns) {
                    if (!c.id.equals(result.direct) || !user.getUUID().equals(result.cause)
                            || result.sourcePosition == null || !(result.sourcePosition.distanceTo(result.cutterPosition) < EPS)) {
                        mismatches.add("literal-projectile-source@" + index);
                    }
                }
                if ((index == 0 || refuse) && (physical.size() != 1 || Math.abs(physical.getFirst().original - 2F) >= EPS)) {
                    mismatches.add("physical2@" + index);
                }
                if (hamonAttempts.size() != 1 || Math.abs(hamonAttempts.getFirst().original - hamonAttempts.getFirst().expectedHamon) >= EPS) {
                    mismatches.add("hamon0.075@" + index);
                }
                if (!physical.isEmpty() && (!c.attempts.getFirst().channel.equals("physical") || hamonAttempts.isEmpty())) {
                    mismatches.add("channel-order@" + index);
                }
                for (Attempt a : c.attempts) {
                    if (a.channel.equals("unexpected") || !c.id.equals(a.direct) || !user.getUUID().equals(a.cause)
                            || a.sourcePosition == null || a.sourcePosition.distanceTo(a.cutterPosition) >= EPS) {
                        mismatches.add("projectile-source@" + index);
                    }
                }
                if (refuse && (accepted || applied != 0 || hpLoss != 0 || c.attempts.stream().anyMatch(a -> a.applied != null))) {
                    mismatches.add("refusal-health@" + index);
                }
                if (!refuse && index == 0 && (physical.size() != 1 || physical.getFirst().applied == null
                        || physical.getFirst().applied <= 0 || hamonAttempts.size() != 1
                        || hamonAttempts.getFirst().applied == null || hamonAttempts.getFirst().applied <= 0)) {
                    mismatches.add("first-contact-acceptance");
                }
                log("validated-contact index=" + index + " hpLoss=" + hpLoss + " applied=" + applied
                        + " accepted=" + accepted + " training=" + trained + " iFrames=" + c.before.invulnerability
                        + "->" + c.after.invulnerability + " effects=" + c.before.effects + "->" + c.after.effects);
            }
            log("result hits=" + contacts.size() + " health=" + contacts.getFirst().before.health + "->" + target.getHealth()
                    + " points=" + points() + " mismatches=" + mismatches);
            helper.assertTrue(mismatches.isEmpty(), "CUTTER-ORACLE: source, dose or accepted-result policy differs; see trace");
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 70, "Watchdog expired before the one-cast gate");
                requireActors();
                if (!pressed && userTicks >= 20) press();
                if (paidBurst && !released) release();
                if (contacts.size() == 8) { validate(); close(); helper.succeed(); return; }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private Points points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return new Points(nbt.getInt("StrengthPoints"), nbt.getFloat("PointsIncFrac"));
        }
        private Snapshot snapshot() {
            return new Snapshot(target.getHealth(), target.invulnerableTime, points(),
                    target.getActiveEffects().stream().map(Object::toString).sorted().toList());
        }
        private Vec3 mouth() { return user.getEyePosition(1F).add(0, -0.1, 0); }
        private String phase() { return action == null ? "none" : action.getPhase() + "/" + action.getPhaseTick(); }
        private int inventory(net.minecraft.world.item.Item item) {
            return user.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
        }
        private boolean contains(AABB box) {
            return room.contains(new Vec3(box.minX, box.minY, box.minZ)) && room.contains(new Vec3(box.maxX, box.maxY, box.maxZ));
        }
        private boolean ticking(Vec3 pos) {
            BlockPos block = BlockPos.containing(pos);
            return new ChunkPos(block).equals(chunk) && level.isPositionEntityTicking(block);
        }
        private void release() { if (pressed && !released) { AbilityInput.keyRelease(KEY, user); released = true; } }
        private void premise(boolean condition, String message) { helper.assertTrue(condition, "CUTTER-PREMISE: " + message); }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) {
                observerFailure = error;
                JojoMod.LOGGER.error("HAMON-CUTTER-DAMAGE observer failed; refusal=" + refuse, error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("HAMON-CUTTER-DAMAGE {} {}", refuse ? "refused" : "normal", message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            try {
                for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
                listeners.clear();
            }
            finally {
                cleanupStep(this::release, failures);
                for (Tracked tracked : cutters.values()) cleanupStep(() -> { if (!tracked.cutter.isRemoved()) tracked.cutter.discard(); }, failures);
                if (target != null) cleanupStep(target::discard, failures);
                if (user != null) {
                    cleanupStep(user::stopUsingItem, failures);
                    cleanupStep(() -> user.getInventory().clearContent(), failures);
                    cleanupStep(user::discard, failures);
                }
                for (Map.Entry<BlockPos, BlockState> entry : supports.entrySet()) {
                    cleanupStep(() -> level.setBlock(entry.getKey(), entry.getValue(), 3), failures);
                }
                cleanupStep(() -> {
                    premise(cutters.values().stream().allMatch(t -> t.cutter.isRemoved() && level.getEntity(t.cutter.getUUID()) == null)
                            && (target == null || target.isRemoved() && level.getEntity(target.getUUID()) == null)
                            && (user == null || user.isRemoved() && level.getEntity(user.getUUID()) == null), "Owned cleanup incomplete");
                    for (Map.Entry<BlockPos, BlockState> entry : supports.entrySet()) {
                        premise(entry.getValue().isAir() && level.getBlockState(entry.getKey()).equals(entry.getValue())
                                && level.getBlockEntity(entry.getKey()) == null, "Original support AIR state not restored");
                    }
                    if (roomVerified) for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMax)) {
                        premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "Room changed during fixture");
                    }
                }, failures);
            }
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException("CUTTER-CLEANUP: verification failed");
                failures.forEach(failure::addSuppressed); throw failure;
            }
            log("cleanup-verified listeners=0 ownedCutters=" + cutters.size() + " actorsGone=true restoredAirSupports=" + supports.size());
        }
        private void cleanupStep(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        private void closeAfterFailure(Throwable primary) {
            try { close(); } catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup); JojoMod.LOGGER.error("Cutter cleanup failed; original failure retained", cleanup);
            }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
            if (test.getError() != null) closeAfterFailure(test.getError()); else close();
        }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }

    private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
}
