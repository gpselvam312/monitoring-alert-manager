package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.AlertCommentRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.UserRepository;

class AlertCommentServiceTest {
    private AlertRepository alertRepository;
    private AlertCommentRepository commentRepository;
    private UserRepository userRepository;
    private AlertCommentService service;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        commentRepository = mock(AlertCommentRepository.class);
        userRepository = mock(UserRepository.class);
        service = new AlertCommentService(alertRepository, commentRepository, userRepository);
    }

    @Test
    void rejectsBlankCommentWithoutPersisting() {
        assertThrows(IllegalArgumentException.class, () -> service.addComment(7L, 3L, "  "));
        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsCommentLongerThanLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.addComment(7L, 3L, "x".repeat(5001)));
        verify(alertRepository, never()).findById(7L);
    }

    @Test
    void persistsTrimmedCommentWithAuthorAndAlert() {
        Alert alert = mock(Alert.class);
        User author = mock(User.class);
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));
        when(userRepository.findById(3L)).thenReturn(Optional.of(author));

        service.addComment(7L, 3L, "  Investigating the failure.  ");

        org.mockito.ArgumentCaptor<com.dxc.monitoring.entity.AlertComment> captor =
                org.mockito.ArgumentCaptor.forClass(com.dxc.monitoring.entity.AlertComment.class);
        verify(commentRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(alert, captor.getValue().getAlert());
        org.junit.jupiter.api.Assertions.assertEquals(author, captor.getValue().getAuthor());
        org.junit.jupiter.api.Assertions.assertEquals("Investigating the failure.", captor.getValue().getCommentText());
    }
}
