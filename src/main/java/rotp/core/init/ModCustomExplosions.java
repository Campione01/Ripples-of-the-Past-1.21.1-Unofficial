package rotp.core.init;

import java.util.HashMap;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.explosion.CustomExplosion.CustomExplosionSupplier;
import rotp.core.impl.powers.hamon.HamonBlastExplosion;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanSelfDetonationAbility;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

public class ModCustomExplosions {

	public static final ResourceLocation CROSSFIRE_HURRICANE = JojoMod.resLoc("cfh");
	public static final ResourceLocation PILLAR_MAN_DETONATION = JojoMod.resLoc("acdc");
	public static final ResourceLocation HAMON = JojoMod.resLoc("hamon");
	public static final ResourceLocation STAND_HEAVY_PUNCH = JojoMod.resLoc("heavy_punch");

	public static final HashMap<ResourceLocation, CustomExplosionSupplier> REGISTER = Util.make(new HashMap<>(), map -> {
		map.put(CROSSFIRE_HURRICANE, MRCrossfireHurricaneEntity.CrossfireHurricaneExplosion::new);
		map.put(PILLAR_MAN_DETONATION, PillarmanSelfDetonationAbility.PillarmanExplosion::new);
		map.put(HAMON, HamonBlastExplosion::new);
		map.put(STAND_HEAVY_PUNCH, StandEntityHeavyPunchAbility.HeavyPunchExplosion::new);
	});
}
