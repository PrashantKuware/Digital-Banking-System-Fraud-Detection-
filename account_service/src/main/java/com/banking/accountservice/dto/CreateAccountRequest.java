package com.banking.accountservice.dto;

import java.math.BigDecimal;

import com.banking.accountservice.entity.AccountType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

	@NotBlank(message= "Account holder name is required")
	 private String accoutHolderName;
	
	@NotBlank(message= "Email is required")
	@Email(message= "Invalid email formate")
	private String email; 
	
	@NotBlank(message= "Phone is required")
	private String phone;
	
	@NotBlank(message= "Account Type is required")
	 private AccountType accountType;
	
	@NotNull(message= "Initial deposit name is required")
	@Positive(message= "initial deposit must be positive")
	private BigDecimal initialDeposit;
}
