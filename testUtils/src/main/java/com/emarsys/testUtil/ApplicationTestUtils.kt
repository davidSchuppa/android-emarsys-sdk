package com.emarsys.testUtil

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.InstrumentationRegistry
import com.emarsys.testUtil.mockito.MockitoTestUtils.whenever
import org.mockito.Mockito.spy

object ApplicationTestUtils {

    @JvmStatic
    val applicationDebug: Application
        get() = getApplication { flags = ApplicationInfo.FLAG_DEBUGGABLE }

    @JvmStatic
    val applicationRelease: Application
        get() = getApplication { flags = 0 }

    private fun getApplication(init: ApplicationInfo.() -> Unit) = (spy(InstrumentationRegistry.getTargetContext().applicationContext) as Application).also {
        whenever(it.applicationInfo).thenReturn(ApplicationInfo().apply(init))
    }

}
