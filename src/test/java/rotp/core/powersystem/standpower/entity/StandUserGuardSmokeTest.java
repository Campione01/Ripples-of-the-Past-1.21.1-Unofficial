package rotp.core.powersystem.standpower.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 GameplayEventHandler.standBlockUserAttack / blockDamage and StandEntity.standDamageResistance's
 * getDamageBlockMultiplier. The math runs here; the wiring is pinned in the sources, and StandUserGuardGameTests runs
 * the hits in the game.
 */
public final class StandUserGuardSmokeTest {
	private static final String STAND_ENTITY =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandEntity.java";
	private static final String USER_GUARD =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandUserGuard.java";
	private static final String EVENT_HANDLER = "src/main/java/rotp/core/core/EventHandler.java";
	private static final String LIVING_MIXIN = "src/main/java/rotp/core/mixin/damage/LivingEntityMixin.java";
	private static final String DAMAGE_SOURCE_MODIFIED = "src/main/java/rotp/core/customobjects/DamageSourceModified.java";

	private StandUserGuardSmokeTest() {}

	public static void run() {
		verifyUserHitMath();
		verifyExplosionMath();
		verifyEventOrder();
		verifyGuardConditions();
		verifyTaskMultiplier();
		verifyCancelledHitContainer();
	}

	/*
	 * NeoForge leaves a cancelled hit's DamageContainer on the stack, and every guard redirect cancels one; the
	 * knockback readers then took its modifiers for later knockback. A redirected hit also reaches the user's knockback
	 * through StandEntity.knockback, already modified, so the readers skip it while the Stand takes it.
	 */
	private static void verifyCancelledHitContainer() {
		requireInOrder(compact(source(LIVING_MIXIN)),
				"@WrapOperation(method=\"hurt\",at=@At(value=\"INVOKE\",remap=false,"
						+ "target=\"Lnet/neoforged/neoforge/common/CommonHooks;onEntityIncomingDamage(\"",
				"booleancanceled=original.call(entity,container);"
						+ "if(canceled&&!damageContainers.isEmpty()&&damageContainers.peek()==container){damageContainers.pop();}"
						+ "returncanceled;",
				"DamageSourceModified.afterKnockbackApplied(entity,DamageSourceModified.currentKnockbackSource(entity));");
		String modified = compact(source(DAMAGE_SOURCE_MODIFIED));
		requireInOrder(modified,
				"publicstaticDamageSourcecurrentKnockbackSource(LivingEntitytarget){if(target.damageContainers.isEmpty()){returnnull;}"
						+ "DamageContainercurDamage=target.damageContainers.peek();"
						+ "returnStandUserGuard.isRedirecting(curDamage)?null:curDamage.getSource();}",
				"if(currentKnockbackSource(event.getEntity())instanceofDamageSourceModifiedkbModifier){"
						+ "event.setStrength((event.getStrength()+kbModifier.jojo_ripples$addKnockback())",
				"if(currentKnockbackSource(event.getEntity())instanceofDamageSourceModifiedkbModifier){"
						+ "floatxRotDeg=kbModifier.jojo_ripples$knockbackXRotDeg();");
		check(occurrences(modified, "damageContainers.peek()") == 1, "a knockback reader takes the stack top directly");
		requireInOrder(compact(source(USER_GUARD)),
				"publicstaticbooleanisRedirecting(DamageContainercontainer){return!REDIRECTING.isEmpty()&&REDIRECTING.contains(container);}",
				"publicstaticbooleanblockedWhole(DamageContainercontainer){return!BLOCKED_WHOLE.isEmpty()&&BLOCKED_WHOLE.contains(container);}",
				"if(!BLOCKED_WHOLE.isEmpty()){BLOCKED_WHOLE.clear();}");
	}

	// 1.16: max(amount - durability / 2, 0)
	private static void verifyUserHitMath() {
		check(StandUserGuard.userHitAfterGuard(11, 16) == 3, "a hit loses half the durability");
		check(StandUserGuard.userHitAfterGuard(8, 16) == 0 && StandUserGuard.userHitAfterGuard(2, 16) == 0,
				"a hit up to half the durability is blocked whole");
		check(StandUserGuard.userHitAfterGuard(5, 0) == 5, "no durability, no cut");
	}

	// 1.16: durability > 4 and cos > 0, then max(1 - cos, 4 / durability)
	private static void verifyExplosionMath() {
		check(StandUserGuard.explosionMultiplier(1, 16) == 0.25F, "straight ahead the durability floor holds");
		check(StandUserGuard.explosionMultiplier(0.5, 16) == 0.5F, "at 60 degrees half is cut");
		check(StandUserGuard.explosionMultiplier(0.1, 16) == 0.9F, "at the side little is cut");
		check(StandUserGuard.explosionMultiplier(0, 16) == 1 && StandUserGuard.explosionMultiplier(-0.7, 16) == 1,
				"nothing is cut beside or behind the guard");
		check(StandUserGuard.explosionMultiplier(1, 4) == 1 && StandUserGuard.explosionMultiplier(1, 3) == 1,
				"a guard of durability 4 or less cuts no explosion");
		check(StandUserGuard.explosionMultiplier(0.9, 8) == 0.5F, "4 / durability is the least left");
	}

	// Attack stage (redirect) before the Vampirism and Pillar Man checks; hurt stage (block) before Hamon protection.
	private static void verifyEventOrder() {
		String handler = compact(source(EVENT_HANDLER));
		requireInOrder(handler,
				"@SubscribeEvent(priority=EventPriority.HIGHEST)publicstaticvoidonPillarmanUtilityIncomingDamage(LivingIncomingDamageEventevent){"
						+ "StandUserGuard.redirectToGuardingStand(event);if(VampirismFreezeAbility.onUserIncomingDamage(event)");
		requireInOrder(handler,
				"@SubscribeEvent(priority=EventPriority.HIGH)publicstaticvoidonHamonProtectionIncomingDamage(",
				"if(HamonSnakeMufflerAbility.onUserIncomingDamage(event)){event.setCanceled(true);return;}",
				"if(StandUserGuard.blockDamage(event)){return;}",
				"HamonProtectionAbility.reduceDamageAmount(");
		check(occurrences(handler, "StandUserGuard.") == 2, "the user guard runs once per stage");
	}

	private static void verifyGuardConditions() {
		String guard = compact(source(USER_GUARD));
		requireInOrder(guard,
				// the attack stage takes what the Stand can take, the hurt stage the rest
				"if(stand!=null&&standTakesHit(stand,user,source)){DamageContainercontainer=event.getContainer();"
						+ "REDIRECTING.add(container);try{stand.hurt(source,event.getAmount());}"
						+ "finally{REDIRECTING.remove(container);}event.setCanceled(true);}",
				"if(source.is(DamageTypeTags.IS_EXPLOSION)){",
				"if(stand==null||standTakesHit(stand,user,source)){returnfalse;}",
				"stand.playStandBlockSound(source);NoKnockbackOnBlocking.setOneTickKbRes(stand);",
				"returnstand!=null&&stand!=user&&stand.guardsHitsOnUser()&&stand.isFollowingUser()&&stand.isStandBlocking()?stand:null;",
				"if(sourceinstanceofStandLinkDamageSource||source.getDirectEntity()==null||sourcePos==null){returnnull;}",
				"returnstand!=null&&stand.canBlockDamage(source)&&stand.canBlockFromAngle(sourcePos)?stand:null;",
				"returnattacker!=stand&&attacker!=user&&!stand.isInvulnerableTo(source);",
				// in the hurt cooldown only the excess over lastHurt is cut; lastHurt keeps the whole hit
				"if(user.invulnerableTime>10&&!source.is(DamageTypeTags.BYPASSES_COOLDOWN)){",
				"floatleft=cut.apply(Math.max(amount-lastHurt,0));if(left<=0&&onLanded!=null){BLOCKED_WHOLE.add(container);}"
						+ "returnamount-left;",
				"booleanshieldTakesIt=user.isDamageSourceBlocked(source);",
				"user.lastHurt+=removed;",
				// 1.16 cancelled a hit the Stand's block took whole; the explosion cut never did
				"booleanwhole=!shieldTakesIt&&reduced<=0;if(whole&&onLanded!=null){BLOCKED_WHOLE.add(event.getContainer());}"
						+ "returnwhole;",
				"explosion.damageSource==source");
	}

	private static void verifyTaskMultiplier() {
		String stand = compact(source(STAND_ENTITY));
		requireInOrder(stand,
				"protectedfloatstandDamageResistance(DamageSourcedmgSource,floatdmgAmount,booleanisBlocking){",
				"standCrash();",
				"EntityActionInstancecurAction=getCurStandAction();"
						+ "floatmultiplier=curAction!=null?getDamageBlockMultiplier(curAction):0;"
						+ "if(multiplier!=0){blockedRatio+=(1-blockedRatio)*multiplier;}"
						+ "if(blockedRatio>=1){wasDamageBlocked=true;",
				"if(wasDamageBlocked){blockDamage+=finalDamage;NoKnockbackOnBlocking.setOneTickKbRes(this);}",
				"protectedfloatgetDamageBlockMultiplier(EntityActionInstancecurAction){return0.5F;}");
		check(!stand.contains(".getDamageBlockMultiplier(userPower,this,task)"), "the 1.16 multiplier is still commented out");
		requireInOrder(stand, "protectedbooleanguardsHitsOnUser(){returntrue;}");
		// 1.16 getDurability: a vampire user high on blood doubles it
		requireInOrder(stand, "publicdoublegetDurability(){doubledurability=getAttributeValue(ModEntityAttributes.STAND_DURABILITY);"
				+ "LivingEntityuser=getUser();if(user!=null&&ModPlayerPowers.VAMPIRISM.get().isHighOnBlood(user)){durability*=2;}"
				+ "returndurability*getStandEfficiency();}");
	}

	private static void requireInOrder(String text, String... tokens) {
		int from = 0;
		for (String token : tokens) {
			int at = text.indexOf(token, from);
			check(at >= 0, "Stand user guard source lost or reordered: " + token);
			from = at + token.length();
		}
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
