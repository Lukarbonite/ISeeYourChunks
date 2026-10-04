package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import me.cortex.voxy.client.core.RenderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Turns on Voxy 0.2.18's built-in reverse-Z mode whenever {@link ReverseZ} runs the world pass reversed. Voxy picks
 * the mode from vanilla's default depth test (standard on 26.1.2) and its 0..1 flag from the device (off on 26.1.2,
 * which never enables clip control); under ReverseZ both hold. With them on, every depth comparison, clear value and
 * shader define Voxy uses follows reverse-Z, including its reading of vanilla's depth to mask LOD behind real terrain
 * and its write of LOD depth into vanilla's buffer. Voxy reads these properties once when it builds its render system,
 * which is when {@link ReverseZ#decideForVoxy()} records the choice.
 */
@Mixin(RenderProperties.class)
public class VoxyReverseZMixin {
	@ModifyArg(
		method = "getRenderProperties",
		at = @At(value = "INVOKE", target = "Lme/cortex/voxy/client/core/RenderProperties;<init>(ZZZ)V", ordinal = 0),
		index = 0
	)
	private static boolean iSeeYourChunks$zeroToOne(boolean isZero2One) {
		return isZero2One || ReverseZ.decideForVoxy();
	}

	@ModifyArg(
		method = "getRenderProperties",
		at = @At(value = "INVOKE", target = "Lme/cortex/voxy/client/core/RenderProperties;<init>(ZZZ)V", ordinal = 0),
		index = 1
	)
	private static boolean iSeeYourChunks$reverseZ(boolean isReverseZ) {
		return ReverseZ.decideForVoxy();
	}
}
