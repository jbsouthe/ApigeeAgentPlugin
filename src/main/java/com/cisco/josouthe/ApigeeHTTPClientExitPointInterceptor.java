package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApigeeHTTPClientExitPointInterceptor extends MyBaseInterceptor{

    IReflector methodAttribute, hostAttribute, urlAttribute, headersAttribute; // com.apigee.protocol.http.msg.HTTPRequest
    IReflector nameAttribute; // com.apigee.protocol.http.msg.HTTPMethod.name
    IReflector setHeader; // com.apigee.protocol.http.msg.HTTPHeaders.set( String name, String value )

    public ApigeeHTTPClientExitPointInterceptor() {
        super();

        methodAttribute = makeAccessFieldValueReflector("requestMethod"); //HTTPMethod
        hostAttribute = makeAccessFieldValueReflector("host"); //String
        urlAttribute = makeAccessFieldValueReflector("requestUrl"); //String
        headersAttribute = makeAccessFieldValueReflector("headers"); //HTTPHeaders

        nameAttribute = makeAccessFieldValueReflector("name"); //String

        setHeader = makeInvokeInstanceMethodReflector( "set", new String[]{String.class.getCanonicalName(), String.class.getCanonicalName()}); //HTTPHeaders.set(String,String)
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        //public void send(HTTPRequest request, String messageId, boolean discardRequest, MessageListener requestListener, MessageListener responseListener) {
        rules.add(new Rule.Builder(
                "com.apigee.protocol.http.HTTPClient$Context")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("send").build());

        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodBegin() start");
        Object httpRequest = params[0];
        Transaction transaction = AppdynamicsAgent.getTransaction();
        this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodBegin() beginning exit call for transaction uuid: "+ transaction.getUniqueIdentifier() );
        ExitCall exitCall = null;
        Map<String, String> properties = new HashMap<String, String>();

        Object httpMethod = getReflectiveObject( httpRequest, methodAttribute );
        properties.put("METHOD", getReflectiveString( httpMethod, nameAttribute,"POST"));
        properties.put("HOST", getReflectiveString( httpRequest, hostAttribute, "UNKNOWN-HOST"));
        properties.put("URL", getReflectiveString( httpRequest, urlAttribute, "UNKNOWN-URL"));
        URL url = null;
        try {
            url = new URL( properties.get("URL") );
        } catch (MalformedURLException e) {
            this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodBegin() not a url: "+ properties.get("URL"));
        }
        if( ! "UNKNOWN-URL".equals(properties.get("URL")) && url != null ) {
            if( "UNKNOWN-HOST".equals(properties.get("HOST"))) {
                properties.put("HOST", url.getHost());
                properties.put("PORT", String.valueOf(url.getPort()) );
            }
            exitCall = transaction.startExitCall( properties, getUrlWithoutParameters(properties.get("URL")), EntryTypes.HTTP, false);
        } else {
            exitCall = transaction.startExitCall( properties, properties.get("HOST"), EntryTypes.POJO, false);
        }
        Object httpHeaders = getReflectiveObject( httpRequest, headersAttribute );
        if( httpHeaders != null ) {
            getReflectiveObject(httpHeaders, setHeader, CORRELATION_HEADER_KEY, exitCall.getCorrelationHeader() );

        }
        this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodBegin() end");
        return new State( transaction, exitCall );
    }

    public void onMethodEnd(Object stateObject, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodEnd() start");
        State state = (State) stateObject;
        if( exception != null )
            state.transaction.markAsError( exception.getMessage() );
        state.exitCall.end();
        this.getLogger().debug("ApigeeHTTPClientExitPointInterceptor.onMethodEnd() end");
    }
}
