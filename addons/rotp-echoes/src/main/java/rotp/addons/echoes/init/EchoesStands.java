package rotp.addons.echoes.init;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.stand.EchoesAct1Stand;
import rotp.addons.echoes.stand.EchoesAct2Stand;
import rotp.addons.echoes.stand.EchoesAct3Stand;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesStands {
    public static final DeferredRegister<StandType> STANDS =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, RotpEchoes.MOD_ID);

    public static final DeferredHolder<StandType, EntityStandType> ECHOES_ACT1 =
            STANDS.register("echoes_act1", EchoesAct1Stand::create);
    public static final DeferredHolder<StandType, EntityStandType> ECHOES_ACT2 =
            STANDS.register("echoes_act2", EchoesAct2Stand::create);
    public static final DeferredHolder<StandType, EntityStandType> ECHOES_ACT3 =
            STANDS.register("echoes_act3", EchoesAct3Stand::create);

    private EchoesStands() {}
}
