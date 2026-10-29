package rotp.addons.mandom.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.mandom.MandomAddon;
import rotp.addons.mandom.MandomRollbackGate;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 time_rewind (003 InitStands.java:40) had no builder cooldown; TimeRewind.standPerform :75 set
 * RewindConfig.getSecond() * 20 (120 ticks) with setCooldownTimer, which neither Resolve nor Creative touched, and
 * Action.getCooldownTechnical :369-371 was the builder's 0, so a Stand-bearing mob waited only 10 ticks after it.
 */
@GameTestHolder(MandomAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MandomCooldownGameTests {
    private MandomCooldownGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mandom", batch = "mandom_cooldown", timeoutTicks = 20)
    public static void theRewindCooldownIsSetWholeInEveryModeWithNoTechnicalPartAsIn116(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MandomCooldown"));
        List<String> wrong = new ArrayList<>();
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            player.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Mandom user");
            player.setGameMode(GameType.SURVIVAL);
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(MandomAddon.MANDOM.get());
            Ability rewind = power.getAbility("time_rewind");
            helper.assertTrue(rewind instanceof TimeRewindAbility, "setup: Mandom has no time_rewind: " + rewind);
            int expected = MandomRollbackGate.COOLDOWN_TICKS;
            helper.assertTrue(expected == 120, "setup: 1.16 RewindConfig's default was 6 s, the gate says " + expected);
            int technical = rewind.getCooldownTechnical(power);
            if (technical != 0) {
                wrong.add("time_rewind reports technical " + technical + ", the donor's 0 (no builder cooldown)");
            }
            check(power, rewind, expected, "in Survival", wrong);
            player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
            check(power, rewind, expected, "in Resolve", wrong);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(power.isUserCreative(), "setup: the Mandom user is not in Creative");
            check(power, rewind, expected, "as a Creative user in Resolve", wrong);
            player.removeEffect(ModStatusEffects.RESOLVE);
            check(power, rewind, expected, "as a Creative user", wrong);
        }
        finally {
            player.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Mandom setCooldownTimer(time_rewind, 120): " + String.join("; ", wrong));
        helper.succeed();
    }

    private static void check(StandPower power, Ability rewind, int expected, String when, List<String> wrong) {
        power.setAbilityCooldown(rewind.name(), 0);
        int cooldown = rewind.getCooldown(power, -1);
        rewind.setCooldownOnUse(power);
        int set = power.getAbilityCooldown(rewind.name());
        if (cooldown != expected || set != expected) {
            wrong.add("time_rewind gets " + cooldown + " ticks, set " + set + ", " + when + ", the donor's " + expected);
        }
    }
}
