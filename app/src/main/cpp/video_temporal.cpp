#include <algorithm>
#include <cstdint>
#include <vector>

namespace {

inline int medianOf(std::vector<uint8_t>& values) {
    if (values.empty()) return 0;
    const size_t mid = values.size() / 2;
    std::nth_element(values.begin(), values.begin() + static_cast<long>(mid), values.end());
    return values[mid];
}

} // namespace

extern "C" {

/**
 * Applies per-channel temporal median inside ROI for each frame (RGBA in/out).
 * frames: nFrames * frameStride bytes, row-major RGBA
 */
void applyTemporalMedianRgba(
    uint8_t* frames,
    int nFrames,
    int frameWidth,
    int frameHeight,
    int frameStride,
    int roiLeft,
    int roiTop,
    int roiWidth,
    int roiHeight
) {
    if (frames == nullptr || nFrames <= 0 || roiWidth <= 0 || roiHeight <= 0) return;

    const int roiRight = roiLeft + roiWidth;
    const int roiBottom = roiTop + roiHeight;

    std::vector<uint8_t> rSamples;
    std::vector<uint8_t> gSamples;
    std::vector<uint8_t> bSamples;
    rSamples.reserve(static_cast<size_t>(nFrames));
    gSamples.reserve(static_cast<size_t>(nFrames));
    bSamples.reserve(static_cast<size_t>(nFrames));

    for (int y = roiTop; y < roiBottom; ++y) {
        for (int x = roiLeft; x < roiRight; ++x) {
            rSamples.clear();
            gSamples.clear();
            bSamples.clear();
            for (int f = 0; f < nFrames; ++f) {
                const uint8_t* base = frames + static_cast<size_t>(f) * static_cast<size_t>(frameStride);
                const int idx = (y * frameWidth + x) * 4;
                rSamples.push_back(base[idx]);
                gSamples.push_back(base[idx + 1]);
                bSamples.push_back(base[idx + 2]);
            }
            const uint8_t mr = static_cast<uint8_t>(medianOf(rSamples));
            const uint8_t mg = static_cast<uint8_t>(medianOf(gSamples));
            const uint8_t mb = static_cast<uint8_t>(medianOf(bSamples));
            for (int f = 0; f < nFrames; ++f) {
                uint8_t* pix = frames + static_cast<size_t>(f) * static_cast<size_t>(frameStride) + static_cast<size_t>((y * frameWidth + x) * 4);
                pix[0] = mr;
                pix[1] = mg;
                pix[2] = mb;
                // alpha unchanged
            }
        }
    }
}

} // extern "C"
