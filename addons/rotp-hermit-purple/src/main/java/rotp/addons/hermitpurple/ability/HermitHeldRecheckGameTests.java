package rotp.addons.hermitpurple.ability;

import java.lang.reflect.Field;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import rotp.core.init.power.ModPlayerPowers;
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
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 PowerBaseImpl.tickHeldAction ran checkRequirements again on every tick of a held action and stopped the hold,
 * with the result's message, once it failed. HamonBreath.checkSpecificConditions asked for a Hamon user with full air.
 */
@GameTestHolder("rotp_hp_lash")
@PrefixGameTestTemplate(false)
public final class HermitHeldRecheckGameTests {
    private static final short KEY = 92;
    private static final int HEIGHT = 92;
    private static final short OTHER_KEY = 93;
    private static final int HOLD_TYPE_HEIGHT = 102;
    private static final int PHASES_HEIGHT = 112;
    private static final int PRESSED_AGAIN_HEIGHT = 122;
    private static final int CAMERA_HEIGHT = 132;
    private static final String BREATH_KEY = "jojo.message.action_condition.hamon_breath";

    private HermitHeldRecheckGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_held_breath")
    public static void heldBreathEndsOnceTheAirRunsOutAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        int y = origin.getY() + HEIGHT;
        helper.assertTrue(level.getBlockStates(new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 4, chunkZ + 15))
                        .allMatch(state -> state.isAir() && state.getFluidState().isEmpty()),
                "The breath fixture needs an empty, dry space");
        GameProfile profile = new GameProfile(UUID.randomUUID(), "HPHeldBreath");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(KEY, player);
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            player.connection.disconnect(Component.literal("Hermit Purple held GameTest complete"));
            channel.finishAndReleaseAll();
        };
        HermitPurpleStandEntity stand;
        Ability breath;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(chunkX + 7.5D, y, chunkZ + 3.5D, 0.0F, 0.0F);
            PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "The summon produced no Hermit controller");
            breath = power.getAbility(HermitPurpleAddon.HP_BREATH_NAME);
            helper.assertTrue(breath instanceof HermitBreathAbility, "Hermit Purple has no hp_breath");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        EntityActionInstance[] held = {null};
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> guarded(close, () -> {
                    player.setAirSupply(player.getMaxAirSupply());
                    helper.assertTrue(breath.checkConditions(power).isPositive(),
                            "Setup: hp_breath was refused with full air");
                    BufferingState buffering = BufferingState.clickOnly();
                    AbilityInput.keyPress(KEY, breath, player, null, InputMethod.HOLD, 0.0F, buffering,
                            breath.getAbilityId());
                    held[0] = stand.getCurStandAction();
                    helper.assertTrue(buffering.isActionSuccess && held[0] != null && held[0].ability == breath,
                            "Setup: the held hp_breath did not start");
                }))
                .thenIdle(3)
                .thenExecute(() -> guarded(close, () -> {
                    helper.assertTrue(stand.getCurStandAction() == held[0]
                                    && held[0].getPhase() == ActionPhase.PERFORM,
                            "Setup: hp_breath is not held with full air: " + held[0].getPhase());
                    channel.flushOutbound();
                    channel.outboundMessages().clear();
                    player.setAirSupply(0);
                }))
                .thenIdle(2)
                .thenExecute(() -> guarded(close, () -> {
                    helper.assertTrue(held[0].isOver() || held[0].getPhase() != ActionPhase.PERFORM,
                            "1.16 ended the held breath once the air ran out; it is still " + held[0].getPhase());
                    channel.flushOutbound();
                    boolean message = false;
                    for (Object sent : channel.outboundMessages()) {
                        if (sent instanceof ClientboundSystemChatPacket chat && chat.overlay()
                                && chat.content().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals(BREATH_KEY)) {
                            message = true;
                        }
                    }
                    helper.assertTrue(message, "The stopped breath sent no breath message");
                    LogUtils.getLogger().info("R770_HP_HELD_PASS {}", "breath");
                    close.run();
                }))
                .thenSucceed();
    }

    /*
     * 1.16 hp_block (HPThorns), hp_vine (HPGrapple) and hp_breath (HamonBreath) were holdType Stand actions:
     * PowerBaseImpl let every other action of the power through them and ended the hold once that action went off
     * (onClickAction stopHeldAction(false), setHeldAction). Every Hermit Purple ability is performed by its Stand, so a
     * vine attack still replaces each hold at once.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_held_hold_type")
    public static void hermitHoldsAreHoldTypeStandActionsAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        int y = origin.getY() + HOLD_TYPE_HEIGHT;
        helper.assertTrue(level.getBlockStates(new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 4, chunkZ + 15))
                        .allMatch(state -> state.isAir() && state.getFluidState().isEmpty()),
                "The hold-type fixture needs an empty, dry space");
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPHoldType"));
        player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(KEY, player);
            AbilityInput.keyRelease(OTHER_KEY, player);
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            for (Entity vine : level.getEntities((Entity) null, player.getBoundingBox().inflate(96.0D),
                    entity -> entity instanceof HermitVineProjectileEntity
                            || entity instanceof HermitGrappleVineEntity)) {
                vine.discard();
            }
            if (!player.isRemoved()) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        };
        HermitPurpleStandEntity stand;
        try {
            player.setGameMode(GameType.SURVIVAL);
            PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "The summon produced no Hermit controller");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity controller = stand;
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> guarded(close, () -> {
                    for (String hold : new String[] {
                            HermitPurpleAddon.HP_BLOCK_NAME,
                            HermitPurpleAddon.HP_VINE_NAME,
                            HermitPurpleAddon.HP_BREATH_NAME }) {
                        holdReplacedByAVineAttack(helper, player, power, controller, hold);
                    }
                    LogUtils.getLogger().info("R770_HP_HELD_PASS {}", "hold_type");
                    close.run();
                }))
                .thenSucceed();
    }

    /*
     * 1.16 061 InitStands:69-85: none of Hermit Purple's Stand actions set standRecoveryTicks (StandEntityAction default
     * 0), so a released hold ended at once (stoppedHolding moved it to a 0-tick recovery); hp_vine_attack and
     * hp_vine_shift wound up for HPVineAttack.getStandWindupTicks, max(standWindupDuration 5 - standPerformDuration
     * 10 / 2, 3) = 3 ticks.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_held_phases")
    public static void hermitPhasesAreTheDonorsAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        int y = origin.getY() + PHASES_HEIGHT;
        helper.assertTrue(level.getBlockStates(new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 4, chunkZ + 15))
                        .allMatch(state -> state.isAir() && state.getFluidState().isEmpty()),
                "The phases fixture needs an empty, dry space");
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPPhases"));
        player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(KEY, player);
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            if (!player.isRemoved()) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        };
        HermitPurpleStandEntity stand;
        try {
            player.setGameMode(GameType.SURVIVAL);
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "The summon produced no Hermit controller");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity controller = stand;
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> guarded(close, () -> {
                    for (String name : new String[] {
                            HermitPurpleAddon.HP_GRAB_NAME, HermitPurpleAddon.HP_VINE_NAME,
                            HermitPurpleAddon.HP_VINE_ENTITY_NAME, HermitPurpleAddon.HP_BLOCK_NAME,
                            HermitPurpleAddon.HP_BARRIER_NAME, HermitPurpleAddon.HP_UNBARRIER_NAME,
                            HermitPurpleAddon.HP_GRAB_OD_NAME, HermitPurpleAddon.HP_GRAB_SC_NAME,
                            HermitPurpleAddon.HP_GRAB_HEAL_NAME, HermitPurpleAddon.HP_BEAT_NAME,
                            HermitPurpleAddon.CRINGE_NAME }) {
                        float recovery = initialPhase(helper, power, player, controller, name, ActionPhase.RECOVERY);
                        helper.assertTrue(recovery == 0.0F,
                                "1.16 " + name + " had no recovery (no standRecoveryTicks), the port has " + recovery);
                    }
                    for (String name : new String[] {
                            HermitPurpleAddon.HP_VINE_ATTACK_NAME, HermitPurpleAddon.HP_VINE_SHIFT_NAME }) {
                        float windup = initialPhase(helper, power, player, controller, name, ActionPhase.WINDUP);
                        helper.assertTrue(windup == 3.0F,
                                "1.16 " + name + " wound up for max(5 - 10 / 2, 3) = 3 ticks, the port for " + windup);
                    }
                    Ability thorns = power.getAbility(HermitPurpleAddon.HP_BLOCK_NAME);
                    helper.assertTrue(thorns != null && thorns.checkConditions(power).isPositive(),
                            "Setup: hp_block was refused");
                    BufferingState pressed = BufferingState.clickOnly();
                    AbilityInput.keyPress(KEY, thorns, player, null, InputMethod.HOLD, 0.0F, pressed,
                            thorns.getAbilityId());
                    EntityActionInstance hold = controller.getCurStandAction();
                    helper.assertTrue(pressed.isActionSuccess && hold != null && hold.ability == thorns,
                            "Setup: the held hp_block did not start");
                    AbilityInput.keyRelease(KEY, player);
                    helper.assertTrue(hold.isOver(),
                            "1.16 ended the released hp_block at once (a 0-tick recovery): " + hold.getPhase());
                    LogUtils.getLogger().info("R770_HP_HELD_PASS {}", "phases");
                    close.run();
                }))
                .thenSucceed();
    }

    /*
     * 1.16 StandEntityAction.canClickDuringTask :495-497 refused the running action's own ability pressed again
     * (clickedAction == this, isChainable false; HamonBreath, HPGrapple, HPGrabCommand and HPCamera overrode neither,
     * HPThorns only heldAllowsOtherAction, 061 HPThorns.java:40): no message, not queued, the running action left alone,
     * under any key.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 100, batch = "hp_held_again")
    public static void hermitActionsPressedAgainAreRefusedSilentlyAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        int y = origin.getY() + PRESSED_AGAIN_HEIGHT;
        helper.assertTrue(level.getBlockStates(new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 4, chunkZ + 15))
                        .allMatch(state -> state.isAir() && state.getFluidState().isEmpty()),
                "The pressed-again fixture needs an empty, dry space");
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HPAgain"));
        player.setPos(chunkX + 7.5D, y, chunkZ + 3.5D);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(KEY, player);
            AbilityInput.keyRelease(OTHER_KEY, player);
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            discardVinesAndCatches(level, player);
            if (!player.isRemoved()) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        };
        HermitPurpleStandEntity stand;
        try {
            player.setGameMode(GameType.CREATIVE);
            PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "The summon produced no Hermit controller");
            stand.setTargetSelection(HermitPurpleSpec.TARGET_MODE_PLAYER, "HPAgainNobody");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse("jojo_ripples:polaroid"))));
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity controller = stand;
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> guarded(close, () -> {
                    for (PressedAgain again : new PressedAgain[] {
                            new PressedAgain(HermitPurpleAddon.HP_BREATH_NAME, false, false),
                            new PressedAgain(HermitPurpleAddon.HP_VINE_NAME, false, false),
                            new PressedAgain(HermitPurpleAddon.HP_VINE_ENTITY_NAME, true, false),
                            new PressedAgain(HermitPurpleAddon.HP_GRAB_NAME, false, false),
                            new PressedAgain(HermitPurpleAddon.HP_GRAB_NAME, false, true),
                            new PressedAgain(HermitPurpleAddon.HP_CAMERA_NAME, false, false),
                            new PressedAgain(HermitPurpleAddon.HP_BLOCK_NAME, false, false) }) {
                        ownAbilityPressedAgain(helper, level, player, power, controller, again);
                    }
                    LogUtils.getLogger().info("R770_HP_HELD_PASS {}", "pressed_again");
                    close.run();
                }))
                .thenSucceed();
    }

    /*
     * 1.16 hp_camera (HPCamera, 061 InitStands:83) was a non-hold StandEntityAction that nothing cancelled
     * (StandEntityAction.canBeCanceled :507-512): another action pressed during its windup, perform or recovery waited
     * in the queue (checkTaskCancelling :133-141) and ran after the shot, so the photo was not lost.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_hp_lash", timeoutTicks = 250, batch = "hp_camera_buffered")
    public static void spiritCameraShotSurvivesAnotherPressAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        int y = origin.getY() + CAMERA_HEIGHT;
        helper.assertTrue(level.getBlockStates(new AABB(chunkX + 1, y - 1, chunkZ + 1, chunkX + 15, y + 4, chunkZ + 15))
                        .allMatch(state -> state.isAir() && state.getFluidState().isEmpty()),
                "The camera fixture needs an empty, dry space");
        EmbeddedChannel[] channels = new EmbeddedChannel[2];
        ServerPlayer player = joinedPlayer(level, "HPCamUser", channels, 0);
        ServerPlayer subject = joinedPlayer(level, "HPCamSubject", channels, 1);
        StandPower power = PowerClass.STAND.attachGet(player);
        Runnable close = () -> {
            AbilityInput.keyRelease(KEY, player);
            AbilityInput.keyRelease(OTHER_KEY, player);
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            discardVinesAndCatches(level, player);
            for (int i = 0; i < 2; i++) {
                ServerPlayer joined = i == 0 ? player : subject;
                joined.connection.disconnect(Component.literal("Hermit Purple camera GameTest complete"));
                channels[i].finishAndReleaseAll();
            }
        };
        HermitPurpleStandEntity stand;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.moveTo(chunkX + 7.5D, y, chunkZ + 3.5D, 0.0F, 0.0F);
            subject.setNoGravity(true);
            subject.moveTo(chunkX + 7.5D, y, chunkZ + 9.5D, 0.0F, 0.0F);
            PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
            power.setStand(HermitPurpleAddon.HERMIT_PURPLE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(HermitPurpleAddon.HERMIT_PURPLE.get().summon(player, power), "Hermit summon failed");
            stand = HermitPurpleAddon.controller(player);
            helper.assertTrue(stand != null, "The summon produced no Hermit controller");
            stand.setTargetSelection(HermitPurpleSpec.TARGET_MODE_PLAYER, "HPCamSubject");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse("jojo_ripples:polaroid"))));
            player.getInventory().add(new ItemStack(Items.PAPER, 5));
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        HermitPurpleStandEntity controller = stand;
        EntityActionInstance[] shot = {null};
        boolean[] followUpRan = {false};
        helper.startSequence()
                .thenIdle(stand.summonLockTicks + 2)
                .thenExecute(() -> guarded(close, () -> {
                    power.setStamina(power.getMaxStamina());
                    power.resetAbilityCooldowns();
                    Ability camera = power.getAbility(HermitPurpleAddon.HP_CAMERA_NAME);
                    helper.assertTrue(camera != null && camera.checkConditions(power).isPositive(),
                            "Setup: hp_camera was refused");
                    BufferingState first = BufferingState.clickCanBuffer();
                    AbilityInput.keyPress(KEY, camera, player, null, InputMethod.CLICK, 0.0F, first,
                            camera.getAbilityId());
                    AbilityInput.keyRelease(KEY, player);
                    shot[0] = controller.getCurStandAction();
                    helper.assertTrue(first.isActionSuccess && shot[0] != null && shot[0].ability == camera
                                    && shot[0].getPhase() == ActionPhase.WINDUP,
                            "Setup: hp_camera did not start in its windup: " + shot[0]);
                    power.resetAbilityCooldowns();
                    Ability attack = power.getAbility(HermitPurpleAddon.HP_VINE_ATTACK_NAME);
                    helper.assertTrue(attack != null && attack.checkConditions(power).isPositive(),
                            "Setup: the vine attack was refused during hp_camera");
                    BufferingState second = BufferingState.clickCanBuffer();
                    AbilityInput.keyPress(OTHER_KEY, attack, player, null, InputMethod.CLICK, 0.0F, second,
                            attack.getAbilityId());
                    AbilityInput.keyRelease(OTHER_KEY, player);
                    helper.assertTrue(!second.isActionSuccess && second.shouldBuffer
                                    && controller.getCurStandAction() == shot[0] && !shot[0].isOver(),
                            "1.16 queued the vine attack pressed in hp_camera's windup and left the camera running:"
                                    + " started " + second.isActionSuccess + ", buffered " + second.shouldBuffer
                                    + ", the Stand's action " + controller.getCurStandAction() + " (was " + shot[0]
                                    + ")");
                }))
                .thenIdle(12)
                .thenExecute(() -> guarded(close, () -> {
                    int paper = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.is(Items.PAPER)) {
                            paper += stack.getCount();
                        }
                    }
                    helper.assertTrue(paper == 4,
                            "1.16's hp_camera went on to its shot and used one paper; the camera is in "
                                    + shot[0].getPhase() + " with " + paper + " paper left (5 at the start)");
                }))
                .thenExecuteFor(60, () -> guarded(close, () -> {
                    EntityActionInstance now = controller.getCurStandAction();
                    if (shot[0].isOver() && now != null && now.ability != shot[0].ability) {
                        followUpRan[0] = true;
                    }
                }))
                .thenExecute(() -> guarded(close, () -> {
                    helper.assertTrue(shot[0].isOver() && followUpRan[0],
                            "1.16 ran the queued vine attack once hp_camera ended: the camera is "
                                    + (shot[0].isOver() ? "over" : "still in " + shot[0].getPhase())
                                    + ", the vine attack ran " + followUpRan[0]);
                    LogUtils.getLogger().info("R770_HP_HELD_PASS {}", "camera_buffered");
                    close.run();
                }))
                .thenSucceed();
    }

    private static ServerPlayer joinedPlayer(ServerLevel level, String name, EmbeddedChannel[] channels, int slot) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        channels[slot] = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    // sneaking: hp_vine_entity is hp_vine's sneak variation; caughtGrab: hp_grab's key is released while its vine holds
    // an entity (GrappleAction.releasedWhileAttached), the one hold 1.16's HPGrabCommand kept after the release
    private record PressedAgain(String name, boolean sneaking, boolean caughtGrab) {}

    private static void ownAbilityPressedAgain(GameTestHelper helper, ServerLevel level, FakePlayer player,
            StandPower power, HermitPurpleStandEntity stand, PressedAgain pressedAgain) {
        String name = pressedAgain.name() + (pressedAgain.sneaking() ? " (sneaking)" : "")
                + (pressedAgain.caughtGrab() ? " (released on a caught vine)" : "");
        player.setShiftKeyDown(pressedAgain.sneaking());
        power.setStamina(power.getMaxStamina());
        player.setAirSupply(player.getMaxAirSupply());
        power.resetAbilityCooldowns();
        Ability ability = power.getAbility(pressedAgain.name());
        helper.assertTrue(ability != null && ability.checkConditions(power).isPositive(),
                "Setup: " + name + " was refused");
        InputMethod firstMethod = HermitPurpleAddon.HP_CAMERA_NAME.equals(pressedAgain.name())
                ? InputMethod.CLICK : InputMethod.HOLD;
        BufferingState first = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(KEY, ability, player, null, firstMethod, 0.0F, first, ability.getAbilityId());
        EntityActionInstance running = stand.getCurStandAction();
        helper.assertTrue(first.isActionSuccess && running != null && running.ability == ability,
                "Setup: " + name + " did not start");
        if (pressedAgain.caughtGrab()) {
            catchWithGrabVine(helper, level, stand);
            AbilityInput.keyRelease(KEY, player);
            helper.assertTrue(stand.getCurStandAction() == running && !running.isOver()
                            && running.getPhase() == ActionPhase.PERFORM,
                    "Setup: the released " + name + " did not stay with its caught vine: " + running.getPhase());
        }
        power.resetAbilityCooldowns();
        // the moves are cached once a tick, so this asks again as the next tick's input would
        power._curAvailableMoves.update(power, power.getMoveset());
        AbilityConditionCheck again = power.updateAvailableMoves().getContextVariationContainer(ability);
        helper.assertTrue(again != null && again.ability == ability && !again.conditionCheck.isPositive()
                        && again.conditionCheck.getWarning() == null,
                "1.16 refused " + name + " pressed again during the action without a word, the port's check was "
                        + (again == null ? "nothing" : again.ability.name() + (again.conditionCheck.isPositive()
                                ? " positive" : " a refusal with " + again.conditionCheck.getWarning())));
        for (InputMethod method : new InputMethod[] {InputMethod.CLICK, InputMethod.HOLD}) {
            BufferingState second = BufferingState.clickCanBuffer();
            if (AbilityInput.withConditionCheck(again, player, method)) {
                AbilityInput.keyPress(OTHER_KEY, again.ability, player, null, method, 0.0F, second,
                        ability.getAbilityId());
            }
            AbilityInput.keyRelease(OTHER_KEY, player);
            EntityActionInstance now = stand.getCurStandAction();
            helper.assertTrue(!second.isActionSuccess && !second.shouldBuffer && now == running && !running.isOver(),
                    "1.16 neither restarted nor queued " + name + " pressed again (" + method + "): started "
                            + second.isActionSuccess + ", buffered " + second.shouldBuffer + ", the Stand's action "
                            + now + " (was " + running + ")");
        }
        AbilityInput.keyRelease(KEY, player);
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        player.setShiftKeyDown(false);
        discardVinesAndCatches(level, player);
    }

    // The Stand's grab vine holds a zombie, as a landed hit leaves it (the running hp_grab throws its own vine only
    // in its first tick)
    private static void catchWithGrabVine(GameTestHelper helper, ServerLevel level, HermitPurpleStandEntity stand) {
        HermitGrappleVineEntity vine = new HermitGrappleVineEntity(
                HermitPurpleAddon.HP_GRAB_VINE_ENTITY.get(), stand, level, true, true, 0.0F);
        vine.setPos(stand.getX(), stand.getEyeY(), stand.getZ());
        level.addFreshEntity(vine);
        stand.replaceHeldVine(vine);
        helper.assertTrue(stand.getHeldVine() == vine && vine.isCommandGrab(), "Setup: the grab vine is not held");
        Zombie caught = EntityType.ZOMBIE.create(level);
        caught.setNoAi(true);
        caught.setNoGravity(true);
        caught.setSilent(true);
        caught.moveTo(vine.getX(), vine.getY(), vine.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(caught);
        try {
            Field field = HermitGrappleVineEntity.class.getDeclaredField("ATTACHED_ENTITY");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Integer> attachedEntity = (EntityDataAccessor<Integer>) field.get(null);
            vine.getEntityData().set(attachedEntity, caught.getId());
        }
        catch (ReflectiveOperationException failure) {
            throw new AssertionError("Setup: could not attach the grab vine", failure);
        }
        helper.assertTrue(stand.getAttachedGrabVine() == vine, "Setup: the grab vine holds no entity");
    }

    private static void discardVinesAndCatches(ServerLevel level, Entity around) {
        for (Entity entity : level.getEntities((Entity) null, around.getBoundingBox().inflate(96.0D),
                e -> e instanceof HermitVineProjectileEntity || e instanceof HermitGrappleVineEntity
                        || e instanceof Zombie)) {
            entity.discard();
        }
    }

    private static float initialPhase(GameTestHelper helper, StandPower power, FakePlayer player,
            HermitPurpleStandEntity stand, String name, ActionPhase phase) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType, "Hermit Purple has no action " + name);
        EntityActionType type = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), player, stand);
        return action.phasesLength.getFloat(phase);
    }

    private static void holdReplacedByAVineAttack(GameTestHelper helper, FakePlayer player, StandPower power,
            HermitPurpleStandEntity stand, String holdName) {
        power.setStamina(power.getMaxStamina());
        player.setAirSupply(player.getMaxAirSupply());
        Ability holdAbility = power.getAbility(holdName);
        helper.assertTrue(holdAbility != null && holdAbility.checkConditions(power).isPositive(),
                "Setup: " + holdName + " was refused");
        BufferingState held = BufferingState.clickOnly();
        AbilityInput.keyPress(KEY, holdAbility, player, null, InputMethod.HOLD, 0.0F, held, holdAbility.getAbilityId());
        EntityActionInstance hold = stand.getCurStandAction();
        helper.assertTrue(held.isActionSuccess && hold != null && hold.ability == holdAbility,
                "Setup: the held " + holdName + " did not start");
        helper.assertTrue(hold.isHoldTypeStandAction(),
                "1.16 " + holdName + " was a holdType Stand action, ended by another action of the power that went off:"
                        + " it must be a hold-type Stand action");
        Ability attack = power.getAbility(HermitPurpleAddon.HP_VINE_ATTACK_NAME);
        power.resetAbilityCooldowns();
        helper.assertTrue(attack != null && attack.checkConditions(power).isPositive(),
                "Setup: the vine attack was refused during " + holdName);
        BufferingState pressed = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(OTHER_KEY, attack, player, null, InputMethod.CLICK, 0.0F, pressed, attack.getAbilityId());
        EntityActionInstance now = stand.getCurStandAction();
        helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != hold
                        && now.ability == attack && hold.isOver(),
                "1.16 let the vine attack through the held " + holdName + " at once: started " + pressed.isActionSuccess
                        + ", buffered " + pressed.shouldBuffer + ", the Stand's action " + now + ", the hold over "
                        + hold.isOver() + " in " + hold.getPhase());
        AbilityInput.keyRelease(OTHER_KEY, player);
        AbilityInput.keyRelease(KEY, player);
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
    }

    private static void guarded(Runnable close, Runnable step) {
        try {
            step.run();
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
    }
}
