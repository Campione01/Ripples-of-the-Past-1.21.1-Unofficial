package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.network.s2c.TrPillarmanParticlesPacket;
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
import rotp.core.util.functions.MathUtil;

/**
 * Real Light-mode blade hits through the registered abilities. The spark payload is read from the connection of a
 * player that tracks the hit entity, which is where PacketDistributor delivers it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanBladeHitGameTests {
    private PillarmanBladeHitGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_slash", timeoutTicks = 140)
    public static void bladeSlashHitSendsNineSparksForItsTargetOnly(GameTestHelper helper) {
        start(helper, Case.SLASH);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_slash_refused", timeoutTicks = 140)
    public static void refusedBladeSlashSendsNoSparks(GameTestHelper helper) {
        start(helper, Case.SLASH_REFUSED);
    }

    // 1.16 PillarmanUtil.sparkEffect: sendToClientsTrackingAndSelf, so a hit player sees the sparks on himself
    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_slash_player", timeoutTicks = 140)
    public static void bladeSlashHitSendsItsSparksToTheHitPlayerToo(GameTestHelper helper) {
        start(helper, Case.SLASH_PLAYER);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_barrage", timeoutTicks = 140)
    public static void bladeBarrageSendsTwelveSparksPerLandedHit(GameTestHelper helper) {
        start(helper, Case.BARRAGE);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_dash", timeoutTicks = 140)
    public static void bladeDashSendsSixtySparksOncePerTarget(GameTestHelper helper) {
        start(helper, Case.DASH);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_dash_late_look", timeoutTicks = 140)
    public static void bladeDashSideKnockbackFollowsBodyYawAfterLateLook(GameTestHelper helper) {
        start(helper, Case.DASH_LATE_LOOK);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hit_deflect", timeoutTicks = 140)
    public static void heldBladesDeflectAirborneArrowWithTwelveSparks(GameTestHelper helper) {
        start(helper, Case.DEFLECT);
    }

    private enum Case {
        SLASH("pillarman_blade_slash", InputMethod.CLICK),
        SLASH_REFUSED("pillarman_blade_slash", InputMethod.CLICK),
        SLASH_PLAYER("pillarman_blade_slash", InputMethod.CLICK),
        BARRAGE("pillarman_blade_barrage", InputMethod.HOLD),
        DASH("pillarman_blade_dash_attack", InputMethod.HOLD),
        DASH_LATE_LOOK("pillarman_blade_dash_attack", InputMethod.HOLD),
        DEFLECT("pillarman_blade_barrage", InputMethod.HOLD);

        final String ability;
        final InputMethod input;
        Case(String ability, InputMethod input) { this.ability = ability; this.input = input; }
        boolean dash() { return this == DASH || this == DASH_LATE_LOOK; }
    }

    private record SideKnockback(float bodyYawBeforeActionTick, float lookYaw, Vec3 toTarget, double ratioX, double ratioZ) {
        float bearing() { return MathUtil.yRotDegFromVec(toTarget); }
        boolean leftOfBody() { return Mth.wrapDegrees(bodyYawBeforeActionTick - bearing()) < 0.0F; }
        boolean leftOfLook() { return Mth.wrapDegrees(lookYaw - bearing()) < 0.0F; }
        /** The angle the ability added to the user-to-target direction: +60..90 for "left", -60..-90 otherwise. */
        float sideAngle() {
            float knockbackYaw = (float) Mth.atan2(ratioX, -ratioZ) * MathUtil.RAD_TO_DEG;
            return Mth.wrapDegrees(knockbackYaw - (float) -Mth.atan2(toTarget.x, toTarget.z) * MathUtil.RAD_TO_DEG);
        }
    }

    private static void start(GameTestHelper helper, Case testCase) {
        Fixture fixture = new Fixture(helper, testCase);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 41;
        private static final float LATE_LOOK_YAW = 45.0F;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Case testCase;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final Map<ChunkMap.TrackedEntity, Watcher> watched = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private LivingEntity target;
        private Mob bystander;
        private HitPlayer hitPlayer;
        private Watcher targetWatcher, bystanderWatcher;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action;
        private SideKnockback sideKnockback;
        private long generation;
        private float energyAtPress, bodyYawBeforeActionTick;
        private int userPosts, grantPost, heldPosts, chargePosts, performPosts, settlePosts, hits;
        private boolean granted, pressed, released, turned, probed, closed;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, Case testCase) {
            this.helper = helper; this.level = helper.getLevel(); this.testCase = testCase;
        }
        private void premise(boolean ok, String message) { helper.assertTrue(ok, "BLADE-HIT-PREMISE " + testCase + " " + message); }
        private void oracle(boolean ok, String message) { helper.assertTrue(ok, message); }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ(), y = template.getY() + 32;
            premise(y - 1 >= level.getMinBuildHeight() && y + 4 < level.getMaxBuildHeight(), "room build bounds");
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(minX + 5, y - 1, minZ + 1), new BlockPos(minX + 11, y + 4, minZ + 12))) {
                premise(level.isEmptyBlock(pos), "room is not air: " + pos);
                boolean barrier = testCase.dash() && pos.getY() == y + 1 && pos.getZ() == minZ + 5
                        && pos.getX() >= minX + 7 && pos.getX() <= minX + 9;
                if (pos.getY() == y - 1 || pos.getY() == y + 4 || barrier) {
                    original.put(pos.immutable(), level.getBlockState(pos));
                    premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "room block placement failed");
                }
            }
            double x = minX + 8.5D, userZ = minZ + 4.7D;
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            owned.add(user);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.moveTo(x, y, userZ, 0, 0);
            user.setYHeadRot(0); user.yBodyRot = 0;
            if (testCase.dash()) {
                // The head-level barrier keeps the leaping user in place; the pig stands under it, 22 degrees off the user's facing.
                target = mob(EntityType.PIG, new Vec3(x - 0.45D, y, userZ + 1.11D));
                bystander = mob(EntityType.PIG, new Vec3(x, y, userZ - 2.2D));
            }
            else if (testCase == Case.SLASH_PLAYER) {
                hitPlayer = new HitPlayer(level, "blade-hit-player");
                owned.add(hitPlayer);
                hitPlayer.moveTo(x, y, minZ + 7.2D, 0, 0);
                target = hitPlayer;
                bystander = mob(EntityType.IRON_GOLEM, new Vec3(x, y, minZ + 10.2D));
            }
            else {
                target = mob(EntityType.IRON_GOLEM, new Vec3(x, y, minZ + 7.2D));
                bystander = mob(EntityType.IRON_GOLEM, new Vec3(x, y, minZ + 10.2D));
            }
            if (testCase == Case.SLASH_REFUSED) target.setInvulnerable(true);
            premise(level.addFreshEntity(user) && level.addFreshEntity(target) && level.addFreshEntity(bystander), "actors failed to join");
            targetWatcher = watch(target, "blade-target-watcher");
            bystanderWatcher = watch(bystander, "blade-bystander-watcher");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            premise(!power.hasPower(), "fresh user already has a power");
            observeEvents();
        }

        private <T extends Mob> T mob(EntityType<T> type, Vec3 pos) {
            T mob = type.create(level);
            premise(mob != null, "mob factory failed: " + type);
            owned.add(mob);
            mob.setNoAi(true);
            mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
            return mob;
        }

        private Watcher watch(Entity entity, String name) {
            ChunkMap.TrackedEntity tracked = level.getChunkSource().chunkMap.entityMap.get(entity.getId());
            premise(tracked != null, "entity is not tracked: " + entity);
            Watcher watcher = new Watcher(level, name);
            tracked.seenBy.add(watcher.connection);
            watched.put(tracked, watcher);
            return watcher;
        }

        private void observeEvents() {
            Consumer<EntityTickEvent.Post> beforeActionTick = event -> observe(() -> {
                if (event.getEntity() == user) bodyYawBeforeActionTick = user.yBodyRot;
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() != user) return;
                userPosts++;
                if (!pressed || action == null) return;
                if (action.isOver()) { settlePosts++; return; }
                if (LivingComponentAction.getCurEntityAction(user) != action) return;
                if (action.getPhase() == ActionPhase.BUTTON_CHARGE) chargePosts++;
                if (action.getPhase() == ActionPhase.PERFORM) {
                    performPosts++;
                    if (AbilityInput.isHeldByKey(user, action)) heldPosts++;
                }
            });
            Consumer<LivingDamageEvent.Post> damage = event -> observe(() -> {
                if (event.getEntity() == user || event.getEntity() == bystander) {
                    throw new IllegalStateException("BLADE-HIT-PREMISE unexpected damage to " + event.getEntity());
                }
                if (event.getEntity() == target && event.getSource().getEntity() == user && event.getNewDamage() > 0) hits++;
            });
            Consumer<LivingKnockBackEvent> knockback = event -> observe(() -> {
                if (event.getEntity() != target || !testCase.dash() || event.getOriginalStrength() != 0.75F) return;
                if (sideKnockback != null) throw new IllegalStateException("BLADE-HIT-PREMISE second side knockback");
                sideKnockback = new SideKnockback(bodyYawBeforeActionTick, user.getYRot(),
                        target.position().subtract(user.position()), event.getOriginalRatioX(), event.getOriginalRatioZ());
            });
            // The mod ticks the action in its own default-priority Post listener.
            listeners.add(beforeActionTick); NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, beforeActionTick);
            listeners.add(post); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            listeners.add(damage); NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damage);
            listeners.add(knockback); NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingKnockBackEvent.class, knockback);
        }

        private void grant() {
            premise(power.trySetPowerType(ModPlayerPowers.PILLAR_MAN.get()), "Pillar Man grant failed");
            data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, user); data.setMode(PillarmanMode.LIGHT, user); data.setEnergy(user, 300F);
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility(testCase.ability);
            premise(ability != null, "registered ability absent: " + testCase.ability);
            aim();
            grantPost = userPosts; granted = true;
        }

        private void aim() {
            boolean aimed = testCase == Case.SLASH || testCase == Case.SLASH_REFUSED || testCase == Case.SLASH_PLAYER
                    || testCase == Case.BARRAGE;
            LivingComponentAction.getComponent(user).entityAim.setTarget(aimed ? new ActionTarget(target) : ActionTarget.EMPTY);
        }

        private void press() {
            premise(user.isAlive() && target.isAlive() && bystander.isAlive() && user.onGround() && !user.isOnFire()
                    && !level.canSeeSky(BlockPos.containing(user.getEyePosition())) && !data.isStoneFormEnabled()
                    && user.getMainHandItem().isEmpty() && user.getYRot() == 0 && user.yBodyRot == 0, "actors are not ready");
            aim();
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, testCase.input),
                    "registered input admission failed");
            energyAtPress = data.getEnergy();
            pressed = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, testCase.input, 0, BufferingState.clickOnly(), ability.getAbilityId());
            premise(input != null && input.action instanceof EntityActionInstance, "input did not install an action");
            action = (EntityActionInstance) input.action; generation = input.generation;
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.ability == ability && generation > 0,
                    "installed action differs");
        }

        private void release() {
            long actual = AbilityInput.keyReleaseAndGetGeneration(KEY, user);
            premise(actual == generation, "key release generation differs");
            released = true;
        }

        /** @return whether the case is complete */
        private boolean step() {
            aim();
            switch (testCase) {
                case SLASH, SLASH_REFUSED -> {
                    if (settlePosts < 1) return false;
                    if (testCase == Case.SLASH) {
                        premise(hits == 1, "the slash landed once, hits=" + hits);
                        oracle(targetWatcher.sparks.equals(List.of(new TrPillarmanParticlesPacket(target.getId(), 9))),
                                "A landed Blade Slash sends one 9-spark packet for its target, but the target's tracker got " + targetWatcher.sparks);
                    }
                    else {
                        premise(hits == 0 && data.getEnergy() < energyAtPress, "the refused slash ran and paid its cost");
                        oracle(targetWatcher.sparks.isEmpty(),
                                "A Blade Slash refused by its target sends no sparks, but the target's tracker got " + targetWatcher.sparks);
                    }
                }
                case SLASH_PLAYER -> {
                    if (settlePosts < 1) return false;
                    premise(hits == 1, "the slash landed once on the player, hits=" + hits);
                    TrPillarmanParticlesPacket sparks = new TrPillarmanParticlesPacket(target.getId(), 9);
                    premise(targetWatcher.sparks.equals(List.of(sparks)), "the hit player's tracker got " + targetWatcher.sparks);
                    oracle(hitPlayer.sparks.equals(List.of(sparks)),
                            "A player hit by a Blade Slash gets the 9-spark packet for himself on his own connection (1.16.5"
                                    + " sendToClientsTrackingAndSelf), but he got " + hitPlayer.sparks);
                }
                case BARRAGE -> {
                    if (!released) { if (heldPosts >= 6) release(); return false; }
                    if (settlePosts < 1) return false;
                    premise(hits >= 2, "the held barrage landed repeatedly, hits=" + hits);
                    oracle(targetWatcher.sparks.size() == hits && targetWatcher.sparks.stream()
                            .allMatch(new TrPillarmanParticlesPacket(target.getId(), 12)::equals),
                            "Every landed Blade Barrage hit sends one 12-spark packet for its target: hits=" + hits
                                    + ", the target's tracker got " + targetWatcher.sparks);
                }
                case DASH, DASH_LATE_LOOK -> {
                    if (!released) {
                        if (chargePosts >= 12) {
                            release();
                            premise(!action.isOver() && action.getPhase() == ActionPhase.PERFORM, "charged release did not start the dash");
                        }
                        return false;
                    }
                    if (testCase == Case.DASH_LATE_LOOK && !turned && performPosts >= 1) {
                        premise(performPosts == 1 && sideKnockback == null, "the look turn comes after the leap and before the hit");
                        user.setYRot(LATE_LOOK_YAW); user.setYHeadRot(LATE_LOOK_YAW);
                        turned = true;
                    }
                    if (settlePosts < 1) return false;
                    premise(hits == 1 && sideKnockback != null, "the dash landed once with a side knockback, hits=" + hits);
                    oracle(targetWatcher.sparks.equals(List.of(new TrPillarmanParticlesPacket(target.getId(), 60))),
                            "A landed Blade Dash sends one 60-spark packet per target, but the target's tracker got " + targetWatcher.sparks);
                    float side = sideKnockback.sideAngle();
                    premise(Math.abs(side) >= 59.9F && Math.abs(side) <= 90.1F, "side knockback angle is 60..90 degrees: " + side);
                    if (testCase == Case.DASH_LATE_LOOK) {
                        premise(turned && sideKnockback.lookYaw() == LATE_LOOK_YAW
                                && sideKnockback.leftOfBody() != sideKnockback.leftOfLook(),
                                "the target stands between the body yaw and the look yaw: " + sideKnockback);
                    }
                    else {
                        premise(sideKnockback.leftOfBody() == sideKnockback.leftOfLook(), "aligned control: " + sideKnockback);
                    }
                    oracle((side > 0) == sideKnockback.leftOfBody(),
                            "Blade Dash knocks its target to the side it is on relative to the user's body yaw (1.16.5), but the side angle was "
                                    + side + " for " + sideKnockback);
                }
                case DEFLECT -> {
                    if (!probed) {
                        if (heldPosts < 2) return false;
                        probed = true;
                        arrowProbe(false);
                        arrowProbe(true);
                        release();
                        return false;
                    }
                    if (settlePosts < 1) return false;
                    oracle(targetWatcher.sparks.isEmpty(), "Deflecting an arrow sends no sparks for other entities, but got " + targetWatcher.sparks);
                }
            }
            oracle(bystanderWatcher.sparks.isEmpty(),
                    "An entity that was not hit gets no spark packet, but the bystander's tracker got " + bystanderWatcher.sparks);
            return true;
        }

        private void arrowProbe(boolean grounded) {
            Entity created = EntityType.ARROW.create(level);
            premise(created instanceof AbstractArrow, "arrow factory failed");
            AbstractArrow arrow = (AbstractArrow) created;
            owned.add(arrow);
            arrow.setOwner(bystander);
            Vec3 pos = user.getEyePosition().add(0, 0, 2);
            arrow.setPos(pos.x, pos.y, pos.z);
            arrow.setDeltaMovement(0, 0, -1);
            premise(level.addFreshEntity(arrow), "arrow failed to join");
            Watcher watcher = watch(arrow, "blade-arrow-watcher");
            arrow.setOnGround(grounded);
            premise(action == LivingComponentAction.getCurEntityAction(user) && action.getPhase() == ActionPhase.PERFORM
                    && AbilityInput.isHeldByKey(user, action) && user.getYRot() == 0, "blades are not held towards the arrow");
            DamageSource source = level.damageSources().arrow(arrow, bystander);
            float before = user.getHealth();
            boolean accepted = user.hurt(source, 2F);
            premise(!accepted && user.getHealth() == before, "held blades did not deflect the frontal arrow");
            if (grounded) {
                oracle(watcher.sparks.isEmpty(), "Deflecting a grounded arrow sends no sparks, but its tracker got " + watcher.sparks);
            }
            else {
                oracle(watcher.sparks.equals(List.of(new TrPillarmanParticlesPacket(arrow.getId(), 12))),
                        "Deflecting an airborne arrow sends one 12-spark packet for the arrow, but its tracker got " + watcher.sparks);
            }
            unwatch(watcher);
            arrow.discard();
        }

        private void unwatch(Watcher watcher) {
            watched.entrySet().removeIf(entry -> {
                if (entry.getValue() != watcher) return false;
                entry.getKey().seenBy.remove(watcher.connection);
                return true;
            });
        }

        private void poll() {
            if (closed) return;
            try {
                throwObserverFailure();
                premise(helper.getTick() < 120, "watchdog: granted=" + granted + " pressed=" + pressed + " released=" + released
                        + " charge=" + chargePosts + " perform=" + performPosts + " held=" + heldPosts + " hits=" + hits);
                if (!granted) {
                    if (userPosts >= 2 && user.onGround() && !level.canSeeSky(BlockPos.containing(user.getEyePosition()))) grant();
                }
                else if (!pressed) {
                    if (userPosts >= grantPost + 3) press();
                }
                else if (step()) {
                    throwObserverFailure();
                    close(); helper.succeed(); return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private void throwObserverFailure() {
            if (observerFailure instanceof RuntimeException error) throw error;
            if (observerFailure instanceof Error error) throw error;
        }
        private void observe(Runnable operation) {
            if (closed || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) {
            try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { watched.forEach((tracked, watcher) -> tracked.seenBy.remove(watcher.connection)); watched.clear(); }, failures);
            cleanup(() -> { if (pressed && user != null) AbilityInput.keyRelease(KEY, user); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var entry : original.entrySet()) cleanup(() -> level.setBlockAndUpdate(entry.getKey(), entry.getValue()), failures);
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("blade hit cleanup failed");
                failures.forEach(error::addSuppressed); throw error;
            }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }

    private static final class Watcher extends FakePlayer {
        final List<TrPillarmanParticlesPacket> sparks = new ArrayList<>();

        Watcher(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
            this.connection = new RecordingConnection(level, this, sparks);
        }
    }

    /** FakePlayer takes no damage and no player's attack; this one takes the real hit and records its own connection. */
    private static final class HitPlayer extends FakePlayer {
        final List<TrPillarmanParticlesPacket> sparks = new ArrayList<>();

        HitPlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
            this.connection = new RecordingConnection(level, this, sparks);
            try {
                // a real player loses this join protection in its connection tick, which a FakePlayer does not have
                Field spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                spawnProtection.setAccessible(true);
                spawnProtection.setInt(this, 0);
            }
            catch (ReflectiveOperationException error) {
                throw new AssertionError("Could not reach ServerPlayer.spawnInvulnerableTime", error);
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean canHarmPlayer(Player other) {
            return true;
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<TrPillarmanParticlesPacket> sink;

        RecordingConnection(ServerLevel level, ServerPlayer player, List<TrPillarmanParticlesPacket> sink) {
            super(level.getServer(), openConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sink = sink;
        }

        private static Connection openConnection() {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            return connection;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            if (sink == null) return;
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(sub -> send(sub, null));
            }
            else if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof TrPillarmanParticlesPacket sparks) {
                sink.add(sparks);
            }
        }
    }
}
