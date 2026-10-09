package com.jedco.jedcoengineeringspring.controllers;

import com.jedco.jedcoengineeringspring.config.PoleDataAuthorization;
import com.jedco.jedcoengineeringspring.mappers.PoleDataMapper;
import com.jedco.jedcoengineeringspring.models.*;
import com.jedco.jedcoengineeringspring.repositories.PoleDataRepository;
import com.jedco.jedcoengineeringspring.repositories.MeterDataRepository;
import com.jedco.jedcoengineeringspring.rest.request.LvDataRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.request.LvMeterDataRequest;
import com.jedco.jedcoengineeringspring.rest.response.LvDataResponse;
import com.jedco.jedcoengineeringspring.rest.response.LvMeterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PoleDataAuthorizationTests {
    private final PoleDataRepository poles = mock(PoleDataRepository.class);
    private final PoleDataMapper mapper = mock(PoleDataMapper.class);
    private final MeterDataRepository meters = mock(MeterDataRepository.class);
    private final PoleDataAuthorization authorization = new PoleDataAuthorization(poles, mapper, meters);
    private PoleData pole;
    private LvMeterResponse existing;

    @BeforeEach
    void setUp() {
        pole = new PoleData();
        pole.setId(1L);
        pole.setPoleNo("P1");
        pole.setRemark("Original");
        Status active = new Status();
        active.setId(1L);
        pole.setStatus(active);
        TxInfo tx = new TxInfo();
        tx.setId(2L);
        pole.setTransformer(tx);
        MeterData meter = new MeterData();
        meter.setId(3L);
        meter.setMeterNo("M1");
        meter.setStatus(active);
        pole.getMeterDataSet().add(meter);
        existing = meter(3L, "M1");
        when(mapper.toLvMeterResponse(meter)).thenReturn(existing);
        when(poles.findById(1L)).thenReturn(Optional.of(pole));
    }

    @Test
    void meterPermissionAllowsOnlyAddingToUnchangedData() {
        assertTrue(authorization.canUpdate(update("Original", List.of(existing, meter(null, "M2"))), auth("REGISTER_METER_DATA")));
        assertFalse(authorization.canUpdate(update("Changed", List.of(existing, meter(null, "M2"))), auth("REGISTER_METER_DATA")));
        assertFalse(authorization.canUpdate(update("Original", List.of(meter(3L, "Changed"))), auth("REGISTER_METER_DATA")));
        assertFalse(authorization.canUpdate(update("Original", List.of(meter(null, "M2"))), auth("REGISTER_METER_DATA")));
        verify(poles, never()).save(any());
    }

    @Test
    void updatePermissionAllowsEditsAndRemovalButCannotAdd() {
        assertTrue(authorization.canUpdate(update("Changed", List.of(existing)), auth("UPDATE_POLE_DATA")));
        assertTrue(authorization.canUpdate(update("Original", List.of(meter(3L, "Changed"))), auth("UPDATE_POLE_DATA")));
        assertTrue(authorization.canUpdate(update("Original", List.of()), auth("UPDATE_POLE_DATA")));
        assertFalse(authorization.canUpdate(update("Changed", List.of(existing, meter(null, "M2"))), auth("UPDATE_POLE_DATA")));
    }

    @Test
    void combinedPermissionsAllowCombinedOperations() {
        assertTrue(authorization.canUpdate(update("Changed", List.of(existing, meter(null, "M2"))),
                auth("UPDATE_POLE_DATA", "REGISTER_METER_DATA")));
    }

    @Test
    void viewAndPoleRegistrationDoNotAllowUpdates() {
        assertFalse(authorization.canUpdate(update("Original", List.of(existing)), auth("VIEW_POLE_DATA")));
        assertFalse(authorization.canUpdate(update("Original", List.of(existing)), auth("REGISTER_POLE_DATA")));
        verifyNoInteractions(poles);
    }

    @Test
    void cannotEditForeignMeterEvenWithBothPermissions() {
        assertFalse(authorization.canUpdate(update("Original", List.of(meter(99L, "M1"))),
                auth("UPDATE_POLE_DATA", "REGISTER_METER_DATA")));
    }

    @Test
    void malformedOrMissingDataIsDenied() {
        assertFalse(authorization.canUpdate(null, auth("REGISTER_METER_DATA")));
        assertFalse(authorization.canUpdate(update("Original", null), auth("REGISTER_METER_DATA")));
        assertFalse(authorization.canUpdate(update("Original", List.of(existing, existing)), auth("REGISTER_METER_DATA")));
        when(poles.findById(1L)).thenReturn(Optional.empty());
        assertFalse(authorization.canUpdate(update("Original", List.of()), auth("UPDATE_POLE_DATA")));
    }

    @Test
    void registeringPoleWithMetersRequiresBothGranularPermissions() {
        assertTrue(authorization.canRegister(registration(List.of()), auth("REGISTER_POLE_DATA")));
        LvMeterDataRequest meter = new LvMeterDataRequest(null, "M1", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        assertFalse(authorization.canRegister(registration(List.of(meter)), auth("REGISTER_POLE_DATA")));
        assertFalse(authorization.canRegister(registration(List.of()), auth("REGISTER_METER_DATA")));
        assertTrue(authorization.canRegister(registration(List.of(meter)), auth("REGISTER_POLE_DATA", "REGISTER_METER_DATA")));
    }

    @Test
    void meterOnlyGrantCannotDeactivateDuplicateDatabaseRecords() {
        MeterData duplicate = new MeterData();
        duplicate.setId(99L);
        when(meters.findAllByMeterNoAndStatusId("M1", 1L)).thenReturn(List.of(duplicate));
        assertFalse(authorization.canUpdate(update("Original", List.of(existing, meter(null, "M2"))),
                auth("REGISTER_METER_DATA")));
    }

    private LvDataResponse update(String remark, List<LvMeterResponse> meters) {
        return new LvDataResponse(1L, 2L, null, null, null, "P1", null, null, null,
                null, null, null, null, remark, null, null, null, null, meters);
    }

    private LvDataRegisterRequest registration(List<LvMeterDataRequest> meters) {
        return new LvDataRegisterRequest(2L, null, "P1", null, null, null, null, null,
                null, null, null, null, null, null, null, meters);
    }

    private LvMeterResponse meter(Long id, String number) {
        return new LvMeterResponse(id, null, number, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private Authentication auth(String... authorities) {
        var user = User.withUsername("operator").password("unused").authorities(authorities).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
