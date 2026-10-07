package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodGiftAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismDarkAuraAbility;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 NonStandAction.checkEnergy: a vampire action that lacks blood fails with the power's own message
 * (jojo.message.action_condition.no_energy_vampirism), on the press and when a hold runs out of it. The target
 * check came first (PowerBaseImpl.checkRequirements), so a missing target is not reported as hunger.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismNoEnergyMessageGameTests {
    private static final String BATCH = "vampirism_no_energy_message";
    private static final String NO_ENERGY = "jojo.message.action_condition.no_energy_vampirism";

    private VampirismNoEnergyMessageGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void underfundedGiftPressShowsNoEnergyMessage(GameTestHelper helper) {
        try (Fixture fixture = Fixture.open(helper, false)) {
            Ability gift = fixture.gift();
            fixture.aimAtRecipient();
            fixture.blood(300.0F);
            helper.assertTrue(gift.checkConditions(fixture.power).isPositive(),
                    "NO-ENERGY premise: a funded Gift on the same recipient is not admitted: " + fixture.keys());
            fixture.blood(50.0F);
            boolean admitted = fixture.admit(gift, InputMethod.HOLD);
            helper.assertTrue(!admitted && fixture.keys().equals(List.of(NO_ENERGY)),
                    "Underfunded Blood Gift press: admitted=" + admitted + " messages=" + fixture.keys()
                            + " expected the single message " + NO_ENERGY);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void underfundedGiftPressWithoutRecipientIsNotReportedAsHunger(GameTestHelper helper) {
        try (Fixture fixture = Fixture.open(helper, false)) {
            Ability gift = fixture.gift();
            fixture.blood(50.0F);
            boolean admitted = fixture.admit(gift, InputMethod.HOLD);
            helper.assertTrue(!admitted && !fixture.keys().contains(NO_ENERGY),
                    "Underfunded Blood Gift press at no recipient: admitted=" + admitted + " messages=" + fixture.keys()
                            + "; 1.16 checked the target before the blood");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void giftHoldRunningOutOfBloodShowsNoEnergyMessage(GameTestHelper helper) {
        try (Fixture fixture = Fixture.open(helper, false)) {
            EntityActionInstance action = fixture.holdGiftForThreeTicks();
            fixture.blood(100.0F);
            fixture.component.tick();
            helper.assertTrue(action.isOver() && fixture.component.getAction() == null
                            && fixture.keys().equals(List.of(NO_ENERGY)) && fixture.bloodNow() == 100.0F,
                    "Blood Gift hold that ran out of blood: over=" + action.isOver() + " messages=" + fixture.keys()
                            + " blood=" + fixture.bloodNow() + " expected a stop with the single message " + NO_ENERGY);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void giftHoldLosingItsRecipientIsNotReportedAsHunger(GameTestHelper helper) {
        try (Fixture fixture = Fixture.open(helper, false)) {
            EntityActionInstance action = fixture.holdGiftForThreeTicks();
            fixture.blood(100.0F);
            fixture.component.entityAim.setTarget(ActionTarget.EMPTY);
            fixture.component.tick();
            helper.assertTrue(action.isOver() && fixture.component.getAction() == null
                            && !fixture.keys().contains(NO_ENERGY),
                    "Blood Gift hold that lost its recipient: over=" + action.isOver() + " messages=" + fixture.keys()
                            + "; 1.16 checked the target before the blood");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void underfundedDarkAuraPressShowsNoEnergyMessage(GameTestHelper helper) {
        try (Fixture fixture = Fixture.open(helper, true)) {
            Ability aura = fixture.power.getAbility("vampirism_dark_aura");
            helper.assertTrue(aura instanceof VampirismDarkAuraAbility, "NO-ENERGY premise: registered Dark Aura is missing");
            fixture.blood(25.0F);
            helper.assertTrue(aura.checkConditions(fixture.power).isPositive(),
                    "NO-ENERGY premise: Dark Aura at its exact cost is not admitted");
            fixture.blood(1.0F);
            boolean admitted = fixture.admit(aura, InputMethod.CLICK);
            helper.assertTrue(!admitted && fixture.keys().equals(List.of(NO_ENERGY)),
                    "Underfunded Dark Aura press: admitted=" + admitted + " messages=" + fixture.keys()
                            + " expected the single message " + NO_ENERGY);
        }
        helper.succeed();
    }

    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> actionBar = new ArrayList<>();

        private RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "VampireHunger"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                this.actionBar.add(message);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private static final short KEY = 73;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private RecordingPlayer giver;
        private Player recipient;
        private PlayerPower power;
        private VampirismData data;
        private LivingComponentAction component;
        private boolean pressed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        private static Fixture open(GameTestHelper helper, boolean fullPower) {
            Fixture fixture = new Fixture(helper);
            try {
                fixture.setUp(fullPower);
                return fixture;
            }
            catch (RuntimeException | Error error) {
                fixture.close();
                throw error;
            }
        }

        private void setUp(boolean fullPower) {
            helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL, "NO-ENERGY premise: requires the Normal difficulty world");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
            helper.assertTrue(level.getEntities((Entity) null, new AABB(origin, origin).inflate(4.0D)).isEmpty(),
                    "NO-ENERGY premise: scene contains a foreign entity");
            giver = new RecordingPlayer(level);
            giver.setGameMode(GameType.SURVIVAL);
            giver.setNoGravity(true);
            giver.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            level.addNewPlayer(giver);
            recipient = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            recipient.setNoGravity(true);
            recipient.moveTo(origin.x, origin.y, origin.z + 1.5D, 180.0F, 0.0F);
            recipient.setHealth(6.0F);
            level.addFreshEntity(recipient);
            PowerClass.PLAYER_POWER.attachGet(recipient);
            power = PowerClass.PLAYER_POWER.attachGet(giver);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(giver, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(fullPower, giver);
            giver.setHealth(giver.getMaxHealth());
            component = LivingComponentAction.getComponent(giver);
            giver.actionBar.clear();
            helper.assertTrue(giver.getHealth() > 10.0F && giver.getMainHandItem().isEmpty() && !giver.isCreative()
                            && giver.hasLineOfSight(recipient) && recipient.getHealth() == 6.0F,
                    "NO-ENERGY premise: giver or recipient is not in the ordinary Gift state");
        }

        private Ability gift() {
            Ability gift = power.getAbility("vampirism_blood_gift");
            helper.assertTrue(gift instanceof VampirismBloodGiftAbility, "NO-ENERGY premise: registered Blood Gift is missing");
            return gift;
        }

        private void aimAtRecipient() {
            component.entityAim.setTarget(new ActionTarget(recipient));
        }

        private void blood(float value) {
            VampirismState.get(giver).blood().setCurrent(value);
            data.setBloodLevel(value);
        }

        private float bloodNow() {
            return VampirismState.get(giver).blood().current();
        }

        private boolean admit(Ability ability, InputMethod inputMethod) {
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            return AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), giver, inputMethod);
        }

        private EntityActionInstance holdGiftForThreeTicks() {
            Ability gift = gift();
            aimAtRecipient();
            blood(320.0F);
            helper.assertTrue(admit(gift, InputMethod.HOLD), "NO-ENERGY premise: funded Gift HOLD was not admitted: " + keys());
            HeldInputEntry held = AbilityInput.keyPress(KEY, gift, giver, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), gift.getAbilityId());
            pressed = true;
            EntityActionInstance action = component.getAction();
            helper.assertTrue(held != null && action != null && held.action == action
                            && action.getPhase() == ActionPhase.WINDUP, "NO-ENERGY premise: Gift HOLD did not install its WINDUP");
            for (int tick = 0; tick < 3; tick++) {
                component.tick();
            }
            helper.assertTrue(component.getAction() == action && !action.isOver() && action.getPhase() == ActionPhase.WINDUP
                            && bloodNow() == 305.0F && keys().isEmpty(),
                    "NO-ENERGY premise: funded Gift hold did not pay three ticks quietly: blood=" + bloodNow()
                            + " messages=" + keys());
            return action;
        }

        private List<String> keys() {
            return giver.actionBar.stream()
                    .map(message -> message.getContents() instanceof TranslatableContents contents
                            ? contents.getKey() : message.getString())
                    .toList();
        }

        @Override
        public void close() {
            try {
                if (pressed) AbilityInput.keyRelease(KEY, giver);
                if (component != null) {
                    component.entityAim.setTarget(ActionTarget.EMPTY);
                    component.setAction(null, giver, SyncType.NO_SYNC);
                }
            }
            finally {
                if (recipient != null) recipient.discard();
                if (giver != null) giver.discard();
            }
        }
    }
}
