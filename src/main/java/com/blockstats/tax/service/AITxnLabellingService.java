package com.blockstats.tax.service;

import com.blockstats.common.mongo.delegate.TransactionsTxnTypeUpsertDelegate;
import com.blockstats.common.mongo.delegate.dto.TransactionTxnType;
import com.blockstats.common.mongo.delegate.dto.UserContext;
import com.blockstats.common.mongo.userdata.delegate.repository.TransactionsRepository;
import com.blockstats.enums.Source;
import com.blockstats.enums.SourceType;
import com.blockstats.tax.bo.AITxnLabellingBO;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.blockstats.enums.SourceType.DEX;
import static com.blockstats.enums.SourceType.DEXEVM;

@Service
@AllArgsConstructor
public class AITxnLabellingService {

    private final TransactionsRepository transactionsRepository;
    private final TransactionsTxnTypeUpsertDelegate transactionsTxnTypeUpsertDelegate;

    public void invoke(AITxnLabellingBO aITxnLabellingBO) {
        AtomicInteger count = new AtomicInteger(10);
        this.transactionsRepository
                .findAllByUserIdAndSourceTypeInAndAiTxnTypeIsNullOrderByTxnDateDesc(
                        aITxnLabellingBO.getUserId(),
                        List.of(DEX.value(), DEXEVM.value()),
                        PageRequest.of(1, 5))
                .limitRate(5)
                .buffer(5)
                .concatMap(Flux::fromIterable)
                .subscribe(transactionEntity -> {
                    if (StringUtils.isBlank(transactionEntity.getAiTxnType())) {
                        count.incrementAndGet();
                        UserContext userContext =
                                UserContext
                                        .builder()
                                        .userId(transactionEntity.getUserId())
                                        .walletId(transactionEntity.getWalletId())
                                        .sourceType(SourceType.valueOf(transactionEntity.getSource()))
                                        .source(Source.valueOf(transactionEntity.getSource()))
                                        .build();
                        TransactionTxnType transactionTxnType =
                                TransactionTxnType
                                        .builder()
                                        .aiTxnType("Transfer")
                                        .build();
                        transactionsTxnTypeUpsertDelegate.upsert(userContext, List.of(transactionTxnType));
                    }
                });
    }

}
