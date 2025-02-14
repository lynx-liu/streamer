package com.vrviu.opengl.gltext;

import android.opengl.GLES20;

public class Program {
	
	private int programHandle;
	private int vertexShaderHandle;
	private int fragmentShaderHandle;
	private boolean mInitialized;

	private static final String vertexShaderCode =
			"uniform mat4 u_MVPMatrix[24];      \n"     // An array representing the combined
					// model/view/projection matrices for each sprite

					+ "attribute float a_MVPMatrixIndex; \n"	// The index of the MVPMatrix of the particular sprite
					+ "attribute vec4 a_Position;     \n"     // Per-vertex position information we will pass in.
					+ "attribute vec2 a_TexCoordinate;\n"     // Per-vertex texture coordinate information we will pass in
					+ "varying vec2 v_TexCoordinate;  \n"   // This will be passed into the fragment shader.
					+ "void main()                    \n"     // The entry point for our vertex shader.
					+ "{                              \n"
					+ "   int mvpMatrixIndex = int(a_MVPMatrixIndex); \n"
					+ "   v_TexCoordinate = a_TexCoordinate; \n"
					+ "   gl_Position = u_MVPMatrix[mvpMatrixIndex]   \n"     // gl_Position is a special variable used to store the final position.
					+ "               * a_Position;   \n"     // Multiply the vertex by the matrix to get the final point in
					// normalized screen coordinates.
					+ "}                              \n";


	private static final String fragmentShaderCode =
			"uniform sampler2D u_Texture;       \n"    // The input texture.
					+	"precision mediump float;       \n"     // Set the default precision to medium. We don't need as high of a
					// precision in the fragment shader.
					+ "uniform vec4 u_Color;          \n"
					+ "varying vec2 v_TexCoordinate;  \n" // Interpolated texture coordinate per fragment.

					+ "void main()                    \n"     // The entry point for our fragment shader.
					+ "{                              \n"
					+ "   gl_FragColor = texture2D(u_Texture, v_TexCoordinate).w * u_Color;\n" // texture is grayscale so take only grayscale value from
					// it when computing color output (otherwise font is always black)
					+ "}                             \n";

	private static final AttribVariable[] programVariables = {
			AttribVariable.A_Position, AttribVariable.A_TexCoordinate, AttribVariable.A_MVPMatrixIndex
	};

	public Program() {
		mInitialized = false;
	}

	public void init() {
		init(vertexShaderCode, fragmentShaderCode, programVariables);
	}

	public void init(String vertexShaderCode, String fragmentShaderCode, AttribVariable[] programVariables) {
		vertexShaderHandle = Utilities.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
		fragmentShaderHandle = Utilities.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
		
		programHandle = Utilities.createProgram(
					vertexShaderHandle, fragmentShaderHandle, programVariables);
		
		mInitialized = true;
	}
	
	public int getHandle() {
		return programHandle;
	}
	
	public void delete() {
		GLES20.glDeleteShader(vertexShaderHandle);
		GLES20.glDeleteShader(fragmentShaderHandle);
		GLES20.glDeleteProgram(programHandle);
		mInitialized = false;
	}
	
	public boolean initialized() {
		return mInitialized;
	}
}