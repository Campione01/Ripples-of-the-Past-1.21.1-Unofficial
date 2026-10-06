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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
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
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersDeadOwnerGameTests {
    private static final double EPS = 1.0E-6D;
    private ClackersDeadOwnerGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_dead_owner_recoverable", timeoutTicks = 160)
    public static void ordinaryClackersRemainAfterTheirOwnersNaturalWeaponDeath(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper); helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record Frame(long time, int age, Vec3 position, Vec3 velocity, boolean grounded,
            boolean removed, Entity.RemovalReason reason, boolean sameOwner, UUID ownerId,
            boolean ownerAlive, float ownerHealth, boolean ownerRemoved, int ownerAge, int deathTime,
            Vec3 ownerPosition, Vec3 ownerVelocity, boolean ticking, boolean ownerTicking,
            boolean creativeOnly, int savedAge) {}
    private record Step(Frame before, Frame after) {}
    private record Death(LivingDeathEvent event, long time, int actorAge, int swing) {}
    private static final class Hit {
        final LivingIncomingDamageEvent incoming;
        final float healthBefore;
        final int swing;
        Float applied, healthAfter;
        Hit(LivingIncomingDamageEvent incoming, float healthBefore, int swing) {
            this.incoming = incoming; this.healthBefore = healthBefore; this.swing = swing;
        }
        boolean accepted() { return !incoming.isCanceled() && applied != null && applied > 0 && healthAfter < healthBefore; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Entity> owned = new ArrayList<>();
        private final List<ClackersEntity> shots = new ArrayList<>();
        private final List<ItemEntity> replacementItems = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private final List<Death> deaths = new ArrayList<>();
        private final List<LivingDropsEvent> dropEvents = new ArrayList<>();
        private final List<LivingExperienceDropEvent> xpEvents = new ArrayList<>();
        private final List<Step> deadSteps = new ArrayList<>();
        private Player user, attacker;
        private PlayerPower power, attackerPower;
        private HamonData hamon;
        private ClackersEntity shot;
        private AABB room;
        private Vec3 userFeet, attackerFeet;
        private Frame pending, landing, leave;
        private Step terminal;
        private DamageSource lethalSource;
        private Throwable observerFailure;
        private long lastShotTime = -1;
        private int userPosts, attackerPosts, useStartPosts, usePres, impacts, pairs, liveGroundedPairs, swings;
        private boolean using, releasing, released, swingOpen, deathComplete, closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }
        private void premise(boolean value, String text) { helper.assertTrue(value, "CLACKERS-DEAD-PREMISE " + text); }
        private void oracle(boolean value, String text) { helper.assertTrue(value, "CLACKERS-DEAD-ORACLE " + text); }
        private void log(String text) { System.out.println("[CLACKERS-DEAD] " + text); }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO); ChunkPos chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), y = origin.getY() + 24;
            room = new AABB(x + 3, y - 1, z + 1, x + 13, y + 5, z + 13);
            userFeet = new Vec3(x + 8.5D, y, z + 3.5D); attackerFeet = userFeet.add(0.8D, 0, 0);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY <= level.getMaxBuildHeight(), "build bounds");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(Math.nextDown(room.maxX), Math.nextDown(room.maxY), Math.nextDown(room.maxZ)))) {
                premise(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null
                        && level.isPositionEntityTicking(pos) && new ChunkPos(pos).equals(chunk), "room not empty/ticking: " + pos);
            }
            premise(level.getEntities((Entity) null, room).isEmpty(), "foreign room entity");
            for (int bx = x + 3; bx < x + 13; bx++) for (int bz = z + 1; bz < z + 13; bz++) {
                put(new BlockPos(bx, y - 1, bz)); put(new BlockPos(bx, y + 4, bz));
            }
            for (int bx = x + 6; bx <= x + 10; bx++) for (int by = y; by < y + 4; by++) {
                BlockPos pos = new BlockPos(bx, by, z + 9); wall.add(pos); put(pos);
            }
            // This declared solid backstop receives ordinary knockback; neither actor is repositioned after setup.
            for (int bz = z + 2; bz <= z + 5; bz++) for (int by = y; by < y + 4; by++) put(new BlockPos(x + 7, by, bz));
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(user);
            attacker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL); owned.add(attacker);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities()); GameType.SURVIVAL.updatePlayerAbilities(attacker.getAbilities());
            user.moveTo(userFeet.x, userFeet.y + 0.2D, userFeet.z, 0, 0); user.setYHeadRot(0); user.yBodyRot = 0;
            attacker.moveTo(attackerFeet.x, attackerFeet.y + 0.2D, attackerFeet.z, 90, 0); attacker.setYHeadRot(90); attacker.yBodyRot = 90;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
            premise(user.getHealth() == 20F && attacker.getHealth() == 20F && level.addFreshEntity(user)
                    && level.addFreshEntity(attacker), "fresh ordinary20HP actors");
            power = PowerClass.PLAYER_POWER.attachGet(user); attackerPower = PowerClass.PLAYER_POWER.attachGet(attacker);
            premise(!power.hasPower() && !attackerPower.hasPower() && !PowerClass.STAND.attachGet(user).hasPower()
                    && !PowerClass.STAND.attachGet(attacker).hasPower(), "fresh power state");
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability()); hamon.setEnergy(hamon.getMaxEnergy());
            premise(!hamon.isSkillLearned(ModHamonSkills.CRIMSON_BUBBLE.get()) && !hamon.isSkillLearned(ModHamonSkills.DEEP_PASS.get()),
                    "death-perk skill outside ordinary throw scene");
            observers();
            log("SETUP user=" + user.getUUID() + " attacker=" + attacker.getUUID() + " room=" + room + " cells=" + blocks.size()
                    + " feet=" + userFeet + "/" + attackerFeet + " naturalGravity=true maxAxeSwings=3");
        }
        private void put(BlockPos pos) {
            blocks.put(pos.immutable(), level.getBlockState(pos));
            premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone placement");
        }

        private void observers() {
            add((EntityJoinLevelEvent event) -> {
                if (closed || event.getLevel() != level) return;
                if (event.getEntity() instanceof ClackersEntity created && created.getOwner() == user) {
                    owned.add(created); shots.add(created);
                    observe(() -> {
                        premise(releasing && shot == null && !event.isCanceled(), "unexpected owned emission"); shot = created;
                        CompoundTag tag = shot.saveWithoutId(new CompoundTag());
                        ItemStack pickup = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("PickupItem"));
                        log("JOIN uuid=" + shot.getUUID() + " frame=" + frame() + " pickup=" + pickup + " spent=" + tag.getFloat("HamonSpent"));
                        premise(shot.getType() == ModEntityTypes.CLACKERS.get() && shot.tickCount == 0 && usePres == 20
                                && tag.contains("CreativeOnlyPickup", 1) && !tag.getBoolean("CreativeOnlyPickup")
                                && pickup.is(ModItems.CLACKERS.get()) && pickup.getCount() == 1 && tag.getFloat("HamonSpent") == 100F,
                                "registered recoverable twenty-tick throw");
                    });
                }
                else if (event.getEntity() instanceof ItemEntity item && pending != null && shot != null && item.getOwner() == user
                        && item.getItem().is(ModItems.CLACKERS.get()) && item.position().distanceToSqr(shot.position()) <= 1D
                        && room.contains(item.position())) {
                    owned.add(item); replacementItems.add(item); log("OWNED_REPLACEMENT uuid=" + item.getUUID() + " pre=" + pending);
                }
            }, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Pre event) -> observe(() -> {
                if (event.getEntity() == user || event.getEntity() == attacker) premise(!event.isCanceled(), "owned actor tick canceled");
                if (event.getEntity() == user && using && !released) {
                    premise(user.isUsingItem() && user.getUseItem().is(ModItems.CLACKERS.get()), "natural use tick"); usePres++;
                }
                if (event.getEntity() == shot && terminal == null) {
                    premise(!event.isCanceled() && pending == null, "projectile Pre canceled/unpaired");
                    pending = frame(); actors(); shotState();
                    premise(pending.age == pairs + 1 && pending.savedAge == pending.age && !pending.removed
                            && (lastShotTime < 0 || pending.time == lastShotTime + 1), "natural age/tick sequence");
                    if (landing != null) grounded(pending);
                    if (deathComplete) qualifyDeath();
                }
            }), EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add((EntityTickEvent.Post event) -> observe(() -> {
                if (event.getEntity() == user) userPosts++;
                if (event.getEntity() == attacker) attackerPosts++;
                if (event.getEntity() == shot && terminal == null) {
                    Frame after = frame();
                    premise(pending != null && pending.age == after.age && pending.time == after.time, "projectile Post lacks Pre");
                    Step step = new Step(pending, after); pairs++; lastShotTime = after.time;
                    if (landing == null && after.grounded && impacts == 1) { landing = after; log("LANDING " + after); }
                    if (landing != null) grounded(after);
                    if (pending.grounded && after.grounded && pending.ownerAlive && after.ownerAlive) liveGroundedPairs++;
                    if (deathComplete && !pending.ownerAlive) { deadSteps.add(step); log("DEAD_STEP " + step); }
                    if (after.removed || deadSteps.size() == 2) terminal = step;
                    pending = null;
                }
            }), EntityTickEvent.Post.class, EventPriority.LOWEST, false);
            add((ProjectileImpactEvent event) -> observe(() -> {
                if (event.getProjectile() != shot) return;
                impacts++; log("IMPACT age=" + shot.tickCount + " hit=" + event.getRayTraceResult() + " canceled=" + event.isCanceled());
                premise(pending != null && impacts == 1 && landing == null && !event.isCanceled()
                        && event.getRayTraceResult() instanceof BlockHitResult hit && wall.contains(hit.getBlockPos())
                        && hit.getDirection() == Direction.NORTH, "not the sole owned-wall landing");
            }), ProjectileImpactEvent.class, EventPriority.LOWEST, true);
            add((EntityLeaveLevelEvent event) -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() == shot) { leave = frame(); log("LEAVE " + leave + " pending=" + pending); }
            }), EntityLeaveLevelEvent.class, EventPriority.LOWEST, false);
            add((LivingIncomingDamageEvent event) -> {
                if (closed) return;
                if (event.getEntity() == user) {
                    Hit hit = new Hit(event, user.getHealth(), swings); hits.add(hit);
                    observe(() -> premise(swingOpen && !deathComplete && isAxeSource(event.getSource())
                            && event.getOriginalAmount() > 0, "incoming not from the owned ordinary axe swing"));
                }
                else if (event.getEntity() == attacker) observe(() -> premise(false, "unexpected attacker damage"));
            }, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
            add((LivingDamageEvent.Post event) -> observe(() -> {
                if (event.getEntity() != user) return;
                Hit hit = hits.stream().filter(value -> value.incoming.getSource() == event.getSource() && value.applied == null)
                        .findFirst().orElseThrow();
                premise(swingOpen && hit.swing == swings && isAxeSource(event.getSource()), "unmatched actual applied damage");
                hit.applied = event.getNewDamage(); hit.healthAfter = user.getHealth();
                log("DAMAGE swing=" + swings + " input=" + hit.incoming.getOriginalAmount() + " applied=" + hit.applied
                        + " HP=" + hit.healthBefore + "->" + hit.healthAfter + " direct=" + event.getSource().getDirectEntity().getUUID());
            }), LivingDamageEvent.Post.class, EventPriority.LOWEST, false);
            add((LivingDeathEvent event) -> {
                if (closed || event.getEntity() != user) return;
                deaths.add(new Death(event, level.getGameTime(), user.tickCount, swings));
                observe(() -> {
                    premise(swingOpen && isAxeSource(event.getSource()) && user.getHealth() == 0F
                            && (lethalSource == null || lethalSource == event.getSource()), "unowned/ambiguous real death");
                    lethalSource = event.getSource(); log("DEATH callbacks=" + deaths.size() + " canceled=" + event.isCanceled()
                            + " time=" + level.getGameTime() + " actorAge=" + user.tickCount + " swing=" + swings);
                });
            }, LivingDeathEvent.class, EventPriority.LOWEST, true);
            add((LivingDropsEvent event) -> {
                if (closed || event.getEntity() != user && event.getEntity() != attacker) return;
                dropEvents.add(event); owned.addAll(event.getDrops());
                observe(() -> premise(event.getEntity() == user && swingOpen && lethalSource == event.getSource()
                        && !deaths.isEmpty(), "owned death drops lack the actual lethal attack"));
            }, LivingDropsEvent.class, EventPriority.LOWEST, true);
            add((LivingExperienceDropEvent event) -> {
                if (closed || event.getEntity() != user && event.getEntity() != attacker) return;
                xpEvents.add(event);
                observe(() -> premise(event.getEntity() == user && swingOpen && event.getOriginalExperience() == 0
                        && event.getDroppedExperience() == 0, "nonzero/unowned XP production is outside this zero-XP scene"));
            }, LivingExperienceDropEvent.class, EventPriority.LOWEST, true);
        }

        private boolean isAxeSource(DamageSource source) {
            return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker && source.getEntity() == attacker;
        }
        private void retainDeathDrops() {
            for (LivingDropsEvent event : dropEvents) for (ItemEntity item : event.getDrops()) if (!owned.contains(item)) owned.add(item);
        }
        private Frame frame() {
            CompoundTag tag = shot.saveWithoutId(new CompoundTag()); Entity owner = shot.getOwner();
            return new Frame(level.getGameTime(), shot.tickCount, shot.position(), shot.getDeltaMovement(), shot.isInGround(),
                    shot.isRemoved(), shot.getRemovalReason(), owner == user, owner == null ? null : owner.getUUID(), user.isAlive(),
                    user.getHealth(), user.isRemoved(), user.tickCount, user.deathTime, user.position(), user.getDeltaMovement(),
                    level.isPositionEntityTicking(shot.blockPosition()), level.isPositionEntityTicking(user.blockPosition()),
                    tag.getBoolean("CreativeOnlyPickup"), tag.getInt("Age"));
        }
        private int clackersInInventory() { return user.getInventory().countItem(ModItems.CLACKERS.get()); }
        private void ready(Entity actor) {
            AABB box = actor.getBoundingBox();
            premise(box.minX >= room.minX && box.maxX < room.maxX && box.minY >= room.minY && box.maxY < room.maxY
                    && box.minZ >= room.minZ && box.maxZ < room.maxZ && level.isPositionEntityTicking(actor.blockPosition()), "actor left ready room");
        }
        private void actors() {
            ready(user); ready(attacker);
            premise(level.getEntity(user.getUUID()) == user && !user.isRemoved() && level.getEntity(attacker.getUUID()) == attacker
                    && attacker.isAlive() && !attacker.isRemoved() && (deathComplete || user.isAlive()), "original actor/body identity");
            for (Player actor : new Player[] { user, attacker }) premise(!actor.isCreative() && !actor.isSpectator()
                    && !actor.getAbilities().instabuild && !actor.getAbilities().invulnerable && !actor.isInvulnerable()
                    && !actor.isNoGravity() && !actor.noPhysics && !actor.isOnFire() && !actor.isPassenger()
                    && !actor.isVehicle() && !PowerClass.STAND.attachGet(actor).hasPower(), "ordinary actor flags/power");
            premise(user.experienceLevel == 0 && user.totalExperience == 0 && user.experienceProgress == 0F
                    && user.getArmorValue() == 0 && user.getOffhandItem().isEmpty() && !attackerPower.hasPower(), "zero-XP unarmored victim/plain attacker");
            premise(blocks.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)
                    && level.getBlockEntity(pos) == null), "owned support/backstop changed");
            premise(level.getEntities((Entity) null, room).stream().allMatch(owned::contains), "foreign/unattributed room entity");
        }
        private void shotState() {
            AABB query = shot.getBoundingBox().expandTowards(shot.getDeltaMovement()).inflate(1D);
            premise(query.minX >= room.minX && query.maxX < room.maxX && query.minY >= room.minY && query.maxY < room.maxY
                    && query.minZ >= room.minZ && query.maxZ < room.maxZ, "projectile query left owned ticking chunk");
            premise(shot.getOwner() == user && !shot.isNoGravity() && !shot.noPhysics && !shot.isInWaterOrBubble()
                    && level.isPositionEntityTicking(shot.blockPosition()), "shot owner/ordinary physics/ticking");
            if (landing == null) premise(attacker.getBoundingBox().inflate(0.3D)
                    .clip(shot.position(), shot.position().add(shot.getDeltaMovement())).isEmpty(), "attacker intersects actual first-flight ray");
            else for (Player actor : new Player[] { user, attacker }) premise(!shot.getBoundingBox().inflate(1D, 0.5D, 1D)
                    .intersects(actor.getBoundingBox()), "actor entered pickup-touch volume");
        }
        private void grounded(Frame frame) {
            premise(frame.grounded && frame.position.distanceToSqr(landing.position) < EPS * EPS
                    && frame.velocity.lengthSqr() < EPS * EPS && frame.sameOwner && user.getUUID().equals(frame.ownerId)
                    && !frame.creativeOnly && frame.ticking && frame.ownerTicking && frame.age < 1200, "grounded recoverable/pre-expiry premise changed: " + frame);
        }
        private void qualifyDeath() {
            premise(deathComplete && lethalSource != null && isAxeSource(lethalSource) && !user.isAlive()
                    && user.getHealth() == 0F && !user.isRemoved() && user.deathTime < 20 && !deaths.isEmpty(), "original genuine dead body unavailable");
            Death first = deaths.getFirst();
            premise(deaths.stream().allMatch(value -> !value.event.isCanceled() && value.event.getSource() == lethalSource
                    && value.time == first.time && value.actorAge == first.actorAge && value.swing == first.swing), "death callbacks canceled or different lethal attempts");
            premise(hits.size() == swings && hits.stream().allMatch(Hit::accepted)
                    && hits.getLast().incoming.getSource() == lethalSource && hits.getLast().healthAfter == 0F,
                    "death lacks positive actual applied weapon damage");
            premise(xpEvents.stream().allMatch(value -> value.getOriginalExperience() == 0 && value.getDroppedExperience() == 0), "unexpected final XP event state");
        }

        private void swing() {
            actors(); shotState();
            premise(swings < 3 && user.onGround() && attacker.onGround() && user.invulnerableTime == 0
                    && !attacker.isSprinting() && attacker.getMainHandItem().is(Items.NETHERITE_AXE)
                    && attacker.getAttackStrengthScale(0.5F) >= 1F && Math.abs(attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) - 10D) < EPS
                    && attacker.distanceToSqr(user) < 4D && attacker.hasLineOfSight(user)
                    && user.getBoundingBox().clip(attacker.getEyePosition(), attacker.getEyePosition().add(attacker.getLookAngle().scale(3D))).isPresent(),
                    "ordinary recharged melee/weapon/range/LOS premise");
            int beforeHits = hits.size(); float health = user.getHealth(); swings++; swingOpen = true;
            try { attacker.attack(user); } finally { swingOpen = false; }
            retainDeathDrops();
            if (observerFailure instanceof RuntimeException error) throw error;
            if (observerFailure instanceof Error error) throw error;
            premise(hits.size() == beforeHits + 1 && hits.getLast().swing == swings && hits.getLast().accepted()
                    && user.getHealth() < health, "ordinary axe call did not apply one real hit");
            log("SWING ordinal=" + swings + " HP=" + health + "->" + user.getHealth() + " attackerPosts=" + attackerPosts
                    + " rechargeAfter=" + attacker.getAttackStrengthScale(0.5F) + " userPos=" + user.position() + " delta=" + user.getDeltaMovement());
            if (!user.isAlive()) { deathComplete = true; qualifyDeath(); log("DEATH_COMPLETE shot=" + frame() + " callbacks=" + deaths.size()); }
        }
        private void validate() {
            actors(); qualifyDeath();
            premise(released && usePres == 20 && shots.size() == 1 && impacts == 1 && landing != null && liveGroundedPairs >= 2
                    && !deadSteps.isEmpty() && terminal != null && clackersInInventory() == 0, "missing admitted throw/live-control/dead-update chain");
            Step first = deadSteps.getFirst();
            premise(!first.before.removed && first.before.sameOwner && !first.before.ownerAlive && first.before.ownerHealth == 0F
                    && !first.before.ownerRemoved && first.before.age < 1200, "first update was not eligible for the dead-owner branch");
            for (Step step : deadSteps) premise(!step.before.ownerAlive && !step.after.ownerAlive
                    && step.before.ownerHealth == 0F && step.after.ownerHealth == 0F && !step.before.ownerRemoved
                    && !step.after.ownerRemoved && step.before.sameOwner && step.after.sameOwner, "dead body changed during the sampled updates");
            grounded(first.before); grounded(first.after);
            log("BOUNDARY liveGroundedPairs=" + liveGroundedPairs + " swings=" + swings + " firstDead=" + first
                    + " terminal=" + terminal + " leave=" + leave + " ownedReplacementItems=" + replacementItems.size());
            oracle(!first.after.removed && first.after.reason == null, "recoverable projectile was discarded on its dead owner's first natural update");
            oracle(deadSteps.size() == 2 && !terminal.after.removed && terminal.after.sameOwner && !terminal.after.ownerAlive
                    && shot.isAlive() && level.getEntity(shot.getUUID()) == shot && replacementItems.isEmpty(),
                    "same recoverable projectile was not retained through two actual dead-owner updates");
            log("RESULT sameProjectile=true realDeadOwner=true deadPairs=2 bodyStillPresent=true native=false");
        }
        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                if (terminal != null) { validate(); close(); helper.succeed(); return; }
                premise(helper.getTick() < 140, "finite watchdog pairs=" + pairs + " swings=" + swings + " deadPairs=" + deadSteps.size());
                actors();
                if (!using) {
                    premise(helper.getTick() < 40, "ordinary setup readiness deadline");
                    if (userPosts >= 3 && attackerPosts >= 3 && user.onGround() && attacker.onGround()) {
                        premise(user.position().distanceToSqr(userFeet) < EPS * EPS && attacker.position().distanceToSqr(attackerFeet) < EPS * EPS
                                && user.getHealth() == 20F && hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200
                                && clackersInInventory() == 1, "settled ordinary throw setup");
                        premise(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                && user.isUsingItem(), "registered item use failed");
                        using = true; useStartPosts = userPosts; log("USE userPosts=" + userPosts);
                    }
                }
                else if (!released) {
                    premise(user.isUsingItem(), "use ended before real release");
                    if (user.getTicksUsingItem() >= 20) {
                        premise(user.getTicksUsingItem() == 20 && usePres == 20 && userPosts - useStartPosts == 20
                                && user.onGround() && user.getDeltaMovement().horizontalDistanceSqr() < EPS * EPS, "twenty natural use ticks");
                        releasing = true; try { user.releaseUsingItem(); } finally { releasing = false; }
                        released = true;
                        premise(shots.size() == 1 && shot != null && shot.isAlive() && level.getEntity(shot.getUUID()) == shot
                                && !user.isUsingItem() && clackersInInventory() == 0, "real throw/consumption");
                        log("RELEASE actualUsePres=" + usePres + " UUID=" + shot.getUUID());
                    }
                }
                else {
                    premise(!shot.isRemoved(), "unpaired/out-of-window projectile disappearance: " + leave);
                    premise(landing != null || shot.tickCount <= 8, "natural owned-wall landing absent by age8");
                    if (!deathComplete && liveGroundedPairs >= 2 && attacker.getAttackStrengthScale(0.5F) >= 1F
                            && user.invulnerableTime == 0 && user.onGround() && attacker.onGround()) swing();
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private <T extends Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable action) {
            if (closed || observerFailure != null) return;
            try { action.run(); } catch (RuntimeException | Error error) { observerFailure = error; log("OBSERVER_FAILURE " + error + " pre=" + pending); }
        }
        private void cleanup(Runnable action, List<Throwable> failures) {
            try { action.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true; List<Throwable> failures = new ArrayList<>();
            cleanup(this::retainDeathDrops, failures);
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures); listeners.clear();
            for (Player actor : new Player[] { user, attacker }) if (actor != null) {
                cleanup(actor::stopUsingItem, failures); cleanup(() -> actor.getInventory().clearContent(), failures);
            }
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (attackerPower != null) attackerPower.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : blocks.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            cleanup(() -> {
                boolean restored = blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                boolean gone = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                boolean inputOff = (user == null || !user.isUsingItem()) && (attacker == null || !attacker.isUsingItem());
                log("CLEANUP cells=" + blocks.size() + " exactRestore=" + restored + " ownedGone=" + gone + " inputOff=" + inputOff
                        + " shots=" + shots.size() + " retainedEntities=" + owned.size() + " listeners=0");
                if (!restored || !gone || !inputOff) throw new IllegalStateException("Clackers dead-owner cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Clackers dead-owner cleanup failed");
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
