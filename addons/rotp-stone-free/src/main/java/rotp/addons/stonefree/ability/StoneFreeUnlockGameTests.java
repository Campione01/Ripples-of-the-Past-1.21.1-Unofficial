package rotp.addons.stonefree.ability;

import java.lang.reflect.Field;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeGameEvents;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

/**
 * When 1.16 unlocked two Stone Free moves (017 InitStands, StandType.unlockNewActions, StandAction.canBeUnlocked): the
 * string punch, the heavy punch's finisher variation, at Resolve 1 (:160, :162), and with it the finisher meter
 * (StandUtil.isFinisherMechanicUnlocked); the Mobius Strip (:176 noResolveUnlock) never by Resolve, only by
 * StoneFreeStandType.tickUser:187-190 (Resolve 4, a C-Moon inversion, 10 health or less, 90 strings or fewer) or for a
 * creative user.
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeUnlockGameTests {
    private StoneFreeUnlockGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 40)
    public static void stringPunchFinisherUnlocksAtResolveOneAsIn116(GameTestHelper helper) {
        Cow user = user(helper);
        StandPower power = stoneFree(helper, user);
        try {
            Ability heavy = power.getAbility("heavy_punch");
            Ability stringPunch = power.getAbility(StoneFreeAddon.STRING_PUNCH_NAME);
            helper.assertTrue(heavy != null && stringPunch != null && stringPunch.isStandFinisherOf != null,
                    "setup: Stone Free has no heavy punch with the string punch as its finisher");
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(user, power), "setup: could not summon Stone Free");
            StoneFreeStandEntity stand = StoneFreeServices.controller(user);
            helper.assertTrue(stand != null, "setup: Stone Free is not out");
            stand.setFinisherMeter(1.0F);

            helper.assertTrue(!stringPunch.isAbilityUnlocked(power),
                    "1.16 locked the string punch below Resolve 1 (InitStands:160 resolveLevelToUnlock(1)); the port"
                            + " unlocked it at Resolve 0");
            helper.assertTrue(!StandEntity.isFinisherMechanicUnlocked(power) && stand.getFinisherMeter() == 0.0F,
                    "1.16 kept the finisher meter at 0 below Resolve 1 with its finisher locked; at Resolve 0 the port's"
                            + " meter reads " + stand.getFinisherMeter());
            AbilityConditionCheck key = heavyKey(helper, power);
            helper.assertTrue(key.ability == heavy,
                    "1.16 kept the heavy key on the heavy punch below Resolve 1 at a full meter; at Resolve 0 it holds "
                            + key.ability.name());

            power.setResolveLevel(1);
            helper.assertTrue(stringPunch.isAbilityUnlocked(power),
                    "1.16 unlocked the string punch at Resolve 1; the port keeps it locked");
            helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(power) && stand.getFinisherMeter() == 1.0F,
                    "1.16 opened the finisher meter at Resolve 1; the port's meter reads " + stand.getFinisherMeter());
            key = heavyKey(helper, power);
            helper.assertTrue(key.ability == stringPunch,
                    "1.16 offered the string punch on the heavy key at Resolve 1 and a full meter; the key holds "
                            + key.ability.name());
        }
        finally {
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 40)
    public static void mobiusStripOpensOnlyByItsSpecialUnlockAsIn116(GameTestHelper helper) {
        Cow user = user(helper);
        StandPower power = stoneFree(helper, user);
        StandTypePersistentData data = power.getCurTypeData();
        Ability mobius = power.getAbility(StoneFreeAddon.USER_MOBIUS_STRIP_NAME);
        helper.assertTrue(data != null && mobius != null, "setup: Stone Free has no Mobius Strip");
        StoneFreeState state = StoneFreeState.get(user);
        helper.assertTrue(!state.mobiusMode(), "setup: Mobius mode is on before anything happened");
        for (int level = 0; level <= StandTypePersistentData.MAX_RESOLVE_LEVEL; level++) {
            power.setResolveLevel(level);
            data.unlockOriginalResolveSkills(power);
            helper.assertTrue(!data.isSkillUnlocked(StoneFreeAddon.USER_MOBIUS_STRIP_NAME)
                            && !mobius.isAbilityUnlocked(power),
                    "1.16 never unlocked the Mobius Strip by Resolve (InitStands:176 noResolveUnlock); the port"
                            + " unlocked it at Resolve " + level);
        }
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(!mobius.isAbilityUnlocked(power) && !state.mobiusMode(),
                        "Stone Free's user ticks at Resolve 4 without a C-Moon inversion unlocked the Mobius Strip or"
                                + " switched Mobius mode on");
                user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
                user.setHealth(10.0F);
                specialUnlockRefused(helper, user, power, state, 0, 0, "without a C-Moon inversion");
                specialUnlockRefused(helper, user, power, state, 1, 91, "with 91 strings placed");
                user.setHealth(10.5F);
                specialUnlockRefused(helper, user, power, state, 1, 0, "at 10.5 health");
                user.setHealth(10.0F);
                power.setResolveLevel(3);
                specialUnlockRefused(helper, user, power, state, 1, 0, "at Resolve 3");
                power.setResolveLevel(4);
                helper.assertTrue(StoneFreeGameEvents.tryMobiusSpecialUnlock(user, power, state, 1, 90),
                        "1.16 unlocked the Mobius Strip at Resolve 4 with a C-Moon inversion, 10 health and 90"
                                + " strings; the port did not");
                helper.assertTrue(data.isSkillUnlocked(StoneFreeAddon.USER_MOBIUS_STRIP_NAME)
                                && mobius.isAbilityUnlocked(power) && state.mobiusMode(),
                        "1.16's special unlock left the Mobius Strip usable and Mobius mode on; the port: skill "
                                + data.isSkillUnlocked(StoneFreeAddon.USER_MOBIUS_STRIP_NAME) + ", usable "
                                + mobius.isAbilityUnlocked(power) + ", mode " + state.mobiusMode());
            }
            finally {
                user.discard();
            }
            creativeUnlocks(helper);
            helper.succeed();
        });
    }

    private static void specialUnlockRefused(GameTestHelper helper, Cow user, StandPower power, StoneFreeState state,
            int inversion, int strings, String when) {
        boolean unlocked = StoneFreeGameEvents.tryMobiusSpecialUnlock(user, power, state, inversion, strings);
        helper.assertTrue(!unlocked && !power.getAbility(StoneFreeAddon.USER_MOBIUS_STRIP_NAME).isAbilityUnlocked(power)
                        && !state.mobiusMode(),
                "1.16's special unlock refused the Mobius Strip " + when + "; the port unlocked it");
    }

    // 1.16 StandAction.canBeUnlocked: a creative user got every action, the noResolveUnlock ones included, also one
    // who had the Stand in Survival first (StandType.onNewResolveLevel -> unlockNewActions).
    private static void creativeUnlocks(GameTestHelper helper) {
        Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("0c6b1f7e-3d52-4a8e-9b41-6e2f8a3c5d17"), "SFMobiusCreative"));
        try {
            player.getAbilities().instabuild = false;
            Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            player.moveTo(at.x, at.y, at.z);
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(StoneFreeAddon.STONE_FREE.get());
            power.setResolveLevel(0);
            helper.assertTrue(!power.getCurTypeData().isSkillUnlocked(StoneFreeAddon.USER_MOBIUS_STRIP_NAME),
                    "1.16 kept the Mobius Strip locked for a Survival player at Resolve 0; the port unlocked it");
            player.getAbilities().instabuild = true;
            power.setResolveLevel(1);
            helper.assertTrue(power.getCurTypeData().isSkillUnlocked(StoneFreeAddon.USER_MOBIUS_STRIP_NAME),
                    "1.16 unlocked the Mobius Strip for a creative user at a new Resolve level; the port did not");
            power.setStand(null);
        }
        finally {
            player.getAbilities().instabuild = false;
        }
    }

    private static Cow user(GameTestHelper helper) {
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        user.setNoAi(true);
        user.setNoGravity(true);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        return user;
    }

    private static StandPower stoneFree(GameTestHelper helper, LivingEntity user) {
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(StoneFreeAddon.STONE_FREE.get());
        power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
        power.setResolveLevel(0);
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(power.getHighestResolveLevel() == 0, "setup: the user starts above Resolve 0");
        return power;
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static AbilityConditionCheck heavyKey(GameTestHelper helper, StandPower power) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        AbilityConditionCheck key = power.updateAvailableMoves().getContextVariationContainer("heavy_punch");
        helper.assertTrue(key != null, "The heavy key holds nothing");
        return key;
    }
}
