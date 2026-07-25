package com.github.standobyte.jojo.powersystem.standpower.type;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ui.hud_misc.BottomLeftNotifications;
import com.github.standobyte.jojo.client.ui.text.StandSkillText;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.packet.StandExpPacket;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.util.functions.NBTUtil;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandTypePersistentData extends PowerData {
	private static final String ACTION_LEARNING_NBT = "ActionLearning";
	private static final String USED_TIME_STOP_NBT = "UsedTimeStop";
	private static final String LAST_TIME_STOP_DAY_NBT = "LastTimeStopDay";
	private static final String RESOLVE_LEVEL_NBT = "ResolveLevel";
	public static final int MAX_RESOLVE_LEVEL = 4;

	protected float exp;
	public Set<UUID> defeatedCharacters = new HashSet<>();
	public Set<ResourceLocation> defeatedStands = new HashSet<>();
	protected final Map<String, Float> abilityLearningProgress = new HashMap<>();
	private int resolveLevel;
	private boolean hasUsedTimeStopToday;
	private long lastTimeStopDay = Long.MIN_VALUE;
	
	public StandTypePersistentData(StandType powerType) {
		super(powerType);
	}
	
	@Override
	public StandType getPowerType() {
		return (StandType) super.getPowerType();
	}
	
	//@Override
	//public void onInit(Power<?> userPower) {
	//	super.onInit(userPower);
	//}

	@Override
	public void onInit(Power<?> userPower) {
		super.onInit(userPower);
		if (userPower instanceof StandPower standPower) {
			unlockOriginalResolveSkills(standPower);
		}
	}

	@Override
	public void onPowerGiven(Power<?> userPower, @Nullable PowerType oldType, @Nullable PowerData oldData) {
		if (userPower instanceof StandPower standPower) {
			unlockOriginalResolveSkills(standPower);
		}
	}
	
	@Override
	public boolean _setSkillUnlocked(UnlockableSkill skill, boolean unlocked, boolean inGameplay) {
		boolean changed = super._setSkillUnlocked(skill, unlocked, inGameplay);
		if (changed) {
			for (String abilityName : skill.unlocksAbilities) {
				if (unlocked) {
					abilityLearningProgress.putIfAbsent(abilityName, 0.0F);
				}
				else {
					abilityLearningProgress.remove(abilityName);
				}
			}
		}
		if (changed && inGameplay) {
			if (unlocked) {
				this.exp -= ((StandUnlockableSkill) skill).expToUnlock;
			}
			else {
				this.exp += ((StandUnlockableSkill) skill).expToUnlock;
			}
		}
		return changed;
	}
	
	@Override
	public void tick(Power<?> userPower) {
		if (!(userPower instanceof StandPower standPower)) {
			return;
		}
		LivingEntity user = standPower.getUser();
		if (user == null || user.level().isClientSide()) {
			return;
		}
		long day = user.level().getDayTime() / 24000L;
		if (lastTimeStopDay == Long.MIN_VALUE) {
			lastTimeStopDay = day;
			return;
		}
		if (day > lastTimeStopDay) {
			if (!hasUsedTimeStopToday) {
				TimeStopLearning.applyDailyDecay(this, standPower);
			}
			hasUsedTimeStopToday = false;
			lastTimeStopDay = day;
			syncToUser(standPower);
		}
		else if (day < lastTimeStopDay) {
			lastTimeStopDay = day;
			syncToUser(standPower);
		}
		if (unlockOriginalResolveSkills(standPower)) {
			syncOnUpdate(user);
		}
	}

	public boolean unlockOriginalResolveSkills(StandPower standPower) {
		if (standPower == null || standPower.getMoveset() == null) {
			return false;
		}
		boolean changed = false;
		for (UnlockableSkill skill : getAllSkills().values()) {
			if (!(skill instanceof StandUnlockableSkill standSkill)
					|| skill.hidden
					|| isSkillUnlocked(skill.skillName)
					|| !canUnlockByOriginalResolve(standPower, standSkill)) {
				continue;
			}
			changed |= _setSkillUnlocked(skill, true, false);
		}
		return changed;
	}

	private boolean canUnlockByOriginalResolve(StandPower standPower, StandUnlockableSkill skill) {
		Ability ability = standPower.getMoveset().getAbility(skill.skillName);
		if (ability == null) {
			for (String abilityName : skill.unlocksAbilities) {
				ability = standPower.getMoveset().getAbility(abilityName);
				if (ability != null) {
					break;
				}
			}
		}
		return ability != null && ability.getResolveUnlockConditionCheck(standPower).isPositive();
	}
	
	public float getAbilityLearningProgressPoints(String abilityName) {
		if (abilityName == null) {
			return -1.0F;
		}
		return abilityLearningProgress.getOrDefault(abilityName, isSkillUnlocked(abilityName) ? 0.0F : -1.0F);
	}
	
	public float getAbilityLearningProgressRatio(String abilityName, float maxPoints) {
		float points = getAbilityLearningProgressPoints(abilityName);
		if (points < 0) {
			return 0.0F;
		}
		return maxPoints > 0 ? Mth.clamp(Math.min(points, maxPoints) / maxPoints, 0.0F, 1.0F) : 1.0F;
	}
	
	public void setAbilityLearningProgressPoints(String abilityName, float points, float maxPoints, StandPower userPower) {
		if (abilityName == null || (!isSkillUnlocked(abilityName) && !abilityLearningProgress.containsKey(abilityName))) {
			return;
		}
		float stored = Math.max(points, 0.0F);
		Float previous = abilityLearningProgress.put(abilityName, stored);
		if (previous == null || Float.compare(previous, stored) != 0) {
			Ability ability = userPower != null ? userPower.getAbility(abilityName) : null;
			if (ability instanceof TrainableAbility trainable) {
				trainable.onTrainingPoints(userPower, Math.min(stored, maxPoints));
				if ((previous == null || previous < maxPoints) && stored >= maxPoints) {
					trainable.onMaxTraining(userPower);
				}
			}
			syncToUser(userPower);
		}
	}
	
	public void addAbilityLearningProgressPoints(String abilityName, float points, float maxPoints, StandPower userPower) {
		LivingEntity user = userPower != null ? userPower.getUser() : null;
		if (points > 0 && user != null && user.hasEffect(ModStatusEffects.RESOLVE)) {
			points *= 4F;
		}
		float current = getAbilityLearningProgressPoints(abilityName);
		if (current < 0) {
			return;
		}
		float next = current + points;
		if (current <= maxPoints) {
			next = Math.min(next, maxPoints);
		}
		setAbilityLearningProgressPoints(abilityName, Math.max(next, 0.0F), maxPoints, userPower);
	}
	
	public boolean hasUsedTimeStopToday() {
		return hasUsedTimeStopToday;
	}
	
	public void markUsedTimeStopToday(StandPower userPower) {
		if (!hasUsedTimeStopToday) {
			hasUsedTimeStopToday = true;
			syncToUser(userPower);
		}
	}
	
	private void syncToUser(StandPower userPower) {
		LivingEntity user = userPower != null ? userPower.getUser() : null;
		if (user instanceof ServerPlayer player && !user.level().isClientSide()) {
			syncToPlayer(player);
		}
	}
	
	
	public int getExp() {
		return (int) exp;
	}
	
	public int addExp(float exp, LivingEntity standUser) {
		int prevInt = (int) this.exp;
		this.exp += exp;
		int newInt = (int) this.exp;
		syncExp(standUser);
		return newInt - prevInt;
	}
	
	public void setExp(float exp, StandPower userPower) {
		setExp(exp, userPower, false);
	}
	
	public void setExp(float exp, StandPower userPower, boolean clientSideNewSkillNotification) {
		if (clientSideNewSkillNotification) {
			Collection<? extends UnlockableSkill> couldUnlock = getAllSkills().values().stream()
					.filter(skill -> skill.canUnlockFromMenu(userPower, this).isPositive()).collect(Collectors.toSet());
			
			this.exp = exp;
			
			Collection<? extends UnlockableSkill> newSkillsToUnlock = getAllSkills().values().stream()
					.filter(skill -> skill.canUnlockFromMenu(userPower, this).isPositive() && !couldUnlock.contains(skill)).toList();
			if (!newSkillsToUnlock.isEmpty()) {
				BottomLeftNotifications.add(Component.translatable("jojo_ripples.notification.stand_skill"));
				for (UnlockableSkill skill : newSkillsToUnlock) {
					BottomLeftNotifications.add(Component.translatable("jojo_ripples.list.entry.no_newline", StandSkillText.name(userPower, skill)));
				}
			}
		}
		else {
			this.exp = exp;
			syncExp(userPower.getUser());
		}
	}
	
	protected void syncExp(LivingEntity standUser) {
		if (standUser instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player, new StandExpPacket(this.exp));
		}
	}
	
	
	public static class StandExpSummary {
		static StandExpSummary instance = new StandExpSummary();
		public int spent, total, devPotential, remainingSkills, remainingHiddenSkills;
		StandExpSummary clear() { spent = 0; total = 0; devPotential = 0; remainingSkills = 0; remainingHiddenSkills = 0; return this; }
	}
	public StandExpSummary expSummary(StandPower userPower) {
		StandExpSummary obj = StandExpSummary.instance.clear();
		for (var skillEntry : getAllSkills().entrySet()) {
			StandUnlockableSkill skill = (StandUnlockableSkill) skillEntry.getValue();
			boolean isUnlocked = isSkillUnlocked(skill.skillName);
			if (skill.expToUnlock > 0) {
				obj.total += skill.expToUnlock;
				if (isUnlocked) {
					obj.spent += skill.expToUnlock;
				}
			}
			if (!isUnlocked) {
				obj.remainingSkills++;
				if (skill.hidden) {
					obj.remainingHiddenSkills++;
				}
			}
			obj.devPotential += skill.getDevPotentialCosmeticPoints(userPower, this, isUnlocked);
		}
		return obj;
	}

	public int getResolveLevel() {
		return Mth.clamp(resolveLevel, 0, getMaxResolveLevel());
	}

	public int getMaxResolveLevel() {
		return MAX_RESOLVE_LEVEL;
	}

	public boolean setResolveLevel(StandPower userPower, int level) {
		int clamped = Mth.clamp(level, 0, getMaxResolveLevel());
		if (resolveLevel != clamped) {
			resolveLevel = clamped;
			LivingEntity user = userPower.getUser();
			if (user != null && !user.level().isClientSide()) {
				syncOnUpdate(user);
			}
			return true;
		}
		return false;
	}
	

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = super.serializeNBT(provider);
		nbt.putFloat("exp", exp);
		nbt.putInt(RESOLVE_LEVEL_NBT, getResolveLevel());
		nbt.put("defeatedChars", NBTUtil.toList(defeatedCharacters, NbtUtils::createUUID));
		nbt.put("defeatedStands", NBTUtil.toList(defeatedStands, ResourceLocation.CODEC));
		CompoundTag learningNbt = new CompoundTag();
		for (var entry : abilityLearningProgress.entrySet()) {
			learningNbt.putFloat(entry.getKey(), entry.getValue());
		}
		nbt.put(ACTION_LEARNING_NBT, learningNbt);
		nbt.putBoolean(USED_TIME_STOP_NBT, hasUsedTimeStopToday);
		nbt.putLong(LAST_TIME_STOP_DAY_NBT, lastTimeStopDay);
		return nbt;
	}
	
	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		super.deserializeNBT(provider, nbt);
		this.exp = nbt.getFloat("exp");
		this.resolveLevel = Mth.clamp(nbt.getInt(RESOLVE_LEVEL_NBT), 0, getMaxResolveLevel());
		NBTUtil.fromList(nbt, "defeatedChars", defeatedCharacters::add, NbtUtils::loadUUID);
		NBTUtil.fromList(nbt, "defeatedStands", defeatedStands, ResourceLocation.CODEC);
		abilityLearningProgress.clear();
		if (nbt.get(ACTION_LEARNING_NBT) instanceof CompoundTag learningNbt) {
			for (String abilityName : learningNbt.getAllKeys()) {
				abilityLearningProgress.put(abilityName, learningNbt.getFloat(abilityName));
			}
		}
		hasUsedTimeStopToday = nbt.getBoolean(USED_TIME_STOP_NBT);
		lastTimeStopDay = nbt.contains(LAST_TIME_STOP_DAY_NBT) ? nbt.getLong(LAST_TIME_STOP_DAY_NBT) : Long.MIN_VALUE;
	}
	
	@Override
	public void toBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.toBuf(buf, isSentToTracking);
		buf.writeVarInt(getResolveLevel());
		if (!isSentToTracking) {
			buf.writeFloat(exp);
			buf.writeVarInt(abilityLearningProgress.size());
			for (var entry : abilityLearningProgress.entrySet()) {
				buf.writeUtf(entry.getKey());
				buf.writeFloat(entry.getValue());
			}
			buf.writeBoolean(hasUsedTimeStopToday);
			buf.writeLong(lastTimeStopDay);
		}
	}

	@Override
	public void fromBuf(FriendlyByteBuf buf, boolean isSentToTracking) {
		super.fromBuf(buf, isSentToTracking);
		resolveLevel = Mth.clamp(buf.readVarInt(), 0, getMaxResolveLevel());
		if (!isSentToTracking) {
			exp = buf.readFloat();
			abilityLearningProgress.clear();
			int learningSize = buf.readVarInt();
			for (int i = 0; i < learningSize; i++) {
				abilityLearningProgress.put(buf.readUtf(), buf.readFloat());
			}
			hasUsedTimeStopToday = buf.readBoolean();
			lastTimeStopDay = buf.readLong();
		}
	}
	
	
	@Override public PowerClass<?> getPowerClass() { return PowerClass.STAND; }
	
}
