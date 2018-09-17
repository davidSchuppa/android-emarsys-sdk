package com.emarsys.predict

import com.emarsys.core.provider.timestamp.TimestampProvider
import com.emarsys.core.provider.uuid.UUIDProvider
import com.emarsys.core.request.RequestManager
import com.emarsys.core.shard.ShardModel
import com.emarsys.core.storage.KeyValueStore
import com.emarsys.predict.api.model.PredictCartItem
import com.emarsys.testUtil.TimeoutUtils
import junit.framework.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*

class PredictInternalTest {

    companion object {
        const val TTL = Long.MAX_VALUE
        const val ID = "id"
    }

    @Rule
    @JvmField
    var timeout = TimeoutUtils.timeoutRule

    private lateinit var mockKeyValueStore: KeyValueStore
    private lateinit var predictInternal: PredictInternal
    private lateinit var mockRequestManager: RequestManager
    private lateinit var mockTimestampProvider: TimestampProvider
    private lateinit var mockUuidProvider: UUIDProvider

    @Before
    fun init() {
        mockKeyValueStore = mock(KeyValueStore::class.java)
        mockRequestManager = mock(RequestManager::class.java)
        mockTimestampProvider = mock(TimestampProvider::class.java)
        mockUuidProvider = mock(UUIDProvider::class.java)

        `when`(mockTimestampProvider.provideTimestamp()).thenReturn(1L)
        `when`(mockUuidProvider.provideId()).thenReturn(ID)

        predictInternal = PredictInternal(mockKeyValueStore, mockRequestManager, mockUuidProvider, mockTimestampProvider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructor_keyValueStore_shouldNotBeNull() {
        PredictInternal(null, mockRequestManager, mockUuidProvider, mockTimestampProvider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructor_repository_shouldNotBeNull() {
        PredictInternal(mockKeyValueStore, null, mockUuidProvider, mockTimestampProvider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructor_uuidProvider_shouldNotBeNull() {
        PredictInternal(mockKeyValueStore, mockRequestManager, null, mockTimestampProvider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructor_timestampProvider_shouldNotBeNull() {
        PredictInternal(mockKeyValueStore, mockRequestManager, mockUuidProvider, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSetCustomer_customerId_mustNotBeNull() {
        predictInternal.setCustomer(null)
    }

    @Test
    fun testSetCustomer_shouldPersistsWithKeyValueStore() {
        val customerId = "customerId"

        predictInternal.setCustomer(customerId)

        verify(mockKeyValueStore).putString("predict_customerId", customerId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testTrackCart_items_mustNotBeNull() {
        predictInternal.trackCart(null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testTrackCart_itemElements_mustNotBeNull() {
        predictInternal.trackCart(listOf(null))
    }

    @Test
    fun testTrackCart_returnsShardId() {
        Assert.assertEquals(ID, predictInternal.trackCart(listOf()))
    }

    @Test
    fun testTrackCart_shouldCallRequestManager_withCorrectShardModel() {
        val expectedShardModel =
                ShardModel(mockUuidProvider.provideId(),
                        "predict_cart",
                        mapOf(
                                "cv" to 1,
                                "ca" to "i:itemId1,p:200.0,q:100.0|i:itemId2,p:201.0,q:101.0"
                        ),
                        mockTimestampProvider.provideTimestamp(),
                        TTL)

        predictInternal.trackCart(listOf(
                PredictCartItem("itemId1", 200.0, 100.0),
                PredictCartItem("itemId2", 201.0, 101.0)
        ))

        verify(mockRequestManager).submit(expectedShardModel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testTrackItemView_itemId_mustNotBeNull() {
        predictInternal.trackItemView(null)
    }

    @Test
    fun testTrackItemView_returnsShardId() {
        Assert.assertEquals(ID, predictInternal.trackItemView("itemId"))
    }

    @Test
    fun testTrackItemView_shouldCallRequestManager_withCorrectShardModel() {
        val itemId = "itemId"

        val expectedShardModel = ShardModel(
                mockUuidProvider.provideId(),
                "predict_item_view",
                mapOf("v" to "i:$itemId"),
                mockTimestampProvider.provideTimestamp(),
                TTL)

        predictInternal.trackItemView(itemId)

        verify(mockRequestManager).submit(expectedShardModel)
    }

}