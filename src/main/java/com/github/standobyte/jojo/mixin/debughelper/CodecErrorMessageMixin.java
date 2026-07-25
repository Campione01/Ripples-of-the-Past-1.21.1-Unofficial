package com.github.standobyte.jojo.mixin.debughelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.IdDispatchCodec;

@Mixin(IdDispatchCodec.class)
public class CodecErrorMessageMixin {

	@Inject(method = "encode(Lio/netty/buffer/ByteBuf;Ljava/lang/Object;)V", at = @At(
			value = "INVOKE",
			target = "Lio/netty/handler/codec/EncoderException;<init>(Ljava/lang/String;Ljava/lang/Throwable;)V"))
	private <B extends ByteBuf, V> void killItWithTheFire_onEncodeError(B buffer, V value, CallbackInfo ci, @Local Exception exception) throws Exception {
		throw exception;
	}

	@Inject(method = "decode(Lio/netty/buffer/ByteBuf;)Ljava/lang/Object;", at = @At(
			value = "INVOKE",
			target = "Lio/netty/handler/codec/DecoderException;<init>(Ljava/lang/String;Ljava/lang/Throwable;)V"))
	private <B extends ByteBuf, V> void killItWithTheFire_onDecodeError(B buffer, CallbackInfoReturnable<V> ci, @Local Exception exception) throws Exception {
		throw exception;
	}
}
