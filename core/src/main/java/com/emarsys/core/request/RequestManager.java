package com.emarsys.core.request;

import android.os.Handler;

import com.emarsys.core.database.repository.Repository;
import com.emarsys.core.database.repository.SqlSpecification;
import com.emarsys.core.request.factory.DefaultRunnableFactory;
import com.emarsys.core.request.factory.RunnableFactory;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.core.util.Assert;
import com.emarsys.core.util.log.CoreTopic;
import com.emarsys.core.util.log.EMSLogger;
import com.emarsys.core.worker.Worker;

import java.util.HashMap;
import java.util.Map;

public class RequestManager {

    private Map<String, String> defaultHeaders;

    Worker worker;
    Handler coreSDKHandler;
    RunnableFactory runnableFactory;
    Repository<RequestModel, SqlSpecification> requestRepository;

    public RequestManager(Handler coreSDKHandler, Repository<RequestModel, SqlSpecification> requestRepository, Worker worker) {
        Assert.notNull(coreSDKHandler, "CoreSDKHandler must not be null!");
        Assert.notNull(requestRepository, "RequestRepository must not be null!");
        Assert.notNull(worker, "Worker must not be null!");
        defaultHeaders = new HashMap<>();
        this.requestRepository = requestRepository;
        this.coreSDKHandler = coreSDKHandler;
        this.worker = worker;
        runnableFactory = new DefaultRunnableFactory();
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        EMSLogger.log(CoreTopic.NETWORKING, "Argument: %s", defaultHeaders);
        this.defaultHeaders = defaultHeaders;
    }

    public void submit(final RequestModel model) {
        Assert.notNull(model, "RequestModel must not be null!");
        EMSLogger.log(CoreTopic.NETWORKING, "Argument: %s", model);

        coreSDKHandler.post(runnableFactory.runnableFrom(new Runnable() {
            @Override
            public void run() {
                injectDefaultHeaders(model);
                requestRepository.add(model);
                worker.run();
            }
        }));

    }

    void injectDefaultHeaders(RequestModel model) {
        Map<String, String> modelHeaders = model.getHeaders();
        for (Map.Entry<String, String> defaultHeader : defaultHeaders.entrySet()) {
            String key = defaultHeader.getKey();
            String value = defaultHeader.getValue();

            if (!modelHeaders.containsKey(key)) {
                modelHeaders.put(key, value);
            }
        }
    }

}
