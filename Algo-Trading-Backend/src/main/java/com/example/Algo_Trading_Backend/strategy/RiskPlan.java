package com.example.Algo_Trading_Backend.strategy;

import java.math.BigDecimal;

public record RiskPlan(
		BigDecimal entry,
		BigDecimal stopLoss,
		BigDecimal target,
		BigDecimal riskPoints,
		BigDecimal rewardPoints,
		BigDecimal rewardRisk,
		BigDecimal atrStop,
		BigDecimal oiStop,
		BigDecimal oiWall) {
}
