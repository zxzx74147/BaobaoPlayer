uniform mat4 uMVPMatrix;
uniform mat4 uTexMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
//varying vec2 vTextureCoord;
varying vec2 textureCoordinate;
void main() {
     textureCoordinate = (uTexMatrix * aTextureCoord).xy;
     gl_Position = uMVPMatrix * aPosition;

//     vTextureCoord = (uTexMatrix * aTextureCoord).xy;
}