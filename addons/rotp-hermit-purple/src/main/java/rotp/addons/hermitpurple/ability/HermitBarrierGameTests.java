package rotp.addons.hermitpurple.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitBarrierVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitBarrierGameTests {
    private static final float PRIMING_HIT = 3.0F;
    private static final double TARGET_RISE = 0.2D;
    private static final int TRIP_TICKS = 4;

    private HermitBarrierGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200, batch = "hp_barrier_contact")
    public static void barrierTripsOnATargetInsideAStrongerHitsCooldown(GameTestHelper helper) {
        begin(helper, Case.COOLDOWN_CONTACT);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 200, batch = "hp_barrier_contact")
    public static void barrierHurtsAndTripsOnAFreshTarget(GameTestHelper helper) {
        begin(helper, Case.FRESH_CONTACT);
    }

    private enum Case {
        COOLDOWN_CONTACT("cooldown_contact", 262, true),
        FRESH_CONTACT("fresh_contact", 272, false);

        final String label;
        final int height;
        final boolean primed;

        Case(String label, int height, boolean primed) {
            this.label = label;
            this.height = height;
            this.primed = primed;
        }
    }

    private static void begin(GameTestHelper helper, Case kind) {
        Fixture fixture = new Fixture(helper, kind);
        helper.testInfo.addListener(fixture);
        fixture.guarded(fixture::prepare);
    }

    private record Hit(String type, float amount, long worldTime) {}

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Case kind;
        final List<Hit> plainHits = new ArrayList<>();
        final List<Hit> hamonHits = new ArrayList<>();
        final List<Hit> otherHits = new ArrayList<>();
        final Consumer<LivingDamageEvent.Post> damageListener = this::damaged;
        FakePlayer player;
        StandPower power;
        HermitPurpleStandEntity stand;
        HermitBarrierVineEntity barrier;
        Villager target;
        AABB corridor;
        BlockPos from;
        BlockPos to;
        float healthAtContact;
        long attachedAt;
        boolean watching;
        boolean closed;

        Fixture(GameTestHelper helper, Case kind) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.kind = kind;
        }

        void prepare() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            int chunkX = origin.getX() & ~15;
            int chunkZ = origin.getZ() & ~15;
            int y = origin.getY() + kind.height;
            corridor = new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 8, chunkZ + 15);
            helper.assertTrue(level.getBlockStates(corridor).allMatch(state ->
                            state.isAir() && state.getFluidState().isEmpty()),
                    "Barrier fixture needs an existing empty, dry corridor");
            helper.assertTrue(level.getEntities((Entity) null, corridor,
                            entity -> !entity.isSpectator()).isEmpty(),
                    "Barrier fixture corridor contains another actor");
            from = new BlockPos(chunkX + 4, y, chunkZ + 8);
            to = new BlockPos(chunkX + 10, y, chunkZ + 8);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPBarrier" + kind.ordinal()));
            player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
            player.setNoGravity(true);
            player.setYRot(0.0F);
            player.setYHeadRot(0.0F);
            player.setYBodyRot(0.0F);
            player.setXRot(0.0F);
            level.addNewPlayer(player);
            helper.assertTrue(level.isPositionEntityTicking(player.blockPosition())
                            && level.isPositionEntityTicking(from) && level.isPositionEntityTicking(to),
                    "Barrier fixture is outside an entity-ticking chunk");
            power = PowerClass.STAND.attachGet(player);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power),
                    "Normal Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "Normal summon produced no Hermit controller");
            later(stand.summonLockTicks + 2, this::stretch);
        }

        // HermitBarrierAbility.BarrierAction.actionPerformStart stretches a string with these two calls.
        void stretch() {
            helper.assertTrue(stand.isAlive() && stand.getUser() == player && stand.getPlacedBarrierCount() == 0,
                    "Barrier fixture lost its controller or already has a string");
            stand.attachBarrier(from);
            target = EntityType.VILLAGER.create(level);
            helper.assertTrue(target != null, "Barrier target could not be created");
            target.moveTo(from.getX() + 3.5D, from.getY(), from.getZ() + 0.5D, 0.0F, 0.0F);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(target), "Barrier target could not be added");
            if (kind.primed) {
                helper.assertTrue(target.hurt(level.damageSources().generic(), PRIMING_HIT)
                                && target.invulnerableTime == 20 && target.lastHurt == PRIMING_HIT,
                        "Fixture: the priming hit did not start the target's hurt cooldown");
                helper.assertTrue(plainDamage() <= PRIMING_HIT,
                        "Fixture: the string's plain hit " + plainDamage() + " is stronger than the priming hit");
            }
            target.setDeltaMovement(0.0D, TARGET_RISE, 0.0D);
            healthAtContact = target.getHealth();
            watching = true;
            NeoForge.EVENT_BUS.addListener(damageListener);
            stand.attachBarrier(to);
            List<HermitBarrierVineEntity> strings = level.getEntitiesOfClass(HermitBarrierVineEntity.class,
                    corridor.inflate(2.0D), string -> string.getOwner() == stand && string.isAttachedToBlock());
            helper.assertTrue(strings.size() == 1 && stand.getPlacedBarrierCount() == 1,
                    "Fixture: two anchors stretched " + strings.size() + " strings");
            barrier = strings.getFirst();
            Vec3 start = barrier.getOriginPoint(1.0F);
            Vec3 end = barrier.position();
            helper.assertTrue(start.distanceTo(Vec3.atCenterOf(from)) < 1.0E-6D
                            && end.distanceTo(Vec3.atCenterOf(to)) < 1.0E-6D
                            && target.getBoundingBox().clip(start, end).isPresent(),
                    "Fixture: the target does not stand on the string " + start + " -> " + end);
            attachedAt = level.getGameTime();
            LogUtils.getLogger().info("R765_HP_BARRIER_INPUT case={} world={} cooldown={} lastHurt={} plain={}",
                    kind.label, attachedAt, target.invulnerableTime, target.lastHurt, plainDamage());
            later(1, this::monitor);
        }

        float plainDamage() {
            return HermitPurpleSpec.BARRIER_DAMAGE
                    * JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
        }

        void damaged(LivingDamageEvent.Post event) {
            if (!watching || closed || event.getEntity() != target) return;
            Hit hit = new Hit(event.getSource().type().msgId(), event.getNewDamage(), level.getGameTime());
            if (event.getSource().is(ModDamageTypes.STAND_PROJECTILE)) plainHits.add(hit);
            else if (event.getSource().is(ModDamageTypes.HAMON)) hamonHits.add(hit);
            else otherHits.add(hit);
        }

        // Donor HPVineBarrierEntity.onHitEntity: the target is stopped, the plain hit is tried, and then the string
        // rips and deals its Hamon hit whatever the plain hit returned.
        void monitor() {
            helper.assertTrue(player.isAlive() && stand.isAlive() && target.isAlive(),
                    "Owner, controller or target lifecycle invalidated the barrier fixture");
            long waited = level.getGameTime() - attachedAt;
            String state = "cooldown=" + target.invulnerableTime + " lastHurt=" + target.lastHurt
                    + " plainHits=" + plainHits + " hamonHits=" + hamonHits + " otherHits=" + otherHits
                    + " motion=" + target.getDeltaMovement() + " health=" + healthAtContact + "->" + target.getHealth();
            if (!barrier.wasRipped()) {
                helper.assertTrue(barrier.isAlive(), "The string was removed without ripping; " + state);
                helper.assertTrue(waited < TRIP_TICKS, "The string did not trip within " + TRIP_TICKS
                        + " ticks of a target standing on it"
                        + (kind.primed ? " inside the hurt cooldown of a " + PRIMING_HIT + " hit" : "")
                        + ", the donor rips on contact whatever its plain hit returns; " + state);
                later(1, this::monitor);
                return;
            }
            LogUtils.getLogger().info("R765_HP_BARRIER_TRIP case={} waited={} {}", kind.label, waited, state);
            List<String> errors = new ArrayList<>();
            if (kind.primed) {
                helper.assertTrue(target.invulnerableTime > 10 && plainHits.isEmpty(),
                        "Fixture: the string's plain hit was not refused by the hurt cooldown; " + state);
                if (target.lastHurt != PRIMING_HIT) {
                    errors.add("the refused plain hit changed the target's last hit to " + target.lastHurt);
                }
            }
            else if (plainHits.size() != 1 || Math.abs(plainHits.getFirst().amount() - plainDamage()) > 1.0E-3F) {
                errors.add("a fresh target took the plain hits " + plainHits + ", the donor deals one of " + plainDamage());
            }
            if (hamonHits.size() != 1 || !(hamonHits.getFirst().amount() > 0.0F)) {
                errors.add("the tripped string dealt the Hamon hits " + hamonHits + ", the donor deals one");
            }
            if (!otherHits.isEmpty()) errors.add("unexpected damage " + otherHits);
            float dealt = 0.0F;
            for (Hit hit : plainHits) dealt += hit.amount();
            for (Hit hit : hamonHits) dealt += hit.amount();
            if (Math.abs(healthAtContact - target.getHealth() - dealt) > 1.0E-3F) {
                errors.add("the target lost " + (healthAtContact - target.getHealth()) + " health to hits worth " + dealt);
            }
            if (target.getDeltaMovement().y != 0.0D) {
                errors.add("the target still rises at " + target.getDeltaMovement().y + ", the donor stops it");
            }
            LogUtils.getLogger().info("R765_HP_BARRIER_COMPLETE case={} errors={}", kind.label, errors);
            helper.assertTrue(errors.isEmpty(), "Barrier contact differs from the donor: "
                    + String.join(" | ", errors) + "; " + state);
            LogUtils.getLogger().info("R765_HP_BARRIER_PASS {}", kind.label);
            close();
            helper.succeed();
        }

        void later(int ticks, Runnable task) {
            helper.runAfterDelay(ticks, () -> guarded(task));
        }

        void guarded(Runnable task) {
            if (closed || helper.testInfo.isDone()) return;
            try {
                task.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            watching = false;
            NeoForge.EVENT_BUS.unregister(damageListener);
            try {
                if (stand != null) stand.discardBarriers();
                if (power != null && power.hasPower()) power.getPowerType().forceUnsummon(player, power);
            }
            finally {
                if (barrier != null && !barrier.isRemoved()) barrier.discard();
                if (target != null && !target.isRemoved()) target.discard();
                if (player != null && !player.isRemoved()) {
                    level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                }
                LogUtils.getLogger().info("R765_HP_BARRIER_CLEANUP case={} listenersReleased=true", kind.label);
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
