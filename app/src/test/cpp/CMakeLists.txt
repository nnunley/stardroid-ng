cmake_minimum_required(VERSION 3.22.1)
project(stardroid-awakening-tests)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fetch GoogleTest
include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/refs/tags/v1.14.0.zip
)
# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

enable_testing()

# Include the main cpp directory for math_utils.h
set(MAIN_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp")

# Test executable
add_executable(math_utils_test
    math_utils_test.cpp
)

target_include_directories(math_utils_test PRIVATE
    ${MAIN_CPP_DIR}
)

target_link_libraries(math_utils_test
    GTest::gtest_main
)

include(GoogleTest)
gtest_discover_tests(math_utils_test)
