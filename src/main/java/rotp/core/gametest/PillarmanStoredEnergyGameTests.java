package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanStoredEnergyGameTests {
    private static final String DIVINE = "pillarman_divine_sandstorm";
    private static final String RIFT = "pillarman_atmospheric_rift";
    private static final String[] ABILITIES = { DIVINE, RIFT };

    private PillarmanStoredEnergyGameTests() {}

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void divineCreativeZeroStopsDuringCharge(GameTestHelper helper) {
        zeroDuringCharge(helper, DIVINE);
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void riftCreativeZeroStopsDuringCharge(GameTestHelper helper) {
        zeroDuringCharge(helper, RIFT);
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void divineCreativeZeroStopsBeforePerformWave(GameTestHelper helper) {
        zeroBeforePerform(helper, DIVINE);
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void riftCreativeZeroStopsBeforePerformWave(GameTestHelper helper) {
        zeroBeforePerform(helper, RIFT);
    }

    private static void zeroDuringCharge(GameTestHelper helper, String name) {
        try (Fixture fixture = new Fixture(helper, name, GameType.CREATIVE, 0.0F)) {
            helper.assertTrue(fixture.press(), "The donor's fresh zero-energy press must still be admitted");
            fixture.tick();
            fixture.assertStoppedWithWaves(0, "Zero stored energy kept the charge alive");
            fixture.assertEnergy(0.0F);
            fixture.release();
        }
        helper.succeed();
    }

    private static void zeroBeforePerform(GameTestHelper helper, String name) {
        try (Fixture fixture = new Fixture(helper, name, GameType.CREATIVE, 1.0F)) {
            fixture.charge();
            fixture.data.setEnergy(fixture.player, 0.0F);
            fixture.tick();
            fixture.assertStoppedWithWaves(0, "Zero stored energy allowed a perform wave");
            fixture.assertEnergy(0.0F);
            fixture.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void creativeOneEnergyRemainsFreeAndReleaseStopsNewWaves(GameTestHelper helper) {
        for (String name : ABILITIES) {
            try (Fixture fixture = new Fixture(helper, name, GameType.CREATIVE, 1.0F)) {
                fixture.charge();
                for (int tick = 0; tick < 4; tick++) {
                    fixture.tick();
                    helper.assertTrue(fixture.component.getAction() != null, "Positive Creative hold stopped");
                    fixture.assertEnergy(1.0F);
                }
                helper.assertTrue(fixture.waves().size() == 2, "Positive Creative hold did not emit on both eligible ticks");
                fixture.release();
                fixture.tick();
                fixture.tick();
                fixture.assertStoppedWithWaves(2, "Release emitted new waves or removed existing projectiles");
                fixture.assertEnergy(1.0F);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void survivalThreeEnergyKeepsItsFinalPaidWave(GameTestHelper helper) {
        for (String name : ABILITIES) {
            try (Fixture fixture = new Fixture(helper, name, GameType.SURVIVAL, 3.0F)) {
                fixture.charge();
                fixture.assertEnergy(3.0F);
                fixture.tick();
                fixture.assertEnergy(0.0F);
                helper.assertTrue(fixture.waves().size() == 1, "Exact-cost Survival tick lost its final paid wave");
                fixture.tick();
                fixture.assertStoppedWithWaves(1, "Exhausted Survival hold continued firing");
                fixture.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "pillarman_stored_energy")
    public static void windModePowerAndUnrestrictedStageRemainUnchanged(GameTestHelper helper) {
        for (String name : ABILITIES) {
            try (Fixture fixture = new Fixture(helper, name, GameType.CREATIVE, 1.0F)) {
                fixture.data.setMode(PillarmanMode.NONE, fixture.player);
                helper.assertTrue(!fixture.press(), "A non-Wind user bypassed the mode gate");
                // These two donor actions require WIND but have no additional stage threshold.
                fixture.data.setEvolutionStage(1, fixture.player);
                fixture.data.setMode(PillarmanMode.WIND, fixture.player);
                helper.assertTrue(fixture.press(), "A new stage requirement was imposed on the Wind action");
                fixture.data.setMode(PillarmanMode.HEAT, fixture.player);
                fixture.tick();
                fixture.assertStoppedWithWaves(0, "Losing Wind mode did not stop the hold");
                fixture.release();
                fixture.data.setMode(PillarmanMode.WIND, fixture.player);
                helper.assertTrue(fixture.press(), "Valid Wind hold could not restart");
                fixture.power.setPowerType(null);
                fixture.tick();
                fixture.assertStoppedWithWaves(0, "Removing Pillarman power did not stop the hold");
                fixture.release();
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private static final short KEY = 8;
        private final GameTestHelper helper;
        private final FakePlayer player;
        private final PlayerPower power;
        private final PillarmanData data;
        private final Ability ability;
        private final LivingComponentAction component;
        private HeldInputEntry held;

        private Fixture(GameTestHelper helper, String name, GameType mode, float energy) {
            this.helper = helper;
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PillarStored"));
            player.setGameMode(mode);
            player.setNoGravity(true);
            player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            helper.getLevel().addNewPlayer(player);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, player);
            data.setMode(PillarmanMode.WIND, player);
            data.setEnergy(player, energy);
            ability = power.getAbility(name);
            var registered = DIVINE.equals(name) ? PillarmanPowerType.PILLAR_MAN_DIVINE_SANDSTORM.get()
                    : PillarmanPowerType.PILLAR_MAN_ATMOSPHERIC_RIFT.get();
            helper.assertTrue(ability != null && ability.abilityType == registered,
                    "Fixture did not resolve the registered Wind ability " + name);
            component = LivingComponentAction.getComponent(player);
        }

        private boolean press() {
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            if (!AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.HOLD)) {
                return false;
            }
            held = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            return held != null && held.action == component.getAction();
        }

        private void charge() {
            helper.assertTrue(press(), "Registered Wind HOLD was not admitted");
            for (int tick = 0; tick < 40; tick++) {
                tick();
            }
            EntityActionInstance action = component.getAction();
            helper.assertTrue(action != null && action.getPhase() == ActionPhase.PERFORM,
                    "Real forty-tick charge did not reach perform");
            helper.assertTrue(waves().isEmpty(), "A wave was emitted before the first perform tick");
        }

        private void tick() {
            // Isolate action costs from passive energy drain and projectile world travel.
            component.tick();
        }

        private void assertEnergy(float expected) {
            helper.assertTrue(Float.compare(data.getEnergy(), expected) == 0,
                    "Stored energy changed: expected " + expected + ", got " + data.getEnergy());
        }

        private List<PillarmanDivineSandstormEntity> waves() {
            return helper.getLevel().getEntitiesOfClass(PillarmanDivineSandstormEntity.class,
                    player.getBoundingBox().inflate(32), wave -> wave.getOwner() == player);
        }

        private void assertStoppedWithWaves(int expectedWaves, String message) {
            helper.assertTrue(component.getAction() == null && waves().size() == expectedWaves,
                    message + ": active=" + (component.getAction() != null) + ", waves=" + waves().size());
        }

        private void release() {
            if (held != null) {
                helper.assertTrue(AbilityInput.keyReleaseFromNetwork(KEY, player, held.generation)
                                == AbilityInput.ReleaseResult.RELEASED, "Registered HOLD release was rejected");
                held = null;
            }
            tick();
            helper.assertTrue(!power.hasHeldInput(), "Released Wind input remained held");
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, player);
            component.setAction(null, player, SyncType.NO_SYNC);
            waves().forEach(PillarmanDivineSandstormEntity::discard);
            player.discard();
        }
    }
}
