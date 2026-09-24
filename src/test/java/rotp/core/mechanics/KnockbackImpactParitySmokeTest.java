package rotp.core.mechanics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.subsystems.timestop.TimeStopState;

/**
 * 1.16 knockback impact parts the port had lost: the collision hook after vanilla collision (EntityMixin), the save
 * with the entity (EntityUtilCap "KbImpact"), what the impact does to what it hits (knockback without a successful
 * hurt, non-living targets, fire in whole seconds, cactus 1), and knockback3d's push on a stopped target
 * (DamageUtil). TimeStopKnockbackGameTests runs them in a world.
 */
public final class KnockbackImpactParitySmokeTest {
	private static final String IMPACT = "src/main/java/rotp/core/mechanics/KnockbackCollisionImpact.java";
	private static final String MIXIN = "src/main/java/rotp/core/mixin/KnockbackEntityCollision.java";
	private static final String ATTACHMENTS = "src/main/java/rotp/core/init/ModDataAttachmentTypes.java";
	private static final String DAMAGE = "src/main/java/rotp/core/util/functions/DamageUtil.java";
	private static final String STATE = "src/main/java/rotp/core/subsystems/timestop/TimeStopState.java";

	private KnockbackImpactParitySmokeTest() {}

	public static void run() {
		checkFireSeconds();
		checkCollisionHook();
		checkSave();
		checkWhatItHits();
		checkKnockback3dOnStoppedTarget();
	}

	private static void checkFireSeconds() {
		int[][] cases = {{0, 0}, {19, 0}, {20, 20}, {50, 40}, {100, 100}, {139, 120}};
		for (int[] c : cases) {
			check(KnockbackCollisionImpact.wholeSecondsOfFire(c[0]) == c[1],
					"1.16 set " + c[0] + " fire ticks as " + c[1] / 20 + " whole seconds");
		}
	}

	private static void checkCollisionHook() {
		String mixin = code(MIXIN);
		check(mixin.contains("@Inject(method=\"collide\",at=@At(\"RETURN\"),cancellable=true)")
				&& !mixin.contains("@At(\"HEAD\")"),
				"the impact's collision hook must run after vanilla collision, as 1.16's TAIL hook did");
		String hook = body(mixin, "privatevoidjojo_ripples$collideBreakBlocks(Vec3movementVec,CallbackInfoReturnable<Vec3>ci)", MIXIN);
		check(hook.startsWith("if(jojo_ripples$repeatCollide){return;}"),
				"the repeated collide must not run the impact again");
		check(hook.contains("KnockbackCollisionImpactkbCollision=KnockbackCollisionImpact.getExistingHandler((Entity)(Object)this);"),
				"the hook must look the impact up each time (loading NBT replaces it) without attaching one to every entity");
		check(hook.contains("kbCollision.collideBreakBlocks(movementVec,ci.getReturnValue(),level)"),
				"the hook must pass vanilla's collided move, so blocks are checked only when it changed the move");
		check(hook.contains("jojo_ripples$repeatCollide=true;try{ci.setReturnValue(collide(movementVec));}"
				+ "finally{jojo_ripples$repeatCollide=false;}"),
				"after breaking blocks the move must be collided again (1.16 repeatCollide)");
	}

	private static void checkSave() {
		check(code(ATTACHMENTS).contains("\"kb_impact\",()->AttachmentType.builder(obj->objinstanceofEntityentity?newKnockbackCollisionImpact(entity):null)"
				+ ".serialize(KnockbackCollisionImpact.SERIALIZER).build());"),
				"the impact attachment must be saved with its entity");
		String impact = code(IMPACT);
		check(impact.contains("publicCompoundTagwrite(KnockbackCollisionImpactimpact,HolderLookup.Providerprovider){"
				+ "returnimpact.isActive()?impact.serializeNBT(provider):null;}"),
				"only an armed impact may be written");
		check(impact.contains("KnockbackCollisionImpactimpact=newKnockbackCollisionImpact(holderEntity);"
				+ "impact.deserializeNBT(provider,tag);returnimpact;"),
				"a saved impact must be read back into a new one for its entity");
		check(body(impact, "protectedvoidcollideBoundingBox(Entityentity,Vec3movementVec,booleancollideBlocks,booleanbreakBlocks)", IMPACT)
				.contains("if(explosionRadius>0&&attacker!=null){"),
				"an impact loaded without its attacker must not reach the explosion that needs one");
	}

	private static void checkWhatItHits() {
		String impact = code(IMPACT);
		String collide = body(impact, "protectedbooleanonCollideWith(Entitytarget,LivingEntitytargetAsLiving,Vec3thisEntityMotion)", IMPACT);
		check(collide.startsWith("if(targetAsLiving!=null&&syoPunchBaseDamage>0&&attacker!=null){"),
				"only the Hamon part may be for living targets: 1.16 hurt any entity flown into");
		check(collide.contains("wholeSecondsOfFire(scarletOverdriveFireTicks),false);"
				+ "if(targetAsLiving!=null){targetAsLiving.knockback((float)getKnockbackImpactStrength(),-thisEntityMotion.x,-thisEntityMotion.z);}"
				+ "returnhurt;")
				&& !collide.contains("if(hurt)"),
				"1.16 knocked a living target back whether or not the hurt went through");
		check(body(impact, "protectedvoidcollideBoundingBox(Entityentity,Vec3movementVec,booleancollideBlocks,booleanbreakBlocks)", IMPACT)
				.contains("if(blockState.getBlock()instanceofCactusBlock){hurtTarget(entity,level.damageSources().cactus(),1);}"),
				"1.16 hurt 1 for flying into a cactus");
	}

	private static void checkKnockback3dOnStoppedTarget() {
		check(!TimeStopState.stackedKnockbackInstead(null), "no event was stacked");
		String knockback3d = body(code(DAMAGE), "publicstaticvoidknockback3d(LivingEntitytarget,floatstrength,floatxRot,floatyRot)", DAMAGE);
		check(knockback3d.contains("if(event.isCanceled()&&!TimeStopState.stackedKnockbackInstead(event)){return;}"),
				"a knockback stacked on a stopped target must still get knockback3d's push");
		check(knockback3d.contains("if(targetinstanceofStandEntitystand){LivingEntitystandUser=stand.getUser();"
				+ "if(standUser!=null&&!standUser.is(target)){upwardsKnockback(standUser,(float)knockbackVec.y*strength);}}"),
				"a Stand hit by knockback3d must pass the upward part to its user");
		String state = code(STATE);
		check(body(state, "publicstaticvoidstackKnockbackWhileStopped(LivingKnockBackEventevent)", STATE)
				.endsWith("lastStackedKnockback=newWeakReference<>(event);"),
				"the frozen-knockback stack must mark the event it took over");
		check(body(state, "publicstaticbooleanstackedKnockbackInstead(LivingKnockBackEventevent)", STATE)
				.equals("returnevent!=null&&lastStackedKnockback.get()==event;"),
				"only the event the stack took over may count as stacked");
	}

	private static String body(String code, String signature, String path) {
		int start = code.indexOf(signature + "{");
		check(start >= 0, path + " has no " + signature);
		int open = start + signature.length();
		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return code.substring(open + 1, i);
			}
		}
		throw new AssertionError(path + ": unbalanced " + signature);
	}

	private static String code(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return stripComments(Files.readString(path)).replaceAll("\\s+", "");
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String stripComments(String source) {
		StringBuilder code = new StringBuilder(source.length());
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '"' || c == '\'') {
				int end = literalEnd(source, i);
				code.append(source, i, end);
				i = end - 1;
			}
			else if (source.startsWith("//", i)) {
				int end = source.indexOf('\n', i);
				i = (end < 0 ? source.length() : end) - 1;
			}
			else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				i = (end < 0 ? source.length() : end + 2) - 1;
			}
			else {
				code.append(c);
			}
		}
		return code.toString();
	}

	private static int literalEnd(String source, int start) {
		char quote = source.charAt(start);
		for (int i = start + 1; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
			}
			else if (c == quote) {
				return i + 1;
			}
		}
		return source.length();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
