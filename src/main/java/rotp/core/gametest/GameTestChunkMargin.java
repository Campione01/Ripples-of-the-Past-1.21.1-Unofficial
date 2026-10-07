package rotp.core.gametest;

import java.util.Arrays;
import java.util.List;

import rotp.core.core.JojoMod;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The test cells are one block wide and the tests put their actors a few blocks around them. Vanilla loads only the
 * chunks under a test structure, so an actor past a chunk border was not ticked, or not even found, depending on
 * where the random test grid happened to start. The chunks around the structure are loaded and awaited as well.
 */
public final class GameTestChunkMargin {
	/** One chunk on every side of the test structure. */
	public static final int BLOCKS = 16;

	private GameTestChunkMargin() {}

	public static BoundingBox around(BoundingBox structureBounds) {
		return runsOwnTests() ? structureBounds.inflatedBy(BLOCKS) : structureBounds;
	}

	// Add-ons run their tests with this mod loaded: those runs keep the vanilla loading.
	// The property is read the way NeoForge's GameTestHooks reads it, where no namespace means every namespace.
	private static boolean runsOwnTests() {
		String enabled = System.getProperty("neoforge.enabledGameTestNamespaces");
		if (enabled == null) {
			return true;
		}
		List<String> namespaces = Arrays.stream(enabled.split(",")).filter(namespace -> !namespace.isBlank()).toList();
		return namespaces.isEmpty() || namespaces.contains(JojoMod.MOD_ID);
	}
}
