#include <jni.h>
#include <vulkan/vulkan.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int extensions_ok(VkPhysicalDevice device) {
    const char *const required[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME, VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME,
        VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME, VK_EXT_ROBUSTNESS_2_EXTENSION_NAME};
    uint32_t count = 0;
    if (vkEnumerateDeviceExtensionProperties(device, NULL, &count, NULL) != VK_SUCCESS || !count)
        return 0;
    VkExtensionProperties *extensions = calloc(count, sizeof(*extensions));
    if (!extensions) return 0;
    size_t found = 0;
    if (vkEnumerateDeviceExtensionProperties(device, NULL, &count, extensions) == VK_SUCCESS) {
        for (size_t i = 0; i < sizeof(required) / sizeof(required[0]); ++i) {
            for (uint32_t j = 0; j < count; ++j) {
                if (!strcmp(required[i], extensions[j].extensionName)) {
                    ++found;
                    break;
                }
            }
        }
    }
    free(extensions);
    return found == sizeof(required) / sizeof(required[0]);
}

JNIEXPORT jstring JNICALL Java_org_pistonport_nativeandroid_GpuInfoKt_readStockVulkan(JNIEnv *env, jclass clazz) {
    (void)clazz;
    char output[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE + 48] = "GPU não detectada\tindisponível\t0";
    uint32_t loader_version = VK_API_VERSION_1_0;
    if (vkEnumerateInstanceVersion(&loader_version) != VK_SUCCESS)
        loader_version = VK_API_VERSION_1_0;
    const VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .apiVersion = loader_version < VK_API_VERSION_1_3 ? loader_version : VK_API_VERSION_1_3};
    const VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkInstance instance;
    if (vkCreateInstance(&info, NULL, &instance) == VK_SUCCESS) {
        uint32_t count = 0;
        if (vkEnumeratePhysicalDevices(instance, &count, NULL) == VK_SUCCESS && count) {
            VkPhysicalDevice *devices = calloc(count, sizeof(*devices));
            if (devices && vkEnumeratePhysicalDevices(instance, &count, devices) == VK_SUCCESS) {
                for (uint32_t i = 0; i < count; ++i) {
                    VkPhysicalDeviceProperties p;
                    vkGetPhysicalDeviceProperties(devices[i], &p);
                    const int ready = loader_version >= VK_API_VERSION_1_3 &&
                        p.apiVersion >= VK_API_VERSION_1_3 && extensions_ok(devices[i]);
                    if (i == 0 || ready || p.vendorID == 0x5143 || p.vendorID == 0x13b5)
                        snprintf(output, sizeof(output), "%s\t%u.%u.%u\t%d\t%x", p.deviceName,
                            VK_API_VERSION_MAJOR(p.apiVersion), VK_API_VERSION_MINOR(p.apiVersion),
                            VK_API_VERSION_PATCH(p.apiVersion), ready, p.vendorID);
                    if (ready) break;
                }
            }
            free(devices);
        }
        vkDestroyInstance(instance, NULL);
    }
    return (*env)->NewStringUTF(env, output);
}
