package rotp.addons.stickyfingers.mixin.client;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
    @Accessor("scaleHead")
    boolean stickyFingers$scaleHead();

    @Accessor("babyHeadScale")
    float stickyFingers$babyHeadScale();

    @Accessor("babyBodyScale")
    float stickyFingers$babyBodyScale();

    @Invoker("headParts")
    Iterable<ModelPart> stickyFingers$headParts();

    @Invoker("bodyParts")
    Iterable<ModelPart> stickyFingers$bodyParts();
}
