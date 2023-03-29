package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApigeeRPCInterceptor extends MyBaseInterceptor {

    IReflector getHost, getPort; //String, int methods on com.apigee.rpc.impl.ClientHandleImpl
    IReflector getStringHeader, getTypeName, getDataTypeName, addHeader; // com.apigee.rpc.impl.Frame

    public ApigeeRPCInterceptor() {
        super();

        getHost = makeInvokeInstanceMethodReflector("getHost"); //String
        getPort = makeInvokeInstanceMethodReflector( "getPort" ); //int
        getStringHeader = makeInvokeInstanceMethodReflector("getStringHeader", String.class.getCanonicalName() ); //String
        getTypeName = makeInvokeInstanceMethodReflector("getTypeName"); //String
        getDataTypeName = makeInvokeInstanceMethodReflector( "getDataTypeName" ); //String
        addHeader = makeInvokeInstanceMethodReflector("addHeader", String.class.getCanonicalName(), String.class.getCanonicalName());
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.apigee.rpc.impl.ClientHandleImpl")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("pushFrame")
                .withParams("com.apigee.rpc.impl.Frame", "com.apigee.rpc.impl.RPCMachineImpl$OutgoingCall")
                .build()
        );

        /*
        rules.add(new Rule.Builder(
                "com.apigee.rpc.impl.ServerHandleImpl")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("pushFrame")
                .atLineNumber(233)
                //.withParams("com.apigee.rpc.impl.Frame", "com.apigee.rpc.impl.RPCMachineImpl.OutgoingCall")
                .build()
        );

         */
        return rules;
    }

    @SuppressWarnings("unchecked")
    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("ApigeeRPCInterceptor.onMethodBegin() start: "+ className +"."+ methodName +"()");
        Transaction transaction = null;
        ExitCall exitCall = null;

        Object frame = params[0];


        //if( className.equals("com.apigee.rpc.impl.ClientHandleImpl") ) {
            transaction = AppdynamicsAgent.getTransaction();
            String host = getReflectiveString(object, getHost, "UNKNOWN-HOST");
            Integer port = getReflectiveInteger( object, getPort, -1);
            Map<String, String> properties = new HashMap<String, String>();
            properties.put("HOST", host);
            properties.put("PORT", port.toString());
            exitCall = transaction.startExitCall(properties, properties.get("HOST"), EntryTypes.POJO, false);
            getReflectiveObject( params[0] , addHeader, CORRELATION_HEADER_KEY.toString(), exitCall.getCorrelationHeader() );
                this.getLogger().debug("ApigeeRPCInterceptor.onMethodBegin() adding correlation to exitcall: "+ exitCall.getCorrelationHeader());
        /*} else {
            String correlationHeaderValue = (String) getReflectiveObject(frame, getStringHeader, CORRELATION_HEADER_KEY);
            String typeName = getReflectiveString( frame, getTypeName, "UNKNOWN-TYPE");
            String dataTypeName = getReflectiveString( frame, getDataTypeName, "UNKNOWN-DATA_TYPE");
            transaction = AppdynamicsAgent.startTransaction( "RPC."+typeName+"."+dataTypeName, correlationHeaderValue, EntryTypes.POJO, false);
            this.getLogger().debug("ApigeeRPCInterceptor.onMethodBegin() Frame Type: "+ typeName +" Data Type: "+ dataTypeName +" transaction uuid: "+ transaction.getUniqueIdentifier() +" Correlation Header: "+ correlationHeaderValue );
        }*/
        this.getLogger().debug("ApigeeRPCInterceptor.onMethodBegin() end: "+ className +"."+ methodName +"()");
        return new State( transaction, exitCall );
    }

    public void onMethodEnd(Object stateObject, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("ApigeeRPCInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
        Object context = object;
        State state =  (State) stateObject;
        if( exception != null ) {
            this.getLogger().debug("ApigeeRPCInterceptor.onMethodEnd() exception not null, setting error with: " + exception.getMessage());
            state.transaction.markAsError(exception.getMessage());
        }
        if( state.exitCall != null ) {
            state.exitCall.end();
        } else {
            state.transaction.end();
        }
        this.getLogger().debug("ApigeeRPCInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }
}
