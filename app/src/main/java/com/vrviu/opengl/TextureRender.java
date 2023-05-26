package com.vrviu.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

public  class TextureRender {
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
                "uniform vec2 mTextureSize;\n"+
                "uniform float sharpLevel;\n"+
                "void main() {\n"+
                "  if (vTextureCoord.x > 0.5) {\n" +
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
                "  } else {\n" +
                "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
                "  }\n" +
                "}\n";

    private int mProgram;
    private int mTextureID;
    private int maPositionHandle;
    private int maTextureHandle;
    private int mSharpHandle;
    private int mTextureSizeHandle;
    private float mSharpLevel;
    private FloatBuffer TEXTURE_SIZE;

    public TextureRender(int texture, int width, int height, float sharpLevel) {
        mTextureID = texture;
        mSharpLevel = sharpLevel;
        TEXTURE_SIZE = GlUtil.createFloatBuffer(new float[]{width,height});

        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(mProgram!=0) {
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            mSharpHandle = GLES20.glGetUniformLocation(mProgram, "sharpLevel");
            mTextureSizeHandle = GLES20.glGetUniformLocation(mProgram, "mTextureSize");
        }
    }

    public void release() {
        GLES20.glDeleteProgram(mProgram);
    }

    public void drawFrame() {
        GLES20.glUseProgram(mProgram);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_BUF);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_TEX_BUF);
        GLES20.glEnableVertexAttribArray(maTextureHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mTextureID);

        GLES20.glUniform1f(mSharpHandle, mSharpLevel);
        GLES20.glUniform2fv(mTextureSizeHandle, 1, TEXTURE_SIZE);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glUseProgram(0);
    }
}
