package com.emarsys.mobileengage.inbox;

import android.os.Handler;

import com.emarsys.core.CoreCompletionHandler;
import com.emarsys.core.Registry;
import com.emarsys.core.api.ResponseErrorException;
import com.emarsys.core.api.result.CompletionListener;
import com.emarsys.core.api.result.ResultListener;
import com.emarsys.core.api.result.Try;
import com.emarsys.core.database.repository.Repository;
import com.emarsys.core.device.DeviceInfo;
import com.emarsys.core.provider.timestamp.TimestampProvider;
import com.emarsys.core.provider.uuid.UUIDProvider;
import com.emarsys.core.request.RequestManager;
import com.emarsys.core.request.RestClient;
import com.emarsys.core.request.factory.CompletionProxyFactory;
import com.emarsys.core.request.model.RequestMethod;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.core.response.ResponseModel;
import com.emarsys.core.storage.Storage;
import com.emarsys.core.util.TimestampUtils;
import com.emarsys.core.worker.Worker;
import com.emarsys.mobileengage.RequestContext;
import com.emarsys.mobileengage.api.inbox.Notification;
import com.emarsys.mobileengage.api.inbox.NotificationInboxStatus;
import com.emarsys.mobileengage.fake.FakeInboxResultListener;
import com.emarsys.mobileengage.fake.FakeResetBadgeCountResultListener;
import com.emarsys.mobileengage.fake.FakeRestClient;
import com.emarsys.mobileengage.inbox.model.NotificationCache;
import com.emarsys.mobileengage.request.RequestModelFactory;
import com.emarsys.mobileengage.storage.AppLoginStorage;
import com.emarsys.mobileengage.storage.MeIdSignatureStorage;
import com.emarsys.mobileengage.storage.MeIdStorage;
import com.emarsys.mobileengage.testUtil.RequestModelTestUtils;
import com.emarsys.mobileengage.util.RequestHeaderUtils;
import com.emarsys.mobileengage.util.RequestHeaderUtils_Old;
import com.emarsys.mobileengage.util.RequestUrlUtils;
import com.emarsys.testUtil.TimeoutUtils;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InboxInternal_V2Test {

    public static final long TIMESTAMP = 100_000;
    public static final String APPLICATION_ID = "id";
    public static final String ME_ID = "12345";
    public static final String ME_ID_SIGNATURE = "1111signature";
    public static final String REQUEST_ID = "REQUEST_ID";
    public static final String MESSAGE_ID = "id";
    public static final String SID = "sid";

    private InboxInternal inbox;
    private RequestManager manager;
    private MeIdStorage meIdStorage;
    private MeIdSignatureStorage meIdSignatureStorage;
    private ResultListener<Try<NotificationInboxStatus>> resultListener;
    private CompletionListener resetListenerMock;
    private CountDownLatch latch;
    private NotificationCache cache;
    private RequestContext requestContext;
    private Notification notification;
    private TimestampProvider timestampProvider;
    private UUIDProvider uuidProvider;
    private RequestModelFactory requestModelFactory;

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();
    public static final String NOTIFICATION_STRING_1 = "{" +
            "\"id\":\"id1\", " +
            "\"sid\":\"sid1\", " +
            "\"title\":\"title1\", " +
            "\"custom_data\": {" +
            "\"data1\":\"dataValue1\"," +
            "\"data2\":\"dataValue2\"" +
            "}," +
            "\"root_params\": {" +
            "\"param1\":\"paramValue1\"," +
            "\"param2\":\"paramValue2\"" +
            "}," +
            "\"expiration_time\": 300, " +
            "\"received_at\":10000000" +
            "}";
    public static final String NOTIFICATION_STRING_2 = "{" +
            "\"id\":\"id2\", " +
            "\"sid\":\"sid2\", " +
            "\"title\":\"title2\", " +
            "\"custom_data\": {" +
            "\"data3\":\"dataValue3\"," +
            "\"data4\":\"dataValue4\"" +
            "}," +
            "\"root_params\": {" +
            "\"param3\":\"paramValue3\"," +
            "\"param4\":\"paramValue4\"" +
            "}," +
            "\"expiration_time\": 200, " +
            "\"received_at\":30000000" +
            "}";
    public static final String NOTIFICATION_STRING_3 = "{" +
            "\"id\":\"id3\", " +
            "\"sid\":\"sid3\", " +
            "\"title\":\"title3\", " +
            "\"custom_data\": {" +
            "\"data5\":\"dataValue5\"," +
            "\"data6\":\"dataValue6\"" +
            "}," +
            "\"root_params\": {" +
            "\"param5\":\"paramValue5\"," +
            "\"param6\":\"paramValue6\"" +
            "}," +
            "\"expiration_time\": 100, " +
            "\"received_at\":25000000" +
            "}";

    @Before
    @SuppressWarnings("unchecked")
    public void init() throws Exception {
        clearNotificationCache();

        latch = new CountDownLatch(1);

        manager = mock(RequestManager.class);

        meIdStorage = mock(MeIdStorage.class);
        when(meIdStorage.get()).thenReturn(ME_ID);

        meIdSignatureStorage = mock(MeIdSignatureStorage.class);
        when(meIdSignatureStorage.get()).thenReturn(ME_ID_SIGNATURE);

        uuidProvider = mock(UUIDProvider.class);
        when(uuidProvider.provideId()).thenReturn(REQUEST_ID);

        timestampProvider = mock(TimestampProvider.class);
        when(timestampProvider.provideTimestamp()).thenReturn(TIMESTAMP);
        requestContext = new RequestContext(
                APPLICATION_ID,
                "applicationPassword",
                1,
                mock(DeviceInfo.class),
                mock(AppLoginStorage.class),
                meIdStorage,
                meIdSignatureStorage,
                timestampProvider,
                uuidProvider,
                mock(Storage.class),
                mock(Storage.class),
                mock(Storage.class)
        );

        requestModelFactory = new RequestModelFactory(requestContext);

        inbox = new InboxInternal_V2(manager, requestContext, requestModelFactory);

        resultListener = mock(ResultListener.class);
        resetListenerMock = mock(CompletionListener.class);
        cache = new NotificationCache();

        notification = new Notification(
                MESSAGE_ID,
                SID,
                "title",
                null,
                new HashMap<String, String>(),
                new JSONObject(),
                2000,
                400);
    }

    @After
    public void tearDown() throws Exception {
        clearNotificationCache();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_requestManager_shouldNotBeNull() {
        inbox = new InboxInternal_V2(null, requestContext, requestModelFactory);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_requestContext_shouldNotBeNull() {
        inbox = new InboxInternal_V2(manager, null, requestModelFactory);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_requestModelFactory_shouldNotBeNull() {
        inbox = new InboxInternal_V2(manager, requestContext, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFetchNotifications_listenerShouldNotBeNull() {
        inbox.fetchNotifications(null);
    }

    @Test
    public void testFetchNotifications_shouldMakeRequest_viaRequestManager_submitNow() {
        RequestModel expected = createRequestModel(
                "https://me-inbox.eservice.emarsys.net/api/v1/notifications/" + ME_ID,
                RequestMethod.GET);

        inbox.fetchNotifications(resultListener);

        ArgumentCaptor<RequestModel> requestCaptor = ArgumentCaptor.forClass(RequestModel.class);
        verify(manager).submitNow(requestCaptor.capture(), any(CoreCompletionHandler.class));

        RequestModel requestModel = requestCaptor.getValue();
        Assert.assertNotNull(requestModel.getId());
        Assert.assertNotNull(requestModel.getTimestamp());
        Assert.assertEquals(expected.getUrl(), requestModel.getUrl());
        Assert.assertEquals(expected.getHeaders(), requestModel.getHeaders());
        Assert.assertEquals(expected.getMethod(), requestModel.getMethod());
    }

    @Test
    public void testFetchNotifications_listener_success() throws Exception {
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(createSuccessResponse(), FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(new NotificationInboxStatus(createNotificationList(), 300), listener.resultStatus);
        Assert.assertEquals(1, listener.successCount);
    }

    @Test
    public void testFetchNotifications_listener_success_shouldBeCalledOnMainThread() throws InterruptedException {
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(createSuccessResponse(), FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch, FakeInboxResultListener.Mode.MAIN_THREAD);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(1, listener.successCount);
    }

    @Test
    public void testFetchNotifications_listener_success_withCachedNotifications() throws Exception {
        List<Notification> cachedNotifications = createCacheList();
        for (int i = cachedNotifications.size() - 1; i >= 0; --i) {
            cache.cache(cachedNotifications.get(i));
        }

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(createSuccessResponse(), FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch, FakeInboxResultListener.Mode.MAIN_THREAD);
        inbox.fetchNotifications(listener);

        latch.await();

        List<Notification> result = listener.resultStatus.getNotifications();

        List<Notification> expected = new ArrayList<>(cachedNotifications);
        expected.addAll(createNotificationList());

        Assert.assertEquals(expected, result);
    }

    @Test
    public void testFetchNotifications_listener_failureWithException() throws InterruptedException {
        Exception expectedException = new Exception("FakeRestClientException");
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(expectedException)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(expectedException, listener.errorCause);
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testFetchNotifications_listener_failureWithException_shouldBeCalledOnMainThread() throws InterruptedException {
        Exception expectedException = new Exception("FakeRestClientException");
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(expectedException)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch, FakeInboxResultListener.Mode.MAIN_THREAD);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testFetchNotification_listener_failureWithResponseModel() throws InterruptedException {
        ResponseModel responseModel = new ResponseModel.Builder()
                .statusCode(400)
                .message("Bad request")
                .requestModel(mock(RequestModel.class))
                .build();
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responseModel, FakeRestClient.Mode.ERROR_RESPONSE_MODEL)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch);
        inbox.fetchNotifications(listener);

        latch.await();

        ResponseErrorException expectedException = new ResponseErrorException(
                responseModel.getStatusCode(),
                responseModel.getMessage(),
                responseModel.getBody());

        ResponseErrorException resultException = (ResponseErrorException) listener.errorCause;
        Assert.assertEquals(expectedException.getStatusCode(), resultException.getStatusCode());
        Assert.assertEquals(expectedException.getMessage(), resultException.getMessage());
        Assert.assertEquals(expectedException.getBody(), resultException.getBody());
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testFetchNotification_listener_failureWithResponseModel_shouldBeCalledOnMainThread() throws InterruptedException {
        ResponseModel responseModel = new ResponseModel.Builder()
                .statusCode(400)
                .message("Bad request")
                .requestModel(mock(RequestModel.class))
                .build();
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responseModel, FakeRestClient.Mode.ERROR_RESPONSE_MODEL)),
                requestContext,
                requestModelFactory
        );

        FakeInboxResultListener listener = new FakeInboxResultListener(latch, FakeInboxResultListener.Mode.MAIN_THREAD);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testFetchNotification_listener_failureWithMissingMeId() throws InterruptedException {
        when(meIdStorage.get()).thenReturn(null);

        FakeInboxResultListener listener = new FakeInboxResultListener(latch);
        inbox.fetchNotifications(listener);

        latch.await();

        Assert.assertEquals(NotificationInboxException.class, listener.errorCause.getClass());
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testFetchNotification_listener_success_shouldReturnLastNotificationStatus_whenCalledTwiceInAMinute_SynchronousCalling() throws InterruptedException, JSONException {
        List<ResponseModel> responses = new ArrayList<>();
        responses.add(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1)));
        responses.add(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2)));

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);
        latch2.await();

        NotificationInboxStatus expected = InboxParseUtils.parseNotificationInboxStatus(responses.get(0).getBody());
        Assert.assertEquals(expected, listener1.resultStatus);
        Assert.assertEquals(expected, listener2.resultStatus);
    }

    @Test
    public void testFetchNotification_listener_success_shouldReturnLastNotificationStatus_whenCalledTwiceInAMinute_asynchronousCalling() throws InterruptedException, JSONException {
        List<ResponseModel> responses = new ArrayList<>();
        responses.add(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1)));
        responses.add(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2)));

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);

        latch1.await();
        latch2.await();

        NotificationInboxStatus expected = InboxParseUtils.parseNotificationInboxStatus(responses.get(0).getBody());
        Assert.assertEquals(expected, listener1.resultStatus);
        Assert.assertEquals(expected, listener2.resultStatus);
    }

    @Test
    public void testFetchNotification_listener_errorWithResponseModel_callsBufferedListener_whenCalledTwiceInAMinute_asynchronousCalling() throws InterruptedException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel response1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        ResponseModel response2 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2));
        responses.add(response1);
        responses.add(response2);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.ERROR_RESPONSE_MODEL)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);


        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);

        latch1.await();
        latch2.await();

        ResponseErrorException expected = createExceptionFrom(response1);
        Assert.assertEquals(expected, listener1.errorCause);
        Assert.assertEquals(expected, listener2.errorCause);
    }

    @Test
    public void testFetchNotification_listener_errorWithException_callsBufferedListener_whenCalledTwiceInAMinute_asynchronousCalling() throws InterruptedException {
        List<Exception> exceptions = new ArrayList<>();
        Exception exception1 = createExceptionFrom(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1)));
        Exception exception2 = createExceptionFrom(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1)));
        exceptions.add(exception1);
        exceptions.add(exception2);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(exceptions)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);


        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);

        latch1.await();
        latch2.await();

        Assert.assertEquals(exception1, listener1.errorCause);
        Assert.assertEquals(exception1, listener2.errorCause);
    }

    @Test
    public void testFetchNotification_shouldNotReturnLastNotificationStatus_whenCallTwiceAfterAMinute() throws InterruptedException, JSONException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel notificationStatusResponse1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        ResponseModel notificationStatusResponse2 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2));
        responses.add(notificationStatusResponse1);
        responses.add(notificationStatusResponse2);

        when(timestampProvider.provideTimestamp()).thenReturn(0L, 0L, 60_001L, 60_001L);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);
        latch2.await();

        Assert.assertEquals(InboxParseUtils.parseNotificationInboxStatus(notificationStatusResponse1.getBody()), listener1.resultStatus);

        Assert.assertEquals(InboxParseUtils.parseNotificationInboxStatus(notificationStatusResponse2.getBody()), listener2.resultStatus);
    }

    @Test
    public void testResetBadgeCount_shouldMakeRequest_viaRequestManager_submitNow() {
        RequestModel expected = createRequestModel(
                "https://me-inbox.eservice.emarsys.net/api/v1/notifications/" + ME_ID + "/count",
                RequestMethod.DELETE);

        inbox.resetBadgeCount(resetListenerMock);

        ArgumentCaptor<RequestModel> requestCaptor = ArgumentCaptor.forClass(RequestModel.class);
        verify(manager).submitNow(requestCaptor.capture(), any(CoreCompletionHandler.class));

        RequestModel requestModel = requestCaptor.getValue();
        Assert.assertNotNull(requestModel.getId());
        Assert.assertNotNull(requestModel.getTimestamp());
        Assert.assertEquals(expected.getUrl(), requestModel.getUrl());
        Assert.assertEquals(expected.getHeaders(), requestModel.getHeaders());
        Assert.assertEquals(expected.getMethod(), requestModel.getMethod());
    }

    @Test
    public void testResetBadgeCount_listener_success() throws InterruptedException {
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(mock(ResponseModel.class), FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(1, listener.successCount);
    }

    @Test
    public void testResetBadgeCount_listener_success_shouldBeCalledOnMainThread() throws InterruptedException {
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(mock(ResponseModel.class), FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch, FakeResetBadgeCountResultListener.Mode.MAIN_THREAD);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(1, listener.successCount);
    }

    @Test
    public void testResetBadgeCount_listener_failureWithException() throws InterruptedException {
        Exception expectedException = new Exception("FakeRestClientException");
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(expectedException)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(expectedException, listener.errorCause);
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testResetBadgeCount_listener_failureWithException_shouldBeCalledOnMainThread() throws InterruptedException {
        Exception expectedException = new Exception("FakeRestClientException");
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(expectedException)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch, FakeResetBadgeCountResultListener.Mode.MAIN_THREAD);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testResetBadgeCount_listener_failureWithResponseModel() throws InterruptedException {
        ResponseModel responseModel = new ResponseModel.Builder()
                .statusCode(400)
                .message("Bad request")
                .requestModel(mock(RequestModel.class))
                .build();
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responseModel, FakeRestClient.Mode.ERROR_RESPONSE_MODEL)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch);
        inbox.resetBadgeCount(listener);

        latch.await();

        ResponseErrorException expectedException = new ResponseErrorException(
                responseModel.getStatusCode(),
                responseModel.getMessage(),
                responseModel.getBody());

        ResponseErrorException resultException = (ResponseErrorException) listener.errorCause;
        Assert.assertEquals(expectedException.getStatusCode(), resultException.getStatusCode());
        Assert.assertEquals(expectedException.getMessage(), resultException.getMessage());
        Assert.assertEquals(expectedException.getBody(), resultException.getBody());
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testResetBadgeCount_listener_failureWithResponseModel_shouldBeCalledOnMainThread() throws InterruptedException {
        ResponseModel responseModel = new ResponseModel.Builder()
                .statusCode(400)
                .message("Bad request")
                .requestModel(mock(RequestModel.class))
                .build();
        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responseModel, FakeRestClient.Mode.ERROR_RESPONSE_MODEL)),
                requestContext,
                requestModelFactory
        );

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch, FakeResetBadgeCountResultListener.Mode.MAIN_THREAD);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testResetBadgeCount_listener_failureWithMissingMeId() throws InterruptedException {
        when(meIdStorage.get()).thenReturn(null);

        FakeResetBadgeCountResultListener listener = new FakeResetBadgeCountResultListener(latch, FakeResetBadgeCountResultListener.Mode.MAIN_THREAD);
        inbox.resetBadgeCount(listener);

        latch.await();

        Assert.assertEquals(NotificationInboxException.class, listener.errorCause.getClass());
        Assert.assertEquals(1, listener.errorCount);
    }

    @Test
    public void testResetBadgeCount_listener_shouldResetBadgeCount_onCachedInboxStatus() throws InterruptedException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel notificationStatusResponse1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        responses.add(notificationStatusResponse1);
        responses.add(createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2)));

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeResetBadgeCountResultListener listener2 = new FakeResetBadgeCountResultListener(latch2);
        inbox.resetBadgeCount(listener2);
        latch2.await();

        CountDownLatch latch3 = new CountDownLatch(1);
        FakeInboxResultListener listener3 = new FakeInboxResultListener(latch3);
        inbox.fetchNotifications(listener3);
        latch3.await();

        Assert.assertEquals(0, listener3.resultStatus.getBadgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTrackNotificationOpen_notification_mustNotBeNull() {
        inbox.trackNotificationOpen(null, mock(CompletionListener.class));
    }

    @Test
    public void testTrackNotificationOpen_requestManagerCalled_withCorrectRequestModel() {
        Map<String, String> eventAttributes = new HashMap<>();
        eventAttributes.put("message_id", MESSAGE_ID);
        eventAttributes.put("sid", SID);

        Map<String, Object> event = new HashMap<>();
        event.put("type", "internal");
        event.put("name", "inbox:open");
        event.put("timestamp", TimestampUtils.formatTimestampWithUTC(TIMESTAMP));
        event.put("attributes", eventAttributes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("clicks", new ArrayList<>());
        payload.put("viewedMessages", new ArrayList<>());
        payload.put("events", Collections.singletonList(event));

        RequestModel expected = new RequestModel.Builder(timestampProvider, uuidProvider)
                .url(RequestUrlUtils.createCustomEventUrl(requestContext))
                .payload(payload)
                .headers(RequestHeaderUtils.createBaseHeaders_V3(requestContext))
                .build();

        ArgumentCaptor<RequestModel> captor = ArgumentCaptor.forClass(RequestModel.class);

        inbox.trackNotificationOpen(notification, null);

        verify(manager).submit(captor.capture(), (CompletionListener) isNull());

        RequestModelTestUtils.assertEqualsRequestModels(expected, captor.getValue());
    }

    @Test
    public void testTrackNotificationOpen_requestManagerCalled_withCorrectCompletionListener() {
        CompletionListener completionListener = mock(CompletionListener.class);

        inbox.trackNotificationOpen(notification, completionListener);

        verify(manager).submit(any(RequestModel.class), eq(completionListener));
    }

    @Test
    public void testTrackNotificationOpen_withMissing_id() {
        Notification notification = new Notification(
                null,
                "sid",
                "title",
                "body",
                new HashMap<String, String>(),
                new JSONObject(),
                1000,
                1000);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        CompletionListener completionListener = mock(CompletionListener.class);

        inbox.trackNotificationOpen(notification, completionListener);

        verify(completionListener, Mockito.timeout(100)).onCompleted(captor.capture());

        assertEquals(IllegalArgumentException.class, captor.getValue().getClass());
        assertEquals("Id is missing!", captor.getValue().getMessage());
    }

    @Test
    public void testTrackNotificationOpen_withMissing_sid() {
        Notification notification = new Notification(
                "id",
                null,
                "title",
                "body",
                new HashMap<String, String>(),
                new JSONObject(),
                1000,
                1000);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        CompletionListener completionListener = mock(CompletionListener.class);

        inbox.trackNotificationOpen(notification, completionListener);

        verify(completionListener, Mockito.timeout(100)).onCompleted(captor.capture());

        assertEquals(IllegalArgumentException.class, captor.getValue().getClass());
        assertEquals("Sid is missing!", captor.getValue().getMessage());
    }

    @Test
    public void testTrackNotificationOpen_withMissing_id_sid() {
        Notification notification = new Notification(
                null,
                null,
                "title",
                "body",
                new HashMap<String, String>(),
                new JSONObject(),
                1000,
                1000);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        CompletionListener completionListener = mock(CompletionListener.class);

        inbox.trackNotificationOpen(notification, completionListener);

        verify(completionListener, Mockito.timeout(100)).onCompleted(captor.capture());

        assertEquals(IllegalArgumentException.class, captor.getValue().getClass());
        assertEquals("Id, Sid is missing!", captor.getValue().getMessage());
    }

    @Test
    public void testPurgeNotificationCache_resetsCache_ifCalled() throws InterruptedException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel notificationStatusResponse1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        ResponseModel notificationStatusResponse2 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2));
        responses.add(notificationStatusResponse1);
        responses.add(notificationStatusResponse2);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        inbox.purgeNotificationCache();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);
        latch2.await();

        NotificationInboxStatus expected = InboxParseUtils.parseNotificationInboxStatus(notificationStatusResponse2.getBody());
        Assert.assertEquals(expected, listener2.resultStatus);
    }

    @Test
    public void testPurgeNotificationCache_doesNotResetCache_ifCalled_twiceInAMinute() throws InterruptedException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel notificationStatusResponse1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        ResponseModel notificationStatusResponse2 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2));
        ResponseModel notificationStatusResponse3 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_3));
        responses.add(notificationStatusResponse1);
        responses.add(notificationStatusResponse2);
        responses.add(notificationStatusResponse3);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        inbox.purgeNotificationCache();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);
        latch2.await();

        inbox.purgeNotificationCache();

        CountDownLatch latch3 = new CountDownLatch(1);
        FakeInboxResultListener listener3 = new FakeInboxResultListener(latch3);
        inbox.fetchNotifications(listener3);
        latch3.await();

        NotificationInboxStatus expected = InboxParseUtils.parseNotificationInboxStatus(notificationStatusResponse2.getBody());
        Assert.assertEquals(expected, listener3.resultStatus);
    }

    @Test
    public void testPurgeNotificationCache_resetsCache_ifCalledAgainAfterMinute() throws InterruptedException {
        List<ResponseModel> responses = new ArrayList<>();
        ResponseModel notificationStatusResponse1 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_1));
        ResponseModel notificationStatusResponse2 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_2));
        ResponseModel notificationStatusResponse3 = createNotificationStatusResponse(Collections.singletonList(NOTIFICATION_STRING_3));
        responses.add(notificationStatusResponse1);
        responses.add(notificationStatusResponse2);
        responses.add(notificationStatusResponse3);

        when(timestampProvider.provideTimestamp()).thenReturn(
                100_000L, 100_001L, 100_002L, 100_003L, 200_000L, 200_001L, 200_002L);

        inbox = new InboxInternal_V2(
                requestManagerWithRestClient(new FakeRestClient(responses, FakeRestClient.Mode.SUCCESS)),
                requestContext,
                requestModelFactory
        );

        CountDownLatch latch1 = new CountDownLatch(1);
        FakeInboxResultListener listener1 = new FakeInboxResultListener(latch1);
        inbox.fetchNotifications(listener1);
        latch1.await();

        inbox.purgeNotificationCache();

        CountDownLatch latch2 = new CountDownLatch(1);
        FakeInboxResultListener listener2 = new FakeInboxResultListener(latch2);
        inbox.fetchNotifications(listener2);
        latch2.await();

        inbox.purgeNotificationCache();

        CountDownLatch latch3 = new CountDownLatch(1);
        FakeInboxResultListener listener3 = new FakeInboxResultListener(latch3);
        inbox.fetchNotifications(listener3);
        latch3.await();

        NotificationInboxStatus expected = InboxParseUtils.parseNotificationInboxStatus(notificationStatusResponse3.getBody());
        Assert.assertEquals(expected, listener3.resultStatus);
    }

    private RequestModel createRequestModel(String path, RequestMethod method) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-ems-me-application-code", requestContext.getApplicationCode());
        headers.putAll(RequestHeaderUtils_Old.createDefaultHeaders(requestContext));
        headers.putAll(RequestHeaderUtils_Old.createBaseHeaders_V2(requestContext));

        return new RequestModel.Builder(timestampProvider, uuidProvider)
                .url(path)
                .headers(headers)
                .method(method)
                .build();
    }

    private ResponseModel createSuccessResponse() {

        List<String> notificationStrings = new ArrayList<>();
        notificationStrings.add(NOTIFICATION_STRING_1);
        notificationStrings.add(NOTIFICATION_STRING_2);
        notificationStrings.add(NOTIFICATION_STRING_3);

        return createNotificationStatusResponse(notificationStrings);
    }

    private ResponseErrorException createExceptionFrom(ResponseModel responseModel) {
        return new ResponseErrorException(
                responseModel.getStatusCode(),
                responseModel.getMessage(),
                responseModel.getBody());
    }

    private ResponseModel createNotificationStatusResponse(List<String> notifications) {
        if (notifications == null || notifications.size() == 0) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(notifications.get(0));
        if (notifications.size() > 1) {
            for (int i = 1; i <= notifications.size() - 1; i++) {
                stringBuilder.append(",").append(notifications.get(i));
            }
        }
        String json = "{\"badge_count\": 300, \"notifications\": [" + stringBuilder.toString() + "]}";
        return new ResponseModel.Builder()
                .statusCode(200)
                .message("OK")
                .body(json)
                .requestModel(mock(RequestModel.class))
                .build();
    }

    private List<Notification> createNotificationList() throws JSONException {
        Map<String, String> customData1 = new HashMap<>();
        customData1.put("data1", "dataValue1");
        customData1.put("data2", "dataValue2");

        JSONObject rootParams1 = new JSONObject();
        rootParams1.put("param1", "paramValue1");
        rootParams1.put("param2", "paramValue2");

        Map<String, String> customData2 = new HashMap<>();
        customData2.put("data3", "dataValue3");
        customData2.put("data4", "dataValue4");

        JSONObject rootParams2 = new JSONObject();
        rootParams2.put("param3", "paramValue3");
        rootParams2.put("param4", "paramValue4");


        Map<String, String> customData3 = new HashMap<>();
        customData3.put("data5", "dataValue5");
        customData3.put("data6", "dataValue6");

        JSONObject rootParams3 = new JSONObject();
        rootParams3.put("param5", "paramValue5");
        rootParams3.put("param6", "paramValue6");

        return Arrays.asList(
                new Notification("id1", "sid1", "title1", null, customData1, rootParams1, 300, 10000000),
                new Notification("id2", "sid2", "title2", null, customData2, rootParams2, 200, 30000000),
                new Notification("id3", "sid3", "title3", null, customData3, rootParams3, 100, 25000000)

        );
    }

    private List<Notification> createCacheList() throws JSONException {
        Map<String, String> customData4 = new HashMap<>();
        customData4.put("data7", "dataValue7");
        customData4.put("data8", "dataValue8");

        JSONObject rootParams4 = new JSONObject();
        rootParams4.put("param7", "paramValue7");
        rootParams4.put("param8", "paramValue8");

        Map<String, String> customData5 = new HashMap<>();
        customData5.put("data9", "dataValue9");
        customData5.put("data10", "dataValue10");

        JSONObject rootParams5 = new JSONObject();
        rootParams5.put("param9", "paramValue9");
        rootParams5.put("param10", "paramValue10");

        return Arrays.asList(
                new Notification("id4", "sid4", "title4", null, customData4, rootParams4, 400, 40000000),
                new Notification("id5", "sid5", "title5", null, customData5, rootParams5, 500, 50000000)
        );
    }

    private void clearNotificationCache() throws Exception {
        Field cacheField = NotificationCache.class.getDeclaredField("internalCache");
        cacheField.setAccessible(true);
        ((List) cacheField.get(null)).clear();
    }

    @SuppressWarnings("unchecked")
    private RequestManager requestManagerWithRestClient(RestClient restClient) {
        return new RequestManager(
                mock(Handler.class),
                mock(Repository.class),
                mock(Repository.class),
                mock(Worker.class),
                restClient,
                mock(Registry.class),
                mock(CompletionProxyFactory.class));
    }

}