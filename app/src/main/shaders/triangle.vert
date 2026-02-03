#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec4 inColor;

layout(location = 0) out vec4 fragColor;

layout(set = 0, binding = 0) uniform Matrices {
    mat4 view;
    mat4 projection;
} ubo;

layout(push_constant) uniform PushConstants {
    mat4 model;
} pc;

void main() {
    gl_Position = ubo.projection * ubo.view * pc.model * vec4(inPosition, 1.0);
    gl_PointSize = 4.0;
    fragColor = inColor;
}
