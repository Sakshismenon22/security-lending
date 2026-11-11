package com.example.market_data_service;

import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.service.MarketDataService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class MarketDataServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void shouldUseIndependentTransactionPerSecurity() throws Exception {
		Method allSecuritiesBatch = MarketDataService.class.getDeclaredMethod("processAllSecurities");
		assertFalse(allSecuritiesBatch.isAnnotationPresent(Transactional.class));

		Method singleSecurity = MarketDataService.class.getDeclaredMethod("processSecurity", SecurityMaster.class);
		Transactional transactional = singleSecurity.getAnnotation(Transactional.class);
		assertNotNull(transactional);
		assertEquals(Transactional.TxType.REQUIRES_NEW, transactional.value());
	}

}
