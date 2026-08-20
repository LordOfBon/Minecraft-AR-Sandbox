#include "BlockGeneration.h"
#include <imgui-SFML.h>
#include <imgui.h>
#include <opencv2/opencv.hpp>

BasicGrassProfile::BasicGrassProfile()
{
    m_blockNames = { "air", "stone", "dirt", "grass_block", "water", "snow_block"};
    m_biome = "plains";
}

void BasicGrassProfile::imgui()
{
    ImGui::SliderFloat("MC Water Level", &m_waterLevel, 0.0f, 1.0f);
    ImGui::SliderFloat("MC Snow Level", &m_snowLevel, 0.0f, 1.0f);
}

void BasicGrassProfile::generate(Cube<uint8_t> & output, cv::Mat input, int blockScale)
{
    int wx = input.cols;
    int wz = input.rows;
    int water = (int)(std::ceil(blockScale * m_waterLevel));
    int snow = (int)(std::ceil(blockScale * m_snowLevel));

    if (wx <= 0 || wz <= 0) { return; }

    output.refill(wx, blockScale, wz, 0);

    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            int height = (int)(input.at<float>(j, i) * blockScale);
            if (height >= water)
            {
                if (height >= snow)
                {
                    output.fill(i, snow, j, i, height, j, 5); // snow block
                    height = snow - 1;
                }
                else
                {
                    output.fill(i, height, j, i, height, j, 3); // grass block
                    height--;
                }
            }
            else
            {
                output.fill(i, height, j, i, water, j, 4); // water
            }
            if (height > 1)
            {
                output.fill(i, height, j, i, height, j, 2); //dirt
                height--;
            }
            if (height > 1)
            {
                output.fill(i, 0, j, i, height, j, 1); // stone
            }
        }
    }
}

MonochromeProfile::MonochromeProfile()
{
    m_blockNames = { "minecraft:air", "minecraft:black_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete", "minecraft:white_concrete"};
}
void MonochromeProfile::generate(Cube<uint8_t> & output, cv::Mat input, int blockScale)
{
    int wx = input.cols;
    int wz = input.rows;

    if (wx <= 0 || wz <= 0) { return; }

    output.refill(wx, blockScale, wz, 0);

    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            int height = (int)(input.at<float>(j, i) * blockScale);
            output.fill(i, 0, j, i, height, j, height * 4 / blockScale + 1);
        }
    }
}

bool DesertProfile::waterAdjacent(int i, int j, int blockScale, int water, cv::Mat input)
{
    const static int adjacent[][2] = { {0,1}, {0, -1}, {1, 0}, {-1, 0} };

    for (auto [x, y] : adjacent)
    {
        int i2 = i + x;
        int j2 = j + y;
        if (i2 >= 0 && i2 < input.cols && j2 >= 0 && j2 < input.rows)
        {
            int height = (int)(input.at<float>(j2, i2) * blockScale);
            if (height < water) { return true; }
        }
    }
    return false;
}

DesertProfile::DesertProfile()
{
    m_blockNames = { "minecraft:air", "minecraft:sand", "minecraft:sandstone", "minecraft:grass_block", "minecraft:mud", "minecraft:water", "minecraft:cactus"};
    m_biome = "desert";
}

void DesertProfile::recalculateCacti(int w, int h)
{
    Perlin2DNew p(w, h);
    m_cacti = p.GeneratePerlinNoise(1, 0.2f);
}

void DesertProfile::imgui()
{
    ImGui::SliderFloat("MC Water Level", &m_waterLevel, 0.0f, 1.0f);
    ImGui::SliderFloat("MC Cactus Threshold", &m_cactusThreshold, 0.0f, 1.0f);
}

void DesertProfile::generate(Cube<uint8_t>& output, cv::Mat input, int blockScale)
{
    int wx = input.cols;
    int wz = input.rows;
    int water = (int)(std::ceil(blockScale * m_waterLevel));

    if (wx != m_cacti.width() || wz != m_cacti.height())
    {
        recalculateCacti(wx, wz);
    }

    if (wx <= 0 || wz <= 0) { return; }

    output.refill(wx, blockScale, wz, 0);

    std::vector<cv::Point2i> mud;

    // First pass
    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            int height = (int)(input.at<float>(j, i) * blockScale);

            // water
            if (height < water)
            {
                output.fill(i, height, j, i, water, j, 5);
            }

            // Mud
            if (height == water && waterAdjacent(i, j, blockScale, water, input))
            {
                output.fill(i, height, j, i, height, j, 4);
                height--;
                mud.push_back(cv::Point2i(i,j));
            }
            // Sand
            else if (height > 1)
            {
                output.fill(i, height, j, i, height, j, 1);
                height--;
            }

            // Sandstone
            if (height > 1)
            {
                output.fill(i, 0, j, i, height, j, 2);
            }
        }
    }

    // Second pass
    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            int height = (int)(input.at<float>(j, i) * blockScale);
            uint8_t block = output.get(i, height, j);
            bool cactus = m_cacti.get(i, j) > m_cactusThreshold;

            // Grass
            if ((height == water || height - 1 == water) && block != 4)
            {
                for (auto v : mud)
                {
                    if (abs(i - v.x) < 2 && abs(j - v.y) < 2)
                    {
                        output.fill(i, height, j, i, height, j, 3);
                        break;
                    }
                }
            }
            // Cactus
            else if (cactus && height >= water && block == 1 && height + 2 < blockScale)
            {
                output.fill(i, height + 1, j, i, height + 2, j, 6);
            }
        }
    }
}

NetherProfile::NetherProfile()
{
    m_blockNames = {"minecraft:air", "minecraft:netherrack", "minecraft:basalt", "minecraft:lava", "minecraft:fire"};
    m_biome = "nether_wastes";
}

void NetherProfile::imgui()
{
    ImGui::SliderFloat("MC Lava Level", &m_lavaLevel, 0.0f, 1.0f);
    ImGui::Checkbox("MC Ceiling", &m_ceiling);
}

void NetherProfile::recalculateNoise(int w, int h)
{
    Perlin2DNew p(w, h);
    m_noise = p.GeneratePerlinNoise(1, 0.2f);
}

void NetherProfile::generate(Cube<uint8_t>& output, cv::Mat input, int blockScale)
{
    int wx = input.cols;
    int wz = input.rows;
    int lava = (int)(std::ceil(blockScale * m_lavaLevel));
    float ceilingScale = (float)(blockScale / 5);

    if (wx <= 0 || wz <= 0) { return; }

    if (wx != m_noise.width() || wz != m_noise.height())
    {
        recalculateNoise(wx, wz);
    }

    output.refill(wx, blockScale, wz, 0);

    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            if (m_ceiling)
            {
                // Ceiling
                int y = blockScale - 1 - (int)(input.at<float>(j, i) * ceilingScale);
                output.fill(i, y, j, i, blockScale - 1, j, 1);
            }

            // Floor and pillars
            int height = (int)(input.at<float>(j, i) * blockScale);
            float n = m_noise.get(i, j);
            bool pillar = n > 0.995;
            bool fire = n > 0.49 && n <= 0.5;
            if (height < lava)
            {
                if (pillar)
                {
                    output.fill(i, height, j, i, blockScale - 1, j, 3); // lava pillar
                }
                else
                {
                    output.fill(i, height, j, i, lava, j, 3); // lava
                }
            }
            else if (pillar && height < blockScale)
            {
                output.fill(i, height + 1, j, i, blockScale - 1, j, 2); // basalt pillar
            }
            else if (fire && height < blockScale)
            {
                output.fill(i, height+1, j, i, height+1, j, 4); // fire
            }

            if (height > 1)
            {
                output.fill(i, 0, j, i, height, j, 1); // netherrack
            }
        }
    }
}

SnowProfile::SnowProfile()
{
    m_blockNames = { "air", "snow_block", "ice", "blue_ice" };
    m_biome = "snowy_plains";
}

void SnowProfile::imgui()
{
    ImGui::SliderFloat("MC Ice Level", &m_iceLevel, 0.0f, 1.0f);
}

void SnowProfile::generate(Cube<uint8_t>& output, cv::Mat input, int blockScale)
{
    int wx = input.cols;
    int wz = input.rows;
    int ice = (int)(std::ceil(blockScale * m_iceLevel));

    if (wx <= 0 || wz <= 0) { return; }

    output.refill(wx, blockScale, wz, 0);

    for (int i = 0; i < wx; ++i)
    {
        for (int j = 0; j < wz; ++j)
        {
            int height = (int)(input.at<float>(j, i) * blockScale);
            if (height >= ice)
            {
                output.fill(i, height, j, i, height, j, 1); // snow
                height--;
            }
            else
            {
                output.fill(i, height, j, i, ice, j, 2); // ice
            }
            if (height > 1)
            {
                output.fill(i, height, j, i, height, j, 1); // snow
                height--;
            }
            if (height > 1)
            {
                output.fill(i, 0, j, i, height, j, 3); // stone
            }
        }
    }
}
