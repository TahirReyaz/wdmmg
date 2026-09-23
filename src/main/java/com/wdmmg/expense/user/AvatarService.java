package com.wdmmg.expense.user;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.user.UserDtos.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AvatarService {
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'},
            "image/webp", new byte[]{'R', 'I', 'F', 'F'});

    private final UserRepository users;
    private final UserAvatarRepository avatars;

    public AvatarService(UserRepository users, UserAvatarRepository avatars) {
        this.users = users;
        this.avatars = avatars;
    }

    @Transactional
    public UserResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose an image to upload");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("Image must be 2 MB or smaller");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Couldn't read the uploaded image");
        }
        String type = detectType(bytes)
                .orElseThrow(() -> ApiException.badRequest("Use a JPEG, PNG or WebP image"));

        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        avatars.deleteByUserId(userId);
        avatars.flush();

        UserAvatar avatar = new UserAvatar();
        avatar.setKey(UUID.randomUUID()); // new key per upload = automatic cache busting
        avatar.setUserId(userId);
        avatar.setContentType(type);
        avatar.setData(bytes);
        avatars.save(avatar);

        user.setAvatarKey(avatar.getKey());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse remove(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        avatars.deleteByUserId(userId);
        user.setAvatarKey(null);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Optional<UserAvatar> find(UUID key) {
        return avatars.findById(key);
    }

    /** Trust the bytes, not the client-supplied content type. */
    static Optional<String> detectType(byte[] bytes) {
        for (var e : SIGNATURES.entrySet()) {
            byte[] sig = e.getValue();
            if (bytes.length < sig.length) continue;
            boolean match = true;
            for (int i = 0; i < sig.length; i++) {
                if (bytes[i] != sig[i]) {
                    match = false;
                    break;
                }
            }
            if (match && e.getKey().equals("image/webp")) {
                match = bytes.length >= 12 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            }
            if (match) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
}
