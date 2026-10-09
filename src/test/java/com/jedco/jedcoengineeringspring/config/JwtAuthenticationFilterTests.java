package com.jedco.jedcoengineeringspring.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jedco.jedcoengineeringspring.services.JwtServiceImpl;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTests {
    private final Key signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final JwtServiceImpl jwtService = new JwtServiceImpl();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final UserDetails user = User.withUsername("operator-canary").password("password-canary")
            .authorities("VIEW_POLE_DATA").build();
    private final Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level originalLevel;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(jwtService, "jwtSigningKey", Encoders.BASE64.encode(signingKey.getEncoded()));
        ReflectionTestUtils.setField(jwtService, "tokenExpirationYears", 3);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpirationDays", 7);
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        filter = new JwtAuthenticationFilter(jwtService, users);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        logger.detachAppender(logs);
        logger.setLevel(originalLevel);
        logs.stop();
    }

    @Test
    void expiredTokenIsRejectedAndClearsExistingAuthentication() throws Exception {
        reject(token(signingKey, -60_000), true);
        verifyNoInteractions(users);
    }

    @Test
    void malformedTokenIsRejectedAndClearsExistingAuthentication() throws Exception {
        reject("malformed-credential-canary", false);
        verifyNoInteractions(users);
    }

    @Test
    void invalidSignatureIsRejectedAndClearsExistingAuthentication() throws Exception {
        reject(token(Keys.secretKeyFor(SignatureAlgorithm.HS256), 60_000), false);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void emptyBearerTokenIsRejected(String token) throws Exception {
        reject(token, false);
        verifyNoInteractions(users);
    }

    @Test
    void bearerSchemeWithoutTokenIsRejected() throws Exception {
        setExistingAuthentication();
        request.addHeader("Authorization", "Bearer");
        filter.doFilter(request, response, chain);
        assertRejection("", false);
        verifyNoInteractions(users);
    }

    @Test
    void expirationDuringSubsequentValidationIsRejected() throws Exception {
        String token = token(signingKey, 60_000);
        JwtServiceImpl advancingClockService = spy(jwtService);
        // Parse the same token again with a future clock: deterministic expiration,
        // without sleeping or replacing the real parser with a thrown mock exception.
        doAnswer(invocation -> {
            Jwts.parserBuilder().setSigningKey(signingKey)
                    .setClock(() -> new Date(System.currentTimeMillis() + 120_000))
                    .build().parseClaimsJws(invocation.getArgument(0, String.class));
            return jwtService.isTokenValid(token, user);
        }).when(advancingClockService).isTokenValid(token, user);
        filter = new JwtAuthenticationFilter(advancingClockService, users);
        when(users.loadUserByUsername(user.getUsername())).thenAnswer(invocation -> {
            setExistingAuthentication();
            return user;
        });
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
        assertRejection(token, true);
        verify(users).loadUserByUsername(user.getUsername());
        verify(advancingClockService).isTokenValid(token, user);
    }

    @Test
    void failedValidationIsRejectedInsteadOfContinuingAnonymously() throws Exception {
        String token = token(signingKey, 60_000);
        UserDetails differentUser = User.withUsername("different-user").password("unused").authorities("OTHER").build();
        when(users.loadUserByUsername(user.getUsername())).thenReturn(differentUser);
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
        assertRejection(token, false);
    }

    @Test
    void validGeneratedTokenAuthenticatesWithExistingAuthorities() throws Exception {
        com.jedco.jedcoengineeringspring.models.User entity = new com.jedco.jedcoengineeringspring.models.User();
        entity.setUsername(user.getUsername());
        String token = jwtService.generateToken(entity);
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(user, authentication.getPrincipal());
        assertEquals(new HashSet<>(user.getAuthorities()), new HashSet<>(authentication.getAuthorities()));
        assertNull(authentication.getCredentials());
        assertNotNull(authentication.getDetails());
        verify(users).loadUserByUsername(user.getUsername());
        verify(chain).doFilter(request, response);
        assertTrue(logs.list.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic credential-canary", "Token credential-canary"})
    void requestsWithoutBearerHeaderContinueUnchanged(String header) throws Exception {
        if (header != null) request.addHeader("Authorization", header);
        setExistingAuthentication();
        var existing = SecurityContextHolder.getContext().getAuthentication();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        assertEquals("", response.getContentAsString());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void requestWithoutHeaderCanContinueAnonymously() throws Exception {
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void downstreamJwtExceptionIsNotCaughtAsAuthenticationFailure() throws Exception {
        String token = token(signingKey, 60_000);
        request.addHeader("Authorization", "Bearer " + token);
        JwtException controllerFailure = new JwtException("Downstream failure");
        doThrow(controllerFailure).when(chain).doFilter(request, response);
        assertSame(controllerFailure, assertThrows(JwtException.class, () -> filter.doFilter(request, response, chain)));
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void validTokenPreservesExistingAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(signingKey, 60_000));
        setExistingAuthentication();
        var existing = SecurityContextHolder.getContext().getAuthentication();
        filter.doFilter(request, response, chain);
        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void userLookupFailureIsNotReportedAsInvalidJwt() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(signingKey, 60_000));
        IllegalStateException lookupFailure = new IllegalStateException("User store unavailable");
        when(users.loadUserByUsername(anyString())).thenThrow(lookupFailure);
        assertSame(lookupFailure, assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain)));
        assertEquals(200, response.getStatus());
        verifyNoInteractions(chain);
        assertTrue(logs.list.isEmpty());
    }

    private String token(Key key, long remainingMillis) {
        return Jwts.builder().setSubject(user.getUsername())
                .setExpiration(new Date(System.currentTimeMillis() + remainingMillis))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private void reject(String token, boolean expired) throws Exception {
        setExistingAuthentication();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
        assertRejection(token, expired);
    }

    private void setExistingAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "credential-canary", user.getAuthorities()));
    }

    private void assertRejection(String token, boolean expired) throws Exception {
        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        JsonNode json = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(3, json.size());
        assertEquals(401, json.get("status").asInt());
        assertEquals("Unauthorized", json.get("title").asText());
        assertEquals(expired ? "Authentication token has expired." : "Invalid authentication token.",
                json.get("message").asText());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.getFirst();
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("Bearer JWT rejected: " + (expired ? "expired" : "invalid") + ".", event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
        assertArrayEquals(new Object[]{expired ? "expired" : "invalid"}, event.getArgumentArray());
        assertFalse(event.getFormattedMessage().contains(user.getUsername()));
        assertFalse(event.getFormattedMessage().contains(user.getPassword()));
        assertFalse(event.getFormattedMessage().contains("credential-canary"));
        if (!token.isBlank()) assertFalse(event.getFormattedMessage().contains(token));
    }
}
