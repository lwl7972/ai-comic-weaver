package com.aicomic.repository;

import com.aicomic.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByKey(String key);
}
