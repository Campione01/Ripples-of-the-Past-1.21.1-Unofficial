package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerStandBodyGameTests {
    private GerStandBodyGameTests() {}

    /**
     * Donor GoldExprienceRequiemEntity: the main arm is always the right one,
     * and the eye sits 0.7 above the standing eye of a 0.35 x 1.05 body.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_stand_body")
    public static void standBodyKeepsDonorArmEyeHeightAndSize(GameTestHelper helper) {
        new Body(helper).start();
    }

    private static final class Body extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        Body(GameTestHelper helper) {
            super(helper, "stand/body", 150);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor(scene.origin, 0, user -> user.setMainArm(HumanoidArm.LEFT));
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    float eyeHeight = 1.05F * 0.85F + 0.7F;
                    scene.log("mainArm=" + actor.stand().getMainArm() + " width=" + actor.stand().getBbWidth()
                            + " height=" + actor.stand().getBbHeight() + " eye=" + actor.stand().getEyeHeight());
                    helper.assertTrue(actor.user().getMainArm() == HumanoidArm.LEFT
                                    && actor.stand().getMainArm() == HumanoidArm.RIGHT,
                            scene.id + ": a left-handed user's GER leads with " + actor.stand().getMainArm());
                    helper.assertTrue(Math.abs(actor.stand().getBbWidth() - 0.35F) < GerMeleeGameTestScene.EPS
                                    && Math.abs(actor.stand().getBbHeight() - 1.05F) < GerMeleeGameTestScene.EPS,
                            scene.id + ": body " + actor.stand().getBbWidth() + " x " + actor.stand().getBbHeight());
                    helper.assertTrue(Math.abs(actor.stand().getEyeHeight() - eyeHeight) < GerMeleeGameTestScene.EPS,
                            scene.id + ": eye height " + actor.stand().getEyeHeight() + ", expected " + eyeHeight);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_punch",
                            "ger_stand_punch", InputMethod.CLICK, GerMeleeGameTestScene.LMB);
                    String clip = ((EntityActionType) actor.power().getAbility("ger_stand_punch"))
                            .getEntityAnim(punch).name();
                    helper.assertTrue(((StandEntityPunch) punch).getPunchingHand() == InteractionHand.MAIN_HAND
                                    && "punch".equals(clip),
                            scene.id + ": the first punch plays " + clip);
                    return true;
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }
}
