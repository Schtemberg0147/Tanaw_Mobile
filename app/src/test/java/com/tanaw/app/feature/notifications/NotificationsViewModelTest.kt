package com.tanaw.app.feature.notifications

import app.cash.turbine.test
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads notifications list and updates unread count on mark as read`() = runTest {
        val fakeSupabase = createFakeSupabaseClient()

        val viewModel = NotificationsViewModel(
            supabase = fakeSupabase,
            ioDispatcher = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is NotificationsUiState.Loading) {
                state = awaitItem()
            }

            assertTrue("Expected Content state", state is NotificationsUiState.Content)
            val content = state as NotificationsUiState.Content
            assertEquals(2, content.notifications.size)
            assertEquals(2, content.unreadCount)

            // Mark single notification as read
            viewModel.onEvent(NotificationsUiEvent.MarkAsRead(content.notifications[0].id))

            state = awaitItem()
            val updated = state as NotificationsUiState.Content
            assertEquals(1, updated.unreadCount)
            assertTrue(updated.notifications[0].isRead)
        }
    }

    @Test
    fun `markAllAsRead clears all unread indicators`() = runTest {
        val fakeSupabase = createFakeSupabaseClient()

        val viewModel = NotificationsViewModel(
            supabase = fakeSupabase,
            ioDispatcher = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is NotificationsUiState.Loading) {
                state = awaitItem()
            }

            viewModel.onEvent(NotificationsUiEvent.MarkAllAsRead)

            state = awaitItem()
            val content = state as NotificationsUiState.Content
            assertEquals(0, content.unreadCount)
            assertTrue(content.notifications.all { it.isRead })
        }
    }

    private fun createFakeSupabaseClient(): SupabaseClient {
        return createSupabaseClient("https://fake.supabase.co", "fake-key") {}
    }
}
