
package com.campustrade.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ReservationProperties.class)
public class ReservationConfig {

    @Bean
    public Clock reservationClock(
            ReservationProperties properties
    ) {
        requirePositive(
                properties.getPriorityWindow(),
                "priorityWindow"
        );

        requirePositive(
                properties.getCoolingPeriod(),
                "coolingPeriod"
        );

        requirePositive(
                properties.getSellerResponsePeriod(),
                "sellerResponsePeriod"
        );

        requirePositive(
                properties.getBuyerResponsePeriod(),
                "buyerResponsePeriod"
        );

        requirePositive(
                properties.getMatchingPeriod(),
                "matchingPeriod"
        );

        requirePositive(
                properties.getFulfillmentPeriod(),
                "fulfillmentPeriod"
        );

        requirePositive(
                properties.getFulfillmentExtension(),
                "fulfillmentExtension"
        );

        requirePositive(
                properties.getSellerRelistCooldown(),
                "sellerRelistCooldown"
        );

        requirePositive(
                properties.getBuyerCancelCooldown(),
                "buyerCancelCooldown"
        );

        if (properties.getPriorityWindow().compareTo(
                properties.getCoolingPeriod()
        ) >= 0) {
            throw new IllegalArgumentException(
                    "预约优先期必须短于冷静期"
            );
        }

        if (properties.getBuyerResponsePeriod().compareTo(
                properties.getMatchingPeriod()
        ) > 0) {
            throw new IllegalArgumentException(
                    "单个买家确认期限不能超过候补阶段总期限"
            );
        }

        if (properties.getMaxActiveReservations() <= 0) {
            throw new IllegalArgumentException(
                    "最大预约数量必须大于0"
            );
        }

        if (properties.getMaxDrawWeight() <= 0) {
            throw new IllegalArgumentException(
                    "抽签权重上限必须大于0"
            );
        }

        return Clock.system(
                ZoneId.of("Asia/Shanghai")
        );
    }

    private void requirePositive(
            Duration duration,
            String name
    ) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalArgumentException(
                    name + " 必须为正数"
            );
        }
    }
}
