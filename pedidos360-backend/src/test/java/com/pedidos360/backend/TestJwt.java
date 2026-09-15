package com.pedidos360.backend;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fábrica de tokens de prueba (RS256) para ejercitar el flujo de seguridad
 * sin depender de Azure AD. Emite JWTs como los de una app registration.
 */
public final class TestJwt {

    public static final String ISS = "https://login.microsoftonline.com/test-tenant/v2.0";
    public static final String AUD = "test-client";

    private static final KeyPair KEYPAIR = generateKeyPair();

    private TestJwt() {
    }

    public static PublicKey publicKey() {
        return KEYPAIR.getPublic();
    }

    public static String token(String subject, List<String> roles) {
        return token(subject, roles, AUD, 5, TimeUnit.MINUTES, false);
    }

    public static String tokenExpirado(String subject) {
        return token(subject, List.of("PEDIDOS_CLIENTE"), AUD, -1, TimeUnit.MINUTES, false);
    }

    public static String tokenAudienciaIncorrecta(String subject, List<String> roles) {
        return token(subject, roles, "otra-audiencia", 5, TimeUnit.MINUTES, false);
    }

    public static String tokenSinRoles(String subject) {
        return token(subject, List.of(), AUD, 5, TimeUnit.MINUTES, true);
    }

    private static String token(String subject, List<String> roles, String audience,
            long ttlValue, TimeUnit ttlUnit, boolean omitRolesClaim) {
        try {
            long exp = System.currentTimeMillis() + ttlUnit.toMillis(ttlValue);
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISS)
                    .audience(List.of(audience))
                    .issueTime(new Date())
                    .expirationTime(new Date(exp))
                    .claim("preferred_username", subject);
            if (!omitRolesClaim) {
                builder.claim("roles", roles);
            }

            JWTClaimsSet claims = builder.build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .build();
            SignedJWT signed = new SignedJWT(header, claims);
            signed.sign(new RSASSASigner(KEYPAIR.getPrivate()));
            return signed.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA no disponible", e);
        }
    }
}