package org.hcz;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证本地 RocketMQ NameServer 是否可达。
 * 无需 Spring 上下文，运行速度快，适合日常开发前快速检查。
 */
class RocketMqConnectivityTest {

    private static final String NAMESERVER_ADDR = System.getenv()
            .getOrDefault("ROCKETMQ_NAME_SERVER", "127.0.0.1:9876");
    private static final String NAMESERVER_HOST = NAMESERVER_ADDR.split(":")[0];
    private static final int NAMESERVER_PORT = Integer.parseInt(NAMESERVER_ADDR.split(":")[1]);

    @Test
    @DisplayName("RocketMQ NameServer TCP 端口可达")
    void nameServerPortIsOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(NAMESERVER_HOST, NAMESERVER_PORT), 3000);
            assertTrue(socket.isConnected(),
                    "应能连接到 RocketMQ NameServer " + NAMESERVER_HOST + ":" + NAMESERVER_PORT);
        } catch (IOException e) {
            fail("""
                    ❌ RocketMQ NameServer 不可达 (%s:%d)
                    请确认已启动 RocketMQ：
                      docker run -d --name rmqnamesrv -p 9876:9876 apache/rocketmq:5.3.0 sh mqnamesrv
                      docker run -d --name rmqbroker -p 10909:10909 -p 10911:10911 \
                        -e "NAMESRV_ADDR=host.docker.internal:9876" apache/rocketmq:5.3.0 sh mqbroker
                    原因: %s
                    """.formatted(NAMESERVER_HOST, NAMESERVER_PORT, e.getMessage()));
        }
    }

    @Test
    @DisplayName("RocketMQ Producer 能成功注册并发送消息")
    void producerCanSendTestMessage() throws Exception {
        String nameServer = NAMESERVER_HOST + ":" + NAMESERVER_PORT;
        DefaultMQProducer producer = new DefaultMQProducer("connectivity-test-group");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);

        try {
            producer.start();
            Message msg = new Message(
                    "luckyjourney-connectivity-check",
                    "TAG_CONNECTIVITY",
                    "ping".getBytes());
            SendResult result = producer.send(msg);
            assertNotNull(result.getMsgId(), "消息应返回有效 msgId");
            System.out.println("✅ RocketMQ 发送成功! msgId=" + result.getMsgId()
                    + ", status=" + result.getSendStatus());
        } catch (Exception e) {
            fail("""
                    ❌ RocketMQ Producer 发送失败
                    可能原因：
                      1. Broker 未启动或不可达
                      2. Topic 'luckyjourney-connectivity-check' 未创建（开启自动创建可解决）
                      3. NameServer 地址不正确
                    错误: %s
                    """.formatted(e.getMessage()));
        } finally {
            producer.shutdown();
        }
    }
}
