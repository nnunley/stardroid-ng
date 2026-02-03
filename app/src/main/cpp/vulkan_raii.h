#pragma once

#include <memory>
#include <utility>
#include <vulkan/vulkan.h>
#include <android/native_window.h>

// Forward declaration for debug messenger destroy function
#ifndef NDEBUG
void destroyDebugUtilsMessengerEXT(VkInstance instance, VkDebugUtilsMessengerEXT messenger);
#endif

// =============================================================================
// VulkanHandle: A custom RAII wrapper for Vulkan handles
// =============================================================================
// Unlike std::unique_ptr, this works with non-pointer dispatchable handles
// (which are uint64_t on 32-bit platforms, not actual pointers).

template<typename HandleT, typename Deleter>
class VulkanHandle {
public:
    VulkanHandle() noexcept : handle_{}, deleter_{} {}

    explicit VulkanHandle(HandleT handle) noexcept : handle_(handle), deleter_{} {}

    VulkanHandle(HandleT handle, Deleter deleter) noexcept
        : handle_(handle), deleter_(std::move(deleter)) {}

    ~VulkanHandle() {
        if (handle_) {
            deleter_(handle_);
        }
    }

    // Move-only semantics
    VulkanHandle(const VulkanHandle&) = delete;
    VulkanHandle& operator=(const VulkanHandle&) = delete;

    VulkanHandle(VulkanHandle&& other) noexcept
        : handle_(other.handle_), deleter_(std::move(other.deleter_)) {
        other.handle_ = HandleT{};
    }

    VulkanHandle& operator=(VulkanHandle&& other) noexcept {
        if (this != &other) {
            if (handle_) {
                deleter_(handle_);
            }
            handle_ = other.handle_;
            deleter_ = std::move(other.deleter_);
            other.handle_ = HandleT{};
        }
        return *this;
    }

    // Access
    HandleT get() const noexcept { return handle_; }
    HandleT* ptr() noexcept { return &handle_; }

    // Boolean conversion
    explicit operator bool() const noexcept { return handle_ != HandleT{}; }

    // Reset the handle
    void reset(HandleT handle = HandleT{}) noexcept {
        if (handle_) {
            deleter_(handle_);
        }
        handle_ = handle;
    }

    // Release ownership (caller takes responsibility)
    HandleT release() noexcept {
        HandleT tmp = handle_;
        handle_ = HandleT{};
        return tmp;
    }

private:
    HandleT handle_;
    Deleter deleter_;
};

// =============================================================================
// Custom Deleters for Vulkan Handles
// =============================================================================

// ANativeWindow deleter
struct NativeWindowDeleter {
    void operator()(ANativeWindow* window) const noexcept {
        if (window) ANativeWindow_release(window);
    }
};

// VkInstance deleter (no parent context needed)
struct InstanceDeleter {
    void operator()(VkInstance instance) const noexcept {
        if (instance) vkDestroyInstance(instance, nullptr);
    }
};

// VkDevice deleter (no parent context needed)
struct DeviceDeleter {
    void operator()(VkDevice device) const noexcept {
        if (device) {
            vkDeviceWaitIdle(device);
            vkDestroyDevice(device, nullptr);
        }
    }
};

// Deleters that require VkInstance
struct SurfaceDeleter {
    VkInstance instance = VK_NULL_HANDLE;
    void operator()(VkSurfaceKHR surface) const noexcept {
        if (surface && instance) vkDestroySurfaceKHR(instance, surface, nullptr);
    }
};

#ifndef NDEBUG
struct DebugMessengerDeleter {
    VkInstance instance = VK_NULL_HANDLE;
    void operator()(VkDebugUtilsMessengerEXT messenger) const noexcept {
        if (messenger && instance) destroyDebugUtilsMessengerEXT(instance, messenger);
    }
};
#endif

// Deleters that require VkDevice
struct SwapchainDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkSwapchainKHR swapchain) const noexcept {
        if (swapchain && device) vkDestroySwapchainKHR(device, swapchain, nullptr);
    }
};

struct ImageViewDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkImageView imageView) const noexcept {
        if (imageView && device) vkDestroyImageView(device, imageView, nullptr);
    }
};

struct RenderPassDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkRenderPass renderPass) const noexcept {
        if (renderPass && device) vkDestroyRenderPass(device, renderPass, nullptr);
    }
};

struct FramebufferDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkFramebuffer framebuffer) const noexcept {
        if (framebuffer && device) vkDestroyFramebuffer(device, framebuffer, nullptr);
    }
};

struct CommandPoolDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkCommandPool commandPool) const noexcept {
        if (commandPool && device) vkDestroyCommandPool(device, commandPool, nullptr);
    }
};

struct PipelineLayoutDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkPipelineLayout pipelineLayout) const noexcept {
        if (pipelineLayout && device) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
    }
};

struct PipelineDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkPipeline pipeline) const noexcept {
        if (pipeline && device) vkDestroyPipeline(device, pipeline, nullptr);
    }
};

struct BufferDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkBuffer buffer) const noexcept {
        if (buffer && device) vkDestroyBuffer(device, buffer, nullptr);
    }
};

struct DeviceMemoryDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkDeviceMemory memory) const noexcept {
        if (memory && device) vkFreeMemory(device, memory, nullptr);
    }
};

struct DescriptorPoolDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkDescriptorPool pool) const noexcept {
        if (pool && device) vkDestroyDescriptorPool(device, pool, nullptr);
    }
};

struct DescriptorSetLayoutDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkDescriptorSetLayout layout) const noexcept {
        if (layout && device) vkDestroyDescriptorSetLayout(device, layout, nullptr);
    }
};

struct SemaphoreDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkSemaphore semaphore) const noexcept {
        if (semaphore && device) vkDestroySemaphore(device, semaphore, nullptr);
    }
};

struct FenceDeleter {
    VkDevice device = VK_NULL_HANDLE;
    void operator()(VkFence fence) const noexcept {
        if (fence && device) vkDestroyFence(device, fence, nullptr);
    }
};

// =============================================================================
// Type Aliases for RAII Handles
// =============================================================================

// Platform resources (uses actual std::unique_ptr since it's a true pointer)
using UniqueNativeWindow = std::unique_ptr<ANativeWindow, NativeWindowDeleter>;

// Instance-level handles (no device dependency)
using UniqueInstance = VulkanHandle<VkInstance, InstanceDeleter>;
using UniqueSurface = VulkanHandle<VkSurfaceKHR, SurfaceDeleter>;
#ifndef NDEBUG
using UniqueDebugMessenger = VulkanHandle<VkDebugUtilsMessengerEXT, DebugMessengerDeleter>;
#endif

// Device-level handles
using UniqueDevice = VulkanHandle<VkDevice, DeviceDeleter>;
using UniqueSwapchain = VulkanHandle<VkSwapchainKHR, SwapchainDeleter>;
using UniqueImageView = VulkanHandle<VkImageView, ImageViewDeleter>;
using UniqueRenderPass = VulkanHandle<VkRenderPass, RenderPassDeleter>;
using UniqueFramebuffer = VulkanHandle<VkFramebuffer, FramebufferDeleter>;
using UniqueCommandPool = VulkanHandle<VkCommandPool, CommandPoolDeleter>;
using UniquePipelineLayout = VulkanHandle<VkPipelineLayout, PipelineLayoutDeleter>;
using UniquePipeline = VulkanHandle<VkPipeline, PipelineDeleter>;
using UniqueBuffer = VulkanHandle<VkBuffer, BufferDeleter>;
using UniqueDeviceMemory = VulkanHandle<VkDeviceMemory, DeviceMemoryDeleter>;
using UniqueDescriptorPool = VulkanHandle<VkDescriptorPool, DescriptorPoolDeleter>;
using UniqueDescriptorSetLayout = VulkanHandle<VkDescriptorSetLayout, DescriptorSetLayoutDeleter>;
using UniqueSemaphore = VulkanHandle<VkSemaphore, SemaphoreDeleter>;
using UniqueFence = VulkanHandle<VkFence, FenceDeleter>;
