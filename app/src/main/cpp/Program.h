#ifndef _PROGRAM_H
#define _PROGRAM_H

#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

#define  LOG_TAG    "llx"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

class Program {
public:
    Program() :
        mProgram(0),
        maPositionLoc(0),
        maTextureCoordLoc(0),
        muMVPMatrixLoc(0),
        muGLCMatrixLoc(0),
        muTextureLoc(0)
        {}
    ~Program() { release(); }

    bool setup();
    void release();

    bool drawSharp(GLuint texName, const float* texMatrix, int32_t x, int32_t y, int32_t w, int32_t h, bool invert = false) const;
    void setSharpAlpha(float sharpAlpha);

private:
    Program(const Program&);
    Program& operator=(const Program&);

    bool beforeDraw(GLuint texName, const float* texMatrix, const float* vertices, const float* texes, bool invert, int width, int height) const;
    bool afterDraw() const;

    bool createProgram(GLuint* outPgm, const char* vertexShader, const char* fragmentShader);
    static bool compileShader(GLenum shaderType, const char* src, GLuint* outShader);
    static bool linkShaderProgram(GLuint vs, GLuint fs, GLuint* outPgm);

    GLuint mProgram;

    GLint maPositionLoc;
    GLint maTextureCoordLoc;
    GLint muMVPMatrixLoc;
    GLint muGLCMatrixLoc;
    GLint muTextureLoc;
    GLint mAlphaLoc;
    GLint mTextureSizeLoc;

    float mSharpAlpha = 0.0f;
};

#endif /*_PROGRAM_H*/
