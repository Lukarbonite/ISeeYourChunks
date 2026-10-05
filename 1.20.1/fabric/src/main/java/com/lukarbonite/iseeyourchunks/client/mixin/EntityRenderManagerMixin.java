package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the render decision for managed entities.
 *
 * <p>Vanilla's own answer is unusable here because it folds a distance cut into the same call, and that
 * cut is exactly what the mod exists to lift. What is emphatically <em>not</em> lifted is frustum
 * culling: an entity behind the camera must still be skipped, or every revealed player on the server
 * gets submitted to the renderer every frame.
 *
 * <p>This mirrors vanilla's own {@code shouldRender} logic on 1.20.1, where the culling box comes from
 * {@link Entity#getBoundingBoxForCulling()} and the opt-out is the {@link Entity#noCulling} field (beacon
 * beams and similar), rather than the {@code EntityRenderer} hooks 26.2 uses.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderManagerMixin {
	/** Half-extent of the substitute box used when an entity reports a degenerate culling box. */
	@Unique
	private static final double ISEEYOURCHUNKS$FALLBACK_BOX_RADIUS = 2.0D;

	/** Slack added to the culling box, so an entity is not clipped the instant its edge leaves view. */
	@Unique
	private static final double ISEEYOURCHUNKS$CULL_BOX_PADDING = 0.5D;

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void iSeeYourChunks$decideForManagedEntities(
		Entity entity,
		Frustum frustum,
		double cameraX,
		double cameraY,
		double cameraZ,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!ClientEntityVisibility.isManaged(entity)) {
			return;
		}

		if (!ClientEntityVisibility.isRenderable(entity)) {
			cir.setReturnValue(false);
			return;
		}

		if (entity.noCulling) {
			// The entity opts out of culling entirely (beacon beams and similar).
			cir.setReturnValue(true);
			return;
		}

		cir.setReturnValue(frustum.isVisible(iSeeYourChunks$cullingBox(entity)));
	}

	/**
	 * Padded culling box for a managed entity.
	 *
	 * <p>A zero-size box would be culled from every angle, so a degenerate one is swapped for a small
	 * cube centred on the entity - that happens for entities that never expected to be asked at this
	 * range.
	 */
	@Unique
	private static AABB iSeeYourChunks$cullingBox(Entity entity) {
		AABB reported = entity.getBoundingBoxForCulling();
		if (!reported.hasNaN() && reported.getSize() > 0.0D) {
			return reported.inflate(ISEEYOURCHUNKS$CULL_BOX_PADDING);
		}

		double span = ISEEYOURCHUNKS$FALLBACK_BOX_RADIUS * 2.0D;
		return AABB.ofSize(entity.position(), span, span, span);
	}
}
