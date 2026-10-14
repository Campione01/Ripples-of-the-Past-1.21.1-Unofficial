package rotp.core.gametest;

import java.util.List;
import java.util.Optional;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility.TerrainRestoration;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CrazyDiamondRestoreTerrain had no stamina condition to start or to keep holding; its own stamina rule was the
 * number of blocks restored each tick, min(blocks per tick, (int) (2 * stamina)), paid at 2 a block. On an empty bar
 * the hold starts and goes on but restores nothing, and blocks come back once the bar can pay for them.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondEmptyBarGameTests {
	private CrazyDiamondEmptyBarGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void restoreTerrainHoldsOnAnEmptyBarButRestoresNothing(GameTestHelper helper) {
		EmptyStaminaBarScene scene = new EmptyStaminaBarScene(helper);
		BlockPos broken = helper.absolutePos(new BlockPos(2, 13, 4));
		Runnable close = () -> {
			CrazyDRestoreTerrainAbility.forgetBrokenBlocks(scene.level, List.of(broken));
			scene.level.removeBlock(broken, false);
			scene.close();
		};
		try {
			// the user looks along +Z at the broken block, two blocks ahead at eye height
			scene.start("crazy_diamond", 0, 2, "restore_terrain");
			helper.assertTrue(scene.level.getBlockState(broken).isAir(), "Fixture: the broken block's place is not empty");
			CrazyDRestoreTerrainAbility.rememberBrokenBlock(scene.level, broken, Blocks.DIRT.defaultBlockState(),
					Optional.empty(), List.of(new ItemStack(Items.DIRT)));
			scene.user.getInventory().add(new ItemStack(Items.DIRT, 4));
			helper.assertTrue(scene.pressOnBar("restore_terrain", InputMethod.HOLD, 0),
					"1.16 had no stamina condition: Restore Terrain must be admitted on an empty bar");
		}
		catch (RuntimeException | Error failure) {
			close.run();
			throw failure;
		}
		helper.runAfterDelay(15, () -> {
			try {
				EntityActionInstance action = scene.stand.getCurStandAction();
				helper.assertTrue(action instanceof TerrainRestoration && action.getPhase() == ActionPhase.PERFORM,
						"An empty bar must not end the held Restore Terrain, but its action is " + action
								+ (action != null ? " in " + action.getPhase() : ""));
				helper.assertTrue(scene.level.getBlockState(broken).isAir(),
						"1.16 restored (int) (2 * stamina) blocks a tick: nothing on an empty bar, but got "
								+ scene.level.getBlockState(broken));
				helper.assertTrue(scene.power.getStamina() == 0, "The bar must stay empty: " + scene.power.getStamina());
				scene.power.setStamina(scene.power.getMaxStamina());
			}
			catch (RuntimeException | Error failure) {
				close.run();
				throw failure;
			}
			helper.runAfterDelay(10, () -> {
				try {
					helper.assertTrue(scene.level.getBlockState(broken).is(Blocks.DIRT),
							"Once the bar can pay, the same hold must restore the block: " + scene.level.getBlockState(broken));
					helper.assertTrue(scene.power.getStamina() <= scene.power.getMaxStamina() - 2,
							"Restoring the block costs 2 stamina: " + scene.power.getStamina());
				}
				finally {
					close.run();
				}
				helper.succeed();
			});
		});
	}
}
