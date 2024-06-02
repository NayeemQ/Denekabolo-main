# Dene ka bolo
A small app to resolve actual prices of items through your phone.

# What's that name? 
"Dene ka bolo" is a classic hyderabadi phrase, which translates to "how much are you actually selling this for".

# Dependencies:

1. CameraX Jetpack library for camera management: https://developer.android.com/media/camera/camerax
2. Google MLKit for on-device inference: https://developers.google.com/ml-kit
 * Text recognition to read text from images in real time: https://developers.google.com/ml-kit/vision/text-recognition/v2
 * Entity extraction for currency detection in recognized text: https://developers.google.com/ml-kit/language/entity-extraction

(These are still TODO - Demo uses a placeholders for these values)

3. Location Services to obtain zip code.
4. API/Data from <TBD Service> to fetch current tax rate for zip code and calculate actual sale price.

# Demo:
![Demo Image](https://github.com/moxenseya/Denekabolo/blob/main/Demo.jpg)

Test image: https://www.the-sun.com/money/4639040/target-clearance-markdown-schedule/
