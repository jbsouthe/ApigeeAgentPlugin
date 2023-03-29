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

/**
 * Interceptor to correlate or originate business transaction for Apigee Gateway
 * Interceptor is applied on
 * com.apigee.protocol.http.HTTPServer$Context.startRequestProcessing()
	Object public reader.message.requestURI.getPath().split(\/).[1]
 Customers that may be using this: Equinix, TDA
 * John Southerland josouthe@cisco.com
 * 03/12/2021
 com.apigee.rpc.impl.RPCMachineImpl$IncomingCall.execute(RPCMachineImpl.java:399)
 static final class IncomingCall {
 final AbstractExecutor handler;

 IncomingCall(AbstractExecutor handler) {
 this.handler = handler;
 }

 void execute(Frame frame, ServerHandleImpl handle) throws RPCException {
 this.handler.execute(frame, handle);
 }
 }
 */

public class ApigeeRPCEntryPointInterceptor extends MyBaseInterceptor {

	IReflector getStringHeader, getTypeName, getDataTypeName; // com.apigee.rpc.impl.Frame

	public ApigeeRPCEntryPointInterceptor() {
		super();

		getStringHeader = makeInvokeInstanceMethodReflector("getStringHeader", String.class.getCanonicalName() ); //String
		getTypeName = makeInvokeInstanceMethodReflector("getTypeName"); //String
		getDataTypeName = makeInvokeInstanceMethodReflector( "getDataTypeName" ); //String

	}

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();
        /*
        rules.add( new Rule.Builder(
                "com.apigee.rpc.impl.RPCMachineImpl$IncomingCall")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("execute")
				.build()
		);
        */
        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodBegin() start: "+ className +"."+ methodName +"()");
        Object frame = params[0];
        Object handler = params[1];
		Transaction transaction = null;
		String correlationHeaderValue = (String) getReflectiveObject(frame, getStringHeader, CORRELATION_HEADER_KEY);
		String typeName = getReflectiveString( frame, getTypeName, "UNKNOWN-TYPE");
		String dataTypeName = getReflectiveString( frame, getDataTypeName, "UNKNOWN-DATA_TYPE");
		this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodBegin() Frame Type: "+ typeName +" Data Type: "+ dataTypeName +" Correlation Header: "+ correlationHeaderValue);
		transaction = AppdynamicsAgent.startTransaction( "RPC."+typeName+"."+dataTypeName, correlationHeaderValue, EntryTypes.POJO, false);
        this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodBegin() end: "+ className +"."+ methodName +"()");
        return transaction;
    }

    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
		Object context = object;
		Transaction transaction = (Transaction) state;
		if(transaction == null) return;
		if( exception != null ) {
			this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodEnd() exception not null, setting error with: "+ exception.getMessage());
			transaction.markAsError(exception.getMessage());
		}

		this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodEnd() ending transaction with uuid: "+ transaction.getUniqueIdentifier());
		transaction.end();
		this.getLogger().debug("ApigeeRPCEntryPointInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }


}
