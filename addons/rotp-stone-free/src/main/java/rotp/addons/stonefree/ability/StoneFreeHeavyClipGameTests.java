package rotp.addons.stonefree.ability;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/*
 * 1.16 StoneFreeStringPunch performed for one tick (no standPerformDuration) and StoneFreeModel's
 * HEAVY_ATTACK_FINISHER wound up through five poses into the impact pose, held it over the perform and the first half
 * of the recovery. The string punch performs for one tick again, so its clip holds the impact on PERFORM and enters
 * RECOVERY one tick later instead of spending the start of the recovery inside the hit tick, and every channel of the
 * impact pose, the legs too, holds until the middle of the recovery segment.
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeHeavyClipGameTests {
    private static final String ANIMS = "/assets/rotp_stone_free/stand_skins/stone_free/assets/rotp_stone_free/"
            + "animations/stone_free.animation.json";

    private StoneFreeHeavyClipGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 20)
    public static void stringPunchClipPutsTheImpactOnItsOneTickPerform(GameTestHelper helper) {
        Ability ability = StoneFreeAddon.STONE_FREE.get().getBaseMoveset().getAbility(StoneFreeAddon.STRING_PUNCH_NAME);
        helper.assertTrue(ability instanceof EntityActionAbility, "setup: no string punch");
        EntityActionAbility action = (EntityActionAbility) ability;
        EntityActionInstance instance = action.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(instance != null, "setup: the string punch made no action");
        helper.assertTrue(instance.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                "The string punch must perform for one tick, as 1.16's: "
                        + instance.phasesLength.getFloat(ActionPhase.PERFORM));
        helper.assertTrue("finisher_punch".equals(action.getEntityAnim(null).name()),
                "setup: the string punch plays " + action.getEntityAnim(null).name());
        JsonObject clips = readJson(helper).getAsJsonObject("animations");
        float performAt = Float.NaN;
        float recoveryAt = Float.NaN;
        for (Map.Entry<String, JsonElement> marker : clips.getAsJsonObject("finisher_punch")
                .getAsJsonObject("timeline").entrySet()) {
            String instruction = marker.getValue().isJsonPrimitive() ? marker.getValue().getAsString().replace(" ", "") : "";
            if ("phase=PERFORM;".equals(instruction)) {
                performAt = Float.parseFloat(marker.getKey());
            }
            else if ("phase=RECOVERY;".equals(instruction)) {
                recoveryAt = Float.parseFloat(marker.getKey());
            }
        }
        // the sixth donor pose, the thrust with the arm straight out, is the 1.0 key
        helper.assertTrue(Math.abs(performAt - 1.0F) < 0.0001F && Math.abs(recoveryAt - performAt - 0.05F) < 0.0002F,
                "A heavy hits on its perform's only tick, so finisher_punch must hold the impact on PERFORM and enter"
                        + " RECOVERY one tick (0.05 s) later: PERFORM " + performAt + " (impact 1.0), RECOVERY " + recoveryAt);
        // 1.16 RECOVERY: pose6 -> (0.5) pose6 -> idle, legs included; the recovery plays recoveryAt..length
        JsonObject finisher = clips.getAsJsonObject("finisher_punch");
        float holdEnd = (recoveryAt + finisher.get("animation_length").getAsFloat()) / 2;
        int poseChannels = 0;
        for (Map.Entry<String, JsonElement> bone : finisher.getAsJsonObject("bones").entrySet()) {
            JsonElement rotation = bone.getValue().getAsJsonObject().get("rotation");
            if (rotation == null || !rotation.isJsonObject() || !rotation.getAsJsonObject().has("1.0")) {
                continue;
            }
            poseChannels++;
            JsonElement impact = rotation.getAsJsonObject().get("1.0");
            JsonElement heldTo = null;
            String early = null;
            for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
                float at = Float.parseFloat(key.getKey());
                if (Math.abs(at - holdEnd) < 0.0001F) {
                    heldTo = key.getValue();
                }
                else if (at > 1.0001F && at < holdEnd) {
                    early = key.getKey();
                }
            }
            helper.assertTrue(impact.equals(heldTo) && early == null,
                    "finisher_punch " + bone.getKey() + " must hold the impact pose over the first half of the recovery"
                            + " (to " + holdEnd + ") as 1.16's StoneFreeModel did: hold key " + heldTo
                            + ", impact " + impact + (early != null ? ", earlier key " + early : ""));
        }
        helper.assertTrue(poseChannels == 10, "setup: finisher_punch pose6 channels " + poseChannels + ", expected 10");
        helper.succeed();
    }

    /*
     * 1.16 STONE_FREE_HEAVY_PUNCH used HumanoidStandModel's heavyAttackAnim (StoneFreeModel overrides only the
     * finisher), whose RECOVERY eased impact -> idle by max(2 * (pr - 1) + 1, 0): the impact pose held for the first
     * half of the recovery, then the Stand went back to idle.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 20)
    public static void heavyPunchClipHoldsItsImpactOverHalfTheRecovery(GameTestHelper helper) {
        Ability ability = StoneFreeAddon.STONE_FREE.get().getBaseMoveset().getAbility("heavy_punch");
        helper.assertTrue(ability instanceof EntityActionAbility, "setup: no heavy punch");
        EntityActionAbility action = (EntityActionAbility) ability;
        EntityActionInstance instance = action.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(instance != null, "setup: the heavy punch made no action");
        helper.assertTrue(instance.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                "The heavy punch must perform for one tick, as 1.16's: "
                        + instance.phasesLength.getFloat(ActionPhase.PERFORM));
        helper.assertTrue("heavy_punch".equals(action.getEntityAnim(null).name()),
                "setup: the heavy punch plays " + action.getEntityAnim(null).name());
        JsonObject heavy = readJson(helper).getAsJsonObject("animations").getAsJsonObject("heavy_punch");
        float performAt = Float.NaN;
        float recoveryAt = Float.NaN;
        for (Map.Entry<String, JsonElement> marker : heavy.getAsJsonObject("timeline").entrySet()) {
            String instruction = marker.getValue().isJsonPrimitive() ? marker.getValue().getAsString().replace(" ", "") : "";
            if ("phase=PERFORM;".equals(instruction)) {
                performAt = Float.parseFloat(marker.getKey());
            }
            else if ("phase=RECOVERY;".equals(instruction)) {
                recoveryAt = Float.parseFloat(marker.getKey());
            }
        }
        helper.assertTrue(Math.abs(performAt - 0.55F) < 0.0001F && Math.abs(recoveryAt - performAt - 0.05F) < 0.0002F,
                "heavy_punch must hold the impact (0.55) on PERFORM and enter RECOVERY one tick later: PERFORM "
                        + performAt + ", RECOVERY " + recoveryAt);
        float holdEnd = (recoveryAt + heavy.get("animation_length").getAsFloat()) / 2;
        int poseChannels = 0;
        for (Map.Entry<String, JsonElement> bone : heavy.getAsJsonObject("bones").entrySet()) {
            JsonElement rotation = bone.getValue().getAsJsonObject().get("rotation");
            if (rotation == null || !rotation.isJsonObject() || !rotation.getAsJsonObject().has("0.55")) {
                continue;
            }
            poseChannels++;
            JsonElement impact = rotation.getAsJsonObject().get("0.55");
            JsonElement heldTo = null;
            String early = null;
            for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
                float at = Float.parseFloat(key.getKey());
                if (Math.abs(at - holdEnd) < 0.0001F) {
                    heldTo = key.getValue();
                }
                else if (at > 0.5501F && at < holdEnd) {
                    early = key.getKey();
                }
            }
            helper.assertTrue(impact.equals(heldTo) && early == null,
                    "heavy_punch " + bone.getKey() + " must hold the impact pose over the first half of the recovery"
                            + " (to " + holdEnd + ") as 1.16's heavyAttackAnim did: hold key " + heldTo
                            + ", impact " + impact + (early != null ? ", earlier key " + early : ""));
        }
        helper.assertTrue(poseChannels == 3, "setup: heavy_punch impact channels " + poseChannels + ", expected 3");
        helper.succeed();
    }

    private static JsonObject readJson(GameTestHelper helper) {
        try (InputStream in = StoneFreeHeavyClipGameTests.class.getResourceAsStream(ANIMS)) {
            helper.assertTrue(in != null, "Missing asset " + ANIMS);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + ANIMS, e);
        }
    }
}
