package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.customobjects.entity_projectile.BladeHatEntity;
import com.github.standobyte.jojo.init.ModArmorMaterials;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracker;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.subsystems.itemtracking.KnownItemState;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public class BladeHatItem extends ArmorItem implements ProjectileItem {
	public BladeHatItem(Properties properties) {
		super(ModArmorMaterials.BLADE_HAT, ArmorItem.Type.HELMET, properties);
		DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior() {
			@Override
			protected ItemStack execute(BlockSource blockSource, ItemStack stack) {
				return ArmorItem.dispenseArmor(blockSource, stack) ? stack : shootProjectile(blockSource, stack);
			}

			private ItemStack shootProjectile(BlockSource blockSource, ItemStack stack) {
				Level level = blockSource.level();
				Position position = DispenserBlock.getDispensePosition(blockSource);
				Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
				BladeHatEntity hat = new BladeHatEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1));
				hat.pickup = AbstractArrow.Pickup.ALLOWED;
				hat.shoot(direction.getStepX(), direction.getStepY() + 0.1D, direction.getStepZ(), 1.1F, 6.0F);
				level.addFreshEntity(hat);
				stack.shrink(1);
				return stack;
			}
		});
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			return super.use(level, player, hand);
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			ItemStack projectileStack = stack.copyWithCount(1);
			BladeHatEntity hat = new BladeHatEntity(level, player, projectileStack);
			hat.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.75F, 0.5F);

			ItemTracker tracker = ItemTracking.getItemTracker(projectileStack, level);
			if (tracker != null) {
				tracker.setAtEntity(projectileStack, hat.getId(), level, KnownItemState.ENTITY_IS_ITEM, null);
			}

			level.addFreshEntity(hat);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSoundEvents.BLADE_HAT_THROW.get(),
					SoundSource.PLAYERS, 1.0F, 0.75F + player.getRandom().nextFloat() * 0.5F);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		BladeHatEntity hat = new BladeHatEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1));
		hat.pickup = AbstractArrow.Pickup.ALLOWED;
		return hat;
	}

	@Override
	public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.supportsEnchantment(stack, enchantment) || enchantment.is(Enchantments.SHARPNESS);
	}

	@Override
	public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.isPrimaryItemFor(stack, enchantment) || enchantment.is(Enchantments.SHARPNESS);
	}
}
