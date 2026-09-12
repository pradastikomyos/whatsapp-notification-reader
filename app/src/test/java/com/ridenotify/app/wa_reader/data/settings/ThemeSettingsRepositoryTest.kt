package com.ridenotify.app.wa_reader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ThemeSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: ThemeSettingsRepository

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStoreFile = File(temporaryFolder.root, "theme.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        repository = ThemeSettingsRepository(RuntimeEnvironment.getApplication(), dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `theme follows system until user selects an override`() = runTest {
        assertEquals(ThemeMode.SYSTEM, repository.observeThemeMode().first())

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())
    }
}
