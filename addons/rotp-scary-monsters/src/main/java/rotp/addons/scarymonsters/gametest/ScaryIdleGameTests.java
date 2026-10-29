package rotp.addons.scarymonsters.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

/**
 * 1.16 GeoExampleStandEntity.offsetDefault, StandRelativeOffset.withYOffset(0, 0, 0), kept the idle Stand at its
 * user's feet, and its block's standOffsetFromUser(0, 0.3) took that default y. Its constructor set doOffsetLerp false
 * (:60), so a client saw it at a new offset on the next tick, where the core's Stands glide there over four ticks.
 */
@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryIdleGameTests {
    private ScaryIdleGameTests() {}

    @GameTest(template = "empty", batch = "scary_idle", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void theIdleStandStandsAtItsUsersFeetAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> theIdleStandStandsAtItsUsersFeetBody(h));
    }

    private static void theIdleStandStandsAtItsUsersFeetBody(GameTestHelper helper) {
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ScaryIdle"));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        user.setYHeadRot(0.0F);
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
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
                    "setup: the summoned Scary Monsters is not an idle full Stand following its user");
            helper.assertTrue(stand.Y_OFFSET == 0.0D,
                    "1.16 Scary Monsters' default y, which its tasks used, was 0: Y_OFFSET " + stand.Y_OFFSET);
            stand.updatePosition(user);
            Vec3 offset = stand.position().subtract(user.position());
            helper.assertTrue(offset.lengthSqr() < 1.0E-8D,
                    "1.16 put the idle Scary Monsters at its user's feet at view yaw 0, got " + offset + " (offset "
                            + stand.offsetFromUser.getRelativeOffset() + ")");
        }
        finally {
            if (power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scary_idle", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void theStandStandsAtItsNewOffsetOnTheNextTickAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> theStandStandsAtItsNewOffsetOnTheNextTickBody(h));
    }

    private static void theStandStandsAtItsNewOffsetOnTheNextTickBody(GameTestHelper helper) {
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ScaryOffset"));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        user.setYHeadRot(0.0F);
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
            StandOffsetFromUser offset = stand.offsetFromUser;
            helper.assertTrue(!offset.lerps(),
                    "1.16 GeoExampleStandEntity set doOffsetLerp false: the Scary Monsters offset must not lerp");
            // a tick of its own, so the change takes its start from here
            stand.tickCount++;
            Vec3 before = offset.getAbsoluteOffset(user, false);
            offset.setOffset(new Vec3(1.5D, 0.4D, 2.5D), StandOffsetFromUser.Rotations.HEAD);
            Vec3 after = offset.getAbsoluteOffset(user, false);
            helper.assertTrue(after.distanceTo(before) > 1.0D, "setup: the new offset is too close to the old one");
            stand.tickCount++;
            Vec3 nextTick = offset.getAbsoluteOffset(user, true);
            helper.assertTrue(nextTick.distanceTo(after) < 1.0E-9D,
                    "1.16 put Scary Monsters at its new offset on the next tick: expected " + after + ", it is at "
                            + nextTick + " (from " + before + ")");
        }
        finally {
            if (power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }
}
