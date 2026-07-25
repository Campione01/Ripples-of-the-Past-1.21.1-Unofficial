package com.github.standobyte.jojo.item;

import java.util.List;

import com.github.standobyte.jojo.customobjects.entity_projectile.MolotovEntity;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;

public class MolotovItem extends Item implements ProjectileItem {
	public MolotovItem(Properties properties) {
		super(properties);
		DispenserBlock.registerProjectileBehavior(this);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);
		boolean hasFire = useFire(player, level);
		if (!hasFire) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("jojo.message.action_condition.molotov_fire"), true);
			}
			return InteractionResultHolder.fail(heldItem);
		}

		if (!level.isClientSide()) {
			ItemStack projectileStack = heldItem.copy();
			projectileStack.setCount(1);
			MolotovEntity molotov = new MolotovEntity(level, player, projectileStack);
			molotov.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.75F, 1.0F);
			level.addFreshEntity(molotov);
			if (!player.getAbilities().instabuild) {
				heldItem.shrink(1);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		if (!player.isSilent()) {
			player.playSound(ModSoundEvents.MOLOTOV_THROW.get(), 0.5F,
					0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
		}

		return InteractionResultHolder.sidedSuccess(heldItem, level.isClientSide());
	}

	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		ItemStack projectileStack = stack.copy();
		projectileStack.setCount(1);
		return new MolotovEntity(level, pos.x(), pos.y(), pos.z(), projectileStack);
	}

	public static boolean useFire(Player player, Level level) {
		boolean hasFire = player.isOnFire() || StandPower.getOptional(player)
				.map(stand -> stand.getPowerType() == ModStands.MAGICIANS_RED.get())
				.orElse(false);
		ItemStack flintAndSteel = ItemStack.EMPTY;
		ItemStack fireCharge = ItemStack.EMPTY;
		if (!hasFire) {
			BlockPos center = player.blockPosition().above();
			for (int x = -2; x <= 2 && !hasFire; x++) {
				for (int y = -2; y <= 2 && !hasFire; y++) {
					for (int z = -2; z <= 2 && !hasFire; z++) {
						BlockPos pos = center.offset(x, y, z);
						BlockState blockState = level.getBlockState(pos);
						FluidState fluidState = level.getFluidState(pos);
						if (isFireSource(blockState, fluidState)) {
							hasFire = true;
						}
					}
				}
			}
		}
		if (!hasFire) {
			flintAndSteel = findInInventory(player, stack -> stack.getItem() instanceof FlintAndSteelItem);
			if (!flintAndSteel.isEmpty()) {
				hasFire = true;
				if (!player.isSilent()) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.FLINTANDSTEEL_USE, player.getSoundSource(),
							1.0F, player.getRandom().nextFloat() * 0.4F + 0.8F);
				}
			}
		}
		if (!hasFire) {
			fireCharge = findInInventory(player, stack -> stack.getItem() instanceof FireChargeItem);
			if (!fireCharge.isEmpty()) {
				hasFire = true;
				if (!player.isSilent()) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.FIRECHARGE_USE, player.getSoundSource(),
							1.0F, (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F + 1.0F);
				}
			}
		}

		if (hasFire) {
			if (!flintAndSteel.isEmpty()) {
				flintAndSteel.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
			}
			else if (!fireCharge.isEmpty()) {
				fireCharge.shrink(1);
			}
		}
		return hasFire;
	}

	private static boolean isFireSource(BlockState blockState, FluidState fluidState) {
		return blockState.getBlock() instanceof BaseFireBlock
				|| ((blockState.getBlock() instanceof AbstractFurnaceBlock
						|| blockState.getBlock() instanceof CampfireBlock
						|| blockState.getBlock() instanceof TorchBlock && blockState.getBlock() != Blocks.REDSTONE_TORCH)
						&& (!blockState.hasProperty(BlockStateProperties.LIT) || blockState.getValue(BlockStateProperties.LIT)))
				|| fluidState.is(FluidTags.LAVA);
	}

	private static ItemStack findInInventory(Player player, java.util.function.Predicate<ItemStack> predicate) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && predicate.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.jojo_ripples.molotov.reference_quote"));
	}
}
