package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ServletContext;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Interceptor to correlate or originate business transaction for Apigee Gateway
 * Interceptor is applied on
 * com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask.call()
 *   split on messageContext.applicationIdentifier.org (String)
 * John Southerland josouthe@cisco.com
 * March 09, 2021

    debug-interceptors=com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask/call
 */
@SuppressWarnings("unchecked")
public class ApigeeAsyncExecutionTaskEntryPointInterceptor extends MyBaseInterceptor {


    IReflector execution, messageContext, executionContext; //Attributes on com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask
    IReflector applicationIdentifier, proxyRequestMessage, getRequestMessage, getVariable, getValue, getLoggingContext; //com.apigee.flow.message.MessageContextImpl
    IReflector org, env, proxy, revision; //Attributes Strings on applicationIdentifier
    IReflector getHeader, getHeaderNames, getHeadersAsString, getQueryParamNames, getQueryParam; //com.apigee.flow.message.MessageImpl
    IReflector isSuccess, getCause, getErrorResponse; //com.apigee.flow.execution.ExecutionResult

    public ApigeeAsyncExecutionTaskEntryPointInterceptor() {
        super();


        execution = makeAccessFieldValueReflector("execution");//Attribute on com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask
        messageContext = makeAccessFieldValueReflector("messageContext");//com.apigee.flow.message.MessageContextImpl Attribute on com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask
        executionContext = makeAccessFieldValueReflector("executionContext");//Attribute on com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask

        applicationIdentifier = makeAccessFieldValueReflector("applicationIdentifier"); //ApplicationIdentifier Object attribute in MessageContext
        org = makeAccessFieldValueReflector("org"); //String attribute in applicationIdentifier
        env = makeAccessFieldValueReflector("env"); //String attribute in applicationIdentifier
        proxy = makeAccessFieldValueReflector("proxy"); //String attribute in applicationIdentifier
        revision = makeAccessFieldValueReflector("revision"); //String attribute in applicationIdentifier

        proxyRequestMessage = makeAccessFieldValueReflector("proxyRequestMessage"); //the incoming proxy Request Message
        getRequestMessage = makeInvokeInstanceMethodReflector("getRequestMessage" ); //returns Message
        getVariable = makeInvokeInstanceMethodReflector( "getVariable", String.class.getCanonicalName() );// returns a (T) that can be toString()'ed
        getValue = makeInvokeInstanceMethodReflector( "getValue", String.class.getCanonicalName(), "com.apigee.flow.message.MessageContext" );// returns a (T) that can be toString()'ed
        getLoggingContext = makeInvokeInstanceMethodReflector("getLoggingContext"); //returns Map<String, String>

        getHeader = makeInvokeInstanceMethodReflector( "getHeader", String.class.getCanonicalName() ); //returns a String
        getHeaderNames = makeInvokeInstanceMethodReflector( "getHeaderNames" ); //returns a Set<String>
        getHeadersAsString = makeInvokeInstanceMethodReflector( "getHeadersAsString" , String.class.getCanonicalName() ); //returns String
        getQueryParamNames = makeInvokeInstanceMethodReflector( "getQueryParamNames" ); //returns Set<String>
        getQueryParam = makeInvokeInstanceMethodReflector( "getQueryParam", String.class.getCanonicalName() ); //returns String

        isSuccess = makeInvokeInstanceMethodReflector("isSuccess" ); //return Boolean
        getCause = makeInvokeInstanceMethodReflector( "getCause" ); //returns Throwable
        getErrorResponse = makeInvokeInstanceMethodReflector("getErrorResponse"); //returns String
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.apigee.flow.execution.AbstractAsyncExecutionStrategy$AsyncExecutionTask")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("call")
                .build()
        );
        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        if( apigeeVersion < 4.5 ) {
            return onMethodBegin_4_19(object, className, methodName, params);
        }
        return onMethodBegin_4_50(object, className, methodName, params);
    }

    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( apigeeVersion < 4.5 ) {
            onMethodEnd_4_19(state,object,className,methodName,params,exception,returnVal);
        } else {
            onMethodEnd_4_50(state,object,className,methodName,params,exception,returnVal);
        }

    }

    public Object onMethodBegin_4_50(Object object, String className, String methodName, Object[] params) {
        getLogger().debug(String.format("Beginning onMethodBegin_4_50 %s.%s()",className,methodName));
        Transaction transaction = null;
        Object messageContextObject = getReflectiveObject(object, messageContext);
        if( messageContextObject != null ) {
            Object requestMessage = getReflectiveObject(messageContextObject, getRequestMessage);
            Map<String,String> loggingContext = (Map<String, String>) getReflectiveObject(messageContextObject, getLoggingContext);
            StringBuilder btName = new StringBuilder("AsyncExecutionTask.");
            btName.append(loggingContext.getOrDefault( "org", "UNKNOWN-ORG" )).append(".");
            btName.append(loggingContext.getOrDefault( "env", "UNKNOWN-ENV" )).append(".");
            btName.append(loggingContext.getOrDefault( "apiName", "UNKNOWN-APP" )).append(".");
            btName.append(loggingContext.getOrDefault( "revision", "UNKNOWN-APP-REVISION" ));
            transaction = AppdynamicsAgent.startTransaction( btName.toString(), getCorrelationID(requestMessage), EntryTypes.POJO, false );
            getLogger().debug(String.format("URI: %s Verb: %s", (String) getReflectiveObject(messageContextObject, getVariable, "uri"),(String) getReflectiveObject(messageContextObject, getVariable, "verb")));
            collectSnapshotData(transaction,"uri", (String) getReflectiveObject(messageContextObject, getVariable, "uri"));
        }
        getLogger().debug(String.format("Finished onMethodBegin_4_50 %s.%s()",className,methodName));
        return transaction;
    }

    public void onMethodEnd_4_50(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        getLogger().debug(String.format("Beginning onMethodEnd_4_50 %s.%s()",className,methodName));
        Object executionResult = returnVal;
        Transaction transaction = (Transaction) state;
        if( transaction == null ) return;
        if( exception != null ) {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() exception not null, setting error with: "+ exception.getMessage());
            transaction.markAsError(exception.getMessage());
        }
        Boolean isSuccessValue = (Boolean) getReflectiveObject( executionResult, isSuccess );
        if( isSuccessValue != null && !isSuccessValue ) {
            Throwable throwable = (Throwable) getReflectiveObject( executionResult, getCause );
            if ( throwable != null ) {
                this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() isSuccess() == false, setting error to: " + throwable.getMessage());
                transaction.markAsError(throwable.getMessage());
            }
        }
        if( executionResult != null ) collectSnapshotData(transaction, "Apigee-Execution-Result", executionResult.toString() );

        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() ending transaction with uuid: "+ transaction.getUniqueIdentifier());
        transaction.end();
        getLogger().debug(String.format("Finished onMethodEnd_4_50 %s.%s()",className,methodName));
    }


    public Object onMethodBegin_4_19(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodBegin() start: "+ className +"."+ methodName +"()");
        Object messageContextObject = getReflectiveObject( object, messageContext);
        if( messageContextObject == null ) {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodBegin() Message Context is null, abandoning BT Start");
            return null;
        }
        Object requestMessage = getReflectiveObject( messageContextObject, proxyRequestMessage );
        Object applicationIdentifierObject = getReflectiveObject( messageContextObject, applicationIdentifier );
        //ServletContext servletContext = buildServletContext(requestMessage);
        Transaction transaction = null;
        /*if( servletContext != null ) {
            transaction = AppdynamicsAgent.startServletTransaction(servletContext, EntryTypes.HTTP, getCorrelationID(requestMessage), false);
        } else {*/
            StringBuilder btName = new StringBuilder("AsyncExecutionTask.");
            btName.append(getReflectiveString(applicationIdentifierObject, org, "UNKNOWN-ORG")).append(".");
            btName.append(getReflectiveString(applicationIdentifierObject, env, "UNKNOWN-ENV")).append(".");
            btName.append(getReflectiveString(applicationIdentifierObject, proxy, "UNKNOWN-PROXY")).append(".");
            btName.append(getReflectiveString(applicationIdentifierObject, revision, "UNKNOWN-REVISION"));
            transaction = AppdynamicsAgent.startTransaction( btName.toString(), getCorrelationID(requestMessage), EntryTypes.POJO, false );
        //}
        //String messageIdString = getReflectiveString( context, messageId, "UNKNOWN-MESSAGE-ID");
        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodBegin() starting transaction with uuid: " + transaction.getUniqueIdentifier() ); //+"; X-Apigee.Message-ID: "+ messageIdString);
        //collectData( transaction, "X-Apigee.Message-ID", messageIdString );
        collectData( transaction, className, "Apigee.Organizations", getReflectiveString(applicationIdentifierObject, org, "UNKNOWN-ORG"));
        collectData( transaction, className, "Apigee.Environments", getReflectiveString(applicationIdentifierObject, env, "UNKNOWN-ENV"));
        collectData( transaction, className, "Apigee.APIProxy", getReflectiveString(applicationIdentifierObject, proxy, "UNKNOWN-PROXY"));
        collectData( transaction, className, "Apigee.Revision", getReflectiveString(applicationIdentifierObject, revision, "UNKNOWN-REVISION"));

        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodBegin() end: "+ className +"."+ methodName +"()");
        return transaction;
    }

    public void onMethodEnd_4_19(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
        Object context = object;
        Object executionResult = returnVal;
        Transaction transaction = (Transaction) state;
        if( transaction == null ) return;
        if( exception != null ) {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() exception not null, setting error with: "+ exception.getMessage());
            transaction.markAsError(exception.getMessage());
        }
        Boolean isSuccessValue = (Boolean) getReflectiveObject( executionResult, isSuccess );
        if( isSuccessValue != null && !isSuccessValue ) {
            Throwable throwable = (Throwable) getReflectiveObject( executionResult, getCause );
            if ( throwable != null ) {
                this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() isSuccess() == false, setting error to: " + throwable.getMessage());
                transaction.markAsError(throwable.getMessage());
            }
        }
        if( executionResult != null ) collectSnapshotData(transaction, "Apigee-Execution-Result", executionResult.toString() );

        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() ending transaction with uuid: "+ transaction.getUniqueIdentifier());
        transaction.end();
        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }

    private ServletContext buildServletContext(Object request) {
        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() start");
        ServletContext.ServletContextBuilder builder = new ServletContext.ServletContextBuilder();
        if( request == null ) {
            this.getLogger().info("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() Abandoning attempt to build ServletContext, request object still null");
            return null;
        }

        //1. get URL for request
        String url = (String) getReflectiveObject(request, getValue, "url", request);
        if ( url == null ) {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() URL fetch 1 didn't work");
        } else {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() URL fetch 1 WORKED!: "+ url);
        }
        try {
            /*
            url = getVariable.execute(request.getClass().getClassLoader(), request, new Object[]{"url"});
            if( url == null ) {
                this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() URL fetch 2 didn't work");
                return null;
            } else {
                this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() URL fetch 2 WORKED!: "+ url);
            }

             */
            builder.withURL( url );
        } catch( java.net.MalformedURLException mex ) {
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() MalformedURLException in URL retrieval: "+ url +" Exception: "+ mex, mex);
        } finally {
            if( url == null ) {
                this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() end with null servlet context");
                return null;
            }
        }

        //2. get headers
        HashMap<String, String> appdHeaders = new HashMap<String,String>();
        Set<String> headerStringSet = (Set<String>) getReflectiveObject( request, getHeaderNames );
        for( String headerName : headerStringSet ) {
            //try {
                String headerValue = (String) getReflectiveObject(request, getHeadersAsString, headerName); //getHeadersAsString.execute(request.getClass().getClassLoader(), request, new Object[]{headerName});
                if (headerName != null && headerValue != null) appdHeaders.put(headerName, headerValue);
            //} catch (ReflectorException e) {
            //    this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() Exception in getHeadersAsString(): "+ e.getMessage(), e);
            //}
        }
        builder.withHeaders( appdHeaders );

        //3. get Query Parameters
        Set<String> queryParamStringSet = (Set<String>) getReflectiveObject( request, getQueryParamNames);
        if( queryParamStringSet != null ) {
            HashMap<java.lang.String, java.lang.String[]> appdQueryParams = new HashMap<String, String[]>();
            for( String queryParamName : queryParamStringSet ) {
                    String queryParamValue = (String) getReflectiveObject( request, getQueryParam, queryParamName);
                           // getQueryParam.execute(request.getClass().getClassLoader(), request, new Object[]{queryParamName});
                    if (queryParamName != null && queryParamValue != null)
                        appdQueryParams.put(queryParamName, new String[]{queryParamValue});

            }
            builder.withParameters(appdQueryParams);
        }

        /*
        TODO add the other parameters as we find them....
        builder.withHostValue();
        builder.withRequestMethod();
        builder.withHostOriginatingAddress();
        builder.withCookies();
        */

        this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.buildServletContext() end with a valid servlet context");
        return builder.build();
    }

    private String getCorrelationID(Object requestMessage) {
        String value = null;
        if( requestMessage == null ) return null;
        value = (String) getReflectiveObject( requestMessage, getHeadersAsString, AppdynamicsAgent.TRANSACTION_CORRELATION_HEADER);
        if( value != null && !"".equals(value) )
            this.getLogger().debug("ApigeeAsyncExecutionTaskEntryPointInterceptor.getCorrelationID() found correlation header: "+ value);
        return value;
    }
}
