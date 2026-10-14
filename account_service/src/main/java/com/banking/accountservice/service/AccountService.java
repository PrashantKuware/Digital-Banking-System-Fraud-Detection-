package com.banking.accountservice.service;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.springframework.stereotype.Service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
	
	private final AccountRepository repository;
	private static SecureRandom secureRandom = new SecureRandom();

	public AccountResponse createAccount( CreateAccountRequest request) {
		
		log.info("Creating account for : {}", request.getEmail());
		
		if(repository.existsByEmail(request.getEmail()))
		{
			throw new RuntimeException("Account already exist for email : "+request.getEmail());
		}
		
		Account account = new Account();
		account.setAccountHolderName(request.getAccoutHolderName());
		account.setEmail(request.getEmail());
		account.setPhone(request.getPhone());
		account.setAccountType(request.getAccountType());
		account.setAccountStatus(AccountStatus.ACTIVE);
		account.setBalance(request.getInitialDeposit());
		account.setAccountNumber(generateAccountNumber());
		account.setDailyTransactionLimit(
				request.getAccountType() == AccountType.SAVINGS 
				? new BigDecimal("10000")
				: new BigDecimal("50000")
				);
		
		Account savedAccount = repository.save(account);
		log.info("Account created: {}", savedAccount.getAccountNumber());
		return mapToResponse(savedAccount);
		
	}

	public AccountResponse getAccount(String accountNumber) {
		
		Account account = repository.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new RuntimeException("Account not found"));
		
		return mapToResponse(account);
	}

	public BigDecimal getBalance(String accountNumber) {
		Account account = repository.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new RuntimeException("Account not found"));
		
		return account.getBalance();
	}

//	 Block account - called by fraud detection service via kafka
	
	public void blockAccount(String accountNumber) {
		
		log.info("Blocking account: {}", accountNumber);
		Account account = repository.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new RuntimeException("Account not found"));
		account.setAccountStatus(AccountStatus.BLOCKED);
		repository.save(account);
		
		log.info("account blocked: {}", accountNumber);
	}

	
//	Deduct balance from sender account and called by transaction service
	
	public void deductBalance(String accountNumber, BigDecimal amount) {
		
		  log.info("Deducting balance {} from account: {} ",amount, accountNumber);
		  
		  Account account = repository.findByAccountNumber(accountNumber)
					.orElseThrow(() -> new RuntimeException("Account not found"));
		  
		  if(account.getAccountStatus() != AccountStatus.ACTIVE)
		  {
			  throw new RuntimeException("Account is not active " +accountNumber); 
		  }
		  
		  if(account.getBalance().compareTo(amount) < 0)
		  {
			  throw new RuntimeException("Insufficient finds for account " +accountNumber); 
		  
		  }
		  
		  account.setBalance(account.getBalance().subtract(amount));
		  repository.save(account);
		  
		  log.info("Balance updated, New Balance: {} ",account.getBalance());
	}

	
	public void creditBalance(String accountNumber, BigDecimal amount) {
		
		 log.info("Crediting balance {} to account: {} ",amount, accountNumber);
		  
		  Account account = repository.findByAccountNumber(accountNumber)
					.orElseThrow(() -> new RuntimeException("Account not found"));

		  account.setBalance(account.getBalance().add(amount));
		  repository.save(account);
		  
		  log.info("Balance credited, New Balance: {} ",account.getBalance());
	}
	
	private AccountResponse mapToResponse (Account account)
	{
		AccountResponse response = new AccountResponse();
		
		response.setId(account.getId());
		response.setAccountNumber(account.getAccountNumber());
		response.setAccountHolderName(account.getAccountHolderName());
		response.setEmail(account.getEmail());
		response.setPhone(account.getPhone());
		response.setAccountType(account.getAccountType());
		response.setAccountStatus(account.getAccountStatus());
		response.setBalance(account.getBalance());
		response.setDailyTransactionLimit(account.getDailyTransactionLimit());
		response.setCreatedAt(account.getCreatedAt());
		
		return response;
	}

//	generate unique 12 digit accountNumber
	private String generateAccountNumber()
	{
		String accountNumber;
		do {
			long number = secureRandom.nextLong(1000_000_000_000L);
			accountNumber = String.format("%012d", number);
			
		}
		while(repository.existsByAccountNumber(accountNumber));
		
		return accountNumber;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
