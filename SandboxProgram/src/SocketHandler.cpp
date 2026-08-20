#include "SocketHandler.h"
#include "imgui.h"
#include "imgui-SFML.h"

#include <zmq_addon.hpp>
#include <thread>

SocketHandler::SocketHandler() : m_socket(m_context, zmq::socket_type::rep)
{
    changePalette(1);
    m_spawnNames.emplace(0, "minecraft:sheep");
    m_spawnNames.emplace(1, "minecraft:wolf");
    m_spawnNames.emplace(2, "minecraft:villager");
    m_spawnNames.emplace(3, "minecraft:vindicator");
    m_spawnNames.emplace(4, "minecraft:iron_golem");
}

void SocketHandler::imgui()
{
    if (!m_running)
    {
        ImGui::InputInt("Port", &m_port);
        if (ImGui::Button("Connect"))
        {
            connect();
        }
    }
    else
    {
        if (ImGui::Button("Disconnect"))
        {
            m_socket.unbind(std::format("tcp://127.0.0.1:{}", m_port));
            stop();
        }
    }

    ImGui::InputInt("Block Height:", &m_blockHeight);

    const char* profiles[] = {"Monochrome", "Basic Grass", "Desert", "Nether", "Snow"};
    if (ImGui::Combo("Generation Profile", &m_currentProfile, profiles, IM_ARRAYSIZE(profiles)))
    {
        changePalette(m_currentProfile);
    }

    if (ImGui::CollapsingHeader("Generation Profile Settings"))
    {
        m_profile->imgui();
    }
}

void SocketHandler::connect()
{
    m_socket.bind(std::format("tcp://127.0.0.1:{}", m_port));
    m_running = true;
    m_changePalette = true;
    m_sendReset = true;
    m_thread = std::thread([this]()
        {
            while (m_running) {
                zmq::message_t request;
                auto result = m_socket.recv(request, zmq::recv_flags::dontwait);
                if (result)
                {
                    m_lock.lock();
                    // If anything changes, reset
                    int wx = m_data.cols;
                    int wz = m_data.rows;
                    const auto & cube = m_cubes[m_currentCube];
                    if (request.to_string() == "Restart Pls" || wx != cube.sizeX() || m_blockHeight != cube.sizeY() || wz != cube.sizeZ())
                    {
                        m_cubes[0] = Cube<uint8_t>(wx, m_blockHeight, wz, 0);
                        m_cubes[1] = Cube<uint8_t>(wx, m_blockHeight, wz, 0);
                        m_sendReset = true;
                        m_changePalette = true;
                    }

                    // Construct Message
                    std::vector<zmq::message_t> messages;

                    if (m_sendReset)
                    {
                        resetMessage(messages);
                    }

                    if (m_changePalette)
                    {
                        paletteMessage(messages);
                        // Clean up cubes
                        m_cubes[0] = Cube<uint8_t>(wx, m_blockHeight, wz, 0);
                        m_cubes[1] = Cube<uint8_t>(wx, m_blockHeight, wz, 0);
                    }
                    

                    updateMessage(messages);
                    spawnMessage(messages); // This may or may not tell it to spawn entities

                    // Send Message
                    if (!zmq::send_multipart(m_socket, messages))
                    {
                        std::cout << "Failed to send messages" << std::endl;
                    }
                    m_lock.unlock();
                }
            }
            std::cout << "Thread Ending" << std::endl;
            
        });
}

void SocketHandler::stop()
{
    if (m_thread.joinable())
    {
        m_running = false;
        m_thread.join();
        std::cout << "Socket Thread Joined" << std::endl;
    }
}

void SocketHandler::updateData(cv::Mat data, const std::vector<MarkerData>& markers)
{
    if (m_lock.try_lock())
    {
        m_data = data;
        m_markers = markers;
        m_lock.unlock();
    }
}

SocketHandler::~SocketHandler()
{
    stop();
}

void SocketHandler::updateMessage(std::vector<zmq::message_t> & messages)
{
    const int update = Update;
    messages.push_back(zmq::message_t(&update, sizeof(int)));

    std::vector<int> changes;
    int nextCubeId = (m_currentCube + 1) % 2;
    m_profile->generate(m_cubes[nextCubeId], m_data, m_blockHeight);

    int wx = m_data.cols;
    int wz = m_data.rows;

    const Cube<uint8_t> & cube = m_cubes[nextCubeId];
    const Cube<uint8_t> & pastCube = m_cubes[m_currentCube];

    for (int x = 0; x < wx; ++x)
    {
        for (int y = 0; y < m_blockHeight; ++y)
        {
            for (int z = 0; z < wz; ++z)
            {
                uint8_t block = cube.get(x, y, z);
                if (block != pastCube.get(x, y, z))
                {
                    changes.insert(changes.end(), { x,y,z,(int) block }); // Add block to list of changes
                }
            }
        }
    }

    m_currentCube = nextCubeId;
    messages.push_back(zmq::message_t(changes.data(), sizeof(int) * changes.size()));
}

void SocketHandler::paletteMessage(std::vector<zmq::message_t> & messages)
{
    const int palette = Palette;
    messages.push_back(zmq::message_t(&palette, sizeof(int)));
    messages.push_back(zmq::message_t(m_profile->biome()));
    int length = (int)m_profile->numberOfBlocks();
    messages.push_back(zmq::message_t(&length, sizeof(int)));
    for (const std::string & name : m_profile->blockNames())
    {
        messages.push_back(zmq::message_t(name));
    }
    m_changePalette = false;
}

void SocketHandler::resetMessage(std::vector<zmq::message_t> & messages)
{
    const int reset = Reset;
    messages.push_back(zmq::message_t(&reset, sizeof(int)));
    int coords[6] = {0,0,0, m_data.cols, m_blockHeight, m_data.rows};
    messages.push_back(zmq::message_t(coords));
    m_sendReset = false;
}

void SocketHandler::spawnMessage(std::vector<zmq::message_t>& messages)
{
    const int spawn = Spawn;
    for (auto& marker : m_markers)
    {
        const std::string& entity = spawnName(marker.id);
        if (entity == "") { continue; }
        messages.push_back(zmq::message_t(&spawn, sizeof(int)));
        float coords[2] = { marker.center.x, marker.center.y };
        messages.push_back(zmq::message_t(coords));
        messages.push_back(zmq::message_t(entity));
    }
}

void SocketHandler::changePalette(int profile)
{
    m_changePalette = true;
    m_sendReset = true;
    switch (profile)
    {
    case 0: m_profile = std::make_shared<MonochromeProfile>(); break;
    case 1: m_profile = std::make_shared<BasicGrassProfile>(); break;
    case 2: m_profile = std::make_shared<DesertProfile>(); break;
    case 3: m_profile = std::make_shared<NetherProfile>(); break;
    case 4: m_profile = std::make_shared<SnowProfile>(); break;
    }
    m_currentProfile = profile;
}
