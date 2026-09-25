package rotp.core.impl.stands.theworld;

import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TheWorldBarrageAbility extends StandEntityBarrageAbility {
	@Nullable private Holder<SoundEvent> shoutForNextAction;

	public TheWorldBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, TheWorldBarrage::new);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		// 1.16 TheWorldBarrage#getShout: wasActive is read before the press auto-summons the Stand
		StandPower standPower = PowerClass.STAND.get(user);
		boolean standAlreadySummoned = standPower != null && standPower.getSummonedStandEntity() != null;
		Holder<SoundEvent> shout = null;
		if (!level.isClientSide() && !skipsShoutWhileSneaking(user)) {
			shout = standAlreadySummoned && isHighBloodVampire(user) ? ModSoundEvents.DIO_WRY : ModSoundEvents.DIO_MUDA_MUDA;
		}
		shoutForNextAction = shout;
		try {
			return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
		}
		finally {
			shoutForNextAction = null;
		}
	}

	@Override
	public EntityActionInstance initActionOnAbilityUse(Level level, LivingEntity powerUser, LivingEntity performer, FriendlyByteBuf extraInput) {
		EntityActionInstance action = super.initActionOnAbilityUse(level, powerUser, performer, extraInput);
		if (shoutForNextAction != null && action instanceof TheWorldBarrage barrage) {
			barrage.shoutOnStart(powerUser, shoutForNextAction);
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
		// server only: the press shout, said once the barrage is actually set
		@Nullable private LivingEntity shoutUser;
		@Nullable private Holder<SoundEvent> pendingShout;

		public TheWorldBarrage(EntityActionType ability) {
			super(ability);
		}

		public void suppressStandCry() {
			this.suppressStandCry = true;
		}

		public boolean isStandCrySuppressed() {
			return suppressStandCry;
		}

		void shoutOnStart(LivingEntity user, Holder<SoundEvent> shout) {
			this.shoutUser = user;
			this.pendingShout = shout;
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			// 1.16 said the line only for a press that started the barrage, and a said line drops the Stand's cry;
			// set before the action is synced, so the client reads the flag
			if (pendingShout != null && shoutUser != null && !level().isClientSide()) {
				if (JojoModUtil.sayVoiceLine(shoutUser, pendingShout)) {
					suppressStandCry = true;
				}
			}
			pendingShout = null;
			shoutUser = null;
			super.onActionSet(prevAction);
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
