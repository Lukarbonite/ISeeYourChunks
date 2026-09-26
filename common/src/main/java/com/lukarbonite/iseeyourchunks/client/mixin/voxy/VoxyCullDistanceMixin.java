package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyFarNodeInjector;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Widens Voxy's traversal cull distance for the far roots {@link VoxyFarNodeInjector} injects, at the single place
 * the value becomes a shader uniform.
 *
 * <p>The traversal shader skips any node farther than {@code sectionRenderDistance * 512} blocks, which would cull
 * our injected columns past Voxy's render sphere. The cull must be lifted for them, but <em>not</em> by writing
 * {@code VoxyConfig.sectionRenderDistance}: Voxy also sizes its render ring from that field whenever it (re)builds
 * its render system (settings change, shader toggle, rejoin), and a raised value there made the ring try to
 * enumerate billions of positions and crash the client with an {@code OutOfMemoryError}. Wrapping only this
 * read keeps Voxy's config - and therefore its ring - at the user's real setting.
 */
@Mixin(HierarchicalOcclusionTraverser.class)
public class VoxyCullDistanceMixin {
	@WrapOperation(
		method = "uploadUniform",
		at = @At(
			value = "FIELD",
			target = "Lme/cortex/voxy/client/config/VoxyConfig;sectionRenderDistance:F",
			opcode = Opcodes.GETFIELD
		)
	)
	private float iSeeYourChunks$effectiveCullDistance(VoxyConfig config, Operation<Float> original) {
		return VoxyFarNodeInjector.effectiveCullSections(original.call(config));
	}
}
