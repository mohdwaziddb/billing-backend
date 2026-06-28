package com.billing.service;

import com.billing.dto.state.StateResponse;
import com.billing.entity.StateMaster;
import com.billing.exception.BadRequestException;
import com.billing.repository.StateMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StateMasterService {

    private final StateMasterRepository stateMasterRepository;

    @Transactional(readOnly = true)
    public List<StateResponse> listActive() {
        return stateMasterRepository.findByActiveTrueOrderByCountryNameAscStateNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public StateMaster getActiveByIdOrThrow(Long stateId, String fieldLabel) {
        if (stateId == null) {
            throw new BadRequestException(fieldLabel + " is required");
        }
        return stateMasterRepository.findByIdAndActiveTrue(stateId)
                .orElseThrow(() -> new BadRequestException("Select a valid " + fieldLabel.toLowerCase()));
    }

    private StateResponse toResponse(StateMaster state) {
        return StateResponse.builder()
                .id(state.getId())
                .stateCode(state.getStateCode())
                .stateName(state.getStateName())
                .countryName(state.getCountryName())
                .active(state.isActive())
                .build();
    }
}
