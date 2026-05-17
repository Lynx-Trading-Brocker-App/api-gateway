package com.lynx.apigateway.dto.wallet;

import java.util.List;

public record WalletTransactionHistoryResult(
        List<WalletTransactionDto> transactions,
        long totalRecords,
        int currentPage,
        int totalPages,
        int limit
) {
}
