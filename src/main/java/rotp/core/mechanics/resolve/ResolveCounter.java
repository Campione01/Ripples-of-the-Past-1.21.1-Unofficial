package rotp.core.mechanics.resolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.JojoModConfig;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.objects_java.DefaultedValue;
import rotp.core.util.objects_java.Lerp;
import rotp.core.util.objects_java.OptionalFloat;
import com.google.common.collect.BoundType;
import com.google.common.collect.Multiset;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;
import com.mojang.authlib.GameProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class ResolveCounter {
	public static final float RESOLVE_DMG_REDUCTION = 0.6667F;
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

	public static final int MAX_RESOLVE_RECORDS = 10;
	/** 1.16 StandType.resolveMultiplierTier: every Stand starts at 5, addAttackerResolveMultTier raises it. */
	public static final int DEFAULT_RESOLVE_MULTIPLIER_TIER = 5;
	private static final Map<ResourceLocation, Integer> RESOLVE_MULTIPLIER_TIER_ADD = new ConcurrentHashMap<>(Map.of(
			// 1.16 ModStandsInit: addAttackerResolveMultTier(1) on Star Platinum and The World
			JojoMod.resLoc("star_platinum"), 1,
			JojoMod.resLoc("the_world"), 1));

	public Lerp.FloatValue resolveLerp = new Lerp.FloatValue();
	public DefaultedValue.Int resolveModeTimer = new DefaultedValue.Int(-1);
	public int noResolveDecayTicks = 0;
	
	public float boostAttack = 1;
	public float boostRemoteControl = 1;
	public float boostChat = 1;
	public OptionalFloat hpOnGettingAttacked = OptionalFloat.empty();
	public int noBoostDecayTicks = 0;

	// 1.16 resolve records: the values fights reached (server only), which make climbing back up to them cheaper
	protected final TreeMultiset<Float> resolveRecords = TreeMultiset.create();
	protected boolean saveNextRecord = true;
	public float maxAchievedValue;


	public ResolveCounter() {}
	
	public void copyValues(ResolveCounter prev, boolean wasDeath) {
		this.resolveLerp = prev.resolveLerp;
		this.resolveModeTimer = prev.resolveModeTimer;
		this.noResolveDecayTicks = prev.noResolveDecayTicks;
		this.resolveRecords.clear();
		this.resolveRecords.addAll(prev.resolveRecords);
		this.saveNextRecord = prev.saveNextRecord;
		this.maxAchievedValue = prev.maxAchievedValue;
		if (!wasDeath) {
			this.boostAttack = prev.boostAttack;
			this.boostChat = prev.boostChat;
			this.hpOnGettingAttacked = prev.hpOnGettingAttacked;
			this.noBoostDecayTicks = prev.noBoostDecayTicks;
		}
		else {
			resetOnDeath();
		}
	}

	/** 1.16 ResolveCounter.alwaysResetOnDeath: the value is kept only as a record. */
	protected void resetOnDeath() {
		float resolve = getResolveValue();
		if (resolve > 0) {
			addResolveRecord(resolveRecords, resolve);
		}
		this.maxAchievedValue = maxResolveRecord(resolveRecords);
		this.resolveLerp = new Lerp.FloatValue();
		this.noResolveDecayTicks = 0;
		this.saveNextRecord = true;
		clearBoosts();
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
					// 1.16 counted the boosts on the value's own no-decay ticks, twice a tick with the Stand unsummoned
					noBoostDecayTicks = countDownNoDecayTicks(noBoostDecayTicks, stand.isSummoned());
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
			boolean fightEnds = noResolveDecayTicks == 1;
			noResolveDecayTicks = countDownNoDecayTicks(noResolveDecayTicks, stand.isSummoned());
			// 1.16: the value a fight reached is kept as a record once its no-decay ticks run out
			if (fightEnds && !user.level().isClientSide()) {
				saveResolveRecord(stand);
			}
		}
		else if (getResolveValue() > 0) {
			resolveLerp.set(Math.max(getResolveValue() - RESOLVE_DECAY, 0), true);
			if (getResolveValue() == 0) {
				saveNextRecord = true;
			}
		}
	}

	/** 1.16 ResolveCounter.tick: the no-decay ticks drop by one, and by one more while any are left and the Stand is not summoned. */
	public static int countDownNoDecayTicks(int ticks, boolean standSummoned) {
		if (ticks <= 0) {
			return ticks;
		}
		ticks--;
		if (ticks > 0 && !standSummoned) {
			ticks--;
		}
		return ticks;
	}

	protected void saveResolveRecord(StandPower stand) {
		if (saveNextRecord) {
			saveNextRecord = false;
		}
		else {
			resolveRecords.pollFirstEntry();
		}
		float resolve = getResolveValue();
		if (resolve > 0) {
			addResolveRecord(resolveRecords, resolve);
		}
		setMaxAchievedValue(stand, maxResolveRecord(resolveRecords));
	}

	/** 1.16 DiscardingSortedMultisetWrapper.add with a capacity of MAX_RESOLVE_RECORDS. */
	public static boolean addResolveRecord(SortedMultiset<Float> records, float value) {
		while (records.size() > MAX_RESOLVE_RECORDS) {
			records.pollFirstEntry();
		}
		if (records.size() == MAX_RESOLVE_RECORDS) {
			float min = records.firstEntry().getElement();
			if (value < min) {
				return false;
			}
			records.remove(min);
		}
		return records.add(value);
	}

	public static float maxResolveRecord(SortedMultiset<Float> records) {
		Multiset.Entry<Float> max = records.lastEntry();
		return max != null ? max.getElement() : 0;
	}

	/**
	 * 1.16 ResolveCounter.multiplyRecords: every record above the current value adds the part of the points that
	 * stays below it once more per time it was reached.
	 */
	public static float multiplyRecords(SortedMultiset<Float> records, float currentResolve, float addedValue) {
		float totalBoostedValue = 0;
		for (Multiset.Entry<Float> entry : records.tailMultiset(currentResolve, BoundType.OPEN).entrySet()) {
			float upperBorder = entry.getElement();
			float multiplier = 1 + entry.getCount();
			totalBoostedValue += Math.min(addedValue, upperBorder - currentResolve) * multiplier;
		}
		return totalBoostedValue + addedValue;
	}

	public float getMaxAchievedValue() {
		return maxAchievedValue;
	}

	public void setMaxAchievedValue(StandPower stand, float value) {
		if (this.maxAchievedValue != value) {
			this.maxAchievedValue = value;
			LivingEntity user = stand.getUser();
			if (user instanceof ServerPlayer player) {
				PacketDistributor.sendToPlayer(player, new ResolveBoostsPacket(this));
			}
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
		value = multiplyRecords(resolveRecords, getResolveValue(), value);
		return value;
	}

	protected float boostFromGettingAttacked(LivingEntity user) {
		PlayerPower playerPower = PlayerPower.get(user);
		if (playerPower != null && playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
			// 1.16: a vampire always had half of the missing health boost
			return BOOST_MISSING_HP_MAX / 2;
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
			ResolveAdvancements.onResolveLevelSet(stand, Math.min(newLevel, stand.getMaxResolveLevel()));
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
		resolveRecords.clear();
		saveNextRecord = true;
		maxAchievedValue = 0;
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
		resolveRecords.clear();
		ListTag records = nbt.getList("ResolveRecord", Tag.TAG_FLOAT);
		for (int i = 0; i < records.size(); i++) {
			addResolveRecord(resolveRecords, records.getFloat(i));
		}
		saveNextRecord = !nbt.contains("SaveNextRecord") || nbt.getBoolean("SaveNextRecord");
		maxAchievedValue = nbt.getFloat("MaxAchieved");
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
		ListTag records = new ListTag();
		for (float record : resolveRecords) {
			records.add(FloatTag.valueOf(record));
		}
		nbt.put("ResolveRecord", records);
		nbt.putBoolean("SaveNextRecord", saveNextRecord);
		nbt.putFloat("MaxAchieved", maxAchievedValue);

		return nbt;
	}
	
	
	
	
	
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onAttack(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource dmgSource = event.getSource();
		if (target.is(dmgSource.getEntity()) || !target.isAlive()) return;
		/*
		 * 1.16 resolveOnHurtEvent (LivingHurtEvent, HIGHEST) counted only a hit that landed: past a shield, and in the
		 * hurt cooldown only its excess over lastHurt. actuallyHurt sets the ARMOR reduction at that point, and
		 * getNewDamage() there is that amount. The guard's cut is added back, as 1.16 counted before it.
		 */
		event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
			countHit(target, dmgSource, container.getNewDamage() + StandUserGuard.guardCut(container));
			return reduction;
		});
	}

	private static void countHit(LivingEntity target, DamageSource dmgSource, float dmgAmount) {
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
			StandPower targetStand = StandPower.get(attackTarget);
			if (targetStand != null && targetStand.hasPower()) {
				dmgAmount *= standTargetResolveMultiplier(getResolveMultiplierTier(targetStand.getPowerType().getId()),
						attackerStand.hasPower() ? getResolveMultiplierTier(attackerStand.getPowerType().getId()) : null);
			}
			if (ResolveModeEffect.getResolveEffectLvl(attackTarget) >= 0) {
				dmgAmount *= Math.max(1 / (attackerStand.resolveCounter.getResolveRatio(attackerStand) + 0.2F), 1);
			}

			attackerStand.resolveCounter.addResolveOnAttack(attackerStand, dmgAmount);
		}
	}

	/**
	 * 1.16 StandType.getTargetResolveMultiplier: hitting a Stand user gives the target's tier + 1 less the attacker's
	 * tier, at least 1 (1 between two ordinary Stands, 2 on Star Platinum or The World).
	 */
	public static float standTargetResolveMultiplier(int targetTier, @Nullable Integer attackerTier) {
		float multiplier = targetTier + 1;
		if (attackerTier != null) {
			multiplier = Math.max(multiplier - attackerTier, 1);
		}
		return multiplier;
	}

	public static int getResolveMultiplierTier(@Nullable ResourceLocation standTypeId) {
		return DEFAULT_RESOLVE_MULTIPLIER_TIER
				+ (standTypeId != null ? RESOLVE_MULTIPLIER_TIER_ADD.getOrDefault(standTypeId, 0) : 0);
	}

	/** 1.16 StandType.Builder.addAttackerResolveMultTier, for add-on Stands. */
	public static void addAttackerResolveMultTier(ResourceLocation standTypeId, int tierAdd) {
		RESOLVE_MULTIPLIER_TIER_ADD.merge(standTypeId, tierAdd, Integer::sum);
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
    	// 1.16 LivingDamageEvent never fired for a hit the Stand's guard took whole (blockDamage cancelled it)
    	if (StandUserGuard.blockedWhole(event.getContainer())) {
    		return;
    	}
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
		ServerPlayer entity = event.getPlayer();
		// 1.16 GameplayEventHandler.messageAsStand: a remote-controlled Stand speaks instead of its user
		boolean asStand = messageAsStand(event);
		if (asStand) {
			event.setCanceled(true);
		}
		// 1.16 GameplayEventHandler.onChatMessage: a nearby Joseph-technique user may call the line out first
		for (ServerPlayer joseph : josephNextLineListeners(entity, asStand)) {
			if (joseph.getRandom().nextFloat() < JOSEPH_NEXT_LINE_CHANCE) {
				sayJosephNextLine(joseph, event.getRawText(), joseph.getRandom().nextInt(3) + 1);
			}
		}
		StandPower stand = StandPower.get(entity);
		if (stand != null) {
			stand.resolveCounter.onChatMessage(stand, event.getRawText());
		}
	}

	public static final float JOSEPH_NEXT_LINE_CHANCE = 0.05F;
	public static final double JOSEPH_NEXT_LINE_RANGE = 8.0D;

	public static List<ServerPlayer> josephNextLineListeners(ServerPlayer sender) {
		return josephNextLineListeners(sender, false);
	}

	// other players within 8 blocks with the Joseph technique and chat not hidden;
	// a line spoken by a Stand only reaches those who can hear Stands
	public static List<ServerPlayer> josephNextLineListeners(ServerPlayer sender, boolean asStand) {
		AABB area = AABB.ofSize(sender.getBoundingBox().getCenter(),
				JOSEPH_NEXT_LINE_RANGE * 2, JOSEPH_NEXT_LINE_RANGE * 2, JOSEPH_NEXT_LINE_RANGE * 2);
		return sender.level().getEntitiesOfClass(ServerPlayer.class, area, player -> player != sender
				&& player.getChatVisibility() != ChatVisiblity.HIDDEN
				&& (!asStand || StandUtil.entityCanHearStands(player))
				&& PlayerPower.getPowerData(player, ModPlayerPowers.HAMON)
						.map(hamon -> hamon.characterIs(ModHamonSkills.CHARACTER_JOSEPH.get())).orElse(false));
	}

	// broadcasts "<joseph> Your next line is "..."" with the giggle; null if a chat listener cancelled it
	@Nullable
	public static Component sayJosephNextLine(ServerPlayer joseph, String rawText, int variant) {
		return sayJosephNextLine(joseph, rawText, variant, joseph.server.getPlayerList().getPlayers());
	}

	@Nullable
	public static Component sayJosephNextLine(ServerPlayer joseph, String rawText, int variant, Iterable<ServerPlayer> candidates) {
		Component line = Component.translatable("jojo.chat.joseph.next_line." + variant, rawText);
		// 1.16 ran the line through ForgeHooks.onServerChatEvent as the Joseph's own chat
		Component filtered = CommonHooks.onServerChatSubmittedEvent(joseph, line.getString(), line);
		if (filtered == null) {
			return null;
		}
		Component message = Component.translatable("chat.type.text", joseph.getDisplayName(), filtered);
		JojoModUtil.sayVoiceLine(joseph, ModSoundEvents.JOSEPH_GIGGLE);
		// 1.16 sent it as CHAT: logged once, then only players with full chat get it ("Commands Only" does not)
		joseph.server.sendSystemMessage(message);
		for (ServerPlayer receiver : candidates) {
			if (receiver.getChatVisibility() == ChatVisiblity.FULL) {
				receiver.sendSystemMessage(message);
			}
		}
		return message;
	}

	public static final double STAND_CHAT_RANGE = 16.0D;
	public static final int STAND_CHAT_SPAM_LIMIT = 200;
	private static final Map<UUID, long[]> STAND_CHAT_SPAM = new ConcurrentHashMap<>();

	// 1.16 messageAsStand: false unless the sender is remote-controlling a summoned Stand
	public static boolean messageAsStand(ServerChatEvent event) {
		ServerPlayer sender = event.getPlayer();
		StandPower power = StandPower.get(sender);
		StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
		if (stand == null || !stand.isManuallyControlled()) {
			return false;
		}
		MinecraftServer server = sender.server;
		sendStandChat(sender, stand, event.getMessage(), server.getPlayerList().getPlayers());
		// the cancelled chat skips vanilla's spam counter (1.16 PlayerUtilCap.onChatMsgBypassingSpamCheck)
		GameProfile profile = sender.getGameProfile();
		if (addStandChatSpamTicks(sender) > STAND_CHAT_SPAM_LIMIT
				&& !server.getPlayerList().isOp(profile) && !server.isSingleplayerOwner(profile)) {
			sender.connection.disconnect(Component.translatable("disconnect.spam"));
		}
		return true;
	}

	// "<Stand> text" to the listeners; the server log keeps the real sender
	public static void sendStandChat(ServerPlayer sender, StandEntity stand, Component text, Iterable<ServerPlayer> candidates) {
		Component line = Component.translatable("chat.type.text", stand.getDisplayName(), text);
		Component revealLine = Component.translatable("chat.type.text", standNameWithUser(stand, sender), text);
		sender.server.sendSystemMessage(Component.translatable("chat.type.text", sender.getDisplayName(), text));
		for (ServerPlayer player : standChatListeners(sender, stand, candidates)) {
			// ops (level 3+) see who the Stand's user is
			player.sendSystemMessage(player.hasPermissions(3) ? revealLine : line);
		}
	}

	// the sender, then same-dimension players within 16 blocks of the Stand who can hear Stands
	// (anyone there if the Stand is visible to all); chat set to hide player messages gets nothing, as with CHAT in 1.16
	public static List<ServerPlayer> standChatListeners(ServerPlayer sender, StandEntity stand, Iterable<ServerPlayer> candidates) {
		List<ServerPlayer> listeners = new ArrayList<>();
		if (sender.getChatVisibility() == ChatVisiblity.FULL) {
			listeners.add(sender);
		}
		for (ServerPlayer player : candidates) {
			if (player != sender && player.getChatVisibility() == ChatVisiblity.FULL
					&& player.level().dimension() == sender.level().dimension()
					&& player.position().distanceToSqr(stand.position()) < STAND_CHAT_RANGE * STAND_CHAT_RANGE
					&& (StandUtil.entityCanHearStands(player) || stand.isVisibleForAll())) {
				listeners.add(player);
			}
		}
		return listeners;
	}

	// the Stand's team-formatted name; hovering shows "<Stand> (Stand User: <user>)", shift-click inserts the user's name
	public static Component standNameWithUser(StandEntity stand, ServerPlayer user) {
		return PlayerTeam.formatNameForTeam(stand.getTeam(), stand.getName()).withStyle(style -> style
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
						new HoverEvent.EntityTooltipInfo(stand.getType(), stand.getUUID(),
								Component.translatable("chat.stand_remote_reveal_name", stand.getName(), user.getName()))))
				.withInsertion(user.getGameProfile().getName()));
	}

	// adds 20 spam ticks that decay by 1 per server tick, like vanilla's chat spam counter; returns the new count
	public static int addStandChatSpamTicks(ServerPlayer sender) {
		long now = sender.server.getTickCount();
		long[] entry = STAND_CHAT_SPAM.computeIfAbsent(sender.getUUID(), id -> new long[] { 0L, now });
		synchronized (entry) {
			// a lower tick count means another server run: start over
			long elapsed = now >= entry[1] ? now - entry[1] : Long.MAX_VALUE;
			entry[0] = Math.max(0L, entry[0] - elapsed) + 20L;
			entry[1] = now;
			return (int) entry[0];
		}
	}

}
