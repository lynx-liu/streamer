package com.vrviu.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

public  class HSBRender {
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
                "uniform float uBrightness;\n" +
                "uniform float uContrast;\n" +
                "uniform float uSaturation;\n" +
                "void main() {\n"+
                "    vec4 texColor = texture2D(uTexture, vTextureCoord);\n" +
                "    texColor.rgb += uBrightness;\n" +
                "    texColor.rgb = (texColor.rgb - 0.5) * max(uContrast, 0.0) + 0.5;\n" +
                "    float luminance = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));\n" +
                "    texColor.rgb = mix(vec3(luminance), texColor.rgb, uSaturation);\n" +
                "    gl_FragColor = texColor;\n" +
                "}\n";

    private int mProgram = 0;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private int mBrightnessHandle;
    private float mBrightnessValue;
    private int mContrastHandle;
    private float mContrastValue;
    private int mSaturationHandle;
    private float mSaturationValue;

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    public HSBRender(int texture) {
        this(texture, 0.0f,1.2f,1.5f);
    }

    /*
    brightness：亮度调整参数，取值范围为[-1.0, 1.0]，其中-1.0表示将图像变暗，1.0表示将图像变亮，0.0表示不进行亮度调整。
    contrast：对比度调整参数，取值范围为[0.0, +∞)，其中0.0表示将图像变成灰色，1.0表示不进行对比度调整，大于1.0表示增强对比度。
    saturation：饱和度调整参数，取值范围为[0.0, 2.0]，其中0.0表示将图像变成灰色，1.0表示不进行饱和度调整，大于1.0表示增强饱和度，小于1.0表示降低饱和度。
    */
    public HSBRender(int texture, float brightness, float contrast, float saturation) {
        Matrix.setIdentityM(mSTMatrix, 0);

        mBrightnessValue = brightness;
        mContrastValue = contrast;
        mSaturationValue = saturation;

        bindTexture(texture);
        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(mProgram!=0) {
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");

            mBrightnessHandle = GLES20.glGetUniformLocation(mProgram, "uBrightness");
            mContrastHandle = GLES20.glGetUniformLocation(mProgram, "uContrast");
            mSaturationHandle = GLES20.glGetUniformLocation(mProgram, "uSaturation");
        }
    }

    public void bindTexture(int texture) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
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
        GLES20.glUniform1f(mBrightnessHandle, mBrightnessValue);
        GLES20.glUniform1f(mContrastHandle, mContrastValue);
        GLES20.glUniform1f(mSaturationHandle, mSaturationValue);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glUseProgram(0);
    }
}
