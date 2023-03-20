package com.example;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.rds.RdsClient;

@Factory
public class AwsClientFactory {

    private String host;
    private int port;

    @Singleton
    Ec2Client ec2Client() {
        return Ec2Client.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }

    @Singleton
    RdsClient rdsClient() {
        return RdsClient.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }

    @Singleton
    CostExplorerClient costExplorerClient() {
        return CostExplorerClient.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }

    @Singleton
    CloudWatchLogsClient cloudWatchLogsClient() {
        return CloudWatchLogsClient.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }

    @Singleton
    CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }

}