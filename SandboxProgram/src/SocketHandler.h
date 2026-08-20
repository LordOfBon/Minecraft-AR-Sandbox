#pragma once

#include <zmq.hpp>
#include <opencv2/opencv.hpp>
#include <SFML/Graphics.hpp>

#include "BlockGeneration.h"
#include <MarkerData.h>

enum MessageTypes
{
    Palette,
    Update,
    Reset,
    Spawn,
};

class SocketHandler
{
    int m_port = 4960;
    bool m_running = false;
    zmq::context_t m_context;
    zmq::socket_t m_socket;
    std::thread m_thread;
    cv::Mat m_data;
    std::mutex m_lock;

    std::shared_ptr<GenerationProfile> m_profile;
    int m_currentProfile = 0;

    std::map<int, std::string> m_spawnNames;

    bool m_changePalette = false;
    bool m_sendReset = false;

    int m_currentCube = 0;
    Cube<uint8_t> m_cubes[2];

    std::vector<MarkerData> m_markers;

    int m_blockHeight = 40;

    void updateMessage(std::vector<zmq::message_t> & messages);
    void paletteMessage(std::vector<zmq::message_t> & messages);
    void resetMessage(std::vector<zmq::message_t> & messages);
    void spawnMessage(std::vector<zmq::message_t>& messages);

    void changePalette(int profile);


public:
    SocketHandler();
    void imgui();
    void connect();
    void stop();
    void updateData(cv::Mat data, const std::vector<MarkerData> & markers);
    ~SocketHandler();

    inline const std::string & spawnName(int id) const
    {
        if (!m_spawnNames.contains(id)) { return ""; }
        return m_spawnNames.at(id);
    }
};