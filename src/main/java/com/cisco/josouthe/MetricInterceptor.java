package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.MetricPublisher;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MetricInterceptor extends MyBaseInterceptor {
    private static final String THREAD_NAME = "AppDynamics Apigee Metric Collector";
    //private ScheduledExecutorService scheduler = null;
    private Scheduler scheduler = null;
    private ConcurrentHashMap<String, Collector> metricMap = null;

    IReflector getGroup, getName, getScope, getType; // com.yammer.metrics.core.MetricName
    IReflector count; //Long com.yammer.metrics.core.Counter.count()
    IReflector value; // T, Long, Integer, ...
    IReflector mean, max, min;//Doubles

    public MetricInterceptor() {
        super();

        metricMap = new ConcurrentHashMap<>();
        scheduler = new Scheduler(30000L, metricMap );
        scheduler.start();
        getGroup = makeInvokeInstanceMethodReflector("getGroup"); //String
        getName = makeInvokeInstanceMethodReflector("getName"); //String
        getScope = makeInvokeInstanceMethodReflector("getScope"); //String
        getType = makeInvokeInstanceMethodReflector("getType"); //String
        count = makeInvokeInstanceMethodReflector("count" ); //Long
        value = makeInvokeInstanceMethodReflector( "value" ); //(T)
        mean = makeInvokeInstanceMethodReflector( "mean" ); //Double
        max = makeInvokeInstanceMethodReflector( "max" ); //Double
        min = makeInvokeInstanceMethodReflector( "min" ); //Double
    }

    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add( new Rule.Builder(
                "com.apigee.metrics.Metrics")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("new")
                .methodStringMatchType(SDKStringMatchType.STARTSWITH)
                .build()
        );
        rules.add( new Rule.Builder(
                "com.apigee.metrics.Metrics")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("removeMetric")
                .withParams(String.class.getCanonicalName(), String.class.getCanonicalName(), String.class.getCanonicalName(), String.class.getCanonicalName())
                .build()
        );

        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {

        return null;
    }

    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        /*
        public Counter newCounter(String module, String type, String name) {
            return this.registry.newCounter(new MetricName(module, type, name));
        }

        public Counter newCounter(String module, String type, String name, String scope) {
            return this.registry.newCounter(new MetricName(module, type, name, scope));
        }
         */
        this.getLogger().debug("MetricInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
        if( methodName.equals("removeMetric") ) {
            removeMetric( getMetricName(params) );
            return;
        }

        Object metric = returnVal;
        addMetricToScheduler( metric, getMetricName(params), methodName );
        this.getLogger().debug("MetricInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }

    private String getMetricName( Object[] params ) {
        Object group = null, type = null, name = null, scope = null;

        if( params.length == 2 ) {
            Object metricName = params[0];
            group = getReflectiveString(metricName, getGroup, null);
            type = getReflectiveString(metricName, getType, null);
            name = getReflectiveString(metricName, getName, null);
            scope = getReflectiveString(metricName, getScope, null);
        } else {
            group = params[0];
            type = params[1];
            name = params[2];
            scope = null;
            if (params.length > 3 && params[3] instanceof String) scope = params[3];
        }
        StringBuilder metricName = new StringBuilder("Custom Metrics|Apigee|");
        metricName.append(group).append("|");
        metricName.append(type).append("|");
        metricName.append(name);
        if( scope != null ) {
            metricName.append("|").append(scope);
        }
        return metricName.toString();
    }

    private synchronized void addMetricToScheduler(Object metric, String name, String type) {
        //ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay( new Collector(name, metric, type), 15000, 30000, TimeUnit.MILLISECONDS);
                //scheduleAtFixedRate( new Collector(name, metric, type), 15, 30, TimeUnit.SECONDS);

        metricMap.put(name, new Collector( name, metric, type));
    }

    private synchronized void removeMetric( String name ) {
        getLogger().debug("MetricInterceptor.removeMetric() Cancelling collector for "+ name);
        metricMap.remove(name);
    }

    public class Collector {
        String name, type;
        Object metric;
        MetricPublisher publisher;

        public Collector( String name, Object metric, String type) {
            this.name = name;
            this.metric = metric;
            this.type = type;
            this.publisher = AppdynamicsAgent.getMetricPublisher();
            getLogger().debug("MetricInterceptor.Collector<init> Scheduling collector for "+ name +" of type: "+ type);
        }

        public void run() {

            switch(type) {
                case "newCounter": {
                    Long val = getReflectiveLong( metric, count);
                    if( val == null ) return;
                    this.publisher.reportMetric( name, val, "OBSERVATION", "CURRENT", "COLLECTIVE");
                    break;
                }
                case "newGauge": {
                    Long val = getReflectiveLong( metric, value );
                    if( val == null ) return;
                    this.publisher.reportMetric( name, val, "OBSERVATION", "CURRENT", "COLLECTIVE");
                    break;
                }
                case "newHistogram": {
                    Long meanVal = getReflectiveLong( metric, mean);
                    Long countVal = getReflectiveLong( metric, count);
                    Long minVal = getReflectiveLong( metric, min);
                    Long maxVal = getReflectiveLong( metric, max );
                    if( meanVal == null || countVal == null || minVal == null || maxVal == null ) return;
                    this.publisher.reportMetric( name, meanVal, countVal, minVal, maxVal, "AVERAGE", "AVERAGE", "COLLECTIVE" );
                    break;
                }
                case "newMeter": {
                    //TODO http://javadox.com/com.yammer.metrics/metrics-core/2.2.0/com/yammer/metrics/core/Meter.html
                }
                case "newTimer": {
                    //TODO http://javadox.com/com.yammer.metrics/metrics-core/2.2.0/com/yammer/metrics/core/Timer.html
                    //may be redundant when BT is configured
                }
                default: return;
            }
        }
    }

    class Scheduler extends Thread {
        ConcurrentHashMap<String, Collector> map = null;
        long sleepTime = 15000;
        public Scheduler( long sleepTimeInput, ConcurrentHashMap<String, Collector> collectorConcurrentHashMap ) {
            map = collectorConcurrentHashMap;
            if( sleepTimeInput > sleepTime ) sleepTime = sleepTimeInput; //safety check, we aren't going faster than this
            setDaemon(true);
            try {
                setPriority( (int)getPriority()/2 );
            } catch (Exception e) {
                //we tried, no op
            }
            setName(THREAD_NAME);
        }

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            //noinspection InfiniteLoopStatement
            while(true) {
                for (Collector collector : map.values()) {
                    collector.run();
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    //no op
                }
            }
        }
    }
}
