#include <jni.h>
#include <cstdint>

extern "C" void applyTemporalMedianRgba(
    uint8_t* frames,
    const uint8_t* mask,
    int nFrames,
    int frameWidth,
    int frameHeight,
    int frameStride,
    int roiLeft,
    int roiTop,
    int roiWidth,
    int roiHeight);

extern "C" void patchMatchInpaintRgba(
    uint8_t* imageRgba,
    const uint8_t* mask,
    int width,
    int height,
    int patchSize,
    int emIterations,
    int pmIterations);

extern "C" JNIEXPORT jint JNICALL
Java_com_watermarkstudio_removal_native_RemovalNative_nativePing(JNIEnv*, jobject) {
    return 42;
}

extern "C" JNIEXPORT void JNICALL
Java_com_watermarkstudio_removal_native_RemovalNative_nativeApplyTemporalMedian(
    JNIEnv* env,
    jobject,
    jbyteArray framesArray,
    jbyteArray maskArray,
    jint nFrames,
    jint frameWidth,
    jint frameHeight,
    jint frameStride,
    jint roiLeft,
    jint roiTop,
    jint roiWidth,
    jint roiHeight
) {
    if (framesArray == nullptr || maskArray == nullptr) return;
    jbyte* frames = env->GetByteArrayElements(framesArray, nullptr);
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    if (frames == nullptr || mask == nullptr) {
        if (frames != nullptr) env->ReleaseByteArrayElements(framesArray, frames, 0);
        if (mask != nullptr) env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        return;
    }
    applyTemporalMedianRgba(
        reinterpret_cast<uint8_t*>(frames),
        reinterpret_cast<const uint8_t*>(mask),
        nFrames,
        frameWidth,
        frameHeight,
        frameStride,
        roiLeft,
        roiTop,
        roiWidth,
        roiHeight
    );
    env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    env->ReleaseByteArrayElements(framesArray, frames, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_watermarkstudio_removal_native_RemovalNative_nativePatchMatchInpaint(
    JNIEnv* env,
    jobject,
    jbyteArray imageArray,
    jbyteArray maskArray,
    jint width,
    jint height,
    jint patchSize,
    jint emIterations,
    jint pmIterations
) {
    if (imageArray == nullptr || maskArray == nullptr) return;
    jbyte* image = env->GetByteArrayElements(imageArray, nullptr);
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    if (image == nullptr || mask == nullptr) {
        if (image != nullptr) env->ReleaseByteArrayElements(imageArray, image, 0);
        if (mask != nullptr) env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        return;
    }
    patchMatchInpaintRgba(
        reinterpret_cast<uint8_t*>(image),
        reinterpret_cast<const uint8_t*>(mask),
        width,
        height,
        patchSize,
        emIterations,
        pmIterations
    );
    env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    env->ReleaseByteArrayElements(imageArray, image, 0);
}
