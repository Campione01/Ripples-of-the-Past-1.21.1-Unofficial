package rotp.addons.purplehaze.ability;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/*
 * 1.16 purple_haze_heavy_punch and purple_haze_viral_punch (060 InitStands, StandEntityHeavyAttack builders) performed
 * for one tick, and PurpleHazeModel.initActionPoses left both to HumanoidStandModel's heavyAttackAnim: the windup ends on
 * the impact, shown on the hit, and the RECOVERY (heavyImpact -> idle eased by max(2 (pr - 1) + 1, 0)) holds the impact
 * for its first half before going back to idle. The clips put the impact on PERFORM, RECOVERY one tick later, and keep
 * every impact channel to the middle of the recovery segment.
 */
@GameTestHolder(PurpleHazeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurpleHazeHeavyClipGameTests {
    private static final String ANIMS = "/assets/rotp_ph/stand_skins/purple_haze/assets/rotp_ph/animations/"
            + "purple_haze.animation.json";
    private static final float IMPACT = 0.35F;

    private PurpleHazeHeavyClipGameTests() {}

    @GameTest(template = "empty", batch = "ph_heavy_clip", timeoutTicks = 20)
    public static void heavyClipsHoldTheImpactOverHalfTheRecoveryAsIn116(GameTestHelper helper) {
        JsonObject clips = readJson(helper).getAsJsonObject("animations");
        List<String> wrong = new ArrayList<>();
        check(helper, clips, PurpleHazeAddon.HEAVY_PUNCH_NAME, "heavy_punch", wrong);
        check(helper, clips, "grab_heavy_punch", "heavy_punch", wrong);
        check(helper, clips, PurpleHazeAddon.VIRAL_PUNCH_NAME, "viral_punch", wrong);
        helper.assertTrue(wrong.isEmpty(), "A heavy hits on its perform's only tick, so its clip must show the impact on"
                + " PERFORM, enter RECOVERY one tick (0.05 s) later and hold the impact over the first half of the"
                + " recovery, as 1.16's heavyAttackAnim did: " + wrong);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, JsonObject clips, String abilityName, String clipName,
            List<String> wrong) {
        Ability ability = PurpleHazeAddon.PURPLE_HAZE.get().getBaseMoveset().getAbility(abilityName);
        helper.assertTrue(ability instanceof EntityActionAbility, "setup: no action " + abilityName);
        EntityActionAbility action = (EntityActionAbility) ability;
        EntityActionInstance instance = action.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(instance != null, "setup: " + abilityName + " made no action");
        float perform = instance.phasesLength.getFloat(ActionPhase.PERFORM);
        if (perform != 1) {
            wrong.add(abilityName + " performs for " + perform + " ticks");
        }
        String anim = action.getEntityAnim(null).name();
        // the clip of the moveset name, else the core's legacy alias (grab_heavy_punch -> heavy_punch)
        String played = clips.has(anim) ? anim : "grab_heavy_punch".equals(anim) ? "heavy_punch" : null;
        helper.assertTrue(clipName.equals(played), "setup: " + abilityName + " plays " + played + ", not " + clipName);
        JsonObject clip = clips.getAsJsonObject(clipName);
        float performAt = Float.NaN;
        float recoveryAt = Float.NaN;
        for (Map.Entry<String, JsonElement> marker : clip.getAsJsonObject("timeline").entrySet()) {
            String instruction = marker.getValue().isJsonPrimitive() ? marker.getValue().getAsString().replace(" ", "") : "";
            if ("phase=PERFORM;".equals(instruction)) {
                performAt = Float.parseFloat(marker.getKey());
            }
            else if ("phase=RECOVERY;".equals(instruction)) {
                recoveryAt = Float.parseFloat(marker.getKey());
            }
        }
        if (Math.abs(performAt - IMPACT) > 0.0001F || Math.abs(recoveryAt - performAt - 0.05F) > 0.0002F) {
            wrong.add(clipName + ": PERFORM " + performAt + " (impact " + IMPACT + "), RECOVERY " + recoveryAt);
            return;
        }
        float length = clip.get("animation_length").getAsFloat();
        float holdEnd = (recoveryAt + length) / 2;
        int channels = 0;
        int returning = 0;
        for (Map.Entry<String, JsonElement> bone : clip.getAsJsonObject("bones").entrySet()) {
            for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                if (!channel.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement impactValue = null;
                JsonElement heldTo = null;
                JsonElement endValue = null;
                float endAt = -1;
                String early = null;
                for (Map.Entry<String, JsonElement> key : channel.getValue().getAsJsonObject().entrySet()) {
                    float at = Float.parseFloat(key.getKey());
                    if (Math.abs(at - IMPACT) < 0.0001F) {
                        impactValue = key.getValue();
                    }
                    else if (Math.abs(at - holdEnd) < 0.0001F) {
                        heldTo = key.getValue();
                    }
                    else if (at > IMPACT && at < holdEnd) {
                        early = key.getKey();
                    }
                    if (at > endAt) {
                        endAt = at;
                        endValue = key.getValue();
                    }
                }
                if (impactValue == null) {
                    continue;
                }
                channels++;
                if (!impactValue.equals(heldTo) || early != null) {
                    wrong.add(clipName + " " + bone.getKey() + " " + channel.getKey() + " must hold the impact "
                            + impactValue + " to the recovery's middle " + holdEnd + ": hold key " + heldTo
                            + (early != null ? ", earlier key " + early : ""));
                }
                else if (endAt > holdEnd && !impactValue.equals(endValue)) {
                    returning++;
                }
            }
        }
        if (channels != 3 || returning != 2) {
            wrong.add("setup: " + clipName + " has " + channels + " channels keyed at the impact (expected 3), "
                    + returning + " of them going back to idle after the hold (expected 2: body, right_arm)");
        }
    }

    private static JsonObject readJson(GameTestHelper helper) {
        try (InputStream in = PurpleHazeHeavyClipGameTests.class.getResourceAsStream(ANIMS)) {
            helper.assertTrue(in != null, "Missing asset " + ANIMS);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + ANIMS, e);
        }
    }
}
