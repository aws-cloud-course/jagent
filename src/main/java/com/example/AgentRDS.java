package com.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.OrderBy;
import software.amazon.awssdk.services.rds.RdsClient;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;

import static java.math.RoundingMode.HALF_UP;

@Singleton
@Slf4j
@AllArgsConstructor
@Requires(property = "metrics")
public class AgentRDS {
    private final ZabbixSender zabbixSender;
    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final RdsClient rdsClient;

    @PostConstruct
    public void init() {
        log.info("Starting RDS agent.");
    }

    //@Scheduled(fixedRate = "${schedule.rds}")
    public void start() throws IOException {
        try {
            var logStreams = getEnhancedMetricsForRds(null);
            var memory = getMemoryInfo(logStreams);
            var active = new BigDecimal(memory.get("active").asLong() / 1024.0);

            active = active.setScale(2, HALF_UP);

            var activeMem = DataObject.builder()
                .host("Zabbix DB")
                .key("active.memory")
                .value(active.toString())
                .clock(System.currentTimeMillis() / 1000)
                .build();

            var result = zabbixSender.send(activeMem);


            if (result.success()) {
                log.info("Active memory metric sent successfully.");
            } else {
                log.error("Unable to send the active memory metric");
            }
        } catch (SocketTimeoutException ex) {
            log.error("Unable to send metrics for RDS.");
            log.error("Service must be down.");
        }
    }

    private JsonNode getMemoryInfo(List<LogStream> logStreams) throws JsonProcessingException {

        return new ObjectMapper()
            .readTree(cloudWatchLogsClient.getLogEvents(
                        GetLogEventsRequest
                            .builder()
                            .logGroupName("RDSOSMetrics")
                            .logStreamName(logStreams.get(0).logStreamName())
                            .limit(1)
                            .build()
                    )
                    .events()
                    .get(0)
                    .message()
            )
            .get("memory");
    }

    private List<LogStream> getEnhancedMetricsForRds(String nextToken) {
        var logRequest = DescribeLogStreamsRequest.builder()
            .logGroupName("RDSOSMetrics")
            .orderBy(OrderBy.LAST_EVENT_TIME)
            .descending(true)
            .nextToken(nextToken)
            .build();

        return cloudWatchLogsClient.describeLogStreams(logRequest).logStreams();
    }

}