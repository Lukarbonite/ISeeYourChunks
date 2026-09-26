package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.core.gl.GlTexture;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Teaches Voxy's texture bookkeeping the {@code GL_DEPTH32F_STENCIL8} format that {@link VoxyDepthPrecisionMixin} and
 * {@link VoxyHiZPrecisionMixin} allocate. {@code GlTexture} looks the format up in two hardcoded tables that only list
 * {@code GL_DEPTH24_STENCIL8} and throw on anything else ("Unknown element size" from the memory estimate on every
 * allocation). Both lookups are answered as for {@code GL_DEPTH24_STENCIL8}: the pixel transfer format is
 * {@code GL_DEPTH_STENCIL} for either, and the size is only Voxy's memory statistic. {@code zero()} already knows the
 * float format and picks its correct clear type itself.
 */
@Mixin(GlTexture.class)
public class VoxyTextureFormatMixin {
	@Unique
	private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
	@Unique
	private static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;

	@WrapOperation(
		method = {"getEstimatedSize", "getPixelTransferFormat"},
		at = @At(value = "FIELD", target = "Lme/cortex/voxy/client/core/gl/GlTexture;format:I", opcode = Opcodes.GETFIELD)
	)
	private int iSeeYourChunks$knownDepthFormat(GlTexture texture, Operation<Integer> original) {
		int format = original.call(texture);
		return format == GL_DEPTH32F_STENCIL8 ? GL_DEPTH24_STENCIL8 : format;
	}
}
