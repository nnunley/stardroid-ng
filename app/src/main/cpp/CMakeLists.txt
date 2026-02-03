cmake_minimum_required(VERSION 3.22.1)
project(stardroid-awakening)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android system libraries
find_library(log-lib log)
find_library(android-lib android)

# Vulkan library (provided by Android NDK)
find_library(vulkan-lib vulkan)

# Shader compilation
set(SHADER_DIR "${CMAKE_SOURCE_DIR}/../shaders")
set(SHADER_OUTPUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/shaders")
file(MAKE_DIRECTORY ${SHADER_OUTPUT_DIR})

# Find glslc - check NDK first, then system
find_program(GLSLC glslc
    HINTS
        "${ANDROID_NDK}/../shader-tools/${CMAKE_HOST_SYSTEM_PROCESSOR}"
        "$ENV{VULKAN_SDK}/bin"
        "/usr/local/bin"
)

if(NOT GLSLC)
    message(FATAL_ERROR "glslc not found. Install Vulkan SDK or ensure NDK shader-tools are available.")
endif()

message(STATUS "Found glslc: ${GLSLC}")

# Compile vertex shader
add_custom_command(
    OUTPUT "${SHADER_OUTPUT_DIR}/triangle_vert.spv"
    COMMAND ${GLSLC} -fshader-stage=vertex
            "${SHADER_DIR}/triangle.vert"
            -o "${SHADER_OUTPUT_DIR}/triangle_vert.spv"
    DEPENDS "${SHADER_DIR}/triangle.vert"
    COMMENT "Compiling triangle.vert"
)

# Compile fragment shader
add_custom_command(
    OUTPUT "${SHADER_OUTPUT_DIR}/triangle_frag.spv"
    COMMAND ${GLSLC} -fshader-stage=fragment
            "${SHADER_DIR}/triangle.frag"
            -o "${SHADER_OUTPUT_DIR}/triangle_frag.spv"
    DEPENDS "${SHADER_DIR}/triangle.frag"
    COMMENT "Compiling triangle.frag"
)

# Find Python for shader header generation
find_program(PYTHON python3 REQUIRED)

# Generate C header with embedded SPIR-V using Python script
add_custom_command(
    OUTPUT "${SHADER_OUTPUT_DIR}/shaders.h"
    COMMAND ${PYTHON} "${CMAKE_SOURCE_DIR}/generate_shaders_h.py"
            "${SHADER_OUTPUT_DIR}/shaders.h"
            "${SHADER_OUTPUT_DIR}/triangle_vert.spv"
            "${SHADER_OUTPUT_DIR}/triangle_frag.spv"
    DEPENDS
        "${SHADER_OUTPUT_DIR}/triangle_vert.spv"
        "${SHADER_OUTPUT_DIR}/triangle_frag.spv"
        "${CMAKE_SOURCE_DIR}/generate_shaders_h.py"
    COMMENT "Generating shaders.h"
)

# Custom target for shader compilation
add_custom_target(shaders DEPENDS "${SHADER_OUTPUT_DIR}/shaders.h")

# Create the vulkan_wrapper shared library
add_library(vulkan_wrapper SHARED
    vulkan_wrapper.cpp
)

# Ensure shaders are compiled before vulkan_wrapper
add_dependencies(vulkan_wrapper shaders)

# Include directories for Vulkan headers and generated shaders
target_include_directories(vulkan_wrapper PRIVATE
    ${ANDROID_NDK}/sources/third_party/vulkan/src/include
    ${SHADER_OUTPUT_DIR}
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
