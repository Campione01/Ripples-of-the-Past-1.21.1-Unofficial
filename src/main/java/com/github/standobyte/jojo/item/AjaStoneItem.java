package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.customobjects.entity_projectile.LightBeamEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData.HamonStat;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AjaStoneItem extends Item {
	public AjaStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HamonData hamon = PlayerPower.getPowerData(player, HamonPowerType.HAMON).orElse(null);
		if (hamon != null && (player.getAbilities().instabuild || hamon.hasEnergy(getHamonChargeCost()))
				&& hamon.isSkillLearned(ModHamonSkills.AJA_STONE_KEEPER.get())) {
			if (level.isClientSide()) {
				return InteractionResultHolder.sidedSuccess(stack, true);
			}
			if (player.getAbilities().instabuild || hamon.consumeEnergy(getHamonChargeCost(), player)) {
				useStone(level, player, stack, 0.75F * hamon.getHamonDamageMultiplier() * hamon.getActionEfficiency(getHamonChargeCost(), false, ModHamonSkills.AJA_STONE_KEEPER.get()), true, false);
				hamon.hamonPointsFromAction(HamonStat.STRENGTH, getHamonChargeCost());
				hamon.syncOnUpdate(player);
				JojoModUtil.sayVoiceLine(player, getHamonChargeVoiceLine());
				emitHamonSparkParticles(level, player, hamon.getHamonDamageMultiplier() / HamonData.MAX_HAMON_STRENGTH_MULTIPLIER * 1.5F);
				return InteractionResultHolder.sidedSuccess(stack, false);
			}
		}
		if (sufficientLight(level, player)) {
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		boolean perk = PlayerPower.getPowerData(entity, HamonPowerType.HAMON).map(hamon -> hamon.isSkillLearned(ModHamonSkills.AJA_STONE_KEEPER.get())).orElse(false);
		useStone(level, entity, stack, 10F, perk, true);
		return stack;
	}

	protected void useStone(Level level, LivingEntity entity, ItemStack itemStack, float damage, boolean perk, boolean checkLight) {
		if (checkLight && !sufficientLight(level, entity)) return;
		RandomSource random = entity.getRandom();
		entity.playSound(ModSoundEvents.AJA_STONE_BEAM.get(), Math.min(0.02F * damage, 1.0F),
				1.0F + (random.nextFloat() - random.nextFloat()) * 0.1F);
		if (!level.isClientSide()) {
			LightBeamEntity beam = new LightBeamEntity(ModEntityTypes.AJA_STONE_BEAM.get(), entity, level);
			beam.shoot(damage, 16F + damage / 2F);
			level.addFreshEntity(beam);
		}
		if (entity instanceof Player player) {
			player.getCooldowns().addCooldown(this, getCooldown());
			breakItem(level, player, itemStack, perk);
		}
		else {
			itemStack.shrink(1);
		}
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (level.isClientSide() && remainingTicks == getUseDuration(stack, entity)) {
			RandomSource random = entity.getRandom();
			entity.playSound(ModSoundEvents.AJA_STONE_CHARGING.get(), 0.25F,
					1.0F + (random.nextFloat() - random.nextFloat()) * 0.05F);
		}
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 20;
	}

	protected int getCooldown() {
		return 100;
	}

	protected float getHamonChargeCost() {
		return 200;
	}

	protected SoundEvent getHamonChargeVoiceLine() {
		return ModSoundEvents.LISA_LISA_AJA_STONE.get();
	}

	protected void breakItem(Level level, Player player, ItemStack itemStack, boolean perk) {
		if (!player.getAbilities().instabuild) {
			itemStack.shrink(1);
			RandomSource random = player.getRandom();
			if (!level.isClientSide() && random.nextInt(2) == 0) {
				player.addItem(perk && random.nextInt(200) == 0
						? new ItemStack(ModItems.SUPER_AJA_STONE.get())
						: new ItemStack(Items.REDSTONE));
			}
		}
	}

	private static void emitHamonSparkParticles(Level level, Player player, float intensity) {
		if (intensity <= 0.0F || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		intensity = Math.min(intensity, 4.0F);
		int count = Math.max((int) (intensity * 16.5F), 1);
		Vec3 sparkVec = player.getLookAngle().scale(0.75D).add(player.getX(), player.getY(0.6D), player.getZ());
		double x = sparkVec.x;
		double y = sparkVec.y;
		double z = sparkVec.z;
		serverLevel.sendParticles(ModParticles.HAMON_SPARK.get(), x, y, z, count, 0.05, 0.05, 0.05, 0.25);
		float volume = Math.min(intensity * 2.0F, 1.0F);
		level.playSound(null, x, y, z, ModSoundEvents.HAMON_SPARK.get(), SoundSource.AMBIENT,
				volume, 1.0F + (player.getRandom().nextFloat() - 0.5F) * 0.15F);
	}

	private static boolean sufficientLight(Level level, LivingEntity entity) {
		BlockPos pos = entity.blockPosition();
		if (!level.isClientSide()) {
			return level.getMaxLocalRawBrightness(pos) > 9;
		}

		int time = (int) (level.getDayTime() % 24000);
		int light = level.dimension() != Level.OVERWORLD
				|| level.isRainingAt(pos)
				|| time > 12866 && time < 23135
				? level.getBrightness(LightLayer.BLOCK, pos)
				: level.getMaxLocalRawBrightness(pos);
		return light > 9;
	}
}
