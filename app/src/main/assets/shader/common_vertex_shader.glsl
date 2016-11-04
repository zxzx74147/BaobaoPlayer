uniform mat4 uMVPMatrix;
uniform mat4 uTexMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;

varying vec2 textureCoordinate;
void main() {
     gl_Position = uMVPMatrix * aPosition;
     textureCoordinate = (uTexMatrix * aTextureCoord).xy;
}