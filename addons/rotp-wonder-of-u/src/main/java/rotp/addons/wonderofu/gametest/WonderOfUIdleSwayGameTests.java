package rotp.addons.wonderofu.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandIdleSway;

/**
 * 1.16 drew Wonder of U with its Java WonderOfYouModel (043 WonderOfYouRenderer registers rotp_wou:wonder_of_you; the jar
 * ships no animations/wonder_of_you.animation.json, so the core's GeckoAnimLoader gave it no gecko animator), and
 * StandEntityRenderer.idlePoseSwaying moved the idle model by sin((ticks - idleLoopTickStamp) * 0.04) * 0.04 block, down
 * first, held still while the Stand was visible for all, swinging an arm, not following its user, or while the user
 * sneaked. The core holds an idle still only when the clip carries the mark and keys the sway on stand_pos
 * (StandIdleSway); the port's idle keeps its own 3 s loop and takes the sine over 8 s, so the clip is 24 s long.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUIdleSwayGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ANIMS =
            "/assets/rotp_wou/stand_skins/wonder_of_you/assets/rotp_wou/animations/wonder_of_you.animation.json";

    private WonderOfUIdleSwayGameTests() {}

    @GameTest(template = "empty", batch = "wou_idle_sway", timeoutTicks = 40)
    public static void idleSwaysAndHoldsStillAsThe116JavaModel(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        checkClip(helper, wrong);

        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "wou-sway")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "setup: could not summon Wonder of U");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isVisibleForAll(),
                    "setup: Wonder of U is not out following its user");
            if (!StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle is held still for a user who stands, where 1.16 swayed it");
            }
            user.setShiftKeyDown(true);
            if (StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle sways while its user sneaks");
            }
            user.setShiftKeyDown(false);
            stand.setCanFollowUser(false);
            if (StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle sways while the Stand does not follow its user");
            }
            stand.setCanFollowUser(true);
        }
        finally {
            if (power != null && power.isSummoned()) {
                WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 swayed Wonder of U's idle as a Java model and held it still unless"
                + " it followed a user who did not sneak: " + wrong);
        LOGGER.info("R770_WOU_IDLE_SWAY_PASS idle_sway ");
        helper.succeed();
    }

    private static void checkClip(GameTestHelper helper, List<String> wrong) {
        JsonObject clip = readJson(helper).getAsJsonObject("animations").getAsJsonObject("idle");
        helper.assertTrue(clip != null, "setup: no idle clip");
        JsonObject timeline = clip.getAsJsonObject("timeline");
        JsonElement mark = timeline != null ? timeline.get("0") : null;
        if (mark == null || !mark.isJsonPrimitive() || !mark.getAsString().replace(" ", "")
                .equals(StandIdleSway.CLIP_FIELD + "=" + StandIdleSway.CLIP_VALUE + ";")) {
            wrong.add("the idle lost the mark of 1.16's renderer sway: " + timeline);
        }
        float length = clip.get("animation_length").getAsFloat();
        if (Math.abs(length - 24) > 1.0E-4F) {
            wrong.add("the idle is " + length + " s long, not 24 s (three 8 s sways over eight 3 s idle loops)");
        }
        JsonObject bones = clip.getAsJsonObject("bones");
        for (float t = 0; t <= length + 1.0E-4F; t += 0.005F) {
            float time = Math.min(t, length);
            float down = (float) (Math.sin(2 * Math.PI * time / 8) * 0.04F * 16);
            float[] got = sample(bones, StandIdleSway.SWAYED_BONE, "position", time);
            if (got == null || Math.abs(got[0]) > 0.005F || Math.abs(got[1] + down) > 0.005F
                    || Math.abs(got[2]) > 0.005F) {
                wrong.add("stand_pos is at " + (got == null ? "nothing" : got[1]) + " at " + time
                        + " s, where 1.16 moved the model " + down + " px down");
                break;
            }
        }
        // the idle's own motion: a 3 s loop, as the donor's clip
        String[][] channels = { { "head_rot", "position" }, { "right_arm", "rotation" }, { "left_arm", "position" },
                { "bone2", "position" }, { "bone", "rotation" } };
        for (String[] channel : channels) {
            for (float t = 0; t < 3; t += 0.05F) {
                float[] first = sample(bones, channel[0], channel[1], t);
                for (int loop = 1; loop < 8; loop++) {
                    float[] later = sample(bones, channel[0], channel[1], t + 3 * loop);
                    if (first == null || later == null || Math.abs(first[0] - later[0]) > 1.0E-3F
                            || Math.abs(first[1] - later[1]) > 1.0E-3F || Math.abs(first[2] - later[2]) > 1.0E-3F) {
                        wrong.add("the idle's own 3 s loop changed: " + channel[0] + " " + channel[1] + " at " + t
                                + " s and " + (t + 3 * loop) + " s");
                        return;
                    }
                }
            }
        }
    }

    private static float[] sample(JsonObject bones, String bone, String channel, float time) {
        JsonObject boneJson = bones.getAsJsonObject(bone);
        if (boneJson == null || !boneJson.has(channel)) {
            return null;
        }
        JsonElement channelJson = boneJson.get(channel);
        if (!channelJson.isJsonObject() || channelJson.getAsJsonObject().has("vector")) {
            return vector(channelJson);
        }
        TreeMap<Float, JsonElement> keys = new TreeMap<>();
        for (Map.Entry<String, JsonElement> key : channelJson.getAsJsonObject().entrySet()) {
            keys.put(Float.parseFloat(key.getKey()), key.getValue());
        }
        Map.Entry<Float, JsonElement> before = keys.floorEntry(time);
        Map.Entry<Float, JsonElement> after = keys.ceilingEntry(time);
        if (before == null) {
            return vector(after.getValue());
        }
        if (after == null || after.getKey().equals(before.getKey())) {
            return vector(before.getValue());
        }
        float f = (time - before.getKey()) / (after.getKey() - before.getKey());
        JsonElement to = after.getValue();
        String easing = to.isJsonObject() && to.getAsJsonObject().has("easing")
                ? to.getAsJsonObject().get("easing").getAsString() : "linear";
        f = switch (easing) {
            case "linear" -> f;
            case "easeInSine" -> 1 - (float) Math.cos(f * Math.PI / 2);
            case "easeOutSine" -> (float) Math.sin(f * Math.PI / 2);
            case "easeInOutSine" -> (float) (-(Math.cos(Math.PI * f) - 1) / 2);
            default -> throw new IllegalArgumentException(bone + ": easing " + easing + " not sampled");
        };
        float[] a = vector(before.getValue());
        float[] b = vector(to);
        return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
    }

    private static float[] vector(JsonElement key) {
        JsonElement values = key.isJsonObject() ? key.getAsJsonObject().get("vector") : key;
        return new float[] { values.getAsJsonArray().get(0).getAsFloat(), values.getAsJsonArray().get(1).getAsFloat(),
                values.getAsJsonArray().get(2).getAsFloat() };
    }

    private static JsonObject readJson(GameTestHelper helper) {
        try (InputStream in = WonderOfUIdleSwayGameTests.class.getResourceAsStream(ANIMS)) {
            helper.assertTrue(in != null, "Missing asset " + ANIMS);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + ANIMS, e);
        }
    }
}
