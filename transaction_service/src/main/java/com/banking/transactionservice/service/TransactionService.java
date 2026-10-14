package com.banking.transactionservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor 
public class TransactionService {
	
	private final TransactionRepository repository;
	private final AccountServiceClient accountServiceClient;
	
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final RedisTemplate<String, String> redisTemplate;
	
	private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
	private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
	private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
	private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";
	
	
//	SAGA step 1 : initiate transfer
//	Deduct from sender via feign
//	Saves transaction as PROCESSING
//	publish event to kafka for fraud check
//	Returns
//	@param request
//	@return

	public TransactionResponse transfer(@Valid TransferRequest request) {
		
		log.info("SAGA start - transfer: {} -> {} amount: {}",
				request.getSenderAccountNumber(),
				request.getReceiverAccountNumber(),
				request.getAmount());
		
//		SAGA step 1 : Deduct from sender
		accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());
		
		Transaction transaction = new Transaction();
		transaction.setSenderAccontNmber(request.getSenderAccountNumber());
		transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
		transaction.setAmount(request.getAmount());
		transaction.setType(TransactionType.TRANSFER);
		transaction.setStatus(TransactionStatus.PROCESSING);
		transaction.setDescription(request.getDescription());
		transaction.setReferenceNumber(UUID.randomUUID().toString());
		
		Transaction savedTransaction = repository.save(transaction);
		log.info("Transaction saved as Processing: {}", savedTransaction.getId());
		
		
//		SAGA step 2 : publish event to kafka for fraud check
		
		TransactionInitiatedEvent event = new TransactionInitiatedEvent(
				savedTransaction.getId(),
				savedTransaction.getReceiverAccountNumber(),
				savedTransaction.getSenderAccontNmber(),
				savedTransaction.getAmount(),
				savedTransaction.getDescription()
				);
		
		kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
		log.info("SAGA step 2 : TransactionInitiatedEvent published : {}", savedTransaction.getId());
		
		return mapToResponse(savedTransaction);
	}

	public TransactionResponse getTransaction(String transactionId) 
	{
		return mapToResponse(repository.findById(transactionId)
				.orElseThrow(() -> new RuntimeException("Transaction not found :"+transactionId)));
		
	}

	public List<TransactionResponse> getTransactionHistory(String accountNumber) 
	{
		return repository
				.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
				.stream()
				.map(this::mapToResponse)
				.collect(Collectors.toList());
	}

	public TransactionResponse verifyOtp(String transactionId, String otp) 
	{
		log.info("OTP verification for the transaction: {}", transactionId);
		
		Transaction transaction = repository.findById(transactionId)
					.orElseThrow(() -> new RuntimeException("Transaction not found "+ transactionId));
		
		String otpKey = "verification:otp"+transactionId;
		String storedOtp = redisTemplate.opsForValue().get(otpKey);
		
		if(storedOtp == null)
		{
//			OTP expired
			log.warn("OTP expired for transaction: {}",transactionId);
			compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
			return mapToResponse(transaction);
		}
		
		if(!storedOtp.equals(otp))
		{
//			Block account and refund
			log.warn("wrong OTP - blocking account and refunding: {}",transactionId);
			redisTemplate.delete(otpKey);
			blockAccountAndCompensate(transaction,
					"Wrong OTP entered - transaction cancelled,"+
					"Account blocked for security");
			
			return mapToResponse(transaction);
		}
		
//		OTP correct - complete transaction
		log.info("OTP verified - completing transaction: {}",transactionId);
		redisTemplate.delete(otpKey);
		completeTransaction(transaction);
		return mapToResponse(transaction);
	}

	
	private void compensateTransaction(Transaction transaction, String reason)
	{
		log.warn("SAGA compensation - refunding: {} amount: {}",
				 transaction.getSenderAccontNmber(),
				 transaction.getAmount());
		
//		credit money back to sender synchronously
		accountServiceClient.creditBalance(transaction.getSenderAccontNmber(),
				transaction.getAmount());
		
		transaction.setStatus(TransactionStatus.FLAGGED);
		transaction.setFailureReason(" - SAGA compensation executed, amount refunded at "+ LocalDateTime.now());
		
		repository.save(transaction);
		
//		Publish refund event - Notification service will alert user
		Map<String, Object> refundEvent = new HashMap<>();
		refundEvent.put("transactionId", transaction.getId());
		refundEvent.put("senderAccountNumber", transaction.getSenderAccontNmber());
		refundEvent.put("amount", transaction.getAmount());
		refundEvent.put("reason", reason);
		
		kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);
		
		log.info("SAGA compensation complete - {} refunded to {}",
				transaction.getAmount(), transaction.getSenderAccontNmber());
		
	}
	
	private void blockAccountAndCompensate(Transaction transaction, String reason)
	{
//		Publisg fraud.detected -> Account service will block account
		Map<String, Object> fraudEvent = new HashMap<>();
		fraudEvent.put("transactionId", transaction.getId());
		fraudEvent.put("AccountNumber", transaction.getSenderAccontNmber());
		fraudEvent.put("reason", reason);
		
		kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccontNmber(), fraudEvent);
		
		log.warn("fraud.detected publish - account: {} will be blocked, Kindly contact to bank",
				 transaction.getSenderAccontNmber());
		
//		SAGA Compensation - refund sender
		compensateTransaction(transaction, reason);
	}
	
	private void completeTransaction(Transaction transaction)
	{
		transaction.setStatus(TransactionStatus.COMPLETED);
		transaction.setCompletedAt(LocalDateTime.now());
		repository.save(transaction);
		
		TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
				transaction.getId(),
				transaction.getSenderAccontNmber(),
				transaction.getReceiverAccountNumber(),
				transaction.getAmount(),
				transaction.getDescription()
				);
		
		kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);
		
		log.info("SAGA complete - Transaction {} completed", transaction.getId());
	}
	
	private TransactionResponse mapToResponse(Transaction savedTransaction)
	{
		TransactionResponse response = new TransactionResponse();
		response.setId(savedTransaction.getId());
		response.setSenderAccontNmber(savedTransaction.getSenderAccontNmber());
		response.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
		response.setAmount(savedTransaction.getAmount());
		response.setType(savedTransaction.getType());
		response.setStatus(savedTransaction.getStatus());
		response.setDescription(savedTransaction.getDescription());
		response.setReferenceNumber(savedTransaction.getReferenceNumber());
		response.setFailureReason(savedTransaction.getFailureReason());
		response.setCreatedAt(savedTransaction.getCreatedAt());
		response.setCompletedAt(savedTransaction.getCompletedAt());
		
		return response;
	}

	public void processCleanResult(String transactionId) 
	{
		Transaction transaction = repository.findById(transactionId)
				.orElseThrow(() -> new RuntimeException("Transaction not found "+ transactionId));
		
		if(transaction.getStatus() != TransactionStatus.PROCESSING)
		 {
			 log.warn("Transaction {} not processing - skipping", transactionId);
			 return;
		 }
		
		completeTransaction(transaction);
		
	}

	
}
