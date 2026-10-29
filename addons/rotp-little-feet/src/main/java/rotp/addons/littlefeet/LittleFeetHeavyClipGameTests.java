package rotp.addons.littlefeet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/*
 * 1.16 LittleFeetModel.initActionPoses puts its own HEAVY_ATTACK_FINISHER (the heavy slash) but no HEAVY_ATTACK, so
 * little_feet_heavy_punch drew HumanoidStandModel's default heavy. The port's heavy_punch keeps the core default's clip,
 * the official hooking punch with the core's one-tick perform segment (RECOVERY 0.4833 since core K5-31); the finisher
 * keeps its own clip from the donor's slash poses.
 * The finisher and the shrink slash drew those poses by phase (PosedActionAnimation: WINDUP through the slash poses,
 * PERFORM the strike pose held, RECOVERY the strike pose held to 90% then back to idle), so their clips carry phase
 * markers and show the same pose as 1.16 at every point of every phase, whatever the speed-dependent phase lengths.
 */
@GameTestHolder(LittleFeetAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LittleFeetHeavyClipGameTests {
    private static final String LF_ANIMS = "/assets/rotp_littlefeet/stand_skins/little_feet/assets/rotp_littlefeet/"
            + "animations/little_feet.animation.json";
    private static final JsonObject HOOKING_PUNCH_RIGHT_ARM = JsonParser.parseString(
            "{\"0.0\":{\"vector\":[29.5,0,20]},\"0.3333\":{\"vector\":[9.25322,32.14662,33.30985]},"
                    + "\"0.4667\":{\"vector\":[-17.44969,18.61317,85.35624],\"easing\":\"easeInCubic\"},"
                    + "\"0.5333\":{\"vector\":[-17.44969,18.61317,85.35624]},"
                    + "\"1.0333\":{\"vector\":[-17.44969,18.61317,85.35624]},"
                    + "\"1.2\":{\"vector\":[-14.78217,-2.57594,35.3342]}}").getAsJsonObject();
    private static final JsonObject HOOKING_PUNCH_RIGHT_ARM_BEND = JsonParser.parseString(
            "{\"0.0\":{\"vector\":[-90,-10,-90]},\"0.3333\":{\"vector\":[-90,-31,-90]},"
                    + "\"0.4\":{\"vector\":[-114.00365,-19.49519,6.86178]},\"0.4333\":{\"vector\":[-103.01,0,0]},"
                    + "\"0.4667\":{\"vector\":[0,0,0]},\"0.5333\":{\"vector\":[0,0,0]},\"1.0333\":{\"vector\":[0,0,0]},"
                    + "\"1.2\":{\"vector\":[-96.90944,29.46837,-58.8207]}}").getAsJsonObject();

    private LittleFeetHeavyClipGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyPunchKeepsTheCoreDefaultHookingPunch(GameTestHelper helper) {
        JsonObject animations = animations(helper);
        JsonObject heavy = animations.getAsJsonObject("heavy_punch");
        helper.assertTrue(heavy != null, "Little Feet has no heavy_punch clip");
        JsonObject expectedTimeline = JsonParser.parseString(
                "{\"0.0\":\"phase = WINDUP;\",\"0.4333\":\"phase = PERFORM;\",\"0.4833\":\"phase = RECOVERY;\"}")
                .getAsJsonObject();
        helper.assertTrue(expectedTimeline.equals(heavy.getAsJsonObject("timeline")),
                "Little Feet's heavy_punch must keep the core default's PERFORM 0.4333 / RECOVERY 0.4833 (the thrust"
                        + " ends 0.4667, the hold plays over the recovery): " + heavy.getAsJsonObject("timeline"));
        JsonObject bones = heavy.getAsJsonObject("bones");
        helper.assertTrue(HOOKING_PUNCH_RIGHT_ARM.equals(bones.getAsJsonObject("right_arm").getAsJsonObject("rotation"))
                        && HOOKING_PUNCH_RIGHT_ARM_BEND.equals(
                                bones.getAsJsonObject("right_arm_bend").getAsJsonObject("rotation")),
                "Little Feet's heavy_punch must stay the core default's hooking punch: right_arm "
                        + bones.get("right_arm") + ", right_arm_bend " + bones.get("right_arm_bend"));
        JsonObject finisher = animations.getAsJsonObject(LittleFeetAddon.FINISHER_NAME);
        helper.assertTrue(finisher != null && !finisher.equals(heavy),
                "the finisher must keep its own clip from the donor's heavy slash poses");
        helper.succeed();
    }

    // port bone <- 1.16 part: body, rightArm, rightForeArm, leftArm, leftForeArm, rightLeg, rightLowerLeg, leftLeg,
    // leftLowerLeg (upperPart and torso are 0 in every pose, as in the idle pose)
    private static final String[] BONES = {"body", "right_arm", "right_arm_bend", "left_arm", "left_arm_bend",
            "right_leg", "right_leg_bend", "left_leg", "left_leg_bend"};
    // 044 LittleFeetModel.initIdlePose :317-319
    private static final float[][] IDLE = {{0, 0, 0}, {0, -7.5F, 22.5F}, {-35.5987F, -10.8036F, 6.3253F},
            {-9.8161F, -14.9084F, -20.3018F}, {-21.2351F, 10.6734F, 6.4996F}, {-19.7988F, 28.0265F, 3.2343F},
            {32.5F, 0, 0}, {-1.394F, -29.001F, -11.9571F}, {30, 0, 0}};
    // 044 LittleFeetModel :306-308
    private static final float[][] SLASH0 = {{0, 0, 0}, {0, 0, 32.5F}, {0, 0, -62.5F}, {0, -17.5F, 0},
            {0, -90, 0}, {-27.2989F, 3.4553F, 6.6607F}, {40, 0, 0}, {-7.3242F, -1.6189F, -12.3964F}, {37.5F, 0, 0}};
    private static final float[][] SLASH1 = {{0, 45, 0}, {0, 0, 52.5F}, {-18.4474F, 60.8721F, 69.0997F},
            {37.0662F, -26.9462F, -18.896F}, {-63.2187F, -20.2935F, 80.0707F}, {-27.2989F, 3.4553F, 6.6607F},
            {40, 0, 0}, {-7.3242F, -1.6189F, -12.3964F}, {37.5F, 0, 0}};
    private static final float[][] SLASH2 = {{0, -35, 0}, {-66.638F, -2.9343F, 70.2304F},
            {-62.1392F, -8.8604F, -4.6547F}, {37.0662F, -26.9462F, -18.896F}, {-63.2187F, -20.2935F, 80.0707F},
            {-27.2989F, 3.4553F, 6.6607F}, {40, 0, 0}, {-7.3242F, -1.6189F, -12.3964F}, {37.5F, 0, 0}};
    // 044 LittleFeetModel :310-312
    private static final float[][] HEAVY_SLASH0 = {{0, 0, 0}, {-90, 0, 0}, {0, 0, 0}, {-35, 0, 0},
            {-94.353F, -54.9222F, 93.5646F}, {0, 0, 10}, {32.5F, 0, 0}, {-5.3903F, 3.7317F, -11.9384F}, {30, 0, 0}};
    private static final float[][] HEAVY_SLASH1 = {{0, 47.5F, 0}, {-90, 85, 0}, {0, 0, -105}, {-35, 0, 0},
            {-92.5024F, -2.4976F, 90.1091F}, {0, 0, 10}, {32.5F, 0, 0}, {-5.3903F, 3.7317F, -11.9384F}, {30, 0, 0}};
    private static final float[][] HEAVY_SLASH2 = {{0, -60, 0}, {-90, 12.5F, 0}, {0, 0, -40}, {47.5F, -35, 0},
            {-92.5024F, -2.4976F, 90.1091F}, {0, 0, 10}, {32.5F, 0, 0}, {-5.3903F, 3.7317F, -11.9384F}, {30, 0, 0}};
    private static final float[] COMPLETIONS = {0, 0.05F, 0.1F, 0.15F, 0.3F, 0.5F, 0.7F, 0.8F, 0.85F, 0.9F, 0.95F, 1};

    // 1.16 :309 SHRINK: WINDUP slash0 -> (0.8) slash1 -> slash2, no PERFORM pose (filled rigid with slash2),
    // RECOVERY slash2 -> (0.9) slash2 -> idle
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shrinkSlashPlaysTheDonorShrinkPosesByPhase(GameTestHelper helper) {
        checkPhasedClip(helper, LittleFeetAddon.SHRINK_SLASH_NAME,
                new float[] {0, 0.8F, 1}, new float[][][] {SLASH0, SLASH1, SLASH2}, SLASH2);
    }

    // 1.16 :313 HEAVY_ATTACK_FINISHER: WINDUP idle -> (0.15) heavyslash0 -> (0.8) heavyslash1 -> heavyslash2, no
    // PERFORM pose (filled rigid with heavyslash2), RECOVERY heavyslash2 -> (0.9) heavyslash2 -> idle
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void finisherPlaysTheDonorHeavySlashPosesByPhase(GameTestHelper helper) {
        checkPhasedClip(helper, LittleFeetAddon.FINISHER_NAME,
                new float[] {0, 0.15F, 0.8F, 1}, new float[][][] {IDLE, HEAVY_SLASH0, HEAVY_SLASH1, HEAVY_SLASH2},
                HEAVY_SLASH2);
    }

    private static void checkPhasedClip(GameTestHelper helper, String name, float[] windupPoints,
            float[][][] windupPoses, float[][] strike) {
        Ability ability = LittleFeetAddon.LITTLE_FEET.get().getBaseMoveset().getAbility(name);
        helper.assertTrue(ability instanceof EntityActionAbility, "setup: no " + name);
        EntityActionAbility action = (EntityActionAbility) ability;
        EntityActionInstance instance = action.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(instance != null, "setup: " + name + " made no action");
        helper.assertTrue(instance.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                name + " must perform for one tick, as 1.16's: " + instance.phasesLength.getFloat(ActionPhase.PERFORM));
        helper.assertTrue(name.equals(action.getEntityAnim(null).name()),
                "setup: " + name + " plays " + action.getEntityAnim(null).name());
        JsonObject clip = animations(helper).getAsJsonObject(name);
        helper.assertTrue(clip != null, "Little Feet has no " + name + " clip");
        JsonObject timeline = clip.getAsJsonObject("timeline");
        helper.assertTrue(timeline != null, name + " has no phase markers, so it plays by elapsed time, not by phase");
        Map<ActionPhase, Float> marks = new TreeMap<>();
        for (Map.Entry<String, JsonElement> marker : timeline.entrySet()) {
            String instruction = marker.getValue().getAsString().replace(" ", "");
            for (ActionPhase phase : new ActionPhase[] {ActionPhase.WINDUP, ActionPhase.PERFORM, ActionPhase.RECOVERY}) {
                if (("phase=" + phase.name() + ";").equals(instruction)) {
                    marks.put(phase, Float.parseFloat(marker.getKey()));
                }
            }
        }
        helper.assertTrue(marks.size() == 3 && timeline.size() == 3,
                name + " must mark WINDUP, PERFORM and RECOVERY: " + timeline);
        float end = clip.get("animation_length").getAsFloat();
        float[][] segments = {{marks.get(ActionPhase.WINDUP), marks.get(ActionPhase.PERFORM)},
                {marks.get(ActionPhase.PERFORM), marks.get(ActionPhase.RECOVERY)}, {marks.get(ActionPhase.RECOVERY), end}};
        String[] phaseNames = {"WINDUP", "PERFORM", "RECOVERY"};
        for (int phase = 0; phase < 3; phase++) {
            for (float completion : COMPLETIONS) {
                float[][] expected = phase == 0 ? posesAt(completion, windupPoints, windupPoses)
                        : phase == 1 ? strike
                        : posesAt(completion, new float[] {0, 0.9F, 1}, new float[][][] {strike, strike, IDLE});
                // the core's FIT_PHASE_LENGTH mapping of a phase onto its clip segment
                float seconds = segments[phase][0] + (segments[phase][1] - segments[phase][0]) * completion;
                for (int bone = 0; bone < BONES.length; bone++) {
                    float[] actual = sample(helper, name, clip, BONES[bone], seconds);
                    for (int axis = 0; axis < 3; axis++) {
                        helper.assertTrue(Math.abs(actual[axis] - expected[bone][axis]) < 0.01F,
                                name + " " + BONES[bone] + " at " + phaseNames[phase] + " " + completion + " (clip "
                                        + seconds + " s) shows " + Arrays.toString(actual) + ", 1.16 drew "
                                        + Arrays.toString(expected[bone]));
                    }
                }
            }
        }
        helper.succeed();
    }

    // 1.16 ModelPoseTransitionMultiple: linear between the poses at their points
    private static float[][] posesAt(float completion, float[] points, float[][][] poses) {
        int i = 0;
        while (i < points.length - 2 && completion >= points[i + 1]) {
            i++;
        }
        float t = Math.min(1, Math.max(0, (completion - points[i]) / (points[i + 1] - points[i])));
        float[][] out = new float[BONES.length][3];
        for (int bone = 0; bone < BONES.length; bone++) {
            for (int axis = 0; axis < 3; axis++) {
                out[bone][axis] = poses[i][bone][axis] + (poses[i + 1][bone][axis] - poses[i][bone][axis]) * t;
            }
        }
        return out;
    }

    private static float[] sample(GameTestHelper helper, String name, JsonObject clip, String bone, float seconds) {
        JsonObject boneObj = clip.getAsJsonObject("bones").getAsJsonObject(bone);
        helper.assertTrue(boneObj != null && boneObj.get("rotation") instanceof JsonObject,
                name + " has no keyed rotation for " + bone);
        TreeMap<Float, float[]> keys = new TreeMap<>();
        for (Map.Entry<String, JsonElement> key : boneObj.getAsJsonObject("rotation").entrySet()) {
            helper.assertTrue(key.getValue().isJsonArray(),
                    name + " " + bone + " key " + key.getKey() + " must be a plain linear key: " + key.getValue());
            float[] v = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                v[axis] = key.getValue().getAsJsonArray().get(axis).getAsFloat();
            }
            keys.put(Float.parseFloat(key.getKey()), v);
        }
        Map.Entry<Float, float[]> before = keys.floorEntry(seconds + 1.0E-5F);
        Map.Entry<Float, float[]> after = keys.ceilingEntry(seconds + 1.0E-5F);
        if (before == null) {
            return after.getValue();
        }
        if (after == null || after.getKey().equals(before.getKey())) {
            return before.getValue();
        }
        float t = Math.max(0, (seconds - before.getKey()) / (after.getKey() - before.getKey()));
        float[] out = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = before.getValue()[axis] + (after.getValue()[axis] - before.getValue()[axis]) * t;
        }
        return out;
    }

    private static JsonObject animations(GameTestHelper helper) {
        try (InputStream in = LittleFeetHeavyClipGameTests.class.getResourceAsStream(LF_ANIMS)) {
            helper.assertTrue(in != null, "Missing asset " + LF_ANIMS);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + LF_ANIMS, e);
        }
    }
}
