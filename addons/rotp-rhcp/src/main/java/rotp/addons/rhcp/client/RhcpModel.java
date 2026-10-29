package rotp.addons.rhcp.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Native 1.21.1 translation of the donor 128x128 RHCP model. */
public final class RhcpModel extends StandEntityModel<
        RhcpStandEntity, RhcpRenderState> {
    private final ModelPart tail;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tail4;
    private final ModelPart beakLower;
    private final ModelPart[] actionParts;
    private final ModelPart[] joints;

    private static final int BODY = 0;
    private static final int TORSO = 1;
    private static final int HEAD = 2;
    private static final int BEAK = 3;
    private static final int LEFT_ARM = 4;
    private static final int LEFT_FOREARM = 5;
    private static final int RIGHT_ARM = 6;
    private static final int RIGHT_FOREARM = 7;
    private static final int LEFT_LEG = 8;
    private static final int LEFT_LOWER_LEG = 9;
    private static final int RIGHT_LEG = 10;
    private static final int RIGHT_LOWER_LEG = 11;
    private static final int TAIL = 12;
    private static final int PART_COUNT = 17;
    private static final int[] MIRRORED_PART = {
            BODY, TORSO, HEAD, BEAK,
            RIGHT_ARM, RIGHT_FOREARM,
            LEFT_ARM, LEFT_FOREARM,
            RIGHT_LEG, RIGHT_LOWER_LEG,
            LEFT_LEG, LEFT_LOWER_LEG,
            TAIL, TAIL + 1, TAIL + 2, TAIL + 3, TAIL + 4
    };

    private static final Frame EMPTY = frame();

    private static final Frame FLOATING_IDLE = frame()
            .beak(0, 0, 0).body(0, 0, 0).torso(0, 0, 0)
            .tail(0, -15, 0, 0)
            .tail(1, -5, 10, 0)
            .tail(2, 10, 15, 0)
            .tail(3, 10, 15, 0)
            .tail(4, 10, 20, 0)
            .leftArm(-107.5F, 37.5F, -90)
            .leftForearm(-81.6905F, -19.4555F, 49.077F)
            .rightArm(-107.5F, -37.5F, 90)
            .rightForearm(-78.1822F, 16.4737F, -49.5249F)
            .leftLeg(0, 0, -2.5F).leftLowerLeg(25, 0, 0)
            .rightLeg(-45, 0, 2.5F).rightLowerLeg(90, 0, 0);
    private static final Frame GROUNDED_IDLE = groundedIdle();
    private static final Frame MOVING_IDLE = groundedIdle()
            .leftLeg(10, 0, -5).leftLowerLeg(0, 0, 2.5F)
            .rightLeg(10, 0, 5).rightLowerLeg(0, 0, -2.5F);
    private static final Frame FLOATING_IDLE_LOOP = tailIdleLoop();
    private static final Frame GROUNDED_IDLE_LOOP = tailIdleLoop()
            .leftArm(15, 0, -35).leftForearm(-90, 50, -57.5F)
            .rightArm(15, 0, 35).rightForearm(-90, -50, 57.5F);

    // The inherited 1.16.5 HumanoidStandModel light-jab timeline.
    private static final Frame JAB_1 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, -15.0F, 0.0F)
            .leftArm(-7.5F, 0.0F, -15.0F)
            .leftForearm(-100.0F, 15.0F, 7.5F)
            .rightArm(22.5F, 0.0F, 22.5F)
            .rightForearm(-105.0F, 0.0F, -15.0F);
    private static final Frame JAB_2 = frame()
            .body(0.0F, -5.0F, 0.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .leftArm(30.0F, 0.0F, -15.0F)
            .leftForearm(-107.5F, 15.0F, 7.5F)
            .rightArm(5.941F, 8.4211F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F);
    private static final Frame JAB_3 = frame()
            .body(0.0F, -12.5F, 0.0F)
            .torso(0.0F, -17.5F, 0.0F)
            .leftArm(37.5F, 0.0F, -15.0F)
            .leftForearm(-115.0F, 15.0F, 7.5F)
            .rightArm(-81.9244F, 11.0311F, 70.2661F)
            .rightForearm(0.0F, 0.0F, 0.0F);
    private static final Frame JAB_4 = frame()
            .body(0.0F, -3.75F, 0.0F)
            .torso(0.0F, -3.75F, 0.0F)
            .leftArm(5.63F, 0.0F, -20.62F)
            .leftForearm(-103.75F, 3.75F, 13.13F)
            .rightArm(5.941F, 8.4211F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F);

    // RHCP donor regular heavy-punch timeline.
    private static final Frame HEAVY_1 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(22.5F, 0.0F, 0.0F)
            .rightForearm(-95.0F, 0.0F, 0.0F);
    private static final Frame HEAVY_2 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 30.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(22.5F, 0.0F, 0.0F)
            .rightForearm(-95.0F, 0.0F, 0.0F);
    private static final Frame HEAVY_3 = frame()
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -11.25F, 0.0F)
            .leftArm(22.5F, 0.0F, -30.0F)
            .leftForearm(-7.5F, 0.0F, 0.0F)
            .rightArm(-6.25F, 5.0F, 0.0F)
            .rightForearm(-51.0618F, -0.612F, -0.5875F);
    private static final Frame HEAVY_4 = frame()
            .body(10.0F, -12.5F, 0.0F)
            .torso(0.0F, -40.0F, 0.0F)
            .leftArm(12.5F, 0.0F, -60.0F)
            .leftForearm(-15.0F, 0.0F, 0.0F)
            .rightArm(-25.0F, -25.0F, 50.0F)
            .rightForearm(-50.0F, 90.0F, 0.0F);
    private static final Frame HEAVY_5 = frame()
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 0.0F, 0.0F)
            .leftArm(22.5F, 0.0F, 0.0F)
            .leftForearm(-45.0F, 0.0F, 0.0F)
            .rightArm(-90.0F, -37.5F, 90.0F)
            .rightForearm(0.0F, 90.0F, 37.5F);

    // RHCP donor target/chase alternate heavy timeline.
    private static final Frame HEAVY_ALT_1 = frame()
            .beak(17.5F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -15.0F, 0.0F)
            .leftArm(0.0F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 0.0F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-60.0F, -10.0F, -10.0F);
    private static final Frame HEAVY_ALT_2 = frame()
            .beak(10.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .leftArm(3.33F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 3.33F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-53.33F, -6.66F, -6.66F);
    private static final Frame HEAVY_ALT_3 = frame()
            .beak(20.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -25.0F, 0.0F)
            .leftArm(6.66F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 6.66F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-46.67F, -3.33F, -3.33F);
    private static final Frame HEAVY_ALT_4 = frame()
            .beak(25.0F, 0.0F, 0.0F)
            .body(5.0F, 0.0F, 0.0F)
            .torso(0.0F, -30.0F, 0.0F)
            .leftArm(10.0F, -5.0F, -90.0F)
            .leftForearm(-90.0F, 10.0F, 90.0F)
            .rightArm(25.0F, 0.0F, 60.0F)
            .rightForearm(-40.0F, 0.0F, 0.0F);
    private static final Frame HEAVY_ALT_5 = frame()
            .beak(25.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .torso(0.0F, 40.0F, 0.0F)
            .leftArm(-59.9053F, -4.3288F, -92.5048F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(20.0F, 0.0F, 30.0F)
            .rightForearm(-90.0F, 0.0F, 0.0F);

    // RHCP donor normal double-hit combo.
    private static final Frame DOUBLE_HIT_1 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -20.45F, -6.82F, 0.0F)
            .tail(1, -7.27F, -1.36F, 0.0F)
            .tail(2, 0.91F, 1.36F, 0.0F)
            .tail(3, 0.91F, 1.36F, 0.0F)
            .tail(4, 0.91F, 1.82F, 0.0F)
            .leftArm(-122.3122F, 37.7555F, -111.1693F)
            .leftForearm(-90.0F, -22.5F, 0.0F)
            .rightArm(-122.3122F, -37.7555F, 111.1693F)
            .rightForearm(-90.0F, 27.5F, 0.0F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);
    private static final Frame DOUBLE_HIT_2 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .beak(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -23.72F, -10.91F, 0.0F)
            .tail(1, -8.64F, -8.18F, 0.0F)
            .tail(2, -4.54F, -6.82F, 0.0F)
            .tail(3, -4.54F, -6.82F, 0.0F)
            .tail(4, -4.54F, -9.09F, 0.0F)
            .leftArm(-99.2643F, 11.8491F, -128.4635F)
            .leftForearm(-85.6899F, -33.881F, 92.0179F)
            .rightArm(-99.2643F, -11.8491F, 128.4635F)
            .rightForearm(-85.6899F, 33.881F, -92.0179F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);
    private static final Frame DOUBLE_HIT_3 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .beak(25.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .tail(0, -27.0F, -15.0F, 0.0F)
            .tail(1, -10.0F, -15.0F, 0.0F)
            .tail(2, -10.0F, 15.0F, 0.0F)
            .tail(3, -10.0F, 15.0F, 0.0F)
            .tail(4, -10.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 0.0F, -120.0F)
            .leftForearm(-90.0F, -75.0F, 90.0F)
            .rightArm(0.0F, 0.0F, 120.0F)
            .rightForearm(-90.0F, 75.0F, -90.0F)
            .leftLeg(-20.0F, 0.0F, -2.5F)
            .leftLowerLeg(25.0F, 0.0F, 0.0F)
            .rightLeg(-5.0F, 0.0F, 1.0F)
            .rightLowerLeg(10.0F, 0.0F, 0.0F);

    // RHCP donor potency-four combo alternate.
    private static final Frame COMBO_ALT_1 = frame()
            .head(0.0F, 0.0F, 0.0F)
            .body(18.0F, 0.0F, -18.0F)
            .torso(0.0F, 10.0F, 0.0F)
            .tail(0, -14.36F, 18.0F, 0.0F)
            .tail(1, -6.0F, 10.0F, 0.0F)
            .tail(2, 4.0F, 15.0F, 0.0F)
            .tail(3, 4.0F, 15.0F, 0.0F)
            .tail(4, 4.0F, 20.0F, 0.0F)
            .leftArm(-3.0F, 15.0F, -54.0F)
            .leftForearm(-90.5F, 0.0F, 0.0F)
            .rightArm(-5.0F, 5.0F, 90.0F)
            .rightForearm(-98.735F, 9.9319F, -75.5183F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(80.0F, 0.0F, 0.0F)
            .rightLeg(10.0F, 0.0F, 10.0F)
            .rightLowerLeg(27.0F, 0.0F, 0.0F);
    private static final Frame COMBO_ALT_2 = frame()
            .head(8.33F, 0.0F, -8.33F)
            .beak(0.0F, 0.0F, 0.0F)
            .body(45.0F, 0.0F, -45.0F)
            .torso(0.0F, 30.0F, 0.0F)
            .tail(0, -13.4F, 45.0F, 0.0F)
            .tail(1, -7.5F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, -5.0F, 15.0F, 0.0F)
            .tail(4, -5.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 0.0F, -90.0F)
            .leftForearm(-95.0F, 0.0F, 0.0F)
            .rightArm(23.0292F, 21.9344F, 133.1226F)
            .rightForearm(-90.0F, 45.0F, -90.0F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(80.0F, 0.0F, 0.0F)
            .rightLeg(10.0F, 0.0F, 10.0F)
            .rightLowerLeg(27.0F, 0.0F, 0.0F);
    private static final Frame COMBO_ALT_3 = frame()
            .head(10.0F, 0.0F, -10.0F)
            .beak(25.0F, 0.0F, 0.0F)
            .body(45.0F, 0.0F, -45.0F)
            .torso(0.0F, -20.0F, 0.0F)
            .tail(0, -13.4F, 45.0F, 0.0F)
            .tail(1, -7.5F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, -5.0F, 15.0F, 0.0F)
            .tail(4, -5.0F, 20.0F, 0.0F)
            .leftArm(0.0F, 90.0F, -120.0F)
            .leftForearm(-90.0F, 0.0F, 90.0F)
            .rightArm(-52.5F, -35.0F, 105.0F)
            .rightForearm(-90.0F, 72.5F, -90.0F)
            .leftLeg(-60.0F, 0.0F, -10.0F)
            .leftLowerLeg(120.0F, 0.0F, 0.0F)
            .rightLeg(-50.0F, 30.0F, 10.0F)
            .rightLowerLeg(100.0F, 0.0F, 0.0F);

    // RHCP donor dodge taunt, where both counters start.
    private static final Frame TAUNT_1 = counterStance()
            .head(25.0F, 110.0F, 25.5F)
            .body(0.0F, 45.0F, 0.0F)
            .tail(0, -27.0F, 0.0F, 0.0F)
            .tail(1, -5.0F, 10.0F, 0.0F)
            .tail(2, -5.0F, 15.0F, 0.0F)
            .tail(3, 0.0F, 15.0F, 0.0F)
            .tail(4, 2.5F, 20.0F, 0.0F)
            .leftArm(-68.5365F, 3.9942F, -58.1966F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(40.0458F, -17.6444F, 7.1442F)
            .rightForearm(-57.5F, 0.0F, 0.0F);

    // RHCP donor counterattack timeline.
    private static final Frame COUNTER_1 = counterStance()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, -15.0F, 0.0F)
            .leftArm(-68.5365F, 3.9942F, -58.1966F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(-22.5F, 0.0F, 22.5F)
            .rightForearm(-105.0F, 0.0F, -15.0F);
    private static final Frame COUNTER_2 = counterStance()
            .body(0.0F, -25.0F, 0.0F)
            .leftArm(-15.52F, 2.0F, -36.6F)
            .leftForearm(-102.5F, -2.5F, 48.75F)
            .rightArm(5.941F, 8.421F, 69.059F)
            .rightForearm(-75.0F, 0.0F, 0.0F);
    private static final Frame COUNTER_3 = counterStance()
            .body(0.0F, -30.0F, 0.0F)
            .leftArm(37.5F, 0.0F, -15.0F)
            .leftForearm(-115.0F, 15.0F, 7.5F)
            .rightArm(-85.7155F, 0.1901F, 89.8426F)
            .rightForearm(-10.0F, 0.0F, 0.0F);

    // RHCP donor blitz slash timeline.
    private static final Frame BLITZ_1 = counterStance()
            .head(0.0F, 0.0F, 0.0F)
            .body(0.0F, 0.0F, 0.0F)
            .leftArm(-64.4695F, 11.3125F, 5.3525F)
            .leftForearm(-80.0F, 20.0F, 0.0F)
            .rightArm(0.0F, -30.0F, 25.0F)
            .rightForearm(-61.1136F, 15.5839F, -25.9629F);
    private static final Frame BLITZ_2 = counterStance()
            .head(0.0F, -10.0F, 0.0F)
            .body(0.0F, -17.5F, 0.0F)
            .leftArm(-65.0F, 0.0F, 0.0F)
            .leftForearm(-80.0F, 0.0F, 0.0F)
            .rightArm(-39.2315F, -66.7163F, 88.4349F)
            .rightForearm(-148.8864F, 47.9366F, -140.8895F);
    private static final Frame BLITZ_3 = counterStance()
            .head(0.0F, -15.0F, 0.0F)
            .body(0.0F, -26.25F, 0.0F)
            .leftArm(3.54F, 3.5333F, -89.8908F)
            .leftForearm(-85.0F, -40.0F, 45.0F)
            .rightArm(-59.02F, -53.62F, 125.78F)
            .rightForearm(-90.0F, 80.0F, -90.0F);
    private static final Frame BLITZ_4 = counterStance()
            .head(0.0F, -20.0F, 0.0F)
            .body(0.0F, -35.0F, 0.0F)
            .leftArm(75.0F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(-78.8136F, -40.5276F, 163.1287F)
            .rightForearm(-180.0F, 0.0F, -180.0F);
    private static final Frame BLITZ_5 = counterStance()
            .head(0.0F, -5.0F, 0.0F)
            .body(0.0F, 2.5F, 0.0F)
            .leftArm(31.25F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(9.2665F, -5.1306F, 93.6587F)
            .rightForearm(-92.0259F, 29.2531F, -90.963F);
    private static final Frame BLITZ_6 = counterStance()
            .head(0.0F, 10.0F, 0.0F)
            .body(0.0F, 40.0F, 0.0F)
            .leftArm(-12.5F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -80.0F, 90.0F)
            .rightArm(79.1282F, 19.3597F, 144.9927F)
            .rightForearm(-90.0F, 5.0F, -90.0F);
    private static final Frame BLITZ_7 = counterStance()
            .head(0.0F, 60.0F, 0.0F)
            .body(0.0F, 50.0F, 0.0F)
            .leftArm(-95.0F, 0.0F, -90.0F)
            .leftForearm(-90.0F, -32.5F, 90.0F)
            .rightArm(64.2845F, 0.1901F, 89.8426F)
            .rightForearm(-90.0F, 80.0F, -90.0F);
    private static final Frame BLITZ_8 = counterStance()
            .head(0.0F, -2.5F, 0.0F)
            .body(0.0F, 60.0F, 0.0F)
            .leftArm(-70.0F, 20.0F, 0.0F)
            .leftForearm(-90.0F, -20.0F, 90.0F)
            .rightArm(26.6322F, 3.1053F, 15.4394F)
            .rightForearm(-90.0F, 32.5F, -90.0F);

    private static final Frame[] JAB_WINDUP =
            {JAB_1, JAB_2, JAB_3, JAB_3};
    private static final float[] JAB_WINDUP_MARKS =
            {0.0F, 0.5F, 0.75F, 1.0F};
    private static final Frame[] HEAVY_WINDUP =
            {EMPTY, HEAVY_1, HEAVY_2, HEAVY_3, HEAVY_4};
    private static final float[] HEAVY_WINDUP_MARKS =
            {0.0F, 0.5F, 0.9F, 0.95F, 1.0F};
    private static final Frame[] HEAVY_RECOVERY =
            {HEAVY_4, HEAVY_4, HEAVY_5, HEAVY_5, EMPTY};
    private static final float[] HEAVY_RECOVERY_MARKS =
            {0.0F, 0.5F, 0.7F, 0.8F, 1.0F};
    private static final Frame[] HEAVY_ALT_WINDUP =
            {HEAVY_ALT_1, HEAVY_ALT_2, HEAVY_ALT_3,
                    HEAVY_ALT_4, HEAVY_ALT_5};
    private static final float[] HEAVY_ALT_WINDUP_MARKS =
            {0.0F, 0.316F, 0.622F, 0.95F, 1.0F};
    private static final Frame[] HEAVY_ALT_RECOVERY =
            {HEAVY_ALT_5, HEAVY_ALT_5, EMPTY};
    private static final float[] HEAVY_ALT_RECOVERY_MARKS =
            {0.0F, 0.5F, 1.0F};
    private static final Frame[] DOUBLE_HIT_PERFORM =
            {DOUBLE_HIT_1, DOUBLE_HIT_2, DOUBLE_HIT_3};
    private static final float[] DOUBLE_HIT_PERFORM_MARKS =
            {0.0F, 0.5F, 1.0F};
    private static final Frame[] DOUBLE_HIT_RECOVERY =
            {DOUBLE_HIT_3, DOUBLE_HIT_3, EMPTY};
    private static final float[] DOUBLE_HIT_RECOVERY_MARKS =
            {0.0F, 0.25F, 1.0F};
    private static final Frame[] COMBO_ALT_WINDUP =
            {EMPTY, COMBO_ALT_1, COMBO_ALT_2, COMBO_ALT_3};
    private static final float[] COMBO_ALT_WINDUP_MARKS =
            {0.0F, 0.33335F, 0.9F, 1.0F};
    private static final Frame[] COMBO_ALT_RECOVERY =
            {COMBO_ALT_3, COMBO_ALT_3, COMBO_ALT_3, EMPTY};
    private static final float[] COMBO_ALT_RECOVERY_MARKS =
            {0.0F, 0.5F, 0.7F, 1.0F};
    private static final Frame[] COUNTER_WINDUP =
            {TAUNT_1, COUNTER_1, COUNTER_2, COUNTER_3, COUNTER_3};
    private static final float[] COUNTER_WINDUP_MARKS =
            {0.0F, 0.42857143F, 0.5714286F, 0.85714287F, 1.0F};
    private static final Frame[] COUNTER_RECOVERY =
            {COUNTER_3, EMPTY};
    private static final float[] COUNTER_RECOVERY_MARKS =
            {0.0F, 1.0F};
    private static final Frame[] BLITZ_WINDUP =
            {TAUNT_1, BLITZ_1, BLITZ_2, BLITZ_3, BLITZ_4,
                    BLITZ_5, BLITZ_6, BLITZ_7, BLITZ_7};
    private static final float[] BLITZ_WINDUP_MARKS =
            {0.0F, 0.23076923F, 0.3846154F, 0.46153846F, 0.53846157F,
                    0.61538464F, 0.6923077F, 0.7692308F, 1.0F};
    private static final Frame[] BLITZ_RECOVERY =
            {BLITZ_7, BLITZ_8, BLITZ_8, EMPTY};
    private static final float[] BLITZ_RECOVERY_MARKS =
            {0.0F, 0.05F, 0.8F, 1.0F};

    public RhcpModel(ModelPart root) {
        super(root);
        Model_1_21_2plus model = (Model_1_21_2plus) (Object) this;
        tail = part(model, "tail");
        tail1 = part(model, "tail_1");
        tail2 = part(model, "tail_2");
        tail3 = part(model, "tail_3");
        tail4 = part(model, "tail_4");
        beakLower = part(model, "beak_lower");
        actionParts = new ModelPart[] {
                body_rot, torso_bend, head, beakLower,
                left_arm_xrot, left_arm_bend,
                right_arm_xrot, right_arm_bend,
                left_leg_xrot, left_leg_bend,
                right_leg_xrot, right_leg_bend,
                tail, tail1, tail2, tail3, tail4
        };
        joints = new ModelPart[] {
                part(model, "left_arm_joint"), part(model, "right_arm_joint"),
                part(model, "left_leg_joint"), part(model, "right_leg_joint")
        };
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition standPos = empty(root, "stand_pos", PartPose.ZERO);
        PartDefinition bodyRot = empty(standPos, "body_rot", PartPose.ZERO);
        PartDefinition body = empty(bodyRot, "body", PartPose.ZERO);
        PartDefinition torso = empty(
                body, "torso", PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition torsoBend = empty(
                torso, "torso_bend", PartPose.ZERO);

        PartDefinition head = empty(
                standPos,
                "head",
                PartPose.ZERO);
        PartDefinition headRot = head.addOrReplaceChild(
                "head_rot",
                box(1, 1)
                        .addBox(-3.5F, -5.75F, -3.5F, 7, 5.75F, 7)
                        .texOffs(32, 0)
                        .addBox(
                                -4, -6.4F, -4, 8, 1.65F, 8,
                                new CubeDeformation(0.05F))
                        .texOffs(32, 11)
                        .addBox(
                                -2, -5.25F, -4.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(36, 11)
                        .addBox(
                                1, -5.25F, -4.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(44, 11)
                        .addBox(
                                3.75F, -5.25F, -2, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(48, 11)
                        .addBox(
                                3.75F, -5.25F, 1, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(56, 11)
                        .addBox(
                                1, -5.25F, 3.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(60, 11)
                        .addBox(
                                -2, -5.25F, 3.75F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(36, 13)
                        .addBox(
                                -4.75F, -5.25F, 1, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(40, 13)
                        .addBox(
                                -4.75F, -5.25F, -2, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(64, 0)
                        .addBox(-3.5F, -7.7F, -3.5F, 7, 1.3F, 7)
                        .texOffs(88, 10)
                        .addBox(-2.5F, -8, -2.5F, 5, 0.3F, 5),
                PartPose.ZERO);

        PartDefinition cornerA = empty(
                headRot,
                "corner_a",
                PartPose.offsetAndRotation(
                        4.5F, 0.0F, -4.5F,
                        0.0F, -0.7854F, 0.0F));
        cornerA.addOrReplaceChild(
                "cube",
                box(40, 11)
                        .addBox(
                                -0.5F, -5.25F, 0.25F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(32, 13)
                        .addBox(
                                -0.5F, -5.25F, 11.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F)),
                PartPose.ZERO);
        PartDefinition cornerB = empty(
                headRot,
                "corner_b",
                PartPose.offsetAndRotation(
                        -4.5F, 0.0F, -4.5F,
                        0.0F, -0.7854F, 0.0F));
        cornerB.addOrReplaceChild(
                "cube",
                box(44, 13)
                        .addBox(
                                0.25F, -5.25F, -0.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F))
                        .texOffs(52, 11)
                        .addBox(
                                11.5F, -5.25F, -0.5F, 1, 1, 1,
                                new CubeDeformation(-0.1F)),
                PartPose.ZERO);

        PartDefinition beak = headRot.addOrReplaceChild(
                "beak_upper",
                box(64, 24).addBox(-0.9F, -2.6F, -5.45F, 1.8F, 1.1F, 1.9F),
                PartPose.offset(0.0F, -0.05F, 0.0F));
        beak.addOrReplaceChild(
                "beak_tip",
                box(80, 24).addBox(-0.8F, -0.05F, 0.2F, 1.6F, 0.5F, 0.9F),
                PartPose.offsetAndRotation(
                        0, -2, -6, 0.3491F, 0, 0));
        beak.addOrReplaceChild(
                "beak_left",
                box(72, 24).addBox(-1, -1.5F, 0.6F, 1, 1, 2.15F),
                PartPose.offsetAndRotation(
                        0.5F, -0.95F, -6, 0, 0.2618F, 0));
        beak.addOrReplaceChild(
                "beak_right",
                box(72, 24).addBox(0, -1.5F, 0.6F, 1, 1, 2.15F),
                PartPose.offsetAndRotation(
                        -0.5F, -0.95F, -6, 0, -0.2618F, 0));
        PartDefinition lowerBeak = headRot.addOrReplaceChild(
                "beak_lower",
                box(88, 24)
                        .addBox(-0.8F, -0.55F, -1.6F, 1.6F, 0.5F, 1.65F)
                        .addBox(-0.9F, -0.75F, -1.15F, 1.8F, 0.55F, 1.65F)
                        .texOffs(112, 26).addBox(-0.82F, -0.7F, -1.65F, 1.64F, 0.2F, 1.95F)
                        .texOffs(120, 26).addBox(-0.7F, -0.25F, -1.68F, 1.4F, 0.2F, 0.28F),
                PartPose.offset(0, -0.75F, -4));
        lowerBeak.addOrReplaceChild(
                "beak_lower_left",
                box(96, 24).addBox(-1, -1.4F, 0.45F, 1, 0.5F, 2.3F),
                PartPose.offsetAndRotation(
                        0.5F, 0.75F, -2, 0, 0.2618F, 0));
        lowerBeak.addOrReplaceChild(
                "beak_lower_right",
                box(96, 24).addBox(0, -1.4F, 0.45F, 1, 0.5F, 2.3F),
                PartPose.offsetAndRotation(
                        -0.5F, 0.75F, -2, 0, -0.2618F, 0));

        // Newly authored Blockbench groups flip both X and Y, so their Z angles keep their sign.
        headRot.addOrReplaceChild(
                "rhcp_eye_left",
                box(64, 16).addBox(-1.175F, -0.7F, -0.3F, 2.35F, 1.45F, 0.5F)
                        .texOffs(72, 16).addBox(-1.3F, -0.94F, -0.55F, 2.6F, 0.31F, 0.45F)
                        .texOffs(80, 16).addBox(-0.625F, -0.28F, -0.68F, 0.45F, 0.55F, 0.18F)
                        .texOffs(112, 16).addBox(-0.9F, -0.5F, -0.52F, 1.6F, 1, 0.3F)
                        .texOffs(118, 16).addBox(0.7F, -0.3F, -0.52F, 0.4F, 0.65F, 0.3F)
                        .texOffs(122, 16).addBox(-0.74F, -0.36F, -0.57F, 0.68F, 0.72F, 0.17F),
                PartPose.offsetAndRotation(
                        1.75F, -3, -3.5F, 0, 0, -10 * Mth.DEG_TO_RAD));
        headRot.addOrReplaceChild(
                "rhcp_eye_right",
                box(64, 19).addBox(-1.175F, -0.7F, -0.3F, 2.35F, 1.45F, 0.5F)
                        .texOffs(72, 16).addBox(-1.3F, -0.94F, -0.55F, 2.6F, 0.31F, 0.45F)
                        .texOffs(80, 16).addBox(0.175F, -0.28F, -0.68F, 0.45F, 0.55F, 0.18F)
                        .texOffs(112, 16).addBox(-0.7F, -0.5F, -0.52F, 1.6F, 1, 0.3F)
                        .texOffs(118, 16).addBox(-1.1F, -0.3F, -0.52F, 0.4F, 0.65F, 0.3F)
                        .texOffs(122, 16).addBox(0.06F, -0.36F, -0.57F, 0.68F, 0.72F, 0.17F),
                PartPose.offsetAndRotation(
                        -1.75F, -3, -3.5F, 0, 0, 10 * Mth.DEG_TO_RAD));

        PartDefinition torsoNoArms = torsoBend.addOrReplaceChild(
                "torso_no_arms",
                box(0, 64)
                        .addBox(-4, 0, -2, 8, 6, 4)
                        .texOffs(15, 74)
                        .addBox(-0.5F, 2.5F, 2, 1, 1, 1)
                        .texOffs(19, 74)
                        .addBox(-0.5F, 4, 1.9F, 1, 1, 1)
                        .texOffs(18, 76)
                        .addBox(-0.5F, 5.25F, 0.65F, 1, 1, 2)
                        .texOffs(92, 36)
                        .addBox(-2.75F, 6, -1.5F, 5.5F, 4, 3)
                        .texOffs(0, 81)
                        .addBox(-3.5F, 10, -2, 7, 2, 4)
                        .texOffs(0, 87)
                        .addBox(
                                -3.5F, 10, -2, 7, 1, 4,
                                new CubeDeformation(0.1F))
                        .texOffs(18, 79)
                        .addBox(-0.5F, 6.5F, 1.5F, 1, 1, 1)
                        .texOffs(18, 88)
                        .addBox(-0.5F, 11.1F, -2.1F, 1, 2, 1)
                        .texOffs(0, 88)
                        .addBox(-0.5F, 11.1F, -2.6F, 1, 2, 1)
                        .texOffs(64, 46)
                        .addBox(-2.5F, 9, -1.8F, 5, 1, 0.3F)
                        .addBox(-2.5F, 7.65F, -1.8F, 5, 1, 0.3F)
                        .addBox(-2.5F, 6.3F, -1.8F, 5, 1, 0.3F),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        torsoNoArms.addOrReplaceChild(
                "chest_diamond",
                box(24, 68).addBox(
                        -0.5F, -0.5F, -2.3F, 2, 2, 1),
                PartPose.offsetAndRotation(
                        0.0F, 3.8F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
        empty(torso, "torso_lower", PartPose.ZERO);

        torsoNoArms.addOrReplaceChild(
                "rhcp_chest_left_relief",
                box(64, 36).addBox(-1.75F, -1.3F, -0.7F, 3.5F, 2.6F, 0.8F),
                PartPose.offsetAndRotation(
                        1.75F, 2.4F, -2, 0, -3 * Mth.DEG_TO_RAD, -3 * Mth.DEG_TO_RAD));
        torsoNoArms.addOrReplaceChild(
                "rhcp_chest_right_relief",
                box(76, 36).addBox(-1.75F, -1.3F, -0.7F, 3.5F, 2.6F, 0.8F),
                PartPose.offsetAndRotation(
                        -1.75F, 2.4F, -2, 0, 3 * Mth.DEG_TO_RAD, 3 * Mth.DEG_TO_RAD));

        PartDefinition tail = torsoNoArms.addOrReplaceChild(
                "tail",
                box(22, 81)
                        .addBox(-1, -1, -1, 2, 2, 4)
                        .texOffs(22, 83)
                        .addBox(-0.5F, -2, 0.45F, 1, 1, 1)
                        .texOffs(22, 81)
                        .addBox(-0.5F, -1.85F, 1.7F, 1, 1, 1),
                PartPose.offset(0, 11.25F, 2));
        PartDefinition tail1 = tail.addOrReplaceChild(
                "tail_1",
                box(34, 81).addBox(
                        -1, -1, -0.1F, 2, 2, 4,
                        new CubeDeformation(-0.1F))
                        .texOffs(34, 81)
                        .addBox(-0.5F, -1.75F, 1.25F, 1, 1, 1)
                        .texOffs(34, 83)
                        .addBox(-0.5F, -1.9F, 0, 1, 1, 1),
                PartPose.offset(0, 0, 3));
        PartDefinition tail2 = tail1.addOrReplaceChild(
                "tail_2",
                box(46, 81).addBox(
                        -1, -1, -0.2F, 2, 2, 4,
                        new CubeDeformation(-0.2F)),
                PartPose.offset(0, 0, 3.8F));
        PartDefinition tail3 = tail2.addOrReplaceChild(
                "tail_3",
                box(22, 87).addBox(
                        -1, -1, -0.3F, 2, 2, 4,
                        new CubeDeformation(-0.3F)),
                PartPose.offset(0, 0, 3.6F));
        tail3.addOrReplaceChild(
                "tail_4",
                box(34, 87).addBox(
                        -1, -1, -0.4F, 2, 2, 4,
                        new CubeDeformation(-0.4F)),
                PartPose.offset(0, 0, 3.4F));

        arm(torsoBend, true);
        arm(torsoBend, false);
        leg(body, true);
        leg(body, false);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(RhcpRenderState state) {
        super.setupAnim(state);
        if (state.action.pose == null) {
            setupDonorAnim(state);
        }
        updateJoint(joints[0], left_arm_bend);
        updateJoint(joints[1], right_arm_bend);
        updateJoint(joints[2], left_leg_bend);
        updateJoint(joints[3], right_leg_bend);
    }

    private void setupDonorAnim(RhcpRenderState state) {
        String actionName = state.actionName;
        boolean idle = actionName == null || actionName.isEmpty();
        applyIdlePose(actionParts, state.floating, state.moving, state.idleLoopTick, idle);
        if (!idle) {
            switch (actionName) {
                case "rhcp_punch_off", "rhcp_punch" -> applyLight(state);
                case "rhcp_barrage" -> applyBarrage(state);
                case "rhcp_combo_punch" -> applyCombo(state);
                case "rhcp_heavy_punch" -> applyHeavy(state, state.attackAlternate);
                case "rhcp_electrocute_target" -> applyHeavy(state, false);
                case "rhcp_chase_target" -> applyHeavy(state, true);
                case "rhcp_counterattack" -> applyCounter(actionParts, false,
                        state.actionPhase, state.actionPhaseRatio, !state.mirrorAction);
                case "rhcp_blitz_attack" -> applyCounter(actionParts, true,
                        state.actionPhase, state.actionPhaseRatio, !state.mirrorAction);
                default -> applyPhaseAwareFallback(state);
            }
        }
        if (!state.floating && state.moving
                && !("rhcp_combo_punch".equals(actionName) && state.potency >= 4)
                && !dodging(actionName, state.actionPhase)) {
            applyWalkingPose(actionParts, state.walkAnimationPos, state.walkAnimationSpeed,
                    state.walkSpeedFactor, state.userCrouching);
        }
    }

    // 1.16 RHCPDodge.dodge, which RHCPModel.walkAnim reads: the dodge before
    // its recovery, and both counters in every phase.
    private static boolean dodging(String actionName, ActionPhase phase) {
        return "rhcp_dodge".equals(actionName) && phase != ActionPhase.RECOVERY
                || "rhcp_counterattack".equals(actionName)
                || "rhcp_blitz_attack".equals(actionName);
    }

    // 1.16 RHCPModel COUNTERATTACK_POSE and BLITZ_ATTACK_POSE: the windup
    // starts from the dodge taunt of the punching hand's side (ModelPoseSided)
    // and its last pose is held through PERFORM (PosedActionAnimation).
    private static void applyCounter(ModelPart[] parts, boolean blitz,
            ActionPhase phase, float progress, boolean mirrorTaunt) {
        if (phase == ActionPhase.WINDUP) {
            applyTimeline(parts,
                    blitz ? BLITZ_WINDUP : COUNTER_WINDUP,
                    blitz ? BLITZ_WINDUP_MARKS : COUNTER_WINDUP_MARKS,
                    progress, mirrorTaunt, false);
        }
        else if (phase == ActionPhase.PERFORM) {
            Frame hit = blitz ? BLITZ_7 : COUNTER_3;
            applyTransition(parts, hit, false, hit, false, 1.0F);
        }
        else if (phase == ActionPhase.RECOVERY) {
            applyTimeline(parts,
                    blitz ? BLITZ_RECOVERY : COUNTER_RECOVERY,
                    blitz ? BLITZ_RECOVERY_MARKS : COUNTER_RECOVERY_MARKS,
                    progress, false, false);
        }
    }

    // The dodge stance legs every taunt and counter pose shares.
    private static Frame counterStance() {
        return frame()
                .leftLeg(-10.0F, 0.0F, -2.5F)
                .leftLowerLeg(25.0F, 0.0F, 0.0F)
                .rightLeg(-35.0F, 0.0F, 2.5F)
                .rightLowerLeg(70.0F, 0.0F, 0.0F);
    }

    private static Frame groundedIdle() {
        return frame().beak(0, 0, 0).body(10, 0, 0).torso(0, 0, 0)
                .tail(0, -27, 0, 0)
                .tail(1, -5, 10, 0)
                .tail(2, 10, 15, 0)
                .tail(3, 10, 15, 0)
                .tail(4, 10, 20, 0)
                .leftArm(15, 0, -32.5F).leftForearm(-90, 50, -60)
                .rightArm(15, 0, 32.5F).rightForearm(-90, -50, 60)
                .leftLeg(-20, 0, -10).leftLowerLeg(25, 0, 0)
                .rightLeg(-20, 0, 10).rightLowerLeg(25, 0, 0);
    }

    private static Frame tailIdleLoop() {
        return frame().tail(1, -5, 30, 0)
                .tail(2, 10, 45, 0)
                .tail(3, 10, 45, 0)
                .tail(4, 10, 60, 0);
    }

    private static void applyIdlePose(ModelPart[] parts, boolean floating, boolean moving,
            float loopTicks, boolean animateLoop) {
        Frame base = floating ? FLOATING_IDLE : moving ? MOVING_IDLE : GROUNDED_IDLE;
        for (int index = 0; index < PART_COUNT; index++) {
            if (base.defined(index, false)) {
                rotate(parts[index], base.angle(index, 0, false),
                        base.angle(index, 1, false), base.angle(index, 2, false));
            }
        }
        if (animateLoop) {
            Frame loop = floating ? FLOATING_IDLE_LOOP : GROUNDED_IDLE_LOOP;
            // The donor uses an unclamped [-1, 0] easing, not a [0, 1] blend.
            float weight = (Mth.sin((float) Math.PI * (loopTicks / 40.0F - 0.5F)) - 1.0F) / 2.0F;
            for (int index = 0; index < PART_COUNT; index++) {
                ModelPart part = parts[index];
                if (part != null && loop.defined(index, false)) {
                    rotate(part, Mth.lerp(weight, part.xRot, loop.angle(index, 0, false)),
                            Mth.lerp(weight, part.yRot, loop.angle(index, 1, false)),
                            Mth.lerp(weight, part.zRot, loop.angle(index, 2, false)));
                }
            }
        }
    }

    private static void applyWalkingPose(ModelPart[] parts, float position, float speed,
            float speedFactor, boolean crouching) {
        float bodyX = parts[BODY].xRot;
        float rightRot = Mth.cos(position * 0.6662F) * 1.4F * speed / speedFactor;
        float leftRot = Mth.cos(position * 0.6662F + (float) Math.PI)
                * 1.4F * speed / speedFactor - bodyX;
        if (crouching) {
            rightRot /= 2.0F;
            leftRot /= 2.0F;
        }
        parts[RIGHT_LEG].xRot = rightRot - bodyX;
        parts[RIGHT_LOWER_LEG].xRot = rightRot < 0 ? 0 : rightRot / 4.0F;
        parts[LEFT_LEG].xRot = leftRot - bodyX;
        parts[LEFT_LOWER_LEG].xRot = leftRot < 0 ? 0 : leftRot / 4.0F;
    }

    private static void updateJoint(ModelPart joint, ModelPart limb) {
        if (joint != null && limb != null) {
            rotate(joint, limb.xRot / 2.0F, limb.yRot / 2.0F, limb.zRot / 2.0F);
        }
    }

    private void applyLight(RhcpRenderState state) {
        float progress = state.actionPhaseRatio;
        boolean mirror = state.mirrorAction;
        if (state.actionPhase == ActionPhase.WINDUP) {
            applyTimeline(
                    JAB_WINDUP, JAB_WINDUP_MARKS, progress, mirror);
        }
        else if (state.actionPhase == ActionPhase.PERFORM) {
            if (progress <= 0.25F) {
                applyFrame(JAB_3, mirror);
            }
            else if (progress <= 0.5F) {
                applyTransition(
                        JAB_3, mirror, JAB_4, mirror,
                        normalize(progress, 0.25F, 0.5F));
            }
            else {
                applyTransition(
                        JAB_4, mirror, JAB_1, !mirror,
                        normalize(progress, 0.5F, 1.0F));
            }
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            if (progress <= 0.75F) {
                applyFrame(JAB_1, !mirror);
            }
            else {
                applyTransition(
                        JAB_1, !mirror, EMPTY, false,
                        normalize(progress, 0.75F, 1.0F));
            }
        }
        else {
            applyFrame(JAB_3, mirror);
        }
    }

    private void applyHeavy(
            RhcpRenderState state, boolean alternate) {
        float progress = state.actionPhaseRatio;
        if (alternate) {
            if (state.actionPhase == ActionPhase.WINDUP) {
                applyTimeline(
                        HEAVY_ALT_WINDUP,
                        HEAVY_ALT_WINDUP_MARKS,
                        progress, state.mirrorAction);
            }
            else if (state.actionPhase == ActionPhase.RECOVERY) {
                applyTimeline(
                        HEAVY_ALT_RECOVERY,
                        HEAVY_ALT_RECOVERY_MARKS,
                        progress, state.mirrorAction);
            }
            else {
                applyFrame(HEAVY_ALT_5, state.mirrorAction);
            }
            return;
        }

        if (state.actionPhase == ActionPhase.WINDUP) {
            applyTimeline(
                    HEAVY_WINDUP, HEAVY_WINDUP_MARKS, progress, false);
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            applyTimeline(
                    HEAVY_RECOVERY,
                    HEAVY_RECOVERY_MARKS,
                    progress, false);
        }
        else {
            applyFrame(HEAVY_4, false);
        }
    }

    private void applyCombo(RhcpRenderState state) {
        float progress = state.actionPhaseRatio;
        if (state.comboAlternate) {
            if (state.actionPhase == ActionPhase.WINDUP) {
                applyTimeline(
                        COMBO_ALT_WINDUP,
                        COMBO_ALT_WINDUP_MARKS,
                        progress, false);
            }
            else if (state.actionPhase == ActionPhase.RECOVERY) {
                applyTimeline(
                        COMBO_ALT_RECOVERY,
                        COMBO_ALT_RECOVERY_MARKS,
                        progress, false);
            }
            else {
                applyFrame(COMBO_ALT_3, false);
            }
            return;
        }

        if (state.actionPhase == ActionPhase.WINDUP) {
            applyTransition(
                    EMPTY, false, DOUBLE_HIT_1, false, progress);
        }
        else if (state.actionPhase == ActionPhase.PERFORM) {
            applyTimeline(
                    DOUBLE_HIT_PERFORM,
                    DOUBLE_HIT_PERFORM_MARKS,
                    progress, false);
        }
        else if (state.actionPhase == ActionPhase.RECOVERY) {
            applyTimeline(
                    DOUBLE_HIT_RECOVERY,
                    DOUBLE_HIT_RECOVERY_MARKS,
                    progress, false);
        }
        else {
            applyFrame(DOUBLE_HIT_3, false);
        }
    }

    private void applyBarrage(RhcpRenderState state) {
        float weight = phaseWeight(state);
        float wave = Mth.sin(state.actionPhaseTick * 1.35F);
        rotate(left_arm_xrot,
                (-1.35F + wave * 0.3F) * weight,
                0.18F * weight,
                -0.45F * weight);
        rotate(right_arm_xrot,
                (-1.35F - wave * 0.3F) * weight,
                -0.18F * weight,
                0.45F * weight);
        rotate(left_arm_bend,
                (-0.45F - wave * 0.25F) * weight, 0.0F, 0.0F);
        rotate(right_arm_bend,
                (-0.45F + wave * 0.25F) * weight, 0.0F, 0.0F);
        rotate(torso_bend,
                0.08F * weight, wave * 0.18F * weight, 0.0F);
    }

    private void applyPhaseAwareFallback(RhcpRenderState state) {
        float weight = phaseWeight(state);
        float wave = Mth.sin(state.actionPhaseTick * 1.35F);
        switch (state.actionName) {
            case "rhcp_block" -> {
                rotate(left_arm_xrot,
                        -1.25F * weight, 0.15F * weight, -0.8F * weight);
                rotate(right_arm_xrot,
                        -1.25F * weight, -0.15F * weight, 0.8F * weight);
                rotate(left_arm_bend, -1.0F * weight, 0.0F, 0.0F);
                rotate(right_arm_bend, -1.0F * weight, 0.0F, 0.0F);
            }
            case "rhcp_recharge" -> {
                rotate(body_rot, 0.22F * weight, 0.0F, 0.0F);
                rotate(left_arm_xrot,
                        -0.45F * weight, 0.0F, -1.15F * weight);
                rotate(right_arm_xrot,
                        -0.45F * weight, 0.0F, 1.15F * weight);
                rotate(left_arm_bend, -0.55F * weight, 0.0F, 0.0F);
                rotate(right_arm_bend, -0.55F * weight, 0.0F, 0.0F);
            }
            case "rhcp_dodge" -> {
                rotate(torso_bend,
                        -0.08F * weight, wave * 0.08F * weight, 0.0F);
                rotate(left_arm_xrot,
                        -1.05F * weight, 0.0F, -1.1F * weight);
                rotate(right_arm_xrot,
                        -1.05F * weight, 0.0F, 1.1F * weight);
                rotate(left_arm_bend, -0.9F * weight, 0.0F, 0.0F);
                rotate(right_arm_bend, -0.9F * weight, 0.0F, 0.0F);
            }
            case "rhcp_electric_dash" -> {
                rotate(body_rot, 0.55F * weight, 0.0F, 0.0F);
                rotate(left_arm_xrot,
                        0.45F * weight, 0.0F, -0.35F * weight);
                rotate(right_arm_xrot,
                        0.45F * weight, 0.0F, 0.35F * weight);
                rotate(left_leg_xrot, -0.75F * weight, 0.0F, 0.0F);
                rotate(right_leg_xrot, 0.55F * weight, 0.0F, 0.0F);
            }
            default -> {
            }
        }
    }

    private static float phaseWeight(RhcpRenderState state) {
        float progress = Mth.clamp(state.actionPhaseRatio, 0.0F, 1.0F);
        if (state.actionPhase == ActionPhase.WINDUP) {
            return smooth(progress);
        }
        if (state.actionPhase == ActionPhase.RECOVERY) {
            return 1.0F - smooth(progress);
        }
        return 1.0F;
    }

    private void applyTimeline(
            Frame[] frames,
            float[] marks,
            float progress,
            boolean mirror) {
        applyTimeline(actionParts, frames, marks, progress, mirror, mirror);
    }

    // mirrorFirst: a 1.16 ModelPoseSided start pose (the dodge taunt) is the
    // only sided pose of its timeline.
    private static void applyTimeline(
            ModelPart[] parts,
            Frame[] frames,
            float[] marks,
            float progress,
            boolean mirrorFirst,
            boolean mirror) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        for (int index = 1; index < marks.length; index++) {
            if (clamped <= marks[index]) {
                applyTransition(
                        parts,
                        frames[index - 1], index == 1 ? mirrorFirst : mirror,
                        frames[index], mirror,
                        normalize(clamped, marks[index - 1], marks[index]));
                return;
            }
        }
        Frame last = frames[frames.length - 1];
        applyTransition(parts, last, mirror, last, mirror, 1.0F);
    }

    private void applyFrame(Frame frame, boolean mirror) {
        applyTransition(frame, mirror, frame, mirror, 1.0F);
    }

    private void applyTransition(
            Frame from,
            boolean mirrorFrom,
            Frame to,
            boolean mirrorTo,
            float progress) {
        applyTransition(actionParts, from, mirrorFrom, to, mirrorTo, progress);
    }

    // 1.16 ModelPoseTransition: the first pose in full, then the second by
    // progress. A part only the first pose names keeps its angle, except on
    // the way to the idle pose (EMPTY), which names every part.
    private static void applyTransition(
            ModelPart[] parts,
            Frame from,
            boolean mirrorFrom,
            Frame to,
            boolean mirrorTo,
            float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        boolean toIdle = to == EMPTY;
        for (int partIndex = 0; partIndex < PART_COUNT; partIndex++) {
            boolean fromDefined = from.defined(partIndex, mirrorFrom);
            boolean toDefined = to.defined(partIndex, mirrorTo);
            if (!fromDefined && !toDefined) {
                continue;
            }
            ModelPart part = parts[partIndex];
            if (part == null) {
                continue;
            }
            float fromX = fromDefined
                    ? from.angle(partIndex, 0, mirrorFrom) : part.xRot;
            float fromY = fromDefined
                    ? from.angle(partIndex, 1, mirrorFrom) : part.yRot;
            float fromZ = fromDefined
                    ? from.angle(partIndex, 2, mirrorFrom) : part.zRot;
            float toX = toDefined
                    ? to.angle(partIndex, 0, mirrorTo) : toIdle ? part.xRot : fromX;
            float toY = toDefined
                    ? to.angle(partIndex, 1, mirrorTo) : toIdle ? part.yRot : fromY;
            float toZ = toDefined
                    ? to.angle(partIndex, 2, mirrorTo) : toIdle ? part.zRot : fromZ;
            part.xRot = Mth.lerp(clamped, fromX, toX);
            part.yRot = Mth.lerp(clamped, fromY, toY);
            part.zRot = Mth.lerp(clamped, fromZ, toZ);
        }
    }

    private static float normalize(
            float value, float start, float end) {
        return end > start
                ? Mth.clamp((value - start) / (end - start), 0.0F, 1.0F)
                : 1.0F;
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void arm(
            PartDefinition torso, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 6.0F : -6.0F;
        int baseU = left ? 32 : 0;
        int armorU = left ? 48 : 16;
        PartDefinition xrot = empty(
                torso,
                side + "_arm_xrot",
                PartPose.offset(x, -10.0F, 0.0F));
        PartDefinition arm = xrot.addOrReplaceChild(
                side + "_arm",
                box(baseU, 108)
                        .addBox(
                                -2, -2, -2, 4, 6, 4)
                        .texOffs(armorU, 108)
                        .addBox(
                                -2, -2, -2, 4, 3, 4,
                                new CubeDeformation(0.1F)),
                PartPose.ZERO);
        arm.addOrReplaceChild(
                side + "_arm_bend",
                box(baseU, 118)
                        .addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(-0.001F))
                        .texOffs(armorU, 118)
                        .addBox(
                                -2, 1.4F, -2, 4, 2, 4,
                                new CubeDeformation(0.099F)),
                PartPose.offset(0, 4, 0));
        joint(arm, side + "_arm_joint", baseU, 4, left);
        arm.addOrReplaceChild(
                side + "_arm_spike",
                box(left ? 44 : 12, 108).mirror().addBox(
                        -0.5F, -0.5F, 0.75F, 2, 2, 2,
                        new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(
                        0.0F, 2.85F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
    }

    private static void leg(
            PartDefinition body, boolean left) {
        String side = left ? "left" : "right";
        float x = left ? 2.0F : -2.0F;
        int u = left ? 96 : 64;
        PartDefinition xrot = empty(
                body,
                side + "_leg_xrot",
                PartPose.offset(x, 12.0F, 0.0F));
        PartDefinition leg = xrot.addOrReplaceChild(
                side + "_leg",
                box(u, 108).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.ZERO);
        leg.addOrReplaceChild(
                side + "_leg_bend",
                box(u, 118)
                        .addBox(-2, 0, -2, 4, 6, 4, new CubeDeformation(-0.001F))
                        .texOffs(left ? 112 : 80, 123)
                        .addBox(
                                -2, 5, -2, 4, 1, 4,
                                new CubeDeformation(0.099F)),
                PartPose.offset(0, 6, 0));
        joint(leg, side + "_leg_joint", u, 6, left);
        leg.addOrReplaceChild(
                side + "_leg_spike",
                box(left ? 108 : 76, 118).addBox(
                        -1, -1, -2.5F, 2, 2, 2,
                        new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(
                        0.0F, 5.5F, 0.0F,
                        0.0F, 0.0F, 0.7854F));
    }

    private static void joint(PartDefinition parent, String name, int u, float y, boolean mirror) {
        parent.addOrReplaceChild(name, box(u, 102).mirror(mirror)
                .addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3, new CubeDeformation(-0.125F)),
                PartPose.offset(0, y, 0));
    }

    private static Frame frame() {
        return new Frame();
    }

    private static final class Frame {
        private static final float TO_RADIANS =
                (float) Math.PI / 180.0F;
        private final float[][] rotations = new float[PART_COUNT][3];
        private final boolean[] defined = new boolean[PART_COUNT];

        private Frame body(float x, float y, float z) {
            return part(BODY, x, y, z);
        }

        private Frame torso(float x, float y, float z) {
            return part(TORSO, x, y, z);
        }

        private Frame head(float x, float y, float z) {
            return part(HEAD, x, y, z);
        }

        private Frame beak(float x, float y, float z) {
            return part(BEAK, x, y, z);
        }

        private Frame leftArm(float x, float y, float z) {
            return part(LEFT_ARM, x, y, z);
        }

        private Frame leftForearm(float x, float y, float z) {
            return part(LEFT_FOREARM, x, y, z);
        }

        private Frame rightArm(float x, float y, float z) {
            return part(RIGHT_ARM, x, y, z);
        }

        private Frame rightForearm(float x, float y, float z) {
            return part(RIGHT_FOREARM, x, y, z);
        }

        private Frame leftLeg(float x, float y, float z) {
            return part(LEFT_LEG, x, y, z);
        }

        private Frame leftLowerLeg(float x, float y, float z) {
            return part(LEFT_LOWER_LEG, x, y, z);
        }

        private Frame rightLeg(float x, float y, float z) {
            return part(RIGHT_LEG, x, y, z);
        }

        private Frame rightLowerLeg(float x, float y, float z) {
            return part(RIGHT_LOWER_LEG, x, y, z);
        }

        private Frame tail(
                int segment, float x, float y, float z) {
            return part(TAIL + segment, x, y, z);
        }

        private Frame part(
                int index, float x, float y, float z) {
            defined[index] = true;
            rotations[index][0] = x * TO_RADIANS;
            rotations[index][1] = y * TO_RADIANS;
            rotations[index][2] = z * TO_RADIANS;
            return this;
        }

        private boolean defined(
                int outputPart, boolean mirror) {
            int sourcePart = mirror
                    ? MIRRORED_PART[outputPart] : outputPart;
            return defined[sourcePart];
        }

        private float angle(
                int outputPart, int axis, boolean mirror) {
            int sourcePart = mirror
                    ? MIRRORED_PART[outputPart] : outputPart;
            float value = rotations[sourcePart][axis];
            return mirror && axis > 0 ? -value : value;
        }
    }

    private static PartDefinition empty(
            PartDefinition parent, String name, PartPose pose) {
        return parent.addOrReplaceChild(
                name, CubeListBuilder.create(), pose);
    }

    private static CubeListBuilder box(int u, int v) {
        return CubeListBuilder.create().texOffs(u, v);
    }

    private static ModelPart part(
            Model_1_21_2plus model, String name) {
        return model.jojo_ripples$getAnyDescendantWithName(name)
                .orElse(null);
    }

    private static void rotate(
            ModelPart part, float x, float y, float z) {
        if (part != null) {
            part.xRot = x;
            part.yRot = y;
            part.zRot = z;
        }
    }
}
