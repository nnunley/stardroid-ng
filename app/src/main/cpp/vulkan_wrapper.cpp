#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <vector>
#include <string>
#include <cstring>
#include <algorithm>
#include <memory>
#include "shaders.h"
#include "math_utils.h"
#include "vulkan_raii.h"

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

// Maximum number of frames that can be in flight at once
constexpr uint32_t MAX_FRAMES_IN_FLIGHT = 2;

// Vulkan context holds all Vulkan objects
// IMPORTANT: Member declaration order determines reverse destruction order.
// Members declared FIRST are destroyed LAST. This order matches the required
// Vulkan destruction sequence where child objects must be destroyed before parents.
struct VulkanContext {
    // === Destroyed LAST (declared first) ===
    // Platform resources
    UniqueNativeWindow nativeWindow;

    // Instance-level (destroyed after device-level)
    UniqueInstance instance;
#ifndef NDEBUG
    UniqueDebugMessenger debugMessenger;
#endif
    UniqueSurface surface;

    // Device (destroyed after all device-dependent resources)
    UniqueDevice device;

    // Non-owning handles (no destruction needed)
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue presentQueue = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = UINT32_MAX;
    uint32_t presentQueueFamily = UINT32_MAX;

    // === Device-dependent resources (destroyed BEFORE device) ===
    // Swapchain and image views
    UniqueSwapchain swapchain;
    VkFormat swapchainFormat = VK_FORMAT_UNDEFINED;
    VkExtent2D swapchainExtent = {0, 0};
    std::vector<VkImage> swapchainImages;  // Owned by swapchain, no explicit destroy
    std::vector<UniqueImageView> swapchainImageViews;

    // Render pass
    UniqueRenderPass renderPass;

    // Framebuffers (depend on render pass and image views)
    std::vector<UniqueFramebuffer> framebuffers;

    // Descriptor resources
    UniqueDescriptorSetLayout descriptorSetLayout;
    UniqueDescriptorPool descriptorPool;
    VkDescriptorSet descriptorSet = VK_NULL_HANDLE;  // Freed with pool

    // Buffers (uniform, vertex, dynamic)
    UniqueBuffer uniformBuffer;
    UniqueDeviceMemory uniformBufferMemory;
    void* uniformBufferMapped = nullptr;

    UniqueBuffer vertexBuffer;
    UniqueDeviceMemory vertexBufferMemory;

    UniqueBuffer dynamicVertexBuffer;
    UniqueDeviceMemory dynamicVertexBufferMemory;
    void* dynamicVertexBufferMapped = nullptr;
    size_t dynamicVertexBufferSize = 0;
    size_t dynamicVertexBufferOffset = 0;

    // Pipeline
    UniquePipelineLayout pipelineLayout;
    UniquePipeline trianglePipeline;
    UniquePipeline linePipeline;
    UniquePipeline pointPipeline;

    // Command resources
    UniqueCommandPool commandPool;
    std::vector<VkCommandBuffer> commandBuffers;  // Freed with pool

    // Synchronization (destroyed FIRST among device resources)
    std::vector<UniqueSemaphore> imageAvailableSemaphores;
    std::vector<UniqueSemaphore> renderFinishedSemaphores;
    std::vector<UniqueFence> inFlightFences;
    uint32_t currentFrame = 0;

    // === Non-Vulkan state ===
    int width = 0;
    int height = 0;
    bool initialized = false;
    int frameCount = 0;

    // Cached matrices (column-major, 16 floats each)
    float viewMatrix[16];
    float projectionMatrix[16];

    // Frame state
    bool inFrame = false;
    uint32_t currentImageIndex = 0;

    // Helper to get raw device handle for Vulkan API calls
    VkDevice getDevice() const { return device.get(); }
    VkInstance getInstance() const { return instance.get(); }
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

// RAII wrapper callback (declared in vulkan_raii.h)
void destroyDebugUtilsMessengerEXT(VkInstance instance, VkDebugUtilsMessengerEXT messenger) {
    destroyDebugUtilsMessenger(instance, messenger, nullptr);
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

    VkInstance instance;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Vulkan instance: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->instance = UniqueInstance(instance);

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

        VkDebugUtilsMessengerEXT debugMessenger;
        if (createDebugUtilsMessenger(ctx->instance.get(), &messengerInfo, nullptr, &debugMessenger) != VK_SUCCESS) {
            LOGW("Failed to create debug messenger");
        } else {
            ctx->debugMessenger = UniqueDebugMessenger(debugMessenger, DebugMessengerDeleter{ctx->instance.get()});
        }
    }
#endif

    return true;
}

// Create Android surface
static bool createSurface(VulkanContext* ctx) {
    VkAndroidSurfaceCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = ctx->nativeWindow.get();

    VkSurfaceKHR surface;
    VkResult result = vkCreateAndroidSurfaceKHR(ctx->instance.get(), &createInfo, nullptr, &surface);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create Android surface: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->surface = UniqueSurface(surface, SurfaceDeleter{ctx->instance.get()});

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
        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, ctx->surface.get(), &presentSupport);
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
    vkEnumeratePhysicalDevices(ctx->instance.get(), &deviceCount, nullptr);

    if (deviceCount == 0) {
        LOGE("No Vulkan-capable GPU found");
        return false;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(ctx->instance.get(), &deviceCount, devices.data());

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

    VkDevice device;
    VkResult result = vkCreateDevice(ctx->physicalDevice, &createInfo, nullptr, &device);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create logical device: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->device = UniqueDevice(device);

    vkGetDeviceQueue(ctx->device.get(), ctx->graphicsQueueFamily, 0, &ctx->graphicsQueue);
    vkGetDeviceQueue(ctx->device.get(), ctx->presentQueueFamily, 0, &ctx->presentQueue);

    LOGI("Logical device created successfully");
    return true;
}

// Helper struct for swapchain support details
struct SwapchainSupportDetails {
    VkSurfaceCapabilitiesKHR capabilities;
    std::vector<VkSurfaceFormatKHR> formats;
    std::vector<VkPresentModeKHR> presentModes;
};

// Query swapchain support details
static SwapchainSupportDetails querySwapchainSupport(VkPhysicalDevice device, VkSurfaceKHR surface) {
    SwapchainSupportDetails details;

    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, &details.capabilities);

    uint32_t formatCount;
    vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, &formatCount, nullptr);
    if (formatCount != 0) {
        details.formats.resize(formatCount);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, &formatCount, details.formats.data());
    }

    uint32_t presentModeCount;
    vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, &presentModeCount, nullptr);
    if (presentModeCount != 0) {
        details.presentModes.resize(presentModeCount);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, &presentModeCount, details.presentModes.data());
    }

    return details;
}

// Choose optimal surface format (prefer BGRA8 with SRGB)
static VkSurfaceFormatKHR chooseSwapSurfaceFormat(const std::vector<VkSurfaceFormatKHR>& formats) {
    for (const auto& format : formats) {
        if (format.format == VK_FORMAT_B8G8R8A8_UNORM &&
            format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            return format;
        }
    }
    // Fall back to first available format
    return formats[0];
}

// Choose present mode (prefer MAILBOX for low latency, fall back to FIFO)
static VkPresentModeKHR chooseSwapPresentMode(const std::vector<VkPresentModeKHR>& presentModes) {
    for (const auto& mode : presentModes) {
        if (mode == VK_PRESENT_MODE_MAILBOX_KHR) {
            LOGI("Using MAILBOX present mode (triple buffering)");
            return mode;
        }
    }
    LOGI("Using FIFO present mode (vsync)");
    return VK_PRESENT_MODE_FIFO_KHR;
}

// Choose swap extent (resolution of swapchain images)
static VkExtent2D chooseSwapExtent(const VkSurfaceCapabilitiesKHR& capabilities, uint32_t width, uint32_t height) {
    if (capabilities.currentExtent.width != UINT32_MAX) {
        return capabilities.currentExtent;
    }

    VkExtent2D actualExtent = {width, height};
    actualExtent.width = std::max(capabilities.minImageExtent.width,
                                   std::min(capabilities.maxImageExtent.width, actualExtent.width));
    actualExtent.height = std::max(capabilities.minImageExtent.height,
                                    std::min(capabilities.maxImageExtent.height, actualExtent.height));
    return actualExtent;
}

// Create swapchain
static bool createSwapchain(VulkanContext* ctx) {
    SwapchainSupportDetails support = querySwapchainSupport(ctx->physicalDevice, ctx->surface.get());

    if (support.formats.empty() || support.presentModes.empty()) {
        LOGE("Swapchain support inadequate");
        return false;
    }

    VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(support.formats);
    VkPresentModeKHR presentMode = chooseSwapPresentMode(support.presentModes);
    VkExtent2D extent = chooseSwapExtent(support.capabilities, ctx->width, ctx->height);

    // Request one more image than minimum for triple buffering
    uint32_t imageCount = support.capabilities.minImageCount + 1;
    if (support.capabilities.maxImageCount > 0 && imageCount > support.capabilities.maxImageCount) {
        imageCount = support.capabilities.maxImageCount;
    }

    VkSwapchainCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    createInfo.surface = ctx->surface.get();
    createInfo.minImageCount = imageCount;
    createInfo.imageFormat = surfaceFormat.format;
    createInfo.imageColorSpace = surfaceFormat.colorSpace;
    createInfo.imageExtent = extent;
    createInfo.imageArrayLayers = 1;
    createInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

    uint32_t queueFamilyIndices[] = {ctx->graphicsQueueFamily, ctx->presentQueueFamily};
    if (ctx->graphicsQueueFamily != ctx->presentQueueFamily) {
        createInfo.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
        createInfo.queueFamilyIndexCount = 2;
        createInfo.pQueueFamilyIndices = queueFamilyIndices;
    } else {
        createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    }

    createInfo.preTransform = support.capabilities.currentTransform;

    // Choose composite alpha - prefer INHERIT, fall back to OPAQUE
    VkCompositeAlphaFlagBitsKHR compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (support.capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) {
        compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    } else if (support.capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) {
        compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    } else if (support.capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR) {
        compositeAlpha = VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
    } else if (support.capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR) {
        compositeAlpha = VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
    }
    createInfo.compositeAlpha = compositeAlpha;
    createInfo.presentMode = presentMode;
    createInfo.clipped = VK_TRUE;
    createInfo.oldSwapchain = VK_NULL_HANDLE;

    VkSwapchainKHR swapchain;
    VkResult result = vkCreateSwapchainKHR(ctx->device.get(), &createInfo, nullptr, &swapchain);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create swapchain: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->swapchain = UniqueSwapchain(swapchain, SwapchainDeleter{ctx->device.get()});

    ctx->swapchainFormat = surfaceFormat.format;
    ctx->swapchainExtent = extent;

    // Get swapchain images
    vkGetSwapchainImagesKHR(ctx->device.get(), ctx->swapchain.get(), &imageCount, nullptr);
    ctx->swapchainImages.resize(imageCount);
    vkGetSwapchainImagesKHR(ctx->device.get(), ctx->swapchain.get(), &imageCount, ctx->swapchainImages.data());

    LOGI("Swapchain created: %dx%d, %d images, format=%d",
         extent.width, extent.height, imageCount, surfaceFormat.format);

    return true;
}

// Create image views for swapchain images
static bool createImageViews(VulkanContext* ctx) {
    ctx->swapchainImageViews.clear();
    ctx->swapchainImageViews.reserve(ctx->swapchainImages.size());

    for (size_t i = 0; i < ctx->swapchainImages.size(); i++) {
        VkImageViewCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        createInfo.image = ctx->swapchainImages[i];
        createInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        createInfo.format = ctx->swapchainFormat;
        createInfo.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        createInfo.subresourceRange.baseMipLevel = 0;
        createInfo.subresourceRange.levelCount = 1;
        createInfo.subresourceRange.baseArrayLayer = 0;
        createInfo.subresourceRange.layerCount = 1;

        VkImageView imageView;
        VkResult result = vkCreateImageView(ctx->device.get(), &createInfo, nullptr, &imageView);
        if (result != VK_SUCCESS) {
            LOGE("Failed to create image view %zu: %s (%d)", i, vkResultToString(result), result);
            return false;
        }
        ctx->swapchainImageViews.push_back(UniqueImageView(imageView, ImageViewDeleter{ctx->device.get()}));
    }

    LOGI("Created %zu image views", ctx->swapchainImageViews.size());
    return true;
}

// Create render pass
static bool createRenderPass(VulkanContext* ctx) {
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = ctx->swapchainFormat;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference colorAttachmentRef{};
    colorAttachmentRef.attachment = 0;
    colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorAttachmentRef;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.srcAccessMask = 0;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    createInfo.attachmentCount = 1;
    createInfo.pAttachments = &colorAttachment;
    createInfo.subpassCount = 1;
    createInfo.pSubpasses = &subpass;
    createInfo.dependencyCount = 1;
    createInfo.pDependencies = &dependency;

    VkRenderPass renderPass;
    VkResult result = vkCreateRenderPass(ctx->device.get(), &createInfo, nullptr, &renderPass);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create render pass: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->renderPass = UniqueRenderPass(renderPass, RenderPassDeleter{ctx->device.get()});

    LOGI("Render pass created");
    return true;
}

// Create framebuffers
static bool createFramebuffers(VulkanContext* ctx) {
    ctx->framebuffers.clear();
    ctx->framebuffers.reserve(ctx->swapchainImageViews.size());

    for (size_t i = 0; i < ctx->swapchainImageViews.size(); i++) {
        VkImageView attachments[] = {ctx->swapchainImageViews[i].get()};

        VkFramebufferCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        createInfo.renderPass = ctx->renderPass.get();
        createInfo.attachmentCount = 1;
        createInfo.pAttachments = attachments;
        createInfo.width = ctx->swapchainExtent.width;
        createInfo.height = ctx->swapchainExtent.height;
        createInfo.layers = 1;

        VkFramebuffer framebuffer;
        VkResult result = vkCreateFramebuffer(ctx->device.get(), &createInfo, nullptr, &framebuffer);
        if (result != VK_SUCCESS) {
            LOGE("Failed to create framebuffer %zu: %s (%d)", i, vkResultToString(result), result);
            return false;
        }
        ctx->framebuffers.push_back(UniqueFramebuffer(framebuffer, FramebufferDeleter{ctx->device.get()}));
    }

    LOGI("Created %zu framebuffers", ctx->framebuffers.size());
    return true;
}

// Create command pool
static bool createCommandPool(VulkanContext* ctx) {
    VkCommandPoolCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    createInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    createInfo.queueFamilyIndex = ctx->graphicsQueueFamily;

    VkCommandPool commandPool;
    VkResult result = vkCreateCommandPool(ctx->device.get(), &createInfo, nullptr, &commandPool);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create command pool: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->commandPool = UniqueCommandPool(commandPool, CommandPoolDeleter{ctx->device.get()});

    LOGI("Command pool created");
    return true;
}

// Create command buffers
static bool createCommandBuffers(VulkanContext* ctx) {
    ctx->commandBuffers.resize(MAX_FRAMES_IN_FLIGHT);

    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = ctx->commandPool.get();
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = static_cast<uint32_t>(ctx->commandBuffers.size());

    VkResult result = vkAllocateCommandBuffers(ctx->device.get(), &allocInfo, ctx->commandBuffers.data());
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate command buffers: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Allocated %zu command buffers", ctx->commandBuffers.size());
    return true;
}

// Create synchronization objects
static bool createSyncObjects(VulkanContext* ctx) {
    ctx->imageAvailableSemaphores.clear();
    ctx->renderFinishedSemaphores.clear();
    ctx->inFlightFences.clear();
    ctx->imageAvailableSemaphores.reserve(MAX_FRAMES_IN_FLIGHT);
    ctx->renderFinishedSemaphores.reserve(MAX_FRAMES_IN_FLIGHT);
    ctx->inFlightFences.reserve(MAX_FRAMES_IN_FLIGHT);

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    for (size_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        VkSemaphore imageAvailableSemaphore, renderFinishedSemaphore;
        VkFence inFlightFence;
        if (vkCreateSemaphore(ctx->device.get(), &semaphoreInfo, nullptr, &imageAvailableSemaphore) != VK_SUCCESS ||
            vkCreateSemaphore(ctx->device.get(), &semaphoreInfo, nullptr, &renderFinishedSemaphore) != VK_SUCCESS ||
            vkCreateFence(ctx->device.get(), &fenceInfo, nullptr, &inFlightFence) != VK_SUCCESS) {
            LOGE("Failed to create synchronization objects for frame %zu", i);
            return false;
        }
        ctx->imageAvailableSemaphores.push_back(UniqueSemaphore(imageAvailableSemaphore, SemaphoreDeleter{ctx->device.get()}));
        ctx->renderFinishedSemaphores.push_back(UniqueSemaphore(renderFinishedSemaphore, SemaphoreDeleter{ctx->device.get()}));
        ctx->inFlightFences.push_back(UniqueFence(inFlightFence, FenceDeleter{ctx->device.get()}));
    }

    LOGI("Created synchronization objects");
    return true;
}

// Forward declaration (defined later in file)
static uint32_t findMemoryType(VulkanContext* ctx, uint32_t typeFilter, VkMemoryPropertyFlags properties);

// Create descriptor set layout for uniform buffer
static bool createDescriptorSetLayout(VulkanContext* ctx) {
    VkDescriptorSetLayoutBinding uboLayoutBinding{};
    uboLayoutBinding.binding = 0;
    uboLayoutBinding.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    uboLayoutBinding.descriptorCount = 1;
    uboLayoutBinding.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    uboLayoutBinding.pImmutableSamplers = nullptr;

    VkDescriptorSetLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = 1;
    layoutInfo.pBindings = &uboLayoutBinding;

    VkDescriptorSetLayout descriptorSetLayout;
    VkResult result = vkCreateDescriptorSetLayout(ctx->device.get(), &layoutInfo, nullptr, &descriptorSetLayout);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create descriptor set layout: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->descriptorSetLayout = UniqueDescriptorSetLayout(descriptorSetLayout, DescriptorSetLayoutDeleter{ctx->device.get()});

    LOGI("Descriptor set layout created");
    return true;
}

// Create uniform buffer for view/projection matrices
static bool createUniformBuffer(VulkanContext* ctx) {
    VkDeviceSize bufferSize = sizeof(float) * 32; // 2 mat4 = 128 bytes

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = bufferSize;
    bufferInfo.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer uniformBuffer;
    VkResult result = vkCreateBuffer(ctx->device.get(), &bufferInfo, nullptr, &uniformBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create uniform buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->uniformBuffer = UniqueBuffer(uniformBuffer, BufferDeleter{ctx->device.get()});

    VkMemoryRequirements memRequirements;
    vkGetBufferMemoryRequirements(ctx->device.get(), ctx->uniformBuffer.get(), &memRequirements);

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memRequirements.size;
    allocInfo.memoryTypeIndex = findMemoryType(ctx, memRequirements.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("Failed to find suitable memory type for uniform buffer");
        return false;
    }

    VkDeviceMemory uniformBufferMemory;
    result = vkAllocateMemory(ctx->device.get(), &allocInfo, nullptr, &uniformBufferMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate uniform buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->uniformBufferMemory = UniqueDeviceMemory(uniformBufferMemory, DeviceMemoryDeleter{ctx->device.get()});

    result = vkBindBufferMemory(ctx->device.get(), ctx->uniformBuffer.get(), ctx->uniformBufferMemory.get(), 0);
    if (result != VK_SUCCESS) {
        LOGE("Failed to bind uniform buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Persistently map the buffer
    result = vkMapMemory(ctx->device.get(), ctx->uniformBufferMemory.get(), 0, bufferSize, 0, &ctx->uniformBufferMapped);
    if (result != VK_SUCCESS) {
        LOGE("Failed to map uniform buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Initialize with identity matrices
    math::identity(ctx->viewMatrix);
    math::identity(ctx->projectionMatrix);
    memcpy(ctx->uniformBufferMapped, ctx->viewMatrix, sizeof(float) * 16);
    memcpy(static_cast<char*>(ctx->uniformBufferMapped) + sizeof(float) * 16, ctx->projectionMatrix, sizeof(float) * 16);

    LOGI("Uniform buffer created (%zu bytes, persistently mapped)", (size_t)bufferSize);
    return true;
}

// Create descriptor pool and allocate descriptor set
static bool createDescriptorPool(VulkanContext* ctx) {
    VkDescriptorPoolSize poolSize{};
    poolSize.type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    poolSize.descriptorCount = 1;

    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    poolInfo.maxSets = 1;

    VkDescriptorPool descriptorPool;
    VkResult result = vkCreateDescriptorPool(ctx->device.get(), &poolInfo, nullptr, &descriptorPool);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create descriptor pool: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->descriptorPool = UniqueDescriptorPool(descriptorPool, DescriptorPoolDeleter{ctx->device.get()});

    // Allocate descriptor set
    VkDescriptorSetLayout descriptorSetLayout = ctx->descriptorSetLayout.get();
    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = ctx->descriptorPool.get();
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descriptorSetLayout;

    result = vkAllocateDescriptorSets(ctx->device.get(), &allocInfo, &ctx->descriptorSet);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate descriptor set: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Update descriptor set to point to uniform buffer
    VkDescriptorBufferInfo bufferInfo{};
    bufferInfo.buffer = ctx->uniformBuffer.get();
    bufferInfo.offset = 0;
    bufferInfo.range = sizeof(float) * 32; // 2 mat4

    VkWriteDescriptorSet descriptorWrite{};
    descriptorWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    descriptorWrite.dstSet = ctx->descriptorSet;
    descriptorWrite.dstBinding = 0;
    descriptorWrite.dstArrayElement = 0;
    descriptorWrite.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    descriptorWrite.descriptorCount = 1;
    descriptorWrite.pBufferInfo = &bufferInfo;

    vkUpdateDescriptorSets(ctx->device.get(), 1, &descriptorWrite, 0, nullptr);

    LOGI("Descriptor pool and set created");
    return true;
}

// Create dynamic vertex buffer (512KB for star catalog)
static bool createDynamicVertexBuffer(VulkanContext* ctx) {
    ctx->dynamicVertexBufferSize = 512 * 1024; // 512KB (enough for ~18k stars)

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = ctx->dynamicVertexBufferSize;
    bufferInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer dynamicVertexBuffer;
    VkResult result = vkCreateBuffer(ctx->device.get(), &bufferInfo, nullptr, &dynamicVertexBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create dynamic vertex buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->dynamicVertexBuffer = UniqueBuffer(dynamicVertexBuffer, BufferDeleter{ctx->device.get()});

    VkMemoryRequirements memRequirements;
    vkGetBufferMemoryRequirements(ctx->device.get(), ctx->dynamicVertexBuffer.get(), &memRequirements);

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memRequirements.size;
    allocInfo.memoryTypeIndex = findMemoryType(ctx, memRequirements.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("Failed to find suitable memory type for dynamic vertex buffer");
        return false;
    }

    VkDeviceMemory dynamicVertexBufferMemory;
    result = vkAllocateMemory(ctx->device.get(), &allocInfo, nullptr, &dynamicVertexBufferMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate dynamic vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->dynamicVertexBufferMemory = UniqueDeviceMemory(dynamicVertexBufferMemory, DeviceMemoryDeleter{ctx->device.get()});

    result = vkBindBufferMemory(ctx->device.get(), ctx->dynamicVertexBuffer.get(), ctx->dynamicVertexBufferMemory.get(), 0);
    if (result != VK_SUCCESS) {
        LOGE("Failed to bind dynamic vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Persistently map
    result = vkMapMemory(ctx->device.get(), ctx->dynamicVertexBufferMemory.get(), 0, ctx->dynamicVertexBufferSize, 0, &ctx->dynamicVertexBufferMapped);
    if (result != VK_SUCCESS) {
        LOGE("Failed to map dynamic vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    ctx->dynamicVertexBufferOffset = 0;

    LOGI("Dynamic vertex buffer created (%zu bytes, persistently mapped)", ctx->dynamicVertexBufferSize);
    return true;
}

// Create shader module from SPIR-V bytecode
static VkShaderModule createShaderModule(VulkanContext* ctx, const unsigned char* code, unsigned int codeSize) {
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = codeSize;
    createInfo.pCode = reinterpret_cast<const uint32_t*>(code);

    VkShaderModule shaderModule;
    VkResult result = vkCreateShaderModule(ctx->device.get(), &createInfo, nullptr, &shaderModule);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create shader module: %s (%d)", vkResultToString(result), result);
        return VK_NULL_HANDLE;
    }

    return shaderModule;
}

// Create a single graphics pipeline for a specific topology
// Assumes pipeline layout already exists
static UniquePipeline createPipelineForTopology(VulkanContext* ctx, VkPrimitiveTopology topology,
                                                 VkShaderModule vertShaderModule, VkShaderModule fragShaderModule) {
    // Shader stages
    VkPipelineShaderStageCreateInfo vertShaderStageInfo{};
    vertShaderStageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    vertShaderStageInfo.stage = VK_SHADER_STAGE_VERTEX_BIT;
    vertShaderStageInfo.module = vertShaderModule;
    vertShaderStageInfo.pName = "main";

    VkPipelineShaderStageCreateInfo fragShaderStageInfo{};
    fragShaderStageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    fragShaderStageInfo.stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    fragShaderStageInfo.module = fragShaderModule;
    fragShaderStageInfo.pName = "main";

    VkPipelineShaderStageCreateInfo shaderStages[] = {vertShaderStageInfo, fragShaderStageInfo};

    // Vertex input: position (vec3) and color (vec4) = 7 floats per vertex
    VkVertexInputBindingDescription bindingDescription{};
    bindingDescription.binding = 0;
    bindingDescription.stride = sizeof(float) * 7; // vec3 position + vec4 color
    bindingDescription.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription attributeDescriptions[2] = {};
    // Position (vec3)
    attributeDescriptions[0].binding = 0;
    attributeDescriptions[0].location = 0;
    attributeDescriptions[0].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributeDescriptions[0].offset = 0;
    // Color (vec4)
    attributeDescriptions[1].binding = 0;
    attributeDescriptions[1].location = 1;
    attributeDescriptions[1].format = VK_FORMAT_R32G32B32A32_SFLOAT;
    attributeDescriptions[1].offset = sizeof(float) * 3;

    VkPipelineVertexInputStateCreateInfo vertexInputInfo{};
    vertexInputInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInputInfo.vertexBindingDescriptionCount = 1;
    vertexInputInfo.pVertexBindingDescriptions = &bindingDescription;
    vertexInputInfo.vertexAttributeDescriptionCount = 2;
    vertexInputInfo.pVertexAttributeDescriptions = attributeDescriptions;

    // Input assembly - use the topology parameter
    VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
    inputAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = topology;
    inputAssembly.primitiveRestartEnable = VK_FALSE;

    // Dynamic states for viewport and scissor
    VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamicState{};
    dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamicState.dynamicStateCount = 2;
    dynamicState.pDynamicStates = dynamicStates;

    // Viewport state (will be set dynamically)
    VkPipelineViewportStateCreateInfo viewportState{};
    viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.scissorCount = 1;

    // Rasterizer - disable culling for points and lines
    VkPipelineRasterizationStateCreateInfo rasterizer{};
    rasterizer.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterizer.depthClampEnable = VK_FALSE;
    rasterizer.rasterizerDiscardEnable = VK_FALSE;
    rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
    rasterizer.lineWidth = 1.0f;
    // Disable culling for points and lines (they have no front/back face)
    if (topology == VK_PRIMITIVE_TOPOLOGY_POINT_LIST || topology == VK_PRIMITIVE_TOPOLOGY_LINE_LIST) {
        rasterizer.cullMode = VK_CULL_MODE_NONE;
    } else {
        rasterizer.cullMode = VK_CULL_MODE_BACK_BIT;
    }
    rasterizer.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    rasterizer.depthBiasEnable = VK_FALSE;

    // Multisampling (disabled)
    VkPipelineMultisampleStateCreateInfo multisampling{};
    multisampling.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisampling.sampleShadingEnable = VK_FALSE;
    multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    // Color blending
    VkPipelineColorBlendAttachmentState colorBlendAttachment{};
    colorBlendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    colorBlendAttachment.blendEnable = VK_FALSE;

    VkPipelineColorBlendStateCreateInfo colorBlending{};
    colorBlending.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    colorBlending.logicOpEnable = VK_FALSE;
    colorBlending.attachmentCount = 1;
    colorBlending.pAttachments = &colorBlendAttachment;

    // Create graphics pipeline
    VkGraphicsPipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipelineInfo.stageCount = 2;
    pipelineInfo.pStages = shaderStages;
    pipelineInfo.pVertexInputState = &vertexInputInfo;
    pipelineInfo.pInputAssemblyState = &inputAssembly;
    pipelineInfo.pViewportState = &viewportState;
    pipelineInfo.pRasterizationState = &rasterizer;
    pipelineInfo.pMultisampleState = &multisampling;
    pipelineInfo.pColorBlendState = &colorBlending;
    pipelineInfo.pDynamicState = &dynamicState;
    pipelineInfo.layout = ctx->pipelineLayout.get();
    pipelineInfo.renderPass = ctx->renderPass.get();
    pipelineInfo.subpass = 0;

    VkPipeline pipeline;
    VkResult result = vkCreateGraphicsPipelines(ctx->device.get(), VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline);

    if (result != VK_SUCCESS) {
        LOGE("Failed to create graphics pipeline for topology %d: %s (%d)", topology, vkResultToString(result), result);
        return UniquePipeline{};
    }

    return UniquePipeline(pipeline, PipelineDeleter{ctx->device.get()});
}

// Create all graphics pipelines (triangles, lines, points)
static bool createGraphicsPipelines(VulkanContext* ctx) {
    // Create shader modules (shared by all pipelines)
    VkShaderModule vertShaderModule = createShaderModule(ctx, triangle_vert_spv, triangle_vert_spv_len);
    VkShaderModule fragShaderModule = createShaderModule(ctx, triangle_frag_spv, triangle_frag_spv_len);

    if (vertShaderModule == VK_NULL_HANDLE || fragShaderModule == VK_NULL_HANDLE) {
        LOGE("Failed to create shader modules");
        if (vertShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(ctx->device.get(), vertShaderModule, nullptr);
        }
        if (fragShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(ctx->device.get(), fragShaderModule, nullptr);
        }
        return false;
    }

    LOGI("Shader modules created");

    // Create pipeline layout (shared by all pipelines)
    VkPushConstantRange pushConstantRange{};
    pushConstantRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    pushConstantRange.offset = 0;
    pushConstantRange.size = sizeof(float) * 16; // mat4

    VkDescriptorSetLayout descriptorSetLayout = ctx->descriptorSetLayout.get();
    VkPipelineLayoutCreateInfo pipelineLayoutInfo{};
    pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pipelineLayoutInfo.setLayoutCount = 1;
    pipelineLayoutInfo.pSetLayouts = &descriptorSetLayout;
    pipelineLayoutInfo.pushConstantRangeCount = 1;
    pipelineLayoutInfo.pPushConstantRanges = &pushConstantRange;

    VkPipelineLayout pipelineLayout;
    VkResult result = vkCreatePipelineLayout(ctx->device.get(), &pipelineLayoutInfo, nullptr, &pipelineLayout);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create pipeline layout: %s (%d)", vkResultToString(result), result);
        vkDestroyShaderModule(ctx->device.get(), vertShaderModule, nullptr);
        vkDestroyShaderModule(ctx->device.get(), fragShaderModule, nullptr);
        return false;
    }
    ctx->pipelineLayout = UniquePipelineLayout(pipelineLayout, PipelineLayoutDeleter{ctx->device.get()});

    LOGI("Pipeline layout created");

    // Create pipelines for each topology
    ctx->trianglePipeline = createPipelineForTopology(ctx, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, vertShaderModule, fragShaderModule);
    ctx->linePipeline = createPipelineForTopology(ctx, VK_PRIMITIVE_TOPOLOGY_LINE_LIST, vertShaderModule, fragShaderModule);
    ctx->pointPipeline = createPipelineForTopology(ctx, VK_PRIMITIVE_TOPOLOGY_POINT_LIST, vertShaderModule, fragShaderModule);

    // Clean up shader modules (no longer needed after pipeline creation)
    vkDestroyShaderModule(ctx->device.get(), vertShaderModule, nullptr);
    vkDestroyShaderModule(ctx->device.get(), fragShaderModule, nullptr);

    if (!ctx->trianglePipeline || !ctx->linePipeline || !ctx->pointPipeline) {
        LOGE("Failed to create one or more graphics pipelines");
        return false;
    }

    LOGI("All graphics pipelines created (triangles, lines, points)");
    return true;
}

// Find memory type that satisfies requirements
static uint32_t findMemoryType(VulkanContext* ctx, uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memProperties;
    vkGetPhysicalDeviceMemoryProperties(ctx->physicalDevice, &memProperties);

    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++) {
        if ((typeFilter & (1 << i)) &&
            (memProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }

    LOGE("Failed to find suitable memory type");
    return UINT32_MAX;
}

// Triangle vertex data: position (vec3) + color (vec4) = 7 floats per vertex
static const float triangleVertices[] = {
    // Position (x, y, z)    Color (r, g, b, a)
     0.0f, -0.5f, 0.0f,      1.0f, 0.0f, 0.0f, 1.0f,  // Top vertex - red
    -0.5f,  0.5f, 0.0f,      0.0f, 1.0f, 0.0f, 1.0f,  // Bottom left - green
     0.5f,  0.5f, 0.0f,      0.0f, 0.0f, 1.0f, 1.0f,  // Bottom right - blue
};

// Create vertex buffer
static bool createVertexBuffer(VulkanContext* ctx) {
    VkDeviceSize bufferSize = sizeof(triangleVertices);

    // Create buffer
    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = bufferSize;
    bufferInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer vertexBuffer;
    VkResult result = vkCreateBuffer(ctx->device.get(), &bufferInfo, nullptr, &vertexBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create vertex buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->vertexBuffer = UniqueBuffer(vertexBuffer, BufferDeleter{ctx->device.get()});

    // Get memory requirements
    VkMemoryRequirements memRequirements;
    vkGetBufferMemoryRequirements(ctx->device.get(), ctx->vertexBuffer.get(), &memRequirements);

    // Allocate memory (host visible so we can map and copy)
    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memRequirements.size;
    allocInfo.memoryTypeIndex = findMemoryType(ctx, memRequirements.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("Failed to find suitable memory type for vertex buffer");
        return false;
    }

    VkDeviceMemory vertexBufferMemory;
    result = vkAllocateMemory(ctx->device.get(), &allocInfo, nullptr, &vertexBufferMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }
    ctx->vertexBufferMemory = UniqueDeviceMemory(vertexBufferMemory, DeviceMemoryDeleter{ctx->device.get()});

    // Bind buffer to memory
    result = vkBindBufferMemory(ctx->device.get(), ctx->vertexBuffer.get(), ctx->vertexBufferMemory.get(), 0);
    if (result != VK_SUCCESS) {
        LOGE("Failed to bind vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Map memory and copy vertex data
    void* data;
    result = vkMapMemory(ctx->device.get(), ctx->vertexBufferMemory.get(), 0, bufferSize, 0, &data);
    if (result != VK_SUCCESS) {
        LOGE("Failed to map vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }
    memcpy(data, triangleVertices, bufferSize);
    vkUnmapMemory(ctx->device.get(), ctx->vertexBufferMemory.get());

    LOGI("Vertex buffer created (%zu bytes)", (size_t)bufferSize);
    return true;
}

// Clean up swapchain-related resources (for resize)
// With RAII, we simply clear the vectors and reset the unique_ptrs
static void cleanupSwapchain(VulkanContext* ctx) {
    ctx->framebuffers.clear();
    ctx->swapchainImageViews.clear();
    ctx->swapchain.reset();
}

// Use math::rotateZ from math_utils.h instead of local implementation

// Recreate swapchain (for resize)
static bool recreateSwapchain(VulkanContext* ctx) {
    LOGI("Recreating swapchain...");

    vkDeviceWaitIdle(ctx->device.get());

    cleanupSwapchain(ctx);

    if (!createSwapchain(ctx)) return false;
    if (!createImageViews(ctx)) return false;
    if (!createFramebuffers(ctx)) return false;

    LOGI("Swapchain recreated successfully");
    return true;
}

// Record command buffer for a frame with rotation angle
static bool recordCommandBuffer(VulkanContext* ctx, VkCommandBuffer commandBuffer, uint32_t imageIndex, float angle) {
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;

    VkResult result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
    if (result != VK_SUCCESS) {
        LOGE("Failed to begin command buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = ctx->renderPass.get();
    renderPassInfo.framebuffer = ctx->framebuffers[imageIndex].get();
    renderPassInfo.renderArea.offset = {0, 0};
    renderPassInfo.renderArea.extent = ctx->swapchainExtent;

    // Clear to a nice dark blue color
    VkClearValue clearColor = {{{0.0f, 0.0f, 0.2f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearColor;

    vkCmdBeginRenderPass(commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    // Bind graphics pipeline
    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->trianglePipeline.get());

    // Bind descriptor set (uniform buffer with view/projection matrices)
    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->pipelineLayout.get(),
                           0, 1, &ctx->descriptorSet, 0, nullptr);

    // Set dynamic viewport
    VkViewport viewport{};
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = static_cast<float>(ctx->swapchainExtent.width);
    viewport.height = static_cast<float>(ctx->swapchainExtent.height);
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    vkCmdSetViewport(commandBuffer, 0, 1, &viewport);

    // Set dynamic scissor
    VkRect2D scissor{};
    scissor.offset = {0, 0};
    scissor.extent = ctx->swapchainExtent;
    vkCmdSetScissor(commandBuffer, 0, 1, &scissor);

    // Bind vertex buffer
    VkBuffer vertexBuffers[] = {ctx->vertexBuffer.get()};
    VkDeviceSize offsets[] = {0};
    vkCmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);

    // Build rotation matrix from angle and push to shader
    float transform[16];
    math::rotateZ(angle, transform);
    vkCmdPushConstants(commandBuffer, ctx->pipelineLayout.get(), VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(transform), transform);

    // Draw triangle (3 vertices, 1 instance)
    vkCmdDraw(commandBuffer, 3, 1, 0, 0);

    vkCmdEndRenderPass(commandBuffer);

    result = vkEndCommandBuffer(commandBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to end command buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }
    return true;
}

// Cleanup is now handled automatically by RAII - unique_ptr members are destroyed
// in reverse declaration order when VulkanContext is deleted.

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeInit(
    JNIEnv* env, jobject obj, jobject surface) {

    LOGI("Initializing Vulkan...");

    // Use unique_ptr for RAII - if any step fails, ctx is automatically cleaned up
    auto ctx = std::make_unique<VulkanContext>();

    // Get native window from Android Surface
    ctx->nativeWindow = UniqueNativeWindow(ANativeWindow_fromSurface(env, surface));
    if (!ctx->nativeWindow) {
        LOGE("Failed to get native window from surface");
        return 0;  // ctx automatically cleaned up
    }

    ctx->width = ANativeWindow_getWidth(ctx->nativeWindow.get());
    ctx->height = ANativeWindow_getHeight(ctx->nativeWindow.get());
    LOGI("Surface size: %dx%d", ctx->width, ctx->height);

    // Create Vulkan instance
    if (!createInstance(ctx.get())) return 0;

    // Create Android surface
    if (!createSurface(ctx.get())) return 0;

    // Pick physical device
    if (!pickPhysicalDevice(ctx.get())) return 0;

    // Create logical device
    if (!createLogicalDevice(ctx.get())) return 0;

    // Create swapchain
    if (!createSwapchain(ctx.get())) return 0;

    // Create image views
    if (!createImageViews(ctx.get())) return 0;

    // Create render pass
    if (!createRenderPass(ctx.get())) return 0;

    // Create descriptor set layout (before pipeline)
    if (!createDescriptorSetLayout(ctx.get())) return 0;

    // Create uniform buffer
    if (!createUniformBuffer(ctx.get())) return 0;

    // Create descriptor pool and set
    if (!createDescriptorPool(ctx.get())) return 0;

    // Create graphics pipeline
    if (!createGraphicsPipelines(ctx.get())) return 0;

    // Create vertex buffer (legacy demo)
    if (!createVertexBuffer(ctx.get())) return 0;

    // Create dynamic vertex buffer
    if (!createDynamicVertexBuffer(ctx.get())) return 0;

    // Create framebuffers
    if (!createFramebuffers(ctx.get())) return 0;

    // Create command pool
    if (!createCommandPool(ctx.get())) return 0;

    // Create command buffers
    if (!createCommandBuffers(ctx.get())) return 0;

    // Create sync objects
    if (!createSyncObjects(ctx.get())) return 0;

    ctx->initialized = true;
    LOGI("Vulkan initialization complete!");

    // Transfer ownership to JNI - caller is responsible for calling nativeDestroy
    return reinterpret_cast<jlong>(ctx.release());
}

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeRender(
    JNIEnv* env, jobject obj, jlong contextHandle, jfloat angle) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized) {
        return;
    }

    // Wait for previous frame
    VkFence inFlightFence = ctx->inFlightFences[ctx->currentFrame].get();
    vkWaitForFences(ctx->device.get(), 1, &inFlightFence, VK_TRUE, UINT64_MAX);

    // Acquire next swapchain image
    uint32_t imageIndex;
    VkResult result = vkAcquireNextImageKHR(ctx->device.get(), ctx->swapchain.get(), UINT64_MAX,
                                            ctx->imageAvailableSemaphores[ctx->currentFrame].get(),
                                            VK_NULL_HANDLE, &imageIndex);

    if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapchain(ctx);
        return;
    } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        LOGE("Failed to acquire swapchain image: %s (%d)", vkResultToString(result), result);
        return;
    }

    vkResetFences(ctx->device.get(), 1, &inFlightFence);

    // Reset and record command buffer with rotation angle
    vkResetCommandBuffer(ctx->commandBuffers[ctx->currentFrame], 0);
    if (!recordCommandBuffer(ctx, ctx->commandBuffers[ctx->currentFrame], imageIndex, angle)) {
        return;
    }

    // Submit command buffer
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

    VkSemaphore waitSemaphores[] = {ctx->imageAvailableSemaphores[ctx->currentFrame].get()};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &ctx->commandBuffers[ctx->currentFrame];

    VkSemaphore signalSemaphores[] = {ctx->renderFinishedSemaphores[ctx->currentFrame].get()};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    result = vkQueueSubmit(ctx->graphicsQueue, 1, &submitInfo, ctx->inFlightFences[ctx->currentFrame].get());
    if (result != VK_SUCCESS) {
        LOGE("Failed to submit draw command buffer: %s (%d)", vkResultToString(result), result);
        return;
    }

    // Present
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;

    VkSwapchainKHR swapchains[] = {ctx->swapchain.get()};
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapchains;
    presentInfo.pImageIndices = &imageIndex;

    result = vkQueuePresentKHR(ctx->presentQueue, &presentInfo);

    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
        recreateSwapchain(ctx);
    } else if (result != VK_SUCCESS) {
        LOGE("Failed to present swapchain image: %s (%d)", vkResultToString(result), result);
    }

    ctx->currentFrame = (ctx->currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;

    // Log occasionally to show we're alive
    if (++ctx->frameCount % LOG_FRAME_INTERVAL == 0) {
        LOGI("Rendered %d frames, angle=%.1f", ctx->frameCount, angle);
    }
}

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeResize(
    JNIEnv* env, jobject obj, jlong contextHandle, jint width, jint height) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized) {
        return;
    }

    // Skip resize if dimensions are zero (minimized window)
    if (width <= 0 || height <= 0) {
        LOGW("Ignoring resize to zero dimensions: %dx%d", width, height);
        return;
    }

    if (ctx->width != width || ctx->height != height) {
        LOGI("Surface resized: %dx%d -> %dx%d", ctx->width, ctx->height, width, height);
        ctx->width = width;
        ctx->height = height;
        recreateSwapchain(ctx);
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
    // RAII handles all cleanup - just delete the context
    // The DeviceDeleter calls vkDeviceWaitIdle before destroying
    delete ctx;
    LOGI("Vulkan context destroyed");
}

// New Phase 2 API: Set view matrix
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeSetViewMatrix(
    JNIEnv* env, jobject obj, jlong contextHandle, jfloatArray matrixArray) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized || ctx->uniformBufferMapped == nullptr) {
        return;
    }

    jfloat* matrix = env->GetFloatArrayElements(matrixArray, nullptr);
    if (matrix == nullptr) {
        return;
    }

    memcpy(ctx->viewMatrix, matrix, sizeof(float) * 16);
    memcpy(ctx->uniformBufferMapped, ctx->viewMatrix, sizeof(float) * 16);

    env->ReleaseFloatArrayElements(matrixArray, matrix, JNI_ABORT);
}

// New Phase 2 API: Set projection matrix
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeSetProjectionMatrix(
    JNIEnv* env, jobject obj, jlong contextHandle, jfloatArray matrixArray) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized || ctx->uniformBufferMapped == nullptr) {
        return;
    }

    jfloat* matrix = env->GetFloatArrayElements(matrixArray, nullptr);
    if (matrix == nullptr) {
        return;
    }

    memcpy(ctx->projectionMatrix, matrix, sizeof(float) * 16);
    memcpy(static_cast<char*>(ctx->uniformBufferMapped) + sizeof(float) * 16,
           ctx->projectionMatrix, sizeof(float) * 16);

    env->ReleaseFloatArrayElements(matrixArray, matrix, JNI_ABORT);
}

// New Phase 2 API: Begin frame
JNIEXPORT jboolean JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeBeginFrame(
    JNIEnv* env, jobject obj, jlong contextHandle) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized) {
        return JNI_FALSE;
    }

    // Wait for previous frame
    VkFence inFlightFence = ctx->inFlightFences[ctx->currentFrame].get();
    vkWaitForFences(ctx->device.get(), 1, &inFlightFence, VK_TRUE, UINT64_MAX);

    // Acquire next swapchain image
    VkResult result = vkAcquireNextImageKHR(ctx->device.get(), ctx->swapchain.get(), UINT64_MAX,
                                            ctx->imageAvailableSemaphores[ctx->currentFrame].get(),
                                            VK_NULL_HANDLE, &ctx->currentImageIndex);

    if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapchain(ctx);
        return JNI_FALSE;
    } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        LOGE("Failed to acquire swapchain image: %s (%d)", vkResultToString(result), result);
        return JNI_FALSE;
    }

    vkResetFences(ctx->device.get(), 1, &inFlightFence);

    // Reset command buffer
    vkResetCommandBuffer(ctx->commandBuffers[ctx->currentFrame], 0);

    // Begin command buffer
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;

    result = vkBeginCommandBuffer(ctx->commandBuffers[ctx->currentFrame], &beginInfo);
    if (result != VK_SUCCESS) {
        LOGE("Failed to begin command buffer: %s (%d)", vkResultToString(result), result);
        return JNI_FALSE;
    }

    // Begin render pass
    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = ctx->renderPass.get();
    renderPassInfo.framebuffer = ctx->framebuffers[ctx->currentImageIndex].get();
    renderPassInfo.renderArea.offset = {0, 0};
    renderPassInfo.renderArea.extent = ctx->swapchainExtent;

    VkClearValue clearColor = {{{0.0f, 0.0f, 0.2f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearColor;

    vkCmdBeginRenderPass(ctx->commandBuffers[ctx->currentFrame], &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    // Note: Pipeline is bound per-draw in nativeDraw() to support different primitive types

    // Bind descriptor set (uniform buffer)
    vkCmdBindDescriptorSets(ctx->commandBuffers[ctx->currentFrame], VK_PIPELINE_BIND_POINT_GRAPHICS,
                           ctx->pipelineLayout.get(), 0, 1, &ctx->descriptorSet, 0, nullptr);

    // Set viewport and scissor
    VkViewport viewport{};
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = static_cast<float>(ctx->swapchainExtent.width);
    viewport.height = static_cast<float>(ctx->swapchainExtent.height);
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    vkCmdSetViewport(ctx->commandBuffers[ctx->currentFrame], 0, 1, &viewport);

    VkRect2D scissor{};
    scissor.offset = {0, 0};
    scissor.extent = ctx->swapchainExtent;
    vkCmdSetScissor(ctx->commandBuffers[ctx->currentFrame], 0, 1, &scissor);

    // Reset dynamic vertex buffer offset for new frame
    ctx->dynamicVertexBufferOffset = 0;
    ctx->inFrame = true;

    return JNI_TRUE;
}

// New Phase 2 API: Draw batch
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeDraw(
    JNIEnv* env, jobject obj, jlong contextHandle,
    jint primitiveType, jfloatArray verticesArray, jint vertexCount, jfloatArray transformArray) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized || !ctx->inFrame) {
        return;
    }

    // Get vertex data
    jfloat* vertices = env->GetFloatArrayElements(verticesArray, nullptr);
    if (vertices == nullptr) {
        return;
    }

    // Calculate size needed (7 floats per vertex)
    size_t vertexDataSize = vertexCount * 7 * sizeof(float);

    // Check if we have room in the dynamic buffer
    if (ctx->dynamicVertexBufferOffset + vertexDataSize > ctx->dynamicVertexBufferSize) {
        LOGE("Dynamic vertex buffer overflow! Need %zu bytes, have %zu",
             ctx->dynamicVertexBufferOffset + vertexDataSize, ctx->dynamicVertexBufferSize);
        env->ReleaseFloatArrayElements(verticesArray, vertices, JNI_ABORT);
        return;
    }

    // Copy vertex data to dynamic buffer
    memcpy(static_cast<char*>(ctx->dynamicVertexBufferMapped) + ctx->dynamicVertexBufferOffset,
           vertices, vertexDataSize);

    env->ReleaseFloatArrayElements(verticesArray, vertices, JNI_ABORT);

    // Get transform matrix (or use identity)
    float transform[16];
    if (transformArray != nullptr) {
        jfloat* transformData = env->GetFloatArrayElements(transformArray, nullptr);
        if (transformData != nullptr) {
            memcpy(transform, transformData, sizeof(float) * 16);
            env->ReleaseFloatArrayElements(transformArray, transformData, JNI_ABORT);
        } else {
            math::identity(transform);
        }
    } else {
        math::identity(transform);
    }

    // Select pipeline based on primitive type
    // PrimitiveType enum: POINTS=0, LINES=1, TRIANGLES=2
    VkPipeline pipeline;
    switch (primitiveType) {
        case 0:  // POINTS
            pipeline = ctx->pointPipeline.get();
            break;
        case 1:  // LINES
            pipeline = ctx->linePipeline.get();
            break;
        case 2:  // TRIANGLES
        default:
            pipeline = ctx->trianglePipeline.get();
            break;
    }
    vkCmdBindPipeline(ctx->commandBuffers[ctx->currentFrame], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

    // Push model matrix
    vkCmdPushConstants(ctx->commandBuffers[ctx->currentFrame], ctx->pipelineLayout.get(),
                      VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(transform), transform);

    // Bind vertex buffer at current offset
    VkBuffer buffers[] = {ctx->dynamicVertexBuffer.get()};
    VkDeviceSize offsets[] = {ctx->dynamicVertexBufferOffset};
    vkCmdBindVertexBuffers(ctx->commandBuffers[ctx->currentFrame], 0, 1, buffers, offsets);

    // Draw
    vkCmdDraw(ctx->commandBuffers[ctx->currentFrame], vertexCount, 1, 0, 0);

    // Advance offset for next draw call
    ctx->dynamicVertexBufferOffset += vertexDataSize;
}

// New Phase 2 API: End frame
JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeEndFrame(
    JNIEnv* env, jobject obj, jlong contextHandle) {

    auto* ctx = reinterpret_cast<VulkanContext*>(contextHandle);
    if (ctx == nullptr || !ctx->initialized || !ctx->inFrame) {
        return;
    }

    ctx->inFrame = false;

    // End render pass
    vkCmdEndRenderPass(ctx->commandBuffers[ctx->currentFrame]);

    // End command buffer
    VkResult result = vkEndCommandBuffer(ctx->commandBuffers[ctx->currentFrame]);
    if (result != VK_SUCCESS) {
        LOGE("Failed to end command buffer: %s (%d)", vkResultToString(result), result);
        return;
    }

    // Submit command buffer
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

    VkSemaphore waitSemaphores[] = {ctx->imageAvailableSemaphores[ctx->currentFrame].get()};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &ctx->commandBuffers[ctx->currentFrame];

    VkSemaphore signalSemaphores[] = {ctx->renderFinishedSemaphores[ctx->currentFrame].get()};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    result = vkQueueSubmit(ctx->graphicsQueue, 1, &submitInfo, ctx->inFlightFences[ctx->currentFrame].get());
    if (result != VK_SUCCESS) {
        LOGE("Failed to submit draw command buffer: %s (%d)", vkResultToString(result), result);
        return;
    }

    // Present
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;

    VkSwapchainKHR swapchains[] = {ctx->swapchain.get()};
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapchains;
    presentInfo.pImageIndices = &ctx->currentImageIndex;

    result = vkQueuePresentKHR(ctx->presentQueue, &presentInfo);

    if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
        recreateSwapchain(ctx);
    } else if (result != VK_SUCCESS) {
        LOGE("Failed to present swapchain image: %s (%d)", vkResultToString(result), result);
    }

    ctx->currentFrame = (ctx->currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;

    // Log occasionally
    if (++ctx->frameCount % LOG_FRAME_INTERVAL == 0) {
        LOGI("Rendered %d frames (new API)", ctx->frameCount);
    }
}

} // extern "C"
