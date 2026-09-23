package com.wdmmg.expense.security;

import com.wdmmg.expense.user.Role;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ensures the configured admin account exists (and has the ADMIN role) at startup. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.admin().email().trim().toLowerCase();
        users.findByEmailIgnoreCase(email).ifPresentOrElse(u -> {
            if (u.getRole() != Role.ADMIN) {
                u.setRole(Role.ADMIN);
                log.info("Promoted {} to ADMIN", email);
            }
        }, () -> {
            User admin = new User();
            admin.setEmail(email);
            admin.setName(props.admin().name());
            admin.setPasswordHash(encoder.encode(props.admin().password()));
            admin.setRole(Role.ADMIN);
            users.save(admin);
            log.info("Created admin account {}", email);
        });
    }
}
