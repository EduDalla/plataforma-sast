package com.fiap.sast.auth;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapUserTest {
    @Test void emptyDatabaseRequiresCredentials() {
        var users = mock(UserRepository.class);
        assertThrows(IllegalStateException.class, () -> new BootstrapUser(users, new BCryptPasswordEncoder(), "", "").run(null));
        verify(users, never()).save(any());
    }
    @Test void existingUsersDoNotRequireBootstrap() {
        var users = mock(UserRepository.class); when(users.count()).thenReturn(1L);
        assertDoesNotThrow(() -> new BootstrapUser(users, new BCryptPasswordEncoder(), "", "").run(null));
        verify(users, never()).save(any());
    }
}
