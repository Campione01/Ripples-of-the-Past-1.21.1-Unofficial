package rotp.core.gametest;

import rotp.core.block.WoodenCoffinBlock;
import rotp.core.core.JojoMod;
import rotp.core.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoffinSleepDamageGameTests {
	private CoffinSleepDamageGameTests() {}

	// 1.16 onLivingHurtStart: a sleeper in a closed coffin takes no damage from any source (void and /kill too); an open lid lets it through
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void closedCoffinSleeperTakesNoDamage(GameTestHelper helper) {
		BlockPos headRel = new BlockPos(1, 2, 1);
		BlockPos footRel = headRel.relative(Direction.SOUTH);
		BlockState coffin = ModBlocks.WOODEN_COFFIN_OAK.values().iterator().next().get().defaultBlockState()
				.setValue(WoodenCoffinBlock.FACING, Direction.NORTH);
		BlockPos head = helper.absolutePos(headRel);
		Player sleeper = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			helper.setBlock(headRel, coffin.setValue(BedBlock.PART, BedPart.HEAD));
			helper.setBlock(footRel, coffin.setValue(BedBlock.PART, BedPart.FOOT));
			sleeper.moveTo(head.getX() + 0.5, head.getY() + 0.2, head.getZ() + 0.5, 0, 0);
			sleeper.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(sleeper), "Could not add the sleeper");

			// closed lid: ordinary damage is cancelled and the sleeper stays asleep
			sleeper.startSleeping(head);
			helper.assertTrue(sleeper.isSleeping() && lidClosed(helper, headRel) && lidClosed(helper, footRel),
					"Sleeping did not close the coffin lid");
			float before = sleeper.getHealth();
			helper.assertFalse(hurt(sleeper, helper.getLevel().damageSources().magic(), 4.0F),
					"Magic damage landed on a closed coffin sleeper");
			helper.assertTrue(sleeper.getHealth() == before && sleeper.isSleeping(),
					"Closed coffin sleeper lost health or woke up: health=" + sleeper.getHealth());

			// lid opened while asleep: the same damage lands and wakes the sleeper
			helper.setBlock(headRel, helper.getBlockState(headRel).setValue(WoodenCoffinBlock.CLOSED, false));
			helper.assertFalse(lidClosed(helper, headRel), "Could not open the coffin lid");
			helper.assertTrue(hurt(sleeper, helper.getLevel().damageSources().magic(), 4.0F),
					"Magic damage did not land on an open coffin sleeper");
			helper.assertTrue(sleeper.getHealth() == before - 4.0F && !sleeper.isSleeping(),
					"Open coffin sleeper: health=" + sleeper.getHealth() + ", sleeping=" + sleeper.isSleeping());

			// closed again: void and /kill damage are cancelled too (1.16 had no source exemption)
			sleeper.startSleeping(head);
			helper.assertTrue(sleeper.isSleeping() && lidClosed(helper, headRel), "Sleeping again did not close the lid");
			float beforeVoid = sleeper.getHealth();
			helper.assertFalse(hurt(sleeper, helper.getLevel().damageSources().fellOutOfWorld(), 2.0F),
					"Void damage landed on a closed coffin sleeper");
			helper.assertFalse(hurt(sleeper, helper.getLevel().damageSources().genericKill(), 2.0F),
					"Kill damage landed on a closed coffin sleeper");
			helper.assertTrue(sleeper.getHealth() == beforeVoid && sleeper.isSleeping(),
					"Void/kill damage on a closed coffin sleeper: health=" + sleeper.getHealth()
					+ ", sleeping=" + sleeper.isSleeping());
			helper.succeed();
		}
		finally {
			if (sleeper.isSleeping()) {
				sleeper.stopSleeping();
			}
			sleeper.discard();
			helper.setBlock(headRel, Blocks.AIR);
			helper.setBlock(footRel, Blocks.AIR);
		}
	}

	private static boolean hurt(Player target, DamageSource source, float amount) {
		target.invulnerableTime = 0;
		return target.hurt(source, amount);
	}

	private static boolean lidClosed(GameTestHelper helper, BlockPos rel) {
		BlockState state = helper.getBlockState(rel);
		return state.getBlock() instanceof WoodenCoffinBlock && state.getValue(WoodenCoffinBlock.CLOSED);
	}
}
