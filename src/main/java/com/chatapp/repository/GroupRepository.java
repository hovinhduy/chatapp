package com.chatapp.repository;

import com.chatapp.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.user.userId = :userId")
    List<Group> findGroupsByUserId(Long userId);

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.user.userId = :userId AND LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Group> findGroupsByUserIdAndNameContainingIgnoreCase(Long userId, String name);
}