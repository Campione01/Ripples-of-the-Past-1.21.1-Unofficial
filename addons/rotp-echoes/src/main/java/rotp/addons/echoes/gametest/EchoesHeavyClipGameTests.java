package rotp.addons.echoes.gametest;

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
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.type.StandType;

/*
 * 1.16 heavy attacks performed for one tick (StandEntityHeavyAttack kept the builder's standPerformDuration 1) and
 * their clips put the impact on the PERFORM marker with RECOVERY one tick after it, so the windup led into the impact
 * on the hit tick. The core heavy punch performs for one tick again, so these clips must not hide their strike in it.
 */
@GameTestHolder("rotp_echoes_pilot")
@PrefixGameTestTemplate(false)
public final class EchoesHeavyClipGameTests {
    private static final String ACT1_ANIMS = "/assets/rotp_echoes/stand_skins/echoes_act1/assets/rotp_echoes/"
            + "animations/echoes_act1.animation.json";
    private static final String ACT2_ANIMS = "/assets/rotp_echoes/stand_skins/echoes_act2/assets/rotp_echoes/"
            + "animations/echoes_act2.animation.json";

    private EchoesHeavyClipGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 20)
    public static void heavyClipsPutTheImpactOnTheirOneTickPerform(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        // ACT1 heavy: the punching arm is out at 0.0833; ACT2 heavy: the flip ends and the tail lands at 1.375;
        // ACT2 finisher: the tail sweep that throws the spike ends at 1.375
        check(helper, EchoesStands.ECHOES_ACT1.get(), "heavy_punch", ACT1_ANIMS, "heavy_punch", 0.0833F, wrong);
        check(helper, EchoesStands.ECHOES_ACT2.get(), "act2_heavy_punch", ACT2_ANIMS, "heavy_punch", 1.375F, wrong);
        check(helper, EchoesStands.ECHOES_ACT2.get(), "act2_finisher", ACT2_ANIMS, "finisher", 1.375F, wrong);
        helper.assertTrue(wrong.isEmpty(), "A heavy hits on its perform's only tick, so its clip must hold the impact on"
                + " PERFORM and enter RECOVERY one tick (0.05 s) later, as 1.16's heavy clips did: " + wrong);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, StandType type, String abilityName, String animsPath,
            String clipName, float impact, List<String> wrong) {
        Ability ability = type.getBaseMoveset().getAbility(abilityName);
        helper.assertTrue(ability instanceof EntityActionAbility, "setup: no action " + abilityName);
        EntityActionAbility action = (EntityActionAbility) ability;
        EntityActionInstance instance = action.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(instance != null, "setup: " + abilityName + " made no action");
        float perform = instance.phasesLength.getFloat(ActionPhase.PERFORM);
        if (perform != 1) {
            wrong.add(abilityName + " performs for " + perform + " ticks");
        }
        JsonObject clips = readJson(helper, animsPath).getAsJsonObject("animations");
        String played = playedClip(action, clips);
        helper.assertTrue(clipName.equals(played), "setup: " + abilityName + " plays " + played + ", not " + clipName);
        float performAt = Float.NaN;
        float recoveryAt = Float.NaN;
        for (Map.Entry<String, JsonElement> marker : clips.getAsJsonObject(clipName).getAsJsonObject("timeline").entrySet()) {
            String instruction = marker.getValue().isJsonPrimitive() ? marker.getValue().getAsString().replace(" ", "") : "";
            if ("phase=PERFORM;".equals(instruction)) {
                performAt = Float.parseFloat(marker.getKey());
            }
            else if ("phase=RECOVERY;".equals(instruction)) {
                recoveryAt = Float.parseFloat(marker.getKey());
            }
        }
        if (Math.abs(performAt - impact) > 0.0001F || Math.abs(recoveryAt - performAt - 0.05F) > 0.0002F) {
            wrong.add(clipName + " of " + animsPath + ": PERFORM " + performAt + " (impact " + impact + "), RECOVERY "
                    + recoveryAt);
        }
    }

    // the moveset name's own clip, else the core's ported fallback to the ability type's path without "stand_"
    private static String playedClip(EntityActionAbility ability, JsonObject clips) {
        String anim = ability.getEntityAnim(null).name();
        if (clips.has(anim)) {
            return anim;
        }
        String typePath = ability.abilityType.registryKey.getPath();
        if (clips.has(typePath)) {
            return typePath;
        }
        String bare = typePath.startsWith("stand_") ? typePath.substring("stand_".length()) : typePath;
        return clips.has(bare) ? bare : null;
    }

    private static JsonObject readJson(GameTestHelper helper, String path) {
        try (InputStream in = EchoesHeavyClipGameTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "Missing asset " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
