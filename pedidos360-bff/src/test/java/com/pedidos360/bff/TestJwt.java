package com.pedidos360.bff;

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
        return token(subject, roles, AUD);
    }

    public static String tokenAudienciaIncorrecta(String subject, List<String> roles) {
        return token(subject, roles, "otra-audiencia");
    }

    private static String token(String subject, List<String> roles, String audience) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISS)
                    .audience(List.of(audience))
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 5 * 60_000))
                    .claim("roles", roles)
                    .build();
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