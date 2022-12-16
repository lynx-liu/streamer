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
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n"+//给出默认的浮点精度
            "varying vec2 vTextureCoord;\n"+//从顶点着色器传递过来的纹理坐标
            "uniform samplerExternalOES sTexture;\n"+//纹理内容数据
            "void main() {\n"+//给出卷积内核中各个元素对应像素相对于待处理像素的纹理坐标偏移量
            "   vec2 offset0=vec2(-1.0,-1.0); vec2 offset1=vec2(0.0,-1.0); vec2 offset2=vec2(1.0,-1.0);\n"+
            "   vec2 offset3=vec2(-1.0,0.0); vec2 offset4=vec2(0.0,0.0); vec2 offset5=vec2(1.0,0.0);\n"+
            "   vec2 offset6=vec2(-1.0,1.0); vec2 offset7=vec2(0.0,1.0); vec2 offset8=vec2(1.0,1.0);\n"+
	        "   const float scaleFactor =0.6;\n"+//给出最终求和时的加权因子(为调整亮度)
            //卷积内核中各个位置的值
            "   float kernelValue0 = 0.0; float kernelValue1 = -1.0; float kernelValue2 = 0.0;\n"+
            "   float kernelValue3 = -1.0; float kernelValue4 = 5.0; float kernelValue5 = -1.0;\n"+
            "   float kernelValue6 = 0.0; float kernelValue7 = -1.0; float kernelValue8 = 0.0;\n"+
            "   vec4 sum;\n"+//最终的颜色和
            //获取卷积内核中各个元素对应像素的颜色值
            "   vec4 cTemp0,cTemp1,cTemp2,cTemp3,cTemp4,cTemp5,cTemp6,cTemp7,cTemp8;\n"+
            "   cTemp0=texture2D(sTexture, vTextureCoord.st + offset0.xy/512.0);\n"+
            "   cTemp1=texture2D(sTexture, vTextureCoord.st + offset1.xy/512.0);\n"+
            "   cTemp2=texture2D(sTexture, vTextureCoord.st + offset2.xy/512.0);\n"+
            "   cTemp3=texture2D(sTexture, vTextureCoord.st + offset3.xy/512.0);\n"+
            "   cTemp4=texture2D(sTexture, vTextureCoord.st + offset4.xy/512.0);\n"+
            "   cTemp5=texture2D(sTexture, vTextureCoord.st + offset5.xy/512.0);\n"+
            "   cTemp6=texture2D(sTexture, vTextureCoord.st + offset6.xy/512.0);\n"+
            "   cTemp7=texture2D(sTexture, vTextureCoord.st + offset7.xy/512.0);\n"+
            "   cTemp8=texture2D(sTexture, vTextureCoord.st + offset8.xy/512.0);\n"+
            //颜色求和
            "   sum =kernelValue0*cTemp0+kernelValue1*cTemp1+kernelValue2*cTemp2+kernelValue3*cTemp3+kernelValue4*cTemp4+kernelValue5*cTemp5+kernelValue6*cTemp6+kernelValue7*cTemp7+kernelValue8*cTemp8;\n"+
            "   gl_FragColor = sum * scaleFactor;\n"+ //进行亮度加权后将最终颜色传递给管线
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
