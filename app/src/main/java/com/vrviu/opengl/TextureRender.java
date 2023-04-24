package com.vrviu.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

public  class TextureRender {
    private static final int FLOAT_SIZE_BYTES = 4;

    private static final float[] FULL_RECTANGLE_COORDS = {
            -1.0f, -1.0f,1.0f,   // 0 bottom left
            1.0f, -1.0f,1.0f,   // 1 bottom right
            -1.0f,  1.0f,1.0f,   // 2 top left
            1.0f,  1.0f,1.0f   // 3 top right
    };

    private static final float[] FULL_RECTANGLE_TEX_COORDS = {
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
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n";
/*
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord.xy);\n" +
                    "}\n";
*/
    private static final String FRAGMENT_SHADER =
        "#extension GL_OES_EGL_image_external : require\n"+
                "precision mediump float;\n"+
                "varying vec2 vTextureCoord;\n"+
                "uniform samplerExternalOES uTexture;\n"+
                "uniform vec2 mTextureSize;\n"+
                "uniform float sharpLevel;\n"+
                "void main() {\n"+
                "    float xx = float(mTextureSize.x);\n"+
                "    float yy = float(mTextureSize.y);\n"+
                "    vec2 offset0 = vec2(-1.0 / xx, -1.0 / yy);\n"+
                "    vec2 offset1 = vec2(0.0 / xx, -1.0 / yy);\n"+
                "    vec2 offset2 = vec2(1.0 / xx, -1.0 / yy);\n"+
                "    vec2 offset3 = vec2(-1.0 / xx, 0.0 / yy);\n"+
                "    vec2 offset4 = vec2(0.0 / xx, 0.0 / yy);\n"+
                "    vec2 offset5 = vec2(1.0 / xx, 0.0 / yy);\n"+
                "    vec2 offset6 = vec2(-1.0 / xx, 1.0 / yy);\n"+
                "    vec2 offset7 = vec2(0.0 / xx, 1.0 / yy);\n"+
                "    vec2 offset8 = vec2(1.0 / xx, 1.0 / yy);\n"+
                "    vec4 cTemp0 = texture2D(uTexture, vTextureCoord.st + offset0.xy);\n"+
                "    vec4 cTemp1 = texture2D(uTexture, vTextureCoord.st + offset1.xy);\n"+
                "    vec4 cTemp2 = texture2D(uTexture, vTextureCoord.st + offset2.xy);\n"+
                "    vec4 cTemp3 = texture2D(uTexture, vTextureCoord.st + offset3.xy);\n"+
                "    vec4 cTemp4 = texture2D(uTexture, vTextureCoord.st + offset4.xy);\n"+
                "    vec4 cTemp5 = texture2D(uTexture, vTextureCoord.st + offset5.xy);\n"+
                "    vec4 cTemp6 = texture2D(uTexture, vTextureCoord.st + offset6.xy);\n"+
                "    vec4 cTemp7 = texture2D(uTexture, vTextureCoord.st + offset7.xy);\n"+
                "    vec4 cTemp8 = texture2D(uTexture, vTextureCoord.st + offset8.xy);\n"+
                "    vec4 sum = cTemp4 + (cTemp4-(cTemp0+cTemp1+cTemp1+cTemp2+cTemp3+cTemp4+cTemp4+cTemp5+cTemp3+cTemp4+cTemp4+cTemp5+cTemp6+cTemp7+cTemp7+cTemp8)/16.0)*sharpLevel;\n"+
                "    gl_FragColor = vec4(sum.r, sum.g, sum.b, 1.0);\n"+
                "}\n";

    private int mProgram;
    private int mTextureID;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;
    private int mSharpHandle;
    private int mTextureSizeHandle;
    private float mSharpLevel;
    private FloatBuffer TEXTURE_SIZE;

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    public TextureRender(int width, int height, float sharpLevel) {
        Matrix.setIdentityM(mSTMatrix, 0);

        mSharpLevel = sharpLevel;
        TEXTURE_SIZE = GlUtil.createFloatBuffer(new float[]{width,height});

        mTextureID = genTextures();
        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(mProgram!=0) {
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");

            mSharpHandle = GLES20.glGetUniformLocation(mProgram, "sharpLevel");
            mTextureSizeHandle = GLES20.glGetUniformLocation(mProgram, "mTextureSize");
        }
    }

    public int genTextures() {
        int[] texture = new int[] {0};
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
        return texture[0];
    }

    public int getTextureId() {
        return mTextureID;
    }

    public float getSharp() {
        return mSharpLevel;
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
        GLES20.glUniform1f(mSharpHandle, mSharpLevel);
        GLES20.glUniform2fv(mTextureSizeHandle, 1, TEXTURE_SIZE);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glUseProgram(0);
    }
}
