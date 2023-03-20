package com.example;

import com.example.Hosts.HostInfo;
import com.jcraft.jsch.JSchException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesResponse;

import javax.annotation.PostConstruct;
import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.math.RoundingMode.HALF_UP;

@Singleton
@Slf4j
@RequiredArgsConstructor
@Requires(property = "chaos")
public class ChaosAgent {
    private final Ec2Client ec2Client;
    private final CloudWatchClient cloudWatchClient;
    private final Hosts hosts;

    @PostConstruct
    public void init() {
        log.info("Starting System chaos engineering");
        pressEnterToContinue();
    }

    @Scheduled(fixedRate = "1m")
    public void start() {
        try {

            var jsch = hosts.getjSch();
            var hostsInfos = hosts.getHosts();

            if (hostsInfos.isEmpty()) {
                log.info("Nothing to do.");
                return;
            }

            HostInfo activeNode = hosts.getActiveNode(jsch, hostsInfos);

            if (Objects.isNull(activeNode)) {
                log.info("Zabbix server is down.");
                return;
            }

            log.info("Starting HA test.");

            log.info("Shutting down active node " + activeNode.name + "");
            var session = hosts.getSession(jsch, "ubuntu", activeNode.ip);
            hosts.exec(session, "sudo systemctl restart zabbix-server zabbix-agent apache2");
            log.info(activeNode.name + " is now standby.");
            session.disconnect();

            activeNode = hosts.getActiveNode(jsch, hostsInfos);
            log.info(activeNode.name + " is now active.");
            log.info("Please verify.");
            pressEnterToContinue();

            log.info("Shutting down active node " + activeNode.name + "");
            session = hosts.getSession(jsch, "ubuntu", activeNode.ip);
            hosts.exec(session, "sudo systemctl restart zabbix-server zabbix-agent apache2");
            session.disconnect();

            activeNode = hosts.getActiveNode(jsch, hostsInfos);
            log.info(activeNode.name + " is now active.");
            log.info("Please verify.");
            pressEnterToContinue();

            log.info("Starting CPU high usage test.");

            for (var host : hostsInfos.entrySet()) {
                session = hosts.getSession(jsch, "ubuntu", host.getValue().ip);
                log.info("Starting CPU high usage on " + host.getValue().name);
                hosts.run(session, "stress --cpu 1 --timeout 300 > /dev/null &");
                log.info("Wait a few minutes to receive the email.");
                pressEnterToContinue();
                log.info("Clearing out alert on " + host.getValue().name);
                hosts.run(session, "killall stress");
                pressEnterToContinue();
                session.disconnect();
            }

        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    private void pressEnterToContinue() {
        log.info("Press Enter key to continue...");
        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BigDecimal getSystemFreeSpace() {
        long freeSpace = new File("/").getFreeSpace();
        BigDecimal freeSpaceGB = new BigDecimal(freeSpace / (1024.0 * 1024.0 * 1024.0));
        freeSpaceGB = freeSpaceGB.setScale(2, HALF_UP);

        return freeSpaceGB;
    }

    private DescribeVolumesResponse getDescribeVolumesResponse(String nextToken) {
        DescribeVolumesRequest volumesRequest = DescribeVolumesRequest
            .builder()
            .maxResults(5)
            .nextToken(nextToken)
            .build();

        return ec2Client.describeVolumes(volumesRequest);
    }

    public void listMetrics() {
        boolean done = false;
        String nextToken = null;
        try {
            while (!done) {
                ListMetricsResponse response;
                if (nextToken == null) {
                    ListMetricsRequest request = ListMetricsRequest.builder()
                        .namespace("AWS/Lambda")
                        .build();

                    response = cloudWatchClient.listMetrics(request);
                } else {
                    ListMetricsRequest request = ListMetricsRequest.builder()
                        .namespace("AWS/Lambda")
                        .nextToken(nextToken)
                        .build();

                    response = cloudWatchClient.listMetrics(request);
                }

                for (Metric metric : response.metrics()) {
                    System.out.printf("Retrieved metric %s %s", metric.metricName(), metric.dimensions());
                    System.out.println();
                }

                if (response.nextToken() == null) {
                    done = true;
                } else {
                    nextToken = response.nextToken();
                }
            }

        } catch (CloudWatchException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public void getMetricRequest() {
        Instant startTime = Instant.now().minus(Duration.ofMinutes(500));
        Instant endTime = Instant.now();
        List<MetricDataQuery> metricDataQueries = new ArrayList<>();
        metricDataQueries.add(MetricDataQuery.builder()
            .id("m1")
            .metricStat(MetricStat.builder()
                .metric(Metric.builder()
                    .namespace("AWS/Lambda")
                    .dimensions(Dimension.builder()
                        .name("FunctionName")
                        .value("agent_lambda")
                        .build())
                    .metricName("Invocations")
                    .build())
                .period(60 * 3)
                .stat("SampleCount")
                .build())
            .returnData(true)
            .build());

        GetMetricDataRequest request = GetMetricDataRequest.builder()
            .startTime(startTime)
            .endTime(endTime)
            .metricDataQueries(metricDataQueries)
            .build();


        GetMetricDataResponse dbInstancesResponse = cloudWatchClient.getMetricData(request);
    }

}