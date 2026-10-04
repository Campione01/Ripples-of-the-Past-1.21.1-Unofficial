package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
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
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
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
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfBindingEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.JojoModUtil;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonScarfBindingGameTests {
    private static final double EPSILON = 1.0E-5D;

    private HamonScarfBindingGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_binding_source", timeoutTicks = 100)
    public static void boundScarfPeriodicHamonUsesItsProjectileSource(GameTestHelper helper) {
        start(helper, false, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_binding_mixed_tnt", timeoutTicks = 100)
    public static void boundScarfMixedEntityRayPreservesOwnedTnt(GameTestHelper helper) {
        start(helper, true, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_binding_terminal_tnt", timeoutTicks = 100)
    public static void boundScarfTargetLossPreservesTerminalBlockFallback(GameTestHelper helper) {
        start(helper, false, true);
    }

    private static void start(GameTestHelper helper, boolean mixed, boolean terminal) {
        Fixture fixture = new Fixture(helper, mixed, terminal);
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

    private record Attempt(String channel, float amount, UUID direct, UUID cause, float energy, boolean canceled) {}
    private record ObservedDamage(LivingIncomingDamageEvent event, float energy) {}
    private record Impact(HitResult.Type type, UUID target, BlockPos block, Vec3 point, boolean canceled) {}
    private record Ray(long time, int age, Vec3 root, Vec3 tip, Vec3 delta, AABB query, List<UUID> candidates, List<UUID> hittable,
            AABB targetBox, Vec3 targetClip, BlockHitResult collider, BlockHitResult outline, boolean permission,
            float health, boolean targetAlive, boolean targetRemoved, double points, float targetDamage,
            float ownerMultiplier, float configMultiplier, float expectedDamage, int stunTicks, float cooldown, boolean retracting) {}
    private record Contact(UUID binding, Ray before, Vec3 tip, Vec3 delta, UUID attached, int lifetime,
            boolean removed, boolean retracting, float health, double points, int stunTicks, float cooldown, boolean cooldownActive, int userTicks,
            BlockState blockAfter, List<Impact> impacts, List<Attempt> attempts, List<Boolean> destroyCanceled,
            int drops, int primedTnt) {}
    private record AxeAttack(int number, int bindingAge, int userTicks, float recharge, double attackAttribute,
            float healthBefore, float healthAfter, boolean aliveAfter, boolean removedAfter, List<Attempt> attempts) {}
    private record CooldownAfterTick(long time, int userTicks, float fraction, boolean active, boolean bindingRemoved,
            Entity.RemovalReason removalReason, int stunTicks) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean mixed;
        private final boolean terminal;
        private final List<Object> listeners = new ArrayList<>();
        private final List<SatiporojaScarfBindingEntity> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<PrimedTnt> primed = new ArrayList<>();
        private final List<Entity> deathLoot = new ArrayList<>();
        private final List<AxeAttack> axeAttacks = new ArrayList<>();
        private final List<ObservedDamage> axeDamage = new ArrayList<>();
        private final List<Contact> liveSamples = new ArrayList<>();
        private final List<ObservedDamage> meleeDamage = new ArrayList<>();
        private final List<ObservedDamage> periodicDamage = new ArrayList<>();
        private final List<ProjectileImpactEvent> impacts = new ArrayList<>();
        private final List<LivingDestroyBlockEvent> destruction = new ArrayList<>();
        private Player user;
        private Cow target;
        private HamonData hamon;
        private SatiporojaScarfBindingEntity binding;
        private Vec3 userPosition;
        private Vec3 targetPosition;
        private AABB room;
        private BlockPos tntPos;
        private BlockState originalTntCell;
        private Ray before;
        private Contact contact;
        private Contact firstPeriodic;
        private CooldownAfterTick cooldownAfterTick;
        private RuntimeException observerFailure;
        private int userTicks;
        private int targetTicks;
        private int bindingTicks;
        private int userTicksAtAttack;
        private int userTicksAtEquip;
        private float energyBeforeAttack;
        private float energyAtJoin;
        private double pointsAfterMelee;
        private boolean meleeCall;
        private boolean axeCall;
        private boolean axeEquipped;
        private boolean terminalReady;
        private boolean attacked;
        private boolean permissionProbe;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean mixed, boolean terminal) {
            this.helper = helper;
            level = helper.getLevel();
            this.mixed = mixed;
            this.terminal = terminal;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX() + 5, y - 2, chunk.getMinBlockZ() + 2);
            BlockPos max = new BlockPos(chunk.getMinBlockX() + 12, y + 5, chunk.getMinBlockZ() + 10);
            room = AABB.encapsulatingFullBlocks(min, max);
            helper.assertTrue(max.getY() < level.getMaxBuildHeight()
                            && level.getEntities((Entity) null, room).isEmpty(), "Binding fixture room is unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Binding fixture room is obstructed");
            }
            userPosition = new Vec3(chunk.getMinBlockX() + 8.5D, y, chunk.getMinBlockZ() + 4.25D);
            targetPosition = userPosition.add(0, 0, 2.6D);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setShiftKeyDown(true);
            helper.assertTrue(level.addFreshEntity(user), "Could not add Binding user");
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.SATIPOROJA_SCARF.get());
            hamon.learnSkill(ModHamonSkills.SNAKE_MUFFLER.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Could not create Binding captive");
            target.setNoGravity(true);
            target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(targetPosition);
            helper.assertTrue(level.addFreshEntity(target) && !target.isNoAi() && !target.isInvulnerable(),
                    "Binding captive must admit normal melee and STUN");
            if (terminal) helper.assertTrue(target.getMaxHealth() == 10 && target.getHealth() == 10,
                    "Terminal Binding control requires default Cow health");
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())
                            && hamon.isSkillLearned(ModHamonSkills.SNAKE_MUFFLER.get())
                            && !hamon.isSkillLearned(ModHamonSkills.NATURAL_TALENT.get())
                            && !hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())
                            && HamonAbilityHelpers.configHamonDamageMultiplier() > 0
                            && JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() > 0,
                    "Binding skill or damage/training configuration is invalid");
            registerObservers();
            log("setup user=" + user.getUUID() + " at=" + userPosition + " captive=" + target.getUUID()
                    + " at=" + targetPosition + " noGravity=true captiveAI=true speed=0 knockbackResistance=1");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = joinEvent -> {
                if (closed || joinEvent.getLevel() != level) return;
                if (terminal && target != null && (axeCall || !target.isAlive())
                        && target.getBoundingBox().inflate(1).contains(joinEvent.getEntity().position())) {
                    Entity createdLoot = joinEvent.getEntity();
                    if (createdLoot instanceof ExperienceOrb || createdLoot instanceof ItemEntity item
                            && (item.getItem().is(Items.BEEF) || item.getItem().is(Items.COOKED_BEEF) || item.getItem().is(Items.LEATHER))) {
                        deathLoot.add(createdLoot);
                    }
                }
                if (tntPos != null && new AABB(tntPos).inflate(1).contains(joinEvent.getEntity().position())) {
                    if (joinEvent.getEntity() instanceof PrimedTnt explosive) {
                        primed.add(explosive);
                        observe(() -> helper.assertTrue(false, "Owned Binding TNT unexpectedly primed"));
                    }
                    if (before != null && impacts.stream().anyMatch(hitEvent -> hitEvent.getRayTraceResult() instanceof BlockHitResult hit
                                    && hit.getBlockPos().equals(tntPos)) && joinEvent.getEntity() instanceof ItemEntity item
                            && item.getItem().is(Items.TNT)) drops.add(item);
                }
                if (!(joinEvent.getEntity() instanceof SatiporojaScarfBindingEntity created) || created.getOwner() != user) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(meleeCall && binding == null && spawned.size() == 1 && !joinEvent.isCanceled(),
                            "Unexpected Binding spawn outside successful melee");
                    binding = created;
                    energyAtJoin = hamon.getEnergy();
                    helper.assertTrue(binding.getType() == ModEntityTypes.SATIPOROJA_SCARF_BINDING.get()
                                    && binding.getOwner() == user && binding.getEntityAttachedTo() == target
                                    && binding.ticksLifespan() == 100 && binding.getSpeedFactor() == 1
                                    && stunTicks() == 100 && target.isNoAi(), "Registered Binding attachment or STUN is invalid");
                    log("spawn binding=" + binding.getUUID() + " attached=" + id(binding.getEntityAttachedTo())
                            + " life=" + binding.ticksLifespan() + " tip=" + binding.position()
                            + " energyAtJoin=" + energyAtJoin + " stun=" + stunTicks());
                });
            };
            Consumer<EntityTickEvent.Pre> pre = preEvent -> observe(() -> {
                if (preEvent.getEntity() != binding || contact != null) return;
                helper.assertTrue(attacked && !preEvent.isCanceled() && before == null
                                && binding.getOwner() == user && binding.getEntityAttachedTo() == target
                                && binding.ticksLifespan() == 100 && level.isPositionEntityTicking(binding.blockPosition()),
                        "Binding natural tick lost its live attachment");
                requireActors();
                impacts.clear();
                periodicDamage.clear();
                destruction.clear();
                before = ray();
                log("pre " + before + " collider=" + blockSummary(before.collider) + " outline=" + blockSummary(before.outline));
                if (before.age == 2) requireContactRay(before);
                if (terminal) helper.assertTrue(before.age < 100, "Terminal Binding setup exceeded its real lifespan");
                if (terminalReady) requireTerminalRay(before);
            });
            Consumer<ProjectileImpactEvent> impact = impactEvent -> observe(() -> {
                if (impactEvent.getProjectile() != binding || contact != null) return;
                helper.assertTrue(before != null && (before.age == 2 || terminal && before.age < 100) && !impactEvent.isCanceled(),
                        "Binding impact is early, canceled or outside its natural tick");
                HitResult hit = impactEvent.getRayTraceResult();
                helper.assertTrue(hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target
                                || (mixed || terminalReady) && hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(tntPos),
                        "Binding impact escaped the owned captive/TNT");
                impacts.add(impactEvent);
                log("impact index=" + impacts.size() + " type=" + hit.getType() + " point=" + hit.getLocation());
            });
            Consumer<LivingIncomingDamageEvent> damage = damageEvent -> observe(() -> {
                if (damageEvent.getEntity() != target || contact != null) return;
                ObservedDamage observed = new ObservedDamage(damageEvent, hamon.getEnergy());
                if (meleeCall) meleeDamage.add(observed);
                else if (axeCall) axeDamage.add(observed);
                else {
                    helper.assertTrue(before != null && impacts.stream().anyMatch(hitEvent ->
                                    hitEvent.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == target),
                            "Captive damage lacks its natural Binding impact");
                    periodicDamage.add(observed);
                }
            });
            Consumer<LivingDestroyBlockEvent> destroy = destroyEvent -> observe(() -> {
                if (!permissionProbe && before != null && destroyEvent.getEntity() == user && destroyEvent.getPos().equals(tntPos)) {
                    destruction.add(destroyEvent);
                }
            });
            Consumer<EntityTickEvent.Post> post = postEvent -> observe(() -> {
                if (postEvent.getEntity() == user) {
                    userTicks++;
                    if (terminal && contact != null && cooldownAfterTick == null) {
                        cooldownAfterTick = new CooldownAfterTick(level.getGameTime(), userTicks, cooldown(),
                                user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()), binding.isRemoved(),
                                binding.getRemovalReason(), stunTicks());
                        log("cooldown-next-user-post " + cooldownAfterTick);
                    }
                }
                if (postEvent.getEntity() == target) targetTicks++;
                if (postEvent.getEntity() != binding || contact != null) return;
                helper.assertTrue(before != null && before.time == level.getGameTime() && before.age == binding.tickCount,
                        "Binding lacks its natural Pre/Post pair");
                bindingTicks++;
                Contact sample = new Contact(binding.getUUID(), before, binding.position(), binding.getDeltaMovement(),
                        id(binding.getEntityAttachedTo()), binding.ticksLifespan(), binding.isRemoved(),
                        binding.saveWithoutId(new CompoundTag()).getBoolean("IsRetracting"), target.getHealth(), points(),
                        stunTicks(), cooldown(), user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()), userTicks,
                        tntPos == null ? null : level.getBlockState(tntPos),
                        impacts.stream().map(Fixture::impact).toList(), periodicDamage.stream().map(Fixture::attempt).toList(),
                        destruction.stream().map(LivingDestroyBlockEvent::isCanceled).toList(), drops.size(), primed.size());
                log("post " + sample);
                if (before.age == 1) {
                    helper.assertTrue(before.delta.lengthSqr() < EPSILON && near(before.root, before.tip)
                                    && impacts.isEmpty() && periodicDamage.isEmpty() && !sample.removed
                                    && near(sample.tip, attachedTip()) && sample.delta.lengthSqr() < EPSILON
                                    && sample.health == before.health && Math.abs(sample.points - pointsAfterMelee) < EPSILON,
                            "Binding first natural attachment step changed");
                    if (mixed) placeTnt();
                }
                else if (before.age == 2 && terminal) {
                    firstPeriodic = sample;
                    liveSamples.add(sample);
                }
                else if ((!terminal && before.age == 2) || terminalReady) {
                    contact = sample;
                    // The completed first periodic sample is isolated from later captive damage.
                    if (!terminal && !binding.isRemoved()) binding.discard();
                }
                else if (terminal && before.age > 2) liveSamples.add(sample);
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
            listeners.add(destroy);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDestroyBlockEvent.class, destroy);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Ray ray() {
            Vec3 root = binding.getOriginPoint(1);
            Vec3 tip = binding.position();
            Vec3 delta = binding.getDeltaMovement();
            Vec3 end = tip.add(delta);
            AABB query = binding.getBoundingBox().expandTowards(root.subtract(end)).inflate(1);
            List<Entity> queried = level.getEntities(binding, query, entity -> entity != user);
            List<UUID> candidates = queried.stream().map(Entity::getUUID).toList();
            List<UUID> hittable = queried.stream().filter(entity -> !entity.isSpectator() && entity.canBeHitByProjectile())
                    .map(Entity::getUUID).toList();
            AABB targetBox = target.getBoundingBox().inflate(target.getPickRadius() + binding.getBbWidth() / 2.0D);
            Vec3 targetClip = targetBox.contains(root) ? root : targetBox.clip(root, end).orElse(null);
            BlockHitResult collider = level.clip(new ClipContext(root, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, binding));
            BlockHitResult outline = level.clip(new ClipContext(root, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, binding));
            boolean permission = false;
            if (tntPos != null) {
                permissionProbe = true;
                try { permission = JojoModUtil.canEntityDestroy(level, tntPos, level.getBlockState(tntPos), user); }
                finally { permissionProbe = false; }
            }
            float targetDamage = HamonAbilityHelpers.hamonDamageAmount(target, 0.003F);
            float ownerMultiplier = hamon.getHamonDamageMultiplier();
            float configMultiplier = HamonAbilityHelpers.configHamonDamageMultiplier();
            return new Ray(level.getGameTime(), binding.tickCount, root, tip, delta, query, candidates, hittable, targetBox, targetClip,
                    collider, outline, permission, target.getHealth(), target.isAlive(), target.isRemoved(), points(),
                    targetDamage, ownerMultiplier, configMultiplier, targetDamage * (ownerMultiplier * configMultiplier),
                    stunTicks(), cooldown(), binding.saveWithoutId(new CompoundTag()).getBoolean("IsRetracting"));
        }

        private void placeTnt() {
            helper.assertTrue(tntPos == null && binding.getEntityAttachedTo() == target && !binding.isRemoved(),
                    "TNT setup requires the real Binding attachment");
            Vec3 root = binding.getOriginPoint(1);
            tntPos = BlockPos.containing(root.add(binding.position()).scale(0.5D));
            helper.assertTrue(room.contains(Vec3.atCenterOf(tntPos)) && level.isEmptyBlock(tntPos)
                            && !new AABB(tntPos).intersects(user.getBoundingBox())
                            && !new AABB(tntPos).intersects(target.getBoundingBox()) && !level.hasNeighborSignal(tntPos),
                    "Owned tether TNT would overlap an actor or powered block");
            originalTntCell = level.getBlockState(tntPos);
            helper.assertTrue(level.setBlockAndUpdate(tntPos, Blocks.TNT.defaultBlockState()), "Could not place owned tether TNT");
            log("placed-tnt pos=" + tntPos + " root=" + root + " attachedTip=" + binding.position()
                    + " captive=" + target.getUUID() + " hardness=" + level.getBlockState(tntPos).getDestroySpeed(level, tntPos));
        }

        private void requireContactRay(Ray ray) {
            helper.assertTrue(ray.age == 2 && !ray.retracting && near(ray.tip, attachedTip()) && ray.delta.lengthSqr() < EPSILON
                            && near(ray.root, user.getEyePosition(1).add(0, -0.3D, 0))
                            && ray.candidates.equals(List.of(target.getUUID())) && ray.hittable.equals(List.of(target.getUUID())) && ray.targetClip != null
                            && ray.expectedDamage > 0 && ray.health > ray.expectedDamage && ray.stunTicks > 0
                            && Math.abs(ray.points - pointsAfterMelee) < EPSILON,
                    "Binding first periodic target ray or attachment is invalid");
            if (!mixed) helper.assertTrue(ray.collider.getType() == HitResult.Type.MISS && ray.outline.getType() == HitResult.Type.MISS,
                    "Binding source-only ray found a block");
            else requireTntRay(ray);
        }

        private void requireTntRay(Ray ray) {
            BlockState tnt = level.getBlockState(tntPos);
            helper.assertTrue(tnt.equals(Blocks.TNT.defaultBlockState()) && tnt.getDestroySpeed(level, tntPos) == 0
                                && !tnt.getCollisionShape(level, tntPos).isEmpty() && ray.permission
                                && !binding.isFiery() && !binding.isOnFire()
                                && ray.collider.getType() == HitResult.Type.BLOCK && ray.outline.getType() == HitResult.Type.BLOCK
                                && ray.collider.getBlockPos().equals(tntPos) && ray.outline.getBlockPos().equals(tntPos)
                                && ray.collider.getDirection() == ray.outline.getDirection()
                                && near(ray.collider.getLocation(), ray.outline.getLocation())
                                && ray.collider.getLocation().distanceToSqr(ray.root) < ray.targetClip.distanceToSqr(ray.root),
                        "Binding ray lacks qualifying permitted TNT before its captive endpoint");
        }

        private void requireTerminalRay(Ray ray) {
            helper.assertTrue(terminal && terminalReady && !ray.targetAlive && !ray.targetRemoved && ray.health <= 0
                            && binding.getEntityAttachedTo() == target && !binding.isRemoved() && ray.age < 100
                            && ray.candidates.contains(target.getUUID()) && ray.hittable.isEmpty() && ray.targetClip != null
                            && near(ray.tip, attachedTip()) && ray.delta.lengthSqr() < EPSILON
                            && ray.root.distanceToSqr(ray.tip) > 1 && ray.cooldown > 0,
                    "Terminal Binding ray lacks a present dead captive and empty hit candidates");
            requireTntRay(ray);
        }

        private void requireActors() {
            helper.assertTrue(user.isAlive() && (target.isAlive() || terminalReady && !target.isRemoved()) && near(user.position(), userPosition)
                            && near(target.position(), targetPosition) && user.getYRot() == 0 && user.getXRot() == 0
                            && !user.isCreative() && !user.getAbilities().instabuild && !target.isInvulnerable()
                            && !user.isOnFire() && !target.isOnFire() && !EntityHamonChargeState.get(target).hasHamonCharge()
                            && level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(target.blockPosition()),
                    "Binding actors moved or lost normal ticking/damage eligibility");
        }

        private void attack() {
            requireActors();
            helper.assertTrue(user.isShiftKeyDown() && user.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                            && user.getMainHandItem().is(ModItems.SATIPOROJA_SCARF.get()) && user.getMainHandItem().getCount() == 1
                            && !user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()) && hamon.getEnergy() >= 600
                            && !target.isNoAi() && stunTicks() == 0 && user.distanceToSqr(target) < 9 && user.hasLineOfSight(target),
                    "Registered sneaking scarf melee prerequisites are invalid");
            float healthBefore = target.getHealth();
            double pointsBefore = points();
            float expectedMeleeHamon = HamonAbilityHelpers.hamonDamageAmount(target, 0.6F)
                    * (hamon.getHamonDamageMultiplier() * HamonAbilityHelpers.configHamonDamageMultiplier());
            energyBeforeAttack = hamon.getEnergy();
            meleeCall = true;
            try { user.attack(target); }
            finally { meleeCall = false; }
            List<Attempt> attempts = meleeDamage.stream().map(Fixture::attempt).toList();
            log("melee attempts=" + attempts + " health=" + healthBefore + "->" + target.getHealth()
                    + " energy=" + energyBeforeAttack + "->" + hamon.getEnergy() + " joinEnergy=" + energyAtJoin
                    + " points=" + pointsBefore + "->" + points() + " stun=" + stunTicks() + " cooldown=" + cooldown());
            List<Attempt> hamonHits = attempts.stream().filter(hit -> hit.channel.equals("hamon")).toList();
            helper.assertTrue(attempts.size() == 2 && attempts.get(0).channel.equals("physical") && hamonHits.size() == 1
                            && attempts.stream().allMatch(hit -> !hit.canceled && hit.amount > 0
                                    && user.getUUID().equals(hit.direct) && user.getUUID().equals(hit.cause))
                            && Math.abs(hamonHits.get(0).amount - expectedMeleeHamon) < 1.0E-5F
                            && target.getHealth() < healthBefore && target.isAlive(), "Scarf binding lacks accepted physical and Hamon melee");
            helper.assertTrue(binding != null && spawned.size() == 1 && binding.getEntityAttachedTo() == target && stunTicks() == 100
                            && Math.abs(energyBeforeAttack - hamonHits.get(0).energy - 500) < 0.001F
                            && Math.abs(energyBeforeAttack - energyAtJoin - 600) < 0.001F
                            && Math.abs(energyBeforeAttack - hamon.getEnergy() - 600) < 0.001F
                            && Math.abs(cooldown() - 1) < EPSILON, "Binding creation did not pay 500+100 or install STUN/cooldown");
            double initialAward = 500.0D * JojoModConfig.getCommonConfigInstance(false).hamonPointsMultiplier.get() / 750.0D;
            helper.assertTrue(Math.abs(points() - pointsBefore - initialAward) < 1.0E-4D,
                    "Binding creation lost the successful melee training award");
            pointsAfterMelee = points();
            userTicksAtAttack = userTicks;
            attacked = true;
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Binding fixture watchdog expired");
                if (contact != null) {
                    if (terminal && cooldownAfterTick == null) {
                        helper.assertTrue(contact.removed && !contact.cooldownActive,
                                "Binding target loss did not immediately deactivate its cooldown");
                        helper.runAfterDelay(1, this::poll);
                        return;
                    }
                    if (terminal) validateTerminal();
                    else validate();
                    close();
                    helper.succeed();
                    return;
                }
                requireActors();
                if (!attacked && userTicks >= 2 && targetTicks >= 2 && user.getAttackStrengthScale(0.5F) >= 0.999F) attack();
                if (terminal && firstPeriodic != null && !terminalReady) prepareTargetLoss();
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void prepareTargetLoss() {
            if (!axeEquipped) {
                user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
                axeEquipped = true;
                userTicksAtEquip = userTicks;
                log("equipped-axe userTicks=" + userTicks + " bindingAge=" + binding.tickCount + " health=" + target.getHealth());
            }
            if (userTicks <= userTicksAtEquip || user.getAttackStrengthScale(0.5F) != 1.0F) return;
            helper.assertTrue(axeAttacks.size() < 2 && binding.tickCount < 100 && binding.getEntityAttachedTo() == target
                            && !binding.isRemoved() && target.isAlive() && !target.isRemoved()
                            && user.getMainHandItem().is(Items.NETHERITE_AXE) && user.fallDistance == 0 && !user.isSprinting()
                            && user.distanceToSqr(target) < 9 && user.hasLineOfSight(target) && cooldown() > 0,
                    "Terminal Binding axe attack lost its natural melee prerequisites");
            double attackAttribute = user.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(Math.abs(attackAttribute - 10) < EPSILON, "Ordinary netherite axe attack attribute is not ten");
            float healthBefore = target.getHealth();
            float recharge = user.getAttackStrengthScale(0.5F);
            axeDamage.clear();
            axeCall = true;
            try { user.attack(target); }
            finally { axeCall = false; }
            AxeAttack attack = new AxeAttack(axeAttacks.size() + 1, binding.tickCount, userTicks, recharge, attackAttribute,
                    healthBefore, target.getHealth(), target.isAlive(), target.isRemoved(), axeDamage.stream().map(Fixture::attempt).toList());
            axeAttacks.add(attack);
            log("axe-attack " + attack);
            helper.assertTrue(attack.attempts.size() == 1 && attack.attempts.get(0).channel.equals("physical")
                            && !attack.attempts.get(0).canceled && user.getUUID().equals(attack.attempts.get(0).direct)
                            && user.getUUID().equals(attack.attempts.get(0).cause)
                            && Math.abs(attack.attempts.get(0).amount - attackAttribute) < EPSILON
                            && attack.healthAfter < attack.healthBefore && !attack.removedAfter,
                    "Terminal Binding captive did not take the ordinary full axe hit");
            if (!attack.aliveAfter) {
                helper.assertTrue(attack.healthAfter <= 0 && binding.getEntityAttachedTo() == target
                                && !binding.isRemoved() && binding.tickCount < 100,
                        "Terminal Binding lost the dead captive before its natural tick");
                placeTnt();
                terminalReady = true;
            }
            else helper.assertTrue(axeAttacks.size() < 2, "Two ordinary recharged axe attacks did not kill the captive");
        }

        private void validateTerminal() {
            Contact hit = contact;
            log("terminal-result " + hit + " axeAttacks=" + axeAttacks + " liveSamples=" + liveSamples.size()
                    + " deathLoot=" + deathLoot.stream().map(entity -> entity.getType() + ":" + entity.getUUID()).toList()
                    + " cooldownAfterTick=" + cooldownAfterTick);
            helper.assertTrue(firstPeriodic != null && firstPeriodic.before.age == 2 && !liveSamples.isEmpty()
                            && liveSamples.stream().allMatch(sample -> !sample.removed && sample.lifetime == 100
                                    && sample.attempts.size() == 1 && sample.attempts.get(0).channel.equals("hamon")
                                    && !sample.attempts.get(0).canceled && sample.attempts.get(0).amount > 0
                                    && user.getUUID().equals(sample.attempts.get(0).cause)
                                    && Math.abs(sample.attempts.get(0).amount - sample.before.expectedDamage) < 1.0E-7F
                                    && sample.health < sample.before.health
                                    && Math.abs(sample.points - pointsAfterMelee) < EPSILON),
                    "Terminal control lacks its prior naturally damaging live Binding");
            helper.assertTrue(!axeAttacks.isEmpty() && axeAttacks.size() <= 2
                            && !axeAttacks.get(axeAttacks.size() - 1).aliveAfter && !hit.before.targetAlive && !hit.before.targetRemoved
                            && hit.before.age < 100 && hit.before.hittable.isEmpty() && hit.attempts.isEmpty()
                            && hit.impacts.size() == 1 && hit.impacts.get(0).type == HitResult.Type.BLOCK
                            && tntPos.equals(hit.impacts.get(0).block) && !hit.impacts.get(0).canceled
                            && hit.destroyCanceled.equals(List.of(false)) && hit.blockAfter.isAir() && hit.primedTnt == 0,
                    "Terminal Binding did not perform its real permitted block-only destruction");
            helper.assertTrue(hit.removed && binding.getRemovalReason() == Entity.RemovalReason.DISCARDED && hit.lifetime == 100
                            && !hit.cooldownActive && hit.health <= 0
                            && Math.abs(hit.points - pointsAfterMelee) < EPSILON,
                    "Binding target loss did not naturally discard and clear its cooldown");
            // A zero-length vanilla cooldown is inactive immediately; its percentage settles on the next user tick.
            helper.assertTrue(cooldownAfterTick != null && cooldownAfterTick.userTicks == hit.userTicks + 1
                            && cooldownAfterTick.time >= hit.before.time && cooldownAfterTick.fraction == 0 && !cooldownAfterTick.active
                            && cooldownAfterTick.bindingRemoved && cooldownAfterTick.removalReason == Entity.RemovalReason.DISCARDED
                            && cooldownAfterTick.stunTicks > 0 && cooldownAfterTick.stunTicks <= hit.stunTicks
                            && cooldown() == 0 && !user.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get()),
                    "Binding cooldown did not settle to zero on the next natural user tick");
        }

        private void validate() {
            Contact hit = contact;
            log("result " + hit);
            helper.assertTrue(bindingTicks == 2 && hit.before.age == 2 && hit.lifetime == 100 && !hit.removed
                            && target.getUUID().equals(hit.attached) && near(hit.tip, attachedTip())
                            && hit.delta.lengthSqr() < EPSILON && hit.stunTicks > 0 && hit.primedTnt == 0,
                    "Binding first contact lost its live captive or lifetime contract");
            List<Impact> entityHits = hit.impacts.stream().filter(impact -> impact.type == HitResult.Type.ENTITY).toList();
            List<Impact> blockHits = hit.impacts.stream().filter(impact -> impact.type == HitResult.Type.BLOCK).toList();
            helper.assertTrue(entityHits.size() == 1 && target.getUUID().equals(entityHits.get(0).target)
                            && hit.impacts.stream().noneMatch(Impact::canceled) && blockHits.size() <= 1 && hit.attempts.size() == 1,
                    "Binding sample lacks one uncanceled captive impact and periodic attempt");
            if (!blockHits.isEmpty()) helper.assertTrue(mixed && hit.impacts.size() == 2
                            && hit.impacts.get(0).type == HitResult.Type.BLOCK && hit.impacts.get(1).type == HitResult.Type.ENTITY
                            && tntPos.equals(blockHits.get(0).block)
                            && blockHits.get(0).point.distanceToSqr(hit.before.root) < entityHits.get(0).point.distanceToSqr(hit.before.root),
                    "Binding natural mixed-impact order differs from the independent ray");
            Attempt damage = hit.attempts.get(0);
            helper.assertTrue(damage.channel.equals("hamon") && !damage.canceled && damage.amount > 0
                            && user.getUUID().equals(damage.cause) && Math.abs(damage.amount - hit.before.expectedDamage) < 1.0E-7F
                            && hit.health < hit.before.health && Math.abs(hit.points - hit.before.points) < EPSILON
                            && Math.abs(hit.points - pointsAfterMelee) < EPSILON,
                    "Binding periodic damage changed scaling, acceptance or training");
            float expectedCooldown = 1.0F - (userTicks - userTicksAtAttack) / 100.0F;
            helper.assertTrue(hit.cooldown > 0 && Math.abs(cooldown() - expectedCooldown) < 1.0E-5F,
                    "Binding cooldown did not retain its natural 100-tick schedule");
            if (mixed) helper.assertTrue(blockHits.isEmpty() && hit.destroyCanceled.isEmpty()
                            && hit.blockAfter.equals(Blocks.TNT.defaultBlockState()) && hit.drops == 0,
                    "Binding mixed entity ray destroyed or attempted its owned TNT");
            else {
                helper.assertTrue(blockHits.isEmpty() && hit.destroyCanceled.isEmpty(), "Source-only Binding touched a block");
                helper.assertTrue(hit.binding.equals(damage.direct), "Binding periodic Hamon direct source is not its projectile");
            }
        }

        private Vec3 attachedTip() { return new Vec3(target.getX(), target.getY(0.5D), target.getZ()); }
        private int stunTicks() {
            MobEffectInstance stun = target.getEffect(ModStatusEffects.STUN);
            return stun == null ? 0 : stun.getDuration();
        }
        private float cooldown() { return user.getCooldowns().getCooldownPercent(ModItems.SATIPOROJA_SCARF.get(), 0); }
        private double points() {
            CompoundTag nbt = hamon.serializeNBT(level.registryAccess());
            return nbt.getInt("StrengthPoints") + (double) nbt.getFloat("PointsIncFrac");
        }
        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private static UUID id(Entity entity) { return entity == null ? null : entity.getUUID(); }
        private static String blockSummary(BlockHitResult hit) {
            return hit.getType() + ":" + hit.getBlockPos() + ":" + hit.getDirection() + ":" + hit.getLocation();
        }
        private static Attempt attempt(ObservedDamage observed) {
            LivingIncomingDamageEvent event = observed.event;
            String channel = event.getSource().is(ModDamageTypes.HAMON) ? "hamon"
                    : event.getSource().is(DamageTypes.PLAYER_ATTACK) ? "physical" : "unexpected";
            return new Attempt(channel, event.getOriginalAmount(), id(event.getSource().getDirectEntity()),
                    id(event.getSource().getEntity()), observed.energy, event.isCanceled());
        }
        private static Impact impact(ProjectileImpactEvent event) {
            HitResult hit = event.getRayTraceResult();
            return new Impact(hit.getType(), hit instanceof EntityHitResult entity ? entity.getEntity().getUUID() : null,
                    hit instanceof BlockHitResult block ? block.getBlockPos().immutable() : null, hit.getLocation(), event.isCanceled());
        }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Hamon Binding observer failure: mixed=" + mixed + " terminal=" + terminal, error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("HAMON-BINDING mixed={} terminal={} {}", mixed, terminal, message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            before = null;
            try {
                for (SatiporojaScarfBindingEntity created : spawned) if (!created.isRemoved()) created.discard();
                for (PrimedTnt explosive : primed) if (!explosive.isRemoved()) explosive.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                for (Entity loot : deathLoot) if (!loot.isRemoved()) loot.discard();
                if (user != null) {
                    user.setShiftKeyDown(false);
                    user.stopUsingItem();
                    user.discard();
                }
                if (target != null) target.discard();
            }
            finally {
                if (originalTntCell != null) level.setBlockAndUpdate(tntPos, originalTntCell);
                boolean restored = originalTntCell == null || level.getBlockState(tntPos).equals(originalTntCell);
                log("cleanup listeners=0 bindings=" + spawned.size() + " tntRestored=" + restored
                        + " drops=" + drops.size() + " primed=" + primed.size() + " deathLoot=" + deathLoot.size());
                if (!restored) throw new IllegalStateException("Binding TNT cell was not exactly restored");
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
