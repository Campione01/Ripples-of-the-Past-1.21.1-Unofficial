package rotp.addons.achtungbaby.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.achtungbaby.AchtungBabyAddon;
import rotp.addons.achtungbaby.AchtungBabyGameEvents;
import rotp.addons.achtungbaby.AchtungBabyService;
import rotp.addons.achtungbaby.network.AchtungBabyStatePayload;
import rotp.addons.achtungbaby.stand.AchtungBabyStandType;
import rotp.addons.achtungbaby.state.AchtungBabyState;
import rotp.addons.achtungbaby.ability.AchtungBabyAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 kept Perfect Invisibility in the player's persistent data and never cleared it on logout
 * (PerfectInvisibilityAction 62-88, PerfectInvisibilityManager 85-88); only Ghost mode ended there
 * (AchtungBabyEventHandler 79-88).
 *
 * <p>A FakePlayer does not tick and has no real connection, so each case drives the module's own event
 * subscribers in the order PlayerList uses: logout event, save, remove; then load, add, login event, tick.
 */
@GameTestHolder(AchtungBabyPerfectInvisibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AchtungBabyPerfectInvisibilityGameTests {
    public static final String NAMESPACE = "rotp_ab_perfect";

    private AchtungBabyPerfectInvisibilityGameTests() {}

    // Owner rule of 2026-10-15: only a Stand's guard slows its user. The action runs on the summoned Stand's
    // component, so the core's own walk-speed path (_onActionStarted and setPhase) sets the field in each phase.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void grantActionUsesTenPerformTicksAtFullWalkSpeed(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABGrantWalk", GameType.SURVIVAL, 1.5D);
        StandPower power = StandPower.get(player);
        AchtungBabyStandType type = AchtungBabyAddon.ACHTUNG_BABY.get();
        LivingComponentAction component = null;
        try {
            helper.assertTrue(type.summon(player, power) && power.getSummonedStandEntity() != null,
                    "Setup: the Stand was not summoned");
            StandEntity stand = power.getSummonedStandEntity();
            EntityActionType grant = (EntityActionType) power.getAbility(AchtungBabyAddon.GRANT_INVISIBILITY_NAME);
            helper.assertTrue(grant instanceof StandEntityAbility standAbility
                            && standAbility.getStandUserWalkSpeed() == 1.0F,
                    "Grant Invisibility is registered with a walk speed other than 1.0: " + grant);
            EntityActionInstance action = grant.initActionOnAbilityUse(helper.getLevel(), player, stand, null);
            helper.assertTrue(action instanceof AchtungBabyAbilities.GrantAction
                            && action.phasesLength.getFloat(ActionPhase.PERFORM) == 10.0F
                            && action.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
                    "Grant Invisibility must last ten perform ticks with no recovery.");
            component = LivingComponentAction.getComponent(stand);
            component.setAction(action, player, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action && action.getPhase() != null,
                    "Setup: Grant Invisibility did not start");
            float atStart = stand.getUserWalkSpeed(action.userWalkSpeed);
            action.setPhaseStart(ActionPhase.PERFORM);
            float perform = stand.getUserWalkSpeed(action.userWalkSpeed);
            action.setPhaseStart(ActionPhase.RECOVERY);
            float recovery = stand.getUserWalkSpeed(action.userWalkSpeed);
            helper.assertTrue(atStart == 1.0F && perform == 1.0F && recovery == 1.0F,
                    "Grant slows its user's walk input: start=" + atStart + " perform=" + perform
                            + " recovery=" + recovery);
        } finally {
            try {
                if (component != null) {
                    component.setAction(null, SyncType.NO_SYNC);
                }
            } finally {
                if (power.isSummoned()) {
                    type.forceUnsummon(player, power);
                }
                remove(helper, player);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void perfectInvisibilitySurvivesLogoutSaveAndLogin(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABPerfectRelog", GameType.SURVIVAL);
        AchtungBabyService.togglePerfect(player);
        tick(player);
        helper.assertTrue(state(player).perfectInvisible() && player.hasEffect(MobEffects.INVISIBILITY),
                "Setup: toggling Perfect Invisibility on did not set the mode and its effect");

        AchtungBabyGameEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.assertTrue(state(player).perfectInvisible(),
                "Logout switched Perfect Invisibility off; 1.16 kept it");

        ServerPlayer back = relog(helper, player);
        helper.assertTrue(AchtungBabyAddon.isUser(back), "Setup: the reloaded player lost Achtung Baby");
        helper.assertTrue(state(back).perfectInvisible(),
                "Perfect Invisibility was not in the saved player data");
        back.removeEffect(MobEffects.INVISIBILITY);
        AchtungBabyGameEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(back));
        tick(back);
        helper.assertTrue(state(back).perfectInvisible(),
                "Login or the first tick after it switched Perfect Invisibility off");
        helper.assertTrue(back.hasEffect(MobEffects.INVISIBILITY),
                "The Invisibility effect was not applied again after login");
        helper.assertTrue(AchtungBabyStatePayload.from(back).perfectInvisible(),
                "The state sent to the player and its trackers after login says Perfect Invisibility is off");

        AchtungBabyService.togglePerfect(back);
        helper.assertTrue(!state(back).perfectInvisible() && !back.hasEffect(MobEffects.INVISIBILITY),
                "Toggling off after a relog left the mode or its own effect behind");
        remove(helper, back);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void ghostModeStillExitsOnLogout(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABGhostLogout", GameType.CREATIVE);
        helper.assertTrue(AchtungBabyService.toggleGhost(player) && state(player).ghostMode()
                        && player.isSpectator(),
                "Setup: Ghost mode did not start");
        AchtungBabyGameEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.assertTrue(!state(player).ghostMode(), "Logout left Ghost mode on");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                "Logout did not restore the game mode Ghost mode had replaced: "
                        + player.gameMode.getGameModeForPlayer());
        helper.assertTrue(state(player).originalGameMode() < 0, "Logout kept a stale saved game mode");
        remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void logoutExitsGhostModeButKeepsPerfectInvisibility(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABGhostPerfect", GameType.CREATIVE);
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(AchtungBabyService.toggleGhost(player) && state(player).ghostMode(),
                "Setup: Ghost mode did not start");
        AchtungBabyGameEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.assertTrue(!state(player).ghostMode()
                        && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                "Logout did not exit Ghost mode");
        helper.assertTrue(state(player).perfectInvisible(),
                "Exiting Ghost mode on logout also switched Perfect Invisibility off");
        remove(helper, player);
        helper.succeed();
    }

    // A server that stopped without the logout event leaves Ghost mode in the saved data.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void loginRecoveryExitsSavedGhostModeButKeepsPerfectInvisibility(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABGhostRecover", GameType.CREATIVE);
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(AchtungBabyService.toggleGhost(player) && state(player).ghostMode(),
                "Setup: Ghost mode did not start");

        ServerPlayer back = relog(helper, player);
        // PlayerList.placeNewPlayer restores the saved game mode before the login event.
        back.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(state(back).ghostMode() && state(back).perfectInvisible(),
                "Setup: the saved player data lost Ghost mode or Perfect Invisibility");
        AchtungBabyGameEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(back));
        helper.assertTrue(!state(back).ghostMode()
                        && back.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                "Login did not recover from a saved Ghost mode: ghost=" + state(back).ghostMode()
                        + ", mode=" + back.gameMode.getGameModeForPlayer());
        helper.assertTrue(state(back).perfectInvisible(),
                "Recovering from a saved Ghost mode also switched Perfect Invisibility off");
        remove(helper, back);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void togglingOffKeepsInvisibilityFromAnotherSource(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABPotion", GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3600));
        AchtungBabyService.togglePerfect(player);
        tick(player);
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(!state(player).perfectInvisible(), "Setup: Perfect Invisibility did not toggle off");
        MobEffectInstance potion = player.getEffect(MobEffects.INVISIBILITY);
        helper.assertTrue(potion != null && potion.getDuration() >= 3000,
                "Toggling Perfect Invisibility off removed a 3600-tick Invisibility it had not added: "
                        + potion);
        remove(helper, player);
        helper.succeed();
    }

    // 1.16 kept the flag in data that is not copied to the respawned player.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void deathEndsPerfectInvisibility(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABDeath", GameType.SURVIVAL);
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(state(player).perfectInvisible(), "Setup: Perfect Invisibility did not toggle on");
        AchtungBabyGameEvents.onDeath(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertTrue(!state(player).perfectInvisible(), "Death left Perfect Invisibility on");
        remove(helper, player);
        helper.succeed();
    }

    // 1.16 only ever changed the flag in the toggle (PerfectInvisibilityAction 72-88); nothing looked at the Stand.
    // The player's retraction input runs the Stand's unsummon action, which ends in the type's forceUnsummon.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120)
    public static void perfectInvisibilitySurvivesStandRetraction(GameTestHelper helper) {
        // The Stand idles 0.75 blocks beside and behind its user.
        ServerPlayer player = user(helper, "ABRetract", GameType.SURVIVAL, 1.5D);
        StandPower power = StandPower.get(player);
        AchtungBabyStandType type = AchtungBabyAddon.ACHTUNG_BABY.get();
        helper.assertTrue(type.summon(player, power) && power.isSummoned()
                        && power.getSummonedStandEntity() != null && power.getSummonedStandEntity().isAddedToLevel(),
                "Setup: the Stand was not summoned");
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(state(player).perfectInvisible(), "Setup: Perfect Invisibility did not toggle on");
        helper.runAfterDelay(5, () -> {
            StandEntity stand = power.getSummonedStandEntity();
            if (stand == null || stand.tickCount == 0
                    || !helper.getLevel().isPositionEntityTicking(stand.blockPosition())) {
                String found = stand == null ? "no Stand" : "tick " + stand.tickCount + " at " + stand.position();
                type.forceUnsummon(player, power);
                remove(helper, player);
                helper.fail("Setup: the Stand's chunk does not tick entities, so it cannot act on the retraction"
                        + " input: " + found);
            }
            type.unsummon(player, power);
        });
        helper.runAfterDelay(80, () -> {
            boolean retracted = !power.isSummoned();
            boolean flag = state(player).perfectInvisible();
            player.removeEffect(MobEffects.INVISIBILITY);
            tick(player);
            boolean effect = player.hasEffect(MobEffects.INVISIBILITY);
            boolean sent = AchtungBabyStatePayload.from(player).perfectInvisible();
            AchtungBabyService.togglePerfect(player);
            boolean off = !state(player).perfectInvisible() && !player.hasEffect(MobEffects.INVISIBILITY);
            type.forceUnsummon(player, power);
            remove(helper, player);
            helper.assertTrue(retracted, "Setup: the Stand was still out 75 ticks after the retraction input");
            helper.assertTrue(flag, "Retracting the Stand switched Perfect Invisibility off; 1.16 kept it");
            helper.assertTrue(effect, "The Invisibility effect was not kept up after the Stand was retracted");
            helper.assertTrue(sent,
                    "The state sent to the player and its trackers after the retraction says Perfect Invisibility is off");
            helper.assertTrue(off, "The press after a retraction did not switch Perfect Invisibility off");
            helper.succeed();
        });
    }

    // What the core calls on every tick of a spectator and when the Stand entity is left in another dimension.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void perfectInvisibilitySurvivesForcedUnsummon(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABForced", GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        AchtungBabyService.togglePerfect(player);
        tick(player);
        AchtungBabyAddon.ACHTUNG_BABY.get().forceUnsummon(player, power);
        helper.assertTrue(state(player).perfectInvisible(),
                "A forced unsummon switched Perfect Invisibility off; 1.16 kept it");
        tick(player);
        helper.assertTrue(state(player).perfectInvisible() && player.hasEffect(MobEffects.INVISIBILITY),
                "The tick after a forced unsummon dropped Perfect Invisibility or its effect");
        helper.assertTrue(AchtungBabyStatePayload.from(player).perfectInvisible(),
                "The state sent after a forced unsummon says Perfect Invisibility is off");
        remove(helper, player);
        helper.succeed();
    }

    // Kept port difference: without Achtung Baby there is no ability left to switch it off with.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void losingTheStandEndsPerfectInvisibility(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABStandLoss", GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        AchtungBabyService.togglePerfect(player);
        tick(player);
        helper.assertTrue(state(player).perfectInvisible() && player.hasEffect(MobEffects.INVISIBILITY),
                "Setup: toggling Perfect Invisibility on did not set the mode and its effect");
        power.setStand(null);
        helper.assertTrue(!AchtungBabyAddon.isUser(player), "Setup: the player still has Achtung Baby");
        tick(player);
        helper.assertTrue(!state(player).perfectInvisible(),
                "Perfect Invisibility stayed on a player who no longer has Achtung Baby");
        helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY),
                "Losing Achtung Baby left the mode's own Invisibility effect behind");
        helper.assertTrue(!AchtungBabyStatePayload.from(player).perfectInvisible(),
                "The state sent after losing Achtung Baby says Perfect Invisibility is on");
        remove(helper, player);
        helper.succeed();
    }

    // Ghost mode makes the player a spectator, whose Stand the core force-unsummons on every tick.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void forcedUnsummonLeavesGhostModeAndPerfectInvisibilityOn(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABGhostForced", GameType.CREATIVE);
        StandPower power = StandPower.get(player);
        AchtungBabyService.togglePerfect(player);
        helper.assertTrue(AchtungBabyService.toggleGhost(player) && state(player).ghostMode()
                        && player.isSpectator(),
                "Setup: Ghost mode did not start");
        AchtungBabyAddon.ACHTUNG_BABY.get().forceUnsummon(player, power);
        tick(player);
        helper.assertTrue(state(player).ghostMode() && player.isSpectator(),
                "A forced unsummon ended Ghost mode");
        helper.assertTrue(state(player).perfectInvisible(),
                "A forced unsummon during Ghost mode switched Perfect Invisibility off");
        AchtungBabyService.endGhost(player);
        remove(helper, player);
        helper.succeed();
    }

    // The kept sneak branch of Perfect Invisibility hides the crosshair's living target, the user's own pet too:
    // the core's pressed-target check (a living entity the Stand may attack) must not empty it.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void sneakPerfectInvisibilityHidesTheUsersOwnPet(GameTestHelper helper) {
        ServerPlayer player = user(helper, "ABSneakPet", GameType.SURVIVAL, 3.0D);
        StandPower power = StandPower.get(player);
        AchtungBabyStandType type = AchtungBabyAddon.ACHTUNG_BABY.get();
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        LivingComponentAction component = null;
        try {
            helper.assertTrue(wolf != null, "Setup: no wolf");
            helper.assertTrue(type.summon(player, power) && power.getSummonedStandEntity() != null,
                    "Setup: the Stand was not summoned");
            StandEntity stand = power.getSummonedStandEntity();
            wolf.moveTo(player.getX() + 2.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Setup: the wolf was not added");
            wolf.tame(player);
            helper.assertTrue(!stand.canAttack(wolf), "Setup: the Stand may attack its user's own wolf");
            LivingComponentAction.getComponent(player);
            LivingComponentAction.getAim(player).setTarget(new ActionTarget(wolf));
            player.setPose(Pose.CROUCHING);
            EntityActionType perfect =
                    (EntityActionType) power.getAbility(AchtungBabyAddon.PERFECT_INVISIBILITY_NAME);
            EntityActionInstance action = perfect.createActionObj();
            perfect.initActionFromConfig(action, helper.getLevel(), player, stand);
            action.setStartingPhase();
            component = LivingComponentAction.getComponent(stand);
            component.setAction(action, player, SyncType.NO_SYNC);
            // the summon lock would hold the action back for the Stand's first ticks
            stand.summonLockTicks = 0;
            for (int tick = 0; tick < 10 && !wolf.hasEffect(MobEffects.INVISIBILITY); tick++) {
                component.tick();
            }
            helper.assertTrue(wolf.hasEffect(MobEffects.INVISIBILITY),
                    "Sneaking Perfect Invisibility at the user's own wolf did not hide it: phase " + action.getPhase()
                            + ", crouching " + player.isCrouching() + ", own mode " + state(player).perfectInvisible());
            helper.assertTrue(!state(player).perfectInvisible(),
                    "The sneak branch toggled the user's own Perfect Invisibility");
        } finally {
            try {
                if (component != null) {
                    component.setAction(null, SyncType.NO_SYNC);
                }
                if (wolf != null) {
                    wolf.discard();
                }
            } finally {
                if (power.isSummoned()) {
                    type.forceUnsummon(player, power);
                }
                remove(helper, player);
            }
        }
        helper.succeed();
    }

    private static AchtungBabyState state(ServerPlayer player) {
        return AchtungBabyAddon.state(player);
    }

    private static void tick(ServerPlayer player) {
        AchtungBabyGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
    }

    /**
     * The test origin is random and only its chunk is sure to tick entities. Moves a point just far enough that
     * whatever stands or drifts within {@code reach} blocks of it is still in that chunk.
     */
    static Vec3 insideOriginChunk(GameTestHelper helper, Vec3 relative, double reach) {
        Vec3 at = helper.absoluteVec(relative);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        double minX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX()));
        double minZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ()));
        return new Vec3(
                Mth.clamp(at.x, minX + reach, minX + 16.0D - reach),
                at.y,
                Mth.clamp(at.z, minZ + reach, minZ + 16.0D - reach));
    }

    private static ServerPlayer user(GameTestHelper helper, String name, GameType mode) {
        return user(helper, name, mode, 0.0D);
    }

    private static ServerPlayer user(GameTestHelper helper, String name, GameType mode, double reach) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(insideOriginChunk(helper, new Vec3(0.5D, 2.0D, 0.5D), reach));
        helper.getLevel().addNewPlayer(player);
        helper.assertTrue(helper.getLevel().getEntity(player.getId()) == player,
                "Setup: could not add the test player");
        player.setGameMode(mode);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(AchtungBabyAddon.ACHTUNG_BABY.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(AchtungBabyAddon.isUser(player), "Setup: the test player is not an Achtung Baby user");
        return player;
    }

    private static ServerPlayer relog(GameTestHelper helper, ServerPlayer player) {
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        remove(helper, player);
        // A new instance, as after a restart: nothing but the saved data carries over.
        FakePlayer back = new FakePlayer(helper.getLevel(), new GameProfile(player.getUUID(), "ABBack"));
        back.load(saved);
        helper.getLevel().addNewPlayer(back);
        helper.assertTrue(back != player && helper.getLevel().getPlayerByUUID(player.getUUID()) == back,
                "Setup: the reloaded player did not replace the saved one");
        return back;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }
}
