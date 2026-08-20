#include "Processor_Minecraft.h"

Processor_Minecraft::Processor_Minecraft()
{
}

void Processor_Minecraft::init()
{
    
}

void Processor_Minecraft::imgui()
{
    m_socket.imgui();
}

void Processor_Minecraft::render(sf::RenderWindow & window)
{
}

void Processor_Minecraft::processEvent(const sf::Event & event, const sf::Vector2f & mouse)
{
}

void Processor_Minecraft::save(Save & save) const
{

}
void Processor_Minecraft::load(const Save & save)
{

}

void Processor_Minecraft::processTopography(const IntermediateData& data)
{
    m_socket.updateData(data.topography, data.markers);
}
