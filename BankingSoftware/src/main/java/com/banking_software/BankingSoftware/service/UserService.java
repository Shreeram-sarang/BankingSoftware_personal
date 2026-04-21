package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.dto.CreateUserRequest;
import com.banking_software.BankingSoftware.entity.User;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;

    @Transactional
    public User create(CreateUserRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new BankingException("USER_EMAIL_EXISTS", "Email already registered");
        }
        if (userRepo.existsByPhone(req.getPhone())) {
            throw new BankingException("USER_PHONE_EXISTS", "Phone already registered");
        }
        User u = new User();
        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setPan(req.getPan());
        return userRepo.save(u);
    }
}
