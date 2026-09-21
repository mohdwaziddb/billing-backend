package com.billing.service;

import com.billing.dto.state.StateResponse;
import com.billing.entity.StateMaster;
import com.billing.exception.BadRequestException;
import com.billing.repository.StateMasterRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    @Transactional(readOnly = true)
    public List<StateResponse> listActive(Map<String, Object> param) {
        Map<String, Object> sanitizedParam = param;
        if (sanitizedParam == null) {
            sanitizedParam = Map.of();
        }
        String searchFilter = DataTypeUtility.stringValue(sanitizedParam.get("search"));
        if (searchFilter.length() > 0) {
            String normalizedSearch = searchFilter.trim().toLowerCase();
            if (normalizedSearch.length() > 0) {
                // future search handling placeholder with braces
                return listActive();
            }
        }
        return listActive();
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
