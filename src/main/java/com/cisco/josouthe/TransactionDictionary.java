package com.cisco.josouthe;

import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;

import java.net.URL;
import java.util.Date;

public class TransactionDictionary {
    private String correlationId;
    private ExitCall exitCall;
    private Transaction businessTransaction;
    private URL requestURL;

    public TransactionDictionary(URL requestURL, Transaction transaction, ExitCall exitCall ) {
        this.requestURL=requestURL;
        this.businessTransaction=transaction;
        this.exitCall=exitCall;
        this.correlationId = exitCall.getCorrelationHeader();
        this.finished=false;
        this.updateLastTouchTime();
    }

    private boolean finished=false;
    private long lastTouchTime= new Date().getTime();

    public boolean isFinished() {
        return finished;
    }

    public void setFinished( boolean value ) { this.finished=value; }

    public long getLastTouchTime() { return this.lastTouchTime; }
    public void updateLastTouchTime() { this.lastTouchTime = new Date().getTime(); }

    public ExitCall getExitCall() {
        return exitCall;
    }
    public Transaction getBusinessTransaction() { return businessTransaction; }

    public String getCorrelationId() {
        return correlationId;
    }

    public URL getRequestURL() { return requestURL; }
}
