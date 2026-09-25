package rotp.core.mixin.stand.magiciansred;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

// Magician's Red lights furnaces (1.16 CommonReflection furnace lit time).
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityLitAccessor {
	@Accessor("litTime")
	int jojo_ripples$getLitTime();

	@Accessor("litTime")
	void jojo_ripples$setLitTime(int litTime);

	@Accessor("litDuration")
	int jojo_ripples$getLitDuration();

	@Accessor("litDuration")
	void jojo_ripples$setLitDuration(int litDuration);
}
