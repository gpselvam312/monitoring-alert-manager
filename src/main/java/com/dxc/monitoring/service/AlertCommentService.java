package com.dxc.monitoring.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertComment;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.AlertCommentRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class AlertCommentService {
    private final AlertRepository alertRepository;
    private final AlertCommentRepository commentRepository;
    private final UserRepository userRepository;

    public AlertCommentService(AlertRepository alertRepository, AlertCommentRepository commentRepository,
            UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void addComment(Long alertId, Long userId, String commentText) {
        String text = commentText == null ? "" : commentText.trim();
        if (text.isEmpty() || text.length() > 5000) {
            throw new IllegalArgumentException("Comments must contain between 1 and 5000 characters.");
        }
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert was not found."));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("The signed-in user was not found."));
        AlertComment comment = new AlertComment();
        comment.setAlert(alert);
        comment.setAuthorUserId(author.getId());
        String authorName = author.getFullName();
        if (authorName == null || authorName.isBlank()) authorName = author.getUsername();
        comment.setAuthorName(authorName == null || authorName.isBlank() ? "Former user" : authorName.trim());
        comment.setCommentText(text);
        commentRepository.save(comment);
    }
}
