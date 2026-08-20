#include <iostream>
#include <fstream>
#include <Sandbox.h>
#include "Processor_Minecraft.h"

int main()
{
    cv::setNumThreads(cv::getNumberOfCPUs());

    GameEngine engine;
    engine.changeScene<Scene_Main>("Menu");
    std::dynamic_pointer_cast<Scene_Main>(engine.currentScene())->registerProcessor<Processor_Minecraft>("Minecraft");
    engine.run();
    return 0;
}