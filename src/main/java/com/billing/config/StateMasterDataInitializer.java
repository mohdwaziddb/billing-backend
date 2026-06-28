package com.billing.config;

import com.billing.entity.StateMaster;
import com.billing.repository.StateMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StateMasterDataInitializer implements ApplicationRunner {

    private final StateMasterRepository stateMasterRepository;

    private static final List<StateSeed> STATES = List.of(
            new StateSeed("AN", "Andaman and Nicobar Islands"),
            new StateSeed("AP", "Andhra Pradesh"),
            new StateSeed("AR", "Arunachal Pradesh"),
            new StateSeed("AS", "Assam"),
            new StateSeed("BR", "Bihar"),
            new StateSeed("CH", "Chandigarh"),
            new StateSeed("CT", "Chhattisgarh"),
            new StateSeed("DN", "Dadra and Nagar Haveli and Daman and Diu"),
            new StateSeed("DL", "Delhi"),
            new StateSeed("GA", "Goa"),
            new StateSeed("GJ", "Gujarat"),
            new StateSeed("HR", "Haryana"),
            new StateSeed("HP", "Himachal Pradesh"),
            new StateSeed("JK", "Jammu and Kashmir"),
            new StateSeed("JH", "Jharkhand"),
            new StateSeed("KA", "Karnataka"),
            new StateSeed("KL", "Kerala"),
            new StateSeed("LA", "Ladakh"),
            new StateSeed("LD", "Lakshadweep"),
            new StateSeed("MP", "Madhya Pradesh"),
            new StateSeed("MH", "Maharashtra"),
            new StateSeed("MN", "Manipur"),
            new StateSeed("ML", "Meghalaya"),
            new StateSeed("MZ", "Mizoram"),
            new StateSeed("NL", "Nagaland"),
            new StateSeed("OD", "Odisha"),
            new StateSeed("PY", "Puducherry"),
            new StateSeed("PB", "Punjab"),
            new StateSeed("RJ", "Rajasthan"),
            new StateSeed("SK", "Sikkim"),
            new StateSeed("TN", "Tamil Nadu"),
            new StateSeed("TS", "Telangana"),
            new StateSeed("TR", "Tripura"),
            new StateSeed("UP", "Uttar Pradesh"),
            new StateSeed("UK", "Uttarakhand"),
            new StateSeed("WB", "West Bengal")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (StateSeed seed : STATES) {
            stateMasterRepository.findByStateCodeIgnoreCase(seed.code())
                    .orElseGet(() -> stateMasterRepository.save(StateMaster.builder()
                            .stateCode(seed.code())
                            .stateName(seed.name())
                            .countryName("India")
                            .active(true)
                            .build()));
        }
    }

    private record StateSeed(String code, String name) {
    }
}
