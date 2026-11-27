
package com.campustrade.model;

import java.time.LocalDateTime;

public class ReservationDraw {

    private Long id;
    private Long roundId;
    private Integer drawVersion;
    private ReservationDrawTrigger triggerType;
    private Long triggerReservationId;
    private Integer participantCount;
    private Long totalWeight;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public Integer getDrawVersion() {
        return drawVersion;
    }

    public void setDrawVersion(Integer drawVersion) {
        this.drawVersion = drawVersion;
    }

    public ReservationDrawTrigger getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(
            ReservationDrawTrigger triggerType
    ) {
        this.triggerType = triggerType;
    }

    public Long getTriggerReservationId() {
        return triggerReservationId;
    }

    public void setTriggerReservationId(
            Long triggerReservationId
    ) {
        this.triggerReservationId =
                triggerReservationId;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(
            Integer participantCount
    ) {
        this.participantCount = participantCount;
    }

    public Long getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Long totalWeight) {
        this.totalWeight = totalWeight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }
}
