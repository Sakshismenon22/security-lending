package com.example.borrower_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BorrowerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BorrowerServiceApplication.class, args);
	}

}
