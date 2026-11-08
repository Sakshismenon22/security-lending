package com.example.lender_service.repository;

import com.example.lender_service.model.SecurityMaster;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SecurityMasterRepository extends JpaRepository<SecurityMaster, Long> {}
