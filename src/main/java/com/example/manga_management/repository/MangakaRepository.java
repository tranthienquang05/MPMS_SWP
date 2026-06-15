package com.example.manga_management.repository;

import com.example.manga_management.entity.Mangaka;
import com.example.manga_management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MangakaRepository extends JpaRepository<Mangaka, String> {
    
    // Tìm kiếm thông tin Mangaka dựa trên đối tượng User truyền vào
    Optional<Mangaka> findByUser(User user);
}