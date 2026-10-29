package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core's client-side static holders that kept the world the client left: after "Save and Quit" the heap still held
 * that world's ClientLevel, its LocalPlayer and their powers through each of them. Each is let go as the client logs out
 * (ClientPlayerNetworkEvent.LoggingOut, after which nothing of that world is drawn), and the subtype cache, common code,
 * as any level unloads. The client classes cannot be loaded here: this reads their sources, comments dropped and
 * whitespace squashed.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldLeftReleaseClientGameTests {
	private static final String ROOT = "src/main/java/rotp/core/";
	private static final String CLIENT_SUBSCRIBER = "@EventBusSubscriber(modid=JojoMod.MOD_ID,value=Dist.CLIENT)";
	private static final String LOGGING_OUT = "ClientPlayerNetworkEvent.LoggingOut";

	private WorldLeftReleaseClientGameTests() {}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theClientsAimsAreEmptiedWhenItLeavesTheWorld(GameTestHelper helper) {
		String code = code(helper, "client/input/ClientsideAim.java");
		helper.assertTrue(code.contains(CLIENT_SUBSCRIBER + "publicclassClientsideAim{"),
				"ClientsideAim is not a client event subscriber, so its logging-out handler would not run");
		String handler = handlerOf(code, LOGGING_OUT);
		helper.assertTrue(handler != null && handler.contains("cameraEntityAimTarget=ActionTarget.EMPTY;")
				&& handler.contains("forget(playerAim);") && handler.contains("forget(standAim);"), "ClientsideAim's "
				+ LOGGING_OUT + " handler does not empty the camera's pick and both aims: they keep entities of the world"
				+ " left, and with them its ClientLevel, after \"Save and Quit\" (" + handler + ")");
		helper.assertTrue(code.contains("privatestaticvoidforget(ActionTargetAimaim){aim.setTarget(ActionTarget.EMPTY);"
				+ "aim.checkDirty();}"), "ClientsideAim.forget does not empty an aim and its last sent target (checkDirty),"
				+ " which only a tick in a world replaces and LivingComponentAction.CLIENT_PLAYER_AIM keeps for good");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theClientsAimTakesNoPickWithoutALevel(GameTestHelper helper) {
		String code = code(helper, "client/input/ClientsideAim.java");
		String update = methodBody(code, "publicstaticvoidupdateTarget(Minecraftmc,floatpartialTick)");
		helper.assertTrue(update != null, "ClientsideAim.updateTarget(Minecraft, float) is gone");
		int set = update.indexOf("cameraEntityAimTarget=");
		helper.assertTrue(set >= 0 && update.startsWith("cameraEntityAimTarget=mc.level!=null&&mc.hitResult!=null"
				+ "?ActionTarget.fromVanilla(mc.hitResult):ActionTarget.EMPTY;", set), "ClientsideAim.updateTarget takes"
				+ " the vanilla pick without a level: on the title screen each client tick stores the last pick of the world"
				+ " left, whose entity keeps its ClientLevel (" + update.substring(Math.max(set, 0), Math.min(update.length(),
				Math.max(set, 0) + 120)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theLifeDetectorsReadoutEntityIsLetGoWhenTheClientLeavesTheWorld(GameTestHelper helper) {
		assertLoggingOutRelease(helper, "impl/stands/goldexperience/client/GoldExperienceLifeDetectorClient.java",
				"publicfinalclassGoldExperienceLifeDetectorClient{", "readoutTarget=null;",
				"Gold Experience's life detector readout keeps its last entity, found while the level renders");
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theHamonSparkSoundDelaysAreLetGoWhenTheClientLeavesTheWorld(GameTestHelper helper) {
		assertLoggingOutRelease(helper, "client/sound/HamonSparksLoopSound.java", "publicclassHamonSparksLoopSound{",
				"SOUND_DELAYS.clear();", "the Hamon spark sound delays keep each entity until it dies, which an entity of"
				+ " the world left never does");
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theVoiceLinesAreLetGoWhenTheClientLeavesTheWorld(GameTestHelper helper) {
		assertLoggingOutRelease(helper, "client/sound/ClientVoiceLineManager.java", "publicfinalclassClientVoiceLineManager{",
				"CURRENT_VOICE_LINES.clear();", "each voice line's sound keeps its entity and level until the same entity id"
				+ " speaks again");
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theStandCryHandlersAreLetGoWhenTheClientLeavesTheWorld(GameTestHelper helper) {
		assertLoggingOutRelease(helper, "client/sound/barrage/StandCrySoundHandler.java", "publicfinalclassStandCrySoundHandler{",
				"TICKING_HANDLERS.clear();", "a Stand cry handler keeps its Stand until its stop condition comes true, which"
				+ " in the world left it may never");
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theHamonRippleHandlersAreLetGoWhenTheClientLeavesTheWorld(GameTestHelper helper) {
		assertLoggingOutRelease(helper, "impl/powers/hamon/client/EnergyRippleLayer.java", "publicstaticclassLogoutEvents{",
				"SPARK_HANDLERS.clear();", "each Hamon ripple handler holds its entity, the weak map's key, which so is never"
				+ " let go");
	}

	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void theSubtypeCacheLetsGoOfAnyLevelThatUnloadsTheClientsToo(GameTestHelper helper) {
		String code = code(helper, "util/mc/entitysubtype/EntityTypeToInstance.java");
		helper.assertTrue(code.contains("@EventBusSubscriber(modid=JojoMod.MOD_ID)publicfinalclassEntityTypeToInstance{"),
				"EntityTypeToInstance is not an event subscriber of both sides, so the client's world is not let go");
		String handler = handlerOf(code, "LevelEvent.Unload");
		helper.assertTrue(handler != null && handler.contains("event.getLevel()instanceofLevellevel")
				&& handler.contains("ENTITY_INSTANCES.values().removeIf(entity->entity.level()==level);")
				&& !handler.contains("ServerLevel") && !handler.contains("isClientSide"), "EntityTypeToInstance's level"
				+ " unload handler does not let go of the instances of any level that unloads, the ClientLevel the client"
				+ " leaves included (" + handler + ")");
		helper.succeed();
	}

	private static void assertLoggingOutRelease(GameTestHelper helper, String file, String subscribedClass, String release,
			String holds) {
		String code = code(helper, file);
		helper.assertTrue(code.contains(CLIENT_SUBSCRIBER + subscribedClass), file + ": the class of the release is not a"
				+ " client event subscriber (" + subscribedClass + "), so its handler would not run");
		String handler = handlerOf(code, LOGGING_OUT);
		helper.assertTrue(handler != null && handler.contains(release), file + " has no " + LOGGING_OUT + " handler that"
				+ " runs " + release + ": " + holds + ", so the client's world stays in memory after \"Save and Quit\"");
		helper.succeed();
	}

	private static String code(GameTestHelper helper, String file) {
		String source = null;
		for (Path at = Path.of("").toAbsolutePath(); at != null && source == null; at = at.getParent()) {
			Path path = at.resolve(ROOT + file);
			if (Files.isRegularFile(path)) {
				try {
					source = Files.readString(path);
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		helper.assertTrue(source != null, "Fixture: " + file + " not found above " + Path.of("").toAbsolutePath());
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\\s+", "");
	}

	// the body of the first @SubscribeEvent public static method that takes the event, or null
	private static String handlerOf(String code, String event) {
		for (int at = code.indexOf("@SubscribeEvent"); at >= 0; at = code.indexOf("@SubscribeEvent", at + 1)) {
			int open = code.indexOf('{', at);
			if (open < 0) {
				return null;
			}
			String head = code.substring(at, open);
			if (head.contains("publicstaticvoid") && head.contains("(" + event)) {
				return block(code, open);
			}
		}
		return null;
	}

	private static String methodBody(String code, String head) {
		int at = code.indexOf(head + "{");
		return at < 0 ? null : block(code, at + head.length()).substring(1);
	}

	private static String block(String code, int open) {
		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) {
				return code.substring(open, i + 1);
			}
		}
		throw new IllegalStateException("Unbalanced braces after " + code.substring(open, Math.min(code.length(), open + 80)));
	}
}
