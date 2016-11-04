#extension GL_OES_EGL_image_external : require
precision lowp float;

varying  vec2 textureCoordinate;
uniform samplerExternalOES sTexture;


            varying vec2 leftTextureCoordinate;
            varying vec2 rightTextureCoordinate;

            varying vec2 topTextureCoordinate;
            varying vec2 topLeftTextureCoordinate;
            varying vec2 topRightTextureCoordinate;

            varying vec2 bottomTextureCoordinate;
            varying vec2 bottomLeftTextureCoordinate;
            varying vec2 bottomRightTextureCoordinate;

//            uniform sampler2D sTexture;

            void main()
            {
            float bottomLeftIntensity = texture2D(sTexture, bottomLeftTextureCoordinate).r;
            float topRightIntensity = texture2D(sTexture, topRightTextureCoordinate).r;
            float topLeftIntensity = texture2D(sTexture, topLeftTextureCoordinate).r;
            float bottomRightIntensity = texture2D(sTexture, bottomRightTextureCoordinate).r;
            float leftIntensity = texture2D(sTexture, leftTextureCoordinate).r;
            float rightIntensity = texture2D(sTexture, rightTextureCoordinate).r;
            float bottomIntensity = texture2D(sTexture, bottomTextureCoordinate).r;
            float topIntensity = texture2D(sTexture, topTextureCoordinate).r;
            float h = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;
            float v = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;

            float mag = 1.0 - length(vec2(h, v));

            gl_FragColor = vec4(vec3(mag), 1.0);
            }