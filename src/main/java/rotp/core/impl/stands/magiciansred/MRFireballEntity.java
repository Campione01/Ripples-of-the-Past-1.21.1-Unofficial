package rotp.core.impl.stands.magiciansred;

import javax.annotation.Nullable;

import rotp.core.client.itemrender.ItemIconModels;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModEntityTypes;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.EventHooks;

public class MRFireballEntity extends ModdedProjectileEntity implements ItemSupplier {
	private static final ResourceLocation MR_FIREBALL_ITEM_MODEL = JojoMod.resLoc("item_icon_mr_fireball");
	private static ItemStack mrFireballSpriteItem = ItemStack.EMPTY;

	private static final int ORIGINAL_FIRE_SECONDS = 10;
	private static final int ORIGINAL_FIRE_TICKS = ORIGINAL_FIRE_SECONDS * 20;

	public MRFireballEntity(LivingEntity shooter, Level level) {
		super(ModEntityTypes.MR_FIREBALL.get(), shooter, level);
	}

	public MRFireballEntity(EntityType<? extends MRFireballEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 2.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 5F;
	}

	@Override
	public boolean standDamage() {
		return true;
	}

	@Override
	public ItemStack getItem() {
		if (mrFireballSpriteItem.isEmpty()) {
			mrFireballSpriteItem = ItemIconModels.makeIconItem(MR_FIREBALL_ITEM_MODEL);
		}
		return mrFireballSpriteItem;
	}

	@Override
	public boolean isOnFire() {
		return true;
	}

	@Override
	public boolean isFiery() {
		return true;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		return DamageUtil.dealDamageAndSetOnFire(target,
				entity -> super.hurtTarget(entity, owner), ORIGINAL_FIRE_TICKS, true);
	}

	@Override
	protected void afterBlockHit(BlockHitResult blockRayTraceResult, boolean blockDestroyed) {
		if (!level().isClientSide() && EventHooks.canEntityGrief(level(), this)) {
			Level level = level();
			BlockPos firePos = blockDestroyed ? blockRayTraceResult.getBlockPos()
					: blockRayTraceResult.getBlockPos().relative(blockRayTraceResult.getDirection());
			if (level.isEmptyBlock(firePos) && BaseFireBlock.canBePlacedAt(level, firePos, blockRayTraceResult.getDirection())) {
				level.setBlockAndUpdate(firePos, ModBlocks.MAGICIANS_RED_FIRE.get().getStateForPlacement(level, firePos));
			}
		}
	}
}
