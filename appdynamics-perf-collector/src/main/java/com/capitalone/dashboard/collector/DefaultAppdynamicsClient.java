package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.AppdynamicsApplication;
import com.capitalone.dashboard.model.Performance;
import com.capitalone.dashboard.model.PerformanceMetric;
import com.capitalone.dashboard.util.Supplier;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.appdynamics.appdrestapi.RESTAccess;
import org.appdynamics.appdrestapi.data.Application;
import org.appdynamics.appdrestapi.data.MetricData;
import org.appdynamics.appdrestapi.data.PolicyViolation;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAppdynamicsClient implements AppdynamicsClient {
    private static final Log LOG = LogFactory.getLog(DefaultAppdynamicsClient.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    // private static final String STATUS_WARN = "WARN";
    // private static final String STATUS_CRITICAL = "CRITICAL";
    private final RestOperations rest;
    private final HttpEntity<String> httpHeaders;

    @Autowired
    public DefaultAppdynamicsClient(Supplier<RestOperations> restOperationsSupplier, AppdynamicsSettings settings) {


        this.httpHeaders = new HttpEntity<String>(
                this.createHeaders(settings.getUsername(), settings.getPassword())
            );
        this.rest = restOperationsSupplier.get();
        AppdynamicsSettings temp = settings;
        temp.getUsername(); //temp to relieve errors
    }



    private JSONArray parseAsArray(String url) throws ParseException {
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, this.httpHeaders, String.class);
        return (JSONArray) new JSONParser().parse(response.getBody());
    }

    private long timestamp(JSONObject json, String key) {
        Object obj = json.get(key);
        if (obj != null) {
            try {
                return new SimpleDateFormat(DATE_FORMAT).parse(obj.toString()).getTime();
            } catch (java.text.ParseException e) {
                LOG.error(obj + " is not in expected format " + DATE_FORMAT, e);
            }
        }
        return 0;
    }

    private String str(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : obj.toString();
    }
    @SuppressWarnings("unused")
    private Integer integer(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : (Integer) obj;
    }

    @SuppressWarnings("unused")
    private BigDecimal decimal(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : new BigDecimal(obj.toString());
    }

    @SuppressWarnings("unused")
    private Boolean bool(JSONObject json, String key) {
        Object obj = json.get(key);
        return obj == null ? null : Boolean.valueOf(obj.toString());
    }

    /*private PerformanceMetricStatus metricStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return PerformanceMetricStatus.OK;
        }

        switch(status) {
            case STATUS_WARN:  return PerformanceMetricStatus.WARNING;
            case STATUS_CRITICAL: return PerformanceMetricStatus.CRITICAL;
            default:           return PerformanceMetricStatus.OK;
        }
    }*/

    private HttpHeaders createHeaders(String username, String password){
        HttpHeaders headers = new HttpHeaders();
        if (username != null && !username.isEmpty() &&
            password != null && !password.isEmpty()) {
          String auth = username + ":" + password;
          byte[] encodedAuth = Base64.encodeBase64(
              auth.getBytes(Charset.forName("US-ASCII"))
          );
          String authHeader = "Basic " + new String(encodedAuth);
          headers.set("Authorization", authHeader);
        }
        return headers;
    }

    // TODO: Implement these using AppD rest api
    @Override
   public List<AppdynamicsApplication> getApplications(RESTAccess access) {

        List<AppdynamicsApplication> temp = new ArrayList<>();

        for (Application app : access.getApplications().getApplications())
            temp.add(new AppdynamicsApplication(app));

        return temp;
    }

    @Override
    public Performance getPerformanceMetrics(AppdynamicsApplication application, RESTAccess access) {

        Performance performance = new Performance();

        appName = application.getAppName();
        try {
            buildMetricDataMap(access);
        } catch (IOException e) {
            LOG.error("Oops", e);
        } catch (IllegalAccessException e) {
            LOG.error("Oops", e);
        }

        for (Map.Entry<String, Double> entry : applicationDataMap.entrySet()) {
            String metricName = entry.getKey();
            Double metricValue = entry.getValue();

            PerformanceMetric metric = new PerformanceMetric();
            metric.setName(metricName);
            metric.setValue(metricValue);

            performance.getMetrics().add(metric);

        }

        return performance;

    }

    /*
    ===========================================================
    ===========================================================
     */

    private static final int NUM_MINUTES = 600; //14 days
    private static final double DEFAULT_VALUE = -1.0;
    // private final String METRIC_FILEPATH = "src\\main\\java\\com\\capitalone\\metrics.txt";
    private Map<String, Double> applicationDataMap;
    private String appName = "NA";
    //private int appID = -1;

    public Map<String, Double> getApplicationDataMap() {
        return applicationDataMap;
    }

    /*private RESTAccess getAccess() {
        final String controller = "appdyn-hqa-c01";
        final String port = "80";

        final String user =
        final String passwd =
        final String account = "customer1";
        final boolean useSSL = false;

        return new RESTAccess(controller, port, useSSL, user, passwd, account);
    }*/


    private void buildMetricDataMap(RESTAccess access) throws IOException, IllegalAccessException {

        String[] metrics = new String[]{
                "Average Response Time (ms)",
                "Total Calls",
                "Calls per Minute",
                "Total Errors",
                "Errors per Minute",
                "Node Health Percent"
        };

        applicationDataMap = new HashMap<String, Double>();


        for (String metricName : metrics)
            applicationDataMap.put(metricName, DEFAULT_VALUE);

        //populate fields
        populateMetricFields(access);
    }

    private void buildViolationSeverityMap(RESTAccess access, long start, long end) throws IOException {

        applicationDataMap.put("Error Rate Severity", 0.0);
        applicationDataMap.put("Response Time Severity", 0.0);
        List<PolicyViolation> violations = access.getHealthRuleViolations(appName, start, end).getPolicyViolations();
        for (PolicyViolation violation : violations) {

            double currErrorRateSeverity = applicationDataMap.get("Error Rate Severity");
            double currResponseTimeSeverity = applicationDataMap.get("Response Time Severity");

            // If both are already critical, it's pointless to continue
            if (currErrorRateSeverity == 2.0 && currResponseTimeSeverity == 2.0)
                return;

            double severity = violation.getSeverity().equals("CRITICAL") ? 2.0 : 1.0;

            if (violation.getName().equals("Business Transaction error rate is much higher than normal")) {
                applicationDataMap.replace("Error Rate Severity", Math.max(currErrorRateSeverity, severity));
            } else if (violation.getName().equals("Business Transaction response time is much higher than normal")) {
                applicationDataMap.replace("Response Time Severity", Math.max(currResponseTimeSeverity, severity));
            }
        }

    }

    /*private void setAppName(List<Application> apps, String appIdentifier) {

        appID = Integer.valueOf(appIdentifier);

        if (apps == null) {
            LOG.debug("Something went wrong because getting applications should be easy!");
            //System.exit(1);
            return;
        }

        // iterate through array of applications to find the one with matching ID
        for (Application app : apps) {
            if (app.getId() == appID) {
                // extract the name so that we can pull value from appdynamics
                appName = app.getName();
                return;
            }
        }

        LOG.debug("Could not find application with ID: " + appID + ".");
       // System.exit(1);
       // return;
    }

    private void setAppID(List<Application> apps, String appIdentifier) {


        appName = appIdentifier;


        if (apps == null) {
            LOG.debug("Something went wrong because getting applications should be easy!");
           // System.exit(1);
            return;
        }

        for (Application app : apps) {
            if (app.getName() == appName) {
                appID = app.getId();
                return;
            }
        }

        LOG.debug("Could not find application with Name: " + appName + ".");
       // System.exit(1);

    }*/

    private void populateMetricFields(RESTAccess access) throws IllegalAccessException, IOException {

        //set boundaries. 2 weeks (20160 minutes), in this case.
        Calendar cal = Calendar.getInstance();
        long end = cal.getTimeInMillis();
        cal.add(Calendar.MINUTE, -NUM_MINUTES);
        long start = cal.getTimeInMillis();

        //metrics that need to be calculated (some "totals", "percents", etc. aren't provided by appdynamics)
        List<String> unknownMetrics = new ArrayList<>();

        //contains the names of the metrics requested by user
        for (Map.Entry<String, Double> entry : applicationDataMap.entrySet()) {
            String metricName = entry.getKey();

            double metricValue;
            // uses appdynamics api to obtain value. If it returns -1, it isn't a valid metric name--we have to calculate it.
            // "createPath" allows for generic code (e.g. "Total Calls" -> "Overall Application Performance|Total Calls"
            if ((metricValue = getMetricValue(createPath(metricName), access, start, end)) == -1) {
                unknownMetrics.add(metricName);
                continue;
            }

            entry.setValue(metricValue);
        }

        //individually handle atypical possibilities (e.g. "Total Errors", "Node Health Percent", etc.)
        for (String metricName : unknownMetrics)
            applicationDataMap.replace(metricName, generateMetricValue(metricName, access, start, end));


        buildViolationSeverityMap(access, start, end);
        //testInit();
    }

    private double getMetricValue(String metricPath, RESTAccess access, long start, long end) throws IllegalAccessException {

        // generic call to appdynamics api to retrieve metric value
        List<MetricData> metricDataArr = access.getRESTGenericMetricQuery(appName, metricPath, start, end, true).getMetric_data();

        // if resulting array is empty, the metric doesn't exist--we have to calculate
        if (!metricDataArr.isEmpty())
            return metricDataArr.get(0).getSingleValue().getValue();
        return -1;
    }

    private double generateMetricValue(String metricName, RESTAccess access, long start, long end) throws IllegalAccessException {

        // we have "Errors per Minute", for example. Manipulating the names gives us a generic way to handle all totals
        if (metricName.contains("Total"))
            return NUM_MINUTES * applicationDataMap.get(totalToPerMinute(metricName));

        // must pull all of the nodes and all of the health violations.
        // 100 - (Num Violations / Num Nodes) = Node Health Percent
        if (metricName.equals("Node Health Percent"))
            return getNodeHealthPercent(access, start, end);

        return -1;
    }


    private double getNodeHealthPercent(RESTAccess access, long start, long end) {

        //get # of violations, divide by # of nodes
        int numNodes = (access.getNodesForApplication(appName).getNodes()).size();
        int numViolations = (access.getHealthRuleViolations(appName, start, end)).getPolicyViolations().size();

        return 100.0 - (numViolations / numNodes);

    }

    private String totalToPerMinute(String currField) {
        return currField.replace("Total ", "") + " per Minute";
    }

    private String createPath(String currMember) {

        // "Open", "Close" part is deprecated. Needed when using reflection. Probably not anymore.
        return "Overall Application Performance|" + currMember.replace("OPEN", "(").replace("CLOSE", ")");
    }
/*
    private void testInit() throws IllegalAccessException {
        applicationDataMap.forEach((k, v) -> LOG.debug(k + ": " + v));
    }*/
}
