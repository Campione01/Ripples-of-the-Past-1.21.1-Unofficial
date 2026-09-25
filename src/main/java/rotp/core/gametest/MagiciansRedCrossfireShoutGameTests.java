package rotp.core.gametest;

import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility.CrossfireShot;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Crossfire Hurricane (holdToFire(20), shout AVDOL_CROSSFIRE_HURRICANE) said Avdol's line on the press that
 * started the charge (PowerBaseImpl.onClickAction), under Action#playVoiceLine's sneak rule: the special, a SHIFT
 * variation, still shouts while sneaking. The shot itself said nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagiciansRedCrossfireShoutGameTests {
	private MagiciansRedCrossfireShoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void crossfireHurricaneShoutsWhenTheChargeStarts(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = giveSummoned(helper, player);
			EntityActionInstance charge = startCharge(helper, player, power, "crossfire_hurricane");
			helper.assertTrue(charge instanceof CrossfireShot shot && shot.isPressShoutSaid(),
					"The started charge did not spend its press shout");
			helper.assertTrue(said(player, ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE),
					"No Crossfire Hurricane shout when the charge started (1.16 said it on the press, before the ankh fired)");
			helper.assertTrue(!said(player, ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE_SPECIAL),
					"The plain Crossfire Hurricane said the special's line");
		}
		finally {
			dismiss(player);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aSneakingCrossfireHurricaneShoutsOnlyAsTheSpecial(GameTestHelper helper) {
		Player sneaker = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player specialUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = giveSummoned(helper, sneaker);
			Ability plain = power.getAbility("crossfire_hurricane");
			Ability special = power.getAbility("crossfire_hurricane_special");
			helper.assertTrue(plain != null && !plain.playsVoiceLineOnSneak(),
					"The plain Crossfire Hurricane must follow the sneak rule");
			helper.assertTrue(special != null && special.playsVoiceLineOnSneak(),
					"The special (a 1.16 SHIFT variation) must shout while its user sneaks");

			// the special not trained yet: a sneaking press is the plain one, silent on the press and on the shot
			sneaker.setShiftKeyDown(true);
			EntityActionInstance charge = startCharge(helper, sneaker, power, "crossfire_hurricane");
			power.setStamina(power.getMaxStamina());
			StandEntity stand = power.getSummonedStandEntity();
			charge.actionPerformStart();
			List<MRCrossfireHurricaneEntity> shots = helper.getLevel().getEntitiesOfClass(MRCrossfireHurricaneEntity.class,
					stand.getBoundingBox().inflate(16.0D));
			helper.assertTrue(!shots.isEmpty(), "The sneaking Crossfire Hurricane did not fire");
			shots.forEach(MRCrossfireHurricaneEntity::discard);
			helper.assertTrue(!said(sneaker, ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE),
					"A sneaking plain Crossfire Hurricane shouted (1.16 skipped the line while sneaking)");

			StandPower specialPower = giveSummoned(helper, specialUser);
			specialUser.setShiftKeyDown(true);
			startCharge(helper, specialUser, specialPower, "crossfire_hurricane_special");
			helper.assertTrue(said(specialUser, ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE_SPECIAL),
					"The sneaking special said no line when its charge started");
		}
		finally {
			dismiss(sneaker);
			dismiss(specialUser);
		}
		helper.succeed();
	}

	private static EntityActionInstance startCharge(GameTestHelper helper, Player user, StandPower power, String name) {
		Ability found = power.getAbility(name);
		helper.assertTrue(found instanceof EntityActionAbility, "Missing Stand ability " + name);
		StandEntity stand = power.getSummonedStandEntity();
		EntityActionInstance charge = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		LivingComponentAction.getComponent(stand).setAction(charge, user, SyncType.NO_SYNC);
		helper.assertTrue(LivingComponentAction.getComponent(stand).getAction() == charge
				&& charge.getPhase() == ActionPhase.BUTTON_CHARGE, name + " did not start charging");
		return charge;
	}

	// the probe records the line, so probe each line once per player
	private static boolean said(Player player, Holder<SoundEvent> line) {
		return !player.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get()).checkNotRepeatingVoiceLine(line, 1);
	}

	private static StandPower giveSummoned(GameTestHelper helper, Player player) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Magician's Red test player");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null, "Missing Stand type magicians_red");
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
		helper.assertTrue(type.summon(player, power) && power.getSummonedStandEntity() != null,
				"Could not summon Magician's Red");
		return power;
	}

	private static void dismiss(Player player) {
		StandPower power = PowerClass.STAND.get(player);
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		if (stand != null) {
			LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
			power.setSummonedStand(null);
			stand.discard();
		}
		player.discard();
	}
}
