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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core's static holders that kept a level after it ended, on the server side (the integrated server runs in the
 * client's JVM, so its levels stayed in memory after "Save and Quit" too). The level that unloads here is the End,
 * which no other test uses, with the fixture's entries there; the test level keeps its own. Its unload is called on
 * each handler, not posted, so that no other listener lets the End go.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldLeftReleaseGameTests {
	// own batch: the subtype cache holds one instance per subtype for all levels, so the End's pig asked for here
	// takes the place of the one a test beside it may be using
	static final String BATCH = "world_left_release";

	private WorldLeftReleaseGameTests() {}

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
		ServerLevel end = unloadingLevel(helper);
		EntitySubtype<Cow> cow = EntitySubtype.base(EntityType.COW);
		EntitySubtype<Pig> pig = EntitySubtype.base(EntityType.PIG);
		Pig cached = EntityTypeToInstance.getEntityInstance(pig, end);
		Cow kept = EntityTypeToInstance.getEntityInstance(cow, level);
		helper.assertTrue(cached != null && cached.level() == end && kept != null && kept.level() == level,
				"Fixture: no pig instance of " + end.dimension().location() + " or no cow instance of the test level");
		helper.assertTrue(EntityTypeToInstance.getEntityInstance(pig, end) == cached,
				"EntityTypeToInstance no longer keeps a subtype's instance while its level is open: it made a new pig");

		EntityTypeToInstance.onLevelUnload(new LevelEvent.Unload(end));
		Pig after = EntityTypeToInstance.getEntityInstance(pig, end);
		helper.assertTrue(after != cached, "EntityTypeToInstance still holds the pig of the level that unloaded, and with it"
				+ " that level: after \"Save and Quit\" the client's world (and the integrated server's) stayed in memory");
		helper.assertTrue(after != null && after.level() == end, "EntityTypeToInstance gives no pig of the level asked for: " + after);
		helper.assertTrue(EntityTypeToInstance.getEntityInstance(cow, level) == kept, "The unload of "
				+ end.dimension().location() + " let go of the cow instance of the test level, a level still open");
	}

	private static void anAfterimagesOriginIsLetGoAsItsLevelUnloads(GameTestHelper helper) {
		ServerLevel end = unloadingLevel(helper);
		Map<Object, Object> afterimages = afterimages(helper);
		Zombie origin = new Zombie(EntityType.ZOMBIE, end);
		Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
		origin.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
		Zombie kept = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		List<Object> added = new ArrayList<>();
		try {
			AfterimageEntity.addAfterimages(origin, 1, 20);
			AfterimageEntity.addAfterimages(kept, 1, 20);
			helper.assertTrue(afterimages.get(origin) instanceof List<?> list && !list.isEmpty()
					&& afterimages.get(kept) instanceof List<?> keptList && !keptList.isEmpty(),
					"Fixture: AfterimageEntity.addAfterimages kept no afterimage for the zombie of " + end.dimension().location()
							+ " or for the zombie of the test level");
			added.addAll((List<?>) afterimages.get(origin));
			List<?> keptAfterimages = List.copyOf((List<?>) afterimages.get(kept));
			added.addAll(keptAfterimages);

			AfterimageEntity.LevelEvents.onLevelUnload(new LevelEvent.Unload(end));
			helper.assertTrue(!afterimages.containsKey(origin), "AfterimageEntity still holds the afterimages of an origin in a"
					+ " level that unloaded: its weak keys never let go, as each afterimage holds its origin, so the server's"
					+ " level stayed in memory after it stopped");
			helper.assertTrue(keptAfterimages.equals(afterimages.get(kept)), "The unload of " + end.dimension().location()
					+ " let go of the afterimages of a zombie in the test level, a level still open: " + afterimages.get(kept));
		}
		finally {
			afterimages.remove(origin);
			afterimages.remove(kept);
			added.forEach(afterimage -> ((AfterimageEntity) afterimage).discard());
			origin.discard();
			kept.discard();
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

	private static ServerLevel unloadingLevel(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		if (end == null || end == helper.getLevel()) {
			throw new IllegalStateException("Fixture: the test server has no End beside the test level");
		}
		return end;
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
