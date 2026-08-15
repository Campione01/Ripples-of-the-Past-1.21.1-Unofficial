package com.github.standobyte.jojo.client.firstperson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FirstPersonExtraArmContractSmokeTest {
	private FirstPersonExtraArmContractSmokeTest() {}

	public static void main(String[] args) {
		run(Path.of(System.getProperty("user.dir")));
	}

	public static void run(Path root) {
		String firstPersonRender = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
				+ "firstperson/FirstPersonRender.java"));
		String helper = between(
				firstPersonRender,
				"public static boolean renderExtraPlayerArm(",
				"/**\n\t * @return true");
		check(helper.contains(
				"jojo_ripples$getEquipProgress(")
				&& helper.contains("renderEntityArm(")
				&& helper.contains("finally {"),
				"extra-arm helper lost equip interpolation or arm rendering");
		check(!helper.contains(
				"renderSpecificFirstPersonHand"),
				"extra-arm helper recursively posts RenderHandEvent");
		check(firstPersonRender.contains(
				"public static boolean vanillaRendersBothMapArms(")
				&& firstPersonRender.contains(
						"jojo_ripples$vanillaRendersBothMapArms("),
				"vanilla two-handed-map decision is not exposed");

		String access = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/"
				+ "firstperson/FirstPersonItemRendererAccess.java"));
		check(access.contains(
				"float jojo_ripples$getEquipProgress(")
				&& access.contains(
						"boolean "
						+ "jojo_ripples$vanillaRendersBothMapArms("),
				"first-person renderer access contract is missing");

		String hamonAura = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/powers/"
				+ "hamon/client/particle/custom/FirstPersonHamonAura.java"));
		String sunlightYellowExtraArm = between(
				hamonAura,
				"public static void renderSunlightYellowOffHandAura(",
				"private static boolean isSunlightYellowOverdrive(");
		check(sunlightYellowExtraArm.contains("event.isCanceled()")
				&& sunlightYellowExtraArm.contains(
						"event.getHand() != InteractionHand.MAIN_HAND")
				&& sunlightYellowExtraArm.contains(
						"mc.getCameraEntity() != player")
				&& sunlightYellowExtraArm.contains("player.isInvisible()")
				&& sunlightYellowExtraArm.contains(
						"!event.getItemStack().isEmpty()")
				&& sunlightYellowExtraArm.contains(
						"!player.getMainHandItem().isEmpty()")
				&& sunlightYellowExtraArm.contains(
						"!player.getOffhandItem().isEmpty()")
				&& sunlightYellowExtraArm.contains("firstPersonHamonAura")
				&& sunlightYellowExtraArm.contains(
						"FirstPersonRender.vanillaRendersBothMapArms(event)")
				&& sunlightYellowExtraArm.contains(
						"!isSunlightYellowOverdrive(player)")
				&& sunlightYellowExtraArm.contains("hasDrawableParticles(")
				&& sunlightYellowExtraArm.contains(
						"event, player, InteractionHand.OFF_HAND")
				&& sunlightYellowExtraArm.contains("finally {")
				&& !sunlightYellowExtraArm.contains("event.setCanceled"),
				"Sunlight Yellow extra-arm production gates drifted");
		check(count(hamonAura, "renderExtraPlayerArm(") == 1
				&& hamonAura.contains(
						"SUNLIGHT_YELLOW_OVERDRIVE.equals(")
				&& hamonAura.contains(
						"action.ability.getAbilityId().nameInMoveset()")
				&& hamonAura.contains(
						"particlesForHand != null "
						+ "&& !particlesForHand.isEmpty()"),
				"Sunlight Yellow exact action or drawable-particle gate drifted");

		String mixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "client/firstperson/ItemInHandRendererMixin.java"));
		check(mixin.contains(
				"implements FirstPersonItemRendererAccess")
				&& count(mixin, "@Shadow private float") == 4
				&& count(mixin, "Mth.lerp(") == 2
				&& mixin.contains(
						"mainHandItem.getItem() instanceof MapItem")
				&& mixin.contains("offHandItem.isEmpty()")
				&& mixin.contains(
						"FirstPersonRender.init("
						+ "(ItemInHandRenderer) (Object) this)"),
				"renderer-owned equip interpolation bridge drifted");

		String zoomPunchCancel = between(
				mixin,
				"@Inject(method = \"renderArmWithItem\"",
				"if (stack.is(ModItems.PHOTO.get()))");
		check(zoomPunchCancel.contains("at = @At(\"HEAD\")")
				&& zoomPunchCancel.contains("cancellable = true")
				&& zoomPunchCancel.contains(
						"hand == InteractionHand.MAIN_HAND")
				&& zoomPunchCancel.contains(
						"HamonZoomPunchState.isUsingZoomPunch(player)")
				&& zoomPunchCancel.contains("ci.cancel();")
				&& zoomPunchCancel.contains("return;"),
				"Zoom Punch first-person main-arm cancellation drifted");
	}

	private static String between(
			String source,
			String startToken,
			String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static int count(String source, String token) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
