package com.example.Algo_Trading_Backend.api;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Algo_Trading_Backend.backtest.BacktestObservation;
import com.example.Algo_Trading_Backend.backtest.BacktestService;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {
	private final BacktestService backtests;

	public BacktestController(BacktestService backtests) {
		this.backtests = backtests;
	}

	@PostMapping
	public BacktestService.Result run(@RequestBody List<BacktestObservation> observations) {
		return backtests.run(observations);
	}
}
