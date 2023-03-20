package com.example;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;

import static java.lang.System.err;
import static org.apache.commons.lang3.ThreadUtils.*;

@Singleton
public class Hosts {
    private static final Logger log = LoggerFactory.getLogger(Hosts.class);
    private static final int CONNECT_TIMEOUT = 1000;
    private final Ec2Client ec2Client;

    public Hosts(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    public Map<String, HostInfo> getHosts() {
        var hostsInfo = new HashMap<String, HostInfo>();
        var server = getDescribeInstances();
        if (server.hasReservations()) {
            server.reservations().forEach(reservation -> {
                if (reservation.hasInstances()) {
                    reservation.instances().forEach(instance -> {
                        if (instance.state().nameAsString().equals("running")) {
                            hostsInfo.put(instance.publicIpAddress(), new HostInfo(
                                instance.publicIpAddress(),
                                instance.instanceId()
                            ));
                        }
                    });
                }
            });
        }

        return hostsInfo;
    }

    public HostInfo getActiveNode(JSch jsch, Map<String, HostInfo> hostInfos) {
        HostInfo activeNode = null;
        try {
            while (activeNode == null) {
                for (var host : hostInfos.entrySet()) {
                    var session = getSession(jsch, "ubuntu", host.getValue().ip);
                    var lines = exec(session, "sudo zabbix_server -R ha_status | grep active | cut -b 33-41,58-80");
                    if (lines.isEmpty() || lines.get(0).contains("only in")) {
                        session.disconnect();
                        continue;
                    }
                    var arValues = lines.get(0).trim().split(" ");
                    hostInfos.get(arValues[1].split(":")[0]).name = arValues[0];
                    lines = exec(session, "sudo zabbix_server -R ha_status | grep standby | cut -b 33-41,58-80");
                    arValues = lines.get(0).trim().split(" ");
                    hostInfos.get(arValues[1].split(":")[0]).name = arValues[0];
                    session.disconnect();
                    if(Objects.isNull(host.getValue().name)){
                        continue;
                    }
                    activeNode = host.getValue();
                    break;
                }
                sleep(Duration.ofSeconds(1));
            }
        } catch (JSchException e) {
            log.error("Unable to establish the connection", e);
        } catch (Exception e) {
            log.error("An error occurred", e);
        }

        return activeNode;
    }

    public List<String> exec(Session session, String command) throws JSchException {
        var lines = new LinkedList<String>();
        var channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);
        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(err);
        channel.connect(CONNECT_TIMEOUT);

        try {
            var br = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            channel.disconnect();

            return lines;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void run(Session session, String command) throws JSchException {
        var channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);
        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(err);
        channel.connect(CONNECT_TIMEOUT);
        channel.disconnect();
    }

    public Session getSession(JSch jsch, String user, String host) throws JSchException {
        var session = jsch.getSession(user, host, 22);
        var config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(CONNECT_TIMEOUT);

        return session;
    }

    public JSch getjSch() {
        var jsch = new JSch();

        try {
            jsch.addIdentity(this.getClass().getResource("/G2C-Keypair.pem").getPath());
        } catch (JSchException e) {
            var msg = "Error opening the key.";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        return jsch;
    }

    private DescribeInstancesResponse getDescribeInstances() {
        var describeInstancesRequest = DescribeInstancesRequest
            .builder()
            //.filters(Filter.builder().name("tag:Name").values(name).build())
            .build();

        return ec2Client.describeInstances(describeInstancesRequest);
    }

    static class HostInfo {
        String ip;
        String id;
        String name;

        public HostInfo(
            String ip,
            String id
        ) {
            this.ip = ip;
            this.id = id;
        }

    }
}
