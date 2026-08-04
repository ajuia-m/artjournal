package com.ajuia.artjournal.data.session

import com.ajuia.artjournal.data.remote.ApiClientFactory
import com.ajuia.artjournal.data.remote.ApiClients
import com.ajuia.artjournal.data.remote.RemoteFailure
import com.ajuia.artjournal.data.remote.TokenPairDto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerSessionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var clients: ApiClients
    private lateinit var repository: ServerSessionRepository
    private val activatedSchools = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemorySessionStore()
        clients = ApiClientFactory.create(
            baseUrl = server.url("/").toString(),
            sessionStore = store,
            logRequests = false
        )
        repository = ServerSessionRepository(
            authenticationApi = clients.authenticationApi,
            accountApi = clients.accountApi,
            schoolsApi = clients.schoolsApi,
            tokenManager = clients.tokenManager,
            sessionStore = store,
            onSchoolActivated = { schoolId -> activatedSchools.add(schoolId) }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login loads user schools and persists explicit school selection`() = runBlocking {
        server.enqueue(jsonResponse(TOKENS))
        server.enqueue(jsonResponse(CURRENT_USER))
        server.enqueue(jsonResponse(SCHOOLS))

        val session = repository.login("teacher", "password")

        assertEquals("refresh-1", store.refreshToken)
        assertEquals("Teacher One", session.user.displayName)
        assertEquals(listOf("Art School"), session.schools.map { it.name })
        assertNull(session.selectedSchool)

        val selected = repository.selectSchool(session, SCHOOL_ID)

        assertEquals(SCHOOL_ID, store.selectedSchoolId)
        assertEquals("teacher", selected.selectedSchool?.role)
        assertEquals(listOf(SCHOOL_ID), activatedSchools)
        assertEquals("/api/v1/auth/token/", server.takeRequest().path)
        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `invalid credentials are represented by a typed failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"No active account\"}"))

        val failure = runCatching { repository.login("teacher", "wrong") }.exceptionOrNull()

        assertTrue(failure is RemoteFailure.InvalidCredentials)
        assertNull(store.refreshToken)
    }

    @Test
    fun `restore rotates refresh token and restores selected school`() = runBlocking {
        store.refreshToken = "refresh-1"
        store.selectedSchoolId = SCHOOL_ID
        server.enqueue(jsonResponse("{\"access\":\"access-2\",\"refresh\":\"refresh-2\"}"))
        server.enqueue(jsonResponse(CURRENT_USER))
        server.enqueue(jsonResponse(SCHOOLS))

        val restored = repository.restore()

        assertEquals("refresh-2", store.refreshToken)
        assertEquals(SCHOOL_ID, restored?.selectedSchool?.id)
        assertEquals(listOf(SCHOOL_ID), activatedSchools)
    }

    @Test
    fun `authenticated request refreshes once after an expired access token`() = runBlocking {
        clients.tokenManager.acceptTokenPair(TokenPairDto("expired-access", "refresh-1"))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/me/" &&
                    request.getHeader("Authorization") == "Bearer expired-access" ->
                    MockResponse().setResponseCode(401)
                request.path == "/api/v1/auth/token/refresh/" ->
                    jsonResponse("{\"access\":\"access-2\",\"refresh\":\"refresh-2\"}")
                request.path == "/api/v1/auth/me/" &&
                    request.getHeader("Authorization") == "Bearer access-2" ->
                    jsonResponse(CURRENT_USER)
                else -> MockResponse().setResponseCode(500)
            }
        }

        val user = clients.accountApi.currentUser()

        assertEquals("teacher", user.username)
        assertEquals("refresh-2", store.refreshToken)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `revoked refresh token clears local session and selected school`() = runBlocking {
        store.refreshToken = "revoked"
        store.selectedSchoolId = SCHOOL_ID
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"Token is invalid\"}"))

        val restored = repository.restore()

        assertNull(restored)
        assertNull(store.refreshToken)
        assertNull(store.selectedSchoolId)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private class InMemorySessionStore : SessionStore {
        var refreshToken: String? = null
        var selectedSchoolId: String? = null

        override fun readRefreshToken(): String? = refreshToken
        override fun writeRefreshToken(token: String) {
            refreshToken = token
        }
        override fun clearRefreshToken() {
            refreshToken = null
        }
        override fun readSelectedSchoolId(): String? = selectedSchoolId
        override fun writeSelectedSchoolId(schoolId: String) {
            selectedSchoolId = schoolId
        }
        override fun clearSelectedSchoolId() {
            selectedSchoolId = null
        }
    }

    private companion object {
        const val SCHOOL_ID = "11111111-1111-1111-1111-111111111111"
        const val TOKENS = """{"access":"access-1","refresh":"refresh-1"}"""
        const val CURRENT_USER = """
            {
              "id":"22222222-2222-2222-2222-222222222222",
              "username":"teacher",
              "email":"teacher@example.com",
              "first_name":"Teacher",
              "last_name":"One",
              "memberships":[{
                "id":"33333333-3333-3333-3333-333333333333",
                "school_id":"$SCHOOL_ID",
                "school_name":"Art School",
                "school_slug":"art-school",
                "role":"teacher",
                "teaching_assignments":[]
              }]
            }
        """
        const val SCHOOLS = """
            [{
              "id":"$SCHOOL_ID",
              "name":"Art School",
              "slug":"art-school",
              "default_currency":"RUB"
            }]
        """
    }
}
