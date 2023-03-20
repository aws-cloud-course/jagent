package com.example;

import com.alibaba.fastjson.JSONObject;
import io.github.hengyunabc.zabbix.api.DefaultZabbixApi;
import io.github.hengyunabc.zabbix.api.Request;
import io.github.hengyunabc.zabbix.api.RequestBuilder;
import io.github.hengyunabc.zabbix.api.ZabbixApi;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ZabbixApiClient {
    private String url = "http://34.200.134.102/zabbix/api_jsonrpc.php";
    private ZabbixApi zabbixApi = new DefaultZabbixApi(url);
    @Value("${zabbix.user}")
    private String user;
    @Value("${zabbix.pass}")
    private String pass;
    public void sendCost(
        String name
    ) {
        zabbixApi.init();
        boolean loggedIn = zabbixApi.login("Admin", "22146522");
        var filter = new JSONObject();
        filter.put("host", new String[]{"CostTemplate"});

        var templateResp = zabbixApi.call(
            RequestBuilder
            .newBuilder()
            .method("template.get")
            .paramEntry("filter", filter)
            .build()
        );

        var templateid = templateResp.getJSONArray("result").getJSONObject(0).getString("templateid");

        zabbixApi.call(
            RequestBuilder
            .newBuilder()
            .method("item.create")
            .paramEntry("name", name)
            .paramEntry("key_", name.toLowerCase().replace(" ","") + "amount")
            .paramEntry("hostid", templateid)
            .paramEntry("type", 2)
            .paramEntry("value_type", 0)
            .paramEntry("history", "1d")
            .paramEntry("trends", "1d")
            .build()
        );

    }

    public void get() {
        zabbixApi.init();
        boolean loggedIn = zabbixApi.login("Admin", "22146522");
        var filter = new JSONObject();
        filter.put("host", new String[]{"Cost"});
        var template = RequestBuilder
            .newBuilder()
            .method("template.get")
            .paramEntry("filter", filter)
            .build();
        var templateResp = zabbixApi.call(template);
        var hostid = templateResp.getJSONArray("result").getJSONObject(0).getString("templateid");

        var templateCreation = RequestBuilder
            .newBuilder()
            .method("item.create")
            .paramEntry("name", "Cloud Watch")
            .paramEntry("key_", "amount")
            .paramEntry("hostid", hostid)
            .paramEntry("type", 2)
            .paramEntry("value_type", 4)
            .paramEntry("history", "1d")
            .build();

        zabbixApi.call(templateCreation);

        filter = new JSONObject();
        filter.put("host", new String[]{"Cost"});
        Request getRequest = RequestBuilder
            .newBuilder()
            .method("host.get")
            .paramEntry("filter", filter)
            .build();
        JSONObject getResponse = zabbixApi.call(getRequest);
        hostid = getResponse.getJSONArray("result").getJSONObject(0).getString("hostid");
        /*
            getRequest = RequestBuilder
                    .newBuilder()
                    .method("host.get")
                    .paramEntry("output", new String[]{"hostid","host"})
                    .paramEntry("selectInterfaces", new String[]{"interfaceid","ip"})
                    .build();
            getResponse = zabbixApi.call(getRequest);
            System.out.println(getResponse);*/

            /*getRequest = RequestBuilder
                    .newBuilder()
                    .method("item.get")
                    .paramEntry("output", "extend")
                    .paramEntry("hostids", hostid)
                    .build();
            getResponse = zabbixApi.call(getRequest);
            System.out.println(getResponse);
        */
        getRequest = RequestBuilder
            .newBuilder()
            .method("item.update")
            //.paramEntry("master_itemid", "44907")
            .paramEntry("itemid", "44976")
            .paramEntry("lastvalue", 10)
            //.paramEntry("status", 0)
            .build();

        getResponse = zabbixApi.call(getRequest);
    }

}