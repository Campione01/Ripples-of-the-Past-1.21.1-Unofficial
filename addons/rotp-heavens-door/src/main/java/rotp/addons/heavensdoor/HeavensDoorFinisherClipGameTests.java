package rotp.addons.heavensdoor;

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
import rotp.addons.heavensdoor.ability.HeavensDoorFinisherAbility;
import rotp.addons.heavensdoor.ability.RemoveBookAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/*
 * 1.16 HEAVENS_DOOR_FINISHER_PUNCH (InitStands :63) posed HeavensDoorFinisherPunch.FINISHER = new
 * StandPose("finisher") (:44), and GeckoStandAnimator.getAnims played the skin clip of that name, so the finisher
 * showed its own clip, not Remove Book's heavyPunch. The skin has no clip under the moveset name finisher_punch.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorFinisherClipGameTests {
    private static final String ANIMS = "/assets/rotp_hd/stand_skins/heavens_door/assets/rotp_hd/"
            + "animations/heavens_door.animation.json";

    private HeavensDoorFinisherClipGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void finisherPlaysTheDonorsFinisherClip(GameTestHelper helper) {
        Ability finisher = HeavensDoorAddon.HEAVENS_DOOR.get().getBaseMoveset().getAbility("finisher_punch");
        Ability removeBook = HeavensDoorAddon.HEAVENS_DOOR.get().getBaseMoveset()
                .getAbility(HeavensDoorAddon.REMOVE_BOOK_ID);
        helper.assertTrue(finisher instanceof HeavensDoorFinisherAbility && removeBook instanceof RemoveBookAbility,
                "setup: Heaven's Door lost its finisher or Remove Book");
        HeavensDoorFinisherAbility finisherAbility = (HeavensDoorFinisherAbility) finisher;
        String clip = finisherAbility.getEntityAnim(null).name();
        helper.assertTrue("finisher".equals(clip),
                "1.16's finisher posed StandPose(\"finisher\") and played the skin's finisher clip; the port's"
                        + " finisher requests " + clip);
        String removeBookClip = ((RemoveBookAbility) removeBook).getEntityAnim(null).name();
        helper.assertTrue("heavy_punch".equals(removeBookClip),
                "Remove Book must keep the 1.16 heavy attack pose, got " + removeBookClip);

        JsonObject clips = readJson(helper).getAsJsonObject("animations");
        helper.assertTrue(clips.has(clip) && clips.get(clip).isJsonObject(),
                "The skin has no clip '" + clip + "', so the finisher would fall back to another clip");
        float performAt = Float.NaN;
        float recoveryAt = Float.NaN;
        for (Map.Entry<String, JsonElement> marker : clips.getAsJsonObject(clip)
                .getAsJsonObject("timeline").entrySet()) {
            String instruction = marker.getValue().isJsonPrimitive()
                    ? marker.getValue().getAsString().replace(" ", "") : "";
            if ("phase=PERFORM;".equals(instruction)) {
                performAt = Float.parseFloat(marker.getKey());
            }
            else if ("phase=RECOVERY;".equals(instruction)) {
                recoveryAt = Float.parseFloat(marker.getKey());
            }
        }
        helper.assertTrue(Math.abs(performAt - 0.5833F) < 0.0001F && Math.abs(recoveryAt - 0.6667F) < 0.0001F,
                "The finisher clip must keep the donor's PERFORM 0.5833 / RECOVERY 0.6667, got "
                        + performAt + " / " + recoveryAt);

        EntityActionInstance action = finisherAbility.initActionOnAbilityUse(helper.getLevel(), null, null, null);
        helper.assertTrue(action != null, "setup: the finisher made no action");
        helper.assertTrue(action.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                "The finisher must perform for one tick, as 1.16's StandEntityHeavyAttack builder: "
                        + action.phasesLength.getFloat(ActionPhase.PERFORM));
        helper.succeed();
    }

    private static JsonObject readJson(GameTestHelper helper) {
        try (InputStream in = HeavensDoorFinisherClipGameTests.class.getResourceAsStream(ANIMS)) {
            helper.assertTrue(in != null, "Missing asset " + ANIMS);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + ANIMS, e);
        }
    }
}
