package com.example.manga_management.repository;

import com.example.manga_management.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, String> {
    // Spring Data JPA sẽ tự động tạo câu lệnh SQL từ tên phương thức này
    List<Proposal> findByStatus(String status);

    List<Proposal> findByStatusAndMangaka_Id(String status, String mangakaId);
}