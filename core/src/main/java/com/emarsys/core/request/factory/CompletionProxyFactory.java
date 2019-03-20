package com.emarsys.core.request.factory;

import android.os.Handler;

import com.emarsys.core.CoreCompletionHandler;
import com.emarsys.core.database.repository.Repository;
import com.emarsys.core.database.repository.SqlSpecification;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.core.util.Assert;
import com.emarsys.core.worker.CoreCompletionHandlerMiddleware;
import com.emarsys.core.worker.Worker;

public class CompletionProxyFactory {

    private Repository<RequestModel, SqlSpecification> requestRepository;
    private Handler uiHandler;
    private Handler coreSdkHandler;
    private CoreCompletionHandler defaultCoreCompletionHandler;

    public CompletionProxyFactory(Repository<RequestModel, SqlSpecification> requestRepository, Handler uiHandler, Handler coreSdkHandler, CoreCompletionHandler defaultCoreCompletionHandler) {
        Assert.notNull(requestRepository, "RequestRepository must not be null!");
        Assert.notNull(uiHandler, "UiHandler must not be null!");
        Assert.notNull(coreSdkHandler, "CoreSdkHandler must not be null!");
        Assert.notNull(defaultCoreCompletionHandler, "DefaultCoreCompletionHandler must not be null!");

        this.requestRepository = requestRepository;
        this.uiHandler = uiHandler;
        this.coreSdkHandler = coreSdkHandler;
        this.defaultCoreCompletionHandler = defaultCoreCompletionHandler;
    }

    public CoreCompletionHandler createCompletionHandler(Worker worker, CoreCompletionHandler completionHandler) {
        CoreCompletionHandler result = defaultCoreCompletionHandler;

        if (completionHandler != null) {
            result = completionHandler;
        }
        if (worker != null) {
            result = new CoreCompletionHandlerMiddleware(worker, requestRepository, uiHandler, coreSdkHandler, result);
        }
        return result;
    }
}
