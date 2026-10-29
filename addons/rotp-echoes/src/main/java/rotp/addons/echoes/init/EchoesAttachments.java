package rotp.addons.echoes.init;

import java.util.function.Supplier;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesFreezeState;
import rotp.addons.echoes.state.EchoesUserState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class EchoesAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES,
                    RotpEchoes.MOD_ID);

    public static final Supplier<AttachmentType<EchoesUserState>> USER_STATE =
            ATTACHMENT_TYPES.register(
                    "user_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof Player
                                            ? new EchoesUserState()
                                            : null)
                            .build());
    public static final Supplier<AttachmentType<EchoesFreezeState>>
            FREEZE_STATE = ATTACHMENT_TYPES.register(
                    "freeze_state",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new EchoesFreezeState()
                                            : null)
                            .build());

    private EchoesAttachments() {}
}
