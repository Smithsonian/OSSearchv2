package edu.si.ossearch.collection.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies that an invalid {@code Collection.name} is rejected at the request boundary of
 * {@link CollectionController#createCollection} as a 400, rather than escaping as a Hibernate
 * {@code ConstraintViolationException} at flush time - which the controller's catch-all
 * {@code @ExceptionHandler(Exception.class)} would report as an opaque 500.
 * <p>
 * Uses {@link MockMvcBuilders#standaloneSetup} rather than {@code @SpringBootTest}: no
 * application context is bootstrapped, which keeps this in line with the module's no-Spring-context
 * test style. The controller's collaborators are left null on purpose - {@code @Valid} rejects the
 * body before the handler method body ever runs, so reaching them would itself be a failure.
 */
class CollectionControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CollectionController()).build();
    }

    private MvcResult postName(String rawJsonName) throws Exception {
        return mockMvc.perform(post("/api/collection2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":" + rawJsonName + "}"))
                .andReturn();
    }

    @Test
    @DisplayName("a name containing a slash is a 400, and the body names the offending field")
    void slashInNameIsBadRequest() throws Exception {
        MvcResult result = postName("\"a/b\"");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("name");
        assertThat(body).contains("slashes");
    }

    @Test
    @DisplayName("a traversal name is a 400")
    void traversalNameIsBadRequest() throws Exception {
        assertThat(postName("\"../evil\"").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("a blank name is a 400")
    void blankNameIsBadRequest() throws Exception {
        assertThat(postName("\"\"").getResponse().getStatus()).isEqualTo(400);
    }
}
