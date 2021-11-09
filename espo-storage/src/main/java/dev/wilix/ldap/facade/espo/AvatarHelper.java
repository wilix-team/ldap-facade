package dev.wilix.ldap.facade.espo;

import dev.wilix.ldap.facade.api.Authentication;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Base64;

public class AvatarHelper {

    private final static Logger LOG = LoggerFactory.getLogger(AvatarHelper.class);

    private final String searchAvatarBaseUri;
    private final RequestHelper requestHelper;

    public AvatarHelper(String baseUrl, RequestHelper requestHelper) {
        this.requestHelper = requestHelper;

        try {
            searchAvatarBaseUri = new URIBuilder(baseUrl).addParameter("entryPoint", "avatar" ).addParameter("size", "small").build().toString();
        } catch (URISyntaxException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    public String getAvatarByUserId(String id, Authentication authentication) {
        String avatarUri;
        try {
            avatarUri = new URIBuilder(searchAvatarBaseUri).addParameter("id", id).build().toString();
        } catch (URISyntaxException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
        // FIXME Will probably have to check the type of the returned image and convert it to jpeg format.
        return convertByteArrayToString(requestHelper.sendCrmRequestForBytes(avatarUri, authentication));
    }

    public static String convertByteArrayToString(byte[] input) {
        if (input == null) {
            LOG.debug("Array of image is null.");
            throw new IllegalArgumentException("Array of image is null.");
        }
        return Base64.getEncoder().encodeToString(input);
    }

}
