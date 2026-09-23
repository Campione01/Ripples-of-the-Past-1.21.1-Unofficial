package rotp.core.mechanics.resolve;

import rotp.core.core.JojoMod;
import rotp.core.JojoModConfig;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.objects_java.DefaultedValue;
import rotp.core.util.objects_java.Lerp;
import rotp.core.util.objects_java.OptionalFloat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class ResolveCounter {
	public static final float RESOLVE_DMG_REDUCTION = 0.6F;
	public static final float[] DEFAULT_MAX_RESOLVE_VALUES = { 2500.0F, 10000.0F, 25000.0F, 50000.0F, 32500.0F };
	protected static final float RESOLVE_DECAY = 2F;
	protected static final int RESOLVE_NO_DECAY_TICKS = 400;
	public static final float RESOLVE_FOR_DMG_POINT = 1F;
	public static final int[] RESOLVE_EFFECT_MIN = { 300, 400, 500, 600, 600 };
	public static final int[] RESOLVE_EFFECT_MAX = { 600, 1200, 1500, 1800, 2400 };


	public static final float BOOST_ATTACK_MAX = 5F;
	public static final float BOOST_PER_DMG_DEALT = 0.05F;
	public static final int NO_BOOST_ATTACK_DECAY_TICKS = 400;

	public static final float BOOST_MISSING_HP_MAX = 10F;
	public static final float BOOST_MIN_HP = 5F;
	public static final float BOOST_MAX_HP = 15F;

	public static final float BOOST_REMOTE_MAX = 5F;
	public static final float BOOST_REMOTE_PER_TICK = 0.025F;

	public static final float BOOST_CHAT_MAX = 1.25F;
	public static final float BOOST_PER_CHARACTER = 0.05F;

	public Lerp.FloatValue resolveLerp = new Lerp.FloatValue();
	public DefaultedValue.Int resolveModeTimer = new DefaultedValue.Int(-1);
	public int noResolveDecayTicks = 0;
	
	public float boostAttack = 1;
	public float boostRemoteControl = 1;
	public float boostChat = 1;
	public OptionalFloat hpOnGettingAttacked = OptionalFloat.empty();
	public int noBoostDecayTicks = 0;


	public ResolveCounter() {}
	
	public void copyValues(ResolveCounter prev, boolean wasDeath) {
		this.resolveLerp = prev.resolveLerp;
		this.resolveModeTimer = prev.resolveModeTimer;
		this.noResolveDecayTicks = prev.noResolveDecayTicks;
		if (!wasDeath) {
			this.boostAttack = prev.boostAttack;
			this.boostChat = prev.boostChat;
			this.hpOnGettingAttacked = prev.hpOnGettingAttacked;
			this.noBoostDecayTicks = prev.noBoostDecayTicks;
		}
		else {
			clearBoosts();
		}
	}
	
	public void clearBoosts() {
		this.boostAttack = 1;
		this.boostRemoteControl = 1;
		this.boostChat = 1;
		hpOnGettingAttacked = OptionalFloat.empty();
		this.noBoostDecayTicks = 0;
	}


	public void tick(StandPower stand) {
		if (stand.usesResolve()) {
			resolveLerp.lerpTick();
			LivingEntity user = stand.getUser();
			float resolveBeforeTick = getResolveValue();
			boolean resolveEffectOn = user != null && user.hasEffect(ModStatusEffects.RESOLVE);
			tickResolveValue(stand, user);
			float curResolve = getResolveValue();
			
			// 1.16 had no mode timer: a draining Resolve ends when its value runs out (tickResolveValue) and
			// any Resolve ends on its effect's own duration, finite or infinite. The timer only shows what is left.
			MobEffectInstance resolveMode = user.getEffect(ModStatusEffects.RESOLVE);
			resolveModeTimer.value = nextResolveModeTimer(resolveModeTimer.value, resolveModeTimer.defaultValue,
					resolveMode != null && drainsResolveValue(resolveMode.getAmplifier()), curResolve, getMaxResolveValue(stand));
			if (resolveModeTimer.value <= 0) {
				resolveModeTimer.defaultValue = -1;
				resolveModeTimer.reset();
			}
			
			// 1.16 ResolveCounter.tick: boosts neither count down nor reset while the Resolve effect is on
			if (!resolveEffectOn) {
				if (noBoostDecayTicks > 0) {
					noBoostDecayTicks--;
				}
				else {
					boolean hadValue = resolveBeforeTick > 0;
					if (hadValue) {
						boostAttack = 1;
					}
					if (hadValue && curResolve == 0) {
						boostChat = 1;
						hpOnGettingAttacked = OptionalFloat.empty();
					}
					else if (user != null && user.getHealth() == user.getMaxHealth()) {
						hpOnGettingAttacked = OptionalFloat.empty();
					}
				}
			}
			
			tickBoostRemoteControl(stand);
		}
	}

	private void tickResolveValue(StandPower stand, LivingEntity user) {
		MobEffectInstance resolveMode = user.getEffect(ModStatusEffects.RESOLVE);
		if (resolveMode != null) {
			// 1.16 ResolveCounter.tick: only levels below RESOLVE_EFFECT_MIN.length drain the value,
			// a higher Resolve (GER's evolution, level 5) keeps it full for the whole effect
			if (!drainsResolveValue(resolveMode.getAmplifier())) {
				return;
			}
			int resolveLevel = resolveMode.getAmplifier();
			if (resolveLevel < 0) {
				resolveLevel = 255;
			}
			resolveLevel = Math.min(resolveLevel, RESOLVE_EFFECT_MIN.length - 1);
			float nextResolve = Math.max(getResolveValue() - getMaxResolveValue(stand) / (float) RESOLVE_EFFECT_MIN[resolveLevel], 0);
			resolveLerp.set(nextResolve, true);
			if (!user.level().isClientSide() && nextResolve == 0) {
				user.removeEffect(ModStatusEffects.RESOLVE);
			}
			return;
		}
		
		if (noResolveDecayTicks > 0) {
			noResolveDecayTicks--;
			if (noResolveDecayTicks > 0 && !stand.isSummoned()) {
				noResolveDecayTicks--;
			}
		}
		else if (getResolveValue() > 0) {
			resolveLerp.set(Math.max(getResolveValue() - RESOLVE_DECAY, 0), true);
		}
	}
	
	public float getResolveValue() {
		return resolveLerp.get();
	}
	
	public float getResolveRatio(StandPower stand) { return getResolveRatio(stand, 1); }
	
	public float getResolveRatio(StandPower stand, float partialTick) {
		if (!stand.usesResolve()) return 0;
		float maxResolve = getMaxResolveValue(stand);
		return maxResolve > 0 ? resolveLerp.lerp(partialTick) / maxResolve : 0;
	}
	
	public float getMaxResolveValue(StandPower stand) {
		int index = stand != null ? stand.getResolveLevel() : 0;
		boolean clientSide = stand != null && stand.getUser() != null && stand.getUser().level().isClientSide();
		return JojoModConfig.getResolveLevelMax(clientSide, index);
	}

	public float getMaxResolveValue() {
		return JojoModConfig.getResolveLevelMax(false, 0);
	}
	
	public float getResolveModeTimerRatio(StandPower stand, float partialTick) {
		LivingEntity user = stand.getUser();
		MobEffectInstance resolveEffect = ResolveModeEffect.maxDurationResolveEffect(user);
		if (resolveEffect != null) {
			boolean timerOn = resolveModeTimer.defaultValue > -1 && resolveModeTimer.value > -1;
			// an infinite Resolve with no countdown keeps a full ring
			if (resolveEffect.isInfiniteDuration() && !timerOn) {
				return 1;
			}
			int duration = resolveEffect.getDuration();
			if (timerOn) {
				duration = resolveModeTicksShown(resolveModeTimer.value, duration);
			}
			float value = duration + 1 - partialTick;
			if (value > 0) {
				if (resolveModeTimer.defaultValue > 0) {
					return value / resolveModeTimer.defaultValue;
				}
				return 1;
			}
		}
		return -1;
	}

	/** Mode ticks the HUD prints: the timer, clamped to the effect's remaining duration as the ring is. */
	public int getResolveModeTicksShown(StandPower stand) {
		MobEffectInstance resolveEffect = ResolveModeEffect.maxDurationResolveEffect(stand.getUser());
		return resolveEffect != null ? resolveModeTicksShown(resolveModeTimer.value, resolveEffect.getDuration()) : -1;
	}

	/** The mode timer, never past the effect's remaining ticks; effectTicks below 0 is an infinite effect. */
	public static int resolveModeTicksShown(int timer, int effectTicks) {
		return timer > 0 && effectTicks >= 0 ? Math.min(timer, effectTicks) : timer;
	}



	public void setResolveValue(StandPower stand, float resolve) {
		setResolveValue(stand, resolve, -1);
	}

	public void setResolveValue(StandPower stand, float resolve, int noDecayTicks) {
		resolve = Mth.clamp(resolve, 0, getMaxResolveValue(stand));
		if (noDecayTicks < 0) {
			noDecayTicks = this.noResolveDecayTicks;
		}
		boolean send = this.noResolveDecayTicks != noDecayTicks;
		this.noResolveDecayTicks = noDecayTicks;
		send |= resolveLerp.set(resolve, true);

		LivingEntity user = stand.getUser();
		if (!user.level().isClientSide() && send) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrResolvePacket(user.getId(), getResolveValue(), noResolveDecayTicks));
		}
		if (!user.level().isClientSide()) {
			autoResolveModeActivation(stand);
		}
	}

	public void addResolveValue(StandPower stand, float resolve) {
		LivingEntity user = stand.getUser();
		MobEffectInstance resolveMode = user.getEffect(ModStatusEffects.RESOLVE);
		
		if (resolveMode == null) {
			setResolveValue(stand, getResolveValue() + boostAddedValue(resolve, user), RESOLVE_NO_DECAY_TICKS);
			noBoostDecayTicks = NO_BOOST_ATTACK_DECAY_TICKS;
		}
		else {
//			int resolveLevel = resolveMode.getAmplifier();
//			if (resolveLevel < RESOLVE_EFFECT_MAX.length) {
//				resolveModeTimer.value = Math.max(resolveModeTimer.value, resolveModeTimer.defaultValue / 2);
//			}
			// 1.16 ResolveCounter.addResolveValue: the boosted points are added first, then
			// the value is kept at half or more (Rain Redemption's refill keeps it full)
			float addedResolve = getResolveValue() + boostAddedValue(resolve, user);
			setResolveValue(stand, Math.max(getMaxResolveValue(stand) * 0.5F, addedResolve), 0);
			// the shown timer follows the new value at once; a non-draining Resolve's timer is its
			// effect duration and is left alone (the original guard skipped levels past the table)
			if (drainsResolveValue(resolveMode.getAmplifier())) {
				resolveModeTimer.value = resolveModeTicksForValue(resolveModeTimer.defaultValue, getResolveValue(), getMaxResolveValue(stand));
			}
		}
		
		if (user instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
		}
	}

	/** 1.16 ResolveCounter.tick drained the value only for Resolve levels below RESOLVE_EFFECT_MIN.length. */
	public static boolean drainsResolveValue(int amplifier) {
		return amplifier < RESOLVE_EFFECT_MIN.length;
	}

	/**
	 * Resolve mode ticks left for a value: 1.16's draining value lost max / RESOLVE_EFFECT_MIN
	 * per tick, so a value lasts value / max of the timer.
	 */
	public static int resolveModeTicksForValue(int modeTicksMax, float value, float maxValue) {
		if (modeTicksMax <= 0 || maxValue <= 0) {
			return 0;
		}
		return Mth.ceil(modeTicksMax * Mth.clamp(value / maxValue, 0.0F, 1.0F));
	}

	/**
	 * The mode timer after a tick, on both sides. It is display only and never ends the Resolve:
	 * a draining one shows how long its value lasts (so a refill, e.g. from a soul, stretches it),
	 * any other one counts down the effect duration it started with (-1 for an infinite effect).
	 */
	public static int nextResolveModeTimer(int timer, int timerMax, boolean drainingResolve, float value, float maxValue) {
		if (drainingResolve) {
			return resolveModeTicksForValue(timerMax, value, maxValue);
		}
		return timer > 0 ? timer - 1 : timer;
	}

	protected float boostAddedValue(float value, LivingEntity entity) {
		value *= boostAttack * boostFromGettingAttacked(entity);
		return value;
	}

	protected float boostFromGettingAttacked(LivingEntity user) {
		PlayerPower playerPower = PlayerPower.get(user);
		if (playerPower != null && playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
			return 1;
		}
		float hp = user.getHealth();
		if (hpOnGettingAttacked.isPresent() && hpOnGettingAttacked.getAsFloat() < hp) {
			hp = hpOnGettingAttacked.getAsFloat();
		}
		hp = Mth.clamp(hp, BOOST_MIN_HP, BOOST_MAX_HP);
		float boost = Mth.clamp((BOOST_MAX_HP - hp) * (BOOST_MISSING_HP_MAX - 1) / (BOOST_MAX_HP - BOOST_MIN_HP) + 1, 0, BOOST_MAX_HP);
		return boost;
	}

	public float getTotalBoostVisible(LivingEntity user) {
		float boost = boostAttack * boostFromGettingAttacked(user) * boostChat * boostRemoteControl;
		return boost;
	}
	
	
	@Deprecated
	protected void autoResolveModeActivation(StandPower stand) {
		if (canEnterResolveMode(stand)) {
			startResolveMode(stand);
		}
	}
	
	public boolean canEnterResolveMode(StandPower stand) {
		LivingEntity user = stand.getUser();
		return user != null && getResolveValue() >= getMaxResolveValue(stand) && ResolveModeEffect.getResolveEffectLvl(user) < 0;
	}
	
	public boolean startResolveMode(StandPower stand) {
		if (canEnterResolveMode(stand)) {
			LivingEntity user = stand.getUser();
			if (!user.level().isClientSide()) {
				int resolveLevel = Mth.clamp(stand.getResolveLevel(), 0, RESOLVE_EFFECT_MAX.length - 1);
				stand.getUser().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 
						RESOLVE_EFFECT_MAX[resolveLevel], resolveLevel, false, 
						false, true));
			}
			return true;
		}
		return false;
	}
	
	public void onResolveEffectStart(StandPower stand, LivingEntity user, MobEffectInstance resolveEffect) {
		if (user != null) {
			int resolveLevel = Mth.clamp(resolveEffect.getAmplifier(), 0, RESOLVE_EFFECT_MAX.length - 1);
			int newLevel = resolveLevel + 1;
			stand.setResolveLevel(Math.min(newLevel, stand.getMaxResolveLevel()));
			setResolveValue(stand, stand.resolveCounter.getMaxResolveValue(stand), 0);
			
			boolean hasMinDuration = false;
			// a non-draining Resolve (level 5+) runs for the effect's own duration, as in 1.16
			if (resolveEffect.is(ModStatusEffects.RESOLVE) && drainsResolveValue(resolveEffect.getAmplifier())) {
				hasMinDuration = true;
				resolveModeTimer.defaultValue = RESOLVE_EFFECT_MIN[resolveLevel];
			}
			if (!hasMinDuration) {
				// an infinite effect has no countdown to show (-1, never a huge timer)
				resolveModeTimer.defaultValue = resolveEffect.isInfiniteDuration() ? -1 : resolveEffect.getDuration();
			}
			resolveModeTimer.reset();
			
			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
			}
		}
	}
	
	public void onResolveEffectEnd(StandPower stand, LivingEntity user) {
//		if (hasAnotherResolveEffect()) {
//			onResolveEffectStart(stand, user, resolveEffect);
//		}
//		else {
			resetResolveValue(stand);
			if (!user.level().isClientSide()) {
				if (user instanceof ServerPlayer player) {
					PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
				}
			}
//		}
	}

	public void resetResolveValue(StandPower stand) {
		resolveLerp.set(0, false);
		noResolveDecayTicks = 0;
		clearBoosts();
		resolveModeTimer.defaultValue = -1;
		resolveModeTimer.reset();
		
		LivingEntity user = stand.getUser();
		if (user != null && !user.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, TrResolvePacket.reset(user.getId()));
		}
	}


	public void addResolveOnAttack(StandPower stand, float dmgAmount) {
		if (stand.usesResolve()) {
			LivingEntity user = stand.getUser();
			float points = dmgAmount * RESOLVE_FOR_DMG_POINT;
			addResolveValue(stand, points);
			if (!user.level().isClientSide() && boostAttack < BOOST_ATTACK_MAX) {
				float boost = dmgAmount * BOOST_PER_DMG_DEALT;
				boostAttack = Math.min(boostAttack + boost, BOOST_ATTACK_MAX);
				if (user instanceof ServerPlayer player) {
					PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
				}
			}
		}
	}

	public void soulAddResolveLook(StandPower stand) {
		setResolveValue(stand, getResolveValue() + getMaxResolveValue(stand) / 60.0F, -1);
	}

	public void soulAddResolveTeammate(StandPower stand) {
		setResolveValue(stand, getResolveValue() + getMaxResolveValue(stand) / 300.0F, -1);
	}

	public void onGettingAttacked(DamageSource dmgSource, float dmgAmount, StandPower stand, LivingEntity user) {
		Entity attacker = dmgSource.getEntity();
		if (attacker != null && !attacker.level().isClientSide() && stand.usesResolve() && attacker != null && !attacker.is(user)) {
			float hp = Math.max(user.getHealth() - dmgAmount, 0);
			if (hpOnGettingAttacked.isPresent()) {
				hp = Math.min(hp, hpOnGettingAttacked.getAsFloat());
			}
			hpOnGettingAttacked = OptionalFloat.of(hp);

			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
			}

			if (dmgAmount >= user.getMaxHealth() * 0.4F) {
				addResolveValue(stand, resolveOnGettingAttacked(dmgAmount));
			}
		}
	}

	/** 1.16 ResolveCounter.onGettingAttacked: a hit of 40% max health or more adds dmg * BOOST_PER_DMG_DEALT * 2 before boosts. */
	public static float resolveOnGettingAttacked(float dmgAmount) {
		return dmgAmount * BOOST_PER_DMG_DEALT * 2;
	}

	protected void tickBoostRemoteControl(StandPower stand) {
		if (stand.isSummoned() && stand.getUser() != null) {
			StandEntity standEntity = stand.getSummonedStandEntity();
			if (standEntity != null && standEntity.isManuallyControlled() /*&& ((StandEntity) standManifestation).distanceToSqr(stand.getUser()) >= 25*/) {
				boostRemoteControl = Math.min(boostRemoteControl + BOOST_REMOTE_PER_TICK, BOOST_REMOTE_MAX);
				return;
			}
		}
		boostRemoteControl = 1;
	}

	public void onChatMessage(StandPower stand, String message) {
		if (boostAttack > 1 || hpOnGettingAttacked.isPresent()) {
			int length = message.length();
			boostChat = Math.min(boostChat + length * BOOST_PER_CHARACTER, BOOST_CHAT_MAX);
			LivingEntity user = stand.getUser();
			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
			}
		}
	}


//	public void soulAddResolveLook() {
//		setResolveValue(getResolveValue() + getMaxResolveValue() / 60);
//	}
//
//	public void soulAddResolveTeammate() {
//		setResolveValue(getResolveValue() + getMaxResolveValue() / 300);
//	}


	public void syncToTracking(LivingEntity user, ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new TrResolvePacket(user.getId(), resolveLerp.get(), noResolveDecayTicks));
	}

	public void syncToUser(ServerPlayer user) {
		PacketDistributor.sendToPlayer(user, new TrResolvePacket(user.getId(), resolveLerp.get(), noResolveDecayTicks));
		PacketDistributor.sendToPlayer(user, new ResolveBoostsPacket(this));
	}

	public void readNBT(CompoundTag nbt) {
		resolveLerp.set(nbt.getFloat("Resolve"), false);
		resolveModeTimer.defaultValue = nbt.getInt("ResolveModeMax");
		resolveModeTimer.value = nbt.getInt("ResolveMode");
		noResolveDecayTicks = nbt.getInt("ResolveTicks");
		boostAttack = nbt.getFloat("BoostAttack");
		boostRemoteControl = nbt.getFloat("BoostRemoteControl");
		boostChat = nbt.getFloat("BoostChat");
		hpOnGettingAttacked = nbt.contains("HpOnGettingAttacked") ? OptionalFloat.of(nbt.getFloat("HpOnGettingAttacked")) : OptionalFloat.empty();
		noBoostDecayTicks = nbt.getInt("NoDecayTicks");
	}

	public CompoundTag writeNBT() {
		CompoundTag nbt = new CompoundTag();
		nbt.putFloat("Resolve", resolveLerp.get());
		nbt.putInt("ResolveModeMax", resolveModeTimer.defaultValue);
		nbt.putInt("ResolveMode", resolveModeTimer.value);
		nbt.putInt("ResolveTicks", noResolveDecayTicks);
		nbt.putFloat("BoostAttack", boostAttack);
		nbt.putFloat("BoostRemoteControl", boostRemoteControl);
		nbt.putFloat("BoostChat", boostChat);
		hpOnGettingAttacked.ifPresent(hp -> nbt.putFloat("HpOnGettingAttacked", hp));
		nbt.putInt("NoDecayTicks", noBoostDecayTicks);

		return nbt;
	}
	
	
	
	
	
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onAttack(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		float dmgAmount = event.getAmount();
		
		if (target.is(dmgSource.getEntity()) || !target.isAlive()) return;
		float points = dmgAmount;
//		float points = Math.min(dmgAmount, target.getHealth());

		if (dmgSource.is(ModDamageTypes.ADDS_RESOLVE)) {
			Entity attacker = dmgSource.getEntity();
			if (attacker instanceof LivingEntity living) {
				LivingEntity standUser = StandUtil.getStandUser(living);
				StandPower attackerStand = StandPower.get(standUser);
				if (attackerStand != null && attackerStand.hasPower()) {
					addResolve(attackerStand, target, points);
				}
			}
		}

		else if (dmgSource.getEntity() instanceof LivingEntity) {
			LivingEntity attacker = (LivingEntity) dmgSource.getEntity();
//			UserStandEffects.getEffectsTargetedBy(attacker, ModStandEffects.GE_CREATED_LIFEFORM.get()).findAny().ifPresent(geLifeform -> {
//				StandPower geUserPower = geLifeform.getUserPower();
//				addResolve(geUserPower, target, points * 1.25F);
//			});

			StandPower attackerStand = StandPower.get(attacker);
			if (attackerStand != null && attackerStand.isSummoned()) {
				addResolve(attackerStand, target, points * 0.5F);
			}
		}
	}
    

	public static void addResolve(StandPower attackerStand, LivingEntity attackTarget, float dmgAmount) {
		if (attackerStand == null) return;
		attackTarget = StandUtil.getStandUser(attackTarget);
		boolean hitSelf = attackTarget != null && attackerStand.getUser() != null && attackTarget.is(attackerStand.getUser());
		if (!hitSelf && attackTarget.isAlive() && attackingTargetGivesResolve(attackTarget)) {
//			for (PowerClass<?> classification : PowerClass.values()) {
//				points *= classification.getOptional(attackTarget).map(power -> {
//					if (power.hasPower()) {
//						return power.getPowerType().getTargetResolveMultiplier(getThis(), attackerStand);
//					}
//					return 1F;
//				}).orElse(1F);
//			}
			PlayerPower targetPower =
					PlayerPower.get(attackTarget);
			if (targetPower != null) {
				dmgAmount *= targetPower
						.getTargetResolveMultiplier(attackerStand);
			}
			if (ResolveModeEffect.getResolveEffectLvl(attackTarget) >= 0) {
				dmgAmount *= Math.max(1 / (attackerStand.resolveCounter.getResolveRatio(attackerStand) + 0.2F), 1);
			}

			attackerStand.resolveCounter.addResolveOnAttack(attackerStand, dmgAmount);
		}
	}

	public static boolean attackingTargetGivesResolve(Entity target) {
		if (target.getClassification(false) == MobCategory.MONSTER || target.getType() == EntityType.PLAYER) {
			return true;
		}
		if (target instanceof LivingEntity) {
			LivingEntity livingEntity = (LivingEntity) target;
			if (livingEntity instanceof StandEntity) {
				return true;
			}
			if (livingEntity instanceof Mob mob) {
				return livingEntity instanceof Monster || mob.isAggressive();
			}
		}
		return false;
	}
	

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void resolveOnTakingDamage(LivingDamageEvent.Pre event) {
    	LivingEntity target = event.getEntity();
    	StandPower stand = StandPower.get(target);
    	if (stand != null && stand.usesResolve()) {
    		stand.resolveCounter.onGettingAttacked(event.getSource(), event.getNewDamage(), stand, target);
    	}
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void reduceDamageFromResolve(LivingDamageEvent.Pre event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        LivingEntity target = event.getEntity();
        StandPower stand = StandPower.get(target);
        if (stand != null) {
        	float dmgReduction = stand.resolveCounter.getResolveDmgReduction(stand, target);
        	if (dmgReduction > 0F) {
        		event.setNewDamage(event.getNewDamage() * (1 - dmgReduction));
        	}
        }
    }
    
    public float getResolveDmgReduction(StandPower stand, LivingEntity user) {
    	PlayerPower playerPower = PlayerPower.get(user);
    	if (playerPower != null && playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
    		return 0;
    	}
        if (ResolveModeEffect.getResolveEffectLvl(user) >= 0) {
            return RESOLVE_DMG_REDUCTION;
        }
        if (stand.usesResolve()) {
            return stand.resolveCounter.getResolveRatio(stand) * RESOLVE_DMG_REDUCTION;
        }
        return 0;
    }

    
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onChatMessage(ServerChatEvent event) {
		LivingEntity entity = event.getPlayer();
		StandPower stand = StandPower.get(entity);
		if (stand != null) {
			stand.resolveCounter.onChatMessage(stand, event.getRawText());
		}
	}
	
}
