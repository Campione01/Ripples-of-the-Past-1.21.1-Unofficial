package rotp.addons.weatherreport.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportFuguRainAbility;
import rotp.addons.weatherreport.entity.WeatherReportFugaProjectileEntity;
import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16.5 FugaSummonerEntity rains for 2400 ticks or until the level stops raining and has no rule about its caster.
 * The owner's rule of 2026-10-07 adds that the rain ends with game time. The storm is started the way
 * FuguRainAction.actionPerformStart starts it and is then left to the server's own entity ticks.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportFuguStormGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SETUP_TICK = 5;
    private static final int RAIN_BEFORE_EVENT = 15;
    private static final int FLIGHT_SETTLE = 3;
    private static final int FLIGHT_CHECK = 10;
    private static final int OBSERVE = 40;
    private static final int MIN_NEW_FISH = 3;

    private WeatherReportFuguStormGameTests() {}

    @GameTest(template = "empty", batch = "wr_storm_caster_dies", timeoutTicks = 300)
    public static void stormKeepsRainingAfterItsCasterDies(GameTestHelper helper) {
        new CasterFixture(helper, CasterEvent.DIES).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_caster_leaves", timeoutTicks = 300)
    public static void stormKeepsRainingAfterItsCasterLeavesTheLevel(GameTestHelper helper) {
        new CasterFixture(helper, CasterEvent.LEAVES).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_caster_spectator", timeoutTicks = 300)
    public static void stormKeepsRainingWhileItsCasterIsASpectator(GameTestHelper helper) {
        new CasterFixture(helper, CasterEvent.SPECTATES).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_rain_stops", timeoutTicks = 300)
    public static void stormOfADeadCasterEndsWhenTheRainStops(GameTestHelper helper) {
        new CasterFixture(helper, CasterEvent.DIES_THEN_RAIN_STOPS).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_old_save", timeoutTicks = 300)
    public static void oldSaveWithoutACasterRainsForItsRemainingAge(GameTestHelper helper) {
        new SavedFixture(helper, SavedCase.OLD_SAVE).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_reload_past_end", timeoutTicks = 300)
    public static void stormReloadedAfterItsEndIsDiscardedAtOnce(GameTestHelper helper) {
        new SavedFixture(helper, SavedCase.RELOAD_PAST_END).start();
    }

    @GameTest(template = "empty", batch = "wr_storm_reload_gap", timeoutTicks = 300)
    public static void timeSpentUnloadedCountsAgainstTheStorm(GameTestHelper helper) {
        new SavedFixture(helper, SavedCase.RELOAD_GAP).start();
    }

    private enum CasterEvent {
        DIES("caster_dies"),
        LEAVES("caster_leaves"),
        SPECTATES("caster_spectator"),
        DIES_THEN_RAIN_STOPS("rain_stops");

        final String id;

        CasterEvent(String id) {
            this.id = id;
        }
    }

    private enum SavedCase {
        OLD_SAVE("old_save", 20),
        RELOAD_PAST_END("reload_past_end", 20),
        RELOAD_GAP("reload_gap", 60);

        final String id;
        final int remainingAtLoad;

        SavedCase(String id, int remainingAtLoad) {
            this.id = id;
            this.remainingAtLoad = remainingAtLoad;
        }
    }

    private abstract static class StormFixture {
        final GameTestHelper helper;
        final ServerLevel level;
        BlockPos cell;
        WeatherReportFugaSummonerEntity storm;
        boolean finished;

        StormFixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        final void start() {
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            // GameTest keeps only the template's own chunk entity-ticking, so the storm and the caster stay inside it.
            cell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 2, chunk.getMinBlockZ() + 8);
            helper.assertTrue(level.isPositionEntityTicking(cell)
                            && level.isPositionEntityTicking(cell.above((int) WeatherReportFuguRainAbility.SUMMON_HEIGHT)),
                    "The storm fixture is outside the entity-ticking test chunk");
            helper.onEachTick(() -> {
                if (finished) {
                    return;
                }
                try {
                    tick(helper.getTick());
                }
                catch (RuntimeException | Error error) {
                    finished = true;
                    close();
                    throw error;
                }
            });
        }

        abstract void tick(long now);

        void setRain(boolean raining) {
            if (raining) {
                level.setWeatherParameters(0, 12000, true, false);
            }
            else {
                level.setWeatherParameters(12000, 0, false, false);
            }
            level.setRainLevel(raining ? 1.0F : 0.0F);
        }

        Vec3 stormPos() {
            return new Vec3(cell.getX() + 0.5D, cell.getY() + WeatherReportFuguRainAbility.SUMMON_HEIGHT,
                    cell.getZ() + 0.5D);
        }

        Set<UUID> stormFish(WeatherReportFugaSummonerEntity coordinator) {
            Set<UUID> fish = new HashSet<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof WeatherReportFugaProjectileEntity projectile && projectile.isAlive()
                        && projectile.isOwnedBy(coordinator)) {
                    fish.add(projectile.getUUID());
                }
            }
            return fish;
        }

        void pass(String id, String outcome) {
            finished = true;
            close();
            LOGGER.info("R764_WR_STORM_PASS {} {}", id, outcome);
            helper.succeed();
        }

        void close() {
            try {
                if (storm != null && !storm.isRemoved()) {
                    storm.discard();
                }
            }
            finally {
                setRain(false);
            }
        }
    }

    private static final class CasterFixture extends StormFixture {
        private final CasterEvent event;
        private boolean spectating;
        private Player user;
        private StandPower power;
        private WeatherReportStandEntity stand;
        private WeatherReportFugaProjectileEntity flight;
        private long castAt = -1L;
        private long flightAt = -1L;
        private long eventAt = -1L;
        private long rainOffAt = -1L;
        private Set<UUID> fishAtEvent = Set.of();
        private double flightYAtEvent;
        private String flightAfter = "not checked";
        private boolean flightKept;

        private CasterFixture(GameTestHelper helper, CasterEvent event) {
            super(helper);
            this.event = event;
        }

        @Override
        void tick(long now) {
            if (castAt < 0L) {
                if (now >= SETUP_TICK) {
                    cast();
                    castAt = now;
                }
            }
            else if (flightAt < 0L) {
                if (now - castAt >= RAIN_BEFORE_EVENT) {
                    int fish = stormFish(storm).size();
                    helper.assertTrue(storm.isAlive() && fish >= MIN_NEW_FISH,
                            "The storm did not rain while its caster was present: alive=" + storm.isAlive()
                                    + ", fish=" + fish);
                    flight = new WeatherReportFugaProjectileEntity(user, storm, level);
                    flight.setPos(cell.getX() + 3.5D, cell.getY() + 38.0D, cell.getZ() + 0.5D);
                    flight.setDeltaMovement(0.0D, WeatherReportFugaSummonerEntity.FALL_SPEED, 0.0D);
                    helper.assertTrue(level.isPositionEntityTicking(flight.blockPosition())
                                    && level.addFreshEntity(flight),
                            "Could not put a fish in flight inside the ticking chunk");
                    flightAt = now;
                }
            }
            else if (eventAt < 0L) {
                if (now - flightAt >= FLIGHT_SETTLE) {
                    helper.assertTrue(flight.isAlive() && flight.getY() < cell.getY() + 37.0D,
                            "The fish in flight is not falling before the event: alive=" + flight.isAlive()
                                    + ", y=" + flight.getY());
                    clearUnderFlight();
                    applyEvent();
                    fishAtEvent = stormFish(storm);
                    flightYAtEvent = flight.getY();
                    eventAt = now;
                }
            }
            else if (event == CasterEvent.DIES_THEN_RAIN_STOPS) {
                tickRainStop(now);
            }
            else {
                if (now - eventAt == FLIGHT_CHECK) {
                    flightKept = flight.isAlive() && flight.getY() < flightYAtEvent - 5.0D;
                    flightAfter = "alive=" + flight.isAlive() + ", y=" + String.format("%.1f", flightYAtEvent)
                            + "->" + String.format("%.1f", flight.getY());
                }
                if (now - eventAt >= OBSERVE) {
                    judge();
                }
            }
        }

        /** FuguRainAction.actionPerformStart: the storm entity above the target point, registered with the Stand. */
        private void cast() {
            setRain(true);
            helper.assertTrue(level.isRaining(), "The level is not raining");
            user = new Player(level, BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "wrs-" + event.ordinal())) {
                @Override
                public boolean isSpectator() {
                    return spectating;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.setInvulnerable(true);
            user.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof WeatherReportStandEntity,
                    "Production Weather Report summon failed");
            stand = (WeatherReportStandEntity) power.getSummonedStandEntity();

            storm = new WeatherReportFugaSummonerEntity(user, stand, level);
            Vec3 pos = stormPos();
            storm.moveTo(pos.x, pos.y, pos.z);
            helper.assertTrue(level.addFreshEntity(storm), "Could not add the storm");
            stand.activateFugaSummoner(storm);
        }

        /** An older storm fish falling just under the fish in flight can stop it, whatever the caster does. */
        private void clearUnderFlight() {
            for (WeatherReportFugaProjectileEntity fish : level.getEntitiesOfClass(
                    WeatherReportFugaProjectileEntity.class, flight.getBoundingBox().inflate(2.0D, 4.0D, 2.0D),
                    fish -> fish != flight)) {
                fish.discard();
            }
        }

        private void applyEvent() {
            switch (event) {
                case DIES, DIES_THEN_RAIN_STOPS -> {
                    user.kill();
                    helper.assertTrue(!user.isAlive(), "The caster did not die");
                }
                case LEAVES -> {
                    user.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                    helper.assertTrue(user.isRemoved() && level.getEntity(user.getUUID()) == null,
                            "The caster did not leave the level");
                }
                case SPECTATES -> {
                    spectating = true;
                    helper.assertTrue(user.isAlive() && user.isSpectator(), "The caster is not a spectator");
                }
            }
        }

        private void judge() {
            Set<UUID> fresh = stormFish(storm);
            fresh.removeAll(fishAtEvent);
            fresh.remove(flight.getUUID());
            String outcome = "stormAlive=" + storm.isAlive() + ", stormAge=" + storm.tickCount
                    + ", fishAtEvent=" + fishAtEvent.size() + ", newFishIn" + OBSERVE + "Ticks=" + fresh.size()
                    + ", flightFishAfter" + FLIGHT_CHECK + "Ticks{" + flightAfter + "}, casterAlive="
                    + user.isAlive() + ", casterInLevel=" + (level.getEntity(user.getUUID()) != null)
                    + ", casterSpectator=" + user.isSpectator();
            helper.assertTrue(storm.isAlive() && fresh.size() >= MIN_NEW_FISH && flightKept,
                    "The pufferfish rain depended on its caster (" + event.id + "): " + outcome);
            pass(event.id, outcome);
        }

        private void tickRainStop(long now) {
            if (rainOffAt < 0L) {
                if (now - eventAt >= 5) {
                    helper.assertTrue(storm.isAlive() && !stormFish(storm).isEmpty(),
                            "The storm of the dead caster ended before the rain did: alive=" + storm.isAlive());
                    setRain(false);
                    rainOffAt = now;
                }
            }
            else if (now - rainOffAt >= 3) {
                int fish = stormFish(storm).size();
                String outcome = "stormRemoved=" + storm.isRemoved() + ", fishLeft=" + fish
                        + ", raining=" + level.isRaining();
                helper.assertTrue(storm.isRemoved() && fish == 0 && !level.isRaining(),
                        "The storm did not end with the rain (" + event.id + "): " + outcome);
                pass(event.id, outcome);
            }
        }

        @Override
        void close() {
            try {
                if (flight != null && !flight.isRemoved()) {
                    flight.discard();
                }
                if (user != null && !user.isRemoved()) {
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.discard();
                }
                if (stand != null && !stand.isRemoved()) {
                    stand.discard();
                }
            }
            finally {
                super.close();
            }
        }
    }

    private static final class SavedFixture extends StormFixture {
        private static final int UNLOAD_AFTER = 5;
        private final SavedCase savedCase;
        private final int reloadAfter;
        private long loadedAt = -1L;
        private long unloadedAt = -1L;
        private long reloadedAt = -1L;
        private long removedAt = -1L;
        private CompoundTag saved;
        private int remainingAtLoad = -1;
        private int remainingAtUnload = -1;
        private int remainingAtReload = -1;
        private int fishBeforeLimit = -1;

        private SavedFixture(GameTestHelper helper, SavedCase savedCase) {
            super(helper);
            this.savedCase = savedCase;
            this.reloadAfter = savedCase == SavedCase.RELOAD_PAST_END ? 30 : 25;
        }

        @Override
        void tick(long now) {
            if (loadedAt < 0L) {
                if (now >= SETUP_TICK) {
                    setRain(true);
                    helper.assertTrue(level.isRaining(), "The level is not raining");
                    storm = load(oldSave(WeatherReportFugaSummonerEntity.LIFETIME_TICKS
                            - savedCase.remainingAtLoad));
                    remainingAtLoad = storm.remainingLifetime();
                    loadedAt = now;
                }
                return;
            }
            if (storm.isRemoved() && removedAt < 0L && (savedCase == SavedCase.OLD_SAVE || reloadedAt >= 0L)) {
                removedAt = now;
            }
            switch (savedCase) {
                case OLD_SAVE -> tickOldSave(now);
                case RELOAD_PAST_END, RELOAD_GAP -> tickReload(now);
            }
        }

        /** What the port wrote before the storm kept a game-time end: its age, its caster and its Stand. */
        private CompoundTag oldSave(int age) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Age", age);
            tag.putUUID("Owner", UUID.randomUUID());
            tag.putUUID("Stand", UUID.randomUUID());
            return tag;
        }

        private WeatherReportFugaSummonerEntity load(CompoundTag tag) {
            WeatherReportFugaSummonerEntity loaded = WeatherReportAddon.FUGA_SUMMONER.get().create(level);
            helper.assertTrue(loaded != null, "Could not create the storm entity");
            loaded.load(tag);
            Vec3 pos = stormPos();
            loaded.moveTo(pos.x, pos.y, pos.z);
            helper.assertTrue(level.addFreshEntity(loaded), "Could not add the loaded storm");
            return loaded;
        }

        private void tickOldSave(long now) {
            long age = now - loadedAt;
            if (age == 15) {
                helper.assertTrue(storm.isAlive(), "The old save ended before its remaining age");
                fishBeforeLimit = stormFish(storm).size();
            }
            if (age >= 26) {
                String outcome = "remainingAtLoad=" + remainingAtLoad + ", fishAfter15Ticks=" + fishBeforeLimit
                        + ", removedAfter=" + (removedAt < 0L ? "never" : String.valueOf(removedAt - loadedAt));
                helper.assertTrue(remainingAtLoad == savedCase.remainingAtLoad && fishBeforeLimit >= MIN_NEW_FISH
                                && removedAt >= 0L && removedAt - loadedAt >= 19 && removedAt - loadedAt <= 22,
                        "An old save without a caster did not rain for its remaining age (" + savedCase.id + "): "
                                + outcome);
                pass(savedCase.id, outcome);
            }
        }

        private void tickReload(long now) {
            if (unloadedAt < 0L) {
                if (now - loadedAt >= UNLOAD_AFTER) {
                    helper.assertTrue(storm.isAlive(), "The storm ended before it was unloaded");
                    remainingAtUnload = storm.remainingLifetime();
                    saved = new CompoundTag();
                    helper.assertTrue(storm.saveWithoutId(saved) != null, "Could not save the storm");
                    // A chunk unload: the entity leaves the level without being destroyed.
                    storm.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    unloadedAt = now;
                }
                return;
            }
            if (reloadedAt < 0L) {
                if (now - unloadedAt >= reloadAfter) {
                    storm = load(saved);
                    remainingAtReload = storm.remainingLifetime();
                    reloadedAt = now;
                }
                return;
            }
            long sinceReload = now - reloadedAt;
            long gap = reloadedAt - unloadedAt;
            int expectedRemaining = (int) Math.max(0L, remainingAtUnload - gap);
            String outcome = "remainingAtLoad=" + remainingAtLoad + ", remainingAtUnload=" + remainingAtUnload
                    + ", unloadedForTicks=" + gap + ", remainingAtReload=" + remainingAtReload
                    + ", expectedRemaining=" + expectedRemaining + ", removedAfterReload="
                    + (removedAt < 0L ? "never" : String.valueOf(removedAt - reloadedAt));
            if (savedCase == SavedCase.RELOAD_PAST_END) {
                if (sinceReload >= 3) {
                    helper.assertTrue(expectedRemaining == 0 && remainingAtReload == 0 && removedAt >= 0L,
                            "A storm reloaded after its game-time end resumed (" + savedCase.id + "): " + outcome);
                    pass(savedCase.id, outcome);
                }
            }
            else if (sinceReload >= expectedRemaining + 4) {
                helper.assertTrue(expectedRemaining > 0 && Math.abs(remainingAtReload - expectedRemaining) <= 1
                                && removedAt >= 0L
                                && Math.abs(removedAt - reloadedAt - expectedRemaining) <= 2,
                        "The time a storm spent unloaded did not count against it (" + savedCase.id + "): "
                                + outcome);
                pass(savedCase.id, outcome);
            }
        }
    }
}
