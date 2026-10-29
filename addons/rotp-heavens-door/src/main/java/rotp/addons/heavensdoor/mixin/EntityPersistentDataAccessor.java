package rotp.addons.heavensdoor.mixin;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityPersistentDataAccessor {
    @Nullable
    @Accessor(value = "persistentData", remap = false)
    CompoundTag heavensDoor$existingPersistentData();
}
