package com.vanrides.vanrides.model;

import com.vanrides.vanrides.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_event", indexes = {
        @Index(name = "idx_notification_event_recipient", columnList = "recipient_role,recipient_user_id"),
        @Index(name = "idx_notification_event_company_id", columnList = "company_id"),
        @Index(name = "idx_notification_event_status", columnList = "status"),
        @Index(name = "idx_notification_event_created_at", columnList = "created_at")
})
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 32)
    private UserRole recipientRole;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationDispatchStatus status = NotificationDispatchStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "provider_status", length = 160)
    private String providerStatus;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public void setEventType(NotificationEventType eventType) {
        this.eventType = eventType;
    }

    public UserRole getRecipientRole() {
        return recipientRole;
    }

    public void setRecipientRole(UserRole recipientRole) {
        this.recipientRole = recipientRole;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getDataJson() {
        return dataJson;
    }

    public void setDataJson(String dataJson) {
        this.dataJson = dataJson;
    }

    public NotificationDispatchStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationDispatchStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
