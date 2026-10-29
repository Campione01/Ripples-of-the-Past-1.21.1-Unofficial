package rotp.addons.tusk;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 gave Tusk Act 4 its stopped-time awareness through a mixin (056 mixin/TimeStopHandlerMixin
 * canPlayerSeeInStoppedTime, act == 3), no Action: nothing of it went through PowerBaseImpl.onClickAction, so it never
 * ended the power's held action (stopHeldAction(false)) nor was refused by one.
 */
@GameTestHolder(TuskAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TuskHeldPressGameTests {
    private static final String BARRAGE = "tusk_barrage";
    private static final String ACT_4_AWARENESS = "tusk_act_4_time_stop_awareness";

    private TuskHeldPressGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void act4AwarenessLeavesAHeldBarrageAloneAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "tusk-held-press")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tusk user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(TuskAddon.TUSK_ACT_4.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && TuskAddon.TUSK_ACT_4.get().summon(user, power),
                    "Could not summon Tusk Act 4");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            power.tick();

            Ability barrageAbility = power.getAbility(BARRAGE);
            helper.assertTrue(barrageAbility instanceof EntityActionAbility
                            && AbilityInput.withConditionCheck(barrageAbility, user),
                    "Setup: Tusk Act 4's barrage was refused");
            AbilityInput.keyPress((short) 41, barrageAbility, user, new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD, 0, BufferingState.clickOnly(), barrageAbility.getAbilityId());
            EntityActionInstance barrage = stand.getCurStandAction();
            EntityActionAbility barrageAction = (EntityActionAbility) barrageAbility;
            helper.assertTrue(barrage != null && barrage.ability == barrageAbility,
                    "Setup: the held press did not start the barrage: " + barrage);
            for (int tick = 0; tick < 20 && !barrageAction.isActionHeld(barrage) && !barrage.isOver(); tick++) {
                barrage._tickAction();
            }
            helper.assertTrue(isHeld(stand, barrage),
                    "Setup: the barrage is not held (phase " + barrage.getPhase() + ")");

            Ability awareness = power.getAbility(ACT_4_AWARENESS);
            helper.assertTrue(awareness != null && AbilityInput.withConditionCheck(awareness, user),
                    "1.16 Act 4 awareness was no Action, so a held barrage did not refuse it");
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress((short) 42, awareness, user, new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.CLICK, 0, press, awareness.getAbilityId());
            AbilityInput.keyRelease((short) 42, user);
            barrage._tickAction();
            helper.assertTrue(press.isActionSuccess && isHeld(stand, barrage),
                    "1.16 Act 4 awareness was no Action and left the held barrage alone: pressed="
                            + press.isActionSuccess + ", barrage phase " + barrage.getPhase()
                            + ", over " + barrage.isOver());
            helper.succeed();
        }
        finally {
            AbilityInput.keyRelease((short) 41, user);
            if (power != null && power.isSummoned()) {
                TuskAddon.TUSK_ACT_4.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static boolean isHeld(StandEntity stand, EntityActionInstance action) {
        return stand.getCurStandAction() == action && !action.isOver()
                && ((EntityActionAbility) action.ability).isActionHeld(action);
    }
}
