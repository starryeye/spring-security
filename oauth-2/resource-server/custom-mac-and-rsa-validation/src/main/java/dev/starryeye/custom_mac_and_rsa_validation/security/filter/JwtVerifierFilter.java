package dev.starryeye.custom_mac_and_rsa_validation.security.filter;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;

public class JwtVerifierFilter extends OncePerRequestFilter {

    private static final String BEARER_TYPE = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_TYPE)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_TYPE.length()).trim();

        // todo, Go to JWTVerifier and AuthenticationProvider
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            MACVerifier macVerifier = new MACVerifier(jwk.toSecretKey());

            if (signedJWT.verify(macVerifier)) {

            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

    }
}
