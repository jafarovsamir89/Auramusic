#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "common.h"
#include "mainSystem.h"

namespace {
constexpr const char* kTag = "AuraChatScript";
constexpr size_t kOutputCapacity = 64 * 1024;

std::mutex gMutex;
bool gStarted = false;
std::string gRoot;

std::string jstringToString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring stringToJstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string runChat(const std::string& user, const std::string& bot, const std::string& input) {
    std::vector<char> incoming(input.begin(), input.end());
    incoming.push_back('\0');
    std::vector<char> output(kOutputCapacity, '\0');
    std::vector<char> userBuffer(user.begin(), user.end());
    userBuffer.push_back('\0');
    std::vector<char> botBuffer(bot.begin(), bot.end());
    botBuffer.push_back('\0');
    PerformChat(userBuffer.data(), botBuffer.data(), incoming.data(), nullptr, output.data());
    return std::string(output.data());
}

std::string startEngine(const std::string& root, const std::string& bot, const std::string& user) {
    if (!gStarted) {
        gRoot = root;
        std::vector<char> appName{'A', 'U', 'R', 'A', '\0'};
        std::vector<char> rootBuffer(root.begin(), root.end());
        rootBuffer.push_back('\0');
        char* args[] = {appName.data(), rootBuffer.data(), nullptr};
        unsigned int status = InitSystem(2, args, rootBuffer.data(), nullptr, nullptr, nullptr, nullptr, nullptr);
        if (status != 0) {
            __android_log_print(ANDROID_LOG_ERROR, kTag, "InitSystem failed: %u", status);
            return "__ERROR__:ChatScript InitSystem failed (" + std::to_string(status) + ")";
        }
        gStarted = true;

        // The lab's RU/AZ brains are intentionally compiled from their tiny
        // RAWDATA scripts on first use. This keeps the experiment editable
        // without shipping a second opaque binary topic pack.
        if (bot != "harry") {
            std::string command = std::string(":build ") + (bot == "aura_az_lab" ? "AuraAz" : "AuraRu");
            std::vector<char> commandBuffer(command.begin(), command.end());
            commandBuffer.push_back('\0');
            std::vector<char> commandOutput(kOutputCapacity, '\0');
            DoCommand(commandBuffer.data(), commandOutput.data(), true);
            __android_log_print(ANDROID_LOG_INFO, kTag, "Compiled %s: %s", bot.c_str(), commandOutput.data());
            CloseSystem();
            gStarted = false;
            status = InitSystem(2, args, rootBuffer.data(), nullptr, nullptr, nullptr, nullptr, nullptr);
            if (status != 0) {
                return "__ERROR__:ChatScript reload failed (" + std::to_string(status) + ")";
            }
            gStarted = true;
        }
    }
    return runChat(user, bot, "");
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_az_simplesoft_aura_assistant_chatscript_ChatScriptNative_nativeStart(
        JNIEnv* env, jobject, jstring root, jstring bot, jstring user) {
    std::lock_guard<std::mutex> lock(gMutex);
    return stringToJstring(env, startEngine(jstringToString(env, root), jstringToString(env, bot), jstringToString(env, user)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_az_simplesoft_aura_assistant_chatscript_ChatScriptNative_nativeSend(
        JNIEnv* env, jobject, jstring bot, jstring user, jstring text) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gStarted) return stringToJstring(env, "__ERROR__:ChatScript session is not started");
    return stringToJstring(env, runChat(jstringToString(env, user), jstringToString(env, bot), jstringToString(env, text)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_az_simplesoft_aura_assistant_chatscript_ChatScriptNative_nativeReset(
        JNIEnv* env, jobject, jstring bot, jstring user) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gStarted) return stringToJstring(env, "__ERROR__:ChatScript session is not started");
    return stringToJstring(env, runChat(jstringToString(env, user), jstringToString(env, bot), ":reset:"));
}

extern "C" JNIEXPORT void JNICALL
Java_az_simplesoft_aura_assistant_chatscript_ChatScriptNative_nativeClose(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gStarted) CloseSystem();
    gStarted = false;
    gRoot.clear();
}
