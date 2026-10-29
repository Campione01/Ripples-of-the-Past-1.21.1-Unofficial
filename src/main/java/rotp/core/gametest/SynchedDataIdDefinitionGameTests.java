package rotp.core.gametest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands.silverchariot.SilverChariotBarrageAbility.SilverChariotBarrage;
import rotp.core.impl.stands.theworld.TheWorldBarrageAbility.TheWorldBarrage;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.ClassTreeIdRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * SynchedEntityData.defineId gives a class the id after the last one its class tree has defined so far, and NeoForge
 * warns of a call made from another class than the one it names ("defineId called for: ... from ..."). An id defined
 * as its own class initialises comes after its superclasses' ids and before its subclasses', on the server and on
 * every client alike. One defined from a holder class that loads with its first use takes the number of whatever its
 * class tree defined before that, which the order of class loading decides on each side: the barrage's clash data id
 * was such a one, defined at the first barrage of a session.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SynchedDataIdDefinitionGameTests {
	private SynchedDataIdDefinitionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theBarrageDefinesItsOneSyncedDataIdAsItsOwnClassLoads(GameTestHelper helper) {
		List<Field> own = accessorFields(StandEntityBarrage.class);
		helper.assertTrue(own.size() == 1 && Modifier.isStatic(own.get(0).getModifiers()) && Modifier.isFinal(own.get(0).getModifiers()),
				"the barrage does not define its clash data id as a static field of its own class, fixed as the class loads: " + own);
		for (Class<?> member : StandEntityBarrage.class.getDeclaredClasses()) {
			helper.assertTrue(accessorFields(member).isEmpty(), "a class inside the barrage defines a data id, which loads with"
					+ " its first use: " + member.getName() + " " + accessorFields(member));
		}
		for (Class<?> subclass : new Class<?>[] { TheWorldBarrage.class, SilverChariotBarrage.class }) {
			helper.assertTrue(accessorFields(subclass).isEmpty(), subclass.getSimpleName() + " defines data ids of its own: "
					+ accessorFields(subclass));
		}

		EntityDataAccessor<?> clashHit = (EntityDataAccessor<?>) read(own.get(0));
		ClassTreeIdRegistry ids;
		try {
			ids = (ClassTreeIdRegistry) read(SynchedEntityData.class.getDeclaredField("ID_REGISTRY"));
		}
		catch (NoSuchFieldException e) {
			throw new IllegalStateException("SynchedEntityData.ID_REGISTRY is not reachable", e);
		}
		int first = ids.getLastIdFor(EntityActionInstance.class) + 1;
		helper.assertTrue(clashHit.id() == first && ids.getLastIdFor(StandEntityBarrage.class) == first
				&& ids.getLastIdFor(TheWorldBarrage.class) == first && ids.getLastIdFor(SilverChariotBarrage.class) == first,
				"the barrage's class tree holds other data ids than its clash data id, the first after EntityActionInstance's ("
						+ first + "): the clash data id " + clashHit.id() + ", the last id of the barrage "
						+ ids.getLastIdFor(StandEntityBarrage.class) + ", of The World's " + ids.getLastIdFor(TheWorldBarrage.class)
						+ ", of Silver Chariot's " + ids.getLastIdFor(SilverChariotBarrage.class));

		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing Stand type star_platinum");
		Ability ability = type.getBaseMoveset().getAbility("barrage");
		helper.assertTrue(ability instanceof EntityActionType, "Star Platinum's barrage is not an entity action: " + ability);
		StandEntityBarrage barrage = new StandEntityBarrage((EntityActionType) ability);
		SynchedDataBuilder builder = new SynchedDataBuilder(barrage);
		barrage.defineSynchedData(builder);
		SynchedEntityData.DataItem<?>[] items = builder.itemsById();
		helper.assertTrue(items.length == 1 && items[0].getAccessor() == clashHit && Integer.valueOf(-1).equals(items[0].getValue()),
				"a barrage's synced data is not its one clash data id, -1 at first: " + items.length + " items");
		helper.succeed();
	}

	/*
	 * Every data id the core defines is the initialiser of a static field of the class it names: NeoForge has nothing to
	 * warn of, and each id is fixed as that class loads.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyCoreSyncedDataIdIsAStaticFieldOfTheClassItNames(GameTestHelper helper) {
		Path root = coreSources(helper);
		List<String> wrong = new ArrayList<>();
		int definitions = 0;
		boolean barrageRead = false;
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not list " + root, e);
		}
		for (Path file : files) {
			String code;
			try {
				code = blanked(Files.readString(file));
			}
			catch (IOException e) {
				throw new IllegalStateException("Could not read " + file, e);
			}
			String where = root.relativize(file).toString().replace('\\', '/');
			List<int[]> bodies = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (Matcher declaration = DECLARATION.matcher(code); declaration.find();) {
				int open = code.indexOf('{', declaration.end());
				if (open >= 0) {
					bodies.add(new int[] { open, closingBrace(code, open) });
					names.add(declaration.group(1));
				}
			}
			int calls = 0;
			for (Matcher call = ANY_CALL.matcher(code); call.find();) {
				calls++;
			}
			int read = 0;
			for (Matcher definition = DEFINITION.matcher(code); definition.find(); read++) {
				int at = definition.start();
				int owner = -1;
				for (int i = 0; i < bodies.size(); i++) {
					int[] body = bodies.get(i);
					if (body[0] < at && at < body[1] && (owner < 0 || body[0] > bodies.get(owner)[0])) {
						owner = i;
					}
				}
				String named = definition.group(1).substring(definition.group(1).lastIndexOf('.') + 1);
				int depth = 0;
				for (int i = owner >= 0 ? bodies.get(owner)[0] + 1 : at; i < at; i++) {
					char c = code.charAt(i);
					if (c == '{') depth++;
					else if (c == '}') depth--;
				}
				int start = Math.max(code.lastIndexOf(';', at), Math.max(code.lastIndexOf('{', at), code.lastIndexOf('}', at))) + 1;
				String declared = code.substring(start, at).trim().replaceAll("\\s+", " ");
				String ownerName = owner >= 0 ? names.get(owner) : null;
				if (!named.equals(ownerName) || depth != 0 || !STATIC.matcher(declared).find()) {
					wrong.add(where + ": the id of " + named + " is defined in " + (ownerName != null ? ownerName : "no class")
							+ (depth != 0 ? " inside a block" : "") + " by '" + declared + "'");
				}
				if (where.endsWith("impl/stands/_entitybase/StandEntityBarrageAbility.java") && named.equals("StandEntityBarrage")) {
					barrageRead = true;
				}
			}
			definitions += read;
			if (calls != read) {
				wrong.add(where + ": " + (calls - read) + " defineId calls not of the form SynchedEntityData.defineId(Owner.class, ...)");
			}
		}
		helper.assertTrue(definitions > 0 && barrageRead, "Fixture: the barrage's data id definition was not read under " + root
				+ " (" + definitions + " definitions)");
		helper.assertTrue(wrong.isEmpty(), "data ids defined elsewhere than in a static field of the class they name, so not fixed"
				+ " as that class loads (" + wrong.size() + " of " + definitions + "): " + wrong);
		helper.succeed();
	}

	private static final Pattern DECLARATION = Pattern.compile("(?<![\\w.$])(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
	private static final Pattern DEFINITION = Pattern.compile("SynchedEntityData\\s*\\.\\s*defineId\\s*\\(\\s*([\\w$.]+?)\\s*\\.\\s*class\\b");
	private static final Pattern ANY_CALL = Pattern.compile("(?<![\\w$])defineId\\s*\\(");
	private static final Pattern STATIC = Pattern.compile("(?<![\\w$])static(?![\\w$])");

	private static List<Field> accessorFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			if (field.getType() == EntityDataAccessor.class) {
				fields.add(field);
			}
		}
		return fields;
	}

	private static Object read(Field staticField) {
		try {
			staticField.setAccessible(true);
			return staticField.get(null);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(staticField + " is not reachable", e);
		}
	}

	private static Path coreSources(GameTestHelper helper) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path sources = at.resolve("src/main/java/rotp/core");
			if (Files.isDirectory(sources)) {
				return sources;
			}
		}
		helper.fail("Fixture: src/main/java/rotp/core not found above " + Path.of("").toAbsolutePath());
		return null;
	}

	// the source with its comments and its string and char literals blanked, the line breaks kept
	private static String blanked(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int n = source.length();
		for (int i = 0; i < n;) {
			int end;
			if (source.startsWith("//", i)) {
				end = source.indexOf('\n', i);
				end = end < 0 ? n : end;
			}
			else if (source.startsWith("/*", i)) {
				end = source.indexOf("*/", i + 2);
				end = end < 0 ? n : end + 2;
			}
			else if (source.startsWith("\"\"\"", i)) {
				end = source.indexOf("\"\"\"", i + 3);
				end = end < 0 ? n : end + 3;
			}
			else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
				char quote = source.charAt(i);
				end = i + 1;
				while (end < n && source.charAt(end) != quote) {
					end += source.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(end + 1, n);
			}
			else {
				out.append(source.charAt(i++));
				continue;
			}
			for (; i < end; i++) {
				out.append(source.charAt(i) == '\n' ? '\n' : ' ');
			}
		}
		return out.toString();
	}

	private static int closingBrace(String code, int open) {
		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) {
				return i;
			}
		}
		return code.length();
	}
}
