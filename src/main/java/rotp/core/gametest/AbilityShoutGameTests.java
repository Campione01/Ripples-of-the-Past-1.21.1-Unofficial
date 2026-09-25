package rotp.core.gametest;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.stands.theworld.TheWorldBarrageAbility;
import rotp.core.impl.stands.theworld.TheWorldBarrageAbility.TheWorldBarrage;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Action#playVoiceLine said an action's shout only when the user was not sneaking, unless the action was a SHIFT
 * variation (StandEntityHeavyAttack also for a finisher; HamonBreath overrode the rule away). And the Resolve gate of an
 * ability, per-Stand overrides included, is readable by add-ons.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityShoutGameTests {
	private AbilityShoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void sneakingSkipsAShoutUnlessTheActionWasAShiftVariation(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, player, "the_world");
			Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			ResourceLocation hierophant = JojoMod.resLoc("hierophant_green");
			ResourceLocation hamon = HamonPowerType.HAMON.getId();
			Ability splash = stand(ModStandAbilities.HG_EMERALD_SPLASH.get(), hierophant, "emerald_splash");
			Ability concentrated = stand(ModStandAbilities.HG_EMERALD_SPLASH_CONCENTRATED.get(), hierophant,
					"emerald_splash_concentrated");
			Ability syo = hamon(HamonPowerType.SUNLIGHT_YELLOW_OVERDRIVE.get(), hamon, "sunlight_yellow_overdrive");
			Ability syoBarrage = hamon(HamonPowerType.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get(), hamon,
					"sunlight_yellow_overdrive_barrage");
			Ability bubbleCutter = hamon(HamonPowerType.HAMON_BUBBLE_CUTTER.get(), hamon, "bubble_cutter");
			Ability gliding = hamon(HamonPowerType.HAMON_BUBBLE_CUTTER_GLIDING.get(), hamon, "bubble_cutter_gliding");
			Ability breath = hamon(HamonPowerType.HAMON_BREATH.get(), hamon, "hamon_breath");

			player.setShiftKeyDown(true);
			helper.assertTrue(player.isShiftKeyDown(), "the fixture could not sneak");
			for (Ability silent : new Ability[] { timeStop, splash, syo, bubbleCutter }) {
				helper.assertTrue(silent.skipsShoutWhileSneaking(player),
						silent.name() + " still shouts while its user sneaks (1.16 skipped it)");
			}
			helper.assertTrue(!timeStop.sayShout(player, ModSoundEvents.DIO_THE_WORLD),
					"sayShout said The World's line while its user sneaked");
			for (Ability shouting : new Ability[] { concentrated, syoBarrage, gliding, breath }) {
				helper.assertTrue(shouting.playsVoiceLineOnSneak() && !shouting.skipsShoutWhileSneaking(player),
						shouting.name() + " was a 1.16 SHIFT variation (or HamonBreath) and must shout while sneaking");
			}

			player.setShiftKeyDown(false);
			for (Ability any : new Ability[] { timeStop, splash, concentrated, syo, syoBarrage, bubbleCutter, gliding, breath }) {
				helper.assertTrue(!any.skipsShoutWhileSneaking(player), any.name() + " skipped a shout without sneaking");
			}
			helper.assertTrue(!timeStop.skipsShoutWhileSneaking(null), "a missing user cannot be sneaking");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theRequiredResolveLevelIncludesPerStandOverrides(GameTestHelper helper) {
		Player twUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player spUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower theWorld = give(helper, twUser, "the_world");
			StandPower starPlatinum = give(helper, spUser, "star_platinum");
			Ability twTimeStop = theWorld.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			Ability spTimeStop = starPlatinum.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			// 1.16 THE_WORLD_TIME_STOP resolveLevelToUnlock(2), STAR_PLATINUM_TIME_STOP resolveLevelToUnlock(4)
			helper.assertTrue(twTimeStop.getRequiredResolveLevel(theWorld) == 2,
					"The World's time stop gate: " + twTimeStop.getRequiredResolveLevel(theWorld));
			helper.assertTrue(spTimeStop.getRequiredResolveLevel(starPlatinum) == 4,
					"Star Platinum's time stop gate: " + spTimeStop.getRequiredResolveLevel(starPlatinum));
			// the plain field hides the per-Stand override
			helper.assertTrue(twTimeStop.getResolveLevelToUnlock() == -1 && twTimeStop.getRequiredResolveLevel(null) == -1,
					"the time stop's own field is no longer the unset gate");
			Ability tsPunch = theWorld.getMoveset().getAbility("ts_punch");
			helper.assertTrue(tsPunch != null && tsPunch.getRequiredResolveLevel(theWorld) == 3,
					"The World's TS punch gate (1.16 resolveLevelToUnlock(3))");
		}
		finally {
			twUser.discard();
			spUser.discard();
		}
		helper.succeed();
	}

	// 1.16 TheWorldBarrage: Muda Muda also when the press auto-summons The World (not yet out, so no WRY even high on
	// blood), and a said line drops the Stand's cry; a sneaking press says nothing and keeps the cry
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theWorldBarrageShoutsWhenThePressAutoSummons(GameTestHelper helper) {
		Player shouter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player sneaker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower shouterPower = give(helper, shouter, "the_world");
			makeHighBloodVampire(helper, shouter);
			helper.assertTrue(shouterPower.getSummonedStandEntity() == null && !shouter.isShiftKeyDown(),
					"The World must start unsummoned and its user standing");
			TheWorldBarrage barrage = pressBarrage(helper, shouter, shouterPower, BufferingState.clickOnly());
			helper.assertTrue(barrage != null, "The press did not auto-summon The World into its barrage");
			helper.assertTrue(barrage.isStandCrySuppressed(),
					"The auto-summoning barrage kept the Stand's cry, so its user said no line");
			helper.assertTrue(said(shouter, ModSoundEvents.DIO_MUDA_MUDA), "No Muda Muda on an auto-summoning barrage");
			helper.assertTrue(!said(shouter, ModSoundEvents.DIO_WRY), "WRY needs The World already out (1.16 wasActive)");

			StandPower sneakerPower = give(helper, sneaker, "the_world");
			sneaker.setShiftKeyDown(true);
			helper.assertTrue(sneakerPower.getSummonedStandEntity() == null && sneaker.isShiftKeyDown(),
					"The sneaking fixture is not set up");
			TheWorldBarrage sneaking = pressBarrage(helper, sneaker, sneakerPower, BufferingState.clickOnly());
			helper.assertTrue(sneaking != null && !sneaking.isStandCrySuppressed(),
					"A sneaking barrage press must start and keep the Stand's cry");
			helper.assertTrue(!said(sneaker, ModSoundEvents.DIO_MUDA_MUDA), "A sneaking barrage press said Muda Muda");
		}
		finally {
			dismiss(shouter);
			dismiss(sneaker);
		}
		helper.succeed();
	}

	// 1.16 said the shout only for a press that started the barrage: a press buffered behind another Stand action says
	// nothing, and its replay says it (WRY, The World being out and its user high on blood) once the barrage starts
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theWorldBarrageShoutsOnlyWhenThePressStartsIt(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, player, "the_world");
			makeHighBloodVampire(helper, player);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			helper.assertTrue(type.summon(player, power), "Could not summon The World");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			// a plain action cannot be cancelled into the barrage, so the press buffers behind it
			Ability heavy = power.getAbility("heavy_punch");
			helper.assertTrue(heavy instanceof EntityActionType, "The World's heavy punch is missing");
			EntityActionInstance blocker = new EntityActionInstance((EntityActionType) heavy);
			blocker.phasesLength.put(ActionPhase.PERFORM, 200F);
			blocker.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(blocker, player, SyncType.NO_SYNC);

			BufferingState first = BufferingState.clickCanBuffer();
			pressBarrage(helper, player, power, first);
			helper.assertTrue(first.shouldBuffer && !first.isActionSuccess && stand.getCurStandAction() == blocker,
					"The press was not buffered behind the running action");

			LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
			BufferingState replay = BufferingState.buffered();
			TheWorldBarrage barrage = pressBarrage(helper, player, power, replay);
			helper.assertTrue(replay.isActionSuccess && barrage != null, "The replayed press did not start the barrage");
			helper.assertTrue(barrage.isStandCrySuppressed(),
					"The started barrage kept the Stand's cry: its line was already spent by the buffered press");
			helper.assertTrue(said(player, ModSoundEvents.DIO_WRY), "No WRY from a summoned The World high on blood");
			helper.assertTrue(!said(player, ModSoundEvents.DIO_MUDA_MUDA), "Muda Muda instead of WRY");
		}
		finally {
			dismiss(player);
		}
		helper.succeed();
	}

	@Nullable
	private static TheWorldBarrage pressBarrage(GameTestHelper helper, Player user, StandPower power, BufferingState buffering) {
		Ability barrage = power.getAbility("barrage");
		helper.assertTrue(barrage instanceof TheWorldBarrageAbility && !barrage.playsVoiceLineOnSneak(),
				"The World's barrage is not TheWorldBarrageAbility: " + barrage);
		barrage.onKeyPress(helper.getLevel(), user, null, InputMethod.HOLD, 0.0F, buffering);
		StandEntity stand = power.getSummonedStandEntity();
		return stand != null && stand.getCurStandAction() instanceof TheWorldBarrage started ? started : null;
	}

	private static void makeHighBloodVampire(GameTestHelper helper, Player player) {
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.VAMPIRISM.get());
		var blood = VampirismState.get(player).blood();
		blood.setCurrent(blood.max());
		helper.assertTrue(ModPlayerPowers.VAMPIRISM.get().isHighOnBlood(player), "The fixture is not a vampire high on blood");
	}

	// the probe records the line, so probe each line once, after the presses
	private static boolean said(Player player, Holder<SoundEvent> line) {
		return !player.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get()).checkNotRepeatingVoiceLine(line, 1);
	}

	private static void dismiss(Player player) {
		StandPower power = PowerClass.STAND.get(player);
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		if (stand != null) {
			power.setSummonedStand(null);
			stand.discard();
		}
		player.discard();
	}

	private static StandPower give(GameTestHelper helper, Player player, String standName) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the " + standName + " test player");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(type != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		return power;
	}

	private static Ability stand(AbilityType<?> type, ResourceLocation standId, String name) {
		return type.createInstance(new AbilityId(PowerClass.STAND, standId, name));
	}

	private static Ability hamon(AbilityType<?> type, ResourceLocation hamonId, String name) {
		return type.createInstance(new AbilityId(PowerClass.PLAYER_POWER, hamonId, name));
	}
}
