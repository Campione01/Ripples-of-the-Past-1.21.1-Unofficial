package rotp.addons.wonderofu.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.WonderOfUDamageTypes;
import rotp.addons.wonderofu.entity.CarCalamityEntity;
import rotp.addons.wonderofu.entity.CarCalamityEntity.CarRemovalReason;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The 1.16.5 car is independent once it is in the world: it falls and explodes whatever happens to its caster.
 * Each case spawns the car through the production damage interception and then ends the caster's hold on it.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUCarGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    // A ServerPlayer ignores ordinary damage for its first 60 ticks in the world.
    private static final int SPAWN_PROTECTION_TICKS = 65;
    private static final int LANDING_WINDOW_TICKS = 120;

    private WonderOfUCarGameTests() {}

    @GameTest(template = "empty", batch = "wou_car_living_caster", timeoutTicks = 220)
    public static void carLandsAndExplodesWhileCasterLives(GameTestHelper helper) {
        run(helper, "living_caster", fixture -> {});
    }

    @GameTest(template = "empty", batch = "wou_car_caster_death", timeoutTicks = 220)
    public static void carStillLandsAndExplodesAfterCasterDies(GameTestHelper helper) {
        run(helper, "caster_death", fixture -> {
            fixture.owner.kill();
            helper.assertTrue(fixture.owner.isDeadOrDying(), "The caster was not really killed");
        });
    }

    @GameTest(template = "empty", batch = "wou_car_caster_logout", timeoutTicks = 220)
    public static void carStillLandsAndExplodesAfterCasterLogsOut(GameTestHelper helper) {
        run(helper, "caster_logout", fixture -> {
            UUID ownerUuid = fixture.owner.getUUID();
            fixture.owner.logout();
            helper.assertTrue(fixture.level.getEntity(ownerUuid) == null
                            && fixture.level.getServer().getPlayerList().getPlayer(ownerUuid) == null,
                    "The caster did not really leave the server");
        });
    }

    @GameTest(template = "empty", batch = "wou_car_mode_off", timeoutTicks = 220)
    public static void carStillLandsAndExplodesAfterCarModeIsSwitchedOff(GameTestHelper helper) {
        run(helper, "mode_off", fixture -> {
            fixture.stand.setCarEnabled(false);
            helper.assertTrue(!fixture.stand.isCarEnabled(), "Car mode did not switch off");
        });
    }

    // 1.16 drew the car as a plain living mob: a hit plays CAR_HURT and starts the red hurt flash, and a destroyed
    // car lies dying for vanilla's 20 death ticks (the renderer tips it over) before it is removed, without exploding.
    @GameTest(template = "empty", batch = "wou_car_hurt", timeoutTicks = 220)
    public static void struckCarFlashesAndDestroyedCarTipsOver(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "hurt_and_death");
        int[] hurtSounds = new int[1];
        int[] struckHurtTime = new int[1];
        long[] diedAt = {-1L};
        int[] lastDeathTime = new int[1];
        boolean[] finished = new boolean[1];
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            try {
                fixture.provokeCar();
                CarCalamityEntity car = fixture.car;
                // Held in the air, so the hit and the death are not cut short by the landing blast.
                car.setNoGravity(true);
                car.setDeltaMovement(Vec3.ZERO);
                fixture.soundListener = event -> {
                    if (event.getLevel() == fixture.level && event.getSound() != null
                            && event.getSound().value() == WonderOfUAddon.CAR_DAMAGE.get()
                            && event.getPosition().distanceToSqr(car.position()) < 1.0E-6D) {
                        hurtSounds[0]++;
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class,
                        fixture.soundListener);
                boolean struck = car.hurt(fixture.level.damageSources().mobAttack(fixture.attacker), 4.0F);
                car.setDeltaMovement(Vec3.ZERO);
                struckHurtTime[0] = car.hurtTime;
                helper.assertTrue(struck && car.isAlive() && car.getMaxHealth() == 40.0F
                                && car.getHealth() < car.getMaxHealth(),
                        "The struck car did not take the hit as a 40-health mob: struck=" + struck
                                + ", health=" + car.getHealth() + "/" + car.getMaxHealth());
                helper.assertTrue(car.hurtTime == 10,
                        "The struck car did not start the vanilla hurt flash: hurtTime=" + car.hurtTime);
                helper.assertTrue(hurtSounds[0] == 1,
                        "The struck car did not play CAR_HURT once: heard " + hurtSounds[0]);
            }
            catch (RuntimeException | Error exception) {
                fixture.close();
                throw exception;
            }
        });
        helper.onEachTick(() -> {
            CarCalamityEntity car = fixture.car;
            if (car == null || finished[0]) {
                return;
            }
            try {
                if (diedAt[0] < 0L) {
                    if (car.invulnerableTime > 10) {
                        return;
                    }
                    car.hurt(fixture.level.damageSources().mobAttack(fixture.attacker), 100.0F);
                    diedAt[0] = helper.getTick();
                    helper.assertTrue(car.isDeadOrDying() && !car.isRemoved(),
                            "The destroyed car did not stay to die: dead=" + car.isDeadOrDying()
                                    + ", removed=" + car.isRemoved() + ", reason=" + car.getCarRemovalReason());
                    helper.assertTrue(car.getCarRemovalReason() == CarRemovalReason.DESTROYED
                                    && fixture.stand.getCurrentCarUuid() == null,
                            "The Stand did not forget its destroyed car at once: reason="
                                    + car.getCarRemovalReason() + ", currentCar=" + fixture.stand.getCurrentCarUuid());
                    return;
                }
                if (!car.isRemoved()) {
                    lastDeathTime[0] = car.deathTime;
                    helper.assertTrue(helper.getTick() - diedAt[0] <= 25,
                            "The dying car was never removed: deathTime=" + car.deathTime);
                    return;
                }
                finished[0] = true;
                String outcome = "hurtTime=" + struckHurtTime[0] + ", hurtSounds=" + hurtSounds[0]
                        + ", lastDeathTime=" + lastDeathTime[0] + ", removedAfter=" + (helper.getTick() - diedAt[0])
                        + ", removal=" + car.getRemovalReason() + ", reason=" + car.getCarRemovalReason();
                fixture.close();
                helper.assertTrue(car.getRemovalReason() == Entity.RemovalReason.KILLED
                                && car.getCarRemovalReason() == CarRemovalReason.DESTROYED
                                && lastDeathTime[0] == 19,
                        "The destroyed car did not die like a 1.16 mob: " + outcome);
                LOGGER.info("R770_WOU_CAR_HURT_PASS hurt_and_death {}", outcome);
                helper.succeed();
            }
            catch (RuntimeException | Error exception) {
                finished[0] = true;
                fixture.close();
                throw exception;
            }
        });
    }

    // The 1.16 car is a TameableEntity, an AnimalEntity: a kill by a player drops 1 + random.nextInt(3)
    // experience; vanilla gives a kill by anything else none.
    @GameTest(template = "empty", batch = "wou_car_xp_player", timeoutTicks = 220)
    public static void carKilledByPlayerDropsAnimalExperience(GameTestHelper helper) {
        killHeldCar(helper, "xp_player", true);
    }

    @GameTest(template = "empty", batch = "wou_car_xp_other", timeoutTicks = 220)
    public static void carKilledByMobDropsNoExperience(GameTestHelper helper) {
        killHeldCar(helper, "xp_other", false);
    }

    private static void killHeldCar(GameTestHelper helper, String name, boolean byPlayer) {
        Fixture fixture = new Fixture(helper, name);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            try {
                fixture.provokeCar();
                CarCalamityEntity car = fixture.car;
                car.setNoGravity(true);
                car.setDeltaMovement(Vec3.ZERO);
                int[] experienceEvent = {-1, -1};
                Player[] eventPlayer = new Player[1];
                fixture.listen(LivingExperienceDropEvent.class, event -> {
                    if (event.getEntity() == car) {
                        experienceEvent[0] = event.getOriginalExperience();
                        experienceEvent[1] = event.getDroppedExperience();
                        eventPlayer[0] = event.getAttackingPlayer();
                    }
                });
                DamageSource source = byPlayer
                        ? fixture.level.damageSources().playerAttack(fixture.owner)
                        : fixture.level.damageSources().mobAttack(fixture.attacker);
                car.hurt(source, 100.0F);
                int orbExperience = collectOrbExperience(fixture.level, car);
                String outcome = "dead=" + car.isDeadOrDying() + ", original=" + experienceEvent[0]
                        + ", dropped=" + experienceEvent[1] + ", orbs=" + orbExperience
                        + ", attackingPlayer=" + (eventPlayer[0] != null ? eventPlayer[0].getGameProfile().getName() : null);
                fixture.close();
                helper.assertTrue(car.isDeadOrDying(), "The car was not killed: " + outcome);
                if (byPlayer) {
                    helper.assertTrue(eventPlayer[0] == fixture.owner
                                    && experienceEvent[0] >= 1 && experienceEvent[0] <= 3
                                    && experienceEvent[1] == experienceEvent[0]
                                    && orbExperience == experienceEvent[1],
                            "A car killed by a player did not drop the 1.16 animal's 1 to 3 experience: " + outcome);
                }
                else {
                    helper.assertTrue(experienceEvent[0] == -1 && orbExperience == 0,
                            "A car killed by a mob dropped experience: " + outcome);
                }
                LOGGER.info("R770_WOU_CAR_XP_PASS {} {}", name, outcome);
                helper.succeed();
            }
            catch (RuntimeException | Error exception) {
                fixture.close();
                throw exception;
            }
        });
    }

    private static int collectOrbExperience(ServerLevel level, Entity around) {
        int total = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class,
                around.getBoundingBox().inflate(2.0D))) {
            total += orb.getValue() * Math.max(1, orb.saveWithoutId(new CompoundTag()).getInt("Count"));
            orb.discard();
        }
        return total;
    }

    // 1.16 only exploded a living car on landing: a car destroyed just above the ground falls while it dies,
    // lands, and is removed after its death ticks without a blast.
    @GameTest(template = "empty", batch = "wou_car_dying_landing", timeoutTicks = 220)
    public static void carDestroyedAboveFloorLandsWithoutExploding(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "dying_landing");
        // Block damage is on here, so a blast would break the floor.
        fixture.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, fixture.level.getServer());
        int[] explosions = new int[1];
        long[] diedAt = {-1L};
        double[] landedAt = {Double.NaN};
        boolean[] finished = new boolean[1];
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            try {
                fixture.provokeCar();
                CarCalamityEntity car = fixture.car;
                fixture.listen(ExplosionEvent.Start.class, event -> {
                    if (event.getLevel() == fixture.level && event.getExplosion().getDirectSourceEntity() == car) {
                        explosions[0]++;
                    }
                });
                car.moveTo(car.getX(), fixture.attacker.getY() + 1.0D, car.getZ());
                car.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(car.isAlive() && !car.onGround(), "The car did not start just above the floor");
                car.hurt(fixture.level.damageSources().mobAttack(fixture.attacker), 100.0F);
                diedAt[0] = helper.getTick();
                helper.assertTrue(car.isDeadOrDying() && !car.isRemoved(),
                        "The car was not left dying above the floor: removed=" + car.isRemoved());
            }
            catch (RuntimeException | Error exception) {
                finished[0] = true;
                fixture.close();
                throw exception;
            }
        });
        helper.onEachTick(() -> {
            CarCalamityEntity car = fixture.car;
            if (car == null || diedAt[0] < 0L || finished[0]) {
                return;
            }
            try {
                if (!car.isRemoved()) {
                    if (car.onGround() && Double.isNaN(landedAt[0])) {
                        landedAt[0] = car.getY();
                    }
                    helper.assertTrue(helper.getTick() - diedAt[0] <= 25,
                            "The dying car was never removed: deathTime=" + car.deathTime);
                    return;
                }
                finished[0] = true;
                BlockPos below = fixture.attacker.blockPosition().below();
                int floorLeft = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (!fixture.level.getBlockState(below.offset(dx, 0, dz)).isAir()) {
                            floorLeft++;
                        }
                    }
                }
                String outcome = "landedAt=" + landedAt[0] + ", floorTop=" + (below.getY() + 1)
                        + ", explosions=" + explosions[0] + ", floorLeft=" + floorLeft + "/25"
                        + ", removal=" + car.getRemovalReason() + ", reason=" + car.getCarRemovalReason()
                        + ", attackerHealth=" + fixture.attacker.getHealth();
                boolean attackerUnhurt = fixture.attacker.getHealth() == fixture.attacker.getMaxHealth();
                fixture.close();
                helper.assertTrue(explosions[0] == 0 && floorLeft == 25 && attackerUnhurt
                                && car.getRemovalReason() == Entity.RemovalReason.KILLED
                                && car.getCarRemovalReason() == CarRemovalReason.DESTROYED,
                        "The dying car exploded: " + outcome);
                helper.assertTrue(landedAt[0] == below.getY() + 1,
                        "The dying car did not land on the floor: " + outcome);
                LOGGER.info("R770_WOU_CAR_DYING_LANDING_PASS dying_landing {}", outcome);
                helper.succeed();
            }
            catch (RuntimeException | Error exception) {
                finished[0] = true;
                fixture.close();
                throw exception;
            }
        });
    }

    private static void run(GameTestHelper helper, String name, Consumer<Fixture> afterCarSpawned) {
        Fixture fixture = new Fixture(helper, name);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            try {
                fixture.provokeCar();
                afterCarSpawned.accept(fixture);
                fixture.armedAt = helper.getTick();
            }
            catch (RuntimeException | Error exception) {
                fixture.close();
                throw exception;
            }
        });
        helper.onEachTick(fixture::observe);
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final String name;
        private final ServerLevel level;
        private final boolean mobGriefingBefore;
        private final List<BlockPos> floorBlocks = new ArrayList<>();
        private OnlinePlayer owner;
        private StandPower power;
        private WonderOfUStandEntity stand;
        private Husk attacker;
        private CarCalamityEntity car;
        private double carSpawnY;
        private long armedAt = -1L;
        private boolean hitByCar;
        private boolean finished;
        private Consumer<PlayLevelSoundEvent.AtPosition> soundListener;
        private final List<Consumer<?>> listeners = new ArrayList<>();

        private Fixture(GameTestHelper helper, String name) {
            this.helper = helper;
            this.name = name;
            level = helper.getLevel();
            GameRules.BooleanValue mobGriefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
            mobGriefingBefore = mobGriefing.get();
            // The radius-6 blast must not dig into the neighbouring test plots.
            mobGriefing.set(false, level.getServer());
            try {
                // GameTest keeps only the template's own chunk entity-ticking, so everything stays inside it. The scene
                // stands 8 blocks up, as in the stamina and walls fixtures: every test of this class runs in its own
                // batch, and the earlier ones leave their structure blocks and two-high barrier rings (grid steps of 6)
                // on the ground of the same chunk, where a dying car once came to rest a block above the floor.
                BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
                ChunkPos chunk = new ChunkPos(template);
                BlockPos attackerPos = clearAttackerPos(level, chunk, template.getY() + 8);
                helper.assertTrue(attackerPos != null, "No place in the test chunk is clear for the scene");
                BlockPos ownerPos = attackerPos.west(4);
                helper.assertTrue(level.isPositionEntityTicking(attackerPos)
                                && level.isPositionEntityTicking(attackerPos.above(7)),
                        "The car's fall is outside the entity-ticking test chunk");
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = attackerPos.offset(dx, -1, dz);
                        if (level.getBlockState(pos).isAir()
                                && level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState())) {
                            floorBlocks.add(pos);
                        }
                    }
                }
                // the caster's own footing, which the ground gave him before the scene went up
                BlockPos ownerFooting = ownerPos.below();
                if (level.getBlockState(ownerFooting).isAir()
                        && level.setBlockAndUpdate(ownerFooting, Blocks.STONE.defaultBlockState())) {
                    floorBlocks.add(ownerFooting);
                }

                String playerName = "Wou" + name;
                owner = new OnlinePlayer(level, playerName.substring(0, Math.min(16, playerName.length())));
                owner.login();
                owner.moveTo(Vec3.atBottomCenterOf(ownerPos));
                owner.setGameMode(GameType.SURVIVAL);
                helper.assertTrue(level.getEntity(owner.getUUID()) == owner,
                        "Native PlayerList login did not register the caster");

                power = PowerClass.STAND.attachGet(owner);
                helper.assertTrue(StandPowerTransitions.insert(power,
                                new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                                == StandPowerTransitions.Status.APPLIED,
                        "Could not grant Wonder of U to the caster");
                helper.assertTrue(WonderOfUAddon.WONDER_OF_YOU.get().summon(owner, power)
                                && power.getSummonedStandEntity() instanceof WonderOfUStandEntity,
                        "Production Wonder of U summon failed");
                stand = (WonderOfUStandEntity) power.getSummonedStandEntity();

                attacker = EntityType.HUSK.create(level);
                helper.assertTrue(attacker != null, "Could not create the attacker");
                attacker.setNoAi(true);
                attacker.setPersistenceRequired();
                attacker.moveTo(Vec3.atBottomCenterOf(attackerPos));
                helper.assertTrue(level.addFreshEntity(attacker), "Could not add the attacker");
            }
            catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        /**
         * The attacker's place at height y: chunk x+8, z+8 when it is clear, else the clear place nearest to it, so that
         * nothing an earlier test left at that height (the stamina and walls fixtures use it too) shapes the scene.
         * Clear means air in the 5x5 floor and the column the car falls through, and around the caster 4 blocks west.
         */
        private static BlockPos clearAttackerPos(ServerLevel level, ChunkPos chunk, int y) {
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int x = 6; x <= 13; x++) {
                for (int z = 2; z <= 13; z++) {
                    int distance = Math.abs(x - 8) + Math.abs(z - 8);
                    BlockPos attackerPos = new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
                    if (distance < bestDistance && isAir(level, attackerPos.offset(-2, -1, -2), attackerPos.offset(2, 9, 2))
                            && isAir(level, attackerPos.offset(-5, 0, -1), attackerPos.offset(-3, 2, 1))) {
                        best = attackerPos;
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        private static boolean isAir(ServerLevel level, BlockPos from, BlockPos to) {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (!level.getBlockState(pos).isAir()) return false;
            }
            return true;
        }

        /** Car mode on, then a real hostile hit on the caster: the production interception spawns the car. */
        private void provokeCar() {
            stand.setCarEnabled(true);
            helper.assertTrue(stand.isCarEnabled() && stand.getCurrentCarUuid() == null,
                    "Car mode did not start clean");
            float healthBefore = owner.getHealth();
            boolean damaged = owner.hurt(level.damageSources().mobAttack(attacker), 4.0F);
            UUID carUuid = stand.getCurrentCarUuid();
            Entity spawned = carUuid != null ? level.getEntity(carUuid) : null;
            helper.assertTrue(!damaged && owner.getHealth() == healthBefore
                            && spawned instanceof CarCalamityEntity,
                    "The hostile hit was not intercepted into a car: damaged=" + damaged
                            + ", carUuid=" + carUuid + ", entity=" + spawned);
            car = (CarCalamityEntity) spawned;
            carSpawnY = car.getY();
            helper.assertTrue(Math.abs(carSpawnY - (attacker.getY() + 7.0D)) < 1.0E-6D
                            && car.isAlive() && !car.onGround(),
                    "The car did not start seven blocks above its attacker");
        }

        private <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, type, listener);
            listeners.add(listener);
        }

        private void observe() {
            if (armedAt < 0L || finished) {
                return;
            }
            DamageSource last = attacker.getLastDamageSource();
            if (last != null && last.is(WonderOfUDamageTypes.CALAMITY_CAR) && last.getDirectEntity() == car) {
                hitByCar = true;
            }
            boolean timedOut = helper.getTick() - armedAt > LANDING_WINDOW_TICKS;
            if (!car.isRemoved() && !timedOut) {
                return;
            }
            finished = true;
            CarRemovalReason reason = car.getCarRemovalReason();
            String outcome = "removed=" + car.isRemoved() + ", reason=" + reason
                    + ", carTicks=" + car.tickCount
                    + ", fell=" + String.format("%.2f", carSpawnY - car.getY())
                    + ", attackerHitByCar=" + hitByCar
                    + ", attackerHealth=" + attacker.getHealth();
            close();
            helper.assertTrue(reason == CarRemovalReason.COLLISION && hitByCar,
                    "The car did not land and explode (" + name + "): " + outcome);
            LOGGER.info("R764_WOU_CAR_PASS {} {}", name, outcome);
            helper.succeed();
        }

        private void close() {
            try {
                if (car != null && !car.isRemoved()) car.discard();
                if (attacker != null && !attacker.isRemoved()) attacker.discard();
                if (owner != null) {
                    if (power != null && power.isSummoned()) {
                        WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(owner, power);
                    }
                    owner.logout();
                }
            }
            finally {
                if (soundListener != null) {
                    NeoForge.EVENT_BUS.unregister(soundListener);
                    soundListener = null;
                }
                for (Consumer<?> listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
                listeners.clear();
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                        .set(mobGriefingBefore, level.getServer());
                for (BlockPos pos : floorBlocks) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                floorBlocks.clear();
            }
        }
    }

    private static final class OnlinePlayer extends ServerPlayer {
        private final Connection network;
        private final EmbeddedChannel channel;
        private boolean loggedOut;

        private OnlinePlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name),
                    ClientInformation.createDefault());
            network = new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
                    super.setupInboundProtocol(protocol, listener);
                    if (listener instanceof ServerGamePacketListenerImpl) {
                        // Native login syncs datapacks before returning; this server-only fixture has no client.
                        OnlinePlayer.this.connection = new SilentConnection(
                                serverLevel(), OnlinePlayer.this, this);
                    }
                }
            };
            channel = new EmbeddedChannel(network);
        }

        private void login() {
            server.getPlayerList().placeNewPlayer(network, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false));
        }

        /** The native PlayerList logout, which fires PlayerLoggedOutEvent. */
        private void logout() {
            if (loggedOut) {
                return;
            }
            loggedOut = true;
            if (server.getPlayerList().getPlayer(getUUID()) == this) server.getPlayerList().remove(this);
            discard();
            channel.finishAndReleaseAll();
        }
    }

    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        private SilentConnection(ServerLevel level, ServerPlayer player, Connection network) {
            super(level.getServer(), network, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }
}
