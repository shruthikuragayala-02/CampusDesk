package com.campusdesk;

import com.campusdesk.controller.AuthController;
import com.campusdesk.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@ExtendWith(OutputCaptureExtension.class)
class ProductionSecurityIntegrationTest {
    private static final String DB_NAME = "campusdesk-production-security-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private ServletContext servletContext;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",
            () -> "jdbc:h2:mem:" + DB_NAME + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        properties.add("JWT_SECRET", () -> "production-security-test-signing-key-32-bytes-minimum");
        properties.add("CORS_ALLOWED_ORIGINS", () -> "https://campusdesk.pages.dev");
        properties.add("ADMIN_USERNAME", () -> "production-security-admin");
        properties.add("ADMIN_EMAIL", () -> "production-security-admin@example.invalid");
        properties.add("ADMIN_FULL_NAME", () -> "Production Security Test Admin");
        properties.add("ADMIN_PASSWORD", () -> "test-only-admin-password");
        properties.add("DB_URL", () -> "jdbc:h2:mem:" + DB_NAME
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        properties.add("DB_USERNAME", () -> "sa");
        properties.add("DB_PASSWORD", () -> "");
    }

    @Test
    void productionProfileAllowsPublicRegistrationAndKeepsPrivateEndpointsProtected() throws Exception {
        var filters = filterChainProxy.getFilters("/api/auth/register");
        assertThat(filterChainProxy.getFilterChains()).hasSize(1);
        assertThat(filters).anyMatch(JwtAuthenticationFilter.class::isInstance);
        assertThat(filters.stream()
            .filter(JwtAuthenticationFilter.class::isInstance)
            .count()).isEqualTo(1);

        String username = "prod-register-" + java.util.UUID.randomUUID();
        var registration = mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                    + "@example.invalid\",\"fullName\":\"Production Profile Test\","
                    + "\"password\":\"test-only-password\"}"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(registration.getHandler()).isInstanceOf(HandlerMethod.class);
        HandlerMethod handler = (HandlerMethod) registration.getHandler();
        assertThat(handler.getBeanType()).isEqualTo(AuthController.class);
        assertThat(handler.getMethod().getName()).isEqualTo("register");

        mockMvc.perform(get("/api/tickets/my"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/categories")
                .contentType("application/json")
                .content("{\"name\":\"Protected test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtFilterIsNotIndependentlyRegisteredByServletContainer() {
        assertThat(servletContext.getFilterRegistrations().values())
            .extracting(FilterRegistration::getClassName)
            .doesNotContain(JwtAuthenticationFilter.class.getName());
    }

    @Test
    void authDiagnosticsIdentifyServiceAndSecurity401ResponsesWithoutSensitiveData(CapturedOutput output)
        throws Exception {
        String username = "diagnostic-" + java.util.UUID.randomUUID();
        String email = username + "@example.invalid";
        String password = "test-only-diagnostic-password";
        String registration = "{\"username\":\"" + username + "\",\"email\":\"" + email
            + "\",\"fullName\":\"Diagnostic Test User\",\"password\":\"" + password + "\"}";

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(registration))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"incorrect-test-password\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(registration))
            .andExpect(status().isConflict());
        mockMvc.perform(get("/api/tickets/my"))
            .andExpect(status().isUnauthorized());

        assertThat(hasDiagnosticLine(output, "AuthRequestBoundaryFilter",
            "method=POST path=/api/auth/register status=200 authorization_present=false")).isTrue();
        assertThat(hasDiagnosticLine(output, "SecurityChainDiagnosticFilter",
            "method=POST path=/api/auth/login status=200 authorization_present=false")).isTrue();
        assertThat(hasDiagnosticLine(output, "AuthControllerDiagnosticInterceptor",
            "method=POST path=/api/auth/login status=401 authorization_present=false")).isTrue();
        assertThat(hasDiagnosticLine(output, "AuthService",
            "method=POST path=/api/auth/login status=401 authorization_present=false")).isTrue();
        assertThat(hasDiagnosticLine(output, "AuthService",
            "method=POST path=/api/auth/register status=409 authorization_present=false")).isTrue();
        assertThat(hasDiagnosticLine(output, "SecurityConfig",
            "method=GET path=/api/tickets/my status=401 authorization_present=false")).isTrue();
        assertThat(output.getOut()).doesNotContain(username, email, password, "incorrect-test-password");
    }

    private static boolean hasDiagnosticLine(CapturedOutput output, String loggerName, String fields) {
        return output.getOut().lines()
            .anyMatch(line -> line.contains(loggerName) && line.contains(fields));
    }
}
