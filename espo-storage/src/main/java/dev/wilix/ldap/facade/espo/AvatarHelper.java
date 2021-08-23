package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import dev.wilix.ldap.facade.api.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AvatarHelper {

    private final static Logger LOG = LoggerFactory.getLogger(AvatarHelper.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AvatarHelper(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    //"метод возвращает массив байт" sendCrmRequest(String url, Authentication authentication) {
    //    return sendCrmRequest(prepareCrmRequest(url, authentication));
    //}

    //private "метод возвращает массив байт" sendCrmRequest(HttpRequest request) {
    //  HttpResponse<String> response;
    //
    //}



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
