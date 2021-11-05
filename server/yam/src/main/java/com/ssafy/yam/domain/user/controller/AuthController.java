package com.ssafy.yam.domain.user.controller;

import com.ssafy.yam.domain.user.dto.request.UserRequestDto;
import com.ssafy.yam.domain.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static com.ssafy.yam.utils.ConstantsUtils.AUTH_HEADER;
import static com.ssafy.yam.utils.ConstantsUtils.TOKEN_TYPE;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Validated @RequestBody UserRequestDto.SignUp signup) {
        return ResponseEntity.ok().body(userService.signup(signup));
    }

    @PostMapping("/login")
    public ResponseEntity<?> authorize(@Valid @RequestBody UserRequestDto.Login login) {
        return ResponseEntity.ok().header(AUTH_HEADER, TOKEN_TYPE + userService.login(login).getAccessToken()).body(userService.login(login));
    }
}
