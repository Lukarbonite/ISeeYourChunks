package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports a section as visible when a managed entity stands in it, even with no compiled mesh there.
 *
 * <p>This is load-bearing, and its necessity was established by testing rather than assumed: with it
 * disabled, distant players disappear completely. The cause is that far terrain is drawn by <em>Voxy</em>
 * rather than Sodium. Those sections lie past render distance, so Sodium never compiles them, and
 * vanilla's entity culling - which asks exactly this method - concludes the section is not visible and
 * culls the entity standing on ground the viewer can plainly see.
 *
 * <p>Injected at RETURN and only consulted when vanilla already said no, so the fast path is untouched
 * and the index lookup only happens for sections vanilla was going to reject anyway.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Inject(method = "isSectionCompiledAndVisible", at = @At("RETURN"), cancellable = true)
	private void iSeeYourChunks$allowSectionsDrawnByFarRenderer(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && ClientEntityVisibility.hasRenderableEntityAt(level, pos)) {
			cir.setReturnValue(true);
		}
	}
}
