package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.dto.AccountPasswordChangeRequest;
import com.vanrides.vanrides.dto.AccountProfileUpdateRequest;
import com.vanrides.vanrides.service.AccountAvatarService;
import com.vanrides.vanrides.service.AccountAvatarStorageService;
import com.vanrides.vanrides.service.AccountMeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private AccountMeService accountMeService;

    @Autowired
    private AccountAvatarStorageService avatarStorageService;

    @Autowired
    private AccountAvatarService accountAvatarService;

    @GetMapping("/me")
    public ResponseEntity<AccountMeResponse> me(HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        return ResponseEntity.ok(accountMeService.resolve(role, userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<AccountMeResponse> updateMe(@RequestBody AccountProfileUpdateRequest body,
                                                      HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        return ResponseEntity.ok(accountMeService.updateProfile(role, userId, body));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AccountMeResponse> updateAvatar(@RequestPart("file") MultipartFile file,
                                                          HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        String avatarUrl = avatarStorageService.store(role, userId, file);
        return ResponseEntity.ok(accountAvatarService.updateAvatar(role, userId, avatarUrl));
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(@RequestBody AccountPasswordChangeRequest body,
                                               HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        accountMeService.changePassword(role, userId, body);
        return ResponseEntity.noContent().build();
    }
}
