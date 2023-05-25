package com.vrviu.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

public  class HSBRender {
    private static final int FLOAT_SIZE_BYTES = 4;

    private static final float[] FULL_RECTANGLE_COORDS = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f,  1.0f,   // 2 top left
            1.0f,  1.0f   // 3 top right
    };

    private static final float[] FULL_RECTANGLE_TEX_COORDS = {
            0.0f, 1.0f,    // 0 bottom left
            1.0f, 1.0f,     // 1 bottom right
            0.0f, 0.0f,    // 2 top left
            1.0f, 0.0f     // 3 top right
    };

    private static final FloatBuffer FULL_RECTANGLE_BUF = GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF = GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = aPosition;\n" +
                    "    vTextureCoord = aTextureCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
        "#extension GL_OES_EGL_image_external : require\n"+
                "precision mediump float;\n"+
                "varying vec2 vTextureCoord;\n"+
                "uniform samplerExternalOES uTexture;\n"+
                "uniform float uBrightness;\n" +
                "uniform float uContrast;\n" +
                "uniform float uSaturation;\n" +
                "void main() {\n"+
                "   vec4 texColor = texture2D(uTexture, vTextureCoord);\n" +
                "   if (vTextureCoord.y > 0.5) {\n" +
                "       texColor.rgb += uBrightness;\n" +
                "       texColor.rgb = (texColor.rgb - 0.5) * max(uContrast, 0.0) + 0.5;\n" +
                "       float luminance = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));\n" +
                "       texColor.rgb = mix(vec3(luminance), texColor.rgb, uSaturation);\n" +
                "   }\n" +
                "   gl_FragColor = texColor;\n" +
                "}\n";

    private int mProgram;
    private int mTextureID;
    private int maPositionHandle;
    private int maTextureHandle;

    private int mBrightnessHandle;
    private float mBrightnessValue;
    private int mContrastHandle;
    private float mContrastValue;
    private int mSaturationHandle;
    private float mSaturationValue;

    public HSBRender(int textureId) {
        this(textureId,0.0f,1.2f,1.2f);
    }

    /*
    brightness：亮度调整参数，取值范围为[-1.0, 1.0]，其中-1.0表示将图像变暗，1.0表示将图像变亮，0.0表示不进行亮度调整。
    contrast：对比度调整参数，取值范围为[0.0, +∞)，其中0.0表示将图像变成灰色，1.0表示不进行对比度调整，大于1.0表示增强对比度。
    saturation：饱和度调整参数，取值范围为[0.0, 2.0]，其中0.0表示将图像变成灰色，1.0表示不进行饱和度调整，大于1.0表示增强饱和度，小于1.0表示降低饱和度。
    */
    public HSBRender(int textureId, float brightness, float contrast, float saturation) {
        mBrightnessValue = brightness;
        mContrastValue = contrast;
        mSaturationValue = saturation;

        mTextureID = textureId;

        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(mProgram!=0) {
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            mBrightnessHandle = GLES20.glGetUniformLocation(mProgram, "uBrightness");
            mContrastHandle = GLES20.glGetUniformLocation(mProgram, "uContrast");
            mSaturationHandle = GLES20.glGetUniformLocation(mProgram, "uSaturation");
        }
    }

    public void release() {
        GLES20.glDeleteProgram(mProgram);
    }

    public void drawFrame() {
        GLES20.glUseProgram(mProgram);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_BUF);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_TEX_BUF);
        GLES20.glEnableVertexAttribArray(maTextureHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mTextureID);

        GLES20.glUniform1f(mBrightnessHandle, mBrightnessValue);
        GLES20.glUniform1f(mContrastHandle, mContrastValue);
        GLES20.glUniform1f(mSaturationHandle, mSaturationValue);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glUseProgram(0);
    }
}
