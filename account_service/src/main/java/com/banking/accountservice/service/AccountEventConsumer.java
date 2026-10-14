package com.banking.accountservice.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j 
@RequiredArgsConstructor
public class AccountEventConsumer {

	private final AccountService service;
	
//	Consumes transaction.completed event from kafka.
//	credits  receiver account
	
	@KafkaListener(topics = "transaction.completed")
	public void comsumeTransactionCompleted(@Payload Map<String, Object> payload)
	{
		try {
			String receiverAccount = (String) payload.get("ReceiverAccountNumber");
			BigDecimal amount = new BigDecimal( payload.get("amount").toString());
			
			log.info("Crediting account: {} amount: {}", receiverAccount, amount);
			service.creditBalance(receiverAccount, amount);

		}
		catch(Exception e) {
			log.error("Error crediting account: {}", e.getMessage());
		}
	}
	
	
	 // Consumes fraud.detected event from kafka
	// Blocked the flagged account
	
	@KafkaListener(topics = "fraud.detected")
	public void comsumeFraudDetected(@Payload Map<String, Object> payload)
	{
		try {
			String accountNumber = (String) payload.get("accountNumber");
			
			log.info("Fraud Detected, Blocked account: {}", accountNumber);
			
			service.blockAccount(accountNumber);

		}
		catch(Exception e) {
			log.error("Error blocking account: {}", e.getMessage());
		}
	}
	
	
}
