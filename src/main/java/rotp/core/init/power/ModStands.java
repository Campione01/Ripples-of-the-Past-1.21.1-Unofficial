package rotp.core.init.power;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.SimpleTagKey;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.type.NoSummonStandType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.stands.StandInitCrazyDiamond;
import rotp.core.impl.stands.StandInitHierophantGreen;
import rotp.core.impl.stands.StandInitBoyIIMan;
import rotp.core.impl.stands.StandInitGoldExperience;
import rotp.core.impl.stands.StandInitMagiciansRed;
import rotp.core.impl.stands.StandInitMrPresident;
import rotp.core.impl.stands.StandInitSilverChariot;
import rotp.core.impl.stands.StandInitStarPlatinum;
import rotp.core.impl.stands.StandInitTheWorld;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// XXX (data-driven stands) test the stand datapack configs
// XXX add a way to ban hardcoded stands
public class ModStands {
	public static final DeferredRegister<StandType> DEFAULT_STANDS = DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, JojoMod.MOD_ID);
	
	public static InputUseVanillaMapping USE_SPECIAL = new InputUseVanillaMapping("jojo_ripples.key.use_special_ability");
	public static InputUseVanillaMapping SWITCH_SPECIAL = new InputUseVanillaMapping("jojo_ripples.key.ability_hotbar");
	
	
	public static final SimpleTagKey<StandType> PLAYER_CAN_GET_FROM_ARROW = SimpleTagKey.create(StandType.class, JojoMod.resLoc("player_can_get_from_arrow"));
	
	// Adding all the abilities and skills takes quite a few lines, so I decided to put each into a separate file.
	// Makes it a bit easier to compare them between each other too.
	
	public static final DeferredHolder<StandType, EntityStandType> STAR_PLATINUM = DEFAULT_STANDS.register("star_platinum", StandInitStarPlatinum::create);
	public static final DeferredHolder<StandType, EntityStandType> CRAZY_DIAMOND = DEFAULT_STANDS.register("crazy_diamond", StandInitCrazyDiamond::create);
	public static final DeferredHolder<StandType, EntityStandType> HIEROPHANT_GREEN = DEFAULT_STANDS.register("hierophant_green", StandInitHierophantGreen::create);
	public static final DeferredHolder<StandType, EntityStandType> MAGICIANS_RED = DEFAULT_STANDS.register("magicians_red", StandInitMagiciansRed::create);
	public static final DeferredHolder<StandType, EntityStandType> SILVER_CHARIOT = DEFAULT_STANDS.register("silver_chariot", StandInitSilverChariot::create);
	public static final DeferredHolder<StandType, EntityStandType> THE_WORLD = DEFAULT_STANDS.register("the_world", StandInitTheWorld::create);
	public static final DeferredHolder<StandType, EntityStandType> GOLD_EXPERIENCE = DEFAULT_STANDS.register("gold_experience", StandInitGoldExperience::create);
	public static final DeferredHolder<StandType, NoSummonStandType> BOY_II_MAN = DEFAULT_STANDS.register("boy_ii_man", StandInitBoyIIMan::create);
	public static final DeferredHolder<StandType, NoSummonStandType> MR_PRESIDENT = DEFAULT_STANDS.register("mr_president", StandInitMrPresident::create);

	static {
		PLAYER_CAN_GET_FROM_ARROW.add(STAR_PLATINUM);
		PLAYER_CAN_GET_FROM_ARROW.add(CRAZY_DIAMOND);
		PLAYER_CAN_GET_FROM_ARROW.add(HIEROPHANT_GREEN);
		PLAYER_CAN_GET_FROM_ARROW.add(MAGICIANS_RED);
		PLAYER_CAN_GET_FROM_ARROW.add(SILVER_CHARIOT);
		//PLAYER_CAN_GET_FROM_ARROW.add(THE_WORLD); // 5c user decision: NO — legacy villain stand, special path acquisition, not arrow-pool
		PLAYER_CAN_GET_FROM_ARROW.add(GOLD_EXPERIENCE);
		//PLAYER_CAN_GET_FROM_ARROW.add(BOY_II_MAN); // 5c user decision: NO — legacy NPC-only, hooked to RPS Kid encounter, not arrow-pool
	}
}
