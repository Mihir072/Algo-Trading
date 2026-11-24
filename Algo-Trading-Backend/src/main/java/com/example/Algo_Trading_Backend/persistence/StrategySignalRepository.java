package com.example.Algo_Trading_Backend.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategySignalRepository extends JpaRepository<StrategySignalEntity, Long> {
	List<StrategySignalEntity> findTop100ByOrderByCapturedAtDesc();
	List<StrategySignalEntity> findByUnderlyingAndCapturedAtAfterOrderByCapturedAtDesc(String underlying, Instant after);
}
