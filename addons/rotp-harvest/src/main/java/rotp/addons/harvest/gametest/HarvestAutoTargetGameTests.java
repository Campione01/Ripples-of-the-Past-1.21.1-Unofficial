package rotp.addons.harvest.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 chose Harvest's automatic targets with vanilla TargetGoals on the member mob (051 HarvestMainEntity :204-207:
 * HarvestOwnerHurtTargetGoal, HarvestOwnerHurtByTargetGoal, NearestAttackableTargetGoal). They used
 * EntityPredicate.DEFAULT and dropped a player in Creative, so a Creative attacker and an invulnerable monster were
 * never chased. The commanded SET_TARGET set the target directly (SetTarget :25-37) and is not part of this rule.
 */
@GameTestHolder(HarvestAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestAutoTargetGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HarvestAutoTargetGameTests() {}

    @GameTest(template = "empty", batch = "harvest_auto_target", timeoutTicks = 200)
    public static void automaticTargetsRefuseInvulnerableOnesAsIn116(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(f.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        f.run();
                    }
                    finally {
                        f.close();
                    }
                })
                .thenSucceed();
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos ownerPos;
        private final ServerPlayer owner;
        private final StandPower power;
        private final List<LivingEntity> spawned = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            ownerPos = new BlockPos(chunk.getMinBlockX() + 4, template.getY() + 8, chunk.getMinBlockZ() + 8);
            owner = HarvestWallsGameTests.makeServerPlayer(level, ownerPos);
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Harvest");
        }

        private void run() {
            helper.assertTrue(HarvestAddon.HARVEST_STAND.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "Production Harvest summon failed");
            HarvestStandEntity coordinator = (HarvestStandEntity) power.getSummonedStandEntity();
            coordinator.tick();

            ServerPlayer creative = player(GameType.CREATIVE, 3.5D);
            retaliate(coordinator, creative);
            helper.assertTrue(coordinator.resolveAutomaticTarget(owner) == null,
                    "1.16's owner-hurt-by goal dropped a Creative attacker (EntityPredicate.DEFAULT, abilities"
                            + ".invulnerable); the swarm chases " + coordinator.resolveAutomaticTarget(owner));

            Zombie invulnerableAttacker = zombie(-3.5D);
            invulnerableAttacker.setInvulnerable(true);
            retaliate(coordinator, invulnerableAttacker);
            helper.assertTrue(coordinator.resolveAutomaticTarget(owner) == null,
                    "1.16's owner-hurt-by goal refused an invulnerable attacker; the swarm chases "
                            + coordinator.resolveAutomaticTarget(owner));

            Zombie invulnerableNear = zombie(2.5D);
            invulnerableNear.setInvulnerable(true);
            Zombie plainFar = zombie(7.5D);
            scan(coordinator);
            helper.assertTrue(coordinator.resolveAutomaticTarget(owner) == plainFar,
                    "1.16's NearestAttackableTargetGoal skipped the nearer invulnerable monster and took the"
                            + " plain one; the swarm scan picked " + coordinator.resolveAutomaticTarget(owner));

            ServerPlayer survival = player(GameType.SURVIVAL, -6.5D);
            retaliate(coordinator, survival);
            helper.assertTrue(coordinator.resolveAutomaticTarget(owner) == survival,
                    "1.16's owner-hurt-by goal chased a Survival attacker; the swarm chases "
                            + coordinator.resolveAutomaticTarget(owner));

            Zombie plainAttacker = zombie(-9.5D);
            retaliate(coordinator, plainAttacker);
            helper.assertTrue(coordinator.resolveAutomaticTarget(owner) == plainAttacker,
                    "1.16's owner-hurt-by goal chased a plain monster; the swarm chases "
                            + coordinator.resolveAutomaticTarget(owner));
            LOGGER.info("R770_HARVEST_AUTO_TARGET_PASS creative={} survival={}", creative.getUUID(),
                    survival.getUUID());
        }

        // The owner is hit by the attacker; the next coordinator tick reads it as a new hurt-by timestamp
        private void retaliate(HarvestStandEntity coordinator, LivingEntity attacker) {
            owner.tickCount += 100;
            owner.setLastHurtByMob(attacker);
            tick(coordinator);
        }

        private void scan(HarvestStandEntity coordinator) {
            tick(coordinator);
        }

        // The scan runs when the coordinator's tick count is a multiple of AUTO_TARGET_SCAN_INTERVAL_TICKS
        private void tick(HarvestStandEntity coordinator) {
            coordinator.tickCount = 0;
            coordinator.tick();
        }

        private Zombie zombie(double dx) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.moveTo(ownerPos.getX() + 0.5D + dx, ownerPos.getY(), ownerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(zombie);
            spawned.add(zombie);
            return zombie;
        }

        private ServerPlayer player(GameType mode, double dx) {
            ServerPlayer player = HarvestWallsGameTests.makeServerPlayer(level,
                    ownerPos.offset((int) Math.floor(dx), 0, 0));
            player.setNoGravity(true);
            player.setGameMode(mode);
            spawned.add(player);
            return player;
        }

        private void close() {
            try {
                for (LivingEntity entity : spawned) {
                    entity.discard();
                }
                if (power.isSummoned()) {
                    HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
                }
            }
            finally {
                owner.discard();
            }
        }
    }
}
