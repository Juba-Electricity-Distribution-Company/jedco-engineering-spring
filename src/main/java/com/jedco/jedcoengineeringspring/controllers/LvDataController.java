package com.jedco.jedcoengineeringspring.controllers;

import com.jedco.jedcoengineeringspring.rest.request.LvDataRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.response.LvDataResponse;
import com.jedco.jedcoengineeringspring.rest.response.ResponseDto;
import com.jedco.jedcoengineeringspring.services.LvDataService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/lvData")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "LvNetworkData")
public class LvDataController {
    private final LvDataService lvDataService;

    @GetMapping("/checkMeter")
    public ResponseDto checkMeter(@RequestParam("meterNo") String meterNo) {
        return this.lvDataService.checkMeter(meterNo);
    }

    @GetMapping("/getDataByUser")
    @PreAuthorize("hasAnyAuthority('REGISTER_LV_DATA', 'VIEW_POLE_DATA')")
    @Operation(summary = "Count Activity Log", description = "The Dates should be provided in 'yyyy/MM/dd' String format.\n\n")
    public List<LvDataResponse> getDataByUser(@RequestParam("date") String date) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvDataService.getDataByUser(date, userDetails.getUsername());
    }

    @GetMapping("/getDataByTx")
    @PreAuthorize("hasAnyAuthority('REGISTER_LV_DATA', 'VIEW_POLE_DATA')")
    @Operation(summary = "Count Activity Log", description = "The Dates should be provided in 'yyyy/MM/dd' String format.\n\n")
    public List<LvDataResponse> getDataByTx(@RequestParam("feeder") String feeder, @RequestParam("txCode") String txCode) {
        return this.lvDataService.getDataByFeederAndTx(feeder, txCode);
    }

    @GetMapping("/getDataByFeederTxPole")
    @PreAuthorize("hasAnyAuthority('REGISTER_LV_DATA', 'VIEW_POLE_DATA')")
    public List<LvDataResponse> getDataByFeederTxPole(@RequestParam("feeder") String feeder, @RequestParam("txCode") String txCode, @RequestParam("poleNo") String poleNo) {
        return this.lvDataService.getDataByFeederTxPole(feeder, txCode, poleNo);
    }

    @GetMapping("/getDataByPoleNo")
    @PreAuthorize("hasAnyAuthority('REGISTER_LV_DATA', 'VIEW_POLE_DATA')")
    public List<LvDataResponse> getDataByPoleNo(@RequestParam("poleNo") String poleNo) {
        return this.lvDataService.getDataByPoleNo(poleNo);
    }

    @PostMapping("/registerLvData")
    @PreAuthorize("hasAuthority('REGISTER_LV_DATA') or @poleDataAuthorization.canRegister(#registerDto, authentication)")
    public ResponseDto registerLvData(@RequestBody LvDataRegisterRequest registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvDataService.insertLvData(registerDto, userDetails.getUsername());
    }

    @PostMapping("/updateLvData")
    @PreAuthorize("hasAuthority('REGISTER_LV_DATA') or @poleDataAuthorization.canUpdate(#registerDto, authentication)")
    public ResponseDto updateLvData(@RequestBody LvDataResponse registerDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return this.lvDataService.updateLvData(registerDto, userDetails.getUsername());
    }
}
