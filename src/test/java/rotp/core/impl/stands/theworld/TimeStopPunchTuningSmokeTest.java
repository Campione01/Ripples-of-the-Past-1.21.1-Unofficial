package rotp.core.impl.stands.theworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 TheWorldTSHeavyAttack was built with its blink and priced its time skip from it (half the blink's start
 * cost plus the blink's per-tick cost); an add-on TS punch set its own staminaCost and could train nothing (Diego's).
 * 1.16 TimeStopInstant#getEntityTargetTeleportPos was an add-on hook (Shadow The World), and TimeStop.Builder#heldWalkSpeed
 * a per-time-stop setting that the core keeps at 1 (owner boundary, Batch907). The wiring is pinned here;
 * TimeStopPunchTuningGameTests checks the numbers in the game.
 */
public final class TimeStopPunchTuningSmokeTest {
	private static final String TS_PUNCH = "src/main/java/rotp/core/impl/stands/theworld/TheWorldTSPunchAbility.java";
	private static final String BLINK = "src/main/java/rotp/core/impl/stands/theworld/TimeStopBlinkAbility.java";
	private static final String LEARNING = "src/main/java/rotp/core/subsystems/timestop/TimeStopLearning.java";
	private static final String TIME_STOP = "src/main/java/rotp/core/impl/stands/theworld/TimeStopAbility.java";

	private TimeStopPunchTuningSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String tsPunch = compact(source(TS_PUNCH));
		// The World's own TS punch: 1.16 DEFAULT_STAMINA_COST, trains its time stop
		require(tsPunch, "privatestaticfinalfloatSTAMINA_COST=50F;");
		require(tsPunch, "privatefloatstaminaCost=STAMINA_COST;privatebooleantrainsTimeStop=true;");
		require(tsPunch, "returncheck.isPositive()?StandAbilityStamina.check(context,staminaCost):check;");
		require(tsPunch, "floatstaminaCost=abilityinstanceofTheWorldTSPunchAbilitytsPunch?tsPunch.staminaCost:STAMINA_COST;"
				+ "if(getPowerUser()==null||!StandAbilityStamina.consumeOrMessage(ability,standPower,getPowerUser(),staminaCost)){");
		require(tsPunch, "booleantrainsTimeStop=!(abilityinstanceofTheWorldTSPunchAbilitytsPunch)||tsPunch.trainsTimeStop;");
		require(tsPunch, "TimeStopLearning.consumeTsPunchTimeStopStamina(standPower,timeStopTicks);"
				+ "if(trainsTimeStop){TimeStopLearning.onTsPunchTimeSkip(standPower,timeStopTicks);}");
		check(occurrences(tsPunch, "STAMINA_COST)") == 0, "the TS punch cost must be the ability's own");

		String learning = compact(source(LEARNING));
		require(learning, "return(blink!=null?blink.getBlinkStaminaCost(power):getTimeStopBlinkStaminaCost(power))"
				+ "*TS_PUNCH_BLINK_STAMINA_RATIO;");
		require(learning, "returnblink!=null?blink.getBlinkStaminaCostTicking(power,getLearningName(power,"
				+ "blink.getTimeStopAbilityName())):getTimeStopBlinkStaminaCostTicking(power);");
		require(learning, "power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK)instanceofTimeStopBlinkAbilityblink");

		String blink = compact(source(BLINK));
		require(blink, "privateVec3getEntityTargetTeleportPos(LivingEntityuser,EntitytargetEntity){"
				+ "if(entityTargetTeleportPos!=null){returnentityTargetTeleportPos.apply(user,targetEntity);}"
				+ "if(teleportBehindEntity){");

		String timeStop = compact(source(TIME_STOP));
		require(timeStop, "privatefloatheldWalkSpeed=1.0F;");
		require(timeStop, "publicvoidonSetPhase(ActionPhasenewPhase){userWalkSpeed=1.0F;"
				+ "if(newPhase==ActionPhase.BUTTON_CHARGE&&abilityinstanceofTimeStopAbilitytimeStop){"
				+ "userWalkSpeed=timeStop.heldWalkSpeed;}}");
		check(!timeStop.contains("userWalkSpeed=0"), "the core time stop's charge must not lock walking");
	}

	private static void require(String text, String token) {
		check(text.contains(token), "TS punch or blink tuning source lost: " + token);
	}

	private static int occurrences(String text, String token) {
		int count = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			count++;
		}
		return count;
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
