package com.example.Algo_Trading_Backend.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionSnapshotRepository extends JpaRepository<OptionSnapshotEntity, Long> {
	List<OptionSnapshotEntity> findTop100ByUnderlyingOrderByCapturedAtDesc(String underlying);
	List<OptionSnapshotEntity> findByUnderlyingAndCapturedAtLessThanEqualOrderByCapturedAtAsc(
			String underlying, Instant capturedAt);
}
