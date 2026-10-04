package com.lukarbonite.iseeyourchunks.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens up the two protected culling hooks {@link EntityRenderManagerMixin} needs to reimplement. */
@Mixin(EntityRenderer.class)
interface EntityRendererAccessor<T extends Entity> {
	@Invoker("getBoundingBoxForCulling")
	AABB iSeeYourChunks$invokeGetBoundingBoxForCulling(T entity);

	@Invoker("affectedByCulling")
	boolean iSeeYourChunks$invokeAffectedByCulling(T entity);
}
