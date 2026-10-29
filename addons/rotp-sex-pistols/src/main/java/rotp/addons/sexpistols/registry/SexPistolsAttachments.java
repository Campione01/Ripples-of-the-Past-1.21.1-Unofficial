package rotp.addons.sexpistols.registry;

import java.util.function.Supplier;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SexPistolsAttachments {
    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES,
                    SexPistolsIds.MOD_ID);

    public static final Supplier<AttachmentType<SexPistolsCoordinator>>
            SEX_PISTOLS_COORDINATOR = ATTACHMENT_TYPES.register(
                    "sex_pistols_coordinator",
                    () -> AttachmentType.serializable(
                                    holder -> holder instanceof LivingEntity
                                            ? new SexPistolsCoordinator()
                                            : null)
                            .sync(SexPistolsCoordinator.STREAM_CODEC)
                            .build());

    private SexPistolsAttachments() {}

    public static SexPistolsCoordinator coordinator(LivingEntity owner) {
        return owner.getData(SEX_PISTOLS_COORDINATOR);
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
