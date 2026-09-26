package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lifts vanilla's render-distance cut for managed entities.
 *
 * <p>{@code shouldRenderAtSqrDistance} is vanilla's "too far to bother" test; for a managed entity the
 * mod's own reveal logic answers instead. No aquatic or fluid-state overrides live here anymore: a
 * revealed mob always stands in a streamed, client-loaded chunk, so its water state reads correctly from
 * that chunk with no help from us.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
	private void iSeeYourChunks$liftRenderDistanceCut(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (ClientEntityVisibility.isManaged(self)) {
			cir.setReturnValue(ClientEntityVisibility.isRenderable(self));
		}
	}
}
