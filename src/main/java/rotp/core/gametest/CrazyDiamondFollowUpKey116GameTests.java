package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 ModStandsInit :641-662: Crazy Diamond's leave-object follow-up belonged to the base heavy punch
 * (CRAZY_DIAMOND_HEAVY_PUNCH.attackRecoveryFollowup(CRAZY_DIAMOND_LEAVE_OBJECT)) and the three misshape follow-ups to its
 * finisher variation (CRAZY_DIAMOND_FINISHER_PUNCH); StandAction.replaceActionKostyl gave a follow-up only while its own
 * action was the Stand's task and the action the heavy key resolved to (StandEntityHeavyAttack.getFinisherVariationIfPresent:
 * the finisher variation from a finisher meter of 0.5, StandEntity.willHeavyPunchBeFinisher).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondFollowUpKey116GameTests {
	private static final short KEY = 24;

	private CrazyDiamondFollowUpKey116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void leaveObjectFollowsTheBaseHeavyPunchAndMisshapingTheFinisherAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondFollowUp"));
		Player punched = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondFollowUpTarget"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "finisher", "leave_object", "disfiguring_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability finisher = power.getAbility("finisher");
			Ability leaveObject = power.getAbility("leave_object");
			Ability misshaping = power.getAbility("disfiguring_punch");
			helper.assertTrue(heavy != null && finisher != null && leaveObject != null && misshaping != null
					&& finisher.isAbilityUnlocked(power) && leaveObject.isAbilityUnlocked(power) && misshaping.isAbilityUnlocked(power),
					"fixture: Crazy Diamond's heavy punch, finisher, leave object or misshaping punch is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;

			// a player: 1.16 offered the misshape follow-ups only at a player or a player's Stand
			punched.moveTo(userPos.x, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			punched.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(punched), "Could not add the punched player");
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(punched));
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(punched));

			StandInstance instance = power.getStandInstance().orElseThrow();
			row(helper, power, user, stand, heavy, 0.0F, true, heavy, leaveObject, null,
					"the base heavy punch (finisher meter 0) with a chorus fruit in the off hand");
			row(helper, power, user, stand, heavy, 0.6F, true, finisher, misshaping, null,
					"the finisher (meter 0.6) with a chorus fruit in the off hand");
			row(helper, power, user, stand, heavy, 1.0F, false, finisher, misshaping, null,
					"the finisher (meter 1) with an empty off hand");
			// willHeavyPunchBeFinisher: the heavy key resolves to the finisher, which has no leave-object follow-up
			row(helper, power, user, stand, heavy, 0.0F, true, heavy, finisher, () -> stand.setFinisherMeter(0.6F),
					"the base heavy punch, the meter raised to 0.6 while it runs, with a chorus fruit in the off hand");
			// getFinisherVariationIfPresent: the finisher needs the arms, so the key resolves to the base heavy punch
			try {
				row(helper, power, user, stand, heavy, 1.0F, false, finisher, heavy, () -> instance.removePart(StandPart.ARMS),
						"the finisher (meter 1) once Crazy Diamond lost its arms, with an empty off hand");
			}
			finally {
				instance.addPart(StandPart.ARMS);
			}
			power.getCurTypeData()._lockedAbilities.add("finisher");
			helper.assertTrue(!finisher.isAbilityUnlocked(power), "fixture: the finisher is still unlocked");
			row(helper, power, user, stand, heavy, 1.0F, false, heavy, heavy, null,
					"the base heavy punch at a full meter (the finisher locked) with an empty off hand");
			helper.succeed();
		}
		finally {
			if (power != null) {
				power.getCurTypeData()._lockedAbilities.remove("finisher");
				if (power.isSummoned()) {
					standType.forceUnsummon(user, power);
				}
			}
			punched.discard();
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
		}
	}

	/*
	 * 1.16 CrazyDiamondMisshapeBodyPart.checkSpecificConditions took only a living target whose Stand user is a player (the
	 * mob effects of misshaping were a TODO), a player or a player's Stand: StandAction.canSetFollowUp ran it on the
	 * finisher's task target to put a misshape follow-up on the heavy key, and the click ran it again on the crosshair's
	 * target (PowerBaseImpl.clickAction with the mouse target), which refused it without a message.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void misshapingFollowsOnlyAFinisherAtAPlayerOrAPlayersStandAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		StandType enemyType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null && enemyType != null, "Missing the Crazy Diamond or Star Platinum Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondMisshapeTargets"));
		Player enemy = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CrazyDiamondMisshapeEnemy"));
		StandPower power = null;
		StandPower enemyPower = null;
		Cow cow = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			Vec3 front = userPos.add(0.0D, 0.0D, 1.5D);
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String name : new String[] { "heavy_punch", "finisher", "disfiguring_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability heavy = power.getAbility("heavy_punch");
			Ability finisher = power.getAbility("finisher");
			Ability misshaping = power.getAbility("disfiguring_punch");
			helper.assertTrue(heavy != null && finisher != null && misshaping != null && finisher.isAbilityUnlocked(power)
					&& misshaping.isAbilityUnlocked(power), "fixture: Crazy Diamond's heavy punch, finisher or misshaping punch is"
							+ " missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;

			enemy.moveTo(front.x, front.y, front.z, 180.0F, 0.0F);
			enemy.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(enemy), "Could not add the other player");
			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.moveTo(userPos.x + 1.5D, userPos.y, userPos.z + 1.5D, 180.0F, 0.0F);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemy, enemy, true,
					"a finisher at a player, the crosshair on the player");
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, cow, cow, false,
					"a finisher at a cow, the crosshair on the cow");
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemy, cow, true,
					"a finisher at a player, the crosshair moved to a cow");

			// the other player 4 blocks away, its Star Platinum where it stood
			enemy.moveTo(userPos.x + 2.0D, userPos.y, userPos.z + 4.0D, 180.0F, 0.0F);
			enemyPower = PowerClass.STAND.attachGet(enemy);
			helper.assertTrue(StandPowerTransitions.insert(enemyPower, new StandInstance(enemyType)).status()
					== StandPowerTransitions.Status.APPLIED && enemyType.summon(enemy, enemyPower), "Could not give and summon Star"
							+ " Platinum");
			StandEntity enemyStand = enemyPower.getSummonedStandEntity();
			helper.assertTrue(enemyStand != null, "Summoned Star Platinum is missing");
			enemyStand.moveTo(front.x, front.y, front.z, 180.0F, 0.0F);
			misshapeRow(helper, power, user, stand, heavy, finisher, misshaping, enemyStand, enemyStand, true,
					"a finisher at another player's Stand, the crosshair on the Stand");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (enemyPower != null && enemyPower.isSummoned()) {
				enemyType.forceUnsummon(enemy, enemyPower);
			}
			if (cow != null) {
				cow.discard();
			}
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.discard();
			enemy.discard();
		}
	}

	/*
	 * 1.16 CrazyDiamondMisshapingPunch.onTaskSet recorded the part of a living task target its user aimed at
	 * (TargetHitPart.getHitTarget from the user's look), turned the Stand to that part (rotateStandTowardsTarget) and the
	 * misshape follow-up worked on that part, not on where the punch landed. The user looks 28 degrees down at another
	 * player 3 blocks ahead, its look line entering the legs.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theFinisherMisshapesThePartItsUserAimedAtWhenItStartedAsIn116(GameTestHelper helper) {
		MisshapeScene scene = MisshapeScene.open(helper, "CrazyDiamondMisshapePart", 28.0F);
		try {
			Vec3 eyes = scene.user.getEyePosition();
			Vec3 entry = scene.enemy.getBoundingBox().clip(eyes, eyes.add(scene.user.getLookAngle().scale(6.0D))).orElse(null);
			helper.assertTrue(entry != null && entry.y - scene.enemy.getY() < scene.enemy.getBbHeight() * 0.375D,
					"fixture: the user's look line does not enter the other player's legs: " + entry);
			// Crazy Diamond itself looks above the player's head: only its user's aim gives the legs
			scene.stand.moveTo(scene.user.getX() - 0.75D, scene.user.getY(), scene.user.getZ() - 0.75D, 0.0F, -20.0F);
			scene.stand.setYHeadRot(0.0F);
			Vec3 standEyes = scene.stand.getEyePosition();
			Vec3 standLook = scene.stand.getLookAngle();
			double standAimY = standEyes.y + standLook.y * (scene.enemy.getBoundingBox().minZ - standEyes.z) / standLook.z;
			helper.assertTrue(standAimY > scene.enemy.getBoundingBox().maxY, "fixture: Crazy Diamond's own look line does not"
					+ " pass above the other player's head: " + standAimY);
			scene.aimAt(scene.enemy);
			EntityActionInstance action = scene.pressFinisher(helper);
			Vec3 lookedAt = action.getStandLookTargetPosition(scene.stand, new ActionTarget(scene.enemy));
			helper.assertTrue(lookedAt != null && lookedAt.distanceTo(scene.enemy.position()) < 1.0E-6D, "1.16 turned Crazy"
					+ " Diamond to the legs of the player its finisher started at, the part its user aimed at (their centre "
					+ scene.enemy.position() + "): it looks at " + lookedAt);
			AbilityConditionCheck followUp = resolve(scene.power, scene.heavy);
			helper.assertTrue(followUp != null && followUp.ability == scene.misshaping && followUp.conditionCheck.isPositive(),
					"fixture: the misshaping punch is not on the heavy key during the finisher: " + describe(followUp));
			press(scene.power, scene.user, scene.heavy);
			int[] ticks = new int[1];
			helper.onEachTick(() -> {
				if (scene.closed) return;
				try {
					boolean face = scene.enemy.hasEffect(ModStatusEffects.MISSHAPEN_FACE);
					boolean arms = scene.enemy.hasEffect(ModStatusEffects.MISSHAPEN_ARMS);
					boolean legs = scene.enemy.hasEffect(ModStatusEffects.MISSHAPEN_LEGS);
					if (face || arms || legs || action.isOver()) {
						helper.assertTrue(legs && !face && !arms, "1.16 misshaped the legs, the part the user aimed at when the"
								+ " finisher started: face " + face + ", arms " + arms + ", legs " + legs + " (the punch struck "
								+ action.punchedTarget + ")");
						scene.close();
						helper.succeed();
					}
					else if (++ticks[0] > 150) {
						scene.close();
						helper.fail("The finisher with the misshaping punch did not end");
					}
				}
				catch (RuntimeException | Error error) {
					scene.close();
					throw error;
				}
			});
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	/*
	 * A finisher pressed at nothing recorded no part (1.16 onTaskSet found no living task target), so even once its strike
	 * took a player as its task target the heavy key offered no misshape follow-up (CrazyDiamondMisshapeBodyPart: no part).
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aFinisherStartedAtNothingOffersNoMisshapingAsIn116(GameTestHelper helper) {
		MisshapeScene scene = MisshapeScene.open(helper, "CrazyDiamondMisshapeNoPart", 0.0F);
		try {
			scene.aimAt(null);
			EntityActionInstance action = scene.pressFinisher(helper);
			helper.assertTrue(action instanceof StandEntityHeavyPunch punch && punch.getTaskTarget(scene.stand.level()).isEmpty(
					scene.stand.level()), "fixture: the finisher pressed at nothing has a target");
			// the client's crosshair now on the other player, which the strike takes
			LivingComponentAction.getComponent(scene.user).entityAim.setTarget(new ActionTarget(scene.enemy));
			int[] ticks = new int[1];
			helper.onEachTick(() -> {
				if (scene.closed) return;
				try {
					StandEntityHeavyPunch punch = (StandEntityHeavyPunch) action;
					if (punch.punchedTarget != null) {
						helper.assertTrue(punch.punchedTarget.getEntity() == scene.enemy
								&& punch.getTaskTarget(scene.stand.level()).getEntity() == scene.enemy && !punch.isOver(),
								"fixture: the strike did not take the other player as the finisher's target: " + punch.punchedTarget);
						AbilityConditionCheck key = resolve(scene.power, scene.heavy);
						helper.assertTrue(key != null && key.ability == scene.finisher, "1.16 offered no misshape follow-up for a"
								+ " finisher that started without a living target, even once it struck a player: the heavy key"
								+ " resolves to " + describe(key));
						scene.close();
						helper.succeed();
					}
					else if (++ticks[0] > 150 || punch.isOver()) {
						scene.close();
						helper.fail("The finisher pressed at nothing never struck the other player");
					}
				}
				catch (RuntimeException | Error error) {
					scene.close();
					throw error;
				}
			});
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	/*
	 * 1.16 StandAction.canSetFollowUp asked PowerBaseImpl.checkRequirements, which refused an action whose performer, the
	 * summoned Stand, was stunned: a stunned Crazy Diamond was offered none of its three follow-ups, the heavy key staying on
	 * the running punch and the restore-terrain key on the restore terrain, and each follow-up asked for itself was refused
	 * with the stun message.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStunnedCrazyDiamondIsOfferedNoFollowUpAsIn116(GameTestHelper helper) {
		MisshapeScene scene = MisshapeScene.open(helper, "CrazyDiamondStunnedFollowUp", 0.0F);
		try {
			for (String name : new String[] { "leave_object", "restore_terrain", "fuse_with_rock" }) {
				scene.power.getCurTypeData()._lockedAbilities.remove(name);
			}
			Ability leaveObject = scene.power.getAbility("leave_object");
			Ability restoreTerrain = scene.power.getAbility("restore_terrain");
			Ability angeloRock = scene.power.getAbility("fuse_with_rock");
			helper.assertTrue(leaveObject != null && restoreTerrain != null && angeloRock != null && leaveObject.isAbilityUnlocked(scene.power)
					&& angeloRock.isAbilityUnlocked(scene.power), "fixture: the leave object, restore terrain or Angelo Rock is"
							+ " missing or locked");
			scene.aimAt(scene.enemy);
			LivingComponentAction standAction = LivingComponentAction.getComponent(scene.stand);

			scene.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CHORUS_FRUIT, 8));
			scene.power.setStamina(scene.power.getMaxStamina());
			scene.stand.setFinisherMeter(0.0F);
			AbilityConditionCheck pressed = press(scene.power, scene.user, scene.heavy);
			helper.assertTrue(pressed != null && pressed.ability == scene.heavy && standAction.getAction() instanceof StandEntityHeavyPunch,
					"fixture: the heavy key did not start the base heavy punch: " + describe(pressed));
			stunnedRow(helper, scene, scene.heavy, leaveObject, scene.heavy, "the base heavy punch");
			standAction.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

			scene.pressFinisher(helper);
			stunnedRow(helper, scene, scene.heavy, scene.misshaping, scene.finisher, "the finisher");
			stunnedRow(helper, scene, restoreTerrain, angeloRock, restoreTerrain, "the finisher");
			standAction.setAction(null, scene.user, SyncType.NO_SYNC);
			helper.succeed();
		}
		finally {
			scene.stand.removeEffect(ModStatusEffects.STUN);
			scene.close();
		}
	}

	// the key gives the follow-up; with the Stand stunned it gives the base ability and the follow-up refuses with the stun
	private static void stunnedRow(GameTestHelper helper, MisshapeScene scene, Ability key, Ability followUp, Ability base,
			String running) {
		AbilityConditionCheck given = resolve(scene.power, key);
		helper.assertTrue(given != null && given.ability == followUp && given.conditionCheck.isPositive(), "fixture: during "
				+ running + " the " + key.name() + " key does not give " + followUp.name() + ": " + describe(given));
		scene.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
		try {
			AbilityConditionCheck stunned = resolve(scene.power, key);
			helper.assertTrue(stunned != null && stunned.ability == base, "1.16 offered a stunned Crazy Diamond no "
					+ followUp.name() + " during " + running + " (canSetFollowUp: checkRequirements refused the stunned Stand): the "
					+ key.name() + " key resolves to " + describe(stunned));
			Component warning = followUp.checkConditions(scene.power).getWarning();
			helper.assertTrue(warning != null && warning.getContents() instanceof TranslatableContents contents
					&& "jojo.message.action_condition.stun".equals(contents.getKey()), "1.16 refused " + followUp.name()
							+ " for a stunned Stand with the stun message: " + warning);
		}
		finally {
			scene.stand.removeEffect(ModStatusEffects.STUN);
		}
		AbilityConditionCheck again = resolve(scene.power, key);
		helper.assertTrue(again != null && again.ability == followUp, "fixture: with the stun gone the " + key.name()
				+ " key does not give " + followUp.name() + " again: " + describe(again));
	}

	// 1.16 ModStandsInit :634-641 and assets\jojo: locale, then the names of crazy_diamond_misshape_face, _arms and _legs
	// (en_us where 1.16 had none), and the SHA-1 of their textures\action icons
	private static final String[][] MISSHAPE_NAMES_116 = {
			{ "en_us", "Misshape face", "Misshape arms", "Misshape legs" },
			{ "en_pt", "Misshape face", "Misshape arms", "Misshape legs" },
			{ "es_es", "Cara deformada", "Brazos deformes", "Piernas deformes" },
			{ "es_ve", "Deformar cara", "Deformar brazos", "Deformar piernas" },
			{ "it_it", "Misshape face", "Misshape arms", "Misshape legs" },
			{ "ja_jp", "Misshape face", "Misshape arms", "Misshape legs" },
			{ "lzh", "畸形的臉", "畸形的手臂", "畸形的腿" },
			{ "pt_br", "Rosto Desformado", "Braços Desformados", "Pernas Desformadas" },
			{ "ru_ru", "Деформировать лицо", "Деформировать руки", "Деформировать ноги" },
			{ "uk_ua", "Спотворити лице", "Спотворити руки", "Спотворити ноги" },
			{ "zh_cn", "畸形的脸", "畸形的手臂", "畸形的腿" },
			{ "zh_tw", "使其臉部畸形", "使其手臂畸形", "使其腿部畸形" } };
	private static final String[] MISSHAPE_PARTS = { "face", "arms", "legs" };
	private static final String[] MISSHAPE_ICON_SHA1_116 = {
			"eaa440e244fb8c209731a2daa2cb99e7149c6346",
			"56745e8f7980a6dae2812fcd5a038db745694578",
			"326b460a55877c1d5fb37448ce17d866ec41d18c" };

	/*
	 * 1.16 had a misshape follow-up per part, each drawn with its own icon (textures/action/crazy_diamond_misshape_<part>)
	 * and name (action.jojo.crazy_diamond_misshape_<part>), and the heavy key offered the one for the part the finisher
	 * recorded. The user looks 0, 15 and 28 degrees down at another player 3 blocks ahead: the head, the torso, the legs.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theMisshapingKeyShowsTheIconAndNameOfTheRecordedPartAsIn116(GameTestHelper helper) {
		for (int i = 0; i < MISSHAPE_PARTS.length; i++) {
			String path = "assets/jojo_ripples/stand_skins/crazy_diamond/assets/jojo_ripples/textures/ability/misshape_"
					+ MISSHAPE_PARTS[i] + ".png";
			String sha1 = sha1(readResource(path));
			helper.assertTrue(MISSHAPE_ICON_SHA1_116[i].equals(sha1), "Crazy Diamond's skin must ship 1.16's crazy_diamond_misshape_"
					+ MISSHAPE_PARTS[i] + " icon as " + path + ": SHA-1 " + sha1);
		}
		for (String[] locale : MISSHAPE_NAMES_116) {
			JsonObject lang = JsonParser.parseString(new String(readResource("assets/jojo_ripples/lang/" + locale[0] + ".json"),
					StandardCharsets.UTF_8)).getAsJsonObject();
			for (int i = 0; i < MISSHAPE_PARTS.length; i++) {
				String key = "jojo_ripples.ability.misshape_" + MISSHAPE_PARTS[i];
				JsonElement value = lang.get(key);
				helper.assertTrue(value != null && locale[i + 1].equals(value.getAsString()), locale[0] + " must name " + key
						+ " as 1.16 named action.jojo.crazy_diamond_misshape_" + MISSHAPE_PARTS[i] + ", " + locale[i + 1] + ": it has "
						+ value);
			}
		}
		misshapePartRow(helper, 0.0F, 1.0D, "misshape_face", "the head");
		misshapePartRow(helper, 15.0F, 0.7D, "misshape_arms", "the torso");
		misshapePartRow(helper, 28.0F, 0.0D, "misshape_legs", "the legs");
		helper.succeed();
	}

	private static void misshapePartRow(GameTestHelper helper, float pitch, double partHeight, String asset, String part) {
		MisshapeScene scene = MisshapeScene.open(helper, "CrazyDiamondMisshapeIcon" + (int) pitch, pitch);
		try {
			String idle = scene.misshaping.getSpriteName(scene.power);
			helper.assertTrue("disfiguring_punch".equals(idle), "Without a running finisher the misshaping punch keeps its own icon:"
					+ " it shows " + idle);
			scene.aimAt(scene.enemy);
			EntityActionInstance action = scene.pressFinisher(helper);
			Vec3 lookedAt = action.getStandLookTargetPosition(scene.stand, new ActionTarget(scene.enemy));
			helper.assertTrue(lookedAt != null && Math.abs(lookedAt.y - scene.enemy.getY(partHeight)) < 1.0E-6D, "fixture: the"
					+ " finisher did not record " + part + " (Crazy Diamond looks at " + lookedAt + ")");
			AbilityConditionCheck key = resolve(scene.power, scene.heavy);
			helper.assertTrue(key != null && key.ability == scene.misshaping && key.conditionCheck.isPositive(), "fixture: the"
					+ " misshaping punch is not on the heavy key during the finisher at " + part + ": " + describe(key));
			String sprite = key.ability.getSpriteName(scene.power);
			helper.assertTrue(asset.equals(sprite), "1.16 showed the misshape follow-up for " + part + " with its own icon,"
					+ " crazy_diamond_" + asset + ": the heavy key shows " + sprite);
			// the base name asks the client's Stand skins, which the game test server cannot load
			String name;
			try {
				Component shown = key.ability.getName(scene.power);
				name = shown.getContents() instanceof TranslatableContents contents ? contents.getKey() : shown.getString();
			}
			catch (RuntimeException | LinkageError e) {
				name = "the base name (" + e + ")";
			}
			helper.assertTrue(("jojo_ripples.ability." + asset).equals(name), "1.16 named the misshape follow-up for " + part
					+ " action.jojo.crazy_diamond_" + asset + ": the heavy key is named " + name);
		}
		finally {
			scene.close();
		}
	}

	private static byte[] readResource(String path) {
		InputStream in = CrazyDiamondFollowUpKey116GameTests.class.getResourceAsStream("/" + path);
		if (in == null) {
			in = CrazyDiamondFollowUpKey116GameTests.class.getClassLoader().getResourceAsStream(path);
		}
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

	private static String sha1(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// Crazy Diamond's user floating 4 blocks up, facing south at the given pitch, another player 3 blocks ahead
	private static final class MisshapeScene {
		final GameTestHelper helper;
		final StandType standType;
		final Player user;
		final Player enemy;
		StandPower power;
		StandEntity stand;
		Ability heavy;
		Ability finisher;
		Ability misshaping;
		boolean closed;

		private MisshapeScene(GameTestHelper helper, String name) {
			this.helper = helper;
			this.standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			this.user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			this.enemy = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name + "Target"));
		}

		static MisshapeScene open(GameTestHelper helper, String name, float pitch) {
			MisshapeScene scene = new MisshapeScene(helper, name);
			try {
				scene.setUp(pitch);
				return scene;
			}
			catch (RuntimeException | Error error) {
				scene.close();
				throw error;
			}
		}

		private void setUp(float pitch) {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 5, 1)));
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, pitch);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			enemy.moveTo(userPos.x, userPos.y, userPos.z + 3.0D, 180.0F, 0.0F);
			enemy.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(enemy), "Could not add the other player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String ability : new String[] { "heavy_punch", "finisher", "disfiguring_punch" }) {
				power.getCurTypeData()._lockedAbilities.remove(ability);
			}
			heavy = power.getAbility("heavy_punch");
			finisher = power.getAbility("finisher");
			misshaping = power.getAbility("disfiguring_punch");
			helper.assertTrue(heavy != null && finisher != null && misshaping != null && finisher.isAbilityUnlocked(power)
					&& misshaping.isAbilityUnlocked(power), "fixture: Crazy Diamond's heavy punch, finisher or misshaping punch"
							+ " is missing or locked");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;
		}

		void aimAt(@Nullable Entity target) {
			ActionTarget aim = target != null ? new ActionTarget(target) : ActionTarget.EMPTY;
			LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(aim.copy());
		}

		// the heavy key pressed with a full meter, which starts the finisher
		EntityActionInstance pressFinisher(GameTestHelper helper) {
			power.setStamina(power.getMaxStamina());
			stand.setFinisherMeter(1.0F);
			AbilityConditionCheck pressed = press(power, user, heavy);
			EntityActionInstance action = LivingComponentAction.getComponent(stand).getAction();
			helper.assertTrue(pressed != null && pressed.ability == finisher && action instanceof StandEntityHeavyPunch
					&& action.ability == finisher, "fixture: a full meter did not make the heavy key start the finisher: "
							+ describe(pressed) + ", action " + action);
			return action;
		}

		void close() {
			if (closed) return;
			closed = true;
			try {
				if (power != null && power.isSummoned()) {
					standType.forceUnsummon(user, power);
				}
			}
			finally {
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				user.discard();
				enemy.discard();
			}
		}
	}

	// the finisher pressed at the target with a full meter, the crosshair then on the other entity: whether the key gives
	// the misshaping punch and whether a click takes it (1.16: the crosshair on a player or a player's Stand)
	private static void misshapeRow(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability heavy,
			Ability finisher, Ability misshaping, LivingEntity target, LivingEntity crosshair, boolean followUp, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(1.0F);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
		standAction.entityAim.setTarget(new ActionTarget(target));
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == finisher && action instanceof StandEntityHeavyPunch punch
				&& action.ability == finisher && punch.getTaskTarget(stand.level()).getEntity() == target, "fixture (" + row
						+ "): the key did not start the finisher at " + target + ": " + describe(pressed) + ", action " + action);
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(crosshair));
		AbilityConditionCheck resolved = resolve(power, heavy);
		boolean usable = followUp && crosshair == target;
		helper.assertTrue(resolved != null && (followUp ? resolved.ability == misshaping
				&& resolved.conditionCheck.isPositive() == usable : resolved.ability == finisher), "1.16 gave " + row
						+ (followUp ? " the misshape follow-up on the heavy key, " + (usable ? "usable" : "refused at the click")
								: " the heavy key as the running finisher, no misshape follow-up") + ": it resolves to "
						+ describe(resolved));
		standAction.setAction(null, user, SyncType.NO_SYNC);
	}

	// the heavy key pressed at the meter, then what it resolves to while that punch runs (after whileRunning)
	private static void row(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Ability heavy,
			float meter, boolean offhandItem, Ability running, Ability expected, @Nullable Runnable whileRunning, String row) {
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		standAction.setAction(null, user, SyncType.NO_SYNC);
		user.setItemInHand(InteractionHand.OFF_HAND, offhandItem ? new ItemStack(Items.CHORUS_FRUIT, 8) : ItemStack.EMPTY);
		power.setStamina(power.getMaxStamina());
		stand.setFinisherMeter(meter);
		AbilityConditionCheck pressed = press(power, user, heavy);
		EntityActionInstance action = standAction.getAction();
		helper.assertTrue(pressed != null && pressed.ability == running && action instanceof StandEntityHeavyPunch punch
				&& action.ability == running && punch.getPunchModifiers().isEmpty()
				&& user.getOffhandItem().is(Items.CHORUS_FRUIT) == offhandItem, "fixture (" + row + "): the key did not start "
						+ running.name() + ": " + describe(pressed) + ", action " + action + ", off hand " + user.getOffhandItem());
		if (whileRunning != null) {
			whileRunning.run();
			helper.assertTrue(standAction.getAction() == action && !action.isOver(), "fixture (" + row + "): the punch ended");
		}
		AbilityConditionCheck resolved = resolve(power, heavy);
		boolean followUp = expected.name().equals("leave_object") || expected.name().equals("disfiguring_punch");
		helper.assertTrue(resolved != null && resolved.ability == expected && (!followUp || resolved.conditionCheck.isPositive()),
				"1.16 gave " + row + " the heavy key " + (followUp ? expected.name() + ", its follow-up" : "as " + expected.name()
						+ ", no follow-up") + ": it resolves to " + describe(resolved));
		standAction.setAction(null, user, SyncType.NO_SYNC);
	}

	// as ClAbilityInputPacket handles a click: the key's resolved ability, its condition check, then the press
	private static AbilityConditionCheck press(StandPower power, Player user, Ability base) {
		AbilityConditionCheck container = resolve(power, base);
		if (container != null && AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
			AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.CLICK, 0,
					BufferingState.clickCanBuffer(), base.getAbilityId());
			AbilityInput.keyRelease(KEY, user);
		}
		return container;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(StandPower power, Ability base) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(base);
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused") + ")";
	}
}
