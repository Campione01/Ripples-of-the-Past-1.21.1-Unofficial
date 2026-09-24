package rotp.core.impl.stands._entitybase;

import rotp.core.core.JojoMod;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 ModStandsInit.BLOCK_STAND_ENTITY ("stand_entity_block"): the plain guard StandEntity.actuallyHurt put an
 * idle Stand into for 5 ticks when a hit came from the front, whether or not the Stand had a block of its own.
 * It is not in any moveset, so the Stand keeps its place (1.16 returned no offset) and only the pose changes.
 */
public class StandEntityAutoBlockAction extends SpecialEntityActionType {
	public static final ResourceLocation ID = JojoMod.resLoc("stand_entity_block");
	private static final ActionAnimIdentifier BLOCK_ANIM = ActionAnimIdentifier.getOrCreate("block", false);

	public StandEntityAutoBlockAction(ResourceLocation id) {
		super(null, id);
	}

	// registered in ModSpecialActions so a client can decode it
	public static EntityActionType get() {
		return ModSpecialActions.STAND_ENTITY_BLOCK.get();
	}

	@Override
	public EntityActionInstance createActionObj() {
		return new AutoBlockInstance(this);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return BLOCK_ANIM;
	}

	@Override
	public ResourceLocation getEntityAnimSet(LivingEntity user) {
		// a Stand's clips come from its skin
		return null;
	}

	public static class AutoBlockInstance extends EntityActionInstance {

		public AutoBlockInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			// 1.16 StandEntityBlock.standUserWalkSpeed(0.3F)
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.3F : 1;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			// 1.16 isCancelable: a hold action with no recovery gives way to any action
			return true;
		}
	}
}
