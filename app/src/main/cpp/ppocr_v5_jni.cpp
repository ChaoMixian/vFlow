#include "ppocr_v5.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include <opencv2/imgproc/imgproc.hpp>

namespace {

constexpr const char* TAG = "PpOcrV5Jni";

std::mutex gMutex;
PPOcrV5* gEngine = nullptr;
bool gLoaded = false;

std::string jstring_to_string(JNIEnv* env, jstring value)
{
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> load_dictionary(const std::string& path)
{
    std::ifstream input(path);
    std::vector<std::string> lines;
    std::string line;
    while (std::getline(input, line))
    {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
    }
    return lines;
}

std::string json_escape(const std::string& input)
{
    std::ostringstream out;
    for (unsigned char c : input)
    {
        switch (c)
        {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (c < 0x20)
                {
                    char buf[7];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out << buf;
                }
                else
                {
                    out << c;
                }
        }
    }
    return out.str();
}

cv::Mat bitmap_to_rgb(JNIEnv* env, jobject bitmap)
{
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        return cv::Mat();
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        return cv::Mat();
    }

    cv::Mat rgba;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        rgba = cv::Mat(info.height, info.width, CV_8UC4, pixels).clone();
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        cv::Mat rgb565(info.height, info.width, CV_8UC2, pixels);
        cv::cvtColor(rgb565, rgba, cv::COLOR_BGR5652RGBA);
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    if (rgba.empty()) return cv::Mat();

    cv::Mat rgb;
    cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);
    return rgb;
}

std::string run_ocr_json(JNIEnv* env, jobject bitmap)
{
    cv::Mat rgb = bitmap_to_rgb(env, bitmap);
    if (rgb.empty())
    {
        return "{\"success\":false,\"error\":\"bitmap_decode_failed\"}";
    }

    std::vector<OcrObject> objects;
    int result = gEngine->detectAndRecognize(rgb, objects);
    if (result != 0)
    {
        return "{\"success\":false,\"error\":\"ocr_inference_failed\"}";
    }

    std::ostringstream json;
    json << "{\"success\":true,\"items\":[";

    for (size_t i = 0; i < objects.size(); i++)
    {
        const OcrObject& object = objects[i];
        cv::Point2f corners[4];
        object.rrect.points(corners);
        std::string text = gEngine->decodeText(object);

        if (i > 0) json << ",";
        json << "{";
        json << "\"text\":\"" << json_escape(text) << "\",";
        json << "\"score\":" << object.prob << ",";
        json << "\"orientation\":" << object.orientation << ",";
        json << "\"centerX\":" << object.rrect.center.x << ",";
        json << "\"centerY\":" << object.rrect.center.y << ",";
        json << "\"width\":" << object.rrect.size.width << ",";
        json << "\"height\":" << object.rrect.size.height << ",";
        json << "\"angle\":" << object.rrect.angle << ",";
        json << "\"points\":[";
        for (int p = 0; p < 4; p++)
        {
            if (p > 0) json << ",";
            json << "{\"x\":" << corners[p].x << ",\"y\":" << corners[p].y << "}";
        }
        json << "]";
        json << "}";
    }

    json << "]}";
    return json.str();
}

} // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_chaomixian_vflow_ocr_PpOcrV5Native_nativeLoadModel(
    JNIEnv* env,
    jobject,
    jstring modelDirPath
)
{
    std::lock_guard<std::mutex> guard(gMutex);

    std::string modelDir = jstring_to_string(env, modelDirPath);
    std::string detParam = modelDir + "/det.ncnn.param";
    std::string detBin = modelDir + "/det.ncnn.bin";
    std::string recParam = modelDir + "/rec.ncnn.param";
    std::string recBin = modelDir + "/rec.ncnn.bin";
    std::string dictPath = modelDir + "/ppocr_keys.txt";

    delete gEngine;
    gEngine = new PPOcrV5();
    gEngine->setTargetSize(640);
    gEngine->setDictionary(load_dictionary(dictPath));

    int loadResult = gEngine->load(
        detParam.c_str(),
        detBin.c_str(),
        recParam.c_str(),
        recBin.c_str(),
        true
    );
    gLoaded = loadResult == 0;

    if (!gLoaded)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to load model, code=%d", loadResult);
    }

    return gLoaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_chaomixian_vflow_ocr_PpOcrV5Native_nativeRecognizeBitmap(
    JNIEnv* env,
    jobject,
    jobject bitmap
)
{
    std::lock_guard<std::mutex> guard(gMutex);

    if (!gLoaded || gEngine == nullptr)
    {
        return env->NewStringUTF("{\"success\":false,\"error\":\"model_not_loaded\"}");
    }

    std::string json = run_ocr_json(env, bitmap);
    return env->NewStringUTF(json.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_chaomixian_vflow_ocr_PpOcrV5Native_nativeRelease(
    JNIEnv*,
    jobject
)
{
    std::lock_guard<std::mutex> guard(gMutex);
    delete gEngine;
    gEngine = nullptr;
    gLoaded = false;
}
