package com.jedco.jedcoengineeringspring.controllers;

import com.jedco.jedcoengineeringspring.services.LvDataService;
import com.jedco.jedcoengineeringspring.config.PoleDataAuthorization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class LvDataAuthorizationTests {
    private AnnotationConfigApplicationContext context;
    private LvDataController controller;
    private LvDataService service;

    record Operation(String authority, Consumer<LvDataController> invoke) {}

    static Stream<Operation> operations() {
        return Stream.of(
                new Operation("VIEW_POLE_DATA", c -> c.getDataByUser("2026/10/09")),
                new Operation("VIEW_POLE_DATA", c -> c.getDataByTx("F1", "T1")),
                new Operation("VIEW_POLE_DATA", c -> c.getDataByFeederTxPole("F1", "T1", "P1")),
                new Operation("VIEW_POLE_DATA", c -> c.getDataByPoleNo("P1")),
                new Operation("REGISTER_POLE_DATA", c -> c.registerLvData(null)),
                new Operation("UPDATE_POLE_DATA", c -> c.updateLvData(null))
        );
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        controller = context.getBean(LvDataController.class);
        service = context.getBean(LvDataService.class);
        PoleDataAuthorization authorization = context.getBean(PoleDataAuthorization.class);
        when(authorization.canRegister(any(), any())).thenAnswer(invocation ->
                hasAuthority(invocation.getArgument(1), "REGISTER_POLE_DATA"));
        when(authorization.canUpdate(any(), any())).thenAnswer(invocation ->
                hasAuthority(invocation.getArgument(1), "UPDATE_POLE_DATA"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @MethodSource("operations")
    void legacyAuthorityStillAllowsEveryOperation(Operation operation) {
        authenticate("REGISTER_LV_DATA");
        assertDoesNotThrow(() -> operation.invoke().accept(controller));
        assertFalse(mockingDetails(service).getInvocations().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("operations")
    void granularAuthorityAllowsItsOperation(Operation operation) {
        authenticate(operation.authority());
        assertDoesNotThrow(() -> operation.invoke().accept(controller));
        assertFalse(mockingDetails(service).getInvocations().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("operations")
    void unrelatedAuthoritiesAreDeniedBeforeServiceInvocation(Operation operation) {
        for (String authority : List.of("VIEW_POLE_DATA", "REGISTER_POLE_DATA", "UPDATE_POLE_DATA",
                "REGISTER_METER_DATA", "REGISTER_COMMISSIONING")) {
            if (!authority.equals(operation.authority())) {
                authenticate(authority);
                assertThrows(AccessDeniedException.class, () -> operation.invoke().accept(controller));
                verifyNoInteractions(service);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void userWithoutAuthoritiesIsDenied(Operation operation) {
        authenticate();
        assertThrows(AccessDeniedException.class, () -> operation.invoke().accept(controller));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("operations")
    void unauthenticatedCallIsDenied(Operation operation) {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> operation.invoke().accept(controller));
        verifyNoInteractions(service);
    }

    @Test
    void meterOnlyAuthorityCanUpdateWhenOperationGuardAllowsRequest() {
        authenticate("REGISTER_METER_DATA");
        doReturn(true).when(context.getBean(PoleDataAuthorization.class)).canUpdate(any(), any());
        assertDoesNotThrow(() -> controller.updateLvData(null));
        verify(service).updateLvData(null, "operator");
    }

    @Test
    void operationGuardDeniesBeforeServiceInvocation() {
        authenticate("UPDATE_POLE_DATA");
        doReturn(false).when(context.getBean(PoleDataAuthorization.class)).canUpdate(any(), any());
        assertThrows(AccessDeniedException.class, () -> controller.updateLvData(null));
        verifyNoInteractions(service);
    }

    private boolean hasAuthority(org.springframework.security.core.Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private void authenticate(String... authorities) {
        var user = User.withUsername("operator").password("unused").authorities(authorities).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfiguration {
        @Bean
        PoleDataAuthorization poleDataAuthorization() {
            return mock(PoleDataAuthorization.class);
        }

        @Bean
        LvDataService lvDataService() {
            return mock(LvDataService.class);
        }

        @Bean
        LvDataController lvDataController(LvDataService service) {
            return new LvDataController(service);
        }
    }
}
