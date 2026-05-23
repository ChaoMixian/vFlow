#include "ppocr_v5.h"

#include "cpu.h"

#include <algorithm>
#include <utility>

#include <opencv2/imgproc/imgproc.hpp>

static double contour_score(const cv::Mat& binary, const std::vector<cv::Point>& contour)
{
    cv::Rect rect = cv::boundingRect(contour);
    rect.x = std::max(rect.x, 0);
    rect.y = std::max(rect.y, 0);
    rect.width = std::min(rect.width, binary.cols - rect.x);
    rect.height = std::min(rect.height, binary.rows - rect.y);

    cv::Mat binROI = binary(rect);
    cv::Mat mask = cv::Mat::zeros(rect.height, rect.width, CV_8U);

    std::vector<cv::Point> roiContour;
    roiContour.reserve(contour.size());
    for (const cv::Point& point : contour)
    {
        roiContour.emplace_back(point.x - rect.x, point.y - rect.y);
    }

    cv::fillPoly(mask, std::vector<std::vector<cv::Point>>{roiContour}, cv::Scalar(255));
    return cv::mean(binROI, mask).val[0] / 255.f;
}

static cv::Mat get_rotate_crop_image(const cv::Mat& rgb, const OcrObject& object)
{
    const float rw = object.rrect.size.width;
    const float rh = object.rrect.size.height;
    const int targetHeight = 48;
    const float targetWidth = rh * targetHeight / rw;

    cv::Point2f corners[4];
    object.rrect.points(corners);

    cv::Mat dst;
    if (object.orientation == 0)
    {
        std::vector<cv::Point2f> srcPts = {corners[0], corners[1], corners[3]};
        std::vector<cv::Point2f> dstPts = {
            cv::Point2f(0, 0),
            cv::Point2f(targetWidth, 0),
            cv::Point2f(0, targetHeight)
        };
        cv::Mat transform = cv::getAffineTransform(srcPts, dstPts);
        cv::warpAffine(rgb, dst, transform, cv::Size(targetWidth, targetHeight), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }
    else
    {
        std::vector<cv::Point2f> srcPts = {corners[2], corners[3], corners[1]};
        std::vector<cv::Point2f> dstPts = {
            cv::Point2f(0, 0),
            cv::Point2f(targetWidth, 0),
            cv::Point2f(0, targetHeight)
        };
        cv::Mat transform = cv::getAffineTransform(srcPts, dstPts);
        cv::warpAffine(rgb, dst, transform, cv::Size(targetWidth, targetHeight), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }

    return dst;
}

PPOcrV5::PPOcrV5() : targetSize_(640) {}

PPOcrV5::~PPOcrV5() = default;

int PPOcrV5::load(const char* detParamPath, const char* detModelPath, const char* recParamPath, const char* recModelPath, bool useFp16)
{
    detNet_.clear();
    recNet_.clear();

    detNet_.opt.use_vulkan_compute = false;
    detNet_.opt.use_fp16_packed = useFp16;
    detNet_.opt.use_fp16_storage = useFp16;
    detNet_.opt.use_fp16_arithmetic = useFp16;

    recNet_.opt.num_threads = 1;
    recNet_.opt.use_vulkan_compute = false;
    recNet_.opt.use_fp16_packed = useFp16;
    recNet_.opt.use_fp16_storage = useFp16;
    recNet_.opt.use_fp16_arithmetic = useFp16;

    if (detNet_.load_param(detParamPath) != 0) return -1;
    if (detNet_.load_model(detModelPath) != 0) return -2;
    if (recNet_.load_param(recParamPath) != 0) return -3;
    if (recNet_.load_model(recModelPath) != 0) return -4;

    return 0;
}

void PPOcrV5::setTargetSize(int targetSize)
{
    targetSize_ = targetSize;
}

void PPOcrV5::setDictionary(std::vector<std::string> dictionary)
{
    dictionary_ = std::move(dictionary);
}

int PPOcrV5::detect(const cv::Mat& rgb, std::vector<OcrObject>& objects)
{
    objects.clear();
    cv::setNumThreads(ncnn::get_big_cpu_count());

    int imgW = rgb.cols;
    int imgH = rgb.rows;
    const int targetStride = 32;

    int w = imgW;
    int h = imgH;
    float scale = 1.f;
    if (std::max(w, h) > targetSize_)
    {
        if (w > h)
        {
            scale = (float)targetSize_ / w;
            w = targetSize_;
            h = h * scale;
        }
        else
        {
            scale = (float)targetSize_ / h;
            h = targetSize_;
            w = w * scale;
        }
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgb.data, ncnn::Mat::PIXEL_RGB2BGR, imgW, imgH, w, h);

    int wpad = (w + targetStride - 1) / targetStride * targetStride - w;
    int hpad = (h + targetStride - 1) / targetStride * targetStride - h;

    ncnn::Mat inPad;
    ncnn::copy_make_border(in, inPad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);

    const float meanVals[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float normVals[3] = {1 / 0.229f / 255.f, 1 / 0.224f / 255.f, 1 / 0.225f / 255.f};
    inPad.substract_mean_normalize(meanVals, normVals);

    ncnn::Extractor ex = detNet_.create_extractor();
    ex.input("input", inPad);

    ncnn::Mat out;
    if (ex.extract("output", out) != 0) return -5;

    const float denormVals[1] = {255.f};
    out.substract_mean_normalize(0, denormVals);

    cv::Mat pred(out.h, out.w, CV_8UC1);
    out.to_pixels(pred.data, ncnn::Mat::PIXEL_GRAY);

    cv::Mat bitmap;
    cv::threshold(pred, bitmap, 0.3f * 255, 255, cv::THRESH_BINARY);

    const float boxThresh = 0.6f;
    const float enlargeRatio = 1.95f;
    const float minSize = 3 * scale;
    const int maxCandidates = 1000;

    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    cv::findContours(bitmap, contours, hierarchy, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);
    contours.resize(std::min(contours.size(), (size_t)maxCandidates));

    for (const auto& contour : contours)
    {
        if (contour.size() <= 2) continue;

        double score = contour_score(pred, contour);
        if (score < boxThresh) continue;

        cv::RotatedRect rrect = cv::minAreaRect(contour);
        if (std::max(rrect.size.width, rrect.size.height) < minSize) continue;

        int orientation = 0;
        if (rrect.angle >= -30 && rrect.angle <= 30 && rrect.size.height > rrect.size.width * 2.7) orientation = 1;
        if ((rrect.angle <= -60 || rrect.angle >= 60) && rrect.size.width > rrect.size.height * 2.7) orientation = 1;

        if (rrect.angle < -30)
        {
            rrect.angle += 180;
        }
        if (orientation == 0 && rrect.angle < 30)
        {
            rrect.angle += 90;
            std::swap(rrect.size.width, rrect.size.height);
        }
        if (orientation == 1 && rrect.angle >= 60)
        {
            rrect.angle -= 90;
            std::swap(rrect.size.width, rrect.size.height);
        }

        rrect.size.height += rrect.size.width * (enlargeRatio - 1);
        rrect.size.width *= enlargeRatio;

        rrect.center.x = (rrect.center.x - (wpad / 2)) / scale;
        rrect.center.y = (rrect.center.y - (hpad / 2)) / scale;
        rrect.size.width = rrect.size.width / scale;
        rrect.size.height = rrect.size.height / scale;

        OcrObject object;
        object.rrect = rrect;
        object.orientation = orientation;
        object.prob = score;
        objects.push_back(object);
    }

    return 0;
}

int PPOcrV5::recognize(const cv::Mat& rgb, OcrObject& object)
{
    cv::setNumThreads(1);

    cv::Mat roi = get_rotate_crop_image(rgb, object);
    ncnn::Mat in = ncnn::Mat::from_pixels(roi.data, ncnn::Mat::PIXEL_RGB2BGR, roi.cols, roi.rows);

    const float meanVals[3] = {127.5f, 127.5f, 127.5f};
    const float normVals[3] = {1.f / 127.5f, 1.f / 127.5f, 1.f / 127.5f};
    in.substract_mean_normalize(meanVals, normVals);

    ncnn::Extractor ex = recNet_.create_extractor();
    ex.input("input", in);

    ncnn::Mat out;
    if (ex.extract("output", out) != 0) return -6;

    int lastToken = 0;
    object.text.clear();

    for (int i = 0; i < out.h; i++)
    {
        const float* row = out.row(i);
        int index = 0;
        float maxScore = -9999.f;
        for (int j = 0; j < out.w; j++)
        {
            float score = row[j];
            if (score > maxScore)
            {
                maxScore = score;
                index = j;
            }
        }

        if (lastToken == index) continue;
        lastToken = index;
        if (index <= 0) continue;

        CharacterToken token;
        token.id = index;
        token.prob = maxScore;
        object.text.push_back(token);
    }

    return 0;
}

int PPOcrV5::detectAndRecognize(const cv::Mat& rgb, std::vector<OcrObject>& objects)
{
    int detectResult = detect(rgb, objects);
    if (detectResult != 0) return detectResult;

    for (auto& object : objects)
    {
        int recResult = recognize(rgb, object);
        if (recResult != 0) return recResult;
    }

    return 0;
}

std::string PPOcrV5::decodeText(const OcrObject& object) const
{
    std::string text;
    for (const auto& token : object.text)
    {
        if (token.id < 0 || token.id >= (int)dictionary_.size()) continue;
        text += dictionary_[token.id];
    }
    return text;
}
