package com.wdmmg.expense.user;

import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.user.UserDtos.UserResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
public class AvatarController {
    private final AvatarService service;

    public AvatarController(AvatarService service) {
        this.service = service;
    }

    @PutMapping(value = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse upload(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) {
        return service.upload(me.id(), file);
    }

    @DeleteMapping("/api/users/me/avatar")
    public UserResponse remove(@AuthenticationPrincipal AuthUser me) {
        return service.remove(me.id());
    }

    /**
     * Public so it works in <img src>. Keys are random UUIDs that change on every upload,
     * so responses can be cached forever.
     */
    @GetMapping("/api/avatars/{key}")
    public ResponseEntity<byte[]> get(@PathVariable UUID key) {
        return service.find(key)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(a.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
