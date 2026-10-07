package rotp.core.gametest;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfBindingEntity;
import rotp.core.impl.powers.pillarman.PillarmanExtendingBodyPartEntity;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.init.ModEntityTypes;

/**
 * Save and reload of an attached rib / scarf binding. The donor writes the target's UUID and never reads it back
 * (its read is guarded by a key it never writes), so a reloaded part is unattached.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanAttachmentReloadGameTests {
    private PillarmanAttachmentReloadGameTests() {}

    @GameTest(template = "empty", batch = "pillarman_attachment_reload", timeoutTicks = 20)
    public static void attachedPartSavesTheTargetUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IronGolem owner = golem(helper, 1);
        IronGolem target = golem(helper, 3);
        try {
            for (PillarmanExtendingBodyPartEntity part : parts(owner, level)) {
                String name = part.getType().toShortString();
                CompoundTag free = part.saveWithoutId(new CompoundTag());
                helper.assertTrue(!free.contains("AttachedEntity"),
                        name + ": an unattached part saves no attachment, but the tag holds " + free.get("AttachedEntity"));
                part.attachToEntity(target);
                CompoundTag attached = part.saveWithoutId(new CompoundTag());
                helper.assertTrue(attached.hasUUID("AttachedEntity")
                        && attached.getUUID("AttachedEntity").equals(target.getUUID()),
                        name + ": an attached part saves its target's UUID, but the tag holds "
                                + attached.get("AttachedEntity") + " (target network id " + target.getId() + ")");
            }
        }
        finally {
            owner.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "pillarman_attachment_reload", timeoutTicks = 20)
    public static void reloadedPartIsUnattached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IronGolem owner = golem(helper, 1);
        IronGolem target = golem(helper, 3);
        try {
            for (PillarmanExtendingBodyPartEntity part : parts(owner, level)) {
                String name = part.getType().toShortString();
                int freeLife = part.ticksLifespan();
                part.attachToEntity(target);
                helper.assertTrue(part.isAttachedToAnEntity() && part.getEntityAttachedTo() == target,
                        name + ": premise, the live part is attached to its target");
                PillarmanExtendingBodyPartEntity loaded = reload(part, level, tag -> tag);
                helper.assertTrue(!loaded.isAttachedToAnEntity() && loaded.getEntityAttachedTo() == null,
                        name + ": a reloaded part is unattached, but it resolves to " + loaded.getEntityAttachedTo());
                helper.assertTrue(loaded.ticksLifespan() == freeLife,
                        name + ": a reloaded part keeps the unattached lifespan " + freeLife + ", but has "
                                + loaded.ticksLifespan());
            }
        }
        finally {
            owner.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "pillarman_attachment_reload", timeoutTicks = 20)
    public static void numericOrMissingAttachmentTagBindsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IronGolem owner = golem(helper, 1);
        IronGolem bystander = golem(helper, 3);
        try {
            for (PillarmanExtendingBodyPartEntity part : parts(owner, level)) {
                String name = part.getType().toShortString();
                // A tag from an earlier build holds a network id, which now belongs to some other entity.
                PillarmanExtendingBodyPartEntity numeric = reload(part, level, tag -> {
                    tag.putInt("AttachedEntity", bystander.getId());
                    return tag;
                });
                helper.assertTrue(!numeric.isAttachedToAnEntity() && numeric.getEntityAttachedTo() == null,
                        name + ": a saved network id binds nothing, but the part resolves to "
                                + numeric.getEntityAttachedTo());
                PillarmanExtendingBodyPartEntity missing = reload(part, level, tag -> {
                    tag.remove("AttachedEntity");
                    return tag;
                });
                helper.assertTrue(!missing.isAttachedToAnEntity() && missing.getEntityAttachedTo() == null,
                        name + ": a tag without the attachment key leaves the part unattached");
            }
        }
        finally {
            owner.discard();
            bystander.discard();
        }
        helper.succeed();
    }

    private static PillarmanExtendingBodyPartEntity[] parts(LivingEntity owner, ServerLevel level) {
        PillarmanRibEntity rib = new PillarmanRibEntity(owner, level);
        rib.setLifeSpan(21);
        return new PillarmanExtendingBodyPartEntity[] { rib, new SatiporojaScarfBindingEntity(owner, level) };
    }

    private static PillarmanExtendingBodyPartEntity reload(PillarmanExtendingBodyPartEntity part, ServerLevel level,
            Function<CompoundTag, CompoundTag> edit) {
        CompoundTag tag = edit.apply(part.saveWithoutId(new CompoundTag()));
        PillarmanExtendingBodyPartEntity loaded = (PillarmanExtendingBodyPartEntity) part.getType().create(level);
        if (loaded == null) {
            throw new IllegalStateException("entity type cannot be created: " + part.getType());
        }
        loaded.load(tag);
        return loaded;
    }

    private static IronGolem golem(GameTestHelper helper, int x) {
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(x, 2, 1));
        golem.setNoGravity(true);
        return golem;
    }
}
