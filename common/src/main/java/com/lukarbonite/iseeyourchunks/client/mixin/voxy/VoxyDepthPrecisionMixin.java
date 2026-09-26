package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.core.rendering.util.DepthFramebuffer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stores Voxy's LOD depth (and the Hi-Z pyramid built from it) as 32-bit float instead of 24-bit fixed point.
 *
 * <p>Voxy renders LOD with its own projection (near plane 8 or 16 blocks, reverse-Z) into a
 * {@code GL_DEPTH24_STENCIL8} buffer, then re-projects that depth into the main buffer. In reverse-Z the stored value
 * at distance {@code d} is about {@code near / d}, and a 24-bit step ({@code 2^-24}) then spans
 * {@code 2^-24 * d^2 / near} blocks: about 3 blocks at 20,000 blocks out. Terrain depth snaps to that grid, so a far
 * player standing against a block lands on the wrong side of it every few blocks. As a float, the step is relative to
 * the value, so reverse-Z holds sub-block precision at any distance this mod renders.
 *
 * <p>{@code GL_DEPTH32F_STENCIL8} keeps the stencil Voxy relies on. Only the allocation reads are swapped: every other
 * reader of the format field still sees {@code GL_DEPTH24_STENCIL8}, so Voxy keeps attaching it as depth + stencil,
 * and a framebuffer it builds "with the same format" (the Iris pipeline's translucent copy) is upgraded the same
 * way, keeping depth blits between them format-matched. {@link VoxyHiZPrecisionMixin} does the same for Hi-Z.
 */
@Mixin(DepthFramebuffer.class)
public class VoxyDepthPrecisionMixin {
	@Unique
	private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
	@Unique
	private static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;

	@WrapOperation(
		method = "resize",
		at = @At(value = "FIELD", target = "Lme/cortex/voxy/client/core/rendering/util/DepthFramebuffer;depthType:I", opcode = Opcodes.GETFIELD)
	)
	private int iSeeYourChunks$floatDepthBuffer(DepthFramebuffer framebuffer, Operation<Integer> original) {
		return upgrade(original.call(framebuffer));
	}

	@Unique
	private static int upgrade(int format) {
		return format == GL_DEPTH24_STENCIL8 ? GL_DEPTH32F_STENCIL8 : format;
	}
}
