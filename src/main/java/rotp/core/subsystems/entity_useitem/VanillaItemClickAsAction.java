package rotp.core.subsystems.entity_useitem;

import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * An action for when a Stand clicks specific items, for example, when it throws a projectile,
 * to be able to do things like set the Stand's offset to the front of the user and then reset it.
 */
public class VanillaItemClickAsAction extends SpecialEntityActionType {

	public VanillaItemClickAsAction(ResourceLocation id) {
		super("", id);
		// Anim set overwrites the vanilla player poses, we don't want that
		this.animSet = null;
	}

	@Override
	public EntityActionInstance createActionObj() {
		return new ItemClickInstance(this);
	}

	public static class ItemClickInstance extends EntityActionInstance {
		protected boolean isPlayerEntity;
		protected ServerPlayer standUserPlayer;

		public ItemClickInstance() {
			this(ModSpecialActions.RMB_CLICK_ITEM.get());
			this.setStartingPhase();
		}

		protected ItemClickInstance(EntityActionType ability) {
			super(ability);
			phasesLength.put(ActionPhase.RECOVERY, 10f);
		}
		
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
		
	}
	
}
