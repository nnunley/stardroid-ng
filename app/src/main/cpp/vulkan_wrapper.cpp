#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <vector>
#include <string>
#include <cstring>
#include <stdexcept>

#define LOG_TAG "VulkanWrapper"

// Logging interval for render loop (frames between log messages)
constexpr int LOG_FRAME_INTERVAL = 300;

// Convert VkResult to human-readable string for debugging
static const char* vkResultToString(VkResult result) {
    switch (result) {
        case VK_SUCCESS: return "VK_SUCCESS";
        case VK_NOT_READY: return "VK_NOT_READY";
        case VK_TIMEOUT: return "VK_TIMEOUT";
        case VK_EVENT_SET: return "VK_EVENT_SET";
        case VK_EVENT_RESET: return "VK_EVENT_RESET";
        case VK_INCOMPLETE: return "VK_INCOMPLETE";
        case VK_ERROR_OUT_OF_HOST_MEMORY: return "VK_ERROR_OUT_OF_HOST_MEMORY";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "VK_ERROR_OUT_OF_DEVICE_MEMORY";
        case VK_ERROR_INITIALIZATION_FAILED: return "VK_ERROR_INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST: return "VK_ERROR_DEVICE_LOST";
        case VK_ERROR_MEMORY_MAP_FAILED: return "VK_ERROR_MEMORY_MAP_FAILED";
        case VK_ERROR_LAYER_NOT_PRESENT: return "VK_ERROR_LAYER_NOT_PRESENT";
        case VK_ERROR_EXTENSION_NOT_PRESENT: return "VK_ERROR_EXTENSION_NOT_PRESENT";
        case VK_ERROR_FEATURE_NOT_PRESENT: return "VK_ERROR_FEATURE_NOT_PRESENT";
        case VK_ERROR_INCOMPATIBLE_DRIVER: return "VK_ERROR_INCOMPATIBLE_DRIVER";
        case VK_ERROR_TOO_MANY_OBJECTS: return "VK_ERROR_TOO_MANY_OBJECTS";
        case VK_ERROR_FORMAT_NOT_SUPPORTED: return "VK_ERROR_FORMAT_NOT_SUPPORTED";
        case VK_ERROR_FRAGMENTED_POOL: return "VK_ERROR_FRAGMENTED_POOL";
        case VK_ERROR_SURFACE_LOST_KHR: return "VK_ERROR_SURFACE_LOST_KHR";
        case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR: return "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
        case VK_SUBOPTIMAL_KHR: return "VK_SUBOPTIMAL_KHR";
        case VK_ERROR_OUT_OF_DATE_KHR: return "VK_ERROR_OUT_OF_DATE_KHR";
        default: return "VK_UNKNOWN_ERROR";
    }
}
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Vulkan context holds all Vulkan objects
struct VulkanContext {
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue presentQueue = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = UINT32_MAX;
    uint32_t presentQueueFamily = UINT32_MAX;
    ANativeWindow* nativeWindow = nullptr;
    int width = 0;
    int height = 0;
    bool initialized = false;
    int frameCount = 0;

#ifndef NDEBUG
    VkDebugUtilsMessengerEXT debugMessenger = VK_NULL_HANDLE;
#endif
};

#ifndef NDEBUG
// Validation layer callback
static VKAPI_ATTR VkBool32 VKAPI_CALL debugCallback(
    VkDebugUtilsMessageSeverityFlagBitsEXT messageSeverity,
    VkDebugUtilsMessageTypeFlagsEXT messageType,
    const VkDebugUtilsMessengerCallbackDataEXT* pCallbackData,
    void* pUserData) {

    if (messageSeverity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) {
        LOGW("Vulkan validation: %s", pCallbackData->pMessage);
    } else {
        LOGI("Vulkan validation: %s", pCallbackData->pMessage);
    }
    return VK_FALSE;
}

// Helper to create debug messenger
static VkResult createDebugUtilsMessenger(
    VkInstance instance,
    const VkDebugUtilsMessengerCreateInfoEXT* pCreateInfo,
    const VkAllocationCallbacks* pAllocator,
    VkDebugUtilsMessengerEXT* pDebugMessenger) {

    auto func = (PFN_vkCreateDebugUtilsMessengerEXT)vkGetInstanceProcAddr(
        instance, "vkCreateDebugUtilsMessengerEXT");
    if (func != nullptr) {
        return func(instance, pCreateInfo, pAllocator, pDebugMessenger);
    }
    return VK_ERROR_EXTENSION_NOT_PRESENT;
}

static void destroyDebugUtilsMessenger(
    VkInstance instance,
    VkDebugUtilsMessengerEXT debugMessenger,
    const VkAllocationCallbacks* pAllocator) {

    auto func = (PFN_vkDestroyDebugUtilsMessengerEXT)vkGetInstanceProcAddr(
        instance, "vkDestroyDebugUtilsMessengerEXT");
    if (func != nullptr) {
        func(instance, debugMessenger, pAllocator);
    }
}
#endif

// Check if validation layers are available
static bool checkValidationLayerSupport(const std::vector<const char*>& validationLayers) {
    uint32_t layerCount;
    vkEnumerateInstanceLayerProperties(&layerCount, nullptr);

    std::vector<VkLayerProperties> availableLayers(layerCount);
    vkEnumerateInstanceLayerProperties(&layerCount, availableLayers.data());

    for (const char* layerName : validationLayers) {
        bool layerFound = false;
        for (const auto& layerProperties : availableLayers) {
            if (strcmp(layerName, layerProperties.layerName) == 0) {
                layerFound = true;
                break;
            }
        }
        if (!layerFound) {
            return false;
        }
    }
    return true;
}

// Create Vulkan instance
static bool createInstance(VulkanContext* ctx) {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Stardroid Awakening";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    appInfo.pEngineName = "StardroidEngine";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 1, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    // Required extensions
    std::vector<const char*> extensions = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
    };

    // Validation layers for debug builds
    std::vector<const char*> validationLayers;
#ifndef NDEBUG
    validationLayers.push_back("VK_LAYER_KHRONOS_validation");
    if (checkValidationLayerSupport(validationLayers)) {
        extensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
        LOGI("Validation layers enabled");
    } else {
        LOGW("Validation layers requested but not available");
        validationLayers.clear();
    }
#endif

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
    createInfo.ppEnabledExtensionNames = extensions.data();
    createInfo.enabledLayerCount = static_cast<uint32_t>(validationLayers.size());
    createInfo.ppEnabledLayerNames = validationLayers.empty() ? nullptr : validationLayers.data();

#ifndef NDEBUG
    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo{};
    if (!validationLayers.empty()) {
        debugCreateInfo.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT;
        debugCreateInfo.messageSeverity =
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
        debugCreateInfo.messageType =
            VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
        debugCreateInfo.pfnUserCallback = debugCallback;
        createInfo.pNext = &debugCreateInfo;
    }
#endif

    VkResult result = vkCreateInstance(&createInfo, nullptr, &ctx->instance);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Vulkan instance: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Vulkan instance created successfully");

#ifndef NDEBUG
    // Create debug messenger after instance
    if (!validationLayers.empty()) {
        VkDebugUtilsMessengerCreateInfoEXT messengerInfo{};
        messengerInfo.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT;
        messengerInfo.messageSeverity =
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
        messengerInfo.messageType =
            VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
        messengerInfo.pfnUserCallback = debugCallback;

        if (createDebugUtilsMessenger(ctx->instance, &messengerInfo, nullptr, &ctx->debugMessenger) != VK_SUCCESS) {
            LOGW("Failed to create debug messenger");
        }
    }
#endif

    return true;
}

// Create Android surface
static bool createSurface(VulkanContext* ctx) {
    VkAndroidSurfaceCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = ctx->nativeWindow;

    VkResult result = vkCreateAndroidSurfaceKHR(ctx->instance, &createInfo, nullptr, &ctx->surface);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Android surface: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Android Vulkan surface created successfully");
    return true;
}

// Find queue families for graphics and present
static bool findQueueFamilies(VulkanContext* ctx, VkPhysicalDevice device) {
    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);

    std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, queueFamilies.data());

    ctx->graphicsQueueFamily = UINT32_MAX;
    ctx->presentQueueFamily = UINT32_MAX;

    for (uint32_t i = 0; i < queueFamilyCount; i++) {
        // Check for graphics support
        if (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            ctx->graphicsQueueFamily = i;
        }

        // Check for present support
        VkBool32 presentSupport = false;
        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, ctx->surface, &presentSupport);
        if (presentSupport) {
            ctx->presentQueueFamily = i;
        }

        // Prefer queue family that supports both
        if (ctx->graphicsQueueFamily != UINT32_MAX &&
            ctx->presentQueueFamily != UINT32_MAX) {
            break;
        }
    }

    return ctx->graphicsQueueFamily != UINT32_MAX && ctx->presentQueueFamily != UINT32_MAX;
}

// Check if device has required extensions
static bool checkDeviceExtensions(VkPhysicalDevice device) {
    uint32_t extensionCount;
    vkEnumerateDeviceExtensionProperties(device, nullptr, &extensionCount, nullptr);

    std::vector<VkExtensionProperties> availableExtensions(extensionCount);
    vkEnumerateDeviceExtensionProperties(device, nullptr, &extensionCount, availableExtensions.data());

    const char* requiredExtension = VK_KHR_SWAPCHAIN_EXTENSION_NAME;
    for (const auto& extension : availableExtensions) {
        if (strcmp(extension.extensionName, requiredExtension) == 0) {
            return true;
        }
    }
    return false;
}

// Select physical device
static bool pickPhysicalDevice(VulkanContext* ctx) {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(ctx->instance, &deviceCount, nullptr);

    if (deviceCount == 0) {
        LOGE("No Vulkan-capable GPU found");
        return false;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(ctx->instance, &deviceCount, devices.data());

    for (const auto& device : devices) {
        VkPhysicalDeviceProperties deviceProperties;
        vkGetPhysicalDeviceProperties(device, &deviceProperties);

        LOGI("Checking device: %s", deviceProperties.deviceName);

        if (!findQueueFamilies(ctx, device)) {
            LOGW("Device %s doesn't have required queue families", deviceProperties.deviceName);
            continue;
        }

        if (!checkDeviceExtensions(device)) {
            LOGW("Device %s doesn't support swapchain", deviceProperties.deviceName);
            continue;
        }

        ctx->physicalDevice = device;
        LOGI("Selected device: %s", deviceProperties.deviceName);
        return true;
    }

    LOGE("No suitable GPU found");
    return false;
}

// Create logical device
static bool createLogicalDevice(VulkanContext* ctx) {
    std::vector<VkDeviceQueueCreateInfo> queueCreateInfos;
    std::vector<uint32_t> uniqueQueueFamilies;

    uniqueQueueFamilies.push_back(ctx->graphicsQueueFamily);
    if (ctx->presentQueueFamily != ctx->graphicsQueueFamily) {
        uniqueQueueFamilies.push_back(ctx->presentQueueFamily);
    }

    float queuePriority = 1.0f;
    for (uint32_t queueFamily : uniqueQueueFamilies) {
        VkDeviceQueueCreateInfo queueCreateInfo{};
        queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueCreateInfo.queueFamilyIndex = queueFamily;
        queueCreateInfo.queueCount = 1;
        queueCreateInfo.pQueuePriorities = &queuePriority;
        queueCreateInfos.push_back(queueCreateInfo);
    }

    VkPhysicalDeviceFeatures deviceFeatures{};

    std::vector<const char*> deviceExtensions = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.queueCreateInfoCount = static_cast<uint32_t>(queueCreateInfos.size());
    createInfo.pQueueCreateInfos = queueCreateInfos.data();
    createInfo.pEnabledFeatures = &deviceFeatures;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();

    VkResult result = vkCreateDevice(ctx->physicalDevice, &createInfo, nullptr, &ctx->device);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create logical device: %s (%d)", vkResultToString(result), result);
        return false;
    }

    vkGetDeviceQueue(ctx->device, ctx->graphicsQueueFamily, 0, &ctx->graphicsQueue);
    vkGetDeviceQueue(ctx->device, ctx->presentQueueFamily, 0, &ctx->presentQueue);

    LOGI("Logical device created successfully");
    return true;
}

// Cleanup Vulkan resources
static void cleanup(VulkanContext* ctx) {
    if (ctx->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(ctx->device);
        vkDestroyDevice(ctx->device, nullptr);
        ctx->device = VK_NULL_HANDLE;
    }

    if (ctx->surface != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(ctx->instance, ctx->surface, nullptr);
        ctx->surface = VK_NULL_HANDLE;
    }

#ifndef NDEBUG
    if (ctx->debugMessenger != VK_NULL_HANDLE) {
        destroyDebugUtilsMessenger(ctx->instance, ctx->debugMessenger, nullptr);
        ctx->debugMessenger = VK_NULL_HANDLE;
    }
#endif

    if (ctx->instance != VK_NULL_HANDLE) {
        vkDestroyInstance(ctx->instance, nullptr);
        ctx->instance = VK_NULL_HANDLE;
    }

    if (ctx->nativeWindow != nullptr) {
        ANativeWindow_release(ctx->nativeWindow);
        ctx->nativeWindow = nullptr;
    }

    ctx->initialized = false;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeInit(
    JNIEnv* env, jobject obj, jobject surface) {

    LOGI("Initializing Vulkan...");

    auto* ctx = new VulkanContext();

    // Get native window from Android Surface
    ctx->nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (ctx->nativeWindow == nullptr) {
        LOGE("Failed to get native window from surface");
        delete ctx;
        return 0;
    }

    ctx->width = ANativeWindow_getWidth(ctx->nativeWindow);
    ctx->height = ANativeWindow_getHeight(ctx->nativeWindow);
    LOGI("Surface size: %dx%d", ctx->width, ctx->height);

    // Create Vulkan instance
    if (!createInstance(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create Android surface
    if (!createSurface(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Pick physical device
    if (!pickPhysicalDevice(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create logical device
    if (!createLogicalDevice(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    ctx->initialized = true;
    LOGI("Vulkan initialization complete!");

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeRender(
    JNIEnv* env, jobject obj, jlong contextHandle, jfloat angle) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized) {
        return;
    }

    // TODO: Implement actual rendering in Phase 5+
    // For now, just log occasionally to show we're alive
    if (++ctx->frameCount % LOG_FRAME_INTERVAL == 0) {
        LOGI("Rendered %d frames, angle=%.1f", ctx->frameCount, angle);
    }
}

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeResize(
    JNIEnv* env, jobject obj, jlong contextHandle, jint width, jint height) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr) {
        return;
    }

    if (ctx->width != width || ctx->height != height) {
        LOGI("Surface resized: %dx%d -> %dx%d", ctx->width, ctx->height, width, height);
        ctx->width = width;
        ctx->height = height;
        // TODO: Recreate swapchain in Phase 5
    }
}

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeDestroy(
    JNIEnv* env, jobject obj, jlong contextHandle) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr) {
        return;
    }

    LOGI("Destroying Vulkan context...");
    cleanup(ctx);
    delete ctx;
    LOGI("Vulkan context destroyed");
}

} // extern "C"
