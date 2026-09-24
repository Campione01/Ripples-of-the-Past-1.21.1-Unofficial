package rotp.core.init;

import java.util.function.Supplier;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;
import rotp.core.subsystems.entity_useitem.VanillaItemClickAsAction;
import rotp.core.subsystems.entity_useitem.VanillaItemUseAsAction;
import rotp.core.impl.stands._entitybase.StandEntityAutoBlockAction;
import rotp.core.impl.stands._entitybase.StandEntityUnsummonAction;

import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSpecialActions {
	public static final DeferredRegister<SpecialEntityActionType> ACTIONS = DeferredRegister.create(JojoRegistries.NON_POWER_ACTIONS_REG, JojoMod.MOD_ID);
	
	public static final Supplier<SpecialEntityActionType> STAND_UNSUMMON = ACTIONS.register("stand_unsummon", 
			StandEntityUnsummonAction::new);
	
	public static final Supplier<SpecialEntityActionType> RMB_USING_ITEM = ACTIONS.register("rmb_using_item", 
			key -> new VanillaItemUseAsAction(key));
	
	public static final Supplier<SpecialEntityActionType> RMB_CLICK_ITEM = ACTIONS.register("rmb_click_item", 
			key -> new VanillaItemClickAsAction(key));
	
	// 1.16 ModStandsInit.BLOCK_STAND_ENTITY, the guard of an idle Stand hit from the front
	public static final Supplier<SpecialEntityActionType> STAND_ENTITY_BLOCK = ACTIONS.register("stand_entity_block", 
			StandEntityAutoBlockAction::new);
	
}
