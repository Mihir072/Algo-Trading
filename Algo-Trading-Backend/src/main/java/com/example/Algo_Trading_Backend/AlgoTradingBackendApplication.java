package com.example.Algo_Trading_Backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class AlgoTradingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlgoTradingBackendApplication.class, args);
	}

}
