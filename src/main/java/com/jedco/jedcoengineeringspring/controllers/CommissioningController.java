package com.jedco.jedcoengineeringspring.controllers;

import com.jedco.jedcoengineeringspring.rest.request.CommissioningRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.request.LvExtensionRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.request.MeterRelocationRequest;
import com.jedco.jedcoengineeringspring.rest.request.TxInsertRequest;
import com.jedco.jedcoengineeringspring.rest.response.BoxNumberResponse;
import com.jedco.jedcoengineeringspring.rest.response.DispatchedMeterResponse;
import com.jedco.jedcoengineeringspring.rest.response.RefResponse;
import com.jedco.jedcoengineeringspring.rest.response.ResponseDto;
import com.jedco.jedcoengineeringspring.services.CommissioningService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commissioning")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "LvNetworkData")
public class CommissioningController {
    private final CommissioningService lvCommissioningService;

    @GetMapping("/checkRef")
    public RefResponse checkMeter(@RequestParam("meterNo") String meterNo) {
        return this.lvCommissioningService.checkRef(meterNo);
    }

    @GetMapping("/checkDispatchedMeter")
    public DispatchedMeterResponse checkDispatchedMeter(@RequestParam("meterNo") String meterNo) {
        return this.lvCommissioningService.checkDispatchedMeter(meterNo);
    }

    @GetMapping("/checkDispatchedMeterForMeterChange")
    public DispatchedMeterResponse checkDispatchedMeterForMeterChange(@RequestParam("meterNo") String meterNo) {
        return this.lvCommissioningService.checkDispatchedMeterForMeterChange(meterNo);
    }

    @PostMapping("/registerCommissioning")
    @PreAuthorize("hasAnyAuthority('REGISTER_COMMISSIONING')")
    public ResponseDto registerCommissioning(@RequestBody CommissioningRegisterRequest registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.insertCommissioningData(registerDto, userDetails.getUsername());
    }

    @PostMapping("/registerNewTx")
    @PreAuthorize("hasAnyAuthority('REGISTER_NEW_TX')")
    public ResponseDto registerNewTx(@RequestBody TxInsertRequest registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.registerNewTx(registerDto, userDetails.getUsername());
    }

    @PostMapping("/updateTx")
    @PreAuthorize("hasAnyAuthority('REGISTER_NEW_TX')")
    public ResponseDto updateTx(@RequestBody TxInsertRequest registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.updateTx(registerDto, userDetails.getUsername());
    }

    @PostMapping("/registerMeterChange")
    @PreAuthorize("hasAnyAuthority('REGISTER_METER_CHANGE')")
    public ResponseDto registerMeterChange(@RequestParam("oldMeter") String oldMeter, @RequestParam("newMeter") String newMeter) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.registerMeterChange(oldMeter, newMeter, userDetails.getUsername());
    }

    @PostMapping("/meterRelocation")
    @PreAuthorize("hasAnyAuthority('RELOCATE_METER')")
    public ResponseDto meterRelocation(@RequestBody MeterRelocationRequest relocationDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.registerMeterRelocation(relocationDto, userDetails.getUsername());
    }

    @PostMapping("/registerLvExtension")
    @PreAuthorize("hasAnyAuthority('REGISTER_LV_EXTENSION')")
    public ResponseDto registerLvExtension(@RequestBody LvExtensionRegisterRequest registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvCommissioningService.registerLvExtension(registerDto, userDetails.getUsername());
    }

    @GetMapping("/getBoxNumbers/{poleId}")
    @PreAuthorize("hasAnyAuthority('REGISTER_COMMISSIONING','VIEW_POLE_DATA')")
    public List<BoxNumberResponse> getBoxNumbers(@PathVariable("poleId") Long poleId) {
        return this.lvCommissioningService.getBoxNumbers(poleId);
    }

    @GetMapping("/getPoleBoxNumbers/{poleId}")
    public List<BoxNumberResponse> getPoleBoxNumbers(@PathVariable("poleId") Long poleId) {
        return this.lvCommissioningService.getBoxNumbers(poleId);
    }
}
