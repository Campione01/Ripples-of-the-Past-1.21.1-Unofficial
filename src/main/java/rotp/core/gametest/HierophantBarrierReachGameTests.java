package rotp.core.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantBarrierAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Hierophant Green barrier: the target is the crosshair block (the user's reach, or the Stand's view
 * while manually controlled), and it must lie within 10 blocks of the summoned Stand.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HierophantBarrierReachGameTests {
	private HierophantBarrierReachGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void barrierTargetUsesCrosshairReachAndStandRange(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.fromString("0f6d3c1e-5b8a-4e52-9a0d-7c2b1e4f9a61"), "HierophantBarrierReach"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
		StandPower power = null;
		StandEntity stand = null;
		Map<BlockPos, BlockState> original = new HashMap<>();
		try {
			helper.assertTrue(standType != null, "Missing Stand type hierophant_green");
			Vec3 userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			// everyone looks straight up, so the rays stay inside this test's column
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, -90.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the barrier test player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Hierophant Green: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Hierophant Green");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Hierophant Green entity is missing");
			stand.moveTo(userPos.x + 1.0D, userPos.y, userPos.z, 0.0F, -90.0F);
			double reach = user.blockInteractionRange();
			helper.assertTrue(reach < 6.0D, "Unexpected survival block reach " + reach);

			Vec3 userEye = user.getEyePosition();
			BlockPos near = BlockPos.containing(userEye.add(0.0D, 3.0D, 0.0D));
			BlockPos far = BlockPos.containing(userEye.add(0.0D, 7.0D, 0.0D));

			// a block 7 up is past the user's reach but inside the old 10-block ray
			set(helper, original, far, Blocks.STONE.defaultBlockState());
			ActionTarget target = HierophantBarrierAbility.getCurrentBlockTarget(user, stand, helper.getLevel());
			helper.assertTrue(target.getType() != TargetType.BLOCK,
					"Barrier found a block beyond the user's reach: " + target);

			// a block within reach is the crosshair block
			set(helper, original, near, Blocks.STONE.defaultBlockState());
			target = HierophantBarrierAbility.getCurrentBlockTarget(user, stand, helper.getLevel());
			helper.assertTrue(target.getType() == TargetType.BLOCK && near.equals(target.getBlockPos()),
					"Barrier missed the block in the user's reach: " + target);

			// the 10-block limit is measured from the Stand, not from the user
			stand.moveTo(userPos.x + 12.0D, userPos.y, userPos.z, 0.0F, -90.0F);
			LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(near, Direction.DOWN));
			target = HierophantBarrierAbility.getCurrentBlockTarget(user, stand, helper.getLevel());
			helper.assertTrue(target.getType() != TargetType.BLOCK,
					"Barrier accepted a block more than 10 blocks from the Stand: " + target);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);

			// under manual control the Stand's view is the crosshair
			BlockPos standNear = BlockPos.containing(stand.getEyePosition().add(0.0D, 3.0D, 0.0D));
			set(helper, original, standNear, Blocks.STONE.defaultBlockState());
			stand.setManuallyControlled(true);
			target = HierophantBarrierAbility.getCurrentBlockTarget(user, stand, helper.getLevel());
			helper.assertTrue(target.getType() == TargetType.BLOCK && standNear.equals(target.getBlockPos()),
					"Manually controlled barrier did not aim from the Stand: " + target);
			helper.succeed();
		}
		finally {
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (stand != null) {
				stand.setManuallyControlled(false);
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			for (Map.Entry<BlockPos, BlockState> block : original.entrySet()) {
				helper.getLevel().setBlockAndUpdate(block.getKey(), block.getValue());
			}
			user.discard();
		}
	}

	private static void set(GameTestHelper helper, Map<BlockPos, BlockState> original, BlockPos pos, BlockState state) {
		original.putIfAbsent(pos, helper.getLevel().getBlockState(pos));
		helper.getLevel().setBlockAndUpdate(pos, state);
	}
}
