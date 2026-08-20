#pragma once

#include <vector>
#include <string>
#include <opencv2/opencv.hpp>
#include "Cube.hpp"
#include "Perlin.hpp"

class GenerationProfile
{
protected:
    std::vector<std::string> m_blockNames;
    std::string m_biome = "void";;
public:
    virtual void imgui() {};
    virtual void generate(Cube<uint8_t> & output, cv::Mat input, int blockScale) = 0;
    inline const std::string & blockName(uint8_t id) const { return m_blockNames[id]; };
    inline const size_t numberOfBlocks() const { return m_blockNames.size(); }
    inline const std::vector<std::string> & blockNames() const { return m_blockNames; }
    inline const std::string & biome() const { return m_biome; }
};

class BasicGrassProfile : public GenerationProfile
{
    float m_waterLevel = 0.3f;
    float m_snowLevel = 0.7f;
public:
    BasicGrassProfile();
    void imgui();
    void generate(Cube<uint8_t> & output, cv::Mat input, int blockScale);
};

class MonochromeProfile : public GenerationProfile
{
public:
    MonochromeProfile();
    void generate(Cube<uint8_t> & output, cv::Mat input, int blockScale);
};

class DesertProfile : public GenerationProfile
{
    float m_waterLevel = 0.3f;
    float m_cactusThreshold = 0.99f;
    Grid<float> m_cacti;
    bool waterAdjacent(int i, int j, int blockScale, int water, cv::Mat input);
    void recalculateCacti(int w, int h);
public:
    DesertProfile();
    void imgui();
    void generate(Cube<uint8_t>& output, cv::Mat input, int blockScale);
};

class NetherProfile : public GenerationProfile
{
    float m_lavaLevel = 0.3f;
    Grid<float> m_noise;
    bool m_ceiling = true;
public:
    NetherProfile();
    void imgui();
    void recalculateNoise(int w, int h);
    void generate(Cube<uint8_t>& output, cv::Mat input, int blockScale);
};

class SnowProfile : public GenerationProfile
{
    float m_iceLevel = 0.3f;
public:
    SnowProfile();
    void imgui();
    void generate(Cube<uint8_t>& output, cv::Mat input, int blockScale);
};