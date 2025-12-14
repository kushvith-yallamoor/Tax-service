package com.blockstats.tax.gcp.auth;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class GCPIdTokenProvider {
    private final CredentialsProvider credentialsProvider;
    private final Map<String, IdToken> idTokenMap;

    public GCPIdTokenProvider(CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        this.idTokenMap = new HashMap<>();
    }

    public String getIdToken(String audience) {
        if (idTokenMap.containsKey(audience)
                && Objects.nonNull(idTokenMap.get(audience))
                && (idTokenMap.get(audience).getExpirationTime().getTime() - Instant.now().toEpochMilli()) > 600000) {
            return idTokenMap.get(audience).getTokenValue();
        } else {
            String idTokenString = null;
            log.info("Generating new IdToken for audience: {}", audience);
            try {
                IdTokenProvider idTokenProvider = (IdTokenProvider) credentialsProvider.getCredentials();
                IdToken idToken = idTokenProvider.idTokenWithAudience(audience, new ArrayList<>());
                idTokenString = idToken.getTokenValue();
                idTokenMap.put(audience, idToken);
            } catch (IOException e) {
                log.error("Error while Creating IdToken", e);
            }
            return idTokenString;
        }
    }
}
