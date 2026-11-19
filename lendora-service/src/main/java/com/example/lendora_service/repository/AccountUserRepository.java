package com.example.lendora_service.repository;

import com.example.lendora_service.model.AccountUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountUserRepository
  extends JpaRepository<AccountUser, Long>
{
  boolean existsByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(
    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
  )
  @org.springframework.data.jpa.repository.Query(
    "select u from AccountUser u where u.email=:email"
  )
  java.util.Optional<AccountUser> lockByEmail(String email);
}
