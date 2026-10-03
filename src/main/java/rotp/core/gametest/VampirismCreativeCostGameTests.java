package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismCreativeCostGameTests {
    private static final String[] ABILITIES = { "vampirism_claw_lacerate", "vampirism_dark_aura",
            "vampirism_zombie_summon", "vampirism_space_ripper_stingy_eyes", "vampirism_freeze" };
    private static final float[] COSTS = { 45, 25, 100, 20, 0.45F };

    private VampirismCreativeCostGameTests() {}

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void creativeZeroBloodPassesOrdinaryActionGates(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0)) {
            List<String> rejected = new ArrayList<>();
            for (String name : ABILITIES) {
                if (!fixture.ability(name).checkSpecificConditions(fixture.power).isPositive()) {
                    rejected.add(name);
                }
            }
            helper.assertTrue(rejected.isEmpty(), "Creative zero blood rejected registered abilities: " + rejected);
            helper.assertTrue(!fixture.data.hasBlood(fixture.player, 1),
                    "Raw blood accounting must retain explicit stored-resource checks for addons");
            fixture.assertBlood(0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void survivalThresholdsAndNonResourceGatesRemain(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL, 0)) {
            for (int i = 0; i < ABILITIES.length; i++) {
                var ability = fixture.ability(ABILITIES[i]);
                fixture.blood(0);
                helper.assertTrue(!ability.checkSpecificConditions(fixture.power).isPositive(),
                        "Survival zero blood bypassed " + ABILITIES[i]);
                fixture.blood(COSTS[i]);
                helper.assertTrue(ability.checkSpecificConditions(fixture.power).isPositive(),
                        "Survival exact resource threshold failed " + ABILITIES[i]);
                fixture.assertBlood(COSTS[i]);
            }
            fixture.player.setGameMode(GameType.CREATIVE);
            fixture.blood(0);
            fixture.data.setVampireFullPower(false, fixture.player);
            helper.assertTrue(!fixture.ability(ABILITIES[0]).checkSpecificConditions(fixture.power).isPositive(),
                    "Creative bypassed the full-power unlock");
            fixture.data.setVampireFullPower(true, fixture.player);
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            helper.assertTrue(!fixture.ability(ABILITIES[0]).checkSpecificConditions(fixture.power).isPositive(),
                    "Creative bypassed the empty-hand requirement");
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fixture.data.setAbilityCooldown(ABILITIES[0], 20, 20);
            helper.assertTrue(!fixture.ability(ABILITIES[0]).checkSpecificConditions(fixture.power).isPositive(),
                    "Resource exemption bypassed an existing cooldown");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void creativeEmptyBloodStillPerformsDarkAura(GameTestHelper helper) {
        aura(helper, GameType.CREATIVE, 0, 0);
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void creativeDarkAuraDoesNotDebitStoredBlood(GameTestHelper helper) {
        aura(helper, GameType.CREATIVE, 50, 50);
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void survivalDarkAuraStillPaysBeforeProducingEffect(GameTestHelper helper) {
        aura(helper, GameType.SURVIVAL, 25, 0);
    }

    private static void aura(GameTestHelper helper, GameType mode, float initial, float expected) {
        try (Fixture fixture = new Fixture(helper, mode, initial)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 2));
            fixture.start("vampirism_dark_aura");
            fixture.tick();
            helper.assertTrue(fixture.player.getMainHandItem().is(Items.WITHER_ROSE)
                            && fixture.player.getMainHandItem().getCount() == 2,
                    "Registered Dark Aura did not perform its existing poppy transformation");
            fixture.assertBlood(expected);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void creativeEyesContinueAtZeroBlood(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0)) {
            var action = fixture.start("vampirism_space_ripper_stingy_eyes");
            for (int i = 0; i < 4; i++) {
                fixture.tick();
                helper.assertTrue(!action.isOver(), "Creative firing stopped from the Survival resource cost");
                fixture.assertBlood(0);
            }
            helper.assertTrue(fixture.lasers().size() == 2, "Registered firing did not create both owned lasers");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void survivalEyesPayPerFiringTickAndStopAtExhaustion(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL, 40)) {
            var action = fixture.start("vampirism_space_ripper_stingy_eyes");
            fixture.tick();
            fixture.assertBlood(20);
            fixture.tick();
            fixture.assertBlood(0);
            fixture.tick();
            helper.assertTrue(action.isOver(), "Survival firing continued after exhaustion");
            fixture.assertBlood(0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "vampirism_creative_cost")
    public static void creativeFreezeKeepsItsExistingFreeHeldTicks(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0)) {
            var action = fixture.start("vampirism_freeze");
            for (int i = 0; i < 4; i++) {
                fixture.tick();
                helper.assertTrue(!action.isOver(), "Shared gate cancelled zero-blood Creative Freeze");
                fixture.assertBlood(0);
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Difficulty oldDifficulty;
        private final FakePlayer player;
        private final PlayerPower power;
        private final VampirismData data;
        private final LivingComponentAction component;

        private Fixture(GameTestHelper helper, GameType mode, float blood) {
            this.helper = helper;
            oldDifficulty = helper.getLevel().getDifficulty();
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "VampireCost"));
            player.setGameMode(mode);
            player.setNoGravity(true);
            player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
            helper.getLevel().addNewPlayer(player);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, player);
            player.setHealth(player.getMaxHealth());
            blood(blood);
            component = LivingComponentAction.getComponent(player);
        }

        private VampirismActionAbility ability(String name) {
            var ability = power.getAbility(name);
            helper.assertTrue(ability instanceof VampirismActionAbility, "Missing registered ability " + name);
            return (VampirismActionAbility) ability;
        }

        private EntityActionInstance start(String name) {
            // Enter the production perform phase; windup input/timing is outside this resource regression.
            var action = ability(name).initActionOnAbilityUse(helper.getLevel(), player, player, null);
            action.setPhaseStart(ActionPhase.PERFORM);
            component.setAction(action, player, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "Registered action was rejected " + name);
            return action;
        }

        private void tick() {
            component.tick();
        }

        private void blood(float value) {
            VampirismState.get(player).blood().setCurrent(value);
            data.setBloodLevel(value);
        }

        private void assertBlood(float expected) {
            helper.assertTrue(Math.abs(VampirismState.get(player).blood().current() - expected) < 0.001F
                            && Math.abs(data.getBloodLevel() - expected) < 0.001F,
                    "Incorrect action blood debit: expected " + expected + ", actual "
                            + VampirismState.get(player).blood().current());
        }

        private List<SpaceRipperStingyEyesEntity> lasers() {
            return helper.getLevel().getEntitiesOfClass(SpaceRipperStingyEyesEntity.class,
                    player.getBoundingBox().inflate(32), laser -> laser.getOwner() == player);
        }

        @Override
        public void close() {
            component.setAction(null, player, SyncType.NO_SYNC);
            lasers().forEach(SpaceRipperStingyEyesEntity::discard);
            player.discard();
            helper.getLevel().getServer().setDifficulty(oldDifficulty, true);
        }
    }
}
