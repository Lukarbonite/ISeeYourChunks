package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import me.cortex.voxy.client.core.RenderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Turns on the 1.20.1 Voxy fork's (m3t4f1v3/voxy 0.2.15) built-in reverse-Z mode whenever {@link ReverseZ} runs the
 * world pass reversed. The fork hardcodes {@code isReverseZ = false} (and {@code isZero2One = true}, which is only correct once clip
 * control is 0..1, as it is under ReverseZ). With the flag on, every depth comparison, clear value and shader define
 * Voxy uses follows reverse-Z, including its reading of vanilla's depth to mask LOD behind real terrain and its
 * write of LOD depth into vanilla's buffer. Voxy reads these properties once when it builds its render system, which
 * is when {@link ReverseZ#decideForVoxy()} records the choice.
 */
@Mixin(RenderProperties.class)
public class VoxyReverseZMixin {
	@ModifyArg(
		method = "getRenderProperties",
		at = @At(value = "INVOKE", target = "Lme/cortex/voxy/client/core/RenderProperties;<init>(ZZZ)V", ordinal = 0),
		index = 1
	)
	private static boolean iSeeYourChunks$reverseZ(boolean isReverseZ) {
		return ReverseZ.decideForVoxy();
	}
}
