package com.api.calendar;

import com.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sync_state")
public class SyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "sync_token")
    private String syncToken;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status = SyncStatus.NEVER_SYNCED;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {}

    public SyncState(User user) {
        this.user = user;
        this.status = SyncStatus.NEVER_SYNCED;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getSyncToken() { return syncToken; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public SyncStatus getStatus() { return status; }
    public String getErrorCategory() { return errorCategory; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSyncToken(String syncToken) { this.syncToken = syncToken; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public void setStatus(SyncStatus status) { this.status = status; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void markSynced(String syncToken) {
        this.syncToken = syncToken;
        this.lastSyncAt = Instant.now();
        this.status = SyncStatus.SYNCED;
        this.errorCategory = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCategory, String errorMessage) {
        this.status = SyncStatus.SYNC_FAILED;
        this.errorCategory = errorCategory;
        this.errorMessage = errorMessage;
    }

    public void markReauthRequired(String reason) {
        this.status = SyncStatus.REAUTH_REQUIRED;
        this.errorCategory = "REVOKED";
        this.errorMessage = reason;
    }
}
