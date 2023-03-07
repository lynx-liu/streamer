package com.vrviu.opengl.gltext;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

public class TextRenderer {
	private GLText glText;                             // A GLText Instance
	private float[] mProjMatrix = new float[16];
	private float[] mVMatrix = new float[16];
	private float[] mVPMatrix = new float[16];

	public TextRenderer(Context context, int width, int height)  {
		glText = new GLText(context.getAssets());
		glText.load( "Roboto-Regular.ttf", width/20, 2, 2 );

		GLES20.glViewport(0, 0, width, height);
		float ratio = (float) width / height;

		// Take into account device orientation
		if (width > height) {
			Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 1, 10);
		}
		else {
			Matrix.frustumM(mProjMatrix, 0, -1, 1, -1/ratio, 1/ratio, 1, 10);
		}

		int useForOrtho = Math.min(width, height);
		Matrix.orthoM(mVMatrix, 0,
				-useForOrtho/2,
				useForOrtho/2,
				-useForOrtho/2,
				useForOrtho/2, 0.1f, 100f);
	}

	public void drawText(String text) {
		// enable texture + alpha blending
		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		Matrix.multiplyMM(mVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

		// TEST: render some strings with the font
		glText.begin( 1.0f, 0.0f, 1.0f, 1.0f, mVPMatrix );         // Begin Text Rendering (Set Color WHITE)
		glText.draw(text, 0, 0, 0);                // Draw Test String
		glText.end();                                   // End Text Rendering
	}
}
