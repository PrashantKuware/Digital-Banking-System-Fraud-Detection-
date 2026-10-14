package com.banking.paymentservice.dto;

import java.math.BigDecimal;

import com.banking.paymentservice.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
	
	
	private String razorpayOrderId;
	
	private String paymentId;
	
	private BigDecimal amount;
	
	private String currency;
	
	private PaymentStatus status;
	
	private String razorpayKeyId;
}
