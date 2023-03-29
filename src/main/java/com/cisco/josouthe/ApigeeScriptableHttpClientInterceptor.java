package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
com.apigee.javascript.om.http.ScriptableHttpClient.sendRequest( URL, HTTPRequest, CallBackEvent ) returns ScriptableExchange(CallBackEvent)
com.apigee.javascript.om.callback.CallBackEvent.execute() ends the request
 */
public class ApigeeScriptableHttpClientInterceptor extends MyBaseInterceptor{
    private static final ConcurrentHashMap<Object, TransactionDictionary> transactionsMap = new ConcurrentHashMap<>();
    private final Scheduler scheduler;

    //HTTPRequest stuff:
    IReflector headers;

    //HTTPHeaders stuff:
    IReflector set;

    public ApigeeScriptableHttpClientInterceptor() {
        super();
        scheduler = Scheduler.getInstance(30000L, 120000L, transactionsMap);
        scheduler.start();

        headers = makeAccessFieldValueReflector("headers"); //type HTTPHeaders

        set = makeInvokeInstanceMethodReflector("set", String.class.getCanonicalName(), String.class.getCanonicalName());


    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] parameters) {
        TransactionDictionary transactionDictionary = null;
        switch( methodName ) {
            case "execute": { //this is a callback, find the transaction and end it
                transactionDictionary = transactionsMap.get(objectIntercepted);
                if( transactionDictionary == null ) {
                    getLogger().info("Oops, CallBackEvent not found in the transaction map, it may be unrelated to an ExitCall we made");
                    return null;
                }
                break;
            }
            case "sendRequest": { //this is the beginning of an exitcall, start it
                URL requestURL = (URL) parameters[0];
                Object httpRequest = parameters[1];
                Object callBackEvent = parameters[2];
                Transaction transaction = AppdynamicsAgent.getTransaction();
                if( isFakeTransaction(transaction) ) {
                    getLogger().info("Oops, intercepted an exitcall, but no BT is active? odd? Request URL: "+ requestURL);
                    //return null;
                    transaction = AppdynamicsAgent.startTransaction("PlaceHolder", null, EntryTypes.POJO, false);
                }
                Map<String, String> propertyMap = new HashMap<>();
                propertyMap.put("HOST", requestURL.getHost());
                ExitCall exitCall = transaction.startHttpExitCall(propertyMap, requestURL, true);
                addCorrelationHeaderToHttpRequest( exitCall.getCorrelationHeader(), httpRequest);
                transactionsMap.put(callBackEvent, new TransactionDictionary(requestURL, transaction, exitCall));
                break;
            }
            default: {
                getLogger().info("Oops, unknown method intercepted? check rules and fix the code for method name: "+ className +"."+ methodName +"()");
            }
        }
        return transactionDictionary;
    }

    private void addCorrelationHeaderToHttpRequest(String correlationHeader, Object httpRequest) {
        Object httpHeaders = getReflectiveObject(httpRequest, headers);
        if( httpHeaders == null ) {
            getLogger().info("Oops, http headers is null when trying to add correlation header");
            return;
        }
        try {
            set.execute(httpHeaders.getClass().getClassLoader(), httpHeaders, new Object[]{ AppdynamicsAgent.TRANSACTION_CORRELATION_HEADER, correlationHeader} );
        } catch (ReflectorException exception) {
            getLogger().info("Oops, exception caught while attempting to inject the correlation header on the exit call request: "+ exception.getMessage());
        }
    }

    @Override
    public void onMethodEnd(Object state, Object objectIntercepted, String className, String methodName, Object[] parameters, Throwable exception, Object returnVal) {
        if( state == null ) return; //something is out of phase, just get out
        TransactionDictionary transactionDictionary = (TransactionDictionary) state;
        if( exception != null ) {
            transactionDictionary.getBusinessTransaction().markAsError(String.format("Exception in Http Exit Call: '%s' Message: %s", transactionDictionary.getRequestURL(), exception.getMessage()));
        }
        switch( methodName ) {
            case "execute": { //this is a callback, end the exitcall
                transactionDictionary.getExitCall().end();
                transactionDictionary.setFinished(true);
                transactionsMap.remove(objectIntercepted);
                break;
            }
            case "sendRequest": { //this is the beginning of an exitcall, do nothing here
                break;
            }
            default: {
                getLogger().info("Oops, unknown method intercepted? check rules and fix the code for method name: " + className + "." + methodName + "()");
            }
        }
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.apigee.javascript.om.http.ScriptableHttpClient")
                .classMatchType(SDKClassMatchType.INHERITS_FROM_CLASS)
                .methodMatchString("sendRequest")
                .build()
        );

        rules.add(new Rule.Builder(
                "com.apigee.javascript.om.callback.CallBackEvent")
                .classMatchType(SDKClassMatchType.INHERITS_FROM_CLASS)
                .methodMatchString("execute")
                .build()
        );

        rules.add(new Rule.Builder(
                "com.apigee.javascript.om.http.ScriptableHttpClient")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("sendRequest")
                .build()
        );

        rules.add(new Rule.Builder(
                "com.apigee.javascript.om.callback.CallBackEvent")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("execute")
                .build()
        );
        return rules;
    }
}
