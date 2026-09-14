package rotp.core.item;

import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RoadRollerItem extends Item {
	public RoadRollerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack handStack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			RoadRollerEntity roadRoller = new RoadRollerEntity(level);
			roadRoller.copyPosition(player);
			level.addFreshEntity(roadRoller);
			player.startRiding(roadRoller);
			roadRoller.setOwner(player);
			StandPower standPower = StandPower.get(player);
			if (standPower != null && standPower.getPowerType() == ModStands.THE_WORLD.get()) {
				JojoModUtil.sayVoiceLine(player, ModSoundEvents.DIO_ROAD_ROLLER);
			}
			if (!player.getAbilities().instabuild) {
				handStack.shrink(1);
			}
		}
		return InteractionResultHolder.consume(handStack);
	}
}
