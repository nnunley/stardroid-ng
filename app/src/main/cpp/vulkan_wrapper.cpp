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

#include "shaders.h"

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

    // Swapchain
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat swapchainFormat = VK_FORMAT_UNDEFINED;
    VkExtent2D swapchainExtent = {0, 0};
    std::vector<VkImage> swapchainImages;
    std::vector<VkImageView> swapchainImageViews;

    // Render pass and framebuffers
    VkRenderPass renderPass = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers;

    // Command pool and buffers
    VkCommandPool commandPool = VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> commandBuffers;

    // Synchronization
    std::vector<VkSemaphore> imageAvailableSemaphores;
    std::vector<VkSemaphore> renderFinishedSemaphores;
    std::vector<VkFence> inFlightFences;
    uint32_t currentFrame = 0;

    // Graphics pipeline
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline graphicsPipeline = VK_NULL_HANDLE;

    // Vertex buffer
    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexBufferMemory = VK_NULL_HANDLE;

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
    SwapchainSupportDetails support = querySwapchainSupport(ctx->physicalDevice, ctx->surface);

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
    createInfo.surface = ctx->surface;
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

    VkResult result = vkCreateSwapchainKHR(ctx->device, &createInfo, nullptr, &ctx->swapchain);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create swapchain: %s (%d)", vkResultToString(result), result);
        return false;
    }

    ctx->swapchainFormat = surfaceFormat.format;
    ctx->swapchainExtent = extent;

    // Get swapchain images
    vkGetSwapchainImagesKHR(ctx->device, ctx->swapchain, &imageCount, nullptr);
    ctx->swapchainImages.resize(imageCount);
    vkGetSwapchainImagesKHR(ctx->device, ctx->swapchain, &imageCount, ctx->swapchainImages.data());

    LOGI("Swapchain created: %dx%d, %d images, format=%d",
         extent.width, extent.height, imageCount, surfaceFormat.format);

    return true;
}

// Create image views for swapchain images
static bool createImageViews(VulkanContext* ctx) {
    ctx->swapchainImageViews.resize(ctx->swapchainImages.size());

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

        VkResult result = vkCreateImageView(ctx->device, &createInfo, nullptr, &ctx->swapchainImageViews[i]);
        if (result != VK_SUCCESS) {
            LOGE("Failed to create image view %zu: %s (%d)", i, vkResultToString(result), result);
            return false;
        }
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

    VkResult result = vkCreateRenderPass(ctx->device, &createInfo, nullptr, &ctx->renderPass);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create render pass: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Render pass created");
    return true;
}

// Create framebuffers
static bool createFramebuffers(VulkanContext* ctx) {
    ctx->framebuffers.resize(ctx->swapchainImageViews.size());

    for (size_t i = 0; i < ctx->swapchainImageViews.size(); i++) {
        VkImageView attachments[] = {ctx->swapchainImageViews[i]};

        VkFramebufferCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        createInfo.renderPass = ctx->renderPass;
        createInfo.attachmentCount = 1;
        createInfo.pAttachments = attachments;
        createInfo.width = ctx->swapchainExtent.width;
        createInfo.height = ctx->swapchainExtent.height;
        createInfo.layers = 1;

        VkResult result = vkCreateFramebuffer(ctx->device, &createInfo, nullptr, &ctx->framebuffers[i]);
        if (result != VK_SUCCESS) {
            LOGE("Failed to create framebuffer %zu: %s (%d)", i, vkResultToString(result), result);
            return false;
        }
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

    VkResult result = vkCreateCommandPool(ctx->device, &createInfo, nullptr, &ctx->commandPool);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create command pool: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Command pool created");
    return true;
}

// Create command buffers
static bool createCommandBuffers(VulkanContext* ctx) {
    ctx->commandBuffers.resize(MAX_FRAMES_IN_FLIGHT);

    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = ctx->commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = static_cast<uint32_t>(ctx->commandBuffers.size());

    VkResult result = vkAllocateCommandBuffers(ctx->device, &allocInfo, ctx->commandBuffers.data());
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate command buffers: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Allocated %zu command buffers", ctx->commandBuffers.size());
    return true;
}

// Create synchronization objects
static bool createSyncObjects(VulkanContext* ctx) {
    ctx->imageAvailableSemaphores.resize(MAX_FRAMES_IN_FLIGHT);
    ctx->renderFinishedSemaphores.resize(MAX_FRAMES_IN_FLIGHT);
    ctx->inFlightFences.resize(MAX_FRAMES_IN_FLIGHT);

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    for (size_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        if (vkCreateSemaphore(ctx->device, &semaphoreInfo, nullptr, &ctx->imageAvailableSemaphores[i]) != VK_SUCCESS ||
            vkCreateSemaphore(ctx->device, &semaphoreInfo, nullptr, &ctx->renderFinishedSemaphores[i]) != VK_SUCCESS ||
            vkCreateFence(ctx->device, &fenceInfo, nullptr, &ctx->inFlightFences[i]) != VK_SUCCESS) {
            LOGE("Failed to create synchronization objects for frame %zu", i);
            return false;
        }
    }

    LOGI("Created synchronization objects");
    return true;
}

// Create shader module from SPIR-V bytecode
static VkShaderModule createShaderModule(VulkanContext* ctx, const unsigned char* code, unsigned int codeSize) {
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = codeSize;
    createInfo.pCode = reinterpret_cast<const uint32_t*>(code);

    VkShaderModule shaderModule;
    VkResult result = vkCreateShaderModule(ctx->device, &createInfo, nullptr, &shaderModule);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create shader module: %s (%d)", vkResultToString(result), result);
        return VK_NULL_HANDLE;
    }

    return shaderModule;
}

// Create graphics pipeline
static bool createGraphicsPipeline(VulkanContext* ctx) {
    // Create shader modules
    VkShaderModule vertShaderModule = createShaderModule(ctx, triangle_vert_spv, triangle_vert_spv_len);
    VkShaderModule fragShaderModule = createShaderModule(ctx, triangle_frag_spv, triangle_frag_spv_len);

    if (vertShaderModule == VK_NULL_HANDLE || fragShaderModule == VK_NULL_HANDLE) {
        LOGE("Failed to create shader modules");
        if (vertShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(ctx->device, vertShaderModule, nullptr);
        }
        if (fragShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(ctx->device, fragShaderModule, nullptr);
        }
        return false;
    }

    LOGI("Shader modules created");

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

    // Vertex input: position (vec2) and color (vec3)
    VkVertexInputBindingDescription bindingDescription{};
    bindingDescription.binding = 0;
    bindingDescription.stride = sizeof(float) * 5; // vec2 position + vec3 color
    bindingDescription.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription attributeDescriptions[2] = {};
    // Position
    attributeDescriptions[0].binding = 0;
    attributeDescriptions[0].location = 0;
    attributeDescriptions[0].format = VK_FORMAT_R32G32_SFLOAT;
    attributeDescriptions[0].offset = 0;
    // Color
    attributeDescriptions[1].binding = 0;
    attributeDescriptions[1].location = 1;
    attributeDescriptions[1].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributeDescriptions[1].offset = sizeof(float) * 2;

    VkPipelineVertexInputStateCreateInfo vertexInputInfo{};
    vertexInputInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInputInfo.vertexBindingDescriptionCount = 1;
    vertexInputInfo.pVertexBindingDescriptions = &bindingDescription;
    vertexInputInfo.vertexAttributeDescriptionCount = 2;
    vertexInputInfo.pVertexAttributeDescriptions = attributeDescriptions;

    // Input assembly
    VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
    inputAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
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

    // Rasterizer
    VkPipelineRasterizationStateCreateInfo rasterizer{};
    rasterizer.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterizer.depthClampEnable = VK_FALSE;
    rasterizer.rasterizerDiscardEnable = VK_FALSE;
    rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
    rasterizer.lineWidth = 1.0f;
    rasterizer.cullMode = VK_CULL_MODE_BACK_BIT;
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

    // Push constants for transform matrix (mat4 = 64 bytes)
    VkPushConstantRange pushConstantRange{};
    pushConstantRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    pushConstantRange.offset = 0;
    pushConstantRange.size = sizeof(float) * 16; // mat4

    // Pipeline layout
    VkPipelineLayoutCreateInfo pipelineLayoutInfo{};
    pipelineLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pipelineLayoutInfo.pushConstantRangeCount = 1;
    pipelineLayoutInfo.pPushConstantRanges = &pushConstantRange;

    VkResult result = vkCreatePipelineLayout(ctx->device, &pipelineLayoutInfo, nullptr, &ctx->pipelineLayout);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create pipeline layout: %s (%d)", vkResultToString(result), result);
        vkDestroyShaderModule(ctx->device, vertShaderModule, nullptr);
        vkDestroyShaderModule(ctx->device, fragShaderModule, nullptr);
        return false;
    }

    LOGI("Pipeline layout created");

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
    pipelineInfo.layout = ctx->pipelineLayout;
    pipelineInfo.renderPass = ctx->renderPass;
    pipelineInfo.subpass = 0;

    result = vkCreateGraphicsPipelines(ctx->device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &ctx->graphicsPipeline);

    // Clean up shader modules (no longer needed after pipeline creation)
    vkDestroyShaderModule(ctx->device, vertShaderModule, nullptr);
    vkDestroyShaderModule(ctx->device, fragShaderModule, nullptr);

    if (result != VK_SUCCESS) {
        LOGE("Failed to create graphics pipeline: %s (%d)", vkResultToString(result), result);
        return false;
    }

    LOGI("Graphics pipeline created");
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

// Triangle vertex data: position (vec2) + color (vec3) = 5 floats per vertex
static const float triangleVertices[] = {
    // Position (x, y)    Color (r, g, b)
     0.0f, -0.5f,         1.0f, 0.0f, 0.0f,  // Top vertex - red
    -0.5f,  0.5f,         0.0f, 1.0f, 0.0f,  // Bottom left - green
     0.5f,  0.5f,         0.0f, 0.0f, 1.0f,  // Bottom right - blue
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

    VkResult result = vkCreateBuffer(ctx->device, &bufferInfo, nullptr, &ctx->vertexBuffer);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create vertex buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Get memory requirements
    VkMemoryRequirements memRequirements;
    vkGetBufferMemoryRequirements(ctx->device, ctx->vertexBuffer, &memRequirements);

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

    result = vkAllocateMemory(ctx->device, &allocInfo, nullptr, &ctx->vertexBufferMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Bind buffer to memory
    result = vkBindBufferMemory(ctx->device, ctx->vertexBuffer, ctx->vertexBufferMemory, 0);
    if (result != VK_SUCCESS) {
        LOGE("Failed to bind vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }

    // Map memory and copy vertex data
    void* data;
    result = vkMapMemory(ctx->device, ctx->vertexBufferMemory, 0, bufferSize, 0, &data);
    if (result != VK_SUCCESS) {
        LOGE("Failed to map vertex buffer memory: %s (%d)", vkResultToString(result), result);
        return false;
    }
    memcpy(data, triangleVertices, bufferSize);
    vkUnmapMemory(ctx->device, ctx->vertexBufferMemory);

    LOGI("Vertex buffer created (%zu bytes)", (size_t)bufferSize);
    return true;
}

// Clean up swapchain-related resources (for resize)
static void cleanupSwapchain(VulkanContext* ctx) {
    for (auto framebuffer : ctx->framebuffers) {
        if (framebuffer != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(ctx->device, framebuffer, nullptr);
        }
    }
    ctx->framebuffers.clear();

    for (auto imageView : ctx->swapchainImageViews) {
        if (imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(ctx->device, imageView, nullptr);
        }
    }
    ctx->swapchainImageViews.clear();

    if (ctx->swapchain != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(ctx->device, ctx->swapchain, nullptr);
        ctx->swapchain = VK_NULL_HANDLE;
    }
}

// Recreate swapchain (for resize)
static bool recreateSwapchain(VulkanContext* ctx) {
    LOGI("Recreating swapchain...");

    vkDeviceWaitIdle(ctx->device);

    cleanupSwapchain(ctx);

    if (!createSwapchain(ctx)) return false;
    if (!createImageViews(ctx)) return false;
    if (!createFramebuffers(ctx)) return false;

    LOGI("Swapchain recreated successfully");
    return true;
}

// Record command buffer for a frame
static bool recordCommandBuffer(VulkanContext* ctx, VkCommandBuffer commandBuffer, uint32_t imageIndex) {
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;

    VkResult result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
    if (result != VK_SUCCESS) {
        LOGE("Failed to begin command buffer: %s (%d)", vkResultToString(result), result);
        return false;
    }

    VkRenderPassBeginInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    renderPassInfo.renderPass = ctx->renderPass;
    renderPassInfo.framebuffer = ctx->framebuffers[imageIndex];
    renderPassInfo.renderArea.offset = {0, 0};
    renderPassInfo.renderArea.extent = ctx->swapchainExtent;

    // Clear to a nice dark blue color
    VkClearValue clearColor = {{{0.0f, 0.0f, 0.2f, 1.0f}}};
    renderPassInfo.clearValueCount = 1;
    renderPassInfo.pClearValues = &clearColor;

    vkCmdBeginRenderPass(commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

    // Bind graphics pipeline
    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, ctx->graphicsPipeline);

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
    VkBuffer vertexBuffers[] = {ctx->vertexBuffer};
    VkDeviceSize offsets[] = {0};
    vkCmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);

    // Push identity transform matrix (rotation in Phase 8)
    float identity[16] = {
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f
    };
    vkCmdPushConstants(commandBuffer, ctx->pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(identity), identity);

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

// Cleanup Vulkan resources
static void cleanup(VulkanContext* ctx) {
    if (ctx->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(ctx->device);

        // Destroy sync objects
        for (size_t i = 0; i < ctx->imageAvailableSemaphores.size(); i++) {
            if (ctx->imageAvailableSemaphores[i] != VK_NULL_HANDLE) {
                vkDestroySemaphore(ctx->device, ctx->imageAvailableSemaphores[i], nullptr);
            }
            if (ctx->renderFinishedSemaphores[i] != VK_NULL_HANDLE) {
                vkDestroySemaphore(ctx->device, ctx->renderFinishedSemaphores[i], nullptr);
            }
            if (ctx->inFlightFences[i] != VK_NULL_HANDLE) {
                vkDestroyFence(ctx->device, ctx->inFlightFences[i], nullptr);
            }
        }
        ctx->imageAvailableSemaphores.clear();
        ctx->renderFinishedSemaphores.clear();
        ctx->inFlightFences.clear();

        // Destroy command pool (frees command buffers automatically)
        if (ctx->commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(ctx->device, ctx->commandPool, nullptr);
            ctx->commandPool = VK_NULL_HANDLE;
        }
        ctx->commandBuffers.clear();

        // Destroy graphics pipeline
        if (ctx->graphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(ctx->device, ctx->graphicsPipeline, nullptr);
            ctx->graphicsPipeline = VK_NULL_HANDLE;
        }

        // Destroy pipeline layout
        if (ctx->pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(ctx->device, ctx->pipelineLayout, nullptr);
            ctx->pipelineLayout = VK_NULL_HANDLE;
        }

        // Destroy vertex buffer
        if (ctx->vertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(ctx->device, ctx->vertexBuffer, nullptr);
            ctx->vertexBuffer = VK_NULL_HANDLE;
        }
        if (ctx->vertexBufferMemory != VK_NULL_HANDLE) {
            vkFreeMemory(ctx->device, ctx->vertexBufferMemory, nullptr);
            ctx->vertexBufferMemory = VK_NULL_HANDLE;
        }

        // Destroy framebuffers
        for (auto framebuffer : ctx->framebuffers) {
            if (framebuffer != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(ctx->device, framebuffer, nullptr);
            }
        }
        ctx->framebuffers.clear();

        // Destroy render pass
        if (ctx->renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(ctx->device, ctx->renderPass, nullptr);
            ctx->renderPass = VK_NULL_HANDLE;
        }

        // Destroy image views
        for (auto imageView : ctx->swapchainImageViews) {
            if (imageView != VK_NULL_HANDLE) {
                vkDestroyImageView(ctx->device, imageView, nullptr);
            }
        }
        ctx->swapchainImageViews.clear();

        // Destroy swapchain
        if (ctx->swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(ctx->device, ctx->swapchain, nullptr);
            ctx->swapchain = VK_NULL_HANDLE;
        }

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

    // Create swapchain
    if (!createSwapchain(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create image views
    if (!createImageViews(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create render pass
    if (!createRenderPass(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create graphics pipeline
    if (!createGraphicsPipeline(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create vertex buffer
    if (!createVertexBuffer(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create framebuffers
    if (!createFramebuffers(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create command pool
    if (!createCommandPool(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create command buffers
    if (!createCommandBuffers(ctx)) {
        cleanup(ctx);
        delete ctx;
        return 0;
    }

    // Create sync objects
    if (!createSyncObjects(ctx)) {
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

    // Wait for previous frame
    vkWaitForFences(ctx->device, 1, &ctx->inFlightFences[ctx->currentFrame], VK_TRUE, UINT64_MAX);

    // Acquire next swapchain image
    uint32_t imageIndex;
    VkResult result = vkAcquireNextImageKHR(ctx->device, ctx->swapchain, UINT64_MAX,
                                            ctx->imageAvailableSemaphores[ctx->currentFrame],
                                            VK_NULL_HANDLE, &imageIndex);

    if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapchain(ctx);
        return;
    } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        LOGE("Failed to acquire swapchain image: %s (%d)", vkResultToString(result), result);
        return;
    }

    vkResetFences(ctx->device, 1, &ctx->inFlightFences[ctx->currentFrame]);

    // Reset and record command buffer
    vkResetCommandBuffer(ctx->commandBuffers[ctx->currentFrame], 0);
    if (!recordCommandBuffer(ctx, ctx->commandBuffers[ctx->currentFrame], imageIndex)) {
        return;
    }

    // Submit command buffer
    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

    VkSemaphore waitSemaphores[] = {ctx->imageAvailableSemaphores[ctx->currentFrame]};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &ctx->commandBuffers[ctx->currentFrame];

    VkSemaphore signalSemaphores[] = {ctx->renderFinishedSemaphores[ctx->currentFrame]};
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    result = vkQueueSubmit(ctx->graphicsQueue, 1, &submitInfo, ctx->inFlightFences[ctx->currentFrame]);
    if (result != VK_SUCCESS) {
        LOGE("Failed to submit draw command buffer: %s (%d)", vkResultToString(result), result);
        return;
    }

    // Present
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;

    VkSwapchainKHR swapchains[] = {ctx->swapchain};
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
    cleanup(ctx);
    delete ctx;
    LOGI("Vulkan context destroyed");
}

} // extern "C"
