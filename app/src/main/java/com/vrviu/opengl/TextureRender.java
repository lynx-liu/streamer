package com.vrviu.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

public  class TextureRender {
    private static final int FLOAT_SIZE_BYTES = 4;

    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,1.0f,   // 0 bottom left
            1.0f, -1.0f,1.0f,   // 1 bottom right
            -1.0f,  1.0f,1.0f,   // 2 top left
            1.0f,  1.0f,1.0f   // 3 top right
    };

    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 1.0f, 1f,1.0f,    // 0 bottom left
            1.0f, 1.0f,1f,1.0f,     // 1 bottom right
            0.0f, 0.0f, 1f,1.0f,    // 2 top left
            1.0f, 0.0f ,1f,1.0f     // 3 top right
    };

    private static final FloatBuffer FULL_RECTANGLE_BUF = GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF = GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec4 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = uSTMatrix * aTextureCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec4 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord.xy/vTextureCoord.z);\n" +
                    "}\n";

    private int mProgram = 0;
    private int mTextureID = -1;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    public TextureRender() {
        Matrix.setIdentityM(mSTMatrix, 0);

        mTextureID = genTextures();
        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(mProgram!=0) {
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        }
    }

    public int genTextures() {
        int[] texture = new int[] {0};
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        return texture[0];
    }

    public int getTextureId() {
        return mTextureID;
    }

    public void drawFrame() {
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 3*FLOAT_SIZE_BYTES, FULL_RECTANGLE_BUF);
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        GLES20.glVertexAttribPointer(maTextureHandle, 4, GLES20.GL_FLOAT, false, 4*FLOAT_SIZE_BYTES, FULL_RECTANGLE_TEX_BUF);

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glUseProgram(0);
    }
}
