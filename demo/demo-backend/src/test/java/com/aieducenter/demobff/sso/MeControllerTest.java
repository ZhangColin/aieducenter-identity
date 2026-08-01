package com.aieducenter.demobff.sso;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock OidcClient oidcClient;
    @Mock BffSessionStore sessionStore;
    @InjectMocks MeController controller;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void me_without_cookie_is_401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns_claims_for_valid_session() throws Exception {
        when(sessionStore.get("sid")).thenReturn(Optional.of(new BffSession("a", "IDT", "r")));
        when(oidcClient.decodeIdToken("IDT")).thenReturn(
            new OidcClient.IdTokenClaims("u1", "a@b.c", "A", "pic"));

        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie("demo_session", "sid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("u1"))
            .andExpect(jsonPath("$.email").value("a@b.c"))
            .andExpect(jsonPath("$.nickname").value("A"));
    }
}
