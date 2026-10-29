package rotp.addons.diegosworld.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandIdleSway;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 THEWORLDRenderer drew Diego's The World with a Java model (THEWORLDModel, no gecko animations), so
 * StandEntityRenderer.idlePoseSwaying moved its idle up and down by sin(ticks * 0.04) * 0.04 block and held it still
 * while the user sneaked or the Stand did not follow its user. The core holds that sway only for an idle clip marked
 * with StandIdleSway's timeline field and keyed on its swayed bone.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoIdleSwayGameTests {
    private static final String SKIN_ASSETS = "assets/rotp_theworld/stand_skins/the_world/assets/rotp_theworld/";

    private DiegoIdleSwayGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoIdleSway")
    public static void idleSwaysAndHoldsStillAs116sJavaModel(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        JsonObject idle = readJson(SKIN_ASSETS + "animations/the_world.animation.json")
                .getAsJsonObject("animations").getAsJsonObject("idle");
        helper.assertTrue(idle != null, "Diego's The World has no idle clip");
        JsonObject timeline = idle.getAsJsonObject("timeline");
        JsonElement mark = timeline != null ? timeline.get("0") : null;
        if (mark == null || !mark.isJsonPrimitive() || !mark.getAsString().replace(" ", "")
                .equals(StandIdleSway.CLIP_FIELD + "=" + StandIdleSway.CLIP_VALUE + ";")) {
            wrong.add("the idle lacks the mark the core holds still for a sneaking user: " + mark);
        }
        JsonObject bones = idle.getAsJsonObject("bones");
        JsonObject swayed = bones.getAsJsonObject(StandIdleSway.SWAYED_BONE);
        JsonObject position = swayed != null ? swayed.getAsJsonObject("position") : null;
        double[][] keys = { { 0.0, 0.0 }, { 2.0, -0.64 }, { 6.0, 0.64 }, { 8.0, 0.0 } };
        if (position == null || position.size() != keys.length || idle.get("animation_length").getAsDouble() != 8.0) {
            wrong.add("the idle sway is not 1.16's 8 s sine on " + StandIdleSway.SWAYED_BONE + ": " + position);
        }
        else {
            for (double[] key : keys) {
                JsonObject frame = position.getAsJsonObject(String.format(java.util.Locale.ROOT, "%.1f", key[0]));
                JsonArray vector = frame != null ? frame.getAsJsonArray("vector") : null;
                if (vector == null || vector.get(0).getAsDouble() != 0 || vector.get(1).getAsDouble() != key[1]
                        || vector.get(2).getAsDouble() != 0) {
                    wrong.add("the " + StandIdleSway.SWAYED_BONE + " sway at " + key[0] + " s is not " + key[1] + " px");
                }
            }
        }
        for (String bone : bones.keySet()) {
            if (!bone.equals(StandIdleSway.SWAYED_BONE) && bones.getAsJsonObject(bone).has("position")) {
                wrong.add("the idle bobs " + bone + ", which the core does not hold still");
            }
        }
        JsonArray geoBones = readJson(SKIN_ASSETS + "geo/the_world.geo.json").getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject().getAsJsonArray("bones");
        boolean rootStandPos = false;
        for (JsonElement bone : geoBones) {
            JsonObject object = bone.getAsJsonObject();
            if (StandIdleSway.SWAYED_BONE.equals(object.get("name").getAsString()) && !object.has("parent")) {
                rootStandPos = true;
            }
        }
        if (!rootStandPos) {
            wrong.add("the model has no root " + StandIdleSway.SWAYED_BONE + " bone");
        }

        ServerLevel level = helper.getLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "diego-sway")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        StandPower power = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(DiegosTheWorldAddon.THE_WORLD.get());
            helper.assertTrue(DiegosTheWorldAddon.THE_WORLD.get().summon(user, power),
                    "Setup: could not summon Diego's The World");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isVisibleForAll(),
                    "Setup: Diego's The World is not out following its user");
            if (!StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle is held still while its user stands, where 1.16 swayed it");
            }
            user.setShiftKeyDown(true);
            if (StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle sways while its user sneaks, where 1.16 held it still");
            }
            user.setShiftKeyDown(false);
            stand.setCanFollowUser(false);
            if (StandIdleSway.swaysAsIn116(stand, 0.5F)) {
                wrong.add("the idle sways while the Stand does not follow its user, where 1.16 held it still");
            }
            stand.setCanFollowUser(true);
        } finally {
            if (power != null && power.isSummoned()) {
                DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 idle sway of Diego's The World: " + String.join("; ", wrong));
        helper.succeed();
    }

    private static JsonObject readJson(String file) {
        try {
            Path path = ModList.get().getModFileById(DiegosTheWorldAddon.MOD_ID).getFile().findResource(file);
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
