package com.banking.transactionservice.entity;

 
// Transaction lifecycle flow
// 
// PENDING -> PROCESSING -> COMPLETED (clean transaction)
//						 -> PENDING_VERIFICATION (suspecious detected)
//						 			-> COMPLETED (verified)
//									-> FLAGGED (SAGA REFUND)
//						 -> FAILED
//						 -> FLAGGED

public enum TransactionStatus {

	PENDING,
	PROCESSING,
	PENDING_VERIFICATION,
	COMPLETED,
	FAILED,
	FLAGGED
}
