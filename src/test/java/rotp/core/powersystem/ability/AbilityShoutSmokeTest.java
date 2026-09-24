package rotp.core.powersystem.ability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 Action#playVoiceLine skipped an action's shout while the user sneaked, unless the action was a SHIFT variation
 * (playsVoiceLineOnSneak; StandEntityHeavyAttack also for a finisher; HamonBreath overrode the rule away). Every core
 * shout that 1.16 said through playVoiceLine goes through Ability#sayShout or the Hamon shout, which apply the rule.
 * AbilityShoutGameTests checks the rule per ability in the game.
 */
public final class AbilityShoutSmokeTest {
	private static final String ROOT = "src/main/java/rotp/core/";

	private AbilityShoutSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String ability = compact(source("powersystem/ability/Ability.java"));
		require(ability, "publicbooleanskipsShoutWhileSneaking(@NullableLivingEntityuser){"
				+ "returnuser!=null&&user.isShiftKeyDown()&&!playsVoiceLineOnSneak();}");
		require(ability, "publicbooleansayShout(LivingEntityuser,@NullableHolder<SoundEvent>shout){"
				+ "returnshout!=null&&!skipsShoutWhileSneaking(user)&&JojoModUtil.sayVoiceLine(user,shout);}");
		require(ability, "returnabilityinstanceofAbilityshouting&&shouting.sayShout(user,shout);");
		require(ability, "publicfinalintgetRequiredResolveLevel(@NullablePower<?>context){"
				+ "returngetRequiredResolveLevelToUnlock(context);}");

		// the shouts that 1.16 said through Action#playVoiceLine and that skip a sneaking user
		require(source("impl/stands/theworld/TimeStopAbility.java"), "sayShout(user,voiceLine);");
		require(source("impl/stands/starplatinum/StarFingerAbility.java"), "sayShout(powerUser,ModSoundEvents.JOTARO_STAR_FINGER);");
		require(source("impl/stands/hierophant/HierophantEmeraldSplashAbility.java"),
				"Ability.sayShoutOf(ability,user,ModSoundEvents.KAKYOIN_EMERALD_SPLASH);");
		String chariot = compact(source("impl/stands/silverchariot/SilverChariotBarrageAbility.java"));
		require(chariot, "Ability.sayShoutOf(ability,user,ModSoundEvents.POLNAREFF_FENCING);");
		require(chariot, "Ability.sayShoutOf(ability,user,ModSoundEvents.POLNAREFF_HORA_HORA_HORA);");
		require(source("impl/stands/magiciansred/MagiciansRedRedBindAbility.java"),
				"Ability.sayShoutOf(ability,user,ModSoundEvents.AVDOL_RED_BIND);");
		require(source("impl/stands/crazydiamond/CrazyDRestoreTerrainAbility.java"), "sayShout(powerUser,ModSoundEvents.JOSUKE_FIX);");
		require(source("impl/stands/goldexperience/GoldExperienceCreateLifeformAbility.java"),
				"sayShout(user,ModSoundEvents.GIORNO_NEW_LIFE);");
		require(source("impl/stands/theworld/TheWorldBarrageAbility.java"), "canPlayShout=standAlreadySummoned&&!user.isShiftKeyDown();");
		require(source("impl/powers/hamon/abilities/HamonActionRuntimeAbility.java"),
				"privatevoidsayHamonShout(LivingEntityuser,HamonDatahamon){if(user.level().isClientSide()){return;}"
				+ "if(skipsShoutWhileSneaking(user)){return;}");

		// 1.16 SHIFT variations and HamonBreath shout while sneaking
		require(source("impl/stands/hierophant/HierophantEmeraldSplashAbility.java"),
				"if(concentrated){cooldown(5,60);setPlaysVoiceLineOnSneak();}");
		require(source("impl/powers/hamon/abilities/HamonBubbleCutterAbility.java"), "if(gliding){setPlaysVoiceLineOnSneak();}");
		require(source("impl/powers/hamon/abilities/HamonSunlightYellowOverdriveBarrageAbility.java"),
				"setDefaultPhaseLength(ActionPhase.RECOVERY,6);setPlaysVoiceLineOnSneak();}");
		require(source("impl/powers/hamon/abilities/HamonBreathAbility.java"),
				"setDefaultPhaseLength(ActionPhase.RECOVERY,0);setPlaysVoiceLineOnSneak();}");
		// The World's heavy punch (SHIFT variation) and kick (finisher) shout as before
		require(source("impl/stands/theworld/TheWorldHeavyPunchAbility.java"), "JojoModUtil.sayVoiceLine(powerUser,ModSoundEvents.DIO_DIE);");
		require(source("impl/stands/theworld/TheWorldKickAbility.java"), "JojoModUtil.sayVoiceLine(powerUser,ModSoundEvents.DIO_DIE);");
	}

	private static void require(String text, String token) {
		check(compact(text).contains(token), "the 1.16 shout sneak rule lost: " + token);
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(ROOT + path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + ROOT + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
