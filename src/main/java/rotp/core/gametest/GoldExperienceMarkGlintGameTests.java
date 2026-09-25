package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;
import rotp.core.subsystems.itemtracking.KnownItemState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 GoldExperienceMarkItem.ClientStuff: a GE-marked item renders the golden imbued-with-life glint.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceMarkGlintGameTests {
	private static final String TEXTURE = "textures/item_imbued_with_life.png";

	private GoldExperienceMarkGlintGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void onlyTheMarkingUserSeesImbuedGlint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType ge = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
		helper.assertTrue(ge != null, "Missing registered Gold Experience");
		Player owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GEGlintOwner"));
		Player other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GEGlintOther"));
		ItemTracking tracking = ItemTracking.getItemTracking(level);
		List<UUID> trackers = new ArrayList<>();
		try {
			StandPower ownerPower = grant(helper, owner, ge);
			StandPower otherPower = grant(helper, other, ge);
			ItemStack marked = new ItemStack(Items.APPLE);
			ItemStack otherMarked = new ItemStack(Items.BREAD);
			ItemStack plain = new ItemStack(Items.APPLE);
			GEItemMarkEffect ownerMark = mark(helper, level, owner, ownerPower, marked, trackers);
			mark(helper, level, other, otherPower, otherMarked, trackers);

			helper.assertTrue(GEItemMarkEffect.rendersImbuedGlint(marked, owner),
					"The GE user's own marked item must render the imbued-with-life glint");
			helper.assertTrue(!GEItemMarkEffect.rendersImbuedGlint(plain, owner),
					"An unmarked item must keep the vanilla foil");
			helper.assertTrue(!GEItemMarkEffect.rendersImbuedGlint(marked, other),
					"Another GE user's mark must not show the glint to this viewer");
			helper.assertTrue(!GEItemMarkEffect.rendersImbuedGlint(marked, null),
					"No viewer (no local player) must not render the glint");

			ownerPower.userStandEffects.removeEffect(ownerMark);
			helper.assertTrue(!GEItemMarkEffect.rendersImbuedGlint(marked, owner),
					"The glint must go once the mark effect is gone");
			helper.succeed();
		} finally {
			for (UUID id : trackers) {
				tracking.stopTracking(id, level);
			}
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void imbuedGlintIsShippedAndHooked(GameTestHelper helper) {
		byte[] png = read(helper, "/assets/jojo_ripples/" + TEXTURE);
		helper.assertTrue(png.length > 8 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
				"The imbued-with-life glint texture is not a PNG");
		JsonObject meta = JsonParser.parseString(new String(read(helper, "/assets/jojo_ripples/" + TEXTURE + ".mcmeta"),
				StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("texture");
		helper.assertTrue(meta != null && meta.has("blur") && meta.get("blur").getAsBoolean(),
				"The glint texture must stay blurred like 1.16");

		String glint = classText(helper, "rotp/core/impl/stands/goldexperience/client/GEImbuedGlint");
		helper.assertTrue(glint.contains(TEXTURE), "The GE glint render types do not use " + TEXTURE);
		helper.assertTrue(glint.contains("rendersImbuedGlint"), "The GE glint does not check the item mark");
		helper.assertTrue(glint.contains("RegisterRenderBuffersEvent") && glint.contains("registerRenderBuffer"),
				"The GE glint render types are not registered as fixed buffers");
		String mixin = classText(helper, "rotp/core/mixin/client/ItemRendererMixin");
		for (String call : new String[] { "getFoilBufferDirect(", "getFoilBuffer(", "getCompassFoilBuffer(" }) {
			helper.assertTrue(mixin.contains("ItemRenderer;" + call), "ItemRenderer." + call + ") is not swapped for marked items");
		}
		for (String hook : new String[] { "foilBufferDirect", "foilBuffer", "compassFoilBuffer", "isMarked" }) {
			helper.assertTrue(hasName(mixin, hook), "ItemRendererMixin no longer calls GEImbuedGlint." + hook);
		}
		helper.succeed();
	}

	private static StandPower grant(GameTestHelper helper, Player user, StandType type) {
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Gold Experience");
		return power;
	}

	// Same steps as GoldExperienceMarkItemAbility, without the item transfer.
	private static GEItemMarkEffect mark(GameTestHelper helper, ServerLevel level, Player user, StandPower power,
			ItemStack stack, List<UUID> trackers) {
		ItemTracker tracker = ItemTracking.getItemTracking(level).startTracking(stack, level);
		helper.assertTrue(tracker != null, "Could not track the marked item");
		trackers.add(tracker.trackerId);
		tracker.setAtEntity(stack, user.getId(), level, KnownItemState.ENTITY_HAS_ITEM,
				trackerId -> ItemTracking.hasTrackerId(stack, trackerId));
		GEItemMarkEffect effect = ModStandAbilities.EFFECT_GE_ITEM_MARK.get().create(level);
		effect.withItemTracker(tracker);
		power.userStandEffects.addEffect(effect);
		return effect;
	}

	// Exact constant-pool name (CONSTANT_Utf8 tag + length), so "foilBuffer" does not match "foilBufferDirect".
	private static boolean hasName(String classText, String name) {
		return classText.contains("\u0001" + (char) (name.length() >> 8) + (char) (name.length() & 0xFF) + name);
	}

	private static String classText(GameTestHelper helper, String className) {
		return new String(read(helper, "/" + className + ".class"), StandardCharsets.ISO_8859_1);
	}

	private static byte[] read(GameTestHelper helper, String path) {
		try (InputStream in = GoldExperienceMarkGlintGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing resource " + path);
			return in.readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
