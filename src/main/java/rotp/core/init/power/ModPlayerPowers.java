package rotp.core.init.power;

import java.util.function.Supplier;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.zombie.ZombiePowerType;

import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPlayerPowers {
	public static final DeferredRegister<PlayerPowerType<?>> PLAYER_POWERS = DeferredRegister.create(JojoRegistries.PLAYER_POWER_TYPES_REG, JojoMod.MOD_ID);
	
	public static final Supplier<HamonPowerType> HAMON = HamonPowerType.HAMON;
	public static final Supplier<VampirismPowerType> VAMPIRISM = VampirismPowerType.VAMPIRISM;
	public static final Supplier<ZombiePowerType> ZOMBIE = ZombiePowerType.ZOMBIE;
	public static final Supplier<PillarmanPowerType> PILLAR_MAN = PillarmanPowerType.PILLAR_MAN;
}
