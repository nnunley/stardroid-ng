#ifndef STARDROID_VULKAN_WRAPPER_H
#define STARDROID_VULKAN_WRAPPER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize Vulkan with the given Android Surface.
 * Creates VkInstance, VkSurfaceKHR, selects physical device, and creates logical device.
 *
 * @param env JNI environment
 * @param obj Java object reference
 * @param surface Android Surface to render to
 * @return Native context handle (cast to jlong), or 0 on failure
 */
JNIEXPORT jlong JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeInit(
    JNIEnv* env, jobject obj, jobject surface);

/**
 * Render a frame with the given rotation angle.
 *
 * @param env JNI environment
 * @param obj Java object reference
 * @param contextHandle Native context handle from nativeInit
 * @param angle Rotation angle in degrees
 */
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeRender(
    JNIEnv* env, jobject obj, jlong contextHandle, jfloat angle);

/**
 * Handle surface resize.
 *
 * @param env JNI environment
 * @param obj Java object reference
 * @param contextHandle Native context handle from nativeInit
 * @param width New surface width
 * @param height New surface height
 */
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeResize(
    JNIEnv* env, jobject obj, jlong contextHandle, jint width, jint height);

/**
 * Clean up Vulkan resources.
 *
 * @param env JNI environment
 * @param obj Java object reference
 * @param contextHandle Native context handle from nativeInit
 */
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeDestroy(
    JNIEnv* env, jobject obj, jlong contextHandle);

#ifdef __cplusplus
}
#endif

#endif // STARDROID_VULKAN_WRAPPER_H
