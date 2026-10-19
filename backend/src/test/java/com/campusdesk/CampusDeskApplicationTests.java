package com.campusdesk;

import com.campusdesk.domain.Category;
import com.campusdesk.domain.Location;
import com.campusdesk.domain.Role;
import com.campusdesk.domain.Ticket;
import com.campusdesk.domain.TicketEventType;
import com.campusdesk.domain.TicketStatus;
import com.campusdesk.repository.CategoryRepository;
import com.campusdesk.repository.LocationRepository;
import com.campusdesk.repository.TicketRepository;
import com.campusdesk.repository.TicketEventRepository;
import com.campusdesk.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CampusDeskApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketEventRepository ticketEventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void contextLoads() {
        assertThat(categoryRepository).isNotNull();
        assertThat(locationRepository).isNotNull();
    }

    @Test
    void loginWorksForAllDemoRoles() throws Exception {
        for (String username : java.util.List.of("student", "staff", "admin")) {
            String password = switch (username) {
                case "student" -> "Student@123";
                case "staff" -> "Staff@123";
                default -> "Admin@123";
            };

            String response = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

            assertThat(JsonPath.<String>read(response, "$.token")).isNotBlank();
        }
    }

    @Test
    void dashboardAndAdminConfigurationAreRoleRestricted() throws Exception {
        String studentToken = login("student", "Student@123");
        String staffToken = login("staff", "Staff@123");

        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/categories")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/categories")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/tickets/my"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/categories"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginAndAdminInputReturnSafeClientErrors() throws Exception {
        String invalidLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student\",\"password\":\"incorrect\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(invalidLogin).doesNotContain("BadCredentialsException", "stackTrace", "password");

        String adminToken = login("admin", "Admin@123");
        String categoryId = categoryRepository.findAll().getFirst().getId().toString();
        String invalidPolicy = mockMvc.perform(post("/api/admin/sla-policies")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId
                    + "\",\"priority\":\"HIGH\",\"responseMinutes\":60,\"resolutionMinutes\":30}"))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(invalidPolicy).doesNotContain("stackTrace", "SQLException", "password");

        mockMvc.perform(post("/api/admin/categories")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(81) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void disabledOrDeletedUsersCannotContinueUsingPreviouslyIssuedTokens() throws Exception {
        String username = "disabled-" + UUID.randomUUID();
        String registration = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                    + "@campusdesk.local\",\"fullName\":\"Disabled User\",\"password\":\"Password@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String token = JsonPath.read(registration, "$.token");

        com.campusdesk.domain.User user = userRepository.findByUsername(username).orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"Password@123\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/my")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());

        userRepository.delete(user);
        userRepository.flush();
        mockMvc.perform(get("/api/tickets/my")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanCreateAndListTickets() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student\",\"password\":\"Student@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token = JsonPath.read(loginResponse, "$.token");

        String createdTicket = mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test ticket\",\"description\":\"Need a quick fix\",\"priority\":\"HIGH\",\"categoryId\":\"" + category.getId() + "\",\"locationId\":\"" + location.getId() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String responseDeadline = JsonPath.read(createdTicket, "$.responseDeadline");
        String dueAt = JsonPath.read(createdTicket, "$.dueAt");
        assertThat(responseDeadline).isNotBlank();
        assertThat(dueAt).isNotBlank();
        assertThat(java.time.Instant.parse(responseDeadline)).isBefore(java.time.Instant.parse(dueAt));
        String ticketId = JsonPath.read(createdTicket, "$.id");

        mockMvc.perform(get("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        String myTicketsResponse = mockMvc.perform(get("/api/tickets/my")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(myTicketsResponse).contains("Test ticket");
    }

    @Test
    void ticketListsArePagedAndPageSizeIsBounded() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        createTicket("student", "Student@123", category, location);
        createTicket("student", "Student@123", category, location);
        String token = login("student", "Student@123");

        String firstPage = mockMvc.perform(get("/api/tickets/my").param("page", "0").param("size", "1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String secondPage = mockMvc.perform(get("/api/tickets/my").param("page", "1").param("size", "1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(JsonPath.<java.util.List<Object>>read(firstPage, "$")).hasSize(1);
        assertThat(JsonPath.<java.util.List<Object>>read(secondPage, "$")).hasSize(1);
        assertThat(JsonPath.<String>read(firstPage, "$[0].id"))
            .isNotEqualTo(JsonPath.<String>read(secondPage, "$[0].id"));

        mockMvc.perform(get("/api/tickets/my").param("size", "101")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets/my").param("page", "-1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets").param("search", "x".repeat(201))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotReadAnotherStudentsTicket() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        String ticketResponse = createTicket("student", "Student@123", category, location);
        UUID ticketId = UUID.fromString(JsonPath.read(ticketResponse, "$.id"));

        String otherUsername = "student-" + java.util.UUID.randomUUID();
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + otherUsername + "\",\"email\":\"" + otherUsername
                    + "@campusdesk.local\",\"fullName\":\"Other Student\",\"password\":\"Password@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String otherStudentToken = JsonPath.read(registerResponse, "$.token");

        mockMvc.perform(get("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotReadOrMutateAnotherStudentsTicketByChangingIdsOrRequestFields() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        String ticketResponse = createTicket("student", "Student@123", category, location);
        UUID ticketId = UUID.fromString(JsonPath.read(ticketResponse, "$.id"));
        String otherUsername = "owner-check-" + UUID.randomUUID();
        String registrationResponse = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + otherUsername + "\",\"email\":\"" + otherUsername
                    + "@campusdesk.local\",\"fullName\":\"Private Test Account\",\"password\":\"Password@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String otherStudentToken = JsonPath.read(registrationResponse, "$.token");

        String forbiddenRead = mockMvc.perform(get("/api/tickets/" + ticketId)
                .param("userId", userRepository.findByUsername("student").orElseThrow().getId().toString())
                .header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(forbiddenRead)
            .doesNotContain("Workflow test ticket", "Need service", "student@campusdesk.local", "trace", "stackTrace");
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"reporterId\":\""
                    + userRepository.findByUsername("student").orElseThrow().getId()
                    + "\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"unauthorized comment\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/" + ticketId + "/assign")
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"" + userRepository.findByUsername("staff").orElseThrow().getId() + "\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherStudentToken))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherStudentToken)
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

    @Test
    void registrationAndTicketFieldsCannotGrantRolesOrChangeOwnership() throws Exception {
        String username = "role-check-" + UUID.randomUUID();
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                    + "@campusdesk.local\",\"fullName\":\"Role Check Student\",\"password\":\"Password@123\","
                    + "\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(JsonPath.<String>read(response, "$.role")).isEqualTo("STUDENT");
        assertThat(userRepository.findByUsername(username).orElseThrow().getRole()).isEqualTo(Role.STUDENT);

        String token = JsonPath.read(response, "$.token");
        mockMvc.perform(get("/api/admin/categories")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        String ticketJson = "{\"title\":\"Spoofed reporter fields\",\"description\":\"Ownership comes from auth\","
            + "\"priority\":\"HIGH\",\"categoryId\":\"" + category.getId()
            + "\",\"locationId\":\"" + location.getId()
            + "\",\"reporterId\":\"" + userRepository.findByUsername("admin").orElseThrow().getId()
            + "\",\"assigneeId\":\"" + userRepository.findByUsername("staff").orElseThrow().getId() + "\"}";
        String ticketResponse = mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID ticketId = UUID.fromString(JsonPath.read(ticketResponse, "$.id"));
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getReporter().getId())
            .isEqualTo(userRepository.findByUsername(username).orElseThrow().getId());
        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void invalidExpiredAndTamperedJwtTokensAreRejectedWithoutSensitiveErrors() throws Exception {
        String validStudentToken = login("student", "Student@123");
        String[] tokenParts = validStudentToken.split("\\.");
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + "."
            + (tokenParts[2].charAt(0) == 'A' ? "B" : "A") + tokenParts[2].substring(1);
        String expiredToken = Jwts.builder()
            .subject("student")
            .claim("role", "STUDENT")
            .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
            .expiration(Date.from(Instant.now().minusSeconds(1800)))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();

        for (String token : java.util.List.of("not-a-jwt", tamperedToken, expiredToken)) {
            String errorBody = mockMvc.perform(get("/api/tickets/my")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
            assertThat(errorBody)
                .doesNotContain("trace", "stackTrace", "exception", jwtSecret, "Student@123");
        }
    }

    @Test
    void signedAdminRoleClaimDoesNotElevateStudentAccount() throws Exception {
        String forgedRoleToken = Jwts.builder()
            .subject("student")
            .claim("role", "ADMIN")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();

        mockMvc.perform(get("/api/admin/categories")
                .header("Authorization", "Bearer " + forgedRoleToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentAndStaffAreDeniedAdminOnlyOperationsWhileStaffAssigneeLookupRemainsAvailable() throws Exception {
        String studentToken = login("student", "Student@123");
        String staffToken = login("staff", "Staff@123");
        for (String path : java.util.List.of(
            "/api/admin/dashboard", "/api/admin/categories", "/api/admin/locations", "/api/admin/sla-policies"
        )) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/admin/categories")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Unauthorized category\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/categories")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Unauthorized category\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isOk());
    }

    @Test
    void staffCanAssignAndUpdateTicketStatus() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        String ticketResponse = createTicket("student", "Student@123", category, location);
        UUID ticketId = UUID.fromString(JsonPath.read(ticketResponse, "$.id"));
        String staffLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff\",\"password\":\"Staff@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String staffToken = JsonPath.read(staffLogin, "$.token");
        String staffId = userRepository.findByUsername("staff").orElseThrow().getId().toString();

        String assignedResponse = mockMvc.perform(post("/api/tickets/" + ticketId + "/assign")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"" + staffId + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(JsonPath.<String>read(assignedResponse, "$.status")).isEqualTo("ASSIGNED");

        String statusResponse = mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(JsonPath.<String>read(statusResponse, "$.status")).isEqualTo("IN_PROGRESS");
        String resolvedResponse = mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"resolutionNotes\":\"Work completed\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(JsonPath.<String>read(resolvedResponse, "$.status")).isEqualTo("RESOLVED");
        assertThat(ticketEventRepository.findByTicket_Id(ticketId).stream()
            .map(event -> event.getEventType()))
            .contains(TicketEventType.CREATED, TicketEventType.ASSIGNED,
                TicketEventType.STATUS_CHANGED, TicketEventType.RESOLVED);
    }

    @Test
    void rejectsInvalidStatusTransition() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Location location = locationRepository.findAll().stream().findFirst().orElseThrow();
        String ticketResponse = createTicket("student", "Student@123", category, location);
        String ticketId = JsonPath.read(ticketResponse, "$.id");
        String staffLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff\",\"password\":\"Staff@123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String staffToken = JsonPath.read(staffLogin, "$.token");

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isBadRequest());
    }

    private String createTicket(String username, String password, Category category, Location location) throws Exception {
        String token = login(username, password);

        return mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Workflow test ticket\",\"description\":\"Need service\",\"priority\":\"HIGH\",\"categoryId\":\""
                    + category.getId() + "\",\"locationId\":\"" + location.getId() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private String login(String username, String password) throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return JsonPath.read(loginResponse, "$.token");
    }
}
