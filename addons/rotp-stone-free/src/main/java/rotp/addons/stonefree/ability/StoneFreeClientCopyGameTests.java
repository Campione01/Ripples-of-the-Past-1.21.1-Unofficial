package rotp.addons.stonefree.ability;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * A client's copy of a synced action is of its power type's base-moveset instance of the ability
 * (EntityActionType#encodeAbility sends it without its user), not of the user's own one that the client's moveset, keys
 * and HUD hold. 1.16's HUD asked StoneFreeStringBind.replaceAction of the Stand's synced task as the server asked it of
 * its own: the Stand running the bind itself is not busy with another task. The row builds the running bind once of the
 * user's own ability, as the server holds it, and once of the base-moveset one, as the client holds it.
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeClientCopyGameTests {
    private StoneFreeClientCopyGameTests() {}

    // its own batch: the extended punch's sound tests count the sounds of every plot within 15 blocks
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 40,
            batch = "sfClientCopy")
    public static void theStandRunningTheBindIsNotBusyWithAnotherTaskOnItsUsersClientAsOnTheServerAsIn116(
            GameTestHelper helper) {
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        user.setNoAi(true);
        user.setNoGravity(true);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(StoneFreeAddon.STONE_FREE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(StoneFreeAddon.STONE_FREE.get().summon(user, power), "setup: could not summon Stone Free");
            StoneFreeStandEntity stand = StoneFreeServices.controller(user);
            helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "setup: Stone Free is not out in full");
            stand.summonLockTicks = 0;
            LivingComponentAction standSlot = LivingComponentAction.getComponent(stand);

            String name = StoneFreeAddon.STRING_BIND_NAME;
            Ability own = power.getAbility(name);
            Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
            helper.assertTrue(own instanceof StoneFreeStringAbility && base instanceof StoneFreeStringAbility
                            && base != own && base.getAbilityId().equals(own.getAbilityId()),
                    "setup: the power type's base-moveset " + name + " is not another instance of the user's own: " + base);
            for (Ability of : new Ability[] {own, base}) {
                String side = of == own ? "the server" : "its user's client";
                EntityActionType type = (EntityActionType) of;
                EntityActionInstance bind = type.createActionObj();
                type.initActionFromConfig(bind, helper.getLevel(), user, stand);
                bind.setStartingPhase();
                standSlot.setAction(null, SyncType.NO_SYNC);
                standSlot.setAction(bind, user, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == bind,
                        "setup: the bind as " + side + " holds it did not become the Stand's task");
                helper.assertTrue(!StoneFreeStringAbility.busyWithOtherTask(stand, own),
                        "1.16 StoneFreeStringBind.replaceAction found a Stand running the bind itself not busy with"
                                + " another task; the Stand running the bind as " + side + " holds it is busy");
                Ability drawn = own.replaceWithSubAbility(power, resolve(power));
                helper.assertTrue(drawn == own,
                        "1.16 drew the Stand's own bind on the bind's slot while the Stand ran it; the slot draws "
                                + (drawn != null ? drawn.name() : null) + " with the bind as " + side + " holds it");
            }
            // a task of another ability still counts as busy
            standSlot.setAction(null, SyncType.NO_SYNC);
            Ability block = power.getAbility("guard");
            helper.assertTrue(block instanceof EntityActionType, "setup: Stone Free has no guard");
            EntityActionInstance guard = ((EntityActionType) block).createActionObj();
            ((EntityActionType) block).initActionFromConfig(guard, helper.getLevel(), user, stand);
            guard.setStartingPhase();
            standSlot.setAction(guard, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == guard && StoneFreeStringAbility.busyWithOtherTask(stand, own),
                    "1.16 found a Stand running its guard busy with another task than the bind");
            standSlot.setAction(null, SyncType.NO_SYNC);
        }
        finally {
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static rotp.core.powersystem.ability.condition.AvailableAbilities resolve(Power<?> power) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return power.updateAvailableMoves();
    }
}
