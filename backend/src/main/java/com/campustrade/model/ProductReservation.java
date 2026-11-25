
package com.campustrade.model;

import java.time.LocalDateTime;

public class ProductReservation {

    private Long id;
    private Long roundId;
    private Long buyerId;

    private ReservationStatus status;

    private Integer reservationOrder;
    private Integer drawWeight;
    private Integer queuePosition;

    private LocalDateTime invitedAt;
    private LocalDateTime invitationDeadline;
    private LocalDateTime respondedAt;
    private LocalDateTime cancelledAt;

    private String cancelReason;

    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Integer getReservationOrder() {
        return reservationOrder;
    }

    public void setReservationOrder(
            Integer reservationOrder
    ) {
        this.reservationOrder = reservationOrder;
    }

    public Integer getDrawWeight() {
        return drawWeight;
    }

    public void setDrawWeight(Integer drawWeight) {
        this.drawWeight = drawWeight;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }

    public LocalDateTime getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(LocalDateTime invitedAt) {
        this.invitedAt = invitedAt;
    }

    public LocalDateTime getInvitationDeadline() {
        return invitationDeadline;
    }

    public void setInvitationDeadline(
            LocalDateTime invitationDeadline
    ) {
        this.invitationDeadline = invitationDeadline;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
