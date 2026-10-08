package com.dxc.monitoring.config;

import com.dxc.monitoring.entity.Role;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.RoleRepository;
import com.dxc.monitoring.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeAdminUser(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (userRepository.findByUsername("admin").isPresent()) {
                return;
            }

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() ->
                        new IllegalStateException(
                            "ADMIN role not found"));

            User admin = new User();

            admin.setUsername("admin");
            admin.setPasswordHash(
                    passwordEncoder.encode("ChangeMe123!"));
            admin.setFullName("System Administrator");
            admin.setEmail("admin@localhost");
            admin.setEnabled(true);
            admin.getRoles().add(adminRole);

            userRepository.save(admin);
        };
    }
}
