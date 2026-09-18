package org.hcz.service.audit;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.AcsRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.green.model.v20180509.TextScanRequest;
import com.aliyuncs.green.model.v20180509.VideoAsyncScanRequest;
import com.aliyuncs.green.model.v20180509.VideoAsyncScanResultsRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hcz.config.AliyunContentSecurityProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


// 封装阿里云内容安全 SDK，统一返回原始 JSON 供审核业务转换。

@Service
public class AliyunContentSecurityClient {

    private final AliyunContentSecurityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile IAcsClient client;

    public AliyunContentSecurityClient(AliyunContentSecurityProperties properties) {
        this.properties = properties;
    }

    public JsonNode scanText(String text) {
        final ObjectNode body = objectMapper.createObjectNode();
        body.putArray("scenes").add("antispam");
        final ArrayNode tasks = body.putArray("tasks");
        final ObjectNode task = tasks.addObject();
        task.put("dataId", UUID.randomUUID().toString());
        task.put("content", text);
        return execute(new TextScanRequest(), body);
    }

    public JsonNode scanImage(String url) {
        final ObjectNode body = objectMapper.createObjectNode();
        body.putArray("scenes").add("porn").add("terrorism").add("ad");
        final ArrayNode tasks = body.putArray("tasks");
        final ObjectNode task = tasks.addObject();
        task.put("dataId", UUID.randomUUID().toString());
        task.put("url", url);
        return execute(new ImageSyncScanRequest(), body);
    }

    public JsonNode submitVideo(String url) {
        final ObjectNode body = objectMapper.createObjectNode();
        body.putArray("scenes").add("porn").add("terrorism").add("ad");
        final ArrayNode tasks = body.putArray("tasks");
        final ObjectNode task = tasks.addObject();
        task.put("dataId", UUID.randomUUID().toString());
        task.put("url", url);
        return execute(new VideoAsyncScanRequest(), body);
    }

    public JsonNode queryVideo(String taskId) {
        final ObjectNode body = objectMapper.createObjectNode();
        body.putArray("taskId").add(taskId);
        return execute(new VideoAsyncScanResultsRequest(), body);
    }

    private JsonNode execute(AcsRequest<?> request, ObjectNode body) {
        try {
            request.setHttpContent(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8.name(), FormatType.JSON);
            final HttpResponse response = getClient().doAction(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException("阿里云内容安全请求失败，HTTP 状态码：" + response.getStatus());
            }
            return objectMapper.readTree(response.getHttpContentString());
        } catch (ClientException e) {
            throw new IllegalStateException("阿里云内容安全请求失败", e);
        } catch (Exception e) {
            throw new IllegalStateException("阿里云内容安全响应解析失败", e);
        }
    }

    private IAcsClient getClient() throws ClientException {
        IAcsClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    final DefaultProfile profile = DefaultProfile.getProfile(
                            properties.getRegion(), properties.getAccessKeyId(), properties.getAccessKeySecret());
                    current = new DefaultAcsClient(profile);
                    client = current;
                }
            }
        }
        return current;
    }
}
