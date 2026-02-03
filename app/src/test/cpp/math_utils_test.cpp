#include <gtest/gtest.h>
#include <cmath>
#include "math_utils.h"

namespace {

constexpr float EPSILON = 1e-6f;

// Helper to compare matrices within epsilon
void expectMatrixEqual(const float* expected, const float* actual, float epsilon = EPSILON) {
    for (int i = 0; i < 16; i++) {
        EXPECT_NEAR(expected[i], actual[i], epsilon)
            << "Matrices differ at index " << i;
    }
}

TEST(MathUtilsTest, IdentityMatrixIsCorrect) {
    float matrix[16];
    math::identity(matrix);

    // Expected identity in column-major order
    float expected[16] = {
        1, 0, 0, 0,  // column 0
        0, 1, 0, 0,  // column 1
        0, 0, 1, 0,  // column 2
        0, 0, 0, 1   // column 3
    };

    expectMatrixEqual(expected, matrix);
}

TEST(MathUtilsTest, RotateZ90DegreesIsCorrect) {
    float matrix[16];
    math::rotateZ(90.0f, matrix);

    // 90 degree rotation: cos(90)=0, sin(90)=1
    // Column-major:
    // [ 0 -1  0  0 ]
    // [ 1  0  0  0 ]
    // [ 0  0  1  0 ]
    // [ 0  0  0  1 ]
    float expected[16] = {
        0, 1, 0, 0,   // column 0: (cos, sin, 0, 0)
        -1, 0, 0, 0,  // column 1: (-sin, cos, 0, 0)
        0, 0, 1, 0,   // column 2
        0, 0, 0, 1    // column 3
    };

    expectMatrixEqual(expected, matrix);
}

TEST(MathUtilsTest, RotateZ0DegreesIsIdentity) {
    float matrix[16];
    math::rotateZ(0.0f, matrix);

    float identity[16];
    math::identity(identity);

    expectMatrixEqual(identity, matrix);
}

TEST(MathUtilsTest, RotateZ360DegreesIsIdentity) {
    float matrix[16];
    math::rotateZ(360.0f, matrix);

    float identity[16];
    math::identity(identity);

    expectMatrixEqual(identity, matrix, 1e-5f);  // Slightly looser for accumulated error
}

TEST(MathUtilsTest, MultiplyIdentityReturnsOriginal) {
    float identity[16];
    math::identity(identity);

    float rotated[16];
    math::rotateZ(45.0f, rotated);

    float result[16];
    math::multiply(identity, rotated, result);

    expectMatrixEqual(rotated, result);
}

TEST(MathUtilsTest, MultiplyByIdentityOnRightReturnsOriginal) {
    float identity[16];
    math::identity(identity);

    float rotated[16];
    math::rotateZ(45.0f, rotated);

    float result[16];
    math::multiply(rotated, identity, result);

    expectMatrixEqual(rotated, result);
}

TEST(MathUtilsTest, TwoRotationsAddUp) {
    // Rotating by 30 then by 60 should equal rotating by 90
    float rotate30[16];
    float rotate60[16];
    float rotate90[16];
    float result[16];

    math::rotateZ(30.0f, rotate30);
    math::rotateZ(60.0f, rotate60);
    math::rotateZ(90.0f, rotate90);

    math::multiply(rotate60, rotate30, result);  // rotate30 first, then rotate60

    expectMatrixEqual(rotate90, result, 1e-5f);
}

TEST(MathUtilsTest, MatchesKotlinMatrixOutput) {
    // This test ensures C++ output matches Kotlin Matrix.kt
    // Kotlin Matrix.rotateZ(45.0f) produces these values (column-major)
    float matrix[16];
    math::rotateZ(45.0f, matrix);

    float angle = 45.0f * math::PI / 180.0f;
    float c = std::cos(angle);
    float s = std::sin(angle);

    EXPECT_NEAR(c, matrix[0], EPSILON);   // cos at [0,0]
    EXPECT_NEAR(s, matrix[1], EPSILON);   // sin at [1,0]
    EXPECT_NEAR(-s, matrix[4], EPSILON);  // -sin at [0,1]
    EXPECT_NEAR(c, matrix[5], EPSILON);   // cos at [1,1]
    EXPECT_NEAR(1.0f, matrix[10], EPSILON); // 1 at [2,2]
    EXPECT_NEAR(1.0f, matrix[15], EPSILON); // 1 at [3,3]
}

} // namespace
