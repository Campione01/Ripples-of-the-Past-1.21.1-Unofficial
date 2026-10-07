package rotp.core.client.entityanim;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.core.JojoMod;

/**
 * The loader scans every namespace's {@code animations} folder, as 1.16 did. Other mods keep their own
 * files there (TACZ ships gun animations as {@code tacz:animations/*.animation.json}), and every clip of
 * theirs the parser cannot read used to be reported as an error, hundreds per start. A clip that fails in
 * a file of the core, of an add-on or of a pack overriding one of them is still an error.
 */
public final class AnimationLoaderForeignFilesSmokeTest {
	private AnimationLoaderForeignFilesSmokeTest() {}

	private static final String CORE_PACK = "mod/jojo_ripples";
	private static final String ADDON_PACK = "mod/rotp_fixture_addon";
	private static final String ADDON_MOD_ID = "rotp_fixture_addon";
	/** Add-ons keep 1.16 asset namespaces that differ from their mod id (kiss, scary_monsters...). */
	private static final String ADDON_ASSET_NAMESPACE = "fixture_legacy_ns";
	private static final String FOREIGN_PACK = "tacz_default_gun";
	private static final String USER_PACK = "file/skin_pack.zip";

	private static final String VALID_CLIP = """
			{ "animation_length": 0.5, "bones": { "body": { "rotation": { "0.0": [0, 0, 0], "0.5": [10, 0, 0] } } } }""";
	/** Bedrock's uniform scalar scale, which the Gecko keyframe parser does not read. */
	private static final String UNREADABLE_CLIP = """
			{ "animation_length": 0.5, "bones": { "body": { "scale": 1 } } }""";

	public static void main(String[] args) {
		KeyframesMolangEngine.init();
		List<LogEvent> events = captureLoaderLog();

		Map<ResourceLocation, List<Resource>> files = new LinkedHashMap<>();
		files.put(animFile(JojoMod.MOD_ID, "fixture_core"), List.of(
				file(CORE_PACK, animations("good", VALID_CLIP, "broken_core_clip", UNREADABLE_CLIP))));
		files.put(animFile("tacz", "ak47"), List.of(
				file(FOREIGN_PACK, "{ \"format_version\": \"1.8.0\", \"animations\": { "
						+ "\"draw\": " + VALID_CLIP + ", "
						+ "\"foreign_clip_a\": " + UNREADABLE_CLIP + ", "
						+ "\"foreign_clip_b\": " + UNREADABLE_CLIP + " } }")));
		files.put(animFile("tacz", "not_an_animation_file"), List.of(
				file(FOREIGN_PACK, "{ \"format_version\": \"1.8.0\" }")));
		files.put(animFile(ADDON_MOD_ID, "fixture_addon"), List.of(
				file(ADDON_PACK, animations("good", VALID_CLIP, "broken_addon_clip", UNREADABLE_CLIP))));
		files.put(animFile(ADDON_ASSET_NAMESPACE, "fixture_legacy"), List.of(
				file(ADDON_PACK, animations("good", VALID_CLIP, "broken_legacy_ns_clip", UNREADABLE_CLIP))));
		// a user pack that overrides an add-on's file layers on top of it in the same stack
		files.put(animFile(ADDON_ASSET_NAMESPACE, "fixture_overridden"), List.of(
				file(ADDON_PACK, animations("good", VALID_CLIP)),
				file(USER_PACK, animations("broken_override_clip", UNREADABLE_CLIP))));
		// and one that adds a new file to an add-on's namespace
		files.put(animFile(ADDON_ASSET_NAMESPACE, "fixture_added_by_pack"), List.of(
				file(USER_PACK, animations("broken_pack_clip", UNREADABLE_CLIP))));

		Map<ResourceLocation, AnimationSet.Builder> loaded = new FixtureLoader().prepare(manager(files), InactiveProfiler.INSTANCE);

		for (ResourceLocation ours : List.of(
				JojoMod.resLoc("fixture_core"),
				ResourceLocation.fromNamespaceAndPath(ADDON_MOD_ID, "fixture_addon"),
				ResourceLocation.fromNamespaceAndPath(ADDON_ASSET_NAMESPACE, "fixture_legacy"),
				ResourceLocation.fromNamespaceAndPath(ADDON_ASSET_NAMESPACE, "fixture_overridden"))) {
			AnimationSet.Builder set = loaded.get(ours);
			check(set != null && set.namedAnimations.containsKey("good"),
					"the readable clip of " + ours + " was not loaded: " + loaded.keySet());
		}
		// what a foreign file does give us is kept, as before: only the reporting changes
		AnimationSet.Builder foreign = loaded.get(ResourceLocation.fromNamespaceAndPath("tacz", "ak47"));
		check(foreign != null && foreign.namedAnimations.containsKey("draw"),
				"the readable clip of the foreign file is no longer loaded: " + loaded.keySet());

		for (String ownClip : List.of("broken_core_clip", "broken_addon_clip", "broken_legacy_ns_clip",
				"broken_override_clip", "broken_pack_clip")) {
			check(events.stream().anyMatch(event -> event.getLevel() == Level.ERROR && mentions(event, ownClip)),
					"no error was logged for the unreadable clip " + ownClip + " of a core or add-on animation file");
		}

		List<String> foreignErrors = events.stream()
				.filter(event -> event.getLevel().isMoreSpecificThan(Level.INFO))
				.filter(event -> mentions(event, "tacz:animations/"))
				.map(event -> event.getLevel() + " " + event.getMessage().getFormattedMessage())
				.toList();
		check(foreignErrors.isEmpty(), foreignErrors.size() + " log line(s) at info or above for another mod's "
				+ "animation files, first: " + (foreignErrors.isEmpty() ? "" : foreignErrors.get(0)));
		for (String foreignName : List.of("foreign_clip_a", "foreign_clip_b", "not_an_animation_file")) {
			check(events.stream().anyMatch(event -> event.getLevel() == Level.DEBUG && mentions(event, foreignName)),
					"the skipped foreign " + foreignName + " left no debug line to diagnose it with");
		}

		System.out.println("Animation loader foreign files smoke test passed: another mod's unreadable clips are "
				+ "debug lines, core/add-on/override clips stay errors, readable clips load everywhere.");
	}

	/** The mod list is not there outside the game; the fixture add-on is the one mod depending on the core. */
	private static final class FixtureLoader extends AnimationLoader {
		protected Set<String> coreAndDependentModIds() {
			return Set.of(JojoMod.MOD_ID, ADDON_MOD_ID);
		}
	}

	private static boolean mentions(LogEvent event, String text) {
		return event.getMessage().getFormattedMessage().contains(text);
	}

	private static ResourceLocation animFile(String namespace, String name) {
		return ResourceLocation.fromNamespaceAndPath(namespace, "animations/" + name + ".animation.json");
	}

	private static String animations(String... namesAndClips) {
		StringBuilder json = new StringBuilder("{ \"geckolib_format_version\": 2, \"animations\": { ");
		for (int i = 0; i < namesAndClips.length; i += 2) {
			if (i > 0) json.append(", ");
			json.append('"').append(namesAndClips[i]).append("\": ").append(namesAndClips[i + 1]);
		}
		return json.append(" } }").toString();
	}

	private static Resource file(String packId, String json) {
		PackResources pack = (PackResources) Proxy.newProxyInstance(
				PackResources.class.getClassLoader(), new Class<?>[] { PackResources.class },
				(proxy, method, arguments) -> {
					if (method.getName().equals("packId")) return packId;
					throw new UnsupportedOperationException(method.getName());
				});
		return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@SuppressWarnings("unchecked")
	private static ResourceManager manager(Map<ResourceLocation, List<Resource>> files) {
		return (ResourceManager) Proxy.newProxyInstance(
				ResourceManager.class.getClassLoader(), new Class<?>[] { ResourceManager.class },
				(proxy, method, arguments) -> {
					if (!method.getName().equals("listResourceStacks")) {
						throw new UnsupportedOperationException(method.getName());
					}
					String prefix = arguments[0] + "/";
					Predicate<ResourceLocation> filter = (Predicate<ResourceLocation>) arguments[1];
					Map<ResourceLocation, List<Resource>> found = new LinkedHashMap<>();
					files.forEach((path, stack) -> {
						if (path.getPath().startsWith(prefix) && filter.test(path)) found.put(path, stack);
					});
					return found;
				});
	}

	private static List<LogEvent> captureLoaderLog() {
		List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());
		AbstractAppender appender = new AbstractAppender("animation-loader-capture", null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				events.add(event.toImmutable());
			}
		};
		appender.start();
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.getConfiguration().getRootLogger().addAppender(appender, Level.ALL, null);
		context.updateLoggers();
		Configurator.setLevel(JojoMod.class.getName(), Level.DEBUG);
		JojoMod.getLogger().error("animation loader capture probe");
		check(events.stream().anyMatch(event -> mentions(event, "animation loader capture probe")),
				"the test cannot see the loader's log, so it could not tell an error from silence");
		events.clear();
		return events;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
