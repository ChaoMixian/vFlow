#ifndef VFLOW_PPOCR_V5_H
#define VFLOW_PPOCR_V5_H

#include <opencv2/core/core.hpp>

#include <string>
#include <vector>

#include <net.h>

struct CharacterToken
{
    int id;
    float prob;
};

struct OcrObject
{
    cv::RotatedRect rrect;
    int orientation;
    float prob;
    std::vector<CharacterToken> text;
};

class PPOcrV5
{
public:
    PPOcrV5();
    ~PPOcrV5();

    int load(const char* detParamPath, const char* detModelPath, const char* recParamPath, const char* recModelPath, bool useFp16 = true);
    void setTargetSize(int targetSize);
    void setDictionary(std::vector<std::string> dictionary);

    int detect(const cv::Mat& rgb, std::vector<OcrObject>& objects);
    int recognize(const cv::Mat& rgb, OcrObject& object);
    int detectAndRecognize(const cv::Mat& rgb, std::vector<OcrObject>& objects);
    std::string decodeText(const OcrObject& object) const;

private:
    ncnn::Net detNet_;
    ncnn::Net recNet_;
    int targetSize_;
    std::vector<std::string> dictionary_;
};

#endif
