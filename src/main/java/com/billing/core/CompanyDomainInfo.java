package com.billing.core;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CompanyDomainInfo {

    public static Map<String, JSONObject> domainInfoJSON = new LinkedHashMap<String, JSONObject>();

    static {
        try {
            if (!Registry.IS_ONLINE) {
                domainInfoJSON.put("localhost:9009", getJSONOBJ("billing_common", "", "v2", "billing_common", "billing_common", "jdbc:mysql://localhost:3306/", ""));
                domainInfoJSON.put("localhost:5173", getJSONOBJ("billing_common", "", "v2", "billing_common", "billing_common", "jdbc:mysql://localhost:3306/", ""));
                domainInfoJSON.put("acme.localhost:9009", getJSONOBJ("billing_company_acme", "", "v2", "acme", "acme", "jdbc:mysql://localhost:3306/", "{'company_name':'Acme Demo','company_code':'acme'}"));
                domainInfoJSON.put("acme.localhost:5173", getJSONOBJ("billing_company_acme", "", "v2", "acme", "acme", "jdbc:mysql://localhost:3306/", "{'company_name':'Acme Demo','company_code':'acme'}"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject getJSONOBJ(String database, String timezone, String version, String companyCode, String companyGroupId, String databaseIp, String companyDetail) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("database", database);
        json.put("timezone", timezone);
        json.put("version", version);
        json.put("company_code", companyCode);
        json.put("company_group_id", companyGroupId);
        json.put("databaseip", databaseIp);
        json.put("company_detail", companyDetail);
        return json;
    }

    public static String getDatabaseName(String domain) {
        if (domain == null) {
            return null;
        }
        JSONObject json = domainInfoJSON.get(domain);
        if (json != null) {
            return json.optString("database");
        }
        // try without port
        String domainWithoutPort = domain.split(":")[0];
        for (Map.Entry<String, JSONObject> entry : domainInfoJSON.entrySet()) {
            String key = entry.getKey().split(":")[0];
            if (key.equalsIgnoreCase(domainWithoutPort)) {
                return entry.getValue().optString("database");
            }
        }
        // try subdomain extraction: acme.bizio.in -> check acme
        if (domain.contains(".")) {
            String subdomain = domain.split("\\.")[0];
            for (Map.Entry<String, JSONObject> entry : domainInfoJSON.entrySet()) {
                JSONObject val = entry.getValue();
                if (subdomain.equalsIgnoreCase(val.optString("company_code"))) {
                    return val.optString("database");
                }
            }
        }
        return null;
    }
}
