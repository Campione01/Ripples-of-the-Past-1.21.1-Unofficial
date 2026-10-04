package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.init.ModEntityTypes;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismEyeBeamDataGameTests {
    private VampirismEyeBeamDataGameTests() {}

    @GameTest(template = "empty", batch = "srse_retraction_data", timeoutTicks = 20)
    public static void retractionStateRoundTripsAndLegacyNbtClearsIt(GameTestHelper helper) {
        // Registered factories provide detached data objects; no entity is added or ticked.
        List<SpaceRipperStingyEyesEntity> entities = new ArrayList<>();
        try {
            SpaceRipperStingyEyesEntity source = create(helper, entities);
            SpaceRipperStingyEyesEntity nbtCopy = create(helper, entities);
            SpaceRipperStingyEyesEntity spawnCopy = create(helper, entities);
            SpaceRipperStingyEyesEntity metadataCopy = create(helper, entities);
            assertState(helper, source, false, 0.0F, true, false, "initial");

            for (boolean bound : new boolean[] { false, true }) {
                float length = bound ? 5.5F : 3.25F;
                CompoundTag fixture = source.saveWithoutId(new CompoundTag());
                fixture.putBoolean("IsRetracting", true);
                fixture.putFloat("Length", length);
                fixture.putBoolean("BoundToOwner", bound);
                fixture.putBoolean("IsRightEye", bound);
                source.load(fixture);
                assertState(helper, source, true, length, bound, bound, "producer");

                nbtCopy.load(source.saveWithoutId(new CompoundTag()));
                assertState(helper, nbtCopy, true, length, bound, bound, "NBT");
                transferSpawn(helper, source, spawnCopy);
                assertState(helper, spawnCopy, true, length, bound, bound, "custom-spawn");
                transferMetadata(helper, source, metadataCopy);
                // Eye side is custom spawn/NBT state, not a synced metadata field.
                assertState(helper, metadataCopy, true, length, bound, false, "metadata");
            }

            CompoundTag legacy = source.saveWithoutId(new CompoundTag());
            legacy.remove("IsRetracting");
            source.load(legacy);
            assertState(helper, source, false, 5.5F, true, true, "legacy-producer");
            nbtCopy.load(legacy);
            assertState(helper, nbtCopy, false, 5.5F, true, true, "legacy-NBT-clear");
            transferSpawn(helper, source, spawnCopy);
            assertState(helper, spawnCopy, false, 5.5F, true, true, "custom-spawn-clear");
            transferMetadata(helper, source, metadataCopy);
            assertState(helper, metadataCopy, false, 5.5F, true, false, "metadata-clear");
            JojoMod.LOGGER.info("SRSE-DATA result NBT=true customSpawn=true stockMetadata=true legacyClear=true worldAdded=0");
            helper.succeed();
        }
        finally {
            for (SpaceRipperStingyEyesEntity entity : entities) entity.discard();
        }
    }

    private static SpaceRipperStingyEyesEntity create(GameTestHelper helper, List<SpaceRipperStingyEyesEntity> entities) {
        SpaceRipperStingyEyesEntity entity = ModEntityTypes.SPACE_RIPPER_STINGY_EYES.get().create(helper.getLevel());
        helper.assertTrue(entity != null, "Registered SRSE factory did not create a data object");
        entities.add(entity);
        return entity;
    }

    private static void assertState(GameTestHelper helper, SpaceRipperStingyEyesEntity entity,
            boolean retracting, float length, boolean bound, boolean right, String channel) {
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.contains("IsRetracting") && saved.getBoolean("IsRetracting") == retracting
                        && entity.getLength() == length && saved.getFloat("Length") == length
                        && entity.isBoundToOwner() == bound && saved.getBoolean("BoundToOwner") == bound
                        && saved.getBoolean("IsRightEye") == right,
                "SRSE data mismatch in " + channel);
        JojoMod.LOGGER.info("SRSE-DATA channel={} retracting={} length={} bound={} right={}", channel, retracting, length, bound, right);
    }

    private static void transferSpawn(GameTestHelper helper, SpaceRipperStingyEyesEntity source, SpaceRipperStingyEyesEntity receiver) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            source.writeSpawnData(buffer);
            receiver.readSpawnData(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "SRSE custom spawn reader left unread bytes");
        }
        finally {
            buffer.release();
        }
    }

    private static void transferMetadata(GameTestHelper helper, SpaceRipperStingyEyesEntity source, SpaceRipperStingyEyesEntity receiver) {
        var dirty = source.getEntityData().packDirty();
        helper.assertTrue(dirty != null && !dirty.isEmpty(), "SRSE state change produced no metadata update");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(source.getId(), dirty));
            ClientboundSetEntityDataPacket decoded = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.id() == source.getId() && buffer.readableBytes() == 0,
                    "SRSE stock metadata packet did not round trip completely");
            receiver.getEntityData().assignValues(decoded.packedItems());
        }
        finally {
            buffer.release();
        }
    }
}
