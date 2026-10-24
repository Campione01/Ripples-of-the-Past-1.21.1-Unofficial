package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonHealing.getTranslatedName: while its user sneaked (JojoModUtil.useShiftVar) with Healing Touch learned and
 * the mouse target was a living entity Hamon heals (HamonUtil.isLiving), the slot read "Heal <entity>"
 * (action.jojo.hamon_healing_touch with the entity's name), the entity the held action then healed instead of the user;
 * otherwise "Healing". Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonHealingTouchName116GameTests {
	private static final String BATCH = "hamon_healing_touch_name_116_";
	private static final String KEY = "jojo_ripples.ability.hamon_healing";
	private static final String TOUCH_KEY = KEY + "_touch";
	// 1.16's action.jojo.hamon_healing_touch of each locale the core ships; en_us's where 1.16 had none (en_pt) or kept
	// it disabled as "_action.jojo.hamon_healing_touch" (it_it, pt_br, uk_ua), which then fell back to en_us
	private static final String[][] TOUCH_NAMES_116 = {
			{ "en_us", "Heal %s" }, { "en_pt", "Heal %s" }, { "es_es", "Curar %s" }, { "es_ve", "Sanar %s" },
			{ "it_it", "Heal %s" }, { "ja_jp", "%sを回復" }, { "lzh", "療%s" }, { "pt_br", "Heal %s" },
			{ "ru_ru", "Исцелить %s" }, { "uk_ua", "Heal %s" }, { "zh_cn", "碰触治疗" }, { "zh_tw", "碰觸治療" } };

	private HamonHealingTouchName116GameTests() {}

	/*
	 * A cow, a skeleton and a stone block ahead of a Hamon user: sneaking with Healing Touch at the cow the slot names the
	 * cow, on the server and on the user's own client (the HUD, which reads the aim the client sends); not sneaking,
	 * without Healing Touch, at the undead skeleton or at the block it is "Healing".
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "name")
	public static void healingTouchNamesTheLivingEntityASneakingUserAimsAtAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
		ClientUser user = new ClientUser(level);
		List<Entity> entities = new ArrayList<>();
		BlockPos stone = helper.absolutePos(new BlockPos(2, 3, 5));
		try {
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the user");
			entities.add(user);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HEALING.get());
			Ability healing = power.getAbility("hamon_healing");
			helper.assertTrue(healing != null, "Fixture: Hamon has no hamon_healing");
			Mob cow = mob(helper, EntityType.COW, origin.add(0.0D, 0.0D, 1.5D), entities);
			Mob skeleton = mob(helper, EntityType.SKELETON, origin.add(1.5D, 0.0D, 1.5D), entities);
			level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			ActionTarget atCow = new ActionTarget(cow);

			user.setShiftKeyDown(true);
			assertName(helper, healing, power, user, atCow, KEY, null, "sneaking at a cow without Healing Touch");
			hamon.learnSkill(ModHamonSkills.HEALING_TOUCH.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.HEALING_TOUCH.get()), "Fixture: Healing Touch is not learned");
			assertName(helper, healing, power, user, atCow, TOUCH_KEY, cow, "sneaking with Healing Touch at a cow");
			user.local = true;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(atCow);
			assertName(helper, healing, power, user, ActionTarget.EMPTY, TOUCH_KEY, cow,
					"sneaking with Healing Touch at a cow, on the user's own client (the aim it sends)");
			user.local = false;
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			assertName(helper, healing, power, user, new ActionTarget(skeleton), KEY, null,
					"sneaking with Healing Touch at a skeleton (undead: Hamon does not heal it)");
			assertName(helper, healing, power, user, new ActionTarget(stone, Direction.NORTH), KEY, null,
					"sneaking with Healing Touch at a block");
			user.setShiftKeyDown(false);
			assertName(helper, healing, power, user, atCow, KEY, null, "not sneaking, at a cow");
		}
		finally {
			LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			entities.forEach(Entity::discard);
			level.setBlock(stone, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		helper.succeed();
	}

	// every core locale names it as 1.16's did
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "lang")
	public static void healingTouchNameHas116sTextInEveryCoreLocale(GameTestHelper helper) {
		for (String[] locale : TOUCH_NAMES_116) {
			String path = "assets/jojo_ripples/lang/" + locale[0] + ".json";
			JsonObject lang = JsonParser.parseString(new String(readResource(path), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonElement value = lang.get(TOUCH_KEY);
			helper.assertTrue(value != null && locale[1].equals(value.getAsString()), locale[0] + " must name " + TOUCH_KEY + " as"
					+ " 1.16 named action.jojo.hamon_healing_touch there, \"" + locale[1] + "\": it has " + value);
		}
		helper.succeed();
	}

	private static void assertName(GameTestHelper helper, Ability healing, PlayerPower power, Player user, ActionTarget aim,
			String key, Entity named, String scene) {
		LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
		Component name = healing.getName(power);
		Object[] args = name.getContents() instanceof TranslatableContents contents ? contents.getArgs() : null;
		boolean matches = name.getContents() instanceof TranslatableContents contents && key.equals(contents.getKey())
				&& (named == null ? args.length == 0
						: args.length == 1 && args[0] instanceof Component arg && arg.getString().equals(named.getName().getString()));
		helper.assertTrue(matches, "1.16's Healing slot " + scene + " read " + key + (named != null ? " with " + named.getName()
				.getString() : "") + "; the port's reads " + (name.getContents() instanceof TranslatableContents contents
						? contents.getKey() + " " + Arrays.toString(args) : name.getString()));
	}

	private static Mob mob(GameTestHelper helper, EntityType<? extends Mob> type, Vec3 at, List<Entity> entities) {
		Mob mob = type.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create " + type);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add " + mob);
		entities.add(mob);
		return mob;
	}

	private static byte[] readResource(String path) {
		InputStream in = HamonHealingTouchName116GameTests.class.getClassLoader().getResourceAsStream(path);
		if (in == null) {
			throw new GameTestAssertException(path + " is missing");
		}
		try (InputStream stream = in) {
			return stream.readAllBytes();
		}
		catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
	}

	// a player that is its own client's player (LocalPlayer.isLocalPlayer) while local is set
	private static final class ClientUser extends Player {
		boolean local;

		ClientUser(ServerLevel level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "healing-touch-user"));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}

		@Override
		public boolean isLocalPlayer() {
			return local;
		}
	}
}
