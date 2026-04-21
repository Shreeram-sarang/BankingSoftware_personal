package com.banking_software.BankingSoftware.controller;

import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.entity.User;
import com.banking_software.BankingSoftware.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public User create(@Valid @RequestBody CreateUserRequest req) {
        return userService.create(req);
    }
}
