package com.example;

import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Objects;

import static java.math.RoundingMode.HALF_UP;

@Singleton
@Slf4j
@AllArgsConstructor
public class AgentCost {
    private final ZabbixSender zabbixSender;
    private final CostExplorerClient costExplorerClient;
    private final ZabbixApiClient zabbixApiClient;

    @PostConstruct
    public void init() {
        log.info("Starting Cost agent.");
    }

    //@Scheduled(fixedRate = "${schedule.cost}")
    public void start() throws IOException {
        try {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder().start("2023-01-01").end("2023-02-28").build())
                .granularity("DAILY")
                .groupBy(
                    GroupDefinition.builder()
                    .type(GroupDefinitionType.DIMENSION)
                    .key("SERVICE")
                    .build()
                )
                .metrics("BlendedCost")
                .build();

            var cost = costExplorerClient.getCostAndUsage(request);

            var map = new HashMap<String, BigDecimal>();

            for (var result : cost.resultsByTime()) {
                if(result.groups() != null && !result.groups().isEmpty()){
                    for (var group : result.groups()) {
                        var metrics = group.metrics();
                        var blendedCost = metrics.get("BlendedCost");
                        if(!blendedCost.amount().equals("0")){
                            var key = group.keys().get(0);
                            var entry = map.get(key);
                            if(Objects.nonNull(entry)){
                                var newAmount = entry.add(new BigDecimal(blendedCost.amount()));
                                newAmount = newAmount.setScale(4, HALF_UP);
                                map.put(key, newAmount);
                            } else {
                                var amount = new BigDecimal(blendedCost.amount());
                                amount = amount.setScale(4, HALF_UP);
                                map.put(key, amount);
                            }
                        }
                    }
                }
            }

            for (var key: map.keySet()) {
                zabbixApiClient.sendCost(key);
                var amount = DataObject.builder()
                    .host("Cost")
                    .key(key.toLowerCase().replace(" ","") + "amount")
                    .value(map.get(key).toString())
                    .clock(System.currentTimeMillis() / 1000)
                    .build();
                var result = zabbixSender.send(amount);
                if (result.success()) {
                    log.info("Active memory metric sent successfully.");
                } else {
                    log.error("Unable to send the active memory metric");
                }
            }
        } catch (SocketTimeoutException ex) {
            log.error("Unable to send metrics for COST.");
            log.error("Service must be down.");
        }
    }

}