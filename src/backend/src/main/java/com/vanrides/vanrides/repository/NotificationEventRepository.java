package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
}
