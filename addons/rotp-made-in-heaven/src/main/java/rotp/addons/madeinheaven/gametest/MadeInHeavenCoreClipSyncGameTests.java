package rotp.addons.madeinheaven.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;

/*
 * Made in Heaven's grab clips are copies of the core Star Platinum set. The core gave its grab_uppercut, made for the
 * old six-tick heavy perform, a one-tick perform segment (PERFORM on the impact, RECOVERY 0.05 s later) when the heavy
 * punch went back to 1.16's one-tick perform; the copy must keep the core's markers.
 */
@GameTestHolder(LightSpeedDashGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MadeInHeavenCoreClipSyncGameTests {
    private static final String MIH_ANIMS = "/assets/rotp_mih/stand_skins/made_in_heaven/assets/rotp_mih/animations/"
            + "made_in_heaven.animation.json";
    private static final String CORE_ANIMS = "/assets/jojo_ripples/stand_skins/star_platinum/assets/jojo_ripples/"
            + "animations/star_platinum.animation.json";

    private MadeInHeavenCoreClipSyncGameTests() {}

    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE, timeoutTicks = 20)
    public static void grabUppercutCopyKeepsTheCoreOneTickPerform(GameTestHelper helper) {
        // each file through its own mod's classes: the mods are separate modules
        JsonObject core = timeline(helper, JojoMod.class, CORE_ANIMS);
        JsonObject copy = timeline(helper, MadeInHeavenCoreClipSyncGameTests.class, MIH_ANIMS);
        helper.assertTrue("phase = PERFORM;".equals(core.has("0.4583") ? core.get("0.4583").getAsString() : null)
                && "phase = RECOVERY;".equals(core.has("0.5083") ? core.get("0.5083").getAsString() : null),
                "setup: the core grab_uppercut is not the one-tick 0.4583 / 0.5083 clip: " + core);
        helper.assertTrue(copy.equals(core), "Made in Heaven's grab_uppercut must keep the core's markers (PERFORM on the"
                + " impact, RECOVERY one tick later): core " + core + ", Made in Heaven " + copy);
        helper.succeed();
    }

    /*
     * 1.16 MadeInHeavenModel puts no action pose (no initActionPoses), so mih_heavy_punch and its finisher mih_chop
     * drew HumanoidStandModel's default heavy. The port keeps the core default's clip for heavy_punch, chop and
     * grab_heavy_punch: the official hooking punch with the core's one-tick perform segment (RECOVERY 0.4833, K5-31).
     */
    @GameTest(template = "empty", templateNamespace = LightSpeedDashGameTests.NAMESPACE, timeoutTicks = 20)
    public static void heavyClipsKeepTheCoreDefaultHookingPunch(GameTestHelper helper) {
        JsonObject animations = animations(helper, MadeInHeavenCoreClipSyncGameTests.class, MIH_ANIMS);
        JsonObject heavy = animations.getAsJsonObject("heavy_punch");
        helper.assertTrue(heavy != null, "Made in Heaven has no heavy_punch clip");
        JsonObject expectedTimeline = JsonParser.parseString(
                "{\"0.0\":\"phase = WINDUP;\",\"0.4333\":\"phase = PERFORM;\",\"0.4833\":\"phase = RECOVERY;\"}")
                .getAsJsonObject();
        helper.assertTrue(expectedTimeline.equals(heavy.getAsJsonObject("timeline")),
                "Made in Heaven's heavy_punch must keep the core default's PERFORM 0.4333 / RECOVERY 0.4833 (the"
                        + " thrust ends 0.4667, the hold plays over the recovery): " + heavy.getAsJsonObject("timeline"));
        JsonObject bones = heavy.getAsJsonObject("bones");
        helper.assertTrue(HOOKING_PUNCH_RIGHT_ARM.equals(bones.getAsJsonObject("right_arm").getAsJsonObject("rotation"))
                        && HOOKING_PUNCH_RIGHT_ARM_BEND.equals(
                                bones.getAsJsonObject("right_arm_bend").getAsJsonObject("rotation")),
                "Made in Heaven's heavy_punch must stay the core default's hooking punch: right_arm "
                        + bones.get("right_arm") + ", right_arm_bend " + bones.get("right_arm_bend"));
        for (String copy : new String[] {"chop", "grab_heavy_punch"}) {
            helper.assertTrue(heavy.equals(animations.get(copy)),
                    "Made in Heaven's " + copy + " must be the same clip as heavy_punch (1.16 drew the same humanoid"
                            + " heavy for the heavy and its finisher)");
        }
        helper.succeed();
    }

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

    private static JsonObject timeline(GameTestHelper helper, Class<?> owner, String path) {
        return animations(helper, owner, path).getAsJsonObject("grab_uppercut").getAsJsonObject("timeline");
    }

    private static JsonObject animations(GameTestHelper helper, Class<?> owner, String path) {
        try (InputStream in = owner.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "Missing asset " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
