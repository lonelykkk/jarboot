package com.mz.jarboot.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.mz.jarboot.common.JarbootException;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Http工具类
 * @author majianzheng
 */
public class HttpUtils {
    /** 连接超时 */
    private static final int CONNECT_TIMEOUT = 30000;

    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final int SUCCESS_STATUS = 200;
    private static final CloseableHttpClient HTTP_CLIENT;

    public static JsonNode get(String url, Map<String, String> header) {
        HttpGet httpGet = new HttpGet(url);
        return doRequest(httpGet, CONTENT_TYPE_FORM, header);
    }

    /**
     * Get请求
     * @param url api接口
     * @param type 期望的结果类型
     * @param <T> 范型类
     * @return 期望的结构
     */
    public static <T> T getObj(String url, Class<T> type) {
        return JsonUtils.treeToValue(get(url, null), type);
    }

    public static JsonNode postJson(String url, Object json, Map<String, String> header) {
        String content = null == json ? StringUtils.EMPTY : JsonUtils.toJsonString(json);
        return doPost(url, new StringEntity(content, StandardCharsets.UTF_8), CONTENT_TYPE_JSON, header);
    }

    /**
     * post请求
     * @param url api接口
     * @param data 请求参数
     * @param type 范型类
     * @return 期望的结果类型
     * @param <T>
     */
    public static <T> T  postObjByString(String url, String data, Class<T> type) {
        return JsonUtils.treeToValue(doPost(url, new StringEntity(data, StandardCharsets.UTF_8), CONTENT_TYPE_JSON, null), type);
    }

    /**
     * Post请求
     * @param url api接口
     * @param object 传入的参数
     * @param type 期望的结果类型
     * @param <T> 范型类
     * @return 期望的结构
     */
    public static <T> T postObj(String url, Object object, Class<T> type) {
        return JsonUtils.treeToValue(postJson(url, object, null), type);
    }

    public static JsonNode post(String url, Map<String, String> formData, Map<String, String> header) {
        HttpEntity request;
        if (null == formData || formData.isEmpty()) {
            request = new StringEntity(StringUtils.EMPTY, StandardCharsets.UTF_8);
        } else {
            List<NameValuePair> nameValuePairList = formData
                    .entrySet()
                    .stream()
                    .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            try {
                request = new UrlEncodedFormEntity(nameValuePairList, "utf-8");
            } catch (Exception e) {
                request = new StringEntity(StringUtils.EMPTY, StandardCharsets.UTF_8);
            }
        }
        return doPost(url, request, CONTENT_TYPE_FORM, header);
    }

    public static JsonNode delete(String url, Map<String, String> header) {
        HttpDelete httpGet = new HttpDelete(url);
        return doRequest(httpGet, CONTENT_TYPE_FORM, header);
    }

    public static JsonNode doPost(String url, HttpEntity request, String contentType, Map<String, String> header) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(request);
        return doRequest(httpPost, contentType, header);
    }

    private static JsonNode doRequest(HttpRequestBase httpUriRequest, String contentType, Map<String, String> header) {
        fillHeader(httpUriRequest, header);
        httpUriRequest.setHeader(CONTENT_TYPE, contentType);
        try {
            CloseableHttpResponse response = HTTP_CLIENT.execute(httpUriRequest);
            checkStatus(response);
            return JsonUtils.readAsJsonNode(response.getEntity().getContent());
        } catch (IOException e) {
            throw new JarbootException(e);
        } finally {
            httpUriRequest.releaseConnection();
        }
    }

    private static void fillHeader(HttpRequestBase httpMessage, Map<String, String> header) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setSocketTimeout(CONNECT_TIMEOUT)
                .build();
        httpMessage.setConfig(requestConfig);
        if (null != header && !header.isEmpty()) {
            header.forEach(httpMessage::addHeader);
        }
    }
    private static void checkStatus(CloseableHttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (SUCCESS_STATUS != statusCode) {
            throw new JarbootException(statusCode, "请求失败，status code:" + statusCode);
        }
    }
    static  {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContextBuilder
                    .create()
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();
        } catch (Exception e) {
            throw new JarbootException(e);
        }

        // we can optionally disable hostname verification.
        // if you don't want to further weaken the security, you don't have to include this.
        HostnameVerifier allowAllHosts = new NoopHostnameVerifier();

        // create an SSL Socket Factory to use the SSLContext with the trust self signed certificate strategy
        // and allow all hosts verifier.
        SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts);

        HTTP_CLIENT = HttpClients.custom().setSSLSocketFactory(connectionFactory).build();
    }

    private HttpUtils() {}
}
