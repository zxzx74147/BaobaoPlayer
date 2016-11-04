package cn.myhug.baobaoplayer.filter.base;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.FloatBuffer;

import cn.myhug.baobaoplayer.R;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.gles.GlUtil;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;

import static android.opengl.GLES20.GL_FRAMEBUFFER;


public class MagicSurfaceInputFilter extends GPUImageFilter {

    private float[] mTextureTransformMatrix;
    private int mTextureTransformMatrixLocation;

    private int[] mFrameBuffers = null;
    private int[] mFrameBufferTextures = null;
    private int mFrameWidth = -1;
    private int mFrameHeight = -1;

    public MagicSurfaceInputFilter(){
        super(OpenGlUtils.readShaderFromRawResource(R.raw.default_vertex) ,
                OpenGlUtils.readShaderFromRawResource(R.raw.default_no_filter_surface_fragment));

    }

    protected void onInit() {
        super.onInit();
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");

    }

    public void setTextureTransformMatrix(float[] mtx){
        mTextureTransformMatrix = mtx;
    }

    @Override
    public int onDrawFrame(int textureId) {
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
        mGLCubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return OpenGlUtils.ON_DRAWN;
    }

    @Override
    public int onDrawFrame(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        GLES20.glUseProgram(mGLProgId);
        GlUtil.checkGlError("glUseProgram");
        runPendingOnDrawTasks();
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer"+mGLAttribPosition+vertexBuffer.limit());
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");
        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GlUtil.checkGlError("glBindTexture");
            GLES20.glUniform1i(mGLUniformTexture, 0);

        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return OpenGlUtils.ON_DRAWN;
    }

    public int onDrawToTexture(final int textureId) {
        if(mFrameBuffers == null)
            return OpenGlUtils.NO_TEXTURE;
        runPendingOnDrawTasks();
        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, mFrameBuffers[0]);
        GlUtil.checkGlError("glBindFramebuffer");
        int status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
            return OpenGlUtils.NO_TEXTURE;
        }
        GLES20.glUseProgram(mGLProgId);
        if(!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }
         status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
            return OpenGlUtils.NO_TEXTURE;
        }

        mGLCubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);

         status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
            return OpenGlUtils.NO_TEXTURE;
        }

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GlUtil.checkGlError("glBindTexture");
            GLES20.glUniform1i(mGLUniformTexture, 0);
            GlUtil.checkGlError("glUniform1i");
        }

         status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
            return OpenGlUtils.NO_TEXTURE;
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkGlError("glDrawArrays");
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GlUtil.checkGlError("onDrawToTexture");
        return mFrameBufferTextures[0];
    }

    public void initSurfaceFrameBuffer(int width, int height) {
        if(mFrameBuffers != null && (mFrameWidth != width || mFrameHeight != height))
            destroyFramebuffers();
        if (mFrameBuffers == null) {
            mFrameWidth = width;
            mFrameHeight = height;
            mFrameBuffers = new int[1];
            mFrameBufferTextures = new int[1];

            GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
            GLES20.glGenTextures(1, mFrameBufferTextures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, mFrameBuffers[0]);
            GLES20.glFramebufferTexture2D(GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);


            int status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
                status++;
            }

            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, mFrameBuffers[0]);
             status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){
                status++;
            }
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        GlUtil.checkGlError("initSurfaceFrameBuffer");
    }

    public void destroyFramebuffers() {
        if (mFrameBufferTextures != null) {
            GLES20.glDeleteTextures(1, mFrameBufferTextures, 0);
            mFrameBufferTextures = null;
        }
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0);
            mFrameBuffers = null;
        }
        mFrameWidth = -1;
        mFrameHeight = -1;
    }


    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyFramebuffers();
    }

}