package com.example;

import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static java.math.RoundingMode.HALF_UP;

@Singleton
@Slf4j
@AllArgsConstructor
@Requires(property = "metrics.zabbix")
public class AgentZabbix {
    private final ZabbixSender zabbixSender;
    private final Ec2Client ec2Client;
    private final CloudWatchClient cloudWatchClient;

    @PostConstruct
    public void init() {
        log.info("Starting Zabbix agent.");
    }

    //@Scheduled(fixedRate = "${schedule.default}")
    public void start() throws IOException {
        try {
            var describeInstancesResponse = getDescribeInstances(null);

            BigDecimal freeSpaceGB = getSystemFreeSpace();

            var volumeSizeReq = DataObject.builder()
                .host("Zabbix EC2 instance")
                .key("volume.size")
                .value(String.valueOf(1))
                .clock(System.currentTimeMillis() / 1000)
                .build();

            var freeSpaceReq = DataObject.builder()
                .host("Zabbix EC2 instance")
                .key("free.space")
                .value(String.valueOf(freeSpaceGB))
                .clock(System.currentTimeMillis() / 1000)
                .build();

            var sizeResponse = zabbixSender.send(volumeSizeReq);
            var freeSpaceResponse = zabbixSender.send(freeSpaceReq);

            if (sizeResponse.success()) {
                log.info("Volume size metric sent successfully.");
            } else {
                log.error("Unable to send the Volume size metric.");
            }

            if (freeSpaceResponse.success()) {
                log.info("Free space metric sent successfully.");
            } else {
                log.error("Unable to send the free space metric.");
            }
        } catch (SocketTimeoutException ex) {
            log.error("Unable to send metrics EC2.");
            log.error("Service must be down.");
        }
    }

    private static BigDecimal getSystemFreeSpace() {
        long freeSpace = new File("/").getFreeSpace();
        BigDecimal freeSpaceGB = new BigDecimal(freeSpace / (1024.0 * 1024.0 * 1024.0));
        freeSpaceGB = freeSpaceGB.setScale(2, HALF_UP);

        return freeSpaceGB;
    }

    private DescribeInstancesResponse getDescribeInstances(String nextToken) {
        DescribeInstancesRequest describeInstancesRequest = DescribeInstancesRequest
            .builder()
            .maxResults(5)
            .filters(Filter.builder().name("tag:Name").values("G2C-Zabbix").build())
            .nextToken(nextToken)
            .build();

        return ec2Client.describeInstances(describeInstancesRequest);
    }

}