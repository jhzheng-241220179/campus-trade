
package com.campustrade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "campustrade.reservation")
public class ReservationProperties {

    private Duration priorityWindow = Duration.ofMinutes(1);

    private Duration coolingPeriod = Duration.ofMinutes(30);

    private Duration sellerResponsePeriod = Duration.ofMinutes(30);

    private Duration buyerResponsePeriod = Duration.ofMinutes(15);

    private Duration matchingPeriod = Duration.ofMinutes(60);

    private Duration fulfillmentPeriod = Duration.ofHours(72);

    private Duration fulfillmentExtension = Duration.ofHours(48);

    private Duration sellerRelistCooldown = Duration.ofMinutes(30);

    private Duration buyerCancelCooldown = Duration.ofMinutes(10);

    private int maxActiveReservations = 5;

    private int maxDrawWeight = 10;

    public Duration getPriorityWindow() {
        return priorityWindow;
    }

    public void setPriorityWindow(Duration priorityWindow) {
        this.priorityWindow = priorityWindow;
    }

    public Duration getCoolingPeriod() {
        return coolingPeriod;
    }

    public void setCoolingPeriod(Duration coolingPeriod) {
        this.coolingPeriod = coolingPeriod;
    }

    public Duration getSellerResponsePeriod() {
        return sellerResponsePeriod;
    }

    public void setSellerResponsePeriod(Duration sellerResponsePeriod) {
        this.sellerResponsePeriod = sellerResponsePeriod;
    }

    public Duration getBuyerResponsePeriod() {
        return buyerResponsePeriod;
    }

    public void setBuyerResponsePeriod(Duration buyerResponsePeriod) {
        this.buyerResponsePeriod = buyerResponsePeriod;
    }

    public Duration getMatchingPeriod() {
        return matchingPeriod;
    }

    public void setMatchingPeriod(Duration matchingPeriod) {
        this.matchingPeriod = matchingPeriod;
    }

    public Duration getFulfillmentPeriod() {
        return fulfillmentPeriod;
    }

    public void setFulfillmentPeriod(Duration fulfillmentPeriod) {
        this.fulfillmentPeriod = fulfillmentPeriod;
    }

    public Duration getFulfillmentExtension() {
        return fulfillmentExtension;
    }

    public void setFulfillmentExtension(Duration fulfillmentExtension) {
        this.fulfillmentExtension = fulfillmentExtension;
    }

    public Duration getSellerRelistCooldown() {
        return sellerRelistCooldown;
    }

    public void setSellerRelistCooldown(Duration sellerRelistCooldown) {
        this.sellerRelistCooldown = sellerRelistCooldown;
    }

    public Duration getBuyerCancelCooldown() {
        return buyerCancelCooldown;
    }

    public void setBuyerCancelCooldown(Duration buyerCancelCooldown) {
        this.buyerCancelCooldown = buyerCancelCooldown;
    }

    public int getMaxActiveReservations() {
        return maxActiveReservations;
    }

    public void setMaxActiveReservations(int maxActiveReservations) {
        this.maxActiveReservations = maxActiveReservations;
    }

    public int getMaxDrawWeight() {
        return maxDrawWeight;
    }

    public void setMaxDrawWeight(int maxDrawWeight) {
        this.maxDrawWeight = maxDrawWeight;
    }
}
