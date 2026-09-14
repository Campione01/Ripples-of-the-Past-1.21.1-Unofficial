package rotp.core.mixin.attributes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.entityattachment.DataEventListeners;
import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;

@Mixin(Entity.class)
public class EntityMixin {

	@Inject(method = "load", at = @At(
			value = "INVOKE", 
			target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", 
			shift = At.Shift.AFTER))
	public void jojo_ripples$afterDeserialization(CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		AttachmentType<DataEventListeners> key = ModDataAttachmentTypes.DATA_EVENT_HELPER.get();
		if (entity.hasData(key)) {
			entity.getData(key).afterNbtRead();
		}
	}
}
