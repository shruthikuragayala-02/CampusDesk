package com.campusdesk;

import com.campusdesk.domain.Category;
import com.campusdesk.domain.Location;
import com.campusdesk.domain.Ticket;
import com.campusdesk.domain.TicketEventType;
import com.campusdesk.domain.TicketStatus;
import com.campusdesk.repository.CategoryRepository;
import com.campusdesk.repository.LocationRepository;
import com.campusdesk.repository.SlaPolicyRepository;
import com.campusdesk.repository.TicketRepository;
import com.campusdesk.repository.TicketEventRepository;
import com.campusdesk.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostgresCampusDeskIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("campusdesk_test")
            .withUsername("campusdesk_test")
            .withPassword("campusdesk_test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void flywayCreatesPostgresSchemaAndCatalogSeedData() {
        assertThat(categoryRepository.count()).isEqualTo(5);
        assertThat(locationRepository.count()).isEqualTo(5);
        assertThat(slaPolicyRepository.count()).isEqualTo(20);
    }

    @Test
    void postgresSupportsRoleChecksSlaTicketWorkflowAndAuditEvents() throws Exception {
        String studentToken = login("student", "Student@123");
        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        Category category = categoryRepository.findAll().getFirst();
        Location location = locationRepository.findAll().getFirst();
        String createResponse = mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Postgres integration ticket\",\"description\":\"Verify PostgreSQL ticket flow\","
                    + "\"priority\":\"HIGH\",\"categoryId\":\"" + category.getId()
                    + "\",\"locationId\":\"" + location.getId() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID ticketId = UUID.fromString(JsonPath.read(createResponse, "$.id"));
        Instant responseDeadline = Instant.parse(JsonPath.read(createResponse, "$.responseDeadline"));
        Instant resolutionDeadline = Instant.parse(JsonPath.read(createResponse, "$.dueAt"));
        assertThat(responseDeadline).isBefore(resolutionDeadline);

        String staffToken = login("staff", "Staff@123");
        UUID staffId = userRepository.findByUsername("staff").orElseThrow().getId();
        mockMvc.perform(post("/api/tickets/" + ticketId + "/assign")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"" + staffId + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk());

        List<TicketEventType> eventTypes = ticketEventRepository.findByTicket_Id(ticketId).stream()
            .map(event -> event.getEventType())
            .toList();
        assertThat(eventTypes).contains(
            TicketEventType.CREATED,
            TicketEventType.ASSIGNED,
            TicketEventType.STATUS_CHANGED
        );
    }

    @Test
    void postgresEnforcesTicketOwnershipForReadsAndMutations() throws Exception {
        String ownerToken = login("student", "Student@123");
        Category category = categoryRepository.findAll().getFirst();
        Location location = locationRepository.findAll().getFirst();
        String createResponse = mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Private PostgreSQL ticket\",\"description\":\"Must stay private\","
                    + "\"priority\":\"HIGH\",\"categoryId\":\"" + category.getId()
                    + "\",\"locationId\":\"" + location.getId() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID ticketId = UUID.fromString(JsonPath.read(createResponse, "$.id"));

        String otherUsername = "postgres-owner-" + UUID.randomUUID();
        String registrationResponse = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + otherUsername + "\",\"email\":\"" + otherUsername
                    + "@campusdesk.local\",\"fullName\":\"Other PostgreSQL Student\","
                    + "\"password\":\"Password@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String otherToken = JsonPath.read(registrationResponse, "$.token");

        mockMvc.perform(get("/api/tickets/" + ticketId))
            .andExpect(status().isUnauthorized());
        String forbiddenResponse = mockMvc.perform(get("/api/tickets/" + ticketId)
                .param("userId", userRepository.findByUsername("student").orElseThrow().getId().toString())
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(forbiddenResponse)
            .doesNotContain("Private PostgreSQL ticket", "Must stay private", "student@campusdesk.local",
                "trace", "stackTrace");
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"unauthorized PostgreSQL comment\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Unauthorized replacement\"}"))
            .andExpect(status().isMethodNotAllowed());

        Ticket unchanged = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(unchanged.getReporter().getId())
            .isEqualTo(userRepository.findByUsername("student").orElseThrow().getId());
        assertThat(unchanged.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketEventRepository.findByTicket_Id(ticketId).stream()
            .map(event -> event.getEventType()))
            .containsExactly(TicketEventType.CREATED);
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return JsonPath.read(response, "$.token");
    }
}
