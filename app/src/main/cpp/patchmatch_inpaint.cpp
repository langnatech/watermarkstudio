#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <vector>

namespace {

constexpr int kChannels = 4;
constexpr int kMinSourceSamples = 16;
constexpr int kMaxPyramidBase = 128;
constexpr float kBadDistance = 1.0e20f;

struct Image {
    int width = 0;
    int height = 0;
    std::vector<uint8_t> rgba;
    std::vector<uint8_t> mask;
};

struct Match {
    int sx = 0;
    int sy = 0;
    float distance = kBadDistance;
};

inline int clampInt(int v, int lo, int hi) {
    return std::max(lo, std::min(v, hi));
}

inline int pixelIndex(int width, int x, int y) {
    return (y * width + x) * kChannels;
}

inline bool isMasked(const Image& image, int x, int y) {
    return image.mask[y * image.width + x] != 0;
}

uint32_t nextRand(uint32_t& state) {
    state ^= state << 13;
    state ^= state >> 17;
    state ^= state << 5;
    return state;
}

std::vector<int> collectSourcePixels(const Image& image) {
    std::vector<int> samples;
    samples.reserve(static_cast<size_t>(image.width * image.height));
    for (int y = 0; y < image.height; ++y) {
        for (int x = 0; x < image.width; ++x) {
            if (!isMasked(image, x, y)) {
                samples.push_back(y * image.width + x);
            }
        }
    }
    return samples;
}

float patchDistance(const Image& image, int tx, int ty, int sx, int sy, int radius) {
    if (sx < 0 || sy < 0 || sx >= image.width || sy >= image.height || isMasked(image, sx, sy)) {
        return kBadDistance;
    }

    float distance = 0.0f;
    int samples = 0;
    for (int dy = -radius; dy <= radius; ++dy) {
        const int ty2 = ty + dy;
        const int sy2 = sy + dy;
        if (ty2 < 0 || sy2 < 0 || ty2 >= image.height || sy2 >= image.height) continue;
        for (int dx = -radius; dx <= radius; ++dx) {
            const int tx2 = tx + dx;
            const int sx2 = sx + dx;
            if (tx2 < 0 || sx2 < 0 || tx2 >= image.width || sx2 >= image.width) continue;
            if (isMasked(image, tx2, ty2) || isMasked(image, sx2, sy2)) continue;

            const int ti = pixelIndex(image.width, tx2, ty2);
            const int si = pixelIndex(image.width, sx2, sy2);
            const int dr = static_cast<int>(image.rgba[ti]) - static_cast<int>(image.rgba[si]);
            const int dg = static_cast<int>(image.rgba[ti + 1]) - static_cast<int>(image.rgba[si + 1]);
            const int db = static_cast<int>(image.rgba[ti + 2]) - static_cast<int>(image.rgba[si + 2]);
            distance += static_cast<float>(dr * dr + dg * dg + db * db);
            ++samples;
        }
    }

    if (samples == 0) return kBadDistance;
    return distance / static_cast<float>(samples);
}

void tryCandidate(Image& image, std::vector<Match>& matches, int x, int y, int sx, int sy, int radius) {
    sx = clampInt(sx, 0, image.width - 1);
    sy = clampInt(sy, 0, image.height - 1);
    if (isMasked(image, sx, sy)) return;
    const float distance = patchDistance(image, x, y, sx, sy, radius);
    Match& current = matches[y * image.width + x];
    if (distance < current.distance) {
        current.sx = sx;
        current.sy = sy;
        current.distance = distance;
    }
}

void initializeMatches(Image& image, std::vector<Match>& matches, const std::vector<int>& sources, int radius) {
    if (sources.empty()) return;
    uint32_t state = 0x9E3779B9u ^ static_cast<uint32_t>(image.width * 73856093) ^ static_cast<uint32_t>(image.height * 19349663);
    for (int y = 0; y < image.height; ++y) {
        for (int x = 0; x < image.width; ++x) {
            if (!isMasked(image, x, y)) continue;

            Match& match = matches[y * image.width + x];
            for (int r = 1; r < std::max(image.width, image.height); r *= 2) {
                bool found = false;
                for (int dy = -r; dy <= r && !found; dy += r) {
                    for (int dx = -r; dx <= r; dx += r) {
                        const int sx = x + dx;
                        const int sy = y + dy;
                        if (sx >= 0 && sy >= 0 && sx < image.width && sy < image.height && !isMasked(image, sx, sy)) {
                            match.sx = sx;
                            match.sy = sy;
                            match.distance = patchDistance(image, x, y, sx, sy, radius);
                            found = true;
                            break;
                        }
                    }
                }
                if (found) break;
            }

            const int randomTries = std::min<int>(32, sources.size());
            for (int i = 0; i < randomTries; ++i) {
                const int packed = sources[nextRand(state) % sources.size()];
                tryCandidate(image, matches, x, y, packed % image.width, packed / image.width, radius);
            }
        }
    }
}

void patchMatchIteration(Image& image, std::vector<Match>& matches, int radius, int iteration) {
    const bool forward = iteration % 2 == 0;
    const int yStart = forward ? 0 : image.height - 1;
    const int yEnd = forward ? image.height : -1;
    const int yStep = forward ? 1 : -1;
    const int xStart = forward ? 0 : image.width - 1;
    const int xEnd = forward ? image.width : -1;
    const int xStep = forward ? 1 : -1;
    uint32_t state = 0x85EBCA6Bu ^ static_cast<uint32_t>(iteration * 2654435761u);

    for (int y = yStart; y != yEnd; y += yStep) {
        for (int x = xStart; x != xEnd; x += xStep) {
            if (!isMasked(image, x, y)) continue;

            const int nx1 = x - xStep;
            if (nx1 >= 0 && nx1 < image.width) {
                const Match& neighbor = matches[y * image.width + nx1];
                tryCandidate(image, matches, x, y, neighbor.sx + xStep, neighbor.sy, radius);
            }
            const int ny1 = y - yStep;
            if (ny1 >= 0 && ny1 < image.height) {
                const Match& neighbor = matches[ny1 * image.width + x];
                tryCandidate(image, matches, x, y, neighbor.sx, neighbor.sy + yStep, radius);
            }

            const Match base = matches[y * image.width + x];
            int search = std::max(image.width, image.height);
            while (search > 1) {
                const int minX = clampInt(base.sx - search, 0, image.width - 1);
                const int maxX = clampInt(base.sx + search, 0, image.width - 1);
                const int minY = clampInt(base.sy - search, 0, image.height - 1);
                const int maxY = clampInt(base.sy + search, 0, image.height - 1);
                const int sx = minX + static_cast<int>(nextRand(state) % static_cast<uint32_t>(maxX - minX + 1));
                const int sy = minY + static_cast<int>(nextRand(state) % static_cast<uint32_t>(maxY - minY + 1));
                tryCandidate(image, matches, x, y, sx, sy, radius);
                search /= 2;
            }
        }
    }
}

void voteMaskedPixels(Image& image, const std::vector<Match>& matches, int radius) {
    std::vector<uint8_t> output = image.rgba;
    for (int y = 0; y < image.height; ++y) {
        for (int x = 0; x < image.width; ++x) {
            if (!isMasked(image, x, y)) continue;

            float r = 0.0f;
            float g = 0.0f;
            float b = 0.0f;
            float weightSum = 0.0f;
            for (int dy = -radius; dy <= radius; ++dy) {
                const int qy = y + dy;
                if (qy < 0 || qy >= image.height) continue;
                for (int dx = -radius; dx <= radius; ++dx) {
                    const int qx = x + dx;
                    if (qx < 0 || qx >= image.width || !isMasked(image, qx, qy)) continue;
                    const Match& match = matches[qy * image.width + qx];
                    const int sx = match.sx - dx;
                    const int sy = match.sy - dy;
                    if (sx < 0 || sy < 0 || sx >= image.width || sy >= image.height || isMasked(image, sx, sy)) continue;
                    const float weight = 1.0f / (1.0f + std::sqrt(std::max(0.0f, match.distance)));
                    const int si = pixelIndex(image.width, sx, sy);
                    r += static_cast<float>(image.rgba[si]) * weight;
                    g += static_cast<float>(image.rgba[si + 1]) * weight;
                    b += static_cast<float>(image.rgba[si + 2]) * weight;
                    weightSum += weight;
                }
            }

            const int oi = pixelIndex(image.width, x, y);
            if (weightSum > 0.0f) {
                output[oi] = static_cast<uint8_t>(clampInt(static_cast<int>(r / weightSum + 0.5f), 0, 255));
                output[oi + 1] = static_cast<uint8_t>(clampInt(static_cast<int>(g / weightSum + 0.5f), 0, 255));
                output[oi + 2] = static_cast<uint8_t>(clampInt(static_cast<int>(b / weightSum + 0.5f), 0, 255));
            }
        }
    }
    image.rgba.swap(output);
}

Image downsample(const Image& src) {
    Image dst;
    dst.width = std::max(1, src.width / 2);
    dst.height = std::max(1, src.height / 2);
    dst.rgba.assign(static_cast<size_t>(dst.width * dst.height * kChannels), 0);
    dst.mask.assign(static_cast<size_t>(dst.width * dst.height), 0);

    for (int y = 0; y < dst.height; ++y) {
        for (int x = 0; x < dst.width; ++x) {
            int r = 0, g = 0, b = 0, a = 0, count = 0, maskCount = 0;
            for (int dy = 0; dy < 2; ++dy) {
                for (int dx = 0; dx < 2; ++dx) {
                    const int sx = std::min(src.width - 1, x * 2 + dx);
                    const int sy = std::min(src.height - 1, y * 2 + dy);
                    const int si = pixelIndex(src.width, sx, sy);
                    r += src.rgba[si];
                    g += src.rgba[si + 1];
                    b += src.rgba[si + 2];
                    a += src.rgba[si + 3];
                    maskCount += src.mask[sy * src.width + sx] != 0 ? 1 : 0;
                    ++count;
                }
            }
            const int di = pixelIndex(dst.width, x, y);
            dst.rgba[di] = static_cast<uint8_t>(r / count);
            dst.rgba[di + 1] = static_cast<uint8_t>(g / count);
            dst.rgba[di + 2] = static_cast<uint8_t>(b / count);
            dst.rgba[di + 3] = static_cast<uint8_t>(a / count);
            dst.mask[y * dst.width + x] = maskCount > 0 ? 255 : 0;
        }
    }
    return dst;
}

void sampleBilinearRgb(const Image& image, float x, float y, float& r, float& g, float& b) {
    const float maxX = static_cast<float>(std::max(0, image.width - 1));
    const float maxY = static_cast<float>(std::max(0, image.height - 1));
    const float cx = std::min(maxX, std::max(0.0f, x));
    const float cy = std::min(maxY, std::max(0.0f, y));
    const int x0 = static_cast<int>(cx);
    const int y0 = static_cast<int>(cy);
    const int x1 = std::min(image.width - 1, x0 + 1);
    const int y1 = std::min(image.height - 1, y0 + 1);
    const float tx = cx - static_cast<float>(x0);
    const float ty = cy - static_cast<float>(y0);
    const int i00 = pixelIndex(image.width, x0, y0);
    const int i10 = pixelIndex(image.width, x1, y0);
    const int i01 = pixelIndex(image.width, x0, y1);
    const int i11 = pixelIndex(image.width, x1, y1);
    const float w00 = (1.0f - tx) * (1.0f - ty);
    const float w10 = tx * (1.0f - ty);
    const float w01 = (1.0f - tx) * ty;
    const float w11 = tx * ty;
    r = image.rgba[i00] * w00 + image.rgba[i10] * w10 + image.rgba[i01] * w01 + image.rgba[i11] * w11;
    g = image.rgba[i00 + 1] * w00 + image.rgba[i10 + 1] * w10 + image.rgba[i01 + 1] * w01 +
        image.rgba[i11 + 1] * w11;
    b = image.rgba[i00 + 2] * w00 + image.rgba[i10 + 2] * w10 + image.rgba[i01 + 2] * w01 +
        image.rgba[i11 + 2] * w11;
}

void upsampleIntoMasked(const Image& coarse, Image& fine) {
    // Bilinear upsample into masked pixels — nearest-neighbor creates blocky color patches.
    const float scaleX =
        fine.width > 1 ? static_cast<float>(std::max(1, coarse.width - 1)) /
                             static_cast<float>(std::max(1, fine.width - 1))
                       : 0.0f;
    const float scaleY =
        fine.height > 1 ? static_cast<float>(std::max(1, coarse.height - 1)) /
                              static_cast<float>(std::max(1, fine.height - 1))
                        : 0.0f;
    for (int y = 0; y < fine.height; ++y) {
        for (int x = 0; x < fine.width; ++x) {
            if (!isMasked(fine, x, y)) continue;
            float r = 0.0f;
            float g = 0.0f;
            float b = 0.0f;
            sampleBilinearRgb(coarse, static_cast<float>(x) * scaleX, static_cast<float>(y) * scaleY, r, g, b);
            const int fi = pixelIndex(fine.width, x, y);
            fine.rgba[fi] = static_cast<uint8_t>(clampInt(static_cast<int>(r + 0.5f), 0, 255));
            fine.rgba[fi + 1] = static_cast<uint8_t>(clampInt(static_cast<int>(g + 0.5f), 0, 255));
            fine.rgba[fi + 2] = static_cast<uint8_t>(clampInt(static_cast<int>(b + 0.5f), 0, 255));
        }
    }
}

void upsampleMatches(
    const Image& coarse,
    const std::vector<Match>& coarseMatches,
    Image& fine,
    std::vector<Match>& fineMatches,
    int radius
) {
    const size_t fineCount = static_cast<size_t>(fine.width * fine.height);
    fineMatches.assign(fineCount, Match{});

    for (int y = 0; y < fine.height; ++y) {
        for (int x = 0; x < fine.width; ++x) {
            if (!isMasked(fine, x, y)) continue;

            const int cx = clampInt(x * coarse.width / fine.width, 0, coarse.width - 1);
            const int cy = clampInt(y * coarse.height / fine.height, 0, coarse.height - 1);
            const Match& coarseMatch = coarseMatches[static_cast<size_t>(cy * coarse.width + cx)];

            const int scaledSx = coarseMatch.sx * fine.width / coarse.width;
            const int scaledSy = coarseMatch.sy * fine.height / coarse.height;

            tryCandidate(fine, fineMatches, x, y, scaledSx, scaledSy, radius);
            tryCandidate(fine, fineMatches, x, y, scaledSx + 1, scaledSy, radius);
            tryCandidate(fine, fineMatches, x, y, scaledSx, scaledSy + 1, radius);
        }
    }
}

bool inpaintSingleScale(
    Image& image,
    int patchSize,
    int iterations,
    std::vector<Match>* matchesInOut
) {
    const std::vector<int> sources = collectSourcePixels(image);
    if (sources.size() < kMinSourceSamples) return false;

    const int radius = std::max(1, patchSize / 2);
    std::vector<Match> owned;
    std::vector<Match>* matches = matchesInOut != nullptr ? matchesInOut : &owned;
    const size_t expected = static_cast<size_t>(image.width * image.height);

    if (matches->size() != expected) {
        matches->assign(expected, Match{});
        initializeMatches(image, *matches, sources, radius);
    } else {
        for (int y = 0; y < image.height; ++y) {
            for (int x = 0; x < image.width; ++x) {
                if (!isMasked(image, x, y)) continue;
                Match& current = (*matches)[static_cast<size_t>(y * image.width + x)];
                if (isMasked(image, current.sx, current.sy)) {
                    current.distance = kBadDistance;
                }
                tryCandidate(image, *matches, x, y, current.sx, current.sy, radius);
            }
        }
    }

    for (int i = 0; i < iterations; ++i) {
        patchMatchIteration(image, *matches, radius, i);
    }
    voteMaskedPixels(image, *matches, radius);
    return true;
}

bool inpaintMultiScale(Image& image, int patchSize, int emIterations, int pmIterations) {
    std::vector<Image> pyramid;
    pyramid.push_back(image);
    while (
        std::max(pyramid.back().width, pyramid.back().height) > kMaxPyramidBase &&
        collectSourcePixels(pyramid.back()).size() >= kMinSourceSamples * 2
    ) {
        pyramid.push_back(downsample(pyramid.back()));
    }

    const int radius = std::max(1, (std::max(3, patchSize | 1)) / 2);
    std::vector<Match> matches;

    for (int level = static_cast<int>(pyramid.size()) - 1; level >= 0; --level) {
        if (level < static_cast<int>(pyramid.size()) - 1) {
            upsampleIntoMasked(pyramid[level + 1], pyramid[level]);
            upsampleMatches(pyramid[level + 1], matches, pyramid[level], matches, radius);
        } else {
            matches.clear();
        }

        const int levelIterations = std::max(1, pmIterations + (static_cast<int>(pyramid.size()) - 1 - level));
        for (int em = 0; em < std::max(1, emIterations); ++em) {
            if (!inpaintSingleScale(pyramid[level], patchSize, levelIterations, &matches)) {
                return false;
            }
        }
    }

    image.rgba.swap(pyramid.front().rgba);
    return true;
}

} // namespace

extern "C" {

int patchMatchInpaintRgba(
    uint8_t* imageRgba,
    const uint8_t* mask,
    int width,
    int height,
    int patchSize,
    int emIterations,
    int pmIterations
) {
    if (imageRgba == nullptr || mask == nullptr || width <= 0 || height <= 0) return 2;
    Image image;
    image.width = width;
    image.height = height;
    const size_t pixelBytes = static_cast<size_t>(width) * static_cast<size_t>(height) * kChannels;
    image.rgba.assign(imageRgba, imageRgba + pixelBytes);
    image.mask.assign(mask, mask + static_cast<size_t>(width) * static_cast<size_t>(height));

    const bool ok = inpaintMultiScale(
        image,
        std::max(3, patchSize | 1),
        std::max(1, emIterations),
        std::max(1, pmIterations)
    );
    if (!ok) return 1;

    std::copy(image.rgba.begin(), image.rgba.end(), imageRgba);
    return 0;
}

} // extern "C"
