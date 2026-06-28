package com.billing.repository;

import com.billing.entity.StateMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StateMasterRepository extends JpaRepository<StateMaster, Long> {
    List<StateMaster> findByActiveTrueOrderByCountryNameAscStateNameAsc();
    Optional<StateMaster> findByIdAndActiveTrue(Long id);
    Optional<StateMaster> findByStateCodeIgnoreCase(String stateCode);
}
