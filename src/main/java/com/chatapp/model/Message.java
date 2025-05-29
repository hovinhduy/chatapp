package com.chatapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import com.chatapp.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = true)
    @JsonBackReference(value = "sender-messages")
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    @JsonBackReference(value = "receiver-messages")
    private User receiver;

    @ManyToOne
    @JoinColumn(name = "group_id")
    @JsonBackReference(value = "group-messages")
    private Group group;

    @Column(nullable = false , length = 2048)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MessageType type;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    @JsonBackReference(value = "conversation-messages")
    private Conversation conversation;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    @JsonManagedReference
    private Set<Attachments> attachments = new HashSet<>();
}