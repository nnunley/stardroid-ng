#ifndef MATH_UTILS_H
#define MATH_UTILS_H

#include <cmath>

namespace math {

constexpr float PI = 3.14159265358979f;

/**
 * Set a 4x4 matrix to identity.
 * Column-major order for Vulkan/GLSL compatibility.
 */
inline void identity(float* matrix) {
    matrix[0] = 1.0f;  matrix[4] = 0.0f;  matrix[8]  = 0.0f;  matrix[12] = 0.0f;
    matrix[1] = 0.0f;  matrix[5] = 1.0f;  matrix[9]  = 0.0f;  matrix[13] = 0.0f;
    matrix[2] = 0.0f;  matrix[6] = 0.0f;  matrix[10] = 1.0f;  matrix[14] = 0.0f;
    matrix[3] = 0.0f;  matrix[7] = 0.0f;  matrix[11] = 0.0f;  matrix[15] = 1.0f;
}

/**
 * Build a Z-axis rotation matrix.
 * Column-major order for Vulkan/GLSL compatibility.
 */
inline void rotateZ(float angleDegrees, float* matrix) {
    float angleRadians = angleDegrees * PI / 180.0f;
    float cosA = std::cos(angleRadians);
    float sinA = std::sin(angleRadians);

    // Column-major order
    matrix[0] = cosA;   matrix[4] = -sinA;  matrix[8]  = 0.0f;  matrix[12] = 0.0f;
    matrix[1] = sinA;   matrix[5] = cosA;   matrix[9]  = 0.0f;  matrix[13] = 0.0f;
    matrix[2] = 0.0f;   matrix[6] = 0.0f;   matrix[10] = 1.0f;  matrix[14] = 0.0f;
    matrix[3] = 0.0f;   matrix[7] = 0.0f;   matrix[11] = 0.0f;  matrix[15] = 1.0f;
}

/**
 * Multiply two 4x4 matrices: result = a * b
 * Column-major order for Vulkan/GLSL compatibility.
 * result must not alias a or b.
 */
inline void multiply(const float* a, const float* b, float* result) {
    for (int col = 0; col < 4; col++) {
        for (int row = 0; row < 4; row++) {
            float sum = 0.0f;
            for (int k = 0; k < 4; k++) {
                sum += a[row + k * 4] * b[k + col * 4];
            }
            result[row + col * 4] = sum;
        }
    }
}

} // namespace math

#endif // MATH_UTILS_H
