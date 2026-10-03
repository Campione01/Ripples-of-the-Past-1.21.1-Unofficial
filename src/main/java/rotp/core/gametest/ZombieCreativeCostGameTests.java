package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.impl.powers.zombie.ZombiePowerType;
import rotp.core.impl.powers.zombie.abilities.ZombieClawLacerateAbility;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieCreativeCostGameTests {
    private ZombieCreativeCostGameTests() {}

    @GameTest(template = "empty", batch = "zombie_creative_cost")
    public static void creativeZeroEnergyClawStartsAndKeepsDonorNoDebit(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0.0F)) {
            helper.assertTrue(!fixture.data.hasEnergy(60.0F), "Raw ZombieData energy gate was bypassed");
            helper.assertTrue(fixture.press(), "Creative zero energy rejected the registered Zombie Claw");
            fixture.performWithoutDebit(0.0F);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "zombie_creative_cost")
    public static void survivalClawKeepsSixtyEnergyGateWithoutSpendingIt(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL, 0.0F)) {
            for (float insufficient : new float[] { 0.0F, 59.0F }) {
                fixture.data.setEnergy(fixture.player, insufficient);
                helper.assertTrue(!fixture.press(), "Survival energy " + insufficient + " admitted Zombie Claw");
                helper.assertTrue(fixture.component.getAction() == null, "Rejected press installed an action");
                fixture.assertEnergy(insufficient);
            }
            fixture.data.setEnergy(fixture.player, 60.0F);
            helper.assertTrue(fixture.press(), "Survival exact sixty-energy threshold rejected Zombie Claw");
            fixture.performWithoutDebit(60.0F);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "zombie_creative_cost")
    public static void creativeAdmissionStillRequiresEmptyMainHandAndNoDisguise(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0.0F)) {
            for (float energy : new float[] { 0.0F, 60.0F }) {
                fixture.data.setEnergy(fixture.player, energy);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                helper.assertTrue(!fixture.press(), "Creative occupied main hand bypassed its admission gate");
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.data.setDisguiseEnabled(true, fixture.player);
                helper.assertTrue(!fixture.press(), "Creative disguise bypassed its admission gate");
                fixture.data.setDisguiseEnabled(false, fixture.player);
                helper.assertTrue(fixture.component.getAction() == null, "Rejected hand/disguise press installed an action");
                fixture.assertEnergy(energy);
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private static final short KEY = 6;
        private final GameTestHelper helper;
        private final FakePlayer player;
        private final Cow target;
        private final PlayerPower power;
        private final ZombieData data;
        private final Ability ability;
        private final LivingComponentAction component;

        private Fixture(GameTestHelper helper, GameType mode, float energy) {
            this.helper = helper;
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ZombieCost"));
            player.setGameMode(mode);
            player.setNoGravity(true);
            player.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.0D)));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.getLevel().addNewPlayer(player);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.ZOMBIE.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.ZOMBIE).orElseThrow();
            data.setEnergy(player, energy);
            ability = power.getAbility("zombie_claw_lacerate");
            helper.assertTrue(ability instanceof ZombieClawLacerateAbility
                            && ability.abilityType == ZombiePowerType.ZOMBIE_CLAW_LACERATE.get(),
                    "Fixture did not resolve the registered Zombie Claw ability");
            component = LivingComponentAction.getComponent(player);
            target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 2));
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
            component.entityAim.setTarget(new ActionTarget(target));
        }

        private boolean press() {
            // Evaluate each synchronous fixture control without ticking unrelated passive energy loss.
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            if (!AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.CLICK)) {
                return false;
            }
            var input = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.CLICK,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            return input != null && input.action == component.getAction();
        }

        private void performWithoutDebit(float energy) {
            assertEnergy(energy);
            for (int tick = 0; tick < 8; tick++) {
                component.tick();
                assertEnergy(energy);
            }
            helper.assertTrue(target.getHealth() < 100.0F, "Registered Zombie Claw never delivered its tick-five hit");
            helper.assertTrue(component.getAction() == null, "Zombie Claw did not finish its eight-tick action");
            AbilityInput.keyRelease(KEY, player);
        }

        private void assertEnergy(float expected) {
            helper.assertTrue(Float.compare(data.getEnergy(), expected) == 0,
                    "Zombie Claw energy changed: expected " + expected + ", got " + data.getEnergy());
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, player);
            component.setAction(null, player, SyncType.NO_SYNC);
            target.discard();
            player.discard();
        }
    }
}
