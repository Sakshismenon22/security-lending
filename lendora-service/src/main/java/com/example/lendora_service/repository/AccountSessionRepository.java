package com.example.lendora_service.repository;

import com.example.lendora_service.model.AccountSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSessionRepository
  extends JpaRepository<AccountSession, String>
{
  void deleteByUserId(Long userId);
}
