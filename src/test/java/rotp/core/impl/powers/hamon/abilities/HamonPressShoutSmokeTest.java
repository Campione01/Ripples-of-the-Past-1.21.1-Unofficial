package rotp.core.impl.powers.hamon.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 PowerBaseImpl.onClickAction said an action's line on the press, a hold-to-fire one's too; HamonSunlightYellow-
 * OverdriveBarrage.perform then said the barrage line with interrupt on. HamonSendoOverdrive.stoppedHolding sent the
 * wave on any stop with a block targeted, a release that failed its energy check included, and still asked for the
 * cost. HamonPressShoutGameTests runs them in the game; the wiring is pinned here.
 */
public final class HamonPressShoutSmokeTest {
	private static final String DIR = "src/main/java/rotp/core/impl/powers/hamon/abilities/";

	private HamonPressShoutSmokeTest() {}

	public static void run() {
		verifyPressShout();
		verifyBarrageLine();
		verifySendoRelease();
	}

	private static void verifyPressShout() {
		String runtime = compact(source(DIR + "HamonActionRuntimeAbility.java"));
		requireInOrder(runtime,
				"protectedvoidplayHamonShout(LivingEntityuser,HamonDatahamon){if(pressShoutPlayed(user)){return;}"
						+ "sayHamonShout(user,hamon);}",
				"privatebooleanpressShoutPlayed(LivingEntityuser){returnisHamonHoldToFire()"
						+ "&&LivingComponentAction.getCurEntityAction(user)instanceofHamonRuntimeActionInstanceaction"
						+ "&&action.hamonAbility()==this&&action.pressShoutPlayed;}",
				"privatevoidsayHamonShout(LivingEntityuser,HamonDatahamon){if(user.level().isClientSide()){return;}",
				"publicstaticclassHamonRuntimeActionInstanceextendsEntityActionInstance{",
				"protectedvoid_onTick(){HamonActionRuntimeAbilitypressed=hamonAbility();"
						+ "if(!pressShoutPlayed&&pressed!=null&&pressed.isHamonHoldToFire()"
						+ "&&getPhase()==ActionPhase.WINDUP&&!level().isClientSide()){pressShoutPlayed=true;",
				"if(hamon!=null){pressed.sayHamonShout(user,hamon);}}",
				"if(!runtimeApplied&&getPhase()==ActionPhase.PERFORM&&getPhaseTick()<1){");
	}

	private static void verifyBarrageLine() {
		String barrage = compact(source(DIR + "HamonSunlightYellowOverdriveBarrageAbility.java"));
		requireInOrder(barrage,
				"publicvoidactionPerformStart(){",
				"JojoModUtil.sayVoiceLine(user,ModSoundEvents.JONATHAN_SYO_BARRAGE,1.0F,1.0F,200,true);");
	}

	private static void verifySendoRelease() {
		String sendo = compact(source(DIR + "HamonSendoOverdriveAbility.java"));
		requireInOrder(sendo,
				"protectedbooleanconsumeRuntimeOnPerform(LivingEntityuser){"
						+ "if(getSendoBlockTarget(user,user.level()).getType()!=TargetType.BLOCK){returnfalse;}",
				"if(!hamon.isAbilityOnCooldown(name())&&hasHamonEnergy(context,hamon)){super.consumeRuntimeOnPerform(user);}"
						+ "else{hamon.getHamonEnergyUsageEfficiency(ENERGY_COST,true,user);hamon.syncOnUpdate(user);"
						+ "playHamonShout(user,hamon);}returntrue;}");
	}

	private static void requireInOrder(String text, String... tokens) {
		int from = 0;
		for (String token : tokens) {
			int at = text.indexOf(token, from);
			check(at >= 0, "Hamon press shout or Sendo release source lost or reordered: " + token);
			from = at + token.length();
		}
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
