package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.customobjects.RoadRollerEntity;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.util.functions.JojoModUtil;

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
