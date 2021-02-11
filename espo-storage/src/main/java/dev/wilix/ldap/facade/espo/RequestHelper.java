package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import dev.wilix.ldap.facade.api.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class RequestHelper {

    private final static Logger LOG = LoggerFactory.getLogger(RequestHelper.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RequestHelper(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    JsonNode sendCrmRequest(String url, Authentication authentication) {
        return sendCrmRequest(prepareCrmRequest(url, authentication));
    }

    private JsonNode sendCrmRequest(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Get errors when trying to communicate with CRM!", ex);
        }

        LOG.debug("Receive response from CRM: {}", response.body());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            LOG.warn("For request {} received UNAUTHORIZED response", request);
            throw new IllegalStateException("Incorrect credentials or access rights!");
        }

        if (response.statusCode() != 200) {
            LOG.warn("For request {} receive bad response from CRM {}", request, response);
            throw new IllegalStateException("Get bad request from CRM!");
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", request, response.body());
            throw new IllegalStateException("Can't properly parse CRM response.");
        }
    }

    static HttpRequest prepareCrmRequest(String url, Authentication authentication) {
        if (authentication instanceof ServiceAuthentication) {
            return prepareRequestWithServiceCreds(url, (ServiceAuthentication) authentication);
        } else if (authentication instanceof UserAuthentication) {
            return prepareRequestWithUserCreds(url, (UserAuthentication) authentication);
        }

        throw new IllegalStateException("Not expected authentication to prepare CRM request" + authentication);
    }

    static HttpRequest prepareRequestWithUserCreds(String url, UserAuthentication authentication) {
        HttpRequest.Builder request = prepareBasicCrmRequest(url);

        String password = authentication.getPassword();
        String userName = authentication.getUserName();
        String base64Credentials = Base64.getEncoder()
                .encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials);

        return request.build();
    }

    static HttpRequest prepareRequestWithServiceCreds(String url, ServiceAuthentication authentication) {
        HttpRequest.Builder request = prepareBasicCrmRequest(url);

        String token = authentication.getToken();
        request.setHeader("X-Api-Key", token);

        return request.build();
    }

    private static HttpRequest.Builder prepareBasicCrmRequest(String requestUri) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(requestUri))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8");
    }

}
