package rotp.core.client.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 1.16 ModSplashes: JoJo lines that may replace the title-screen splash.
 * No client classes here, so the rules can be checked on a test server.
 */
public class ModSplashes extends SimplePreparableReloadListener<List<String>> {
	// not texts/splashes.txt: NeoForge merges that path from every namespace into the vanilla pool unfiltered
	public static final ResourceLocation LOCATION = JojoMod.resLoc("texts/jojo_splashes.txt");
	public static final String HALLOWEEN_SPLASH = "\u30b4 \u30b4 \u30b4 \u30b4 \u30b4 \u30b4 \u30b4 \u30b4 \u30b4 \u30b4";
	private static final Random RANDOM = new Random();
	private final List<String> splashes = new ArrayList<>();

	@Override
	protected List<String> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		Optional<Resource> resource = resourceManager.getResource(LOCATION);
		if (resource.isEmpty()) {
			return Collections.emptyList();
		}
		try (BufferedReader reader = resource.get().openAsReader()) {
			return parse(reader.lines());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	@Override
	protected void apply(List<String> splashes, ResourceManager resourceManager, ProfilerFiller profiler) {
		this.splashes.clear();
		this.splashes.addAll(splashes);
	}

	@Nullable
	public String overrideSplash(String userName) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		return pick(splashes, calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), RANDOM, userName);
	}

	// trimmed lines, blanks and "//" comments dropped
	public static List<String> parse(Stream<String> lines) {
		return lines
				.map(String::trim)
				.filter(line -> line.length() > 0 && !line.startsWith("//"))
				.collect(Collectors.toList());
	}

	// month is a Calendar month; null keeps the vanilla splash
	@Nullable
	public static String pick(List<String> splashes, int month, int day, Random random, String userName) {
		if (month == Calendar.DECEMBER && day == 24 || month == Calendar.JANUARY && day == 1) {
			return null;
		}
		if (month == Calendar.OCTOBER && day == 31 && random.nextInt(50) == 0) {
			return HALLOWEEN_SPLASH;
		}
		if (!splashes.isEmpty() && random.nextInt(420 + splashes.size()) < splashes.size()) {
			String splash = splashes.get(random.nextInt(splashes.size()));
			return splash.replace("@p", userName);
		}
		return null;
	}
}
