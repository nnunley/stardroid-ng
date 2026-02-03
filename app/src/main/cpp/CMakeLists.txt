cmake_minimum_required(VERSION 3.22.1)
project(stardroid-awakening)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android system libraries
find_library(log-lib log)
find_library(android-lib android)

# Vulkan library (provided by Android NDK)
find_library(vulkan-lib vulkan)

# Create the vulkan_wrapper shared library
add_library(vulkan_wrapper SHARED
    vulkan_wrapper.cpp
)

# Include directories for Vulkan headers
target_include_directories(vulkan_wrapper PRIVATE
    ${ANDROID_NDK}/sources/third_party/vulkan/src/include
)

# Link libraries
target_link_libraries(vulkan_wrapper
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
)

# Enable position-independent code
set_target_properties(vulkan_wrapper PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)

# Set warning flags
target_compile_options(vulkan_wrapper PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
)
