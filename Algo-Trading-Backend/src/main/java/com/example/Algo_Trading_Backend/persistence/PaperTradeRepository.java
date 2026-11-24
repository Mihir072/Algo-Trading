package com.example.Algo_Trading_Backend.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperTradeRepository extends JpaRepository<PaperTradeEntity, Long> {
	List<PaperTradeEntity> findByStatusOrderByEntryTimeDesc(PaperTradeEntity.Status status);
	List<PaperTradeEntity> findTop500ByOrderByEntryTimeDesc();
	long countByUnderlyingAndStatus(String underlying, PaperTradeEntity.Status status);
	long countByEntryTimeAfter(Instant after);
	List<PaperTradeEntity> findByStatusAndUnderlyingOrderByEntryTimeAsc(PaperTradeEntity.Status status, String underlying);
	List<PaperTradeEntity> findByStatusAndEntryTimeAfter(PaperTradeEntity.Status status, Instant after);
	List<PaperTradeEntity> findByStatusAndExitTimeAfter(PaperTradeEntity.Status status, Instant after);
	boolean existsBySignal_Id(Long signalId);
	PaperTradeEntity findFirstByUnderlyingOrderByEntryTimeDesc(String underlying);
}
