#pragma once

#include "TopographyProcessor.h"
#include "SocketHandler.h"

#include <opencv2/opencv.hpp>
#include <SFML/Graphics.hpp>

class Processor_Minecraft : public TopographyProcessor
{
     SocketHandler m_socket;

public:
    Processor_Minecraft();
    void init();
    void imgui();
    void render(sf::RenderWindow & window);
    void processEvent(const sf::Event & event, const sf::Vector2f & mouse);
    void save(Save & save) const;
    void load(const Save & save);

    void processTopography(const IntermediateData& data);
};