package com.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.chatapp.model.Attachments;

@Repository
public interface AttachmentsRepository extends JpaRepository<Attachments, Long> {
}