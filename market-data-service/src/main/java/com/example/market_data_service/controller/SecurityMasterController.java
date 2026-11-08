package com.example.market_data_service.controller;

import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.repository.SecurityMasterRepository;
import com.example.market_data_service.response.Response;
import com.example.market_data_service.service.SecurityMasterService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/security-master")
@AllArgsConstructor
public class SecurityMasterController {

    private final SecurityMasterService securityMasterService;

    @PostMapping("/add-security")
    public ResponseEntity<Response<SecurityMaster>> addSecurity(@RequestBody SecurityMaster securityMaster){

        return ResponseEntity.ok(new Response<>(200,true,securityMasterService.addSecurity(securityMaster),"Security Added Successfully!", LocalDateTime.now()));

    }

    @GetMapping("/get-all-securities")
    public ResponseEntity<Response<List<SecurityMaster>>> getAllSecurity(){
        return ResponseEntity.ok(new Response<>(200,true,securityMasterService.getAllSecurities(),"All securities are retrieved.", LocalDateTime.now()));

    }

}
