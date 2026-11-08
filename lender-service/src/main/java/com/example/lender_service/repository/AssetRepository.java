package com.example.lender_service.repository;

import com.example.lender_service.model.Asset;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AssetRepository extends JpaRepository<Asset, Long> {
  Optional<Asset> findByAssetName(String assetName);
}
