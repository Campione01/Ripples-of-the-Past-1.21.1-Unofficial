package rotp.addons.madeinheaven.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

/**
 * A client's copy of a synced action is of its power type's base-moveset instance of the ability
 * (EntityActionType#encodeAbility sends it without its user), not of the user's own one that the client's moveset, keys
 * and HUD hold. 1.16's client asked the same checks of its synced task as the server of its own, so a hold-only dash or
 * aura that lets every press but its own replace it must tell its own ability from the others on the client as on the
 * server: each row builds the action once of the user's own ability, as the server holds it, and once of the
 * base-moveset one, as the client holds it. The client predicts a press as buffered or not, and draws its slot as queued
 * or not, from this answer.
 */
@GameTestHolder(LightSpeedDashGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MadeInHeavenClientCopyGameTests {
    private MadeInHeavenClientCopyGameTests() {}

    /*
     * 1.16 StandEntityAction.isCancelable: a hold-only task with no recovery gave way to any other Stand action at once
     * (mih_dash and tpaura were holdType Stand actions); the same press of itself found the task still held.
     */
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE,
            batch = "mihClientCopy", timeoutTicks = 40)
    public static void dashAndAuraHoldTheirOwnPressOnTheirUsersClientAsOnTheServerAsIn116(GameTestHelper helper) {
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        user.setNoAi(true);
        user.setNoGravity(true);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(MadeInHeavenAddon.MADE_IN_HEAVEN.get());
            Ability punch = power.getAbility("punch");
            helper.assertTrue(punch instanceof EntityActionType, "setup: the moveset has no punch");
            for (String name : new String[] {MadeInHeavenAddon.DASH_NAME, MadeInHeavenAddon.TIME_AURA_NAME}) {
                Ability own = power.getAbility(name);
                Ability base = power.getPowerType().getBaseMoveset().getAbility(name);
                helper.assertTrue(own instanceof EntityActionType && base instanceof EntityActionType && base != own
                                && base.getAbilityId().equals(own.getAbilityId()),
                        "setup: the power type's base-moveset " + name + " is not another instance of the user's own: "
                                + base);
                for (Ability of : new Ability[] {own, base}) {
                    String side = of == own ? "the server" : "its user's client";
                    EntityActionInstance action = ((EntityActionType) of).createActionObj();
                    helper.assertTrue(action.ability == of && action.isOfAbility(own),
                            "setup: " + name + " as " + side + " holds it is not of the ability it was built of");
                    helper.assertTrue(!action.canBeCancelledInto((EntityActionType) own)
                                    && action.canBeCancelledInto((EntityActionType) punch),
                            "1.16 let every Stand action but its own replace the held " + name + " at once; as " + side
                                    + " holds it its own press gives way " + action.canBeCancelledInto((EntityActionType) own)
                                    + ", a punch's " + action.canBeCancelledInto((EntityActionType) punch));
                }
            }
        }
        finally {
            user.discard();
        }
        helper.succeed();
    }
}
