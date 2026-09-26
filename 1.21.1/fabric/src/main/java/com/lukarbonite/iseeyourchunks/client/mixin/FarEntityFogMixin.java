package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.FarEntityFog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes each entity that Voxy's fog reaches through {@link FarEntityFog}, so it is drawn under the same fog as the
 * LOD terrain around it. Only entities headed for vanilla's main buffer source are routed; a glowing entity is going
 * to the outline source and keeps vanilla's path so its outline still draws.
 */
@Mixin(LevelRenderer.class)
abstract class FarEntityFogMixin {
	@Shadow
	@Final
	private RenderBuffers renderBuffers;

	@WrapOperation(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
		)
	)
	private void iSeeYourChunks$fogFarEntity(
		LevelRenderer levelRenderer, Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick,
		PoseStack poseStack, MultiBufferSource buffers, Operation<Void> original
	) {
		if (buffers == this.renderBuffers.bufferSource()) {
			float distance = FarEntityFog.fogDistance(entity, cameraX, cameraY, cameraZ, partialTick);
			float amount = FarEntityFog.fogAmount(distance);
			if (amount > 0.0F) {
				FarEntityFog.renderFogged(amount, distance, entity, () -> original.call(
					levelRenderer, entity, cameraX, cameraY, cameraZ, partialTick, poseStack, FarEntityFog.bufferSource()));
				return;
			}
		}
		original.call(levelRenderer, entity, cameraX, cameraY, cameraZ, partialTick, poseStack, buffers);
	}
}
