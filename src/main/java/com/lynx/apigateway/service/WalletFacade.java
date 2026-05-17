package com.lynx.apigateway.service;

import com.lynx.apigateway.dto.wallet.DepositRequest;
import com.lynx.apigateway.dto.wallet.WithdrawRequest;
import com.lynx.apigateway.dto.wallet.DepositResponse;
import com.lynx.apigateway.dto.wallet.WalletBalanceResponse;
import com.lynx.apigateway.dto.wallet.WalletTransactionsPageResponse;
import com.lynx.apigateway.dto.wallet.WithdrawResponse;

import java.util.UUID;

public interface WalletFacade {
    DepositResponse deposit(UUID userId, DepositRequest request);
    WithdrawResponse withdraw(UUID userId, WithdrawRequest request);
    WalletBalanceResponse getBalance(UUID userId, String currency);
    WalletTransactionsPageResponse getTransactions(UUID userId, int page, int limit);
}