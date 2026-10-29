package rotp.addons.harvest.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.ability.HarvestCommandAbility;
import rotp.addons.harvest.ability.HarvestCommandAbility.Command;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestMemberMode;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.addons.harvest.stand.HarvestStandData;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * 1.16 kept Be Closer and the swarm status on Harvest's user (051 LivingUtilCap :31-36), not on the summoned Harvest:
 * every Harvest took both from its owner on each tick (HarvestMainEntity.tick :242-249), an unsummon reset only the
 * status (HarvestStandType.unsummonStand :75-79), and nothing saved them or copied them to a respawned player.
 */
@GameTestHolder(HarvestAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestUserSwarmStateGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 125;

    private HarvestUserSwarmStateGameTests() {}

    @GameTest(template = "empty", batch = "harvest_be_closer_kept", timeoutTicks = 200)
    public static void beCloserOutlivesAnUnsummonAsIn116(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(f.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        f.runBeCloser();
                    }
                    finally {
                        f.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "harvest_standless_search", timeoutTicks = 200)
    public static void searchFiredWithNoHarvestOutSendsTheNextSwarmSearchingAsIn116(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(f.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        f.runStandlessSearch();
                    }
                    finally {
                        f.close();
                    }
                })
                .thenSucceed();
    }

    // 1.16 HarvestStandType.unsummon :65-68 set the swarm status to STAYING after StandEntity.retractStand :2211-2219,
    // which started no unsummon while the user's power held a command: Harvest stayed out, its attack or search ended
    // (HarvestTeleportGoal took the members back, HarvestFindAndCollectItemGoal stopped), a stay-with went on.
    @GameTest(template = "empty", batch = "harvest_refused_unsummon", timeoutTicks = 200)
    public static void unsummonRefusedForAHeldCommandStillResetsTheSwarmAsIn116(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(f.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        f.runRefusedUnsummon();
                    }
                    finally {
                        f.close();
                    }
                })
                .thenSucceed();
    }

    // 1.16's LivingUtilCap belonged to the player, not to the Stand: StandPower.clear :180-199 (a disc taken out, the
    // stand remover, /stand clear with fullStandClear :643-651, /stand give ... true) did not touch it, and forceUnsummon
    // (the status reset) ran only for a summoned Stand. Be Closer and a left status came back with the Stand; a new
    // player entity (respawn, End return) started without them.
    @GameTest(template = "empty", batch = "harvest_regranted", timeoutTicks = 200)
    public static void beCloserOutlivesTheStandsRemovalAsIn116(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(f.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        f.runRegranted();
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
        private final HarvestStandData data;
        private Zombie zombie;

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
            data = HarvestStandData.of(owner);
            helper.assertTrue(data != null, "Harvest's user has no Harvest data in its Stand power: "
                    + power.getCurTypeData());
        }

        private void runBeCloser() {
            Ability beCloser = power.getAbility(HarvestAddon.BE_CLOSER_NAME);
            String farSprite = beCloser.getSpriteName(power);
            HarvestStandEntity first = summon();
            helper.assertTrue(!data.isCloser() && !first.closeFormation(), "fixture: a new Harvest user starts close");

            press(HarvestAddon.BE_CLOSER_NAME);
            helper.assertTrue(data.isCloser() && first.closeFormation(),
                    "Be Closer did not bring the swarm closer: user " + data.isCloser() + ", Harvest "
                            + first.closeFormation());
            first.executeCommand(Command.SEARCH, owner);
            helper.assertTrue(first.commandMode() == HarvestMemberMode.SEARCH, "fixture: the swarm is not searching");

            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            helper.assertTrue(!power.isSummoned(), "fixture: Harvest is still out after its unsummon");
            helper.assertTrue(data.isCloser(),
                    "1.16 kept Be Closer on Harvest's user (LivingUtilCap) through an unsummon; it was lost");
            // the name (client only: StandSkinsLoader) follows the same flag
            helper.assertTrue(beCloser.getSpriteName(power).equals(farSprite + ".not"),
                    "1.16 showed Be Closer as on (BeCloser.getIconTexture) with Harvest not out: sprite "
                            + beCloser.getSpriteName(power));

            // LivingUtilCap.toNBT wrote nothing: a relog started far
            CompoundTag saved = power.serializeNBT(level.registryAccess());
            FakePlayer relogged = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "harvest-relog"));
            try {
                StandPower reloaded = PowerClass.STAND.attachGet(relogged);
                reloaded.deserializeNBT(level.registryAccess(), saved);
                HarvestStandData reloadedData = HarvestStandData.of(reloaded);
                helper.assertTrue(reloaded.getPowerType() == HarvestAddon.HARVEST_STAND.get()
                                && reloadedData != null && !reloadedData.isCloser(),
                        "1.16 saved no Be Closer: after a relog it was " + (reloadedData != null
                                ? reloadedData.isCloser() : null));
            }
            finally {
                relogged.discard();
            }

            HarvestStandEntity second = summon();
            second.tick();
            helper.assertTrue(second.closeFormation(),
                    "1.16's next Harvest took Be Closer from its user; the new swarm is far");
            helper.assertTrue(second.commandMode() == HarvestMemberMode.FOLLOW,
                    "1.16's unsummon reset the swarm status (HarvestStandType.unsummonStand): the new swarm is "
                            + second.commandMode());
            HarvestMemberEntity member = member(second);
            helper.assertTrue(member != null, "the new Harvest spawned no member");

            press(HarvestAddon.BE_CLOSER_NAME);
            helper.assertTrue(!data.isCloser() && !second.closeFormation()
                            && beCloser.getSpriteName(power).equals(farSprite),
                    "Be Closer pressed again did not send the swarm far: user " + data.isCloser() + ", Harvest "
                            + second.closeFormation() + ", sprite " + beCloser.getSpriteName(power));
            press(HarvestAddon.BE_CLOSER_NAME);
            helper.assertTrue(data.isCloser() && second.closeFormation(), "fixture: Be Closer did not switch on again");
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);

            // a new player entity (End return; death likewise) got a fresh LivingUtilCap
            FakePlayer respawned = new FakePlayer(level, owner.getGameProfile());
            try {
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, owner, false));
                StandPower clonedPower = StandPower.get(respawned);
                HarvestStandData clonedData = HarvestStandData.of(clonedPower);
                helper.assertTrue(clonedPower != null && clonedPower.getPowerType() == HarvestAddon.HARVEST_STAND.get()
                                && clonedData != null && !clonedData.isCloser(),
                        "1.16 did not copy Be Closer to a respawned player: it was " + (clonedData != null
                                ? clonedData.isCloser() : null));
            }
            finally {
                respawned.discard();
            }
            LOGGER.info("R770_HARVEST_BE_CLOSER_KEPT_PASS member={}", member.getUUID());
        }

        private void runStandlessSearch() {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(HarvestAddon.SEARCH_NAME);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the Search press with Harvest not out was refused");
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            EntityActionInstance search = userSlot().getAction();
            helper.assertTrue(press.isActionSuccess && search != null
                            && search.getPhase() == ActionPhase.BUTTON_CHARGE
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "fixture: the Search press did not summon Harvest and start its charge: " + search);
            HarvestStandEntity summoned = (HarvestStandEntity) power.getSummonedStandEntity();
            int firedTick = -1;
            for (int tick = 1; tick <= 40 && firedTick < 0; tick++) {
                if (tick == 5) {
                    HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
                    helper.assertTrue(!power.isSummoned(), "fixture: Harvest is still out after its unsummon");
                }
                userSlot().tick();
                if (!summoned.isRemoved()) {
                    summoned.tick();
                }
                if (search.isOver() || search.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    firedTick = tick;
                }
            }
            AbilityInput.keyRelease(KEY, owner);
            helper.assertTrue(firedTick == HarvestCommandAbility.SEARCH_CHARGE_TICKS && !power.isSummoned(),
                    "fixture: the held Search did not fire with no Harvest out: left its charge in tick " + firedTick);
            helper.assertTrue(data.hasSearchLeftStandless(),
                    "1.16's Search fired with no Harvest out still set its user's swarm status to FINDING_ITEM"
                            + " (Search.perform :47-56); nothing was left");
            userSlot().setAction(null, SyncType.NO_SYNC);

            HarvestStandEntity next = summon();
            next.tick();
            HarvestMemberEntity member = member(next);
            helper.assertTrue(next.commandMode() == HarvestMemberMode.SEARCH
                            && member != null && member.getMode() == HarvestMemberMode.SEARCH,
                    "1.16's next swarm took up the status the standless Search left and went searching: Harvest "
                            + next.commandMode() + ", member " + (member != null ? member.getMode() : null));
            helper.assertTrue(!data.hasSearchLeftStandless(), "the next swarm left the status on its user");

            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            HarvestStandEntity after = summon();
            after.tick();
            helper.assertTrue(after.commandMode() == HarvestMemberMode.FOLLOW,
                    "1.16's unsummon reset the swarm status: the swarm after it is " + after.commandMode());
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);

            // an unsummon before any swarm took the status up reset it as well (unsummonStand)
            data.markSearchLeftStandless();
            summon();
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            HarvestStandEntity last = summon();
            last.tick();
            helper.assertTrue(last.commandMode() == HarvestMemberMode.FOLLOW && !data.hasSearchLeftStandless(),
                    "1.16's unsummon reset a status no swarm had taken up: the swarm is " + last.commandMode());
            LOGGER.info("R770_HARVEST_STANDLESS_SEARCH_PASS search={}", firedTick);
        }

        private void runRefusedUnsummon() {
            HarvestStandEntity coordinator = summon();
            coordinator.tick();
            HarvestMemberEntity member = member(coordinator);
            helper.assertTrue(member != null, "Harvest spawned no member");
            press(HarvestAddon.BE_CLOSER_NAME);
            helper.assertTrue(data.isCloser() && coordinator.closeFormation(), "fixture: Be Closer did not switch on");
            zombie = EntityType.ZOMBIE.create(level);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.moveTo(ownerPos.getX() + 4.5D, ownerPos.getY(), ownerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(zombie);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(zombie.getX(), owner.getEyeY(), zombie.getZ()));

            coordinator.executeCommand(Command.STAY_WITH, owner);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.STAY_WITH,
                    "fixture: the swarm does not stay with the zombie: " + coordinator.commandMode());
            refusedUnsummon(coordinator);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.STAY_WITH
                            && coordinator.commandTarget().filter(zombie.getUUID()::equals).isPresent()
                            && member.getMode() == HarvestMemberMode.STAY_WITH,
                    "1.16's STAYING left a stay-with alone (its members followed their second owner): Harvest "
                            + coordinator.commandMode() + ", member " + member.getMode());
            endHold();

            coordinator.executeCommand(Command.SET_TARGET, owner);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.ATTACK,
                    "fixture: the swarm does not attack the zombie: " + coordinator.commandMode());
            refusedUnsummon(coordinator);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.FOLLOW
                            && coordinator.commandTarget().isEmpty()
                            && member.getMode() == HarvestMemberMode.FOLLOW,
                    "1.16's unsummon pressed during a held command still set the swarm status to STAYING"
                            + " (HarvestStandType.unsummon :65-68), which ended the attack: Harvest "
                            + coordinator.commandMode() + ", member " + member.getMode());
            endHold();

            coordinator.executeCommand(Command.SEARCH, owner);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.SEARCH, "fixture: the swarm is not searching");
            EntityActionInstance search = refusedUnsummon(coordinator);
            helper.assertTrue(coordinator.commandMode() == HarvestMemberMode.FOLLOW
                            && member.getMode() == HarvestMemberMode.FOLLOW,
                    "1.16's unsummon pressed during a held command still set the swarm status to STAYING"
                            + " (HarvestStandType.unsummon :65-68), which ended the search: Harvest "
                            + coordinator.commandMode() + ", member " + member.getMode());
            helper.assertTrue(data.isCloser() && coordinator.closeFormation(),
                    "1.16's unsummon reset only the status and kept Be Closer: user " + data.isCloser() + ", Harvest "
                            + coordinator.closeFormation());
            int firedTick = -1;
            for (int tick = 1; tick <= 40 && firedTick < 0; tick++) {
                userSlot().tick();
                if (search.isOver() || search.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    firedTick = tick;
                }
            }
            helper.assertTrue(power.isSummoned() && coordinator.commandMode() == HarvestMemberMode.SEARCH,
                    "1.16's held Search went off after the refused unsummon and sent the swarm searching again"
                            + " (FINDING_ITEM): summoned " + power.isSummoned() + ", Harvest " + coordinator.commandMode()
                            + ", left its charge in tick " + firedTick);
            endHold();
            LOGGER.info("R770_HARVEST_REFUSED_UNSUMMON_PASS search={}", firedTick);
        }

        private void runRegranted() {
            summon();
            press(HarvestAddon.BE_CLOSER_NAME);
            helper.assertTrue(data.isCloser(), "fixture: Be Closer did not switch on");
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            ResourceLocation harvestId = HarvestAddon.HARVEST_STAND.getId();

            // a disc taken out and put back (StandDiscItem: extract, insert)
            StandPowerTransitions.Result taken = StandPowerTransitions.extract(power, harvestId);
            helper.assertTrue(taken.applied() && !power.hasPower() && taken.previous().isPresent(),
                    "fixture: Harvest was not taken out: " + taken.status());
            helper.assertTrue(StandPowerTransitions.insert(power, taken.previous().get()).applied(),
                    "fixture: Harvest was not put back");
            regrantedClose("taken out and put back");

            // /stand remove (1.16 /stand clear: clear and fullStandClear) and /stand give
            StandPowerTransitions.Result cleared = StandPowerTransitions.fullReset(power,
                    new StandPowerTransitions.TransitionContext(HarvestAddon.id("gametest_full_clear"), null));
            helper.assertTrue(cleared.applied() && !power.hasPower(), "fixture: no full clear: " + cleared.status());
            power.setStand(HarvestAddon.HARVEST_STAND.get());
            regrantedClose("fully cleared and given again");

            // 1.16 StandPower.clear ran forceUnsummon (unsummonStand's STAYING) only for a summoned Stand, so the status
            // a standless Search left came back with the Stand
            HarvestStandData left = HarvestStandData.of(owner);
            left.markSearchLeftStandless();
            taken = StandPowerTransitions.extract(power, harvestId);
            helper.assertTrue(taken.applied()
                            && StandPowerTransitions.insert(power, taken.previous().get()).applied(),
                    "fixture: Harvest was not taken out and put back");
            HarvestStandData back = HarvestStandData.of(owner);
            helper.assertTrue(back != null && back.hasSearchLeftStandless(),
                    "1.16 kept the status a standless Search left on its user while Harvest was taken out and put back"
                            + " (StandPower.clear unsummoned only a summoned Stand): it was reset");
            HarvestStandEntity searching = summon();
            searching.tick();
            helper.assertTrue(searching.commandMode() == HarvestMemberMode.SEARCH,
                    "1.16's next swarm took up the status left before the Stand was taken out: " + searching.commandMode());
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);

            // a new player entity got a fresh LivingUtilCap, also while its user had another Stand (whose clone keeps
            // the data of the Stands it had before)
            StandType other = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            helper.assertTrue(other != null && StandPowerTransitions.replace(power, harvestId,
                            new StandInstance(other)).applied(), "fixture: Harvest was not replaced");
            FakePlayer respawned = new FakePlayer(level, owner.getGameProfile());
            try {
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, owner, false));
                StandPower clonedPower = PowerClass.STAND.attachGet(respawned);
                helper.assertTrue(clonedPower.getPowerType() == other && StandPowerTransitions.replace(clonedPower,
                                other.getId(), new StandInstance(HarvestAddon.HARVEST_STAND.get())).applied(),
                        "fixture: the new player entity did not get Harvest back: " + clonedPower.getPowerType());
                HarvestStandData clonedData = HarvestStandData.of(clonedPower);
                helper.assertTrue(clonedData != null && !clonedData.isCloser(),
                        "1.16 did not copy Be Closer to a new player entity, with or without the Stand at the time: it"
                                + " was " + (clonedData != null ? clonedData.isCloser() : null));
            }
            finally {
                respawned.discard();
            }
            helper.assertTrue(StandPowerTransitions.replace(power, other.getId(),
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).applied()
                            && HarvestStandData.of(owner).isCloser(),
                    "1.16 kept Be Closer while its user had another Stand and got Harvest back");
            LOGGER.info("R770_HARVEST_REGRANTED_PASS closer={}", HarvestStandData.of(owner).isCloser());
        }

        private void regrantedClose(String how) {
            HarvestStandData regranted = HarvestStandData.of(owner);
            helper.assertTrue(power.getPowerType() == HarvestAddon.HARVEST_STAND.get()
                            && regranted != null && regranted.isCloser(),
                    "1.16 kept Be Closer on its user (LivingUtilCap) while Harvest was " + how + ": it was "
                            + (regranted != null ? regranted.isCloser() : null));
            HarvestStandEntity coordinator = summon();
            coordinator.tick();
            helper.assertTrue(coordinator.closeFormation(),
                    "1.16's Harvest " + how + " took Be Closer from its user; the new swarm is far");
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
        }

        // Holds Search with Harvest out and presses the summon key, which 1.16 refused to unsummon with while the
        // user's power held it
        private EntityActionInstance refusedUnsummon(HarvestStandEntity coordinator) {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(HarvestAddon.SEARCH_NAME);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the Search press was refused");
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            EntityActionInstance held = userSlot().getAction();
            helper.assertTrue(press.isActionSuccess && held != null && held.getPhase() == ActionPhase.BUTTON_CHARGE
                            && AbilityInput.getHeldAction(owner, PowerClass.STAND) == held,
                    "fixture: the user's power does not hold the Search: " + held);
            HarvestAddon.HARVEST_STAND.get().onUserSummonCommand(owner, power);
            helper.assertTrue(power.getSummonedStandEntity() == coordinator && !coordinator.isRemoved()
                            && coordinator.getCurStandAction() == null
                            && !held.isOver() && held.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "fixture: 1.16 started no unsummon while the user's power held a command: Stand "
                            + power.getSummonedStandEntity() + ", its action " + coordinator.getCurStandAction()
                            + ", the held Search " + held.getPhase());
            return held;
        }

        private void endHold() {
            AbilityInput.keyRelease(KEY, owner);
            userSlot().setAction(null, SyncType.NO_SYNC);
        }

        private HarvestStandEntity summon() {
            helper.assertTrue(HarvestAddon.HARVEST_STAND.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "Production Harvest summon failed");
            return (HarvestStandEntity) power.getSummonedStandEntity();
        }

        private HarvestMemberEntity member(HarvestStandEntity coordinator) {
            List<HarvestMemberEntity> members = level.getEntitiesOfClass(HarvestMemberEntity.class,
                    new AABB(ownerPos).inflate(32.0D),
                    m -> m.isAlive() && m.isOwnedBy(owner.getUUID()) && m.isBoundTo(coordinator.getUUID()));
            return members.isEmpty() ? null : members.get(0);
        }

        // A click of the command with Harvest out, run until the user's power has performed it
        private void press(String id) {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(id);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, owner, InputMethod.CLICK),
                    "the " + id + " press was refused: "
                            + (selected != null ? selected.conditionCheck.getWarning() : null));
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.CLICK, 0, press,
                    selected.ability.getAbilityId());
            AbilityInput.keyRelease(KEY, owner);
            helper.assertTrue(press.isActionSuccess, "the " + id + " press did not start");
            for (int tick = 0; tick < 5; tick++) {
                EntityActionInstance action = userSlot().getAction();
                if (action == null || action.isOver()) {
                    break;
                }
                userSlot().tick();
            }
            EntityActionInstance action = userSlot().getAction();
            helper.assertTrue(action == null || action.isOver(), "the " + id + " press is still running: " + action);
            userSlot().setAction(null, SyncType.NO_SYNC);
        }

        private LivingComponentAction userSlot() {
            return LivingComponentAction.getComponent(owner, PowerClass.STAND);
        }

        private void close() {
            try {
                if (zombie != null) {
                    zombie.discard();
                }
                AbilityInput.keyRelease(KEY, owner);
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
