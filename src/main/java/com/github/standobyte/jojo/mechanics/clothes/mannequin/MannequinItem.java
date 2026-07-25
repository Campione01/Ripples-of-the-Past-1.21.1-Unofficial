package com.github.standobyte.jojo.mechanics.clothes.mannequin;

import java.util.function.Consumer;

import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MannequinItem extends Item {
	private final boolean slim;

	public MannequinItem(Properties pProperties, boolean slim) {
		super(pProperties);
		this.slim = slim;

		DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior() {
			
			@Override
			public ItemStack execute(BlockSource pSource, ItemStack pStack) {
				Direction direction = pSource.state().getValue(DispenserBlock.FACING);
				BlockPos blockPos = pSource.pos().relative(direction);
				ServerLevel world = pSource.level();
				
				Consumer<MannequinEntity> consumer = EntityType.appendDefaultStackConfig(entity -> {
					entity.setYRot(direction.toYRot());
					entity.setSlim(slim);
				}, world, pStack, null);
				MannequinEntity armorstand = ModEntityTypes.MANNEQUIN.get().spawn(world, consumer, blockPos, MobSpawnType.DISPENSER, false, false);
				if (armorstand != null) {
					pStack.shrink(1);
				}
				return pStack;
			}
		});
	}
	
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Direction face = context.getClickedFace();
		if (face == Direction.DOWN) {
			return InteractionResult.FAIL;
		} else {
			Level world = context.getLevel();
			BlockPlaceContext blockItemUseCtx = new BlockPlaceContext(context);
			BlockPos clickedPos = blockItemUseCtx.getClickedPos();
			ItemStack itemStack = context.getItemInHand();
			Vec3 pos = Vec3.atBottomCenterOf(clickedPos);
			AABB aabb = ModEntityTypes.MANNEQUIN.get().getDimensions().makeBoundingBox(pos.x(), pos.y(), pos.z());
			if (world.noCollision(null, aabb) && world.getEntities(null, aabb).isEmpty()) {
				if (world instanceof ServerLevel serverWorld) {
					Consumer<MannequinEntity> consumer = EntityType.appendDefaultStackConfig(entity -> {
						entity.setSlim(slim);
					}, serverWorld, itemStack, context.getPlayer());
					MannequinEntity armorstand = ModEntityTypes.MANNEQUIN.get().create(serverWorld, consumer, clickedPos, MobSpawnType.SPAWN_EGG/*SPAWN_ITEM_USE*/, true, true);
					if (armorstand == null) {
						return InteractionResult.FAIL;
					}

					float f = (float)Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
					armorstand.moveTo(armorstand.getX(), armorstand.getY(), armorstand.getZ(), f, 0.0F);
					serverWorld.addFreshEntityWithPassengers(armorstand);
					world.playSound(null, armorstand.getX(), armorstand.getY(), armorstand.getZ(), 
							SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
					armorstand.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
				}

				itemStack.shrink(1);
				return InteractionResult.SUCCESS;
			} else {
				return InteractionResult.FAIL;
			}
		}
	}

}