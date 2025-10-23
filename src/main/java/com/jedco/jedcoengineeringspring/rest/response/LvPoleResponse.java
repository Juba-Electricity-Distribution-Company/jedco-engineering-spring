package com.jedco.jedcoengineeringspring.rest.response;

public record LvPoleResponse(
        Long id,
        String feeder,
        String txNo,
        String branchCode,
        String poleNo,
        String boxNo,
        String meterType,
        String poleType,
        String northing,
        String easting,
        String latitude,
        String longitude
) {
}
