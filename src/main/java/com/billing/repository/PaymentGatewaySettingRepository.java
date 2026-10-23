package com.billing.repository;

import com.billing.entity.PaymentGatewaySetting;
import com.billing.entity.enums.PaymentGatewayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentGatewaySettingRepository extends JpaRepository<PaymentGatewaySetting, Long> {

    Optional<PaymentGatewaySetting> findByGatewayType(PaymentGatewayType gatewayType);

    List<PaymentGatewaySetting> findAllByOrderByIdAsc();

    List<PaymentGatewaySetting> findByActiveTrueOrderByDefaultGatewayDescIdAsc();
}
