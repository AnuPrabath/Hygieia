package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.AppdynamicsApplication;
import com.capitalone.dashboard.model.Performance;
import com.capitalone.dashboard.model.PerformanceMetric;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.appdynamics.appdrestapi.RESTAccess;
import org.appdynamics.appdrestapi.data.MetricData;
import org.appdynamics.appdrestapi.data.PolicyViolation;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultAppdynamicsClient implements AppdynamicsClient {
    private static final Log LOG = LogFactory.getLog(DefaultAppdynamicsClient.class);
//    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final int NUM_MINUTES = 600; //14 days
    private static final double DEFAULT_VALUE = -1.0;
    private Map<String, Double> applicationDataMap = new HashMap<>();

    // private static final String STATUS_WARN = "WARN";
    // private static final String STATUS_CRITICAL = "CRITICAL";

//    @Autowired
    public DefaultAppdynamicsClient() {

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


    // TODO: Implement these using AppD rest api
    @Override
   public Set<AppdynamicsApplication> getApplications(RESTAccess restClient) {

        Set<AppdynamicsApplication> returnSet = new HashSet<>();
        for (org.appdynamics.appdrestapi.data.Application app : restClient.getApplications().getApplications()) {
            AppdynamicsApplication newApp = new AppdynamicsApplication();
            newApp.setAppID(String.valueOf(app.getId()));
            newApp.setAppDesc(app.getDescription());
            newApp.setAppName(app.getName());
            returnSet.add(newApp);
        }
        return returnSet;
    }

    @Override
    public Performance getPerformanceMetrics(AppdynamicsApplication application, RESTAccess restClient) {

        Performance performance = new Performance();
        try {
            buildMetricDataMap(restClient, application);
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


    private void buildMetricDataMap(RESTAccess access, AppdynamicsApplication application) throws IOException, IllegalAccessException {

        String[] metrics = new String[]{
                "Average Response Time (ms)",
                "Total Calls",
                "Calls per Minute",
                "Total Errors",
                "Errors per Minute",
                "Node Health Percent"
        };

        for (String metricName : metrics)
            applicationDataMap.put(metricName, DEFAULT_VALUE);

        //populate fields
        populateMetricFields(application.getAppName(), access);
    }

    private void buildViolationSeverityMap(String appName, RESTAccess access, long start, long end) throws IOException {

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


    private void populateMetricFields(String appName, RESTAccess access) throws IllegalAccessException, IOException {

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
            if ((metricValue = getMetricValue(appName, createPath(metricName), access, start, end)) == -1) {
                unknownMetrics.add(metricName);
                continue;
            }

            entry.setValue(metricValue);
        }

        //individually handle atypical possibilities (e.g. "Total Errors", "Node Health Percent", etc.)
        for (String metricName : unknownMetrics)
            applicationDataMap.replace(metricName, generateMetricValue(appName, metricName, access, start, end));


        buildViolationSeverityMap(appName, access, start, end);
        //testInit();
    }

    private double getMetricValue(String appName, String metricPath, RESTAccess access, long start, long end) throws IllegalAccessException {

        // generic call to appdynamics api to retrieve metric value
        List<MetricData> metricDataArr = access.getRESTGenericMetricQuery(appName, metricPath, start, end, true).getMetric_data();

        // if resulting array is empty, the metric doesn't exist--we have to calculate
        if (!metricDataArr.isEmpty())
            return metricDataArr.get(0).getSingleValue().getValue();
        return -1;
    }

    private double generateMetricValue(String appName, String metricName, RESTAccess access, long start, long end) throws IllegalAccessException {

        // we have "Errors per Minute", for example. Manipulating the names gives us a generic way to handle all totals
        if (metricName.contains("Total"))
            return NUM_MINUTES * applicationDataMap.get(totalToPerMinute(metricName));

        // must pull all of the nodes and all of the health violations.
        // 100 - (Num Violations / Num Nodes) = Node Health Percent
        if (metricName.equals("Node Health Percent"))
            return getNodeHealthPercent(appName, access, start, end);

        return -1;
    }


    private double getNodeHealthPercent(String appName, RESTAccess access, long start, long end) {

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
