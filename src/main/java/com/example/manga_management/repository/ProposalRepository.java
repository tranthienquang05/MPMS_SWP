package com.example.manga_management.repository;

import com.example.manga_management.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, String> {
    // THÊM MỚI: Hàm chuẩn JpaRepository để lọc danh sách bản thảo theo trạng thái xử lý
    List<Proposal> findByStatus(String status);
}