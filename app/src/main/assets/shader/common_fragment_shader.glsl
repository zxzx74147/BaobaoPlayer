#extension GL_OES_EGL_image_external : require 
precision mediump float;
varying highp vec2 textureCoordinate;
uniform samplerExternalOES sTexture;
void main() {
     gl_FragColor = texture2D(sTexture, textureCoordinate);
}