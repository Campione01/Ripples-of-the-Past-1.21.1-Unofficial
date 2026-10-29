package rotp.addons.harvest.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.ability.HarvestCommandAbility;
import rotp.addons.harvest.ability.HarvestCommandAbility.HarvestCommandAction;
import rotp.addons.harvest.entity.HarvestMemberEntity;
import rotp.addons.harvest.entity.HarvestMemberMode;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
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

/**
 * 1.16 PowerBaseImpl.tickHeldAction checked the crosshair of a held Give a Potion or Remove a Potion on every tick
 * (ENTITY requirement, an own Harvest, holdToFire(25, false)): the charge ended silently once the view left the
 * Harvest, nothing fired, so neither could be charged before aiming.
 */
@GameTestHolder(HarvestAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestPotionHoldGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 124;

    private HarvestPotionHoldGameTests() {}

    @GameTest(template = "empty", batch = "harvest_potion_hold", timeoutTicks = 200)
    public static void potionChargesStopWhenTheViewLeavesTheHarvest(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(scene::summon)
                .thenWaitUntil(() -> helper.assertTrue(scene.findMember() != null,
                        "Harvest spawned no member"))
                .thenExecute(() -> {
                    try {
                        scene.run();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    // 1.16 InitStands :50, :54: Search holdToFire(25, false) and Carry Up holdToFire(2, false), StandActions with
    // autoSummonStand(). The press summoned Harvest, the user's power counted the hold from the press
    // (PowerBaseImpl.tickHeldAction) and only a Stand task waited out a new Stand's summon lock (StandEntity.tick
    // :1270-1281): Search went off in the 25th tick from the press, Carry Up carried in the 2nd.
    @GameTest(template = "empty", batch = "harvest_summon_lock", timeoutTicks = 200)
    public static void commandsPressedWithHarvestNotOutFireOnTheirDonorTick(GameTestHelper helper) {
        BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
        ChunkPos chunk = new ChunkPos(template);
        BlockPos ownerPos = new BlockPos(chunk.getMinBlockX() + 4, template.getY() + 8, chunk.getMinBlockZ() + 8);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    int searchTick = pressUnsummoned(helper, ownerPos, HarvestAddon.SEARCH_NAME, 40,
                            coordinator -> coordinator.commandMode() == HarvestMemberMode.SEARCH, false);
                    helper.assertTrue(searchTick == HarvestCommandAbility.SEARCH_CHARGE_TICKS,
                            "1.16 sent the swarm searching in the 25th tick from a Search pressed with Harvest not"
                                    + " out, which no summon lock held back: sent in tick " + searchTick);
                    int carryTick = pressUnsummoned(helper, ownerPos, HarvestAddon.CARRY_UP_NAME, 20,
                            HarvestStandEntity::isCarryUpActive, true);
                    helper.assertTrue(carryTick == HarvestCommandAbility.CARRY_UP_CHARGE_TICKS,
                            "1.16 carried its user in the 2nd tick from a Carry Up pressed with Harvest not out,"
                                    + " which no summon lock held back: carried in tick " + carryTick);
                    LOGGER.info("R770_HARVEST_SUMMON_LOCK_PASS search={} carry_up={}", searchTick, carryTick);
                })
                .thenSucceed();
    }

    // 1.16 PowerBaseImpl.tickHeldAction :357-389 ended a held action only when its power could not be used or its
    // requirements failed, so a held command outlived its Harvest (an unsummon already under way at the press, a
    // dimension change). Give a Potion and Remove a Potion needed the summoned Harvest under the crosshair
    // (GiveAPotion.checkRangeAndTarget :45-50, RemoveAPotion :33-38) and ended silently; Search only needed a player
    // user (Search.checkSpecificConditions :40-45) and went off in its 25th tick with no swarm left to send.
    @GameTest(template = "empty", batch = "harvest_unsummon_hold", timeoutTicks = 200)
    public static void heldCommandsOutliveTheirHarvestAsIn116(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(scene::summon)
                .thenWaitUntil(() -> helper.assertTrue(scene.findMember() != null,
                        "Harvest spawned no member"))
                .thenExecute(() -> {
                    try {
                        scene.runUnsummon();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    // Presses the command with Harvest not out and ticks in the level's order (the user's Stand-power slot, where its
    // power holds the command, then the summoned coordinator) until the command has fired: the tick it fired in, the
    // press's own tick the first.
    private static int pressUnsummoned(GameTestHelper helper, BlockPos pos, String id, int maxTicks,
            Predicate<HarvestStandEntity> fired, boolean releaseAfterFiring) {
        RecordingPlayer owner = makePlayer(helper.getLevel(), pos);
        owner.setNoGravity(true);
        owner.setGameMode(GameType.SURVIVAL);
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Harvest");
            helper.assertTrue(!power.isSummoned(), "fixture: Harvest is out before the " + id + " press");
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(id);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null, id + " is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the " + id + " press with Harvest not out was refused: "
                            + selected.conditionCheck.getWarning());
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            EntityActionInstance held = LivingComponentAction.getCurEntityAction(owner, PowerClass.STAND);
            helper.assertTrue(press.isActionSuccess
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity summoned
                            && summoned.isAddedToLevel()
                            && held instanceof HarvestCommandAction
                            && held.getPhase() == ActionPhase.BUTTON_CHARGE
                            && held.getPerformer() == summoned
                            && summoned.getCurStandAction() == null,
                    "1.16 summoned Harvest at the " + id + " press and its user's power held the charge, performed"
                            + " by the summoned Harvest: Stand " + power.getSummonedStandEntity() + ", the user's"
                            + " Stand-power action " + held + " performed by "
                            + (held != null ? held.getPerformer() : null) + ", the Stand's own action "
                            + (power.getSummonedStandEntity() instanceof HarvestStandEntity stand
                                    ? stand.getCurStandAction() : null));
            HarvestStandEntity coordinator = (HarvestStandEntity) power.getSummonedStandEntity();
            int lockAtPress = coordinator.summonLockTicks;
            int fadeAtPress = coordinator.gradualSummonWeaknessTicks;
            int firedTick = -1;
            for (int tick = 1; tick <= maxTicks && firedTick < 0; tick++) {
                LivingComponentAction.getComponent(owner, PowerClass.STAND).tick();
                coordinator.tick();
                if (tick == 1) {
                    // the lock counts through a stun in 1.16, the fade-in's other counter does not
                    helper.assertTrue(lockAtPress > 1 && fadeAtPress == 0
                                    && coordinator.summonLockTicks == lockAtPress - 1
                                    && coordinator.gradualSummonWeaknessTicks == 0,
                            "1.16 kept the new Harvest's summon lock (its fade-in) counting in place under the "
                                    + id + " press: at the press lock " + lockAtPress + ", fade " + fadeAtPress
                                    + "; after tick 1 lock " + coordinator.summonLockTicks + ", fade "
                                    + coordinator.gradualSummonWeaknessTicks);
                }
                if (fired.test(coordinator)) {
                    firedTick = tick;
                }
            }
            if (releaseAfterFiring && firedTick > 0) {
                AbilityInput.keyRelease(KEY, owner);
                LivingComponentAction.getComponent(owner, PowerClass.STAND).tick();
                coordinator.tick();
                helper.assertTrue(fired.test(coordinator),
                        "1.16 kept what " + id + " did once it fired when the key was let go a tick later");
            }
            return firedTick;
        }
        finally {
            AbilityInput.keyRelease(KEY, owner);
            if (power.isSummoned()) {
                HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            }
            owner.discard();
        }
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos ownerPos;
        private final RecordingPlayer owner;
        private StandPower power;
        private HarvestStandEntity coordinator;
        private HarvestMemberEntity member;
        private boolean closed;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            ownerPos = new BlockPos(chunk.getMinBlockX() + 4, template.getY() + 8, chunk.getMinBlockZ() + 8);
            owner = makePlayer(level, ownerPos);
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
        }

        private void summon() {
            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Harvest");
            helper.assertTrue(HarvestAddon.HARVEST_STAND.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "Production Harvest summon failed");
            coordinator = (HarvestStandEntity) power.getSummonedStandEntity();
        }

        private HarvestMemberEntity findMember() {
            if (coordinator == null) {
                return null;
            }
            List<HarvestMemberEntity> members = level.getEntitiesOfClass(HarvestMemberEntity.class,
                    new AABB(ownerPos).inflate(32.0D),
                    m -> m.isAlive() && m.isOwnedBy(owner.getUUID()) && m.isBoundTo(coordinator.getUUID()));
            return members.isEmpty() ? null : members.get(0);
        }

        private void run() {
            member = findMember();
            member.setNoAi(true);
            member.setNoGravity(true);
            member.moveTo(owner.getX() + 3.0D, owner.getY() + 1.0D, owner.getZ(), 0.0F, 0.0F);
            ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            owner.setItemInHand(InteractionHand.MAIN_HAND, potion);
            String give = HarvestAddon.GIVE_POTION_NAME;
            String remove = HarvestAddon.REMOVE_POTION_NAME;

            pressAtMember(give);
            tickHold();
            helper.assertTrue(running(), "fixture: the Give a Potion charge must go on while the view stays on the Harvest");
            lookAway();
            tickHold();
            helper.assertTrue(!running(),
                    "1.16 stopped the held Give a Potion once the crosshair left the Harvest; it still runs: "
                            + userAction());
            helper.assertTrue(member.potionPayloadCount() == 0
                            && owner.getMainHandItem().is(Items.POTION)
                            && !owner.getMainHandItem().has(DataComponents.CUSTOM_DATA),
                    "1.16 fired nothing after the stop: payload " + member.potionPayloadCount() + ", hand "
                            + owner.getMainHandItem());
            helper.assertTrue(owner.messages.isEmpty(),
                    "1.16 stopped the charge silently: " + owner.messages);

            // GiveAPotion.checkConditions (a potion in hand) ran on every held tick too
            pressAtMember(give);
            tickHold();
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            tickHold();
            helper.assertTrue(!running(),
                    "1.16 stopped the held Give a Potion once the potion left the hand; it still runs: "
                            + userAction());
            helper.assertTrue(member.potionPayloadCount() == 0 && owner.messages.isEmpty(),
                    "1.16 gave nothing and said nothing: payload " + member.potionPayloadCount() + ", messages "
                            + owner.messages);
            owner.setItemInHand(InteractionHand.MAIN_HAND, potion);

            pressAtMember(give);
            tickHold();
            fire();
            helper.assertTrue(member.potionPayloadCount() == 1,
                    "a charge whose view stayed on the Harvest gives the potion; payload "
                            + member.potionPayloadCount());

            pressAtMember(remove);
            tickHold();
            helper.assertTrue(running(), "fixture: the Remove a Potion charge must go on while the view stays on the Harvest");
            lookAway();
            tickHold();
            helper.assertTrue(!running(),
                    "1.16 stopped the held Remove a Potion once the crosshair left the Harvest; it still runs: "
                            + userAction());
            helper.assertTrue(member.potionPayloadCount() == 1,
                    "1.16 removed nothing after the stop; payload " + member.potionPayloadCount());
            helper.assertTrue(owner.messages.isEmpty(),
                    "1.16 stopped the charge silently: " + owner.messages);

            pressAtMember(remove);
            tickHold();
            fire();
            helper.assertTrue(member.potionPayloadCount() == 0,
                    "a charge whose view stayed on the Harvest removes the potion; payload "
                            + member.potionPayloadCount());

            // 1.16 RemoveAPotion, like every Harvest command, was a StandAction: it left the Stand no task once it
            // fired, so the next press started at once
            EntityActionInstance fired = userAction();
            helper.assertTrue(fired == null || fired.isOver(),
                    "1.16 left no Harvest task once a command fired; the port still runs " + fired + " in "
                            + (fired != null ? fired.getPhase() : null));
            // the action component's next tick drops the ended action
            userSlot().setAction(null, SyncType.NO_SYNC);
            pressAtMember(give, false);

            // 1.16 GiveAPotion was a StandAction (holdToFire(25, false)): the user's power gave the potion in the
            // tick the hold fired, the 25th from the press (the press's own tick the first)
            int giveTick = -1;
            for (int tick = 1; tick <= 40 && giveTick < 0 && running(); tick++) {
                userSlot().tick();
                if (member.potionPayloadCount() == 1) {
                    giveTick = tick;
                }
            }
            helper.assertTrue(giveTick == HarvestCommandAbility.POTION_TRANSFER_CHARGE_TICKS,
                    "1.16 gave the potion in the 25th tick from the Give a Potion press, the tick its hold fired: given"
                            + " in tick " + giveTick);
            LOGGER.info("R770_HARVEST_POTION_HOLD_PASS member={}", member.getUUID());
        }

        private void runUnsummon() {
            member = findMember();
            member.setNoAi(true);
            member.setNoGravity(true);
            member.moveTo(owner.getX() + 3.0D, owner.getY() + 1.0D, owner.getZ(), 0.0F, 0.0F);
            ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            owner.setItemInHand(InteractionHand.MAIN_HAND, potion);

            pressAtMember(HarvestAddon.GIVE_POTION_NAME);
            tickHold();
            helper.assertTrue(running(), "fixture: the Give a Potion charge must go on while Harvest is out");
            HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
            helper.assertTrue(!power.isSummoned(), "fixture: Harvest is still out after its unsummon");
            tickHold();
            helper.assertTrue(!running(),
                    "1.16 ended the held Give a Potion once its Harvest was gone; it still runs: " + userAction());
            helper.assertTrue(member.potionPayloadCount() == 0
                            && owner.getMainHandItem().is(Items.POTION)
                            && !owner.getMainHandItem().has(DataComponents.CUSTOM_DATA)
                            && owner.messages.isEmpty(),
                    "1.16 gave nothing and said nothing: payload " + member.potionPayloadCount() + ", hand "
                            + owner.getMainHandItem() + ", messages " + owner.messages);

            AbilityInput.keyRelease(KEY, owner);
            userSlot().setAction(null, SyncType.NO_SYNC);
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(HarvestAddon.SEARCH_NAME);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the Search press with Harvest not out was refused");
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            EntityActionInstance search = userAction();
            helper.assertTrue(press.isActionSuccess && search instanceof HarvestCommandAction
                            && search.getPhase() == ActionPhase.BUTTON_CHARGE
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "fixture: the Search press did not summon Harvest and start its charge: " + search);
            HarvestStandEntity summoned = (HarvestStandEntity) power.getSummonedStandEntity();
            HarvestCommandAbility searchAbility = (HarvestCommandAbility) selected.ability;
            int unsummonTick = 5;
            int leftChargeTick = -1;
            boolean recheckPassedBeforeFiring = false;
            for (int tick = 1; tick <= 40 && leftChargeTick < 0; tick++) {
                if (tick == unsummonTick) {
                    HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
                    helper.assertTrue(!power.isSummoned() && search.getPerformer() == owner,
                            "fixture: Harvest is still out after its unsummon, performer " + search.getPerformer());
                }
                if (tick == HarvestCommandAbility.SEARCH_CHARGE_TICKS - 1) {
                    recheckPassedBeforeFiring = searchAbility.checkHeldActionConditions(search, power).isPositive();
                }
                userSlot().tick();
                if (!summoned.isRemoved()) {
                    summoned.tick();
                }
                if (search.isOver() || search.getPhase() != ActionPhase.BUTTON_CHARGE) {
                    leftChargeTick = tick;
                }
            }
            helper.assertTrue(leftChargeTick == HarvestCommandAbility.SEARCH_CHARGE_TICKS && recheckPassedBeforeFiring,
                    "1.16 kept the held Search after its Harvest left in tick " + unsummonTick + " and fired it in the"
                            + " 25th tick from the press: it left its charge in tick " + leftChargeTick
                            + ", the held recheck passed before the firing tick " + recheckPassedBeforeFiring);
            helper.assertTrue(!power.isSummoned() && owner.messages.isEmpty(),
                    "1.16's Search fired without its Harvest summoned again and without a message: summoned "
                            + power.isSummoned() + ", messages " + owner.messages);
            LOGGER.info("R770_HARVEST_UNSUMMON_HOLD_PASS search={}", leftChargeTick);
        }

        private void pressAtMember(String id) {
            pressAtMember(id, true);
        }

        private void pressAtMember(String id, boolean clearBefore) {
            AbilityInput.keyRelease(KEY, owner);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, member.getBoundingBox().getCenter());
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(id);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null, id + " is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the press at the Harvest was refused: " + id + " " + selected.conditionCheck.getWarning());
            EntityActionInstance before = userAction();
            if (clearBefore && before != null) {
                // a charge the test left running ends and the next server tick clears it
                before.forceStop();
                userSlot().setAction(null, SyncType.NO_SYNC);
            }
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            helper.assertTrue(press.isActionSuccess
                            && userAction() instanceof HarvestCommandAction
                            && userAction().getPhase() == ActionPhase.BUTTON_CHARGE,
                    "the press did not start the charge of " + id + ": action " + userAction());
            // 1.16 GiveAPotion and RemoveAPotion were StandActions: the user's power held them, the summoned Harvest
            // performed them (StandAction.getPerformer), and its Stand was given no task
            helper.assertTrue(userAction().getPerformer() == coordinator && coordinator.getCurStandAction() == null,
                    "1.16's user power held the " + id + " charge, performed by Harvest, with no Stand task: performer "
                            + userAction().getPerformer() + ", the Stand's own action "
                            + coordinator.getCurStandAction());
        }

        private void lookAway() {
            owner.setXRot(-90.0F);
        }

        // One server tick of the running action, with its key still down
        private void tickHold() {
            if (running()) {
                userAction()._tickAction();
            }
        }

        // The charge ending with the key still down: the perform starts on the next tick
        private void fire() {
            EntityActionInstance action = userAction();
            helper.assertTrue(action != null && !action.isOver(), "fixture: no charge to fire");
            action.setPhaseStart(ActionPhase.PERFORM);
            action._tickAction();
        }

        private boolean running() {
            EntityActionInstance action = userAction();
            return action != null && !action.isOver();
        }

        // where a press of a Harvest command puts it, the user's Stand-power slot (1.16's user power held it)
        private LivingComponentAction userSlot() {
            return LivingComponentAction.getComponent(owner, PowerClass.STAND);
        }

        private EntityActionInstance userAction() {
            return userSlot().getAction();
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                AbilityInput.keyRelease(KEY, owner);
                if (power != null && power.isSummoned()) {
                    HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
                }
            }
            finally {
                owner.discard();
            }
        }
    }

    private static RecordingPlayer makePlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "harvest-potion");
        RecordingPlayer player = new RecordingPlayer(server, level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentGameListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(pos));
        level.addNewPlayer(player);
        return player;
    }

    // Keeps the action-bar messages the silent connection drops.
    private static final class RecordingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message.getString());
        }
    }

    private static final class SilentGameListener extends ServerGamePacketListenerImpl {
        private SilentGameListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
