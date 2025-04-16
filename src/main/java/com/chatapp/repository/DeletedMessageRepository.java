package com.chatapp.repository;

import com.chatapp.model.DeletedMessage;
import com.chatapp.model.Message;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeletedMessageRepository extends JpaRepository<DeletedMessage, Long> {
    Optional<DeletedMessage> findByUserAndMessage(User user, Message message);

    List<DeletedMessage> findByUser(User user);

    List<DeletedMessage> findByMessage(Message message);

    void deleteByUserAndMessage(User user, Message message);
}