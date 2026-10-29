package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.AfterimageEntity;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUtil.StandAndUserEntity;
import rotp.core.util.mc.entitysubtype.EntitySubtype;
import rotp.core.util.mc.entitysubtype.EntityTypeToInstance;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core's static holders that kept a level after it ended, on the server side (the integrated server runs in the
 * client's JVM, so its levels stayed in memory after "Save and Quit" too). Each level's unload is called on its handler
 * here, not posted, so that no other listener lets the running test level go.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldLeftReleaseGameTests {
	// own batch: the unloads called here let go of what the level's other tests cached
	static final String BATCH = "world_left_release";

	private WorldLeftReleaseGameTests() {}

	// one test, its rows reported apart: with a test for each, the shifted test grid failed Magician's Red's kick
	// slide test (MagiciansRedKickSlide116GameTests) in about half the runs
	@GameTest(template = "empty", batch = WorldLeftReleaseGameTests.BATCH, timeoutTicks = 20)
	public static void eachServerSideHolderLetsGoOfALevelAsItUnloads(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		row(wrong, "SUBTYPE-CACHE", () -> anEntitySubtypesCachedInstanceStaysWhileItsLevelIsOpenAndGoesAsItUnloads(helper));
		row(wrong, "AFTERIMAGES", () -> anAfterimagesOriginIsLetGoAsItsLevelUnloads(helper));
		row(wrong, "STAND-AND-USER", () -> aStandAndUserLookupKeepsNoEntityForTheNextOne(helper));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static void row(List<String> wrong, String name, Runnable row) {
		try {
			row.run();
		}
		catch (GameTestAssertException e) {
			wrong.add(name + " " + e.getMessage());
		}
	}

	private static void anEntitySubtypesCachedInstanceStaysWhileItsLevelIsOpenAndGoesAsItUnloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerLevel other = otherLevel(helper);
		EntitySubtype<Cow> cow = EntitySubtype.base(EntityType.COW);
		EntitySubtype<Pig> pig = EntitySubtype.base(EntityType.PIG);
		Cow cached = EntityTypeToInstance.getEntityInstance(cow, level);
		Pig otherCached = EntityTypeToInstance.getEntityInstance(pig, other);
		helper.assertTrue(cached != null && cached.level() == level && otherCached != null && otherCached.level() == other,
				"Fixture: no cow instance of the test level or no pig instance of " + other.dimension().location());
		helper.assertTrue(EntityTypeToInstance.getEntityInstance(cow, level) == cached,
				"EntityTypeToInstance no longer keeps a subtype's instance while its level is open: it made a new cow");

		EntityTypeToInstance.onLevelUnload(new LevelEvent.Unload(level));
		Cow after = EntityTypeToInstance.getEntityInstance(cow, level);
		helper.assertTrue(after != cached, "EntityTypeToInstance still holds the cow of the level that unloaded, and with it"
				+ " that level: after \"Save and Quit\" the client's world (and the integrated server's) stayed in memory");
		helper.assertTrue(after != null && after.level() == level, "EntityTypeToInstance gives no cow of the level asked for: " + after);
		helper.assertTrue(EntityTypeToInstance.getEntityInstance(pig, other) == otherCached, "The unload of the test level let"
				+ " go of the pig instance of " + other.dimension().location() + ", a level still open");
	}

	private static void anAfterimagesOriginIsLetGoAsItsLevelUnloads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Object, Object> afterimages = afterimages(helper);
		Zombie origin = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		Zombie elsewhere = new Zombie(EntityType.ZOMBIE, otherLevel(helper));
		List<?> added = List.of();
		try {
			AfterimageEntity.addAfterimages(origin, 1, 20);
			afterimages.put(elsewhere, new ArrayList<>());
			helper.assertTrue(afterimages.get(origin) instanceof List<?> list && !list.isEmpty(),
					"Fixture: AfterimageEntity.addAfterimages kept no afterimage for the zombie");
			added = List.copyOf((List<?>) afterimages.get(origin));

			AfterimageEntity.LevelEvents.onLevelUnload(new LevelEvent.Unload(level));
			helper.assertTrue(!afterimages.containsKey(origin), "AfterimageEntity still holds the afterimages of an origin in a"
					+ " level that unloaded: its weak keys never let go, as each afterimage holds its origin, so the server's"
					+ " level stayed in memory after it stopped");
			helper.assertTrue(afterimages.containsKey(elsewhere), "The unload of the test level let go of an origin in "
					+ elsewhere.level().dimension().location() + ", a level still open");
		}
		finally {
			afterimages.remove(origin);
			afterimages.remove(elsewhere);
			added.forEach(afterimage -> ((AfterimageEntity) afterimage).discard());
			origin.discard();
		}
	}

	private static void aStandAndUserLookupKeepsNoEntityForTheNextOne(GameTestHelper helper) {
		// kept out of the level, where they would stand in a neighbouring test's way
		Cow cow = EntityType.COW.create(helper.getLevel());
		Pig pig = EntityType.PIG.create(helper.getLevel());
		helper.assertTrue(cow != null && pig != null, "Fixture: no cow or pig");
		StandAndUserEntity first = StandUtil.getStandAndUser(cow);
		StandAndUserEntity second = StandUtil.getStandAndUser(pig);
		helper.assertTrue(first != second && first.standUser == cow && second.standUser == pig, "StandUtil.getStandAndUser"
				+ " answers through one shared holder, which keeps the last entities asked about (Crazy Diamond's heal asks"
				+ " on the client) and with them their level after the client leaves the world");
		for (Class<?> type : new Class<?>[] { StandUtil.class, StandAndUserEntity.class }) {
			for (Field field : type.getDeclaredFields()) {
				helper.assertTrue(!Modifier.isStatic(field.getModifiers()) || field.getType() != StandAndUserEntity.class,
						type.getSimpleName() + "." + field.getName() + " is a static StandAndUserEntity, which keeps its entities");
			}
		}
	}

	private static ServerLevel otherLevel(GameTestHelper helper) {
		for (ServerLevel level : helper.getLevel().getServer().getAllLevels()) {
			if (level != helper.getLevel()) {
				return level;
			}
		}
		throw new IllegalStateException("Fixture: the test server has no level but the test level");
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> afterimages(GameTestHelper helper) {
		try {
			Field field = AfterimageEntity.class.getDeclaredField("AFTERIMAGES");
			field.setAccessible(true);
			return (Map<Object, Object>) field.get(null);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: AfterimageEntity.AFTERIMAGES cannot be read", e);
		}
	}
}
