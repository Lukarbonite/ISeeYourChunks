package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.core.rendering.util.HiZBuffer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Builds Voxy's Hi-Z occlusion pyramid as 32-bit float depth, matching the LOD depth buffer it is reduced from
 * (see {@link VoxyDepthPrecisionMixin}), so occlusion tests far out are not quantised to multi-block steps.
 */
@Mixin(HiZBuffer.class)
public class VoxyHiZPrecisionMixin {
	@Unique
	private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
	@Unique
	private static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;

	@WrapOperation(
		method = "alloc",
		at = @At(value = "FIELD", target = "Lme/cortex/voxy/client/core/rendering/util/HiZBuffer;type:I", opcode = Opcodes.GETFIELD)
	)
	private int iSeeYourChunks$floatHiZ(HiZBuffer hiZ, Operation<Integer> original) {
		int format = original.call(hiZ);
		return format == GL_DEPTH24_STENCIL8 ? GL_DEPTH32F_STENCIL8 : format;
	}
}
