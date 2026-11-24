package com.example.Algo_Trading_Backend.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FuturesCandleRepository extends JpaRepository<FuturesCandleEntity, Long> {
	List<FuturesCandleEntity> findByInstrumentTokenAndTimestampBetweenOrderByTimestampAsc(
			String instrumentToken, Instant start, Instant end);
}
