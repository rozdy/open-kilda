#include "server42/ProcessThread.h"
#include "server42/Payload.h"
#include "statistics.pb.h"
#include <thread>

#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/PayloadLayer.h>
#include <netinet/in.h>
#include <zmq.hpp>
#include <rte_branch_prediction.h>
#include <rte_mbuf.h>

ProcessThread::ProcessThread(rte_ring *pRing, uint16_t port) : m_Stop(true), m_CoreId(MAX_NUM_OF_CORES + 1),
                                                               not_initilized(true),
                                                               m_rx_ring(pRing), m_port(port) {

}

bool ProcessThread::run(uint32_t coreId) {
    // Register coreId for this worker
    m_CoreId = coreId;
    m_Stop = false;

    //  Prepare our context and publisher
    zmq::context_t context(1);
    zmq::socket_t socket(context, ZMQ_PUSH);
    std::stringstream ss;
    ss << "tcp://*:" << m_port;
    socket.bind(ss.str().c_str());

    // endless loop, until asking the thread to stop
    uint64_t packet_id = 0;
    uint8_t udp_offset = 0;
    uint8_t payload_offset = 0;
    pcpp::Packet dummy_packet;

    const uint16_t src_port = htons(1234);
    const uint16_t dst_port = htons(5678);

    org::openkilda::FlowLatencyPacketBucket flow_bucket;

    // pre init bucket for zmq::message
    for (uint_fast8_t i = 0; i < 32; ++i) {
        org::openkilda::FlowLatencyPacket *packet = flow_bucket.add_packet();
        packet->set_flow_id("payload->flow_id");
        packet->set_t0(uint64_t(-1));
        packet->set_t1(uint64_t(-1));
        packet->set_packet_id(uint64_t(-1));
    }

    zmq::message_t message(flow_bucket.ByteSizeLong());

    rte_mbuf *rtembufArr[32] = {};

    void **obj_table = reinterpret_cast<void **>(rtembufArr);
    while (!m_Stop) {
        try {
            uint16_t numOfPackets = rte_ring_mc_dequeue_bulk(m_rx_ring, obj_table, 32, nullptr);
            if (unlikely(numOfPackets == 0)) {
                continue;
            }

            // find offset for udp and payload
            if (unlikely(not_initilized)) {
                for (int i = 0; i < numOfPackets && not_initilized; ++i) {

                    const uint8_t *mbuf = rte_pktmbuf_mtod(rtembufArr[i], const uint8_t*);
                    pcpp::RawPacket rawPacket(mbuf,
                                              rte_pktmbuf_pkt_len(rtembufArr[i]), timeval(), false,
                                              pcpp::LINKTYPE_ETHERNET);

                    pcpp::Packet parsedPacket(&rawPacket);
                    pcpp::UdpLayer *udpLayer = parsedPacket.getLayerOfType<pcpp::UdpLayer>();

                    if (udpLayer != NULL &&
                        udpLayer->getUdpHeader()->portSrc == htons(1234) &&
                        udpLayer->getUdpHeader()->portDst == htons(5678)) {
                        pcpp::PayloadLayer *payloadLayer = parsedPacket.getLayerOfType<pcpp::PayloadLayer>();

                        udp_offset = udpLayer->getData() - mbuf;
                        payload_offset = payloadLayer->getData() - mbuf;
                        not_initilized = false;

                        std::cout << (uint32_t) udp_offset << " " << (uint32_t) payload_offset << "\n" << std::flush;
                    }
                }
            }

            if (unlikely(not_initilized)) {
                continue;
            }


            for (int i = 0; i < numOfPackets; ++i) {

                flow_bucket.clear_packet();

                uint8_t *mbuf = rte_pktmbuf_mtod(rtembufArr[i], uint8_t*);
                const size_t len = rte_pktmbuf_pkt_len(rtembufArr[i]);
                pcpp::UdpLayer udpLayer(mbuf + udp_offset,
                                        len - udp_offset, NULL, &dummy_packet);
                if (likely(udpLayer.getUdpHeader()->portSrc == src_port &&
                           udpLayer.getUdpHeader()->portDst == dst_port)) {
                    packet_id++;
                    auto payload = reinterpret_cast<const org::openkilda::Payload *>(mbuf +
                                                                                     payload_offset);
                    org::openkilda::FlowLatencyPacket *packet = flow_bucket.add_packet();
                    packet->set_flow_id(payload->flow_id);
                    packet->set_t0(ntohl(payload->t0));
                    packet->set_t1(ntohl(payload->t1));
                    packet->set_packet_id(packet_id);
                    flow_bucket.SerializeToArray(message.data(), message.size());
                }
                rte_pktmbuf_free(rtembufArr[i]);
            }
            socket.send(message);

        } catch (zmq::error_t &exception) {
            std::cerr << "ZMQ Error " << exception.what() << "\n";
        } catch (std::exception &exception) {
            std::cerr << "Error " << exception.what() << "\n";
        }
    }

    return true;
}

void ProcessThread::stop() {
    m_Stop = true;
}

uint32_t ProcessThread::getCoreId() {
    return m_CoreId;
}
