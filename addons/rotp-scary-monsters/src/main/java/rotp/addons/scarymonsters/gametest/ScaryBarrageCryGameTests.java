package rotp.addons.scarymonsters.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ability.ScaryMonstersBarrageAbility;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 example_stand_barrage registered BARRAGE_VOICE_RANDOM with the one-argument standSound (playInArmsOnly
 * true), so an arms-only Scary Monsters still cried through its barrage.
 */
@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryBarrageCryGameTests {
    private ScaryBarrageCryGameTests() {}

    @GameTest(template = "empty", batch = "scary_barrage_cry", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void barrageCriesWithTheArmsOnly(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> barrageCriesWithTheArmsOnlyBody(h));
    }

    private static void barrageCriesWithTheArmsOnlyBody(GameTestHelper helper) {
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ScaryBarrageCry"));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        user.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Scary Monsters user");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Scary Monsters");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Scary Monsters could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            Ability ability = power.getAbility("example_stand_barrage");
            helper.assertTrue(ability instanceof ScaryMonstersBarrageAbility,
                    "The registered example_stand_barrage is not the Scary Monsters barrage: " + ability);
            EntityActionInstance barrage = ((EntityActionType) ability).createActionObj();
            helper.assertTrue(playsBarrageCry(barrage, stand), "Setup: the fully summoned Stand does not cry");
            stand.setArmsOnlyMode(true, true);
            helper.assertTrue(stand.isArmsOnlyMode(), "Setup: the Stand did not switch to its arms only");
            helper.assertTrue(playsBarrageCry(barrage, stand),
                    "1.16 played the barrage cry with the arms only (one-argument standSound); the port stayed silent");
        }
        finally {
            if (power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    private static boolean playsBarrageCry(EntityActionInstance barrage, StandEntity stand) {
        try {
            Method method = StandEntityBarrageAbility.StandEntityBarrage.class
                    .getDeclaredMethod("playsBarrageCry", Level.class, StandEntity.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(barrage, stand.level(), stand);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The core barrage cry check is not reachable", e);
        }
    }
}
