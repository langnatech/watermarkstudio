#include <jni.h>
#include <cstdint>

extern "C" void applyTemporalMedianRgba(
    uint8_t* frames,
    int nFrames,
    int frameWidth,
    int frameHeight,
    int frameStride,
    int roiLeft,
    int roiTop,
    int roiWidth,
    int roiHeight);

extern "C" JNIEXPORT jint JNICALL
Java_com_watermarkstudio_removal_native_RemovalNative_nativePing(JNIEnv*, jobject) {
    return 42;
}

extern "C" JNIEXPORT void JNICALL
Java_com_watermarkstudio_removal_native_RemovalNative_nativeApplyTemporalMedian(
    JNIEnv* env,
    jobject,
    jbyteArray framesArray,
    jint nFrames,
    jint frameWidth,
    jint frameHeight,
    jint frameStride,
    jint roiLeft,
    jint roiTop,
    jint roiWidth,
    jint roiHeight
) {
    if (framesArray == nullptr) return;
    jbyte* frames = env->GetByteArrayElements(framesArray, nullptr);
    if (frames == nullptr) return;
    applyTemporalMedianRgba(
        reinterpret_cast<uint8_t*>(frames),
        nFrames,
        frameWidth,
        frameHeight,
        frameStride,
        roiLeft,
        roiTop,
        roiWidth,
        roiHeight
    );
    env->ReleaseByteArrayElements(framesArray, frames, 0);
}
