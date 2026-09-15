package com.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;
import java.util.Set;

/**
 * Decoder de tokens de Azure AD (igual que el API Manager).
 * - Obtiene las claves públicas (JWKS) desde discovery/v2.0/keys.
 * - Valida la firma (RS256), el emisor (iss), la audiencia (aud),
 *   y las fechas de validez (exp/nbf). Cualquier fallo rechaza la
 *   petición como 401 ANTES de reenviarla al backend.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder azureAdJwtDecoder(
            @Value("${azure.tenant-id}") String tenantId,
            @Value("${app.exposed-client-id}") String clientId,
            @Value("${app.exposed-app-uri}") String appUri) {

        String issuer = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
        String jwkSetUri =
                "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> algorithms.retainAll(Set.of(SignatureAlgorithm.RS256)))
                .build();

decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<Object>("iss", iss -> iss != null
                        && (iss.equals(issuer)
                                || iss.equals("https://sts.windows.net/" + tenantId + "/"))),
                new JwtClaimValidator<List<?>>("aud",
                        aud -> aud != null
                                && (aud.contains(clientId) || aud.contains(appUri)))));

        return decoder;
    }
}