package com.jedco.jedcoengineeringspring.services;

import com.jedco.jedcoengineeringspring.Util.DateConverter;
import com.jedco.jedcoengineeringspring.Util.Day;
import com.jedco.jedcoengineeringspring.mappers.PoleDataMapper;
import com.jedco.jedcoengineeringspring.models.*;
import com.jedco.jedcoengineeringspring.repositories.*;
import com.jedco.jedcoengineeringspring.rest.request.LvDataRegisterRequest;
import com.jedco.jedcoengineeringspring.rest.request.LvMeterDataRequest;
import com.jedco.jedcoengineeringspring.rest.response.LongLatResponse;
import com.jedco.jedcoengineeringspring.rest.response.LvDataResponse;
import com.jedco.jedcoengineeringspring.rest.response.LvMeterResponse;
import com.jedco.jedcoengineeringspring.rest.response.ResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LvDataServiceImpl implements LvDataService {
    private final MeterRepository meterRepository;
    private final MeterDataRepository meterDataRepository;
    private final UserRepository userRepository;
    private final PoleDataRepository poleDataRepository;
    private final StatusRepository statusRepository;
    private final TxInfoRepository txInfoRepository;
    private final BoxNumberRepository boxNumberRepository;

    private final PoleDataMapper poleDataMapper;
    private final UTMtoLangLatService utMtoLangLatService;
    private final DateConverter dateConverter;
    @Override
    public ResponseDto checkMeter(String meterNo) {
        Optional<Meter> optionalMeter= meterRepository.findByMeterNo(meterNo);
        if(optionalMeter.isEmpty()){
            return new ResponseDto(false, "Meter Not found!");
        }
        return new ResponseDto(true, "Meter found!");
    }

    @Override
    public List<LvDataResponse> getDataByUser(String date, String username) {
        Day day= dateConverter.convertToStartAndEndDate(date);
        User user = userRepository.findByUsername(username).get();
        Long activeStatus = 1L;
        List<PoleData>  poleDataList;
        if(username.equals("abe")){
//            poleDataList= poleDataRepository.findAllBySubmittedOnBetween(day.startTime(),day.endTime());
            poleDataList= poleDataRepository.findAllBySubmittedOnBetweenAndStatusId(day.startTime(),day.endTime(),activeStatus);
        }else{
//            poleDataList= poleDataRepository.findAllByRegisteredByAndSubmittedOnBetween(user, day.startTime(), day.endTime());
            poleDataList= poleDataRepository.findAllByRegisteredByAndSubmittedOnBetweenAndStatusId(user, day.startTime(), day.endTime(),activeStatus);
        }
        return getLvDataResponses(poleDataList);

    }

    @Override
    public List<LvDataResponse> getDataByFeederAndTx(String feeder, String txCode) {
//        List<PoleData> poleDataList= poleDataRepository.findAllByFeederAndTxNo(feeder,txCode);
        Long activeStatus = 1L;
//        List<PoleData> poleDataList= poleDataRepository.findAllByTransformerTrafoCodeAndTransformerFeederCode(txCode,feeder);
        List<PoleData> poleDataList= poleDataRepository.findAllByTransformerTrafoCodeAndTransformerFeederCodeAndStatusId(txCode,feeder,activeStatus);
        return getLvDataResponses(poleDataList);
    }

    private List<LvDataResponse> getLvDataResponses(List<PoleData> poleDataList) {
        Long activeStatus= 1L;
        return poleDataList.stream().map(poleData -> {
            List<MeterData> meterDataList = meterDataRepository.findByPoleDataAndStatusId(poleData, activeStatus);
            return poleDataMapper.toLvDataResponse(poleData, meterDataList);
        }).toList();
    }

    @Override
    public List<LvDataResponse> getDataByFeederTxPole(String feeder, String txCode, String poleNo) {
//        List<PoleData> poleDataList= poleDataRepository.findByFeederAndTxNoAndPoleNo(feeder,txCode,poleNo);
        Long activeStatus = 1L;
//        List<PoleData> poleDataList= poleDataRepository.findAllByTransformerTrafoCodeAndTransformerFeederCodeAndPoleNo(txCode,feeder,poleNo);
        List<PoleData> poleDataList= poleDataRepository.findAllByTransformerTrafoCodeAndTransformerFeederCodeAndPoleNoAndStatusId(txCode,feeder,poleNo,activeStatus);
        return getLvDataResponses(poleDataList);
    }

    @Override
    public List<LvDataResponse> getDataByPoleNo(String poleNo) {
        Long activeStatus = 1L;
//        List<PoleData> poleDataList= poleDataRepository.findByPoleNo(poleNo);
        List<PoleData> poleDataList= poleDataRepository.findByPoleNoAndStatusId(poleNo,activeStatus);
        return getLvDataResponses(poleDataList);
    }

    @Override
    @Transactional
    public ResponseDto insertLvData(LvDataRegisterRequest lvDataRegisterDto, String username) {
        try {
            Optional<TxInfo> optionalTransformer = txInfoRepository.findById(lvDataRegisterDto.txId());
            if (optionalTransformer.isEmpty()) {
                return new ResponseDto(false, "Transformer code not found!");
            }
            var transformer = optionalTransformer.get();

            // 🔹 Prevent duplicate pole registration for the same transformer
            Optional<PoleData> optionalPoleData =
                    poleDataRepository.findOneByPoleNoAndTransformer(lvDataRegisterDto.poleNo(), transformer);
            if (optionalPoleData.isPresent()) {
                return new ResponseDto(false, "Pole No. already registered with the same transformer.");
            }

            Long activeStatus = 1L;

            // 🔹 1. Check for duplicates *within* the submitted meter list
            Set<String> submittedMeterNos = new HashSet<>();
            for (LvMeterDataRequest meter : lvDataRegisterDto.meterDataDtoList()) {
                if (!submittedMeterNos.add(meter.meterNo())) {
                    return new ResponseDto(false, "Duplicate meter number found in submission: " + meter.meterNo());
                }
            }

            // 🔹 2. Check if any submitted meter already exists in DB
            for (LvMeterDataRequest meter : lvDataRegisterDto.meterDataDtoList()) {
                MeterData existing = meterDataRepository.findOneByMeterNoAndStatusId(meter.meterNo(), activeStatus);
                if (existing != null) {
                    return new ResponseDto(false,
                            "Meter No. " + meter.meterNo() + " is already registered in the system.");
                }
            }

            // 🔹 3. Proceed to save if all validations pass
            LongLatResponse longLatResponse =
                    utMtoLangLatService.convertUTMToLangLat(lvDataRegisterDto.northing(), lvDataRegisterDto.easting());
            Status status = statusRepository.findById(activeStatus).orElseThrow();
            User user = userRepository.findByUsername(username).orElseThrow();

            PoleData poleData = new PoleData();
            poleData.setAssemblyType(lvDataRegisterDto.assemblyType());
            poleData.setBranchCode(lvDataRegisterDto.branchCode());
            poleData.setConductorType(lvDataRegisterDto.conductorType());
            poleData.setPoleType(lvDataRegisterDto.poleType());
            poleData.setPoleFeature(lvDataRegisterDto.poleFeature());
            poleData.setPoleNo(lvDataRegisterDto.poleNo());
            poleData.setTransformer(transformer);
            poleData.setRegisteredBy(user);
            poleData.setStatus(status);
            poleData.setLatitude(longLatResponse.latitude());
            poleData.setLongitude(longLatResponse.longitude());
            poleData.setLocationAccuracy(lvDataRegisterDto.locationAccuracy());
            poleData.setRemark(lvDataRegisterDto.remark());
            poleData.setSubmittedOn(new Date());
            poleData.setNorthing(lvDataRegisterDto.northing());
            poleData.setEasting(lvDataRegisterDto.easting());
            poleData.setPole_anomaly(lvDataRegisterDto.pole_anomaly());
            poleData.setPoleRegType(lvDataRegisterDto.poleRegisterationType());
            poleData.setUpdatedOn(new Date());
            poleData.setUpdatedBy(user.getFirstName() + " " + user.getLastName());
            poleDataRepository.save(poleData);

            BoxNumber boxNumber = null;
            if (!Objects.equals(lvDataRegisterDto.poleRegisterationType(), "MV EXTENSION")) {
                boxNumber = getBoxNumber(user, poleData);
            }

            for (LvMeterDataRequest meter : lvDataRegisterDto.meterDataDtoList()) {
                MeterData meterData = new MeterData();
                meterData.setComCableLength(meter.comCableLength());
                meterData.setConnectedPhase(meter.connectedPhase());
                meterData.setCustomerName(meter.customerName());
                meterData.setMeterNo(meter.meterNo());
                meterData.setCustomerType(meter.customerType());
                meterData.setEstimatedLoad(meter.estimatedLoad());
                meterData.setMeterType(meter.meterType());
                meterData.setServiceCableLength(meter.serviceCableLength());
                meterData.setServiceCableType(meter.serviceCableType());
                meterData.setPoleData(poleData);
                meterData.setStatus(status);
                meterData.setMeterAnomaly(meter.meterAnomaly());
                meterData.setBoxAssemblyType(meter.assemblyType());
                meterData.setMeterBreakerSize(meter.meterBreakerSize());
                meterData.setCustomerSideBreakerSize(meter.customerSideBreakerSize());
                meterData.setRegisteredBy(user.getId());
                meterData.setRegisteredOn(new Date());
                meterData.setBoxAssemblyType(meter.assemblyType());
                meterData.setMeterRegType("COMMISSIONING");
                meterData.setCtRatio(meter.ctRatio());

                if (!Objects.equals(meter.meterType(), "High Current")) {
                    if (boxNumber == null) {
                        boxNumber = getBoxNumber(user, poleData);
                    }
                    meterData.setBoxNumber(boxNumber);
                }

                meterDataRepository.save(meterData);
            }

            return new ResponseDto(true, "LV Network data registered successfully.");
        } catch (Exception ex) {
            log.error("LV Pole data registration failed: " + ex.getMessage(), ex);
            return new ResponseDto(false, "LV Network data registration failed!");
        }
    }

    private BoxNumber getBoxNumber(User user, PoleData poleData) {
        BoxNumber boxNumber;
        BoxNumber box=new BoxNumber();
        Date boxNumberDate= new Date();
        box.setCreatedBy(user);
        box.setCreatedOn(boxNumberDate);
        box.setUpdatedOn(boxNumberDate);
        box.setPoleData(poleData);
        boxNumber=createBoxNumber(poleData,box);
        return boxNumber;
    }

    //    @Transactional
    protected BoxNumber createBoxNumber(PoleData poleData,BoxNumber boxNumber){
        var transformer= poleData.getTransformer();
        Long newSequence = transformer.getBoxSequence() + 1;
        transformer.setBoxSequence(newSequence);
        txInfoRepository.save(transformer);
        boxNumber.setBoxNumber(newSequence);
        return boxNumberRepository.save(boxNumber);
    }

    @Override
    @Transactional
    public ResponseDto updateLvData(LvDataResponse updateDto, String username) {
        try {
            Optional<TxInfo> optionalTransformer = txInfoRepository.findById(updateDto.txId());
            if (optionalTransformer.isEmpty()) {
                return new ResponseDto(false, "Tx No Not Found!");
            }
            var transformer = optionalTransformer.get();

            Optional<PoleData> optionalPoleData = poleDataRepository.findById(updateDto.id());
            Optional<PoleData> optionalCheckData = poleDataRepository.findOneByPoleNoAndTransformer(updateDto.poleNo(), transformer);

            if (optionalPoleData.isEmpty()) {
                return new ResponseDto(false, "Pole Not Found");
            }

            var poleData = optionalPoleData.get();
            if (optionalCheckData.isPresent() && !optionalCheckData.get().getId().equals(poleData.getId())) {
                return new ResponseDto(false, "Pole No. Already registered with the same Tx Code.");
            }

            Long activeStatus = 1L;
            Long deletedStatus = 3L;
            List<String> updateMeters = new ArrayList<>();

            // --- Step 1: Check for duplicate meter numbers inside the request ---
            Set<String> meterNoSet = new HashSet<>();
            for (LvMeterResponse meter : updateDto.meterDataDtoList()) {
                if (!meterNoSet.add(meter.meterNo())) {
                    return new ResponseDto(false, "Duplicate meter number found in request: " + meter.meterNo());
                }
            }

            // --- Step 2: Validate meters and handle DB duplicates ---
            User user = userRepository.findByUsername(username).get();
            Status status = statusRepository.findById(activeStatus).get();

            for (LvMeterResponse meter : updateDto.meterDataDtoList()) {
                List<MeterData> activeMeters = meterDataRepository.findAllByMeterNoAndStatusId(meter.meterNo(), activeStatus);

                if (meter.id() == null) {
                    // New meter being added
                    if (!activeMeters.isEmpty()) {
                        return new ResponseDto(false, "Meter No. " + meter.meterNo() + " already registered in the system");
                    }
                } else {
                    // Existing meter being updated — deactivate any duplicate DB entries with same meterNo
                    for (MeterData dbMeter : activeMeters) {
                        if (!dbMeter.getId().equals(meter.id())) {
                            dbMeter.setStatus(statusRepository.findById(deletedStatus).get());
                            dbMeter.setUpdatedBy(user);
                            dbMeter.setUpdatedOn(new Date());
                            meterDataRepository.save(dbMeter);
                        }
                    }
                }

                updateMeters.add(meter.meterNo());
            }

            // --- Step 3: Update Pole Data ---
            LongLatResponse longLatResponse = this.utMtoLangLatService.convertUTMToLangLat(updateDto.northing(), updateDto.easting());
            poleData.setAssemblyType(updateDto.assemblyType());
            poleData.setBranchCode(updateDto.branchCode());
            poleData.setConductorType(updateDto.conductorType());
            poleData.setPoleType(updateDto.poleType());
            poleData.setPoleFeature(updateDto.poleFeature());
            poleData.setPoleNo(updateDto.poleNo());
            poleData.setTransformer(transformer);
            poleData.setStatus(status);
            poleData.setLatitude(longLatResponse.latitude());
            poleData.setLongitude(longLatResponse.longitude());
            poleData.setLocationAccuracy(updateDto.locationAccuracy());
            poleData.setRemark(updateDto.remark());
            poleData.setNorthing(updateDto.northing());
            poleData.setEasting(updateDto.easting());
            poleData.setPole_anomaly(updateDto.pole_anomaly());
            poleData.setPoleRegType(updateDto.poleRegistrationType());
            poleData.setUpdatedOn(new Date());
            poleData.setUpdatedBy(user.getFirstName() + " " + user.getLastName());
            poleDataRepository.save(poleData);

            // --- Step 4: Mark removed meters as deleted ---
            for (MeterData meter : poleData.getMeterDataSet()) {
                if (!updateMeters.contains(meter.getMeterNo())) {
                    meter.setStatus(statusRepository.findById(deletedStatus).get());
                    meterDataRepository.save(meter);
                }
            }

            // --- Step 5: Create or update meter data ---
            for (LvMeterResponse meter : updateDto.meterDataDtoList()) {
                MeterData meterData;

                if (meter.id() != null) {
                    // Update existing meter
                    meterData = meterDataRepository.findById(meter.id()).orElse(null);
                    if (meterData != null && !checkIfEqual(meterData, meter)) {
                        poleUpdateData(poleData, status, meter, meterData);
                        if (Objects.equals(meter.meterType(), "High Current")) {
                            meterData.setBoxNumber(null);
                        }
                        meterData.setUpdatedBy(user);
                        meterData.setUpdatedOn(new Date());
                        meterDataRepository.save(meterData);
                    }
                } else {
                    // Create new meter
                    meterData = new MeterData();
                    poleUpdateData(poleData, status, meter, meterData);
                    meterData.setRegisteredBy(user.getId());
                    meterData.setMeterRegType("COMMISSIONING");
                    meterData.setRegisteredOn(new Date());
                    meterDataRepository.save(meterData);
                }
            }

            return new ResponseDto(true, "Lv Network data Registered Successfully");
        }
        catch (Exception ex) {
            log.error("Lv Pole data update failed...", ex);
            return new ResponseDto(false, "Lv Network data Registration Failed!");
        }
    }


    private boolean checkIfEqual(MeterData meterData, LvMeterResponse meter) {
        // Check if any of the fields is different
        if (!Objects.equals(meterData.getComCableLength(), meter.comCableLength())) return false;
        if (!Objects.equals(meterData.getConnectedPhase(), meter.connectedPhase())) return false;
        if (!Objects.equals(meterData.getCustomerName(), meter.customerName())) return false;
        if (!Objects.equals(meterData.getMeterNo(), meter.meterNo())) return false;
        if (!Objects.equals(meterData.getCustomerType(), meter.customerType())) return false;
        if (!Objects.equals(meterData.getEstimatedLoad(), meter.estimatedLoad())) return false;
        if (!Objects.equals(meterData.getMeterType(), meter.meterType())) return false;
        if (!Objects.equals(meterData.getServiceCableLength(), meter.serviceCableLength())) return false;
        if (!Objects.equals(meterData.getServiceCableType(), meter.serviceCableType())) return false;
        if (!Objects.equals(meterData.getMeterBreakerSize(), meter.meterBreakerSize())) return false;
        if (!Objects.equals(meterData.getCustomerSideBreakerSize(), meter.customerSideBreakerSize())) return false;
        if (!Objects.equals(meterData.getMeterAnomaly(), meter.meterAnomaly())) return false;
        BoxNumber meterBoxNumber = meter.boxNoId() != null ? boxNumberRepository.findById(meter.boxNoId()).orElse(null) : null;
        if (!Objects.equals(meterData.getBoxNumber(), meterBoxNumber)) return false;
        if (!Objects.equals(meterData.getBoxAssemblyType(), meter.assemblyType())) return false;
        if (!Objects.equals(meterData.getCtRatio(), meter.ctRatio())) return false;

        // All fields are equal
        return true;

    }

    private void poleUpdateData(PoleData poleData, Status status, LvMeterResponse meter, MeterData meterData) {
        meterData.setComCableLength(meter.comCableLength());
        meterData.setConnectedPhase(meter.connectedPhase());
        meterData.setCustomerName(meter.customerName());
        meterData.setMeterNo(meter.meterNo());
        meterData.setCustomerType(meter.customerType());
        meterData.setEstimatedLoad(meter.estimatedLoad());
        meterData.setMeterType(meter.meterType());
        meterData.setMeterBreakerSize(meter.meterBreakerSize());
        meterData.setCustomerSideBreakerSize(meter.customerSideBreakerSize());
        meterData.setServiceCableLength(meter.serviceCableLength());
        meterData.setServiceCableType(meter.serviceCableType());
        meterData.setPoleData(poleData);
        meterData.setStatus(status);
        meterData.setMeterAnomaly(meter.meterAnomaly());
        if(meter.boxNoId()!=null) {
            meterData.setBoxNumber(boxNumberRepository.findById(meter.boxNoId()).get());
        }
        meterData.setBoxAssemblyType(meter.assemblyType());
        meterData.setCtRatio(meter.ctRatio());
    }
}
