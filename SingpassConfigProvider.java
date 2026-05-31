package com.example.demo.config;

import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class SingpassConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(SingpassConfigProvider.class);

    @Value("${singpass.issuer-url}")
    private String issuerUrl;

    // 'volatile' ensures atomic visibility across threads when the background scheduler refreshes them
    private volatile URI cachedParEndpoint;
    private volatile URI cachedTokenEndpoint;
    private volatile URI cachedUserinfoEndpoint;

    /**
     * POINT 1: Call the URL once when your Spring Boot application initializes.
     */
    @PostConstruct
    public void init() {
        log.info("Executing initial boot-time Singpass discovery...");
        fetchAndCacheMetadata();
    }

    /**
     * Automatically executes every 1 hour (3600000 milliseconds) after the application starts.
     * fixedRate: Time measured from the start of the last execution.
     */
    @Scheduled(fixedRate = 3600000) 
    public void refreshCacheEveryHour() {
        // Skip the very first execution since @PostConstruct already handles it at second 0
        if (this.cachedParEndpoint == null) {
            return; 
        }
        
        log.info("Scheduled trigger: Refreshing cached Singpass endpoints...");
        try {
            fetchAndCacheMetadata();
        } catch (Exception e) {
            // CRITICAL: We catch the error so that if Singpass is briefly down, 
            // your application will simply KEEP using the old, valid cached endpoints.
            log.error("Failed to refresh Singpass metadata. Retaining previous cached values.", e);
        }
    }

    /**
     * Core logic to point to the issuer URL, execute the network request, and parse the JSON.
     */
    private void fetchAndCacheMetadata() {
        try {
            Issuer issuer = new Issuer(issuerUrl);
// 2. Added timeouts to prevent thread hanging if Singpass network lags
ResourceRetriever customRetriever = new DefaultResourceRetriever(5000, 5000);
            // Read the JSON response and extract the endpoints
            OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(issuer, customRetriever);

            // Atomically update our cache variables
            this.cachedParEndpoint = metadata.getPushedAuthorizationRequestEndpointURI();
            this.cachedTokenEndpoint = metadata.getTokenEndpointURI();
            this.cachedUserinfoEndpoint = metadata.getUserInfoEndpointURI();

            log.info("Singpass configuration refreshed successfully.");
            
        } catch (Exception e) {
            // Rethrow so the caller knows the operation failed
            throw new IllegalStateException("Failed to reach identity provider configuration", e);
        }
    }

    // --- Thread-safe Getters to serve active user sessions instantly from cache ---

    public URI getParEndpoint() {
        return this.cachedParEndpoint;
    }

    public URI getTokenEndpoint() {
        return this.cachedTokenEndpoint;
    }

    public URI getUserinfoEndpoint() {
        return this.cachedUserinfoEndpoint;
    }
}
