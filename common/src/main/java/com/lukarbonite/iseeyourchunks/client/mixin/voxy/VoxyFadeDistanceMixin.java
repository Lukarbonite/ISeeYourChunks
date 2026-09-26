package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyFarNodeInjector;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.NormalRenderPipeline;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Voxy's distance fade from erasing the far terrain {@link VoxyFarNodeInjector} injects.
 *
 * <p>Voxy 0.2.19 (26.2 build) added a fade in its final composite that takes LOD to fully transparent near
 * {@code sectionRenderDistance * 512} blocks. It reads the same config field as the traversal cull, so it must see
 * the same widened value while far roots are live, or terrain past Voxy's sphere fades out. {@code require = 0}:
 * older Voxy builds (0.2.17, the 1.21.1 fork) have no such read in {@code finish}, and there this is a no-op.
 */
@Mixin(NormalRenderPipeline.class)
public class VoxyFadeDistanceMixin {
	@WrapOperation(
		method = "finish",
		at = @At(
			value = "FIELD",
			target = "Lme/cortex/voxy/client/config/VoxyConfig;sectionRenderDistance:F",
			opcode = Opcodes.GETFIELD
		),
		require = 0
	)
	private float iSeeYourChunks$effectiveFadeDistance(VoxyConfig config, Operation<Float> original) {
		return VoxyFarNodeInjector.effectiveCullSections(original.call(config));
	}
}
