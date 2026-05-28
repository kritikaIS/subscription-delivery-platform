package com.juiceplatform.dto.wallet;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletSummaryResponse {

    private long balancePaise;
    private boolean lowBalanceWarning;
    private long lowBalanceThresholdPaise;
}
