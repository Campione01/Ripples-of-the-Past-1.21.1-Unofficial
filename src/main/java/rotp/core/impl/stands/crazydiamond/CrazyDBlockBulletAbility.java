package rotp.core.impl.stands.crazydiamond;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.UserStandEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CrazyDBlockBulletAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier BLOCK_BULLET_ANIM = ActionAnimIdentifier.getOrCreate("block_bullet", false);
	private static final float STAMINA_COST = 40F;
	public static final float HOMING_STAMINA_COST_TICK = 2F;
	private static final float SHOT_VELOCITY = 2.0F;
	private static final float SHOT_INACCURACY = 0.25F;

	public CrazyDBlockBulletAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, BlockBulletShot::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.WINDUP, 15);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> power) {
		LivingEntity user = power.getUser();
		ItemStack itemToShoot = user.getOffhandItem();
		if (itemToShoot == null || itemToShoot.isEmpty() || !(itemToShoot.getItem() instanceof BlockItem)) {
			return ConditionCheck.createNegative("block_offhand");
		}
		Block block = ((BlockItem) itemToShoot.getItem()).getBlock();
		BlockState blockState = block.defaultBlockState();
		if (!StandStatFormulas.isBlockBreakable(getBlockBulletStrength(power), blockState, user.level(), user.blockPosition())) {
			return ConditionCheck.createNegative("stand_cant_break_block");
		}
		if (!hardMaterial(blockState)) {
			return ConditionCheck.createNegative("item_hard_material");
		}
		ConditionCheck check = super.checkSpecificConditions(power);
		return check.isPositive() ? StandAbilityStamina.check(power, STAMINA_COST) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return BLOCK_BULLET_ANIM;
	}

	private static double getBlockBulletStrength(Power<?> power) {
		StandPower standPower = PowerClass.STAND.cast(power);
		if (standPower == null || standPower.getPowerType() == null) {
			return 0;
		}
		StandEntity standEntity = standPower.getSummonedStandEntity();
		return standEntity != null ? standEntity.getAttackDamage() : standPower.getPowerType().getStandStats().power();
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, 
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && disableHoming(powerUser)) {
			((BlockBulletShot) action).isHomingDisabled = true;
		}
	}

	public static class BlockBulletShot extends EntityActionInstance {
		protected boolean isHomingDisabled = false;
		protected HumanoidArm side;

		public BlockBulletShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			HumanoidArm mainArm = user != null ? user.getMainArm() : HumanoidArm.RIGHT;
			side = mainArm.getOpposite();
			double left = mainArm == HumanoidArm.LEFT ? -0.25 : 0.25;
			setStandOffset(new Vec3(left, 0.0, -0.5), StandOffsetFromUser.Rotations.BODY, false);
		}

		@Override
		public void actionTick() {
			if (level().isClientSide() && phase == ActionPhase.WINDUP && ClientGlobals.canSeeStands) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					CustomParticlesHelper.createCDRestorationParticle(user, InteractionHand.OFF_HAND);
				}
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (!level.isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user == null) return;
				ItemStack item = user.getOffhandItem();
				Block block = !item.isEmpty() && item.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
				if (block == null) return;

				StandPower standPower = StandPower.get(user);
				if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
					return;
				}
				
				CrazyDBlockBulletEntity bullet = new CrazyDBlockBulletEntity(performer, level);
				bullet.setShootingPosOf(user);
				bullet.setBlock(block);
				
				if (!isHomingDisabled) {
					UserStandEffects.getEffectLookedAt(standPower, ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get(), PLAYER_TRACKING_RANGE, user).ifPresent(effect -> {
						bullet.setTarget(effect.getTarget());
					});
					
				}
				
				float inaccuracy = performer instanceof StandEntity stand
						? StandStatFormulas.projectileInaccuracyScaling(stand.getPrecision(), SHOT_INACCURACY)
						: SHOT_INACCURACY;
				bullet.shootFromRotation(performer, SHOT_VELOCITY, inaccuracy);
				addProjectileWithStandStats(bullet);
				
				if (!(user instanceof Player player && player.getAbilities().instabuild)) {
					item.shrink(1);
				}
				bullet.homingStaminaCost = HOMING_STAMINA_COST_TICK;
			}
		}
		
		@Override
		public void onSetPhase(ActionPhase newPhase) {
			Level level = level();
			if (level.isClientSide() && performer instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
				switch (newPhase) {
					case WINDUP -> {
						ClientsideSoundsHelper.playNonVanillaClassSound(new EntityStoppableSoundInstance(ClientsideSoundsHelper.withStandSkin(
								ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), stand), 
								stand.getSoundSource(), 1, 1, stand, level.random.nextLong(), 
								() -> this.isOver() || this.phase != ActionPhase.WINDUP));
					}
					case PERFORM -> {
						level.playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
								ModSoundEvents.CRAZY_DIAMOND_BULLET_SHOT.get(), stand), 
								stand.getSoundSource(), 1, 1);
					}
					default -> {}
				}
			}
		}
	}

	public static final double PLAYER_TRACKING_RANGE = 64;

	
	public static boolean disableHoming(LivingEntity user) {
		return user.isShiftKeyDown();
	}

	public static boolean isHoming(LivingEntity user, StandPower userPower) {
		return user != null && !disableHoming(user)
				&& UserStandEffects.getEffectLookedAt(userPower, ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get(), PLAYER_TRACKING_RANGE, user).isPresent();
	}


	protected String homingSpriteName;
	
	@Override
	protected void initVariationAssets() {
		this.homingSpriteName = this.spriteName + "_homing";
	}
	
	@Override
	public String getSpriteName(Power<?> context) {
		if (isHoming(context.getUser(), PowerClass.STAND.cast(context))) {
			return homingSpriteName;
		}
		return super.getSpriteName(context);
	}

	@Override
	public Component getName(Power<?> context) {
		if (isHoming(context.getUser(), PowerClass.STAND.cast(context))) {
			return abilityName(context, ".homing");
		}
		return super.getName(context);
	}


	public static boolean hardMaterial(BlockState blockState) {
		return blockState.is(ModBlocks.CRAZY_D_CAN_MAKE_BULLET);
	}
    
    public static boolean isGlassBlock(BlockState blockState, Level level, @Nullable BlockPos blockPos) {
    	SoundType soundType = blockPos != null ? blockState.getSoundType() : blockState.getSoundType(level, blockPos, null);
    	return soundType == SoundType.GLASS;
    }

}
