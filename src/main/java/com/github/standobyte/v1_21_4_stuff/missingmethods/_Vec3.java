package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public class _Vec3 {

    public static final StreamCodec<FriendlyByteBuf, Vec3> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, Vec3>() {
        public Vec3 decode(FriendlyByteBuf p_361466_) {
            return p_361466_.readVec3();
        }

        public void encode(FriendlyByteBuf p_364962_, Vec3 p_364468_) {
        	p_364962_.writeVec3(p_364468_);
        }
    };
}
