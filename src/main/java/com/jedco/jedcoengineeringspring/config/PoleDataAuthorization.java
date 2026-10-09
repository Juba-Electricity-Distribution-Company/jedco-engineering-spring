package com.jedco.jedcoengineeringspring.config;

import com.jedco.jedcoengineeringspring.mappers.PoleDataMapper;
import com.jedco.jedcoengineeringspring.models.MeterData;
import com.jedco.jedcoengineeringspring.models.PoleData;
import com.jedco.jedcoengineeringspring.repositories.PoleDataRepository;
import com.jedco.jedcoengineeringspring.repositories.MeterDataRepository;
import com.jedco.jedcoengineeringspring.rest.request.LvDataRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.response.LvDataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Component("poleDataAuthorization")
@RequiredArgsConstructor
public class PoleDataAuthorization {
    private final PoleDataRepository poleDataRepository;
    private final PoleDataMapper poleDataMapper;
    private final MeterDataRepository meterDataRepository;

    public boolean canRegister(LvDataRegisterRequest request, Authentication authentication) {
        return request != null && hasAuthority(authentication, "REGISTER_POLE_DATA")
                && request.meterDataDtoList() != null
                && (request.meterDataDtoList().isEmpty() || hasAuthority(authentication, "REGISTER_METER_DATA"));
    }

    @Transactional(readOnly = true)
    public boolean canUpdate(LvDataResponse request, Authentication authentication) {
        boolean canEdit = hasAuthority(authentication, "UPDATE_POLE_DATA");
        boolean canAdd = hasAuthority(authentication, "REGISTER_METER_DATA");
        if ((!canEdit && !canAdd) || request == null || request.id() == null || request.meterDataDtoList() == null) {
            return false;
        }
        PoleData pole = poleDataRepository.findById(request.id()).orElse(null);
        if (pole == null || (!canEdit && !samePole(pole, request))) {
            return false;
        }
        Set<Long> meterIds = new HashSet<>();
        Set<String> meterNos = new HashSet<>();
        for (var meter : request.meterDataDtoList()) {
            if (meter == null || !meterNos.add(meter.meterNo())) {
                return false;
            }
            if (meter.id() == null) {
                if (!canAdd) return false;
            } else {
                if (!meterIds.add(meter.id())) return false;
                MeterData existing = pole.getMeterDataSet().stream()
                        .filter(data -> Objects.equals(data.getId(), meter.id())).findFirst().orElse(null);
                // A granular permission never authorizes editing a meter on another pole.
                if (existing == null) return false;
                // updateLvData deactivates other active records with the same number.
                // A meter-only grant must not authorize that cleanup as a side effect.
                if (!canEdit && meterDataRepository.findAllByMeterNoAndStatusId(meter.meterNo(), 1L).stream()
                        .anyMatch(data -> !Objects.equals(data.getId(), meter.id()))) {
                    return false;
                }
                if (!canEdit && (!Objects.equals(existing.getStatus().getId(), 1L)
                        || !Objects.equals(poleDataMapper.toLvMeterResponse(existing), meter))) {
                    return false;
                }
            }
        }
        if (!canEdit) {
            for (MeterData meter : pole.getMeterDataSet()) {
                if (!Objects.equals(meter.getStatus().getId(), 3L) && !meterNos.contains(meter.getMeterNo())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean samePole(PoleData pole, LvDataResponse request) {
        return Objects.equals(pole.getTransformer().getId(), request.txId())
                && Objects.equals(pole.getAssemblyType(), request.assemblyType())
                && Objects.equals(pole.getBranchCode(), request.branchCode())
                && Objects.equals(pole.getConductorType(), request.conductorType())
                && Objects.equals(pole.getPoleType(), request.poleType())
                && Objects.equals(pole.getPoleFeature(), request.poleFeature())
                && Objects.equals(pole.getPoleNo(), request.poleNo())
                && Objects.equals(pole.getLocationAccuracy(), request.locationAccuracy())
                && Objects.equals(pole.getRemark(), request.remark())
                && Objects.equals(pole.getNorthing(), request.northing())
                && Objects.equals(pole.getEasting(), request.easting())
                && Objects.equals(pole.getPole_anomaly(), request.pole_anomaly())
                && Objects.equals(pole.getPoleRegType(), request.poleRegistrationType())
                && Objects.equals(pole.getStatus().getId(), 1L);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
