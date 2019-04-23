/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gles;

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.lk.openstreamer.log.Logger;

import java.nio.FloatBuffer;

/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {
    // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
    // the lower-right half will have the filter applied, and a thin red line will be drawn
    // at the border.
    //
    // This is not optimized for performance.  Some things that might make this faster:
    // - Remove the conditionals.  They're used to present a half & half view with a red
    //   stripe across the middle, but that's only useful for a demo.
    // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
    // - Bake the filter kernel into the shader, instead of passing it through a uniform
    //   array.  That, combined with loop unrolling, should reduce memory accesses.
    public static final int KERNEL_SIZE = 9;

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    // Fragment shader that converts color to black & white with a simple transformation.
    private static final String FRAGMENT_SHADER_EXT_BW =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
                    "    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
                    "    gl_FragColor = vec4(color, color, color, 1.0);\n" +
                    "}\n";
    private static final String FRAGMENT_SHADER_EXT_FILT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
                    "precision highp float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform float uKernel[KERNEL_SIZE];\n" +
                    "uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
                    "uniform float uColorAdjust;\n" +
                    "void main() {\n" +
                    "    int i = 0;\n" +
                    "    vec4 sum = vec4(0.0);\n" +
                    "    if (vTextureCoord.x < vTextureCoord.y - 0.005) {\n" +
                    "        for (i = 0; i < KERNEL_SIZE; i++) {\n" +
                    "            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
                    "            sum += texc * uKernel[i];\n" +
                    "        }\n" +
                    "    sum += uColorAdjust;\n" +
                    "    } else if (vTextureCoord.x > vTextureCoord.y + 0.005) {\n" +
                    "        sum = texture2D(sTexture, vTextureCoord);\n" +
                    "    } else {\n" +
                    "        sum.r = 1.0;\n" +
                    "    }\n" +
                    "    gl_FragColor = sum;\n" +
                    "}\n";
    private ProgramType programType;
    // Handles to the GL program and various components of it.
    private int programHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int muKernelLoc;
    private int muTexOffsetLoc;
    private int muColorAdjustLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private int mTextureTarget;
    @SuppressWarnings("CanBeFinal")
    private float[] mKernel = new float[KERNEL_SIZE];
    private float[] mTexOffset;
    private float mColorAdjust;

    /**
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(ProgramType programType) {
        this.programType = programType;

        switch (programType) {
            case TEXTURE_2D:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                programHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
                break;
            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                programHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);
                break;
            case TEXTURE_EXT_BW:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                programHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW);
                break;
            case TEXTURE_EXT_FILT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                programHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT);
                break;
            default:
                throw new RuntimeException("Unhandled type " + programType);
        }
        if (programHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Logger.d("Created program " + programHandle + " (" + programType + ")");

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(programHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(programHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
        muKernelLoc = GLES20.glGetUniformLocation(programHandle, "uKernel");
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1;
            muTexOffsetLoc = -1;
            muColorAdjustLoc = -1;
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(programHandle, "uTexOffset");
            GlUtil.checkLocation(muTexOffsetLoc, "uTexOffset");
            muColorAdjustLoc = GLES20.glGetUniformLocation(programHandle, "uColorAdjust");
            GlUtil.checkLocation(muColorAdjustLoc, "uColorAdjust");

            // initialize default values
            setKernel(new float[]{0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f}, 0f);
            setTexSize(256, 256);
        }
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        Logger.d("deleting program " + programHandle);
        GLES20.glDeleteProgram(programHandle);
        programHandle = -1;
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return programType;
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }

    /**
     * Configures the convolution filter values.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    public void setKernel(float[] values, float colorAdj) {
        if (values.length != KERNEL_SIZE) {
            throw new IllegalArgumentException("Kernel size is " + values.length +
                    " vs. " + KERNEL_SIZE);
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE);
        mColorAdjust = colorAdj;
        //Log.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    @SuppressWarnings("WeakerAccess")
    public void setTexSize(int width, int height) {
        float rw = 1.0f / width;
        float rh = 1.0f / height;

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = new float[]{
                -rw, -rh, 0f, -rh, rw, -rh,
                -rw, 0f, 0f, 0f, rw, 0f,
                -rw, rh, 0f, rh, rw, rh
        };
        //Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    public void setBitmap(Bitmap bitmap, int textureId) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLUtils.texImage2D(mTextureTarget, 0, GLES20.GL_RGBA, bitmap, 0);
    }

  /*  public void setBitmap(Bitmap bitmap, int textureId) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);
        GLES20.glEnable(GLES20.GL_BLEND); // this, and the next line
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA); // and this were key! I'm still not completely sure as to what this is doing, but it works!
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0);
    }*/

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix       The 4x4 projection matrix.
     * @param vertexBuffer    Buffer with vertex position data.
     * @param firstVertex     Index of first vertex to use in vertexBuffer.
     * @param vertexCount     Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride    Width, in bytes, of the position data for each vertex (often
     *                        vertexCount * sizeof(float)).
     * @param texMatrix       A 4x4 transformation matrix for texture coords.  (Primarily intended
     *                        for use with SurfaceTexture.)
     * @param texBuffer       Buffer with vertex texture data.
     * @param texStride       Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
        GlUtil.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(programHandle);
        GlUtil.checkGlError("glUseProgram");

        //Enable blend by default
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }

    public void clearScreen() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    public enum ProgramType {
        TEXTURE_2D, TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_FILT
    }
}
