package com.jedco.jedcoengineeringspring.services;

import com.jedco.jedcoengineeringspring.config.MeterHistoryType;
import com.jedco.jedcoengineeringspring.mappers.BoxNumberMapper;
import com.jedco.jedcoengineeringspring.mappers.PoleDataMapper;
import com.jedco.jedcoengineeringspring.models.*;
import com.jedco.jedcoengineeringspring.repositories.*;
import com.jedco.jedcoengineeringspring.rest.request.*;
import com.jedco.jedcoengineeringspring.rest.response.*;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissioningServiceImpl implements CommissioningService {
    private final MeterDataRepository meterDataRepository;
    private final DispatchedMeterRepository dispatchedMeterRepository;
    private final PoleDataRepository poleDataRepository;
    private final StatusRepository statusRepository;
    private final UserRepository userRepository;
    private final BoxNumberRepository boxNumberRepository;
    private final TxInfoRepository txInfoRepository;
    private final MeterHistoryRepository meterHistoryRepository;

    private final PoleDataMapper poleDataMapper;
    private final BoxNumberMapper boxNumberMapper;
    private final UTMtoLangLatService utMtoLangLatService;

    @Override
    public RefResponse checkRef(String meterNo) {
        Long activeStatus = 1L;
        List<MeterData> meters = meterDataRepository.findAllByMeterNoAndStatusId(meterNo, activeStatus);
        if (meters.isEmpty()) {
            return new RefResponse(false, "Reference Meter Not found!", null);
        } else if (meters.size() > 1) {
            return new RefResponse(false, "Reference Meter is not unique!", null);
        } else {
            MeterData meterData= meters.get(0);
            PoleData poleData = meterData.getPoleData();
            LvPoleResponse poleResponseDto = poleDataMapper.toPoleResponse(poleData,meterData);
            return new RefResponse(true, "Reference Pole Data Found!", poleResponseDto);
        }
    }

    @Override
    public DispatchedMeterResponse checkDispatchedMeter(String meterNo) {
        List<DispatchedMeter> meters = dispatchedMeterRepository.findByMeterNo(meterNo);
        return getDispatchedMeterResponse(meters);
    }

    @Override
    public DispatchedMeterResponse checkDispatchedMeterForMeterChange(String meterNo) {
        List<DispatchedMeter> meters = dispatchedMeterRepository.findByMeterNo(meterNo);
        Long activeStatus = 1L;
        MeterData commissionedMeter = meterDataRepository.findOneByMeterNoAndStatusId(meterNo, activeStatus);
        if (commissionedMeter != null) {
            return new DispatchedMeterResponse(false, "Reference Meter already Commissioned!", null, null);
        }
        return getDispatchedMeterResponse(meters);
    }

    @NotNull
    private DispatchedMeterResponse getDispatchedMeterResponse(List<DispatchedMeter> meters) {
        if (meters.isEmpty()) {
            return new DispatchedMeterResponse(false, "Reference Meter Not found!", null, null);
        } else if (meters.size() > 1) {
            return new DispatchedMeterResponse(false, "Reference Meter is not unique!", null, null);
        } else {
            return new DispatchedMeterResponse(true, "Reference Meter Found!", meters.get(0).getMeterNo(), meters.get(0).getName());
        }
    }

    @Override
    public ResponseDto insertCommissioningData(CommissioningRegisterRequest lvDataRegisterDto, String username) {
        try {

            Long activeStatus = 1L;
            for (LvMeterDataRequest meter : lvDataRegisterDto.meterDataDtoList()) {
                MeterData meterData = meterDataRepository.findOneByMeterNoAndStatusId(meter.meterNo(), activeStatus);
                if (meterData != null) {
                    return new ResponseDto(false, "Meter No. " + meter.meterNo() + " Already registered in the system");
                }
            }

            //TODO Check this part for duplicate cases
            Optional<PoleData> optionalPoleData;
            if (lvDataRegisterDto.poleId() != null) {
                optionalPoleData = poleDataRepository.findById(lvDataRegisterDto.poleId());
            } else {
//                optionalPoleData = poleDataRepository.findOneByPoleNoAndTxNo(lvDataRegisterDto.poleNo(), lvDataRegisterDto.txNo());
                optionalPoleData = poleDataRepository.findOneByPoleNoAndTransformerTrafoCode(lvDataRegisterDto.poleNo(), lvDataRegisterDto.txNo());
            }

            if (optionalPoleData.isEmpty()) {
                return new ResponseDto(false, "Lv Pole Not Found!");
            }

            Status status = statusRepository.findById(activeStatus).get();
            User user = userRepository.findByUsername(username).get();

            var poleData = optionalPoleData.get();
            //TODO CHECK THIS PART CAREFULLY
            poleDataRepository.save(poleData);

            for (LvMeterDataRequest meter : lvDataRegisterDto.meterDataDtoList()) {
                BoxNumber boxNumber = boxNumberRepository.findById(meter.boxNoId()).get();
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
                meterData.setMeterBreakerSize(meter.meterBreakerSize());
                meterData.setCustomerSideBreakerSize(meter.customerSideBreakerSize());
                meterData.setRegisteredBy(user.getId());
                meterData.setMeterRegType("COMMISSIONING");
                meterData.setBoxNo(meter.boxNo());
                meterData.setBoxAssemblyType(meter.assemblyType());
                meterData.setRegisteredOn(new Date());
                meterData.setBoxNumber(boxNumber);
                meterDataRepository.save(meterData);

            }

            return new ResponseDto(true, "Meter Commissioning data Registered Successfully");
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new ResponseDto(false, "Meter Commissioning data Registration Failed!");

        }
    }

    @Override
    public ResponseDto registerNewTx(TxInsertRequest registerDto, String username) {
        try {
            Optional<TxInfo> optionalTxInfo = txInfoRepository.findByTrafoCode(registerDto.trafoCode());

            Optional<User> optionalUser = userRepository.findByUsername(username);
            if (optionalUser.isEmpty()) {
                return new ResponseDto(false, "User Not Found!");

            }
            if (optionalTxInfo.isPresent()) {
                return new ResponseDto(false, "Transformer Already Exist in the system with the same name!");

            }
            var user = optionalUser.get();
            LongLatResponse longLatResponse = this.utMtoLangLatService.convertUTMToLangLat(registerDto.northing(), registerDto.easting());
            TxInfo newTx = new TxInfo();
            Date date = new Date();
            newTx.setTrafoCode(registerDto.trafoCode());
            newTx.setEasting(registerDto.easting());
            newTx.setNorthing(registerDto.northing());
            newTx.setFeederCode(registerDto.feederCode());
            newTx.setLatitude(Double.parseDouble(longLatResponse.latitude()));
            newTx.setLongitude(Double.parseDouble(longLatResponse.longitude()));
            newTx.setMvPoleCodeNearToTrafo(registerDto.mvPoleCodeNearToTrafo());
            newTx.setTrafoCapacityInKva(registerDto.trafoCapacityInKva());
            newTx.setRegisteredBy(user.getId());
            newTx.setUpdatedBy(user.getId());
            newTx.setUpdatedOn(date);
            newTx.setRegisteredOn(date);
            newTx.setBoxSequence(0L);

            txInfoRepository.save(newTx);

            return new ResponseDto(true, "New Transformer Registered Successfully!");
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new ResponseDto(false, "Transformer Registration Failed!");

        }
    }

    @Override
    public ResponseDto updateTx(TxInsertRequest registerDto, String username) {
        try {
            if (registerDto.trafoCode() == null) {
                return new ResponseDto(false, "Transformer Code can not be empty");
            }
            User user = userRepository.findByUsername(username).get();
            Optional<TxInfo> optionalTxInfo = txInfoRepository.findById(registerDto.id());
            if (optionalTxInfo.isEmpty()) {
                return new ResponseDto(false, "Transformer Not Found");
            }
            var txInfo = optionalTxInfo.get();
            Optional<TxInfo> optionalTrafo = txInfoRepository.findByTrafoCode(registerDto.trafoCode());
            if (optionalTrafo.isPresent() && !Objects.equals(optionalTrafo.get().getId(), txInfo.getId())) {
                return new ResponseDto(false, "Transformer Already Exist in the system with the same name!");
            }
            LongLatResponse longLatResponse = this.utMtoLangLatService.convertUTMToLangLat(registerDto.northing(), registerDto.easting());
            txInfo.setTrafoCode(registerDto.trafoCode());
            txInfo.setEasting(registerDto.easting());
            txInfo.setNorthing(registerDto.northing());
            txInfo.setLatitude(Double.parseDouble(longLatResponse.latitude()));
            txInfo.setLongitude(Double.parseDouble(longLatResponse.longitude()));
            txInfo.setFeederCode(registerDto.feederCode());
            txInfo.setMvPoleCodeNearToTrafo(registerDto.mvPoleCodeNearToTrafo());
            txInfo.setTrafoCapacityInKva(registerDto.trafoCapacityInKva());
            txInfo.setUpdatedBy(user.getId());
            txInfo.setUpdatedOn(new Date());
            txInfoRepository.save(txInfo);
            return new ResponseDto(true, "Transformer Updated Successfully!");
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new ResponseDto(false, "Transformer Update Failed!");
        }
    }

    @Override
    public ResponseDto registerMeterChange(String oldMeter, String newMeter, String username) {
        try {
            //TODO add delete access for some users
            User user = userRepository.findByUsername(username).get();
            Long activeStatus = 1L;
            List<MeterData> chkMeter = meterDataRepository.findAllByMeterNoAndStatusId(oldMeter, activeStatus);
            if (chkMeter.isEmpty()) {
                return new ResponseDto(false, "Old Meter Not Found in the system");
            } else if (chkMeter.size() > 1) {
                return new ResponseDto(false, "Old Meter Not Unique in the system");
            }
            MeterData meter = chkMeter.get(0);
            Long reversedStatus = 7L;
            meter.setStatus(statusRepository.findById(reversedStatus).get());
            meterDataRepository.save(meter);


            MeterData meterData = new MeterData();
            Date registeredOn= new Date();

            meterData.setComCableLength(meter.getComCableLength());
            meterData.setConnectedPhase(meter.getConnectedPhase());
            meterData.setCustomerName(meter.getCustomerName());
            meterData.setMeterNo(newMeter);
            meterData.setCustomerType(meter.getCustomerType());
            meterData.setEstimatedLoad(meter.getEstimatedLoad());
            meterData.setMeterType(meter.getMeterType());
            meterData.setServiceCableLength(meter.getServiceCableLength());
            meterData.setServiceCableType(meter.getServiceCableType());
            meterData.setPoleData(meter.getPoleData());
            meterData.setStatus(this.statusRepository.findById(activeStatus).get());
            meterData.setMeterAnomaly(meter.getMeterAnomaly());
            meterData.setRegisteredBy(user.getId());
            meterData.setMeterRegType("METER CHANGE");
            meterData.setRegisteredOn(registeredOn);
            meterData.setBoxAssemblyType(meter.getBoxAssemblyType());
            meterData.setBoxNumber(meter.getBoxNumber());
            meterDataRepository.save(meterData);

            MeterHistory history= new MeterHistory();
            history.setMeterHistoryType(MeterHistoryType.METER_CHANGE);
            history.setRegisteredOn(registeredOn);
            history.setCreatedBy(user);
            history.setMeter(meterData);
            history.setOldMeter(meter);
            meterHistoryRepository.save(history);


            return new ResponseDto(true, "Meter Change done successfully!");
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new ResponseDto(false, "Meter Change Failed");
        }
    }

    @Override
    public ResponseDto registerMeterRelocation(MeterRelocationRequest relocationDto, String username) {
        try {
            User user = userRepository.findByUsername(username).get();
            Long activeStatus = 1L;
            List<MeterData> chkMeter = meterDataRepository.findAllByMeterNoAndStatusId(relocationDto.meterNo(), activeStatus);

            if (chkMeter.isEmpty()) {
                return new ResponseDto(true, "Meter Not Found in the system");
            } else if (chkMeter.size() > 1) {
                return new ResponseDto(true, "Meter Not Unique in the system");
            }

            Optional<PoleData> optionalDestPole = poleDataRepository.findById(relocationDto.poleId());
            if (optionalDestPole.isEmpty()) {
                return new ResponseDto(true, "Destination Pole Not Found!");
            }

            //Box No is Optional ( for high current meters not required)
            BoxNumber destBoxNumber = null;
            if (relocationDto.boxNoId() != null) {
                Optional<BoxNumber> optionalDestBoxNumber = boxNumberRepository.findById(relocationDto.boxNoId());
                if (optionalDestBoxNumber.isEmpty()) {
                    return new ResponseDto(true, "Box Number Not Found!");
                }
                destBoxNumber = optionalDestBoxNumber.get();
            }

            var destPole = optionalDestPole.get();
            MeterData meter = chkMeter.get(0);

            Long relocatedStatus = 8L;
            meter.setStatus(statusRepository.findById(relocatedStatus).get());
            meterDataRepository.save(meter);

            MeterData meterData = new MeterData();
            Date registeredOn = new Date();

            meterData.setComCableLength(meter.getComCableLength());
            meterData.setConnectedPhase(meter.getConnectedPhase());
            meterData.setCustomerName(meter.getCustomerName());
            meterData.setMeterNo(meter.getMeterNo());
            meterData.setCustomerType(meter.getCustomerType());
            meterData.setEstimatedLoad(meter.getEstimatedLoad());
            meterData.setMeterType(meter.getMeterType());
            meterData.setServiceCableLength(meter.getServiceCableLength());
            meterData.setServiceCableType(meter.getServiceCableType());
            meterData.setPoleData(destPole);
            meterData.setStatus(statusRepository.findById(activeStatus).get());
            meterData.setMeterAnomaly(meter.getMeterAnomaly());
            meterData.setRegisteredBy(user.getId());
            meterData.setMeterRegType("METER RELOCATION");
            meterData.setRegisteredOn(registeredOn);
            meterData.setBoxAssemblyType(meter.getBoxAssemblyType());

            //Set Box Number only if provided, otherwise null
            meterData.setBoxNumber(destBoxNumber);

            meterDataRepository.save(meterData);

            MeterHistory history = new MeterHistory();
            history.setMeterHistoryType(MeterHistoryType.METER_RELOCATION);
            history.setRegisteredOn(registeredOn);
            history.setCreatedBy(user);
            history.setMeter(meterData);
            history.setOldMeter(meter);
            history.setOldPole(meter.getPoleData());
            meterHistoryRepository.save(history);

            return new ResponseDto(true, "Meter Relocation done successfully!");
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new ResponseDto(false, "Meter Relocation Failed");
        }
    }

    @Override
    public ResponseDto registerLvExtension(LvExtensionRegisterRequest registerDto, String username) {
        try {

//            Optional<PoleData> optionalPoleData = poleDataRepository.findOneByPoleNoAndTxNo(registerDto.poleNo(), registerDto.txNo());
            Optional<PoleData> optionalPoleData = poleDataRepository.findOneByPoleNoAndTransformerTrafoCode(registerDto.poleNo(), registerDto.txNo());
            if (optionalPoleData.isPresent()) {
                return new ResponseDto(false, "Pole No. Already registered with the same Tx Code.");
            }

            Long activeStatus = 1L;
            Status status = statusRepository.findById(activeStatus).get();
            User user = userRepository.findByUsername(username).get();

            PoleData poleData = new PoleData();
            poleData.setFeeder(registerDto.feeder());
            poleData.setAssemblyType(registerDto.assemblyType());
            poleData.setBranchCode(registerDto.branchCode());
            poleData.setConductorType(registerDto.conductorType());
            poleData.setPoleType(registerDto.poleType());
            poleData.setPoleFeature(registerDto.poleFeature());
            poleData.setPoleNo(registerDto.poleNo());
            poleData.setTxNo(registerDto.txNo());
            poleData.setRegisteredBy(user);
            poleData.setStatus(status);
            poleData.setLatitude(registerDto.latitude());
            poleData.setLongitude(registerDto.longitude());
            poleData.setLocationAccuracy(registerDto.locationAccuracy());
            poleData.setRemark(registerDto.remark());
            poleData.setSubmittedOn(new Date());
            poleData.setNorthing(registerDto.northing());
            poleData.setEasting(registerDto.easting());
            poleData.setPole_anomaly(registerDto.pole_anomaly());
            poleData.setPoleRegType(registerDto.poleRegisterationType());

            poleDataRepository.save(poleData);

            return new ResponseDto(true, "Lv Extension data Registered Successfully");
        } catch (Exception ex) {
            return new ResponseDto(false, "Lv Extension data Registration Failed!");

        }
    }

    @Override
    public List<BoxNumberResponse> getBoxNumbers(Long poleId) {
        PoleData poleData = poleDataRepository.findById(poleId).get();
        Set<BoxNumber> boxNumbers = poleData.getBoxNumbers();
        return boxNumbers.stream().map(boxNumberMapper::toBoxNumberResponse).collect(Collectors.toList());
    }
}
