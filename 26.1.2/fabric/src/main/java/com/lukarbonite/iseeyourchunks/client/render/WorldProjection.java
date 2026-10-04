package com.lukarbonite.iseeyourchunks.client.render;

import org.joml.Matrix4f;

/**
 * Implemented on {@code Projection} (ProjectionMixin) so the camera can mark its own projection as the world
 * projection, which {@link ReverseZ} reverses for the whole frame rather than only inside {@code renderLevel}.
 */
public interface WorldProjection {
	void iSeeYourChunks$markWorldProjection();

	/** This projection's matrix in vanilla's standard depth, whatever mode it is currently built in; into {@code dest}. */
	Matrix4f iSeeYourChunks$standardMatrix(Matrix4f dest);
}
