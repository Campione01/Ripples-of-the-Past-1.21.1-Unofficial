package rotp.addons.purplehaze.ability;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.CapsuleProjectileEntity;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 PurpleHazeCapsuleShot: standEntity.shootProjectile(capsule, 1.0F, 0.25F) spread the capsule by
 * getProjectileInaccuracy(0.25) of the Stand's precision: 1.5 at Purple Haze's 4, none from 10.
 */
@GameTestHolder(PurpleHazeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurpleHazeCapsuleSpreadGameTests {
    private PurpleHazeCapsuleSpreadGameTests() {}

    @GameTest(template = "empty", batch = "ph_capsule_spread", timeoutTicks = 40)
    public static void capsuleShotsSpreadByTheStandsPrecisionAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PHCapsuleSpread"));
        StandPower power = null;
        try {
            Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            owner.setNoGravity(true);
            level.addNewPlayer(owner);
            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The fake player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(owner, power), "Could not summon Purple Haze");
            PurpleHazeEntity stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing the Purple Haze Stand");
            stand.summonLockTicks = 0;
            EntityActionType shot = (EntityActionType) power.getAbility(PurpleHazeAddon.CAPSULE_SHOT_NAME);
            helper.assertTrue(shot != null, "Purple Haze has no capsule shot");
            // precision, the spread 1.16 gave
            double[][] rows = { { 10.0D, 0.0D }, { 4.0D, 1.5D } };
            for (double[] row : rows) {
                double precision = row[0];
                double spread = row[1];
                owner.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
                StandEntity.updateStandStatAttributes(stand, owner);
                stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
                helper.assertTrue(Math.abs(stand.getPrecision() - precision) < 1.0E-9D,
                        "Fixture: Purple Haze's precision is " + stand.getPrecision() + ", not " + precision);
                for (int i = 0; i < 2; i++) {
                    stand.setYRot(-40.0F);
                    stand.setXRot(15.0F);
                    EntityActionInstance action = shot.initActionOnAbilityUse(level, owner, stand, null);
                    LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
                    action.actionPerformStart();
                    LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                    List<CapsuleProjectileEntity> capsules = level.getEntitiesOfClass(CapsuleProjectileEntity.class,
                            new AABB(owner.blockPosition()).inflate(8.0D));
                    helper.assertTrue(capsules.size() == 1, "Fixture: " + capsules.size() + " capsules were shot");
                    CapsuleProjectileEntity capsule = capsules.get(0);
                    Vec3 along = Vec3.directionFromRotation(stand.getXRot(), stand.getYRot());
                    double off = capsule.getDeltaMovement().normalize().cross(along).length();
                    capsule.discard();
                    boolean asIn116 = spread == 0.0D ? off < 1.0E-6D : off > 1.0E-6D && off <= maxOffAim(spread);
                    helper.assertTrue(asIn116, "1.16 spread Purple Haze's capsule at a precision of " + precision
                            + " by " + spread + " (getProjectileInaccuracy(0.25)); it flies " + off + " off its aim");
                }
            }
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.discard();
        }
        helper.succeed();
    }

    // the sine of the most vanilla's shot noise (0.0172275 a spread point on each axis) turns a unit direction by
    private static double maxOffAim(double spread) {
        double noise = 0.0172275D * spread * Math.sqrt(3.0D);
        return noise / (1.0D - noise) + 1.0E-6D;
    }
}
