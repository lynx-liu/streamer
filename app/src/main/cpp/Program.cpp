#include "Program.h"

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <assert.h>

#define ORIGIN_SHARP 1
#define COMMON_SHARP 2
#define SHARP_TYPE COMMON_SHARP

static const char* kVertexShader =
        "uniform mat4 uMVPMatrix;\n"
        "uniform mat4 uGLCMatrix;\n"
        "attribute vec4 aPosition;\n"
        "attribute vec4 aTextureCoord;\n"
        "varying vec2 vTextureCoord;\n"
        "void main() {\n"
        "    gl_Position = uMVPMatrix * aPosition;\n"
        "    vTextureCoord = (uGLCMatrix * aTextureCoord).xy;\n"
        "}\n";

// Trivial fragment shader for external texture.
static const char* kExtFragmentShader =
        "#extension GL_OES_EGL_image_external : require\n"
        "precision mediump float;\n"
        "varying vec2 vTextureCoord;\n"
        "uniform samplerExternalOES uTexture;\n"
        "void main() {\n"
        "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n"
        "}\n";

#if SHARP_TYPE == ORIGIN_SHARP
static const char *kExtSharpFragmentShader =
  "#extension GL_OES_EGL_image_external : require\n"
  "#extension GL_OES_EGL_image_external_essl3 : enable\n"
  "precision mediump float;\n"
  "varying vec2 vTextureCoord;\n"
  "uniform samplerExternalOES uTexture;\n"
  "uniform float alpha;\n"
  "vec4 sharpen() {\n"
  "    ivec2 TexSize = textureSize(uTexture, 0);\n"
//  "    ivec2 TexSize = ivec2(1280.0, 720.0);\n"
  "    float xx = float(TexSize.x);\n"
  "    float yy = float(TexSize.y);\n"
  "    vec2 offset0 = vec2(-1.0 / xx, -1.0 / yy);\n"
  "    vec2 offset1 = vec2(0.0 / xx, -1.0 / yy);\n"
  "    vec2 offset2 = vec2(1.0 / xx, -1.0 / yy);\n"
  "    vec2 offset3 = vec2(-1.0 / xx, 0.0 / yy);\n"
  "    vec2 offset4 = vec2(0.0 / xx, 0.0 / yy);\n"
  "    vec2 offset5 = vec2(1.0 / xx, 0.0 / yy);\n"
  "    vec2 offset6 = vec2(-1.0 / xx, 1.0 / yy);\n"
  "    vec2 offset7 = vec2(0.0 / xx, 1.0 / yy);\n"
  "    vec2 offset8 = vec2(1.0 / xx, 1.0 / yy);\n"

  "    vec4 sum;\n"
  "    vec4 cTemp0 = texture(uTexture, vTextureCoord.st + offset0.xy);\n"
  "    vec4 cTemp1 = texture(uTexture, vTextureCoord.st + offset1.xy);\n"
  "    vec4 cTemp2 = texture(uTexture, vTextureCoord.st + offset2.xy);\n"
  "    vec4 cTemp3 = texture(uTexture, vTextureCoord.st + offset3.xy);\n"
  "    vec4 cTemp4 = texture(uTexture, vTextureCoord.st + offset4.xy);\n"
  "    vec4 cTemp5 = texture(uTexture, vTextureCoord.st + offset5.xy);\n"
  "    vec4 cTemp6 = texture(uTexture, vTextureCoord.st + offset6.xy);\n"
  "    vec4 cTemp7 = texture(uTexture, vTextureCoord.st + offset7.xy);\n"
  "    vec4 cTemp8 = texture(uTexture, vTextureCoord.st + offset8.xy);\n"
  "    sum = cTemp4 + (cTemp4-(cTemp0+cTemp1+cTemp1+cTemp2+cTemp3+cTemp4+cTemp4+cTemp5+cTemp3+cTemp4+cTemp4+cTemp5+cTemp6+cTemp7+cTemp7+cTemp8)/16.0)*alpha;\n"
  "    return sum;\n"
  "}\n"
  "void main() {\n"
  "   //vec4 tex = texture( s_texture, v_texCoord );\n"
  "   //outColor = vec4(tex.rgb*1.5, 1.0);\n"
  "   vec4 tex = sharpen();\n"
//  "   tex.r = 0.0;\n"
//  "   tex.g = 1.0;\n"
//  "   tex.b = 0.0;\n"
  "   gl_FragColor = vec4(tex.r, tex.g, tex.b, 1.0);\n"
//  "   gl_FragColor = vec4(0.0, 0.5, 0.0, 1.0);\n"
  "   //color = sharpen();\n"
  "}\n";
#else
static const char *kExtSharpFragmentShader =
  "#extension GL_OES_EGL_image_external : require\n"
  "precision mediump float;\n"
  "varying vec2 vTextureCoord;\n"
  "uniform samplerExternalOES uTexture;\n"
  "uniform vec2 mTextureSize;\n"
  "uniform float alpha;\n"
  "void main() {\n"
  "    float xx = float(mTextureSize.x);\n"
  "    float yy = float(mTextureSize.y);\n"
  "    vec2 offset0 = vec2(-1.0 / xx, -1.0 / yy);\n"
  "    vec2 offset1 = vec2(0.0 / xx, -1.0 / yy);\n"
  "    vec2 offset2 = vec2(1.0 / xx, -1.0 / yy);\n"
  "    vec2 offset3 = vec2(-1.0 / xx, 0.0 / yy);\n"
  "    vec2 offset4 = vec2(0.0 / xx, 0.0 / yy);\n"
  "    vec2 offset5 = vec2(1.0 / xx, 0.0 / yy);\n"
  "    vec2 offset6 = vec2(-1.0 / xx, 1.0 / yy);\n"
  "    vec2 offset7 = vec2(0.0 / xx, 1.0 / yy);\n"
  "    vec2 offset8 = vec2(1.0 / xx, 1.0 / yy);\n"
  "    vec4 cTemp0 = texture2D(uTexture, vTextureCoord.st + offset0.xy);\n"
  "    vec4 cTemp1 = texture2D(uTexture, vTextureCoord.st + offset1.xy);\n"
  "    vec4 cTemp2 = texture2D(uTexture, vTextureCoord.st + offset2.xy);\n"
  "    vec4 cTemp3 = texture2D(uTexture, vTextureCoord.st + offset3.xy);\n"
  "    vec4 cTemp4 = texture2D(uTexture, vTextureCoord.st + offset4.xy);\n"
  "    vec4 cTemp5 = texture2D(uTexture, vTextureCoord.st + offset5.xy);\n"
  "    vec4 cTemp6 = texture2D(uTexture, vTextureCoord.st + offset6.xy);\n"
  "    vec4 cTemp7 = texture2D(uTexture, vTextureCoord.st + offset7.xy);\n"
  "    vec4 cTemp8 = texture2D(uTexture, vTextureCoord.st + offset8.xy);\n"
  "    vec4 sum = cTemp4 + (cTemp4-(cTemp0+cTemp1+cTemp1+cTemp2+cTemp3+cTemp4+cTemp4+cTemp5+cTemp3+cTemp4+cTemp4+cTemp5+cTemp6+cTemp7+cTemp7+cTemp8)/16.0)*alpha;\n"
//  "    gl_FragColor = vec4(tex.r, tex.g, tex.b, 1.0);\n"
  "    gl_FragColor = vec4(sum.r, sum.g, sum.b, 1.0);\n"
  "}\n";
#endif

// Trivial fragment shader for mundane texture.
static const char* kFragmentShader =
        "precision mediump float;\n"
        "varying vec2 vTextureCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n"
        //"    gl_FragColor = vec4(0.2, 1.0, 0.2, 1.0);\n"
        "}\n";

bool Program::setup(ProgramType type) {
    LOGI("Program::setup type=%d", type);
    bool ret = false;

    mProgramType = type;

    GLuint program;
    if (type == PROGRAM_TEXTURE_2D) {
        ret = createProgram(&program, kVertexShader, kFragmentShader);
    } else if(type == PROGRAM_EXTERNAL_TEXTURE_SHARP) {
        ret = createProgram(&program, kVertexShader, kExtSharpFragmentShader);
    } else {
        ret = createProgram(&program, kVertexShader, kExtFragmentShader);
    }
    if (!ret) {
        return ret;
    }
    assert(program != 0);

    maPositionLoc = glGetAttribLocation(program, "aPosition");
    maTextureCoordLoc = glGetAttribLocation(program, "aTextureCoord");
    muMVPMatrixLoc = glGetUniformLocation(program, "uMVPMatrix");
    muGLCMatrixLoc = glGetUniformLocation(program, "uGLCMatrix");
    muTextureLoc = glGetUniformLocation(program, "uTexture");
    mAlphaLoc = glGetUniformLocation(program, "alpha");
    mTextureSizeLoc = glGetUniformLocation(program, "mTextureSize");
    if ((maPositionLoc | maTextureCoordLoc | muMVPMatrixLoc |
            muGLCMatrixLoc | muTextureLoc) == -1) {
        LOGE("Attrib/uniform lookup failed: %#x", glGetError());
        glDeleteProgram(program);
        return false;
    }

    mProgram = program;
    return true;
}

void Program::release() {
    LOGI("Program::release");
    if (mProgram != 0) {
        glDeleteProgram(mProgram);
        mProgram = 0;
    }
}

bool Program::createProgram(GLuint* outPgm, const char* vertexShader,
        const char* fragmentShader) {
    GLuint vs, fs;

    if (!compileShader(GL_VERTEX_SHADER, vertexShader, &vs)) {
        return false;
    }

    if (!compileShader(GL_FRAGMENT_SHADER, fragmentShader, &fs)) {
        glDeleteShader(vs);
        return false;
    }

    GLuint program;
    bool ret = linkShaderProgram(vs, fs, &program);
    glDeleteShader(vs);
    glDeleteShader(fs);
    if (ret) {
        *outPgm = program;
    }
    return ret;
}

bool Program::compileShader(GLenum shaderType, const char* src,
        GLuint* outShader) {
    GLuint shader = glCreateShader(shaderType);
    if (shader == 0) {
        LOGE("glCreateShader error: %#x", glGetError());
        return false;
    }

    glShaderSource(shader, 1, &src, NULL);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        LOGE("Compile of shader type %d failed", shaderType);
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen) {
            char* buf = new char[infoLen];
            if (buf) {
                glGetShaderInfoLog(shader, infoLen, NULL, buf);
                LOGE("Compile log: %s", buf);
                delete[] buf;
            }
        }
        glDeleteShader(shader);
        return false;
    }
    *outShader = shader;
    return true;
}

bool Program::linkShaderProgram(GLuint vs, GLuint fs, GLuint* outPgm) {
    GLuint program = glCreateProgram();
    if (program == 0) {
        LOGE("glCreateProgram error: %#x", glGetError());
        return false;
    }

    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    GLint linkStatus = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
    if (linkStatus != GL_TRUE) {
        LOGE("glLinkProgram failed");
        GLint bufLength = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
        if (bufLength) {
            char* buf = new char[bufLength];
            if (buf) {
                glGetProgramInfoLog(program, bufLength, NULL, buf);
                LOGE("Link log: %s", buf);
                delete[] buf;
            }
        }
        glDeleteProgram(program);
        return false;
    }

    *outPgm = program;
    return true;
}

void Program::setSharpAlpha(float sharpAlpha) {
  mSharpAlpha = sharpAlpha;
}

bool Program::drawSharp(GLuint texName, const float* texMatrix,
                       int32_t x, int32_t y, int32_t w, int32_t h, bool invert) const {
  const float pos[] = {
    float(x),   float(y+h),
    float(x+w), float(y+h),
    float(x),   float(y),
    float(x+w), float(y),
  };

  const float uv[] = {
    0.0f, 0.0f,
    1.0f, 0.0f,
    0.0f, 1.0f,
    1.0f, 1.0f,
  };

  if (beforeDraw(texName, texMatrix, pos, uv, invert, w, h)) {
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
      return afterDraw();
  }
  return false;
}

bool Program::blit(GLuint texName, const float* texMatrix,
        int32_t x, int32_t y, int32_t w, int32_t h, bool invert) const {

    const float pos[] = {
        float(x),   float(y+h),
        float(x+w), float(y+h),
        float(x),   float(y),
        float(x+w), float(y),
    };
    const float uv[] = {
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
    };

    if (beforeDraw(texName, texMatrix, pos, uv, invert, w, h)) {
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        return afterDraw();
    }
    return false;
}

bool Program::beforeDraw(GLuint texName, const float* texMatrix,
        const float* vertices, const float* texes, bool invert, int width, int height) const {
    // Create an orthographic projection matrix based on viewport size.
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    float screenToNdc[16] = {
        2.0f/float(vp[2]),  0.0f,               0.0f,   0.0f,
        0.0f,               -2.0f/float(vp[3]), 0.0f,   0.0f,
        0.0f,               0.0f,               1.0f,   0.0f,
        -1.0f,              1.0f,               0.0f,   1.0f,
    };
    if (invert) {
        screenToNdc[5] = -screenToNdc[5];
        screenToNdc[13] = -screenToNdc[13];
    }

    glUseProgram(mProgram);

    glVertexAttribPointer(maPositionLoc, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glVertexAttribPointer(maTextureCoordLoc, 2, GL_FLOAT, GL_FALSE, 0, texes);
    glEnableVertexAttribArray(maPositionLoc);
    glEnableVertexAttribArray(maTextureCoordLoc);

    glUniformMatrix4fv(muMVPMatrixLoc, 1, GL_FALSE, screenToNdc);
    glUniformMatrix4fv(muGLCMatrixLoc, 1, GL_FALSE, texMatrix);

    glActiveTexture(GL_TEXTURE0);

    switch (mProgramType) {
    case PROGRAM_EXTERNAL_TEXTURE:
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, texName);
        break;
    case PROGRAM_TEXTURE_2D:
        glBindTexture(GL_TEXTURE_2D, texName);
        break;
    case  PROGRAM_EXTERNAL_TEXTURE_SHARP:
      glBindTexture(GL_TEXTURE_EXTERNAL_OES, texName);
      break;
    default:
        LOGE("unexpected program type %d", mProgramType);
        return false;
    }

    glUniform1i(muTextureLoc, 0);

    //only sharp use
    if(mAlphaLoc >= 0) {
      glUniform1f(mAlphaLoc, mSharpAlpha);
    }
    if(mTextureSizeLoc >= 0) {
      float size[] = { width * 1.0f, height * 1.0f};
      glUniform2fv(mTextureSizeLoc, 1, size);
    }

    GLenum glErr;
    if ((glErr = glGetError()) != GL_NO_ERROR) {
        LOGE("GL error before draw: %#x", glErr);
        glDisableVertexAttribArray(maPositionLoc);
        glDisableVertexAttribArray(maTextureCoordLoc);
        return false;
    }
    return true;
}

bool Program::afterDraw() const {
    glDisableVertexAttribArray(maPositionLoc);
    glDisableVertexAttribArray(maTextureCoordLoc);

    GLenum glErr;
    if ((glErr = glGetError()) != GL_NO_ERROR) {
        LOGE("GL error after draw: %#x", glErr);
        return false;
    }
    return true;
}
