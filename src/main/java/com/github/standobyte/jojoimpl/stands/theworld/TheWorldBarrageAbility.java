package com.github.standobyte.jojoimpl.stands.theworld;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBarrageAbility;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TheWorldBarrageAbility extends StandEntityBarrageAbility {
	private boolean suppressStandCryForNextAction;

	public TheWorldBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TheWorldBarrage::new);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		StandPower standPower = PowerClass.STAND.get(user);
		boolean standAlreadySummoned = standPower != null && standPower.getSummonedStandEntity() != null;
		boolean canPlayShout = standAlreadySummoned && !user.isShiftKeyDown();
		boolean voiceLineTriggered = false;
		if (canPlayShout && !level.isClientSide()) {
			Holder<SoundEvent> shout = isHighBloodVampire(user) ? ModSoundEvents.DIO_WRY : ModSoundEvents.DIO_MUDA_MUDA;
			voiceLineTriggered = JojoModUtil.sayVoiceLine(user, shout);
		}
		suppressStandCryForNextAction = voiceLineTriggered;
		HeldInput heldInput;
		try {
			heldInput = super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
		}
		finally {
			suppressStandCryForNextAction = false;
		}
		return heldInput;
	}

	@Override
	public EntityActionInstance initActionOnAbilityUse(Level level, LivingEntity powerUser, LivingEntity performer, FriendlyByteBuf extraInput) {
		EntityActionInstance action = super.initActionOnAbilityUse(level, powerUser, performer, extraInput);
		if (suppressStandCryForNextAction && action instanceof TheWorldBarrage barrage) {
			barrage.suppressStandCry();
		}
		return action;
	}

	private static boolean isHighBloodVampire(LivingEntity user) {
		return PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM)
				.map(data -> data.isHighOnBlood(user))
				.orElse(false);
	}

	public static class TheWorldBarrage extends StandEntityBarrage {
		private boolean suppressStandCry;

		public TheWorldBarrage(EntityActionType ability) {
			super(ability);
		}

		public void suppressStandCry() {
			this.suppressStandCry = true;
		}

		@Override
		protected boolean shouldPlayBarrageCry(Level level, StandEntity stand) {
			return !suppressStandCry && super.shouldPlayBarrageCry(level, stand);
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			super.toBuf(buf);
			buf.writeBoolean(suppressStandCry);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			super.fromBuf(buf);
			suppressStandCry = buf.readBoolean();
		}
	}
}
