package com.campusdesk.config;

import com.campusdesk.domain.Category;
import com.campusdesk.domain.Location;
import com.campusdesk.domain.Role;
import com.campusdesk.domain.SlaPolicy;
import com.campusdesk.domain.TicketPriority;
import com.campusdesk.domain.User;
import com.campusdesk.repository.CategoryRepository;
import com.campusdesk.repository.LocationRepository;
import com.campusdesk.repository.SlaPolicyRepository;
import com.campusdesk.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!prod")
public class DataInitializer {
    @Bean
    CommandLineRunner dataInit(UserRepository userRepository,
                               CategoryRepository categoryRepository,
                               LocationRepository locationRepository,
                               SlaPolicyRepository slaPolicyRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@campusdesk.local");
                admin.setFullName("CampusDesk Admin");
                admin.setPassword(passwordEncoder.encode("Admin@123"));
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }

            if (userRepository.findByUsername("staff").isEmpty()) {
                User staff = new User();
                staff.setUsername("staff");
                staff.setEmail("staff@campusdesk.local");
                staff.setFullName("Maintenance Staff");
                staff.setPassword(passwordEncoder.encode("Staff@123"));
                staff.setRole(Role.STAFF);
                userRepository.save(staff);
            }

            if (userRepository.findByUsername("student").isEmpty()) {
                User student = new User();
                student.setUsername("student");
                student.setEmail("student@campusdesk.local");
                student.setFullName("Demo Student");
                student.setPassword(passwordEncoder.encode("Student@123"));
                student.setRole(Role.STUDENT);
                userRepository.save(student);
            }

            String[] categoryNames = {"Wi-Fi", "Classroom Maintenance", "Lab Equipment", "Electrical", "Water Supply"};
            for (String name : categoryNames) {
                if (categoryRepository.findByNameIgnoreCase(name).isEmpty()) {
                    Category category = new Category();
                    category.setName(name);
                    category.setDescription("Sample category for demo");
                    categoryRepository.save(category);
                }
            }

            String[] locationNames = {"Main Block", "Computer Lab", "Hostel Block A", "Library", "Seminar Hall"};
            for (String name : locationNames) {
                if (locationRepository.findByNameIgnoreCase(name).isEmpty()) {
                    Location location = new Location();
                    location.setName(name);
                    locationRepository.save(location);
                }
            }

            if (slaPolicyRepository.count() == 0) {
                for (Category category : categoryRepository.findAll()) {
                    for (TicketPriority priority : TicketPriority.values()) {
                        if (slaPolicyRepository.findByCategoryAndPriority(category, priority).isPresent()) {
                            continue;
                        }
                        SlaPolicy policy = new SlaPolicy();
                        policy.setCategory(category);
                        policy.setPriority(priority);
                        policy.setResponseMinutes(switch (priority) {
                            case CRITICAL -> 15;
                            case HIGH -> 60;
                            case MEDIUM -> 240;
                            case LOW -> 480;
                        });
                        policy.setResolutionMinutes(switch (priority) {
                            case CRITICAL -> 240;
                            case HIGH -> 480;
                            case MEDIUM -> 1440;
                            case LOW -> 4320;
                        });
                        slaPolicyRepository.save(policy);
                    }
                }
            }
        };
    }
}
