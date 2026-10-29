package rotp.addons.metallica.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 create_blades_in_victim (069 InitStands.java:49) was a holdType(100) StandEntityAction with cooldown(300) and no
 * recovery: PowerBaseImpl set no cooldown at its click (setHeldAction) and the 300 as its hold ended (stopHeldAction:
 * the release, a failed check, the 100th held tick, or another action that went off), and StandEntityAction.isCancelable
 * let a pressed Stand action replace it at once. The tests tick the Stand's action and the power's cooldowns themselves,
 * one game tick at a time; Metallica has no punch, so its scalpel throw is the pressed Stand action.
 */
@GameTestHolder(MetallicaAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetallicaVictimBladesHoldGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "metallica_victim_blades_hold";
    private static final short HOLD_KEY = 61;
    private static final short OTHER_KEY = 62;
    private static final String BLADES = MetallicaAddon.VICTIM_BLADES_NAME;
    private static final String SCALPEL = MetallicaAddon.SINGLE_SCALPEL_NAME;
    private static final String INVISIBILITY = MetallicaAddon.INVISIBILITY_NAME;
    private static final int COOLDOWN = MetallicaPhase1Spec.VICTIM_BLADES_COOLDOWN_TICKS;

    private MetallicaVictimBladesHoldGameTests() {}

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void victimBladesTakeTheirCooldownAsTheHoldEndsAsIn116(GameTestHelper helper) {
        for (String row : new String[] { "let go at hold tick 50", "held for its full 100 ticks",
                "ended by a failed check (the Stand stunned)" }) {
            Scene scene = Scene.player(helper, "metallica-blades-cooldown");
            try {
                EntityActionInstance blades = scene.holdBlades(row);
                scene.tick(50);
                helper.assertTrue(!blades.isOver() && blades.getPhase() == ActionPhase.PERFORM
                                && !scene.power.isAbilityOnCooldown(BLADES),
                        "1.16 set no victim-blades cooldown during the hold (" + row + "), at hold tick 50: phase "
                                + blades.getPhase() + ", cooldown " + scene.power.getAbilityCooldown(BLADES));
                switch (row) {
                    case "let go at hold tick 50" -> AbilityInput.keyRelease(HOLD_KEY, scene.user);
                    case "held for its full 100 ticks" -> {
                        int ticks = 50;
                        while (!blades.isOver() && ticks < MetallicaPhase1Spec.VICTIM_BLADES_MAX_HOLD_TICKS + 5) {
                            helper.assertTrue(!scene.power.isAbilityOnCooldown(BLADES), "1.16 set no victim-blades"
                                    + " cooldown while the hold ran (tick " + ticks + "): "
                                    + scene.power.getAbilityCooldown(BLADES));
                            scene.tick(1);
                            ticks++;
                        }
                        helper.assertTrue(ticks >= MetallicaPhase1Spec.VICTIM_BLADES_MAX_HOLD_TICKS
                                        && ticks <= MetallicaPhase1Spec.VICTIM_BLADES_MAX_HOLD_TICKS + 1,
                                "1.16 holdType(100) ended the hold at its 100th tick: it ended after " + ticks);
                    }
                    default -> {
                        scene.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
                        scene.tick(1);
                    }
                }
                scene.assertCooldownTakenOnce(blades, row);
                LOGGER.info("R770_METALLICA_BLADES_HOLD_PASS cooldown row=\"{}\" cooldown={}", row,
                        scene.power.getAbilityCooldown(BLADES));
            }
            finally {
                scene.close();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void aStandActionPressedDuringTheVictimBladesReplacesItAtOnceAsIn116(GameTestHelper helper) {
        Scene scene = Scene.player(helper, "metallica-blades-scalpel");
        try {
            EntityActionInstance blades = scene.holdBlades("a scalpel throw pressed during the hold");
            scene.tick(20);
            BufferingState scalpel = scene.press(SCALPEL, InputMethod.CLICK, OTHER_KEY);
            EntityActionInstance now = scene.standAction.getAction();
            helper.assertTrue(scalpel.isActionSuccess && !scalpel.shouldBuffer && now != null && now != blades
                            && now.ability == scene.power.getAbility(SCALPEL) && blades.isOver(),
                    "1.16 let a scalpel throw pressed during the held victim blades replace them at once: started "
                            + scalpel.isActionSuccess + ", buffered " + scalpel.shouldBuffer + ", the Stand's action "
                            + now + ", the blades in " + blades.getPhase());
            scene.assertCooldownTakenOnce(blades, "a scalpel throw pressed during the hold");
            LOGGER.info("R770_METALLICA_BLADES_HOLD_PASS replaced cooldown={}", scene.power.getAbilityCooldown(BLADES));
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    /*
     * 1.16 invisibility (069 InitStands.java:47): holdType() + holdToFire(30, false) + standWindupDuration(30), no
     * recovery and no isCancelable override. Once the released charge fired, StandEntityAction.isCancelable let a pressed
     * Stand action cancel its task in the windup and at its perform's start, a tick before standPerform toggled the
     * invisibility (StandEntityTask moved into the perform at the end of the windup's last tick).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void aFiredInvisibilityGivesWayToAStandPressUntilItTogglesAsIn116(GameTestHelper helper) {
        for (boolean atPerformStart : new boolean[] { false, true }) {
            String row = atPerformStart ? "at its perform's start" : "in its windup";
            Scene scene = Scene.player(helper, "metallica-invisibility-gives-way");
            try {
                scene.power.getCurTypeData()._lockedAbilities.remove(INVISIBILITY);
                BufferingState held = scene.press(INVISIBILITY, InputMethod.HOLD, HOLD_KEY);
                EntityActionInstance invisibility = scene.standAction.getAction();
                helper.assertTrue(held.isActionSuccess && invisibility != null
                                && invisibility.ability == scene.power.getAbility(INVISIBILITY)
                                && invisibility.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Setup (" + row + "): the invisibility is not charging: " + invisibility);
                scene.tick(MetallicaPhase1Spec.INVISIBILITY_CHARGE_TICKS);
                AbilityInput.keyRelease(HOLD_KEY, scene.user);
                helper.assertTrue(invisibility.getPhase() == ActionPhase.WINDUP,
                        "Setup (" + row + "): the fully charged invisibility let go did not fire into its windup: "
                                + invisibility.getPhase());
                if (atPerformStart) {
                    for (int i = 0; i <= MetallicaPhase1Spec.INVISIBILITY_WINDUP_TICKS
                            && invisibility.getPhase() != ActionPhase.PERFORM && !invisibility.isOver(); i++) {
                        scene.tick(1);
                    }
                    helper.assertTrue(invisibility.getPhase() == ActionPhase.PERFORM
                                    && invisibility.getPhaseTick() < 1,
                            "Setup: the invisibility is not at its perform's start: " + invisibility.getPhase()
                                    + " tick " + invisibility.getPhaseTick());
                }
                else {
                    scene.tick(10);
                }
                helper.assertTrue(!MetallicaAddon.invisibility(scene.user).active(),
                        "Setup (" + row + "): the invisibility toggled before its perform");
                BufferingState scalpel = scene.press(SCALPEL, InputMethod.CLICK, OTHER_KEY);
                EntityActionInstance now = scene.standAction.getAction();
                helper.assertTrue(scalpel.isActionSuccess && !scalpel.shouldBuffer && now != null
                                && now != invisibility && now.ability == scene.power.getAbility(SCALPEL)
                                && invisibility.isOver(),
                        "1.16 let a scalpel throw pressed " + row + " of the fired invisibility cancel it at once"
                                + " (isCancelable): started " + scalpel.isActionSuccess + ", buffered "
                                + scalpel.shouldBuffer + ", the Stand's action " + now + ", the invisibility in "
                                + invisibility.getPhase());
                scene.tick(2);
                helper.assertTrue(!MetallicaAddon.invisibility(scene.user).active(),
                        "1.16 never toggled an invisibility cancelled " + row + ": the user turned invisible");
                LOGGER.info("R770_METALLICA_BLADES_HOLD_PASS invisibility_gives_way row=\"{}\"", row);
            }
            finally {
                scene.close();
            }
        }
        helper.succeed();
    }

    // A mob's AI presses through keyPressMob on the server, as 1.16 mobs pressed with clickAction.
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void aMobsVictimBladesTakeTheirCooldownAsTheHoldEndsAsIn116(GameTestHelper helper) {
        for (boolean letGo : new boolean[] { true, false }) {
            Scene scene = Scene.mob(helper);
            try {
                Ability blades = scene.power.getAbility(BLADES);
                int setsBefore = scene.power.getAbilityCooldownSets(BLADES);
                HeldInputEntry held = AbilityInput.keyPressMob(blades, scene.user, null, InputMethod.HOLD);
                EntityActionInstance action = scene.standAction.getAction();
                helper.assertTrue(held != null && action != null && action.ability == blades
                                && action.getPhase() == ActionPhase.PERFORM && !scene.power.isAbilityOnCooldown(BLADES),
                        "1.16 started the mob's victim blades with no cooldown at the press: " + action + ", cooldown "
                                + scene.power.getAbilityCooldown(BLADES));
                scene.tick(50);
                helper.assertTrue(!action.isOver() && !scene.power.isAbilityOnCooldown(BLADES),
                        "1.16 set no cooldown during the mob's hold: phase " + action.getPhase() + ", cooldown "
                                + scene.power.getAbilityCooldown(BLADES));
                if (letGo) {
                    AbilityInput.keyRelease(held.keyId, scene.user);
                }
                else {
                    AbilityInput.keyPressMob(scene.power.getAbility(SCALPEL), scene.user, null, InputMethod.CLICK);
                    EntityActionInstance now = scene.standAction.getAction();
                    helper.assertTrue(now != null && now != action && now.ability == scene.power.getAbility(SCALPEL),
                            "1.16 let the mob's scalpel throw replace its held victim blades at once: the Stand's action "
                                    + now);
                }
                helper.assertTrue(action.isOver() && scene.power.getAbilityCooldown(BLADES) == COOLDOWN
                                && scene.power.getAbilityCooldownSets(BLADES) == setsBefore + 1,
                        "1.16 set the mob's 300-tick victim-blades cooldown once as its hold ended ("
                                + (letGo ? "let go" : "replaced by a scalpel throw") + "): phase " + action.getPhase()
                                + ", cooldown " + scene.power.getAbilityCooldown(BLADES) + ", sets "
                                + (scene.power.getAbilityCooldownSets(BLADES) - setsBefore));
                LOGGER.info("R770_METALLICA_BLADES_HOLD_PASS mob {} cooldown={}", letGo ? "let go" : "replaced",
                        scene.power.getAbilityCooldown(BLADES));
            }
            finally {
                scene.close();
            }
        }
        helper.succeed();
    }

    /** A survival player or a zombie with Metallica summoned at Resolve level 4; the user's own tick does not run. */
    private static final class Scene {
        private final GameTestHelper helper;
        private final LivingEntity user;
        private final StandPower power;
        private final StandEntity stand;
        private final LivingComponentAction standAction;
        private int setsBefore;

        private Scene(GameTestHelper helper, LivingEntity user) {
            this.helper = helper;
            this.user = user;
            Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Metallica user.");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(MetallicaAddon.METALLICA.get()))
                    .status() == StandPowerTransitions.Status.APPLIED, "Could not grant Metallica.");
            power.setResolveLevel(4);
            power.getCurTypeData()._lockedAbilities.remove(BLADES);
            power.getCurTypeData()._lockedAbilities.remove(SCALPEL);
            helper.assertTrue(MetallicaAddon.METALLICA.get().summon(user, power) && power.getSummonedStandEntity() != null,
                    "Metallica could not be summoned.");
            stand = power.getSummonedStandEntity();
            // the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
            stand.summonLockTicks = 0;
            standAction = LivingComponentAction.getComponent(stand);
            power.setStamina(power.getMaxStamina());
        }

        private static Scene player(GameTestHelper helper, String name) {
            FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            player.getAbilities().instabuild = false;
            return new Scene(helper, player);
        }

        private static Scene mob(GameTestHelper helper) {
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Could not create the zombie.");
            zombie.setNoAi(true);
            return new Scene(helper, zombie);
        }

        // Pressed by its hotbar key and kept held: the held perform starts, with no cooldown.
        private EntityActionInstance holdBlades(String row) {
            setsBefore = power.getAbilityCooldownSets(BLADES);
            BufferingState state = press(BLADES, InputMethod.HOLD, HOLD_KEY);
            EntityActionInstance action = standAction.getAction();
            helper.assertTrue(state.isActionSuccess && action != null && action.ability == power.getAbility(BLADES)
                            && action.getPhase() == ActionPhase.PERFORM,
                    "Setup (" + row + "): the victim blades are not held: started " + state.isActionSuccess
                            + ", the Stand's action " + action);
            helper.assertTrue(!power.isAbilityOnCooldown(BLADES), "1.16 set no victim-blades cooldown at the press ("
                    + row + "): " + power.getAbilityCooldown(BLADES));
            return action;
        }

        private void assertCooldownTakenOnce(EntityActionInstance blades, String row) {
            int sets = power.getAbilityCooldownSets(BLADES) - setsBefore;
            helper.assertTrue(blades.isOver() && power.getAbilityCooldown(BLADES) == COOLDOWN && sets == 1,
                    "1.16 stopHeldAction set the 300-tick victim-blades cooldown once as the hold ended (" + row
                            + "): phase " + blades.getPhase() + ", cooldown " + power.getAbilityCooldown(BLADES)
                            + ", set " + sets + " times");
        }

        // As ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press.
        private BufferingState press(String name, InputMethod method, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = power.getAbility(name);
            helper.assertTrue(base != null, "Missing ability " + name);
            AbilityConditionCheck container = resolve(base);
            if (container != null && AbilityInput.withConditionCheck(container, user, method)) {
                AbilityInput.keyPress(key, container.ability, user, null, method, 0.0F, state, base.getAbilityId());
            }
            return state;
        }

        // The power resolves its keys once a tick, and the test does not tick it.
        private AbilityConditionCheck resolve(Ability ability) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(ability);
        }

        private void tick(int ticks) {
            for (int i = 0; i < ticks; i++) {
                tickCooldowns();
                standAction.tick();
            }
        }

        private void tickCooldowns() {
            try {
                Method method = StandPower.class.getDeclaredMethod("tickAbilityCooldowns");
                method.setAccessible(true);
                method.invoke(power);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("StandPower.tickAbilityCooldowns is not reachable", e);
            }
        }

        private void close() {
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            standAction.setAction(null, user, SyncType.NO_SYNC);
            if (power.isSummoned()) {
                MetallicaAddon.METALLICA.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }
}
