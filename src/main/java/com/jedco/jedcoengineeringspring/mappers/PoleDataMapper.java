package com.jedco.jedcoengineeringspring.mappers;

import com.jedco.jedcoengineeringspring.models.MeterData;
import com.jedco.jedcoengineeringspring.models.PoleData;
import com.jedco.jedcoengineeringspring.rest.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PoleDataMapper {

    @Mapping(target = "meterType", source="meterData.meterType")
    @Mapping(target = "boxNo", source = "meterData.boxNumber.formattedBoxNumber")
    @Mapping(target = "feeder", source = "poleData.transformer.feederCode")
    @Mapping(target = "txNo", source = "poleData.transformer.trafoCode")
    @Mapping(target = "id", source = "poleData.id")
    LvPoleResponse toPoleResponse(PoleData poleData,MeterData meterData);

    @Mapping(target = "txId", source = "poleData.transformer.id")
    @Mapping(target = "poleRegistrationType", source = "poleData.poleRegType")
    @Mapping(target = "meterDataDtoList", source = "meterDataList")
    @Mapping(target = "feeder", source = "poleData.transformer.feederCode")
    @Mapping(target = "txNo", source = "poleData.transformer.trafoCode")
    LvDataResponse toLvDataResponse(PoleData poleData, List<MeterData> meterDataList);

    @Mapping(target = "boxNoId", source = "boxNumber.id")
    @Mapping(target = "assemblyType", source = "boxAssemblyType")
    LvMeterResponse toLvMeterResponse(MeterData meterData);
}
