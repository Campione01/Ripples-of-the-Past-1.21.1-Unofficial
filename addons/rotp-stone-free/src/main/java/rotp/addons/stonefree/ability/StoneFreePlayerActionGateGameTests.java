package rotp.addons.stonefree.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Action.checkConditions (:140-143) refused, first and without a message, an action whose canBeUsedDuringPlayerAction
 * was false while its user ran a continuous player action (PlayerUtilCap.continuousAction: Hamon Overdrive Beat among the
 * 13 non-Stand attacks). Stone Free's StringAction (017 :62-64) returned false: the strings thrown from the user's hand
 * (StoneFreeUserStringAttack, Sweep, Whip, Bind, Pick and both user grapples extend it) were refused, while the user's
 * other strings (Close Wounds, Recover String, the barriers, Mobius: plain StandActions) and the Stand's own moves went
 * through (StandAction :138-141).
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreePlayerActionGateGameTests {
    private static final short BEAT_KEY = 91;
    private static final short STRING_KEY = 92;
    private static final short MOBIUS_KEY = 93;
    private static final short PUNCH_KEY = 94;

    private StoneFreePlayerActionGateGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void thrownStringsAreRefusedDuringAHamonOverdriveBeatAsIn116(GameTestHelper helper) {
        try (Scene s = new Scene(helper)) {
            s.own().entityAim.setTarget(new ActionTarget(s.cow(1.5D)));
            Pressed beatPress = s.press(s.hamon, "hamon_beat", BEAT_KEY);
            EntityActionInstance beat = s.own().getAction();
            helper.assertTrue(beatPress.state.isActionSuccess && beat != null && beat.isPlayerAction()
                            && beat.getPhase() == ActionPhase.PERFORM,
                    "setup: Hamon Overdrive Beat is not running as a player action: started "
                            + beatPress.state.isActionSuccess + ", the user's action " + beat);

            Map<String, Boolean> usable = Map.ofEntries(
                    Map.entry(StoneFreeAddon.USER_STRING_ATTACK_NAME, false),
                    Map.entry(StoneFreeAddon.USER_STRING_SWEEP_NAME, false),
                    Map.entry(StoneFreeAddon.USER_STRING_WHIP_NAME, false),
                    Map.entry(StoneFreeAddon.USER_STRING_BIND_NAME, false),
                    Map.entry(StoneFreeAddon.USER_STRING_PICK_NAME, false),
                    Map.entry(StoneFreeAddon.USER_GRAPPLE_NAME, false),
                    Map.entry(StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME, false),
                    Map.entry(StoneFreeAddon.USER_RECOVER_STRING_NAME, true),
                    Map.entry(StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, true),
                    Map.entry(StoneFreeAddon.USER_BARRIER_NAME, true),
                    Map.entry(StoneFreeAddon.USER_REMOVE_BARRIER_NAME, true),
                    Map.entry(StoneFreeAddon.USER_MOBIUS_STRIP_NAME, true),
                    Map.entry(StoneFreeAddon.STRING_CAPTURE_NAME, true),
                    Map.entry(StoneFreeAddon.STRING_BIND_NAME, true),
                    Map.entry(StoneFreeAddon.GRAPPLE_ENTITY_NAME, true));
            List<String> wrong = new ArrayList<>();
            usable.forEach((name, expected) -> {
                Ability ability = s.stand.getAbility(name);
                if (!(ability instanceof StoneFreeStringAbility)
                        || ability.canBeUsedDuringPlayerAction(beat) != expected) {
                    wrong.add(name + (ability != null ? " " + ability.canBeUsedDuringPlayerAction(beat) : " missing"));
                }
            });
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 refused during a player action only Stone Free's StringActions (the strings thrown from the"
                            + " user's hand); these answer otherwise: " + wrong);

            Pressed string = s.press(s.stand, StoneFreeAddon.USER_STRING_ATTACK_NAME, STRING_KEY);
            Ability attack = s.stand.getAbility(StoneFreeAddon.USER_STRING_ATTACK_NAME);
            helper.assertTrue(!string.check.isPositive() && string.check.getWarning() == null
                            && !string.state.isActionSuccess && !string.state.shouldBuffer
                            && s.standPowerSlot().getAction() == null && s.user.messages.isEmpty()
                            && attack.isHeldBackByHeldAction(s.user) && s.strings().isEmpty(),
                    "1.16 refused the user string attack, without a message, during Hamon Overdrive Beat and drew it"
                            + " unavailable: check " + string.check.isPositive() + ", warning "
                            + string.check.getWarning() + ", started " + string.state.isActionSuccess + ", buffered "
                            + string.state.shouldBuffer + ", the user's Stand-power action "
                            + s.standPowerSlot().getAction() + ", messages " + s.user.messages
                            + ", drawn unavailable " + attack.isHeldBackByHeldAction(s.user) + ", strings out "
                            + s.strings().size());

            boolean mobiusBefore = StoneFreeState.get(s.user).mobiusMode();
            Pressed mobius = s.press(s.stand, StoneFreeAddon.USER_MOBIUS_STRIP_NAME, MOBIUS_KEY);
            EntityActionInstance mobiusAction = s.standPowerSlot().getAction();
            Pressed punch = s.press(s.stand, "punch", PUNCH_KEY);
            EntityActionInstance punchAction = LivingComponentAction.getCurEntityAction(s.standEntity);
            helper.assertTrue(mobius.state.isActionSuccess && !mobius.state.shouldBuffer && mobiusAction != null
                            && mobiusAction.ability == s.stand.getAbility(StoneFreeAddon.USER_MOBIUS_STRIP_NAME)
                            && punch.state.isActionSuccess && punchAction != null
                            && punchAction.ability == s.stand.getAbility("punch") && s.own().getAction() == beat,
                    "1.16 let Stone Free's other Stand actions through the player action: Mobius started "
                            + mobius.state.isActionSuccess + " (" + mobiusAction + "), the punch started "
                            + punch.state.isActionSuccess + " (" + punchAction + "), the beat still running "
                            + (s.own().getAction() == beat));
            s.tickUntil(() -> s.standPowerSlot().getAction() == null);
            helper.assertTrue(StoneFreeState.get(s.user).mobiusMode() != mobiusBefore,
                    "Mobius pressed during the beat did not toggle");

            s.tickUntil(() -> s.own().getAction() != beat && s.standPowerSlot().getAction() == null);
            helper.assertTrue(s.own().getAction() != beat, "setup: Hamon Overdrive Beat did not end");
            Pressed after = s.press(s.stand, StoneFreeAddon.USER_STRING_ATTACK_NAME, STRING_KEY);
            EntityActionInstance thrown = s.standPowerSlot().getAction();
            helper.assertTrue(after.state.isActionSuccess && thrown != null && thrown.ability == attack
                            && thrown.getPerformer() == s.standEntity,
                    "once the player action was over 1.16 took the user string attack: started "
                            + after.state.isActionSuccess + ", the user's Stand-power action " + thrown);
        }
        helper.succeed();
    }

    /*
     * 1.16 PowerBaseImpl.tickHeldAction re-ran Action.checkConditions on every held tick (:376-381): a held pick
     * (StoneFreeUserStringPick extends StringAction, canBeUsedDuringPlayerAction false, :62-64) ended on its next held
     * tick once Hamon Overdrive Beat started, unfired and silently; a held Close Wounds (a plain StandAction) went on.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void aHeldPickEndsOnceABeatStartsAsIn116(GameTestHelper helper) {
        try (Scene s = new Scene(helper)) {
            s.own().entityAim.setTarget(new ActionTarget(s.cow(1.5D)));
            Ability pick = s.stand.getAbility(StoneFreeAddon.USER_STRING_PICK_NAME);
            float stamina = s.stand.getStamina();
            EntityActionInstance charge = s.hold(s.stand, StoneFreeAddon.USER_STRING_PICK_NAME, STRING_KEY);
            s.standPowerSlot().tick();
            helper.assertTrue(charge != null && charge.ability == pick && charge.getPerformer() == s.standEntity
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE && !charge.isOver(),
                    "setup: the pick is not charging in the user's Stand-power slot: " + charge);
            Pressed beatPress = s.press(s.hamon, "hamon_beat", BEAT_KEY);
            EntityActionInstance beat = s.own().getAction();
            helper.assertTrue(beatPress.state.isActionSuccess && beat != null && beat.isPlayerAction()
                            && beat.getPhase() == ActionPhase.PERFORM,
                    "setup: Hamon Overdrive Beat is not running as a player action: " + beat);

            // only the Stand-power slot ticks: the beat strikes in its own tick 5
            s.standPowerSlot().tick();
            boolean endedAtOnce = charge.isOver() || s.standPowerSlot().getAction() != charge;
            for (int tick = 0; tick < 10; tick++) {
                s.standPowerSlot().tick();
            }
            helper.assertTrue(endedAtOnce && s.strings().isEmpty() && !s.stand.isAbilityOnCooldown(pick.name())
                            && s.stand.getStamina() == stamina && s.user.messages.isEmpty(),
                    "1.16 ended the held pick on its next held tick once Hamon Overdrive Beat started, unfired and"
                            + " silently: ended at once " + endedAtOnce + ", strings out " + s.strings().size()
                            + ", cooldown " + s.stand.isAbilityOnCooldown(pick.name()) + ", stamina " + stamina
                            + " -> " + s.stand.getStamina() + ", messages " + s.user.messages);
            AbilityInput.keyRelease(STRING_KEY, s.user);

            helper.assertTrue(s.user.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                    "setup: could not make the user bleed");
            EntityActionInstance closing = s.hold(s.stand, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, STRING_KEY);
            int closedTick = -1;
            for (int tick = 1; tick <= 12 && closedTick < 0; tick++) {
                s.standPowerSlot().tick();
                if (!s.user.hasEffect(ModStatusEffects.BLEEDING)) {
                    closedTick = tick;
                }
            }
            helper.assertTrue(closing != null && closedTick == 10 && s.own().getAction() == beat,
                    "1.16 let a held Close Wounds go on through the beat: closed in tick " + closedTick
                            + ", the beat still running " + (s.own().getAction() == beat));
        }
        helper.succeed();
    }

    private record Pressed(ConditionCheck check, BufferingState state) {}

    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();

        private RecordingPlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }
    }

    /** A Hamon user with Stone Free out at Resolve 4, looking south; it is ticked by hand. */
    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final RecordingPlayer user;
        private final PlayerPower hamon;
        private final StandPower stand;
        private final StoneFreeStandEntity standEntity;
        private final Vec3 origin;
        private final List<Entity> spawned = new ArrayList<>();

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            user = new RecordingPlayer(helper.getLevel(), "SFPlayerActionGate");
            origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            user.setNoGravity(true);
            user.getAbilities().instabuild = false;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the player");
            hamon = PowerClass.PLAYER_POWER.attachGet(user);
            hamon.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData data = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            data.learnSkill(ModHamonSkills.OVERDRIVE.get());
            data.setBreathStability(data.getMaxBreathStability());
            data.setEnergy(data.getMaxEnergy());
            stand = PowerClass.STAND.attachGet(user);
            stand.setStand(StoneFreeAddon.STONE_FREE.get());
            stand.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            stand.skipProgression();
            stand.setResolveLevel(4);
            stand.setStamina(stand.getMaxStamina());
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(user, stand), "Could not summon Stone Free");
            standEntity = StoneFreeServices.controller(user);
            helper.assertTrue(standEntity != null && !standEntity.isArmsOnlyMode(), "Stone Free is not out");
            standEntity.summonLockTicks = 0;
            standEntity.gradualSummonWeaknessTicks = 0;
        }

        private Cow cow(double ahead) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create the cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
            spawned.add(cow);
            return cow;
        }

        private LivingComponentAction own() {
            return LivingComponentAction.getComponent(user);
        }

        private LivingComponentAction standPowerSlot() {
            return LivingComponentAction.getComponent(user, PowerClass.STAND);
        }

        // as the server handles a press: the ability's condition check, then the key press
        private Pressed press(Power<?> power, String name, short key) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability != null, "Missing ability " + name);
            ConditionCheck check = ability.checkConditions(power);
            BufferingState state = BufferingState.clickCanBuffer();
            if (check.isPositive()) {
                AbilityInput.keyPress(key, ability, user, null, InputMethod.CLICK, 0.0F, state, ability.getAbilityId());
            }
            AbilityInput.keyRelease(key, user);
            return new Pressed(check, state);
        }

        // a press whose key stays down; the action it set in the user's Stand-power slot
        private EntityActionInstance hold(Power<?> power, String name, short key) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(ability != null && ability.checkConditions(power).isPositive(),
                    "setup: " + name + " was refused");
            BufferingState state = BufferingState.clickOnly();
            AbilityInput.keyPress(key, ability, user, null, InputMethod.HOLD, 0.0F, state, ability.getAbilityId());
            helper.assertTrue(state.isActionSuccess, "setup: the hold of " + name + " did not start");
            return standPowerSlot().getAction();
        }

        // the user's slots before its Stand's, as the server ticks them
        private void tickUntil(java.util.function.BooleanSupplier done) {
            LivingComponentAction standSlot = LivingComponentAction.getComponent(standEntity);
            for (int tick = 0; tick < 40 && !done.getAsBoolean(); tick++) {
                own().tick();
                standPowerSlot().tick();
                standSlot.tick();
            }
        }

        private List<StoneFreeStringEntity> strings() {
            List<StoneFreeStringEntity> strings = new ArrayList<>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof StoneFreeStringEntity string
                        && StoneFreeServices.userOf(string.getOwner()) == user) {
                    strings.add(string);
                }
            }
            return strings;
        }

        @Override
        public void close() {
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            own().entityAim.setTarget(ActionTarget.EMPTY);
            own().setAction(null, SyncType.NO_SYNC);
            standPowerSlot().setAction(null, SyncType.NO_SYNC);
            if (standEntity != null) {
                LivingComponentAction.getComponent(standEntity).setAction(null, SyncType.NO_SYNC);
            }
            strings().forEach(Entity::discard);
            StoneFreeState.get(user).setMobiusMode(user, false);
            if (stand.isSummoned()) {
                stand.getPowerType().forceUnsummon(user, stand);
            }
            user.removeAllEffects();
            spawned.forEach(Entity::discard);
            user.discard();
        }
    }
}
