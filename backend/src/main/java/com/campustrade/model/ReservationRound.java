
package com.campustrade.model;

import java.time.LocalDateTime;

public class ReservationRound {

    private Long id;
    private Long productId;

    private ReservationRoundStatus status;

    private LocalDateTime firstReservedAt;
    private LocalDateTime priorityEndsAt;
    private LocalDateTime coolingEndsAt;
    private LocalDateTime sellerResponseDeadline;

    private LocalDateTime frozenAt;
    private LocalDateTime sellerRespondedAt;
    private LocalDateTime matchingDeadline;
    private LocalDateTime closedAt;

    private String closeReason;

    private Integer drawVersion;
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public ReservationRoundStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationRoundStatus status) {
        this.status = status;
    }

    public LocalDateTime getFirstReservedAt() {
        return firstReservedAt;
    }

    public void setFirstReservedAt(LocalDateTime firstReservedAt) {
        this.firstReservedAt = firstReservedAt;
    }

    public LocalDateTime getPriorityEndsAt() {
        return priorityEndsAt;
    }

    public void setPriorityEndsAt(LocalDateTime priorityEndsAt) {
        this.priorityEndsAt = priorityEndsAt;
    }

    public LocalDateTime getCoolingEndsAt() {
        return coolingEndsAt;
    }

    public void setCoolingEndsAt(LocalDateTime coolingEndsAt) {
        this.coolingEndsAt = coolingEndsAt;
    }

    public LocalDateTime getSellerResponseDeadline() {
        return sellerResponseDeadline;
    }

    public void setSellerResponseDeadline(
            LocalDateTime sellerResponseDeadline
    ) {
        this.sellerResponseDeadline = sellerResponseDeadline;
    }

    public LocalDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDateTime frozenAt) {
        this.frozenAt = frozenAt;
    }

    public LocalDateTime getSellerRespondedAt() {
        return sellerRespondedAt;
    }

    public void setSellerRespondedAt(
            LocalDateTime sellerRespondedAt
    ) {
        this.sellerRespondedAt = sellerRespondedAt;
    }

    public LocalDateTime getMatchingDeadline() {
        return matchingDeadline;
    }

    public void setMatchingDeadline(
            LocalDateTime matchingDeadline
    ) {
        this.matchingDeadline = matchingDeadline;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public Integer getDrawVersion() {
        return drawVersion;
    }

    public void setDrawVersion(Integer drawVersion) {
        this.drawVersion = drawVersion;
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
