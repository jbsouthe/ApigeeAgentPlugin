package com.cisco.josouthe;

import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;
import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.ServletContext;
import com.appdynamics.agent.api.EntryTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Interceptor to correlate or originate business transaction for Apigee Gateway
 * Interceptor is applied on
 * com.apigee.protocol.http.HTTPServer$Context.startRequestProcessing()
	Object public reader.message.requestURI.getPath().split(\/).[1]
 Customers that may be using this: Equinix, TDA
 * John Southerland
 * July 21, 2020

 TODO: add error handlers for:
 587         public void onInputTimeout() {
588             LOGGER.error(this.marker, "Message id:{} {}.onTimeout", (Object)this.messageId, (Object)this.input.client());
589             this.requestListener.onTimeout(this, true);
590         }
591 
592         @Override
593         public void onInputException(Exception ex) {
594             LOGGER.error("Message id:{} {}.onExceptionRead exception: {}", new Object[]{this.messageId, this.input.client(), ex});
595             this.requestListener.onException(this, true, ex);
596         }

pass the transaction as an object to the end instead of searching for it on the onMethodEnd

 Updated March 4, 2021 for TDA to be super complete and full of awesome will test and update
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@1569040149 for (error) ServerTransaction@1637052789: 1 segment(s) completed (incl. orig.), 2 pending, Business Transaction [58362] Entry Point Type [null] Component ID [-1]
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@253688070 for ServerTransaction@262599930: 1 segment(s) completed (incl. orig.), 1 pending, Business Transaction [DEFAULT_API_TX[0]] Entry Point Type [SERVLET] Component ID [44799]
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@2017958982 for ServerTransaction@1004451876: 1 segment(s) completed (incl. orig.), 2 pending, Business Transaction [DEFAULT_API_TX[0]] Entry Point Type [SERVLET] Component ID [44799]
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@1959234896 for ServerTransaction@1004451876: 1 segment(s) completed (incl. orig.), 2 pending, Business Transaction [DEFAULT_API_TX[0]] Entry Point Type [SERVLET] Component ID [44799]
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@286239898 for (async) (error) ServerTransaction@1768800524: 1 segment(s) completed (incl. orig.), 1 pending, Business Transaction [_APPDYNAMICS_DEFAULT_TX_[58362]] Entry Point Type [POJO] Component ID [44799]
 [AD Thread Pool-Global1] 12 Mar 2021 11:05:57,263 DEBUG SegmentManager - Entry: com.apigee.protocol.http.HTTPServer$Context@675887527 for (error) ServerTransaction@1637052789: 1 segment(s) completed (incl. orig.), 2 pending, Business Transaction [58362] Entry Point Type [null] Component ID [-1]
 */

@SuppressWarnings("unchecked")
public class ApigeeHTTPServerEntryPointInterceptor extends MyBaseInterceptor {


	IReflector getRequest, clientAddress, errorOnSSLClientHello, messageId; //com.apigee.protocol.http.HTTPServer$Context
	IReflector getHostString; //java.net.InetSocketAddress
	IReflector hostAttribute, urlAttribute, headersAttribute, methodAttribute, uriAttribute, bodyAttribute; // com.apigee.protocol.http.msg.HTTPRequest
	IReflector nameAttribute; // com.apigee.protocol.http.msg.HTTPMethod.name
	IReflector names, getAsString; // com.apigee.protocol.http.msg.HTTPHeaders
	IReflector getFormParams; // com.apigee.protocol.http.msg.Body: public Map<String, List<String>> getFormParams(String encoding)

	public ApigeeHTTPServerEntryPointInterceptor() {
		super();

		getRequest = makeInvokeInstanceMethodReflector("getRequest"); //HTTPRequest
		clientAddress = makeInvokeInstanceMethodReflector( "clientAddress" ); //InetSocketAddress
		errorOnSSLClientHello = makeAccessFieldValueReflector( "errorOnSSLClientHello" ); // boolean
		messageId = makeAccessFieldValueReflector( "messageId" ); // String

		getHostString = makeInvokeInstanceMethodReflector( "getHostString" ); // String

		hostAttribute = makeAccessFieldValueReflector("host"); //String
		urlAttribute = makeAccessFieldValueReflector("requestUrl"); //String
		uriAttribute = makeAccessFieldValueReflector("requestURI"); //RequestURI
		headersAttribute = makeAccessFieldValueReflector("headers"); //HTTPHeaders
		bodyAttribute = makeAccessFieldValueReflector("body"); //Body

		methodAttribute = makeAccessFieldValueReflector("requestMethod"); //HTTPMethod
		nameAttribute = makeAccessFieldValueReflector("name"); //String

		names = makeInvokeInstanceMethodReflector( "names" ); // Set<String>
		getAsString = makeInvokeInstanceMethodReflector("getAsString", String.class.getCanonicalName() ); //String

		getFormParams = makeInvokeInstanceMethodReflector( "getFormParams", String.class.getCanonicalName() ); // Map<String, List<String>>
	}

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();



        rules.add( new Rule.Builder(
                "com.apigee.protocol.http.HTTPServer$Context")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("requestStarted")
				.build()
		);

		rules.add( new Rule.Builder(
				"com.apigee.protocol.http.HTTPServer$Context")
				.classMatchType(SDKClassMatchType.MATCHES_CLASS)
				.methodMatchString("requestFinished")
				.build()
		);

		rules.add( new Rule.Builder(
				"com.apigee.protocol.http.HTTPServer$Context")
				.classMatchType(SDKClassMatchType.MATCHES_CLASS)
				.methodMatchString("onInputTimeout")
				.build()
		);

		rules.add( new Rule.Builder(
				"com.apigee.protocol.http.HTTPServer$Context")
				.classMatchType(SDKClassMatchType.MATCHES_CLASS)
				.methodMatchString("onInputException")
				.build()
		);



        /* monitor: this class is an internal class and manages the HTTP request as an FSM with a context.requestState enum
        startRequestProcessing() this is called after startSafeReading, but we have to wait until this to get a full Request object needed for servlet creation,
        	it is when all the initial HTTP line is read and headers have not yet been read, we may need to actually create the transaction, after this method executes, to have headers,
        	and be able to find a correlation header for continuation.
        destroy() called when a failure leads to shutdown, first when ssl handshake is bad, in which case context.errorOnSSLClientHello == true
        messageId attribute may be useful for troubleshooting, send as custom metric on start named "X-Apigee.Message-ID", also a header
        onInputTimeout() and onInputException(Exception ex) should mark the transaction as failed
         */

		/*
		package com.apigee.protocol.http.io;

		import com.apigee.protocol.http.HTTPService;

		public interface MessageListener {
			public void onStart(HTTPService.Context var1);

			public void onLine(HTTPService.Context var1);

			public void onHeaders(HTTPService.Context var1);

			public void onFinish(HTTPService.Context var1);

			public void onTimeout(HTTPService.Context var1, boolean var2);

			public void onException(HTTPService.Context var1, boolean var2, Exception var3);
		}

		rules.add( new Rule.Builder(
				"com.apigee.protocol.http.io.MessageListener")
				.classMatchType( SDKClassMatchType.IMPLEMENTS_INTERFACE )
				.build());
		*/
        return rules;
    }

    public Object onMethodBegin(Object object, String className, String methodName, Object[] params) {
        this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodBegin() start: "+ className +"."+ methodName +"()");
        Object context = object;
		Transaction transaction = null;
		switch (methodName) {
			case "requestStarted": {
				//moved to end of method, so we will have a header read in before starting a bt,
				// this may affect a destroy before a BT is started, but it is better this way regardless
				break;
			}
			default: {
				transaction = AppdynamicsAgent.startSegment(context);
			}
		}
        this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodBegin() end: "+ className +"."+ methodName +"()");
        return transaction;
    }

    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodEnd() start: "+ className +"."+ methodName +"()");
		Object context = object;
		Transaction transaction = (Transaction) state;
		if( transaction != null && exception != null ) {
			this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodEnd() exception not null, setting error with: "+ exception.getMessage());
			transaction.markAsError(exception.getMessage());
			this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodEnd() ending transaction with uuid: "+ transaction.getUniqueIdentifier());
			//transaction.endSegment();
			transaction.end();
			return;
		}
		switch (methodName) {
			case "requestStarted": {
				transaction = AppdynamicsAgent.startServletTransaction(buildServletContext(context), EntryTypes.HTTP, getCorrelationID(context), true);
				String messageIdString = getReflectiveString( context, messageId, "UNKNOWN-MESSAGE-ID");
				this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodEnd() starting transaction with uuid: " + transaction.getUniqueIdentifier() +"; X-Apigee.Message-ID: "+ messageIdString);
				collectData( transaction, "X-Apigee.Message-ID", messageIdString );
				transaction.markHandoff(context);
				break;
			}
			case "onInputTimeout": {
				if( transaction == null ) break;
				transaction.markAsError("Transaction timed out");
				//transaction.endSegment();
				transaction.end();
				break;
			}
			case "onInputException": {
				if( transaction == null ) break;
				transaction.markAsError( "Exception: "+ params[0]);
				//transaction.endSegment();
				transaction.end();
				break;
			}
			case "requestFinished": {
				if( transaction == null ) break;
				Boolean errorOnSSLClientHelloBoolean = (Boolean) getReflectiveObject(object, errorOnSSLClientHello);
				if( errorOnSSLClientHelloBoolean != null && errorOnSSLClientHelloBoolean )
					transaction.markAsError("Transaction Request Failed To SSL Handshake");
				//transaction.endSegment();
				transaction.end();
				break;
			}
			default: {
				if( transaction == null ) break;
				transaction.endSegment();
			}
		}

		this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.onMethodEnd() end: "+ className +"."+ methodName +"()");
    }

	private ServletContext buildServletContext( Object object ) {
		this.getLogger().debug("Entering into buildServletContext");
		ServletContext.ServletContextBuilder builder = new ServletContext.ServletContextBuilder();
		Object request = getReflectiveObject( object, getRequest );
		if( request == null ) {
			this.getLogger().warn("ApigeeHTTPServerEntryPointInterceptor.buildServletContext() Abandoning attempt to build ServletContext, request object still null");
			return builder.build();
		}
		//1. get URL for request
		try {
			String url = getReflectiveString( request, urlAttribute, "http://UNKNOWN-URL");
			if( url != null ) {
				builder.withURL( url );
			}
		} catch( java.net.MalformedURLException mex ) {
			this.getLogger().warn("ApigeeHTTPServerEntryPointInterceptor.buildServletContext() MalformedURLException in URL retrieval: "+ mex, mex);
		}

		//2. get Headers of request
		try {
			Object headers = getReflectiveObject( request, headersAttribute );
			if( headers != null ) {
			  HashMap<java.lang.String,java.lang.String> appdHeaders = new HashMap<String,String>();
			  Set<String> headerStringSet = (Set<String>) getReflectiveObject( headers, names);
			  if( headerStringSet != null ) {
				for( String headerName : headerStringSet ) {
					String headerValue = (String) getAsString.execute(headers.getClass().getClassLoader(), headers, new Object[]{headerName});
					if( headerName != null && headerValue != null ) appdHeaders.put( headerName, headerValue );
				}
				builder.withHeaders( appdHeaders );
			  }
			}
		} catch( ReflectorException rex ) {
			this.getLogger().warn("ApigeeHTTPServerEntryPointInterceptor.buildServletContext() ReflectorException in Headers retrieval: "+ rex, rex);
		}

		//3. get Query Parameters
		if( request != null ) {
			Object body = getReflectiveObject( request, bodyAttribute );
			if( body != null ) {
				String encoding = System.getProperty("file.encoding", "UTF-8"); //TODO com.apigee.protocol.http.msg.Encoding encoding = Context.request.headers.getEncoding(); use encoding.value attribute String
				Map<String, List<String>> queryParamMap = (Map<String, List<String>>) getReflectiveObject(body, getFormParams, encoding); //getFormParams.execute(request.getClass().getClassLoader(), request, new Object[]{encoding});
				if (queryParamMap != null) {
					HashMap<java.lang.String, java.lang.String[]> appdQueryParams = new HashMap<String, String[]>();
					for (String queryParamName : queryParamMap.keySet()) {
						String[] queryParamValues = queryParamMap.get(queryParamName).toArray(new String[]{});
						if (queryParamName != null)
							appdQueryParams.put(queryParamName, queryParamValues);
					}
					builder.withParameters(appdQueryParams);
				}
			}
		}

		//get Method
		Object method = getReflectiveObject( request, methodAttribute );
		builder.withRequestMethod( getReflectiveString( method, nameAttribute,"GET") );

		//get target host
		builder.withHostValue( getReflectiveString( request, hostAttribute, "UNKNOWN-HOST") );

		//get originating host
		Object clientSocketAddress = getReflectiveObject( object, clientAddress );
		builder.withHostOriginatingAddress( getReflectiveString( clientSocketAddress, getHostString, "UNKNOWN-CLIENTADDRESS"));

		ServletContext sc = builder.build();
		this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.buildServletContext() ServletContext to return: "+ sc.toString());
		this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.buildServletContext() Returning from buildServletContext");
		return sc;
	}

	private String getCorrelationID( Object object ) {
		this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.getCorrelationID() start");
		if( object == null ) return null;
		Object request = getReflectiveObject( object, getRequest );
		if( request == null ) return null;

		Object httpHeaders = getReflectiveObject( request, headersAttribute );
		if( httpHeaders == null ) return null;

		String correlationHeader = null;
		try {
			correlationHeader = (String) getAsString.execute( httpHeaders.getClass().getClassLoader(), httpHeaders, new Object[]{ AppdynamicsAgent.TRANSACTION_CORRELATION_HEADER});
			this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.getCorrelationID() found a correlation header: "+ correlationHeader);
		} catch (ReflectorException e) {
			this.getLogger().warn("ApigeeHTTPServerEntryPointInterceptor.getCorrelationID Error in reflection call while getting correlation header on exit call, exception: "+ e.getMessage(),e);
		}
		this.getLogger().debug("ApigeeHTTPServerEntryPointInterceptor.getCorrelationID() end");
		return correlationHeader;
	}
}
